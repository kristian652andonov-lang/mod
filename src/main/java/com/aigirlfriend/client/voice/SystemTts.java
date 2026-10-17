package com.aigirlfriend.client.voice;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The operating system's own text-to-speech, used as raw material for her voice: Windows SAPI through PowerShell,
 * macOS {@code say}, or pico2wave / flite / espeak-ng / espeak on Linux. Detection happens once, lazily; every call
 * runs an external process with a timeout and only ever on the voice worker thread.
 */
final class SystemTts {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_FAILURES = 3;
    private static final long LIST_TIMEOUT_MS = 20_000, SPEAK_TIMEOUT_MS = 12_000, PROBE_TIMEOUT_MS = 8_000;
    private static final int MAX_WAV_BYTES = 24 << 20;
    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final AtomicLong IDS = new AtomicLong();

    /** Windows PowerShell script; all data arrives base64-encoded in environment variables so text can never become code. */
    private static final String SAPI_SCRIPT = String.join("\n",
            "$ErrorActionPreference = 'Stop'",
            "function D([string]$b) { if ([string]::IsNullOrEmpty($b)) { return '' }; return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($b)) }",
            "Add-Type -AssemblyName System.Speech",
            "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer",
            "try {",
            "  if ($env:GFV_MODE -eq 'list') {",
            "    foreach ($v in $s.GetInstalledVoices()) {",
            "      if ($v.Enabled) {",
            "        $i = $v.VoiceInfo",
            "        $c = ''; if ($i.Culture) { $c = $i.Culture.Name }",
            "        [Console]::Out.WriteLine('GFV|' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($i.Name)) + '|' + $i.Gender + '|' + $c)",
            "      }",
            "    }",
            "  } else {",
            "    $voice = D $env:GFV_VOICE",
            "    if ($voice) { $s.SelectVoice($voice) }",
            "    $fmt = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(22050, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)",
            "    $s.SetOutputToWaveFile((D $env:GFV_OUT), $fmt)",
            "    $s.Speak((D $env:GFV_TEXT))",
            "    $s.SetOutputToNull()",
            "    [Console]::Out.WriteLine('GFV|OK')",
            "  }",
            "} finally { $s.Dispose() }");
    private static final String SAPI_ENCODED = Base64.getEncoder().encodeToString(SAPI_SCRIPT.getBytes(StandardCharsets.UTF_16LE));

    private static final List<String> SAPI_FEMALE = List.of("aria", "jenny", "ava", "emma", "michelle", "sonia", "libby", "zira", "hazel", "susan", "catherine", "linda", "heera", "eva");
    private static final List<String> MAC_FEMALE = List.of("ava", "zoe", "allison", "samantha", "susan", "victoria", "karen", "moira", "tessa", "fiona", "kate", "serena", "veena", "nicky", "joelle", "noelle", "kathy", "vicki");
    private static final Pattern MAC_VOICE = Pattern.compile("^(.+?)\\s+([a-z]{2,3}[_-][A-Za-z0-9_]+)\\s+#");
    private static final Pattern ESPEAK_VARIANT = Pattern.compile("/([MF])\\s+\\S+\\s+!v/(\\S+)");

    private record VoiceInfo(String name, boolean female, String locale) {}

    /** One configured voice of one engine. */
    private abstract static class Engine {
        final String id;
        int failures;
        boolean probed, broken;

        Engine(String id) {
            this.id = id;
        }

        /** Writes the spoken text as a WAVE file to {@code out}. */
        abstract void synthesize(String text, Path out) throws IOException, InterruptedException;
    }

    private static boolean detected;
    private static final List<VoiceInfo> VOICES = new ArrayList<>();
    private static final List<Engine> LINUX = new ArrayList<>();
    private static final Map<String, Engine> ENGINES = new HashMap<>();
    @Nullable
    private static Path powershell, say, tempDir;
    @Nullable
    private static Engine current;
    private static String currentPreference = "\0";

    private SystemTts() {}

    /** Detects the engines (first call only); safe to call repeatedly. */
    static synchronized void detect() {
        if (detected) return;
        detected = true;
        try {
            if (OS.startsWith("windows")) detectWindows();
            else if (OS.startsWith("mac") || OS.startsWith("darwin")) detectMac();
            else detectUnix();
        } catch (Exception e) {
            LOGGER.info("Companion voice: no system text-to-speech ({})", e.toString());
        }
    }

    /** Stable identity of the engine/voice that would speak, or null if none is usable. */
    @Nullable
    static synchronized String engineId(String preferredVoice) {
        Engine e = engine(preferredVoice);
        return e == null ? null : e.id;
    }

    /** Speaks the line with the system voice; null when unavailable or failed, so the caller can fall back. */
    @Nullable
    static synchronized Pcm speak(String text, String preferredVoice) {
        Engine e = engine(preferredVoice);
        if (e == null) return null;
        Pcm pcm = attempt(e, text);
        if (pcm != null) {
            e.failures = 0;
            return pcm;
        }
        if (++e.failures >= MAX_FAILURES) {
            e.broken = true;
            LOGGER.warn("Companion voice: system speech '{}' keeps failing; using her synthesized voice instead", e.id);
        }
        return null;
    }

    @Nullable
    private static Pcm attempt(Engine e, String text) {
        Path out = null;
        try {
            out = tempDir().resolve("line-" + IDS.incrementAndGet() + ".wav");
            e.synthesize(text, out);
            if (!Files.isRegularFile(out)) return null;
            long size = Files.size(out);
            if (size < 64 || size > MAX_WAV_BYTES) return null;
            Pcm pcm = WavFile.read(Files.readAllBytes(out));
            float peak = 0;
            for (float v : pcm.samples()) peak = Math.max(peak, Math.abs(v));
            return pcm.seconds() >= 0.08f && peak > 0.003f ? pcm : null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ex) {
            LOGGER.debug("Companion voice: '{}' failed: {}", e.id, ex.toString());
            return null;
        } finally {
            delete(out);
        }
    }

    @Nullable
    private static Engine engine(String preferredVoice) {
        detect();
        String pref = preferredVoice == null ? "" : preferredVoice.trim().toLowerCase(Locale.ROOT);
        if (!pref.equals(currentPreference) || current != null && current.broken) {
            currentPreference = pref;
            current = choose(pref);
            if (current != null) LOGGER.info("Companion voice: speaking with system voice {}", current.id);
        }
        return current;
    }

    @Nullable
    private static Engine choose(String pref) {
        if (powershell != null) {
            VoiceInfo v = pick(pref, SAPI_FEMALE, l -> l.toLowerCase(Locale.ROOT).startsWith("en"));
            String name = v == null ? "" : v.name();
            return healthy(ENGINES.computeIfAbsent("sapi:" + name, k -> sapi(name)));
        }
        if (say != null) {
            VoiceInfo v = pick(pref, MAC_FEMALE, l -> l.toLowerCase(Locale.ROOT).startsWith("en"));
            String name = v == null ? "" : v.name();
            return healthy(ENGINES.computeIfAbsent("say:" + name, k -> macSay(name)));
        }
        List<Engine> order = new ArrayList<>();
        if (!pref.isEmpty()) {
            for (Engine e : LINUX) if (e.id.toLowerCase(Locale.ROOT).contains(pref)) order.add(e);
            if (order.isEmpty() && pref.matches("[a-z]{2,3}(-[a-z0-9]+)*(\\+[a-z0-9_]+)?")) {
                for (Engine e : LINUX) {
                    if (!e.id.startsWith("espeak")) continue;
                    String exe = e.id.substring(0, e.id.indexOf(':'));
                    order.add(ENGINES.computeIfAbsent(exe + ":" + pref, k -> unixCommand(exe, pref)));
                    break;
                }
            }
        }
        order.addAll(LINUX);
        for (Engine e : order) {
            Engine ok = healthy(e);
            if (ok != null) return ok;
        }
        return null;
    }

    /** Engines are probed with a tiny line the first time they are chosen. */
    @Nullable
    private static Engine healthy(Engine e) {
        if (e.broken) return null;
        if (!e.probed) {
            e.probed = true;
            if (attempt(e, "Hi.") == null) {
                e.broken = true;
                LOGGER.info("Companion voice: system voice {} did not work", e.id);
                return null;
            }
        }
        return e;
    }

    /** Preferred name (substring) first, then the best known female English voice, then any female; null = system default. */
    @Nullable
    private static VoiceInfo pick(String pref, List<String> femaleNames, Predicate<String> english) {
        if (VOICES.isEmpty()) return null;
        if (!pref.isEmpty()) {
            for (VoiceInfo v : VOICES) if (v.name().toLowerCase(Locale.ROOT).contains(pref)) return v;
        }
        VoiceInfo best = null;
        int bestScore = Integer.MAX_VALUE;
        for (VoiceInfo v : VOICES) {
            String lower = v.name().toLowerCase(Locale.ROOT);
            int rank = -1;
            for (int i = 0; i < femaleNames.size() && rank < 0; i++) if (Pattern.compile("\\b" + femaleNames.get(i) + "\\b").matcher(lower).find()) rank = i;
            if (rank < 0 && !v.female()) continue;
            int score = (english.test(v.locale()) ? 0 : 1000) + (rank >= 0 ? rank * 2 : 100)
                    - (lower.contains("premium") || lower.contains("enhanced") || lower.contains("natural") ? 1 : 0);
            if (score < bestScore) {
                bestScore = score;
                best = v;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ Windows (SAPI via PowerShell)

    private static void detectWindows() throws IOException, InterruptedException {
        Path ps = Path.of(System.getenv().getOrDefault("SystemRoot", "C:\\Windows"), "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        if (!Files.isRegularFile(ps)) ps = findOnPath("powershell.exe");
        if (ps == null) return;
        Result r = run(powershellCommand(ps), Map.of("GFV_MODE", "list"), LIST_TIMEOUT_MS);
        for (String line : r.output().split("\\R")) {
            String[] parts = line.trim().split("\\|", -1);
            if (parts.length < 4 || !parts[0].equals("GFV")) continue;
            try {
                String name = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                VOICES.add(new VoiceInfo(name, parts[2].equalsIgnoreCase("Female"), parts[3]));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (!VOICES.isEmpty()) powershell = ps;
        LOGGER.info("Companion voice: found {} Windows voice(s)", VOICES.size());
    }

    private static List<String> powershellCommand(Path ps) {
        return List.of(ps.toString(), "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", SAPI_ENCODED);
    }

    private static Engine sapi(String voice) {
        return new Engine("sapi:" + (voice.isEmpty() ? "default" : voice)) {
            @Override
            void synthesize(String text, Path out) throws IOException, InterruptedException {
                Base64.Encoder b64 = Base64.getEncoder();
                Map<String, String> env = Map.of("GFV_MODE", "speak",
                        "GFV_VOICE", b64.encodeToString(voice.getBytes(StandardCharsets.UTF_8)),
                        "GFV_OUT", b64.encodeToString(out.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8)),
                        "GFV_TEXT", b64.encodeToString(text.getBytes(StandardCharsets.UTF_8)));
                Result r = run(powershellCommand(powershell), env, SPEAK_TIMEOUT_MS);
                if (!r.output().contains("GFV|OK")) throw new IOException("SAPI: " + abbreviate(r.output()));
            }
        };
    }

    // ------------------------------------------------------------------ macOS (say)

    private static void detectMac() throws IOException, InterruptedException {
        Path p = Path.of("/usr/bin/say");
        if (!Files.isExecutable(p)) p = findOnPath("say");
        if (p == null) return;
        Result r = run(List.of(p.toString(), "-v", "?"), Map.of(), PROBE_TIMEOUT_MS);
        for (String line : r.output().split("\\R")) {
            Matcher m = MAC_VOICE.matcher(line);
            if (m.find()) VOICES.add(new VoiceInfo(m.group(1).trim(), false, m.group(2)));
        }
        say = p;
        LOGGER.info("Companion voice: found {} macOS voice(s)", VOICES.size());
    }

    private static Engine macSay(String voice) {
        return new Engine("say:" + (voice.isEmpty() ? "default" : voice)) {
            @Override
            void synthesize(String text, Path out) throws IOException, InterruptedException {
                Path in = tempDir().resolve("line-" + IDS.incrementAndGet() + ".txt");
                try {
                    Files.writeString(in, text, StandardCharsets.UTF_8);
                    List<String> cmd = new ArrayList<>(List.of(say.toString()));
                    if (!voice.isEmpty()) cmd.addAll(List.of("-v", voice));
                    cmd.addAll(List.of("-o", out.toString(), "--file-format=WAVE", "--data-format=LEI16@22050", "-f", in.toString()));
                    Result r = run(cmd, Map.of(), SPEAK_TIMEOUT_MS);
                    if (r.exit() != 0) throw new IOException("say: " + abbreviate(r.output()));
                } finally {
                    delete(in);
                }
            }
        };
    }

    // ------------------------------------------------------------------ Linux / BSD

    private static void detectUnix() {
        if (findOnPath("pico2wave") != null) LINUX.add(unixCommand("pico2wave", "en-US"));
        Path flite = findOnPath("flite");
        if (flite != null) LINUX.add(unixCommand("flite", Pattern.compile("\\bslt\\b").matcher(output(flite, "-lv")).find() ? "slt" : ""));
        for (String exe : List.of("espeak-ng", "espeak")) {
            Path p = findOnPath(exe);
            if (p == null) continue;
            String variants = output(p, "--voices=variant");
            String female = null;
            for (String pick : List.of("f3", "f4", "f2", "f1", "f5")) {
                if (variants.contains("!v/" + pick + " ") || variants.contains("!v/" + pick + "\n") || variants.contains("!v/" + pick + "\r")) {
                    female = pick;
                    break;
                }
            }
            if (female == null) {
                Matcher m = ESPEAK_VARIANT.matcher(variants);
                while (m.find()) {
                    if (m.group(1).equals("F") && !m.group(2).toLowerCase(Locale.ROOT).contains("whisper")) {
                        female = m.group(2);
                        break;
                    }
                }
            }
            LINUX.add(unixCommand(exe, female == null ? "en-us" : "en-us+" + female));
            break;
        }
        LOGGER.info("Companion voice: Linux speech engines {}", LINUX.stream().map(e -> e.id).toList());
    }

    private static String output(Path exe, String arg) {
        try {
            return run(List.of(exe.toString(), arg), Map.of(), PROBE_TIMEOUT_MS).output();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (IOException e) {
            return "";
        }
    }

    private static Engine unixCommand(String exe, String voice) {
        return new Engine(exe + ":" + (voice.isEmpty() ? "default" : voice)) {
            @Override
            void synthesize(String text, Path out) throws IOException, InterruptedException {
                Path bin = findOnPath(exe);
                if (bin == null) throw new IOException(exe + " missing");
                Path in = tempDir().resolve("line-" + IDS.incrementAndGet() + ".txt");
                try {
                    List<String> cmd = new ArrayList<>(List.of(bin.toString()));
                    switch (exe) {
                        case "pico2wave" -> cmd.addAll(List.of("-l", voice, "-w", out.toString(), "--", text));
                        case "flite" -> {
                            Files.writeString(in, text, StandardCharsets.UTF_8);
                            if (!voice.isEmpty()) cmd.addAll(List.of("-voice", voice));
                            cmd.addAll(List.of("-f", in.toString(), "-o", out.toString()));
                        }
                        default -> {
                            Files.writeString(in, text, StandardCharsets.UTF_8);
                            cmd.addAll(List.of("-v", voice, "-b", "1", "-f", in.toString(), "-w", out.toString()));
                        }
                    }
                    Result r = run(cmd, Map.of(), SPEAK_TIMEOUT_MS);
                    if (r.exit() != 0) throw new IOException(exe + ": " + abbreviate(r.output()));
                } finally {
                    delete(in);
                }
            }
        };
    }

    // ------------------------------------------------------------------ processes and files

    private record Result(int exit, String output) {}

    /** Runs a process with closed stdin and output captured to a temp file (no pipes to drain); killed on timeout. */
    private static Result run(List<String> cmd, Map<String, String> env, long timeoutMs) throws IOException, InterruptedException {
        Path log = tempDir().resolve("proc-" + IDS.incrementAndGet() + ".log");
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log.toFile());
            pb.environment().putAll(env);
            Process p = pb.start();
            try {
                p.getOutputStream().close();
            } catch (IOException ignored) {
            }
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
                p.waitFor(2, TimeUnit.SECONDS);
                throw new IOException(new File(cmd.get(0)).getName() + " timed out");
            }
            String out = Files.isRegularFile(log) ? new String(Files.readAllBytes(log), StandardCharsets.UTF_8) : "";
            return new Result(p.exitValue(), out);
        } finally {
            delete(log);
        }
    }

    private static synchronized Path tempDir() throws IOException {
        if (tempDir == null || !Files.isDirectory(tempDir)) {
            tempDir = Files.createTempDirectory("aigirlfriend-voice");
            tempDir.toFile().deleteOnExit();
        }
        return tempDir;
    }

    private static void delete(@Nullable Path p) {
        if (p == null) return;
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            p.toFile().deleteOnExit();
        }
    }

    @Nullable
    private static Path findOnPath(String exe) {
        String path = System.getenv("PATH");
        List<String> dirs = new ArrayList<>(path == null ? List.of() : List.of(path.split(File.pathSeparator)));
        dirs.addAll(List.of("/usr/bin", "/usr/local/bin", "/opt/homebrew/bin"));
        for (String dir : dirs) {
            if (dir.isBlank()) continue;
            try {
                Path p = Path.of(dir, exe);
                if (Files.isRegularFile(p) && Files.isExecutable(p)) return p;
            } catch (RuntimeException ignored) {
            }
        }
        return null;
    }

    private static String abbreviate(String s) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() > 200 ? t.substring(0, 200) + "..." : t;
    }
}

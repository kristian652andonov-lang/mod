package com.aigirlfriend.client.voice.neural;

import com.mojang.logging.LogUtils;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.Tlhelp32;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Windows only: makes sure ONNX Runtime gets a C++ runtime it can live with. The pinned build was compiled with
 * Visual Studio 17.10 or newer, whose {@code std::mutex} needs msvcp140.dll 14.40+: with an older one its first lock
 * is an access violation that takes the whole game down, past any catch. Java builds ship their own msvcp140.dll in
 * their bin folder, which Windows picks before the system's, and Minecraft's bundled Java 21 has 14.38. So when that
 * copy is too old, the system's (kept current by the Visual C++ Redistributable) is loaded first, by full path: ONNX
 * Runtime's import then binds to the msvcp140.dll already in the process.
 */
final class VcRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MIN_MAJOR = 14, MIN_MINOR = 40;
    private static final int FIXED_FILE_INFO_SIGNATURE = 0xFEEF04BD;

    /** What loading needs: a copy to load first ({@code preload}, null for none), or why it cannot work ({@code problem}). */
    record Plan(@Nullable Path preload, @Nullable String problem) {
        static final Plan FINE = new Plan(null, null);
    }

    private VcRuntime() {}

    /** Decides from the files on disk and what the process already has loaded; cheap enough to call before each load. */
    static Plan check() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) return Plan.FINE;
        Path loaded = loadedCopy();
        if (loaded != null) return newEnough(loaded) ? Plan.FINE : new Plan(null, describe(loaded) + " already in use");
        Path javaCopy = Path.of(System.getProperty("java.home"), "bin", "msvcp140.dll");
        boolean javaHasOne = Files.isRegularFile(javaCopy);
        if (javaHasOne && newEnough(javaCopy)) return Plan.FINE;
        Path systemCopy = Path.of(Objects.requireNonNullElse(System.getenv("SystemRoot"), "C:\\Windows"), "System32", "msvcp140.dll");
        if (newEnough(systemCopy)) return new Plan(javaHasOne ? systemCopy : null, null);
        return new Plan(null, describe(systemCopy) + (javaHasOne ? ", Java has " + describe(javaCopy) : ""));
    }

    private static boolean newEnough(Path dll) {
        int[] v = version(dll);
        return v != null && (v[0] > MIN_MAJOR || v[0] == MIN_MAJOR && v[1] >= MIN_MINOR);
    }

    private static String describe(Path dll) {
        int[] v = version(dll);
        return v == null ? "no usable " + dll : "msvcp140.dll " + v[0] + "." + v[1] + " (" + dll + ")";
    }

    /** The msvcp140.dll this process already has loaded, through JNA (which Minecraft ships), or null if none or unknown. */
    @Nullable
    private static Path loadedCopy() {
        try {
            for (Tlhelp32.MODULEENTRY32W m : Kernel32Util.getModules(Kernel32.INSTANCE.GetCurrentProcessId())) {
                if (m.szModule().equalsIgnoreCase("msvcp140.dll")) return Path.of(m.szExePath());
            }
        } catch (Throwable t) { // JNA unavailable or refused: decide from the files alone
            LOGGER.debug("Companion voice: could not list this process's modules", t);
        }
        return null;
    }

    /** A DLL's file version as {major, minor}, read from the VS_FIXEDFILEINFO after its "VS_VERSION_INFO" key; null if unreadable. */
    @Nullable
    static int[] version(Path dll) {
        try {
            byte[] b = Files.readAllBytes(dll);
            byte[] key = "VS_VERSION_INFO".getBytes(StandardCharsets.UTF_16LE);
            for (int i = indexOf(b, key, 0); i >= 0; i = indexOf(b, key, i + 1)) {
                // the key's terminator and padding to 32 bits come first, so the signature is a few bytes on
                for (int j = i + key.length + 2; j <= i + key.length + 8 && j + 12 <= b.length; j += 2) {
                    ByteBuffer info = ByteBuffer.wrap(b, j, 12).order(ByteOrder.LITTLE_ENDIAN);
                    if (info.getInt() != FIXED_FILE_INFO_SIGNATURE) continue;
                    info.getInt(); // structure version
                    int fileVersionMs = info.getInt();
                    return new int[]{fileVersionMs >>> 16, fileVersionMs & 0xFFFF};
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("Companion voice: could not read the version of {}", dll, e);
        }
        return null;
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = from; i <= haystack.length - needle.length; i++) {
            for (int k = 0; k < needle.length; k++) {
                if (haystack[i + k] != needle[k]) continue outer;
            }
            return i;
        }
        return -1;
    }
}

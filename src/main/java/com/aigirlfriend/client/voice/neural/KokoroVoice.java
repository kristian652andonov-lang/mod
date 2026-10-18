package com.aigirlfriend.client.voice.neural;

import com.aigirlfriend.client.voice.g2p.EnglishG2P;
import com.aigirlfriend.client.voice.g2p.KokoroVocab;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Her natural voice: text to phonemes (misaki's G2P, ported), phonemes to speech with Kokoro, one chunk at a time
 * so she can start talking while the rest is still being rendered. Chunks are sized from how fast this computer
 * renders: playback may start only once the rest can be rendered before what is ready runs out, so she does not
 * stall mid-line (a slower computer renders a bigger first chunk instead). Used only from the voice worker.
 */
final class KokoroVoice implements AutoCloseable {
    /** Kokoro reads about this many phoneme characters per second of speech at speed 1. */
    private static final float PHONEMES_PER_SECOND = 18f;
    /** Silence Kokoro adds around every chunk at speed 1 (trimmed off later), in seconds of audio it still renders. */
    private static final float PADDING_SECONDS = 0.8f;
    /** How long the first chunk may take to render when the rest would keep up with a smaller one anyway. */
    private static final float FIRST_CHUNK_SECONDS = 2.0f;
    /** Past this much rendering she starts talking even if the rest may not keep up: a pause mid-line beats silence. */
    private static final float MAX_HOLD_SECONDS = 5.0f;
    /**
     * Chunk sizes in phoneme characters. Later chunks are at least a phrase long ({@code MIN_NEXT}, about 2.5 s):
     * every chunk boundary is a short pause and a fresh intonation, so she starts a little later on a slow computer
     * rather than speaking in fragments. No piece shorter than {@code MIN_PIECE} is ever cut off on its own.
     */
    private static final int MIN_FIRST = 40, MAX_FIRST = 160, MIN_NEXT = 48, MAX_NEXT = 250, MIN_PIECE = 12;
    private static final String CLAUSE_MARKS = ",;:—…";

    private final Path dir;
    private final KokoroVocab vocab;
    private final KokoroModel model;
    private final EnglishG2P american;
    private EnglishG2P british;
    private VoiceSpec spec;
    private VoiceTable plain, bright, soft;
    /** Seconds of rendering per second of (untrimmed) audio, smoothed; a modest guess until a real line is measured. */
    private float rtf = 0.5f;
    private boolean measured;

    private KokoroVoice(Path dir, KokoroVocab vocab, KokoroModel model, EnglishG2P american) {
        this.dir = dir;
        this.vocab = vocab;
        this.model = model;
        this.american = american;
    }

    /** Loads the lexicons, voice tables and model, then speaks once so the first real line is not slow. */
    static KokoroVoice load(OrtRuntime runtime, Path dir, VoiceSpec spec, int threads) throws Exception {
        KokoroVocab vocab = KokoroVocab.load(NeuralFiles.VOCAB.in(dir));
        EnglishG2P american = EnglishG2P.load(NeuralFiles.US_GOLD.in(dir), NeuralFiles.US_SILVER.in(dir), false);
        KokoroModel model = KokoroModel.open(runtime, NeuralFiles.MODEL.in(dir), threads);
        KokoroVoice voice = new KokoroVoice(dir, vocab, model, american);
        try {
            voice.setVoice(spec);
            voice.speak("Hi.", 0, 1f, 1f, (s, b, last, play) -> true);
            voice.measured = false; // the first run is cold (one-time setup), so it says little about speed
            return voice;
        } catch (Exception e) {
            voice.close();
            throw e;
        }
    }

    VoiceSpec spec() {
        return spec;
    }

    float rtf() {
        return rtf;
    }

    /** Switches voice (and prepares the delivery blends used for moods); cheap when nothing changed. */
    void setVoice(VoiceSpec next) throws IOException {
        if (next.equals(spec)) return;
        List<VoiceTable> tables = new ArrayList<>();
        for (String id : next.ids()) tables.add(VoiceTable.read(NeuralFiles.VOICES.get(id).in(dir)));
        VoiceTable base = VoiceTable.blend(tables, next.weights());
        if (next.british()) {
            if (british == null) british = EnglishG2P.load(NeuralFiles.GB_GOLD.in(dir), NeuralFiles.GB_SILVER.in(dir), true);
            bright = soft = base; // the mood voices are American; their timing would not suit a British accent
        } else {
            bright = base.withDeliveryOf(table("af_bella"), 0.5f);
            soft = base.withDeliveryOf(table("af_nicole"), 0.5f);
        }
        plain = base;
        spec = next;
    }

    private VoiceTable table(String id) throws IOException {
        return VoiceTable.read(NeuralFiles.VOICES.get(id).in(dir));
    }

    /**
     * Speaks a cleaned line in the given mood; {@code speedScale} multiplies the mood's pace, and {@code playbackScale}
     * is how much longer (or shorter) the audio plays than Kokoro rendered it (a pitch change resamples it).
     */
    void speak(String text, int emotion, float speedScale, float playbackScale, NeuralVoice.Sink sink) throws Exception {
        Delivery delivery = Delivery.of(emotion);
        EnglishG2P g2p = spec.british() ? british : american;
        VoiceTable table = switch (delivery.tone()) {
            case PLAIN -> plain;
            case BRIGHT -> bright;
            case SOFT -> soft;
        };
        float speed = Math.max(0.5f, Math.min(2.0f, delivery.speed() * speedScale));
        float heard = speed / playbackScale; // the pace the listener hears, for how long the audio lasts
        Deque<String> pieces = new ArrayDeque<>();
        for (String chunk : g2p.kokoroChunks(delivery.shape(text))) pieces.addAll(split(chunk, ".!?…"));
        long start = System.nanoTime(), playing = 0; // when playback was allowed to start, 0 = not yet
        float ready = 0; // seconds of speech handed over (a slight underestimate: pauses between chunks are left out)
        boolean first = true;
        while (!pieces.isEmpty()) {
            String chunk;
            if (playing != 0) chunk = take(pieces, 0, chunkLimit(ready - seconds(System.nanoTime() - playing), speed));
            else {
                // she is not talking yet: this chunk must be long enough for the rest to keep up once she starts
                int needed = startLimit(ready, length(pieces), MAX_HOLD_SECONDS - seconds(System.nanoTime() - start), speed, heard);
                chunk = take(pieces, needed, Math.max(needed * 5 / 4, first ? firstChunkLimit(speed) : 0));
            }
            first = false;
            long[] ids = vocab.inputIds(chunk);
            if (ids.length <= 2) continue; // nothing speakable: Kokoro would only make noise
            long t0 = System.nanoTime();
            float[] audio = model.synthesize(ids, table.row(KokoroVocab.styleRow(chunk)), speed);
            measure(System.nanoTime() - t0, audio.length);
            ready += Math.max(0f, audio.length / (float) KokoroModel.SAMPLE_RATE - PADDING_SECONDS / speed) * playbackScale;
            boolean last = pieces.isEmpty();
            if (playing == 0 && (last || keepsUp(ready, length(pieces), speed, heard) || seconds(System.nanoTime() - start) >= MAX_HOLD_SECONDS)) {
                playing = System.nanoTime();
            }
            if (!sink.accept(audio, boundary(chunk), last, playing != 0)) return;
        }
    }

    /** The punctuation a chunk ends on ("..." counts as an ellipsis; closing quotes are skipped). */
    private static char boundary(String chunk) {
        if (chunk.endsWith("...")) return '…';
        int i = chunk.length() - 1;
        while (i > 0 && (chunk.charAt(i) == '”' || chunk.charAt(i) == ')')) i--;
        return chunk.charAt(i);
    }

    private void measure(long nanos, int samples) {
        if (samples <= 0) return;
        float r = seconds(nanos) / (samples / (float) KokoroModel.SAMPLE_RATE);
        rtf = measured ? 0.7f * rtf + 0.3f * r : r;
        measured = true;
    }

    private static float seconds(long nanos) {
        return nanos / 1e9f;
    }

    /** Kokoro's output, padding included, lasts longer the slower it speaks. */
    private float renderSeconds(int phonemes, float speed) {
        return rtf * (phonemes / PHONEMES_PER_SECOND + PADDING_SECONDS) / speed;
    }

    private static float speechSeconds(int phonemes, float speed) {
        return phonemes / (PHONEMES_PER_SECOND * speed);
    }

    /** The most phoneme characters that render within {@code seconds} at the measured speed (may be negative). */
    private int renderableIn(float seconds, float speed) {
        return (int) Math.floor((seconds * speed / Math.max(rtf, 0.01f) - PADDING_SECONDS) * PHONEMES_PER_SECOND);
    }

    /** A first chunk that renders in about {@link #FIRST_CHUNK_SECONDS}: short enough to start soon, long enough to flow. */
    private int firstChunkLimit(float speed) {
        return Math.max(MIN_FIRST, Math.min(MAX_FIRST, renderableIn(FIRST_CHUNK_SECONDS, speed)));
    }

    /** Once she is talking: a next chunk that renders before the {@code ahead} seconds still to play run out. */
    private int chunkLimit(float ahead, float speed) {
        return Math.max(MIN_NEXT, Math.min(MAX_NEXT, renderableIn(ahead, speed)));
    }

    /**
     * Before she talks: the smallest next chunk after which the remaining {@code rest} characters keep up, but no
     * more than renders in {@code budget} seconds (past that she starts anyway). {@code heard}: see {@link #keepsUp}.
     */
    private int startLimit(float ready, int rest, float budget, float speed, float heard) {
        int cap = Math.max(2 * MIN_PIECE, Math.min(rest, renderableIn(budget, speed)));
        for (int n = 2 * MIN_PIECE; n < cap; n += 6) {
            if (keepsUp(ready + speechSeconds(n, heard), rest - n, speed, heard)) return n;
        }
        return cap;
    }

    /**
     * Whether, with {@code ahead} seconds of speech ready to play, the remaining {@code rest} characters can be
     * rendered chunk by chunk, each before the audio ahead of it runs out. Kokoro renders at {@code speed}; the
     * listener hears the result at {@code heard}.
     */
    private boolean keepsUp(float ahead, int rest, float speed, float heard) {
        while (rest > 0) {
            int n = Math.min(rest, chunkLimit(ahead, speed));
            float render = renderSeconds(n, speed);
            if (render > ahead) return false;
            ahead += speechSeconds(n, heard) - render;
            rest -= n;
        }
        return true;
    }

    private static int length(Deque<String> pieces) {
        int n = 0;
        for (String p : pieces) n += p.length() + 1;
        return Math.max(0, n - 1);
    }

    /**
     * The next chunk: whole pieces (sentences) while they fit in {@code max} characters, and more until it has at
     * least {@code min}. A piece far longer than the room left is cut at a phrase break (see {@link #cutPoint}).
     */
    static String take(Deque<String> pieces, int min, int max) {
        StringBuilder b = new StringBuilder(next(pieces, max));
        while (!pieces.isEmpty()) {
            int room = max - b.length() - 1;
            if (pieces.peekFirst().length() <= room) b.append(' ').append(pieces.removeFirst());
            else if (b.length() < min - MIN_PIECE) b.append(' ').append(next(pieces, room));
            else break;
        }
        return b.toString();
    }

    /** Removes the first piece and returns it, or just its head when it is far longer than {@code limit}. */
    private static String next(Deque<String> pieces, int limit) {
        String piece = pieces.removeFirst();
        if (piece.length() <= limit + MIN_PIECE) return piece;
        int cut = cutPoint(piece, Math.min(limit, EnglishG2P.MAX_PHONEMES));
        if (cut <= 0) return piece;
        pieces.addFirst(piece.substring(cut + 1));
        return piece.substring(0, cut);
    }

    /**
     * The space to cut {@code ps} at, within {@code limit} and leaving at least {@code MIN_PIECE} on each side, or -1.
     * In the second half of the limit it prefers a clause mark, then a phrase break: a stressed (content) word
     * followed by an unstressed one, as in "I missed you so much | while you were gone". Otherwise the last space.
     */
    private static int cutPoint(String ps, int limit) {
        int phrase = -1, word = -1;
        for (int i = Math.min(limit, ps.length() - MIN_PIECE - 1); i >= MIN_PIECE; i--) {
            if (ps.charAt(i) != ' ') continue;
            if (word < 0) word = i;
            if (i < limit / 2) break;
            int j = i - 1;
            while (j > 0 && (ps.charAt(j) == '”' || ps.charAt(j) == ')')) j--;
            if (CLAUSE_MARKS.indexOf(ps.charAt(j)) >= 0) return i;
            if (phrase < 0 && stressed(ps, ps.lastIndexOf(' ', i - 1) + 1, i) && !stressed(ps, i + 1, nextSpace(ps, i + 1))) phrase = i;
        }
        return phrase >= 0 ? phrase : word;
    }

    private static boolean stressed(String ps, int from, int to) {
        return ps.substring(from, to).indexOf('ˈ') >= 0;
    }

    private static int nextSpace(String ps, int from) {
        int i = ps.indexOf(' ', from);
        return i < 0 ? ps.length() : i;
    }

    /** Splits a phoneme string after any of {@code marks} (plus closing quotes) that is followed by a space. */
    private static List<String> split(String ps, String marks) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < ps.length(); i++) {
            if (marks.indexOf(ps.charAt(i)) < 0) continue;
            int end = i + 1;
            while (end < ps.length() && (marks.indexOf(ps.charAt(end)) >= 0 || ps.charAt(end) == '”' || ps.charAt(end) == ')')) end++;
            if (end < ps.length() && ps.charAt(end) == ' ') {
                String piece = ps.substring(start, end).strip();
                if (!piece.isEmpty()) out.add(piece);
                start = end + 1;
            }
            i = end - 1;
        }
        String rest = ps.substring(Math.min(start, ps.length())).strip();
        if (!rest.isEmpty()) out.add(rest);
        return out;
    }

    @Override
    public void close() throws Exception {
        model.close();
    }
}

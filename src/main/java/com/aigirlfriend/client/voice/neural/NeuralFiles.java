package com.aigirlfriend.client.voice.neural;

import com.aigirlfriend.util.Download;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything her natural voice downloads, each from an immutable URL (Maven Central, or a repository revision
 * pinned by commit) with its size and SHA-256. Nothing of it ships in the mod jar.
 */
final class NeuralFiles {
    /** A file in the voice folder. */
    record Entry(String path, Download.Pinned pin) {
        Path in(Path dir) {
            return dir.resolve(path);
        }
    }

    private static final String KOKORO = "https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX/resolve/1939ad2a8e416c0acfeecc08a694d14ef25f2231/";
    private static final String MISAKI = "https://raw.githubusercontent.com/hexgrad/misaki/e820629b96334db28227df37f280e4836d46fadb/misaki/data/";

    /** ONNX Runtime 1.23.2 (MIT): the newest release that still has natives for all five desktop platforms. */
    static final Entry RUNTIME = new Entry("onnxruntime-" + OrtRuntime.VERSION + ".jar", new Download.Pinned(
            "https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime/1.23.2/onnxruntime-1.23.2.jar",
            75_255_976L, "e0ab4a1af57d2da09097202f2dfd691e390c82e81183314788ccde4cf7c3cc38"));
    /** Kokoro-82M v1.0, full precision: the quantized exports are slower on CPU and can emit NaN audio. */
    static final Entry MODEL = new Entry("kokoro-82m-v1.0.onnx", new Download.Pinned(KOKORO + "onnx/model.onnx",
            325_532_232L, "8fbea51ea711f2af382e88c833d9e288c6dc82ce5e98421ea61c058ce21a34cb"));
    /** The phoneme vocabulary ({@code model.vocab}). */
    static final Entry VOCAB = new Entry("kokoro-tokenizer.json", new Download.Pinned(KOKORO + "tokenizer.json",
            3_497L, "77a02c8e164413299b4b4c403b14f8e0e1c1b727db4d46a09d6327b861060a34"));
    /** misaki's pronunciation lexicons (Apache-2.0), American and British. */
    static final Entry US_GOLD = lexicon("us_gold.json", 3_000_469L, "dc414872a49a28ae6c141463d502fd945f3b2fde040484fdc47d00cc4612686f");
    static final Entry US_SILVER = lexicon("us_silver.json", 3_099_517L, "de8f67be911bb6c659187b4a65fd966b6a30e56350e0f790d763210b053ac475");
    static final Entry GB_GOLD = lexicon("gb_gold.json", 2_838_552L, "29e62f4b60261c88f7f3c2c7811ca3825978948090b72d2b27d565b729282f71");
    static final Entry GB_SILVER = lexicon("gb_silver.json", 3_663_898L, "48131e2d92ccc41655f4543e87e0f938e71463eb5a54be7f0693bb712ebb6bce");

    /** Voice id to style table: af_* American, bf_* British. */
    static final Map<String, Entry> VOICES;

    static {
        Map<String, Entry> v = new LinkedHashMap<>();
        voice(v, "af_heart", "d583ccff3cdca2f7fae535cb998ac07e9fcb90f09737b9a41fa2734ec44a8f0b");
        voice(v, "af_sarah", "4409fbc125afabacc615d94db5398d847006a737b0247d6892b7a9a0007a2f0a");
        voice(v, "af_bella", "f69d836209b78eb8c66e75e3cda491e26ea838a3674257e9d4e5703cbaf55c8b");
        voice(v, "af_nicole", "cd2191ab31b914ed7b318416b0e4440fdf392ddad9106a060819aa600a64f59a");
        voice(v, "af_sky", "4435255c9744f3f31659e0d714ab7689bf65d9e77ec1cce060f083912614f0b9");
        voice(v, "af_nova", "18778272caa0d0eebaea251c35fd635f038434f9eee5e691d02a174bd328414f");
        voice(v, "af_jessica", "a240a5e3c15b43563d6e923bdca8ef5613a23471d9b77653694012435df23bd8");
        voice(v, "af_aoede", "4a004c33430762e2461eedb2013fad808ef4ab3121f5300f554476caf58d8361");
        voice(v, "af_kore", "9be5221b6a941c04b561959b8ff0b06e809444dcc4ab7e75a7b23606f691819e");
        voice(v, "af_river", "00a2bcf82b1d86e8f19902ede58c65ccf6c0e43b44b7d74fad54e5d8933c9c30");
        voice(v, "af_alloy", "c4a6b876047fd7fb472edf4ebd63cfac7c3b958a7cae7c106e8f038ca6308c45");
        voice(v, "bf_emma", "669fe0647f9dd04fcab92f1439a40eeb4c8b4ab1f82e4996fe3d918ce4a63b73");
        voice(v, "bf_isabella", "3754352c4aaa46d17f27654ab7518d65b62ad6163a0f55a5f4330c2da2c4e94f");
        voice(v, "bf_alice", "08afa6ba24da61ea5e8efa139e5aadc938d83f0a6da5a900adaf763ac1da5573");
        voice(v, "bf_lily", "5e0ee32ebe64a467124976b14e69590746f1c4ce41a12b587a50c862edfea335");
        VOICES = Collections.unmodifiableMap(v);
    }

    private NeuralFiles() {}

    /** Everything, smallest first so the voice tables and lexicons are in place before the long downloads. */
    static List<Entry> all() {
        List<Entry> out = new ArrayList<>(List.of(VOCAB));
        out.addAll(VOICES.values());
        out.addAll(List.of(US_GOLD, US_SILVER, GB_GOLD, GB_SILVER, RUNTIME, MODEL));
        return out;
    }

    static long totalBytes() {
        return all().stream().mapToLong(e -> e.pin().size()).sum();
    }

    private static Entry lexicon(String name, long size, String sha256) {
        return new Entry("misaki/" + name, new Download.Pinned(MISAKI + name, size, sha256));
    }

    private static void voice(Map<String, Entry> map, String id, String sha256) {
        map.put(id, new Entry("voices/" + id + ".bin", new Download.Pinned(KOKORO + "voices/" + id + ".bin", VoiceTable.ROWS * VoiceTable.DIM * 4L, sha256)));
    }
}

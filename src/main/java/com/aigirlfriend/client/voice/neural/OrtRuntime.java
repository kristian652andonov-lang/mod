package com.aigirlfriend.client.voice.neural;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * ONNX Runtime's Java API (MIT, Microsoft), loaded at run time from the downloaded Maven jar in a class loader of its
 * own and driven by reflection, so the mod neither depends on nor ships it. The native libraries are unpacked once
 * into the voice folder (not the system temp folder, which may be mounted noexec or fill up with copies) and loaded
 * from there. A JNI library binds to a single class loader for the life of the JVM, so the runtime is loaded at
 * most once; a failure is remembered and never retried.
 */
final class OrtRuntime {
    static final String VERSION = "1.23.2";
    private static final String NATIVE_PREFIX = "ai/onnxruntime/native/";
    private static final String NATIVE_PATH_PROPERTY = "onnxruntime.native.path";

    @Nullable
    private static OrtRuntime instance;
    @Nullable
    private static Throwable failure;

    private final Object env;
    private final Constructor<?> newOptions;
    private final Method setIntraOpThreads, setInterOpThreads, setExecutionMode, setOptLevel, addConfigEntry;
    private final Method createSession, longTensor, floatTensor, run, resultGet, floatBuffer, inputNames, outputNames;
    private final Object sequential, allOptimizations;

    private OrtRuntime(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> envClass = loader.loadClass("ai.onnxruntime.OrtEnvironment");
        Class<?> levelClass = loader.loadClass("ai.onnxruntime.OrtLoggingLevel");
        Class<?> sessionClass = loader.loadClass("ai.onnxruntime.OrtSession");
        Class<?> optionsClass = loader.loadClass("ai.onnxruntime.OrtSession$SessionOptions");
        Class<?> modeClass = loader.loadClass("ai.onnxruntime.OrtSession$SessionOptions$ExecutionMode");
        Class<?> optClass = loader.loadClass("ai.onnxruntime.OrtSession$SessionOptions$OptLevel");
        Class<?> tensorClass = loader.loadClass("ai.onnxruntime.OnnxTensor");
        Class<?> resultClass = loader.loadClass("ai.onnxruntime.OrtSession$Result");
        // creating the environment runs the native loader: libraries load (or fail) here
        env = envClass.getMethod("getEnvironment", levelClass, String.class).invoke(null, constant(levelClass, "ORT_LOGGING_LEVEL_ERROR"), "aigirlfriend-voice");
        newOptions = optionsClass.getConstructor();
        setIntraOpThreads = optionsClass.getMethod("setIntraOpNumThreads", int.class);
        setInterOpThreads = optionsClass.getMethod("setInterOpNumThreads", int.class);
        setExecutionMode = optionsClass.getMethod("setExecutionMode", modeClass);
        setOptLevel = optionsClass.getMethod("setOptimizationLevel", optClass);
        addConfigEntry = optionsClass.getMethod("addConfigEntry", String.class, String.class);
        createSession = envClass.getMethod("createSession", String.class, optionsClass);
        longTensor = tensorClass.getMethod("createTensor", envClass, LongBuffer.class, long[].class);
        floatTensor = tensorClass.getMethod("createTensor", envClass, FloatBuffer.class, long[].class);
        run = sessionClass.getMethod("run", Map.class);
        resultGet = resultClass.getMethod("get", String.class);
        floatBuffer = tensorClass.getMethod("getFloatBuffer");
        inputNames = sessionClass.getMethod("getInputNames");
        outputNames = sessionClass.getMethod("getOutputNames");
        sequential = constant(modeClass, "SEQUENTIAL");
        allOptimizations = constant(optClass, "ALL_OPT");
    }

    /**
     * The ORT native folder name for this computer ({@code win-x64}, {@code osx-aarch64}, ...), or null when the
     * pinned runtime has no build for it (32-bit, Windows on ARM, macOS before 13.4, ...).
     */
    @Nullable
    static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        // same order as ORT's own detection: "darwin" contains "win"
        String o = os.contains("mac") || os.contains("darwin") ? "osx" : os.contains("win") ? "win" : os.contains("nux") ? "linux" : null;
        String a = arch.startsWith("amd64") || arch.startsWith("x86_64") ? "x64" : arch.startsWith("aarch64") || arch.startsWith("arm64") ? "aarch64" : null;
        if (o == null || a == null) return null;
        if (o.equals("win") && a.equals("aarch64")) return null;
        if (o.equals("osx") && !macOsAtLeast(13, 4)) return null; // the 1.23.2 dylibs are built for Ventura 13.4+
        return o + "-" + a;
    }

    private static boolean macOsAtLeast(int major, int minor) {
        String[] v = System.getProperty("os.version", "0").split("\\.");
        try {
            int ma = Integer.parseInt(v[0]), mi = v.length > 1 ? Integer.parseInt(v[1]) : 0;
            return ma > major || ma == major && mi >= minor;
        } catch (NumberFormatException e) {
            return true; // unknown format: let the loader decide
        }
    }

    /** Loads the runtime from {@code jar} (first call only), unpacking its natives below {@code nativeRoot}. */
    static synchronized OrtRuntime load(Path jar, Path nativeRoot) throws Exception {
        if (instance != null) return instance;
        if (failure != null) throw new IllegalStateException("ONNX Runtime failed to load earlier", failure);
        try {
            String platform = platform();
            if (platform == null) throw new UnsupportedOperationException("no ONNX Runtime build for " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
            VcRuntime.Plan crt = VcRuntime.check();
            if (crt.problem() != null) throw new UnsupportedOperationException("needs msvcp140.dll 14.40 or newer: " + crt.problem());
            Path natives = unpackNatives(jar, nativeRoot.resolve("onnxruntime-" + VERSION).resolve(platform), platform);
            if (crt.preload() != null) System.load(crt.preload().toString()); // ONNX Runtime binds to this one, not Java's older copy
            // the platform loader as parent: ORT only needs the JDK, and sees nothing of the game's classes
            URLClassLoader loader = new URLClassLoader("aigirlfriend-onnxruntime", new URL[]{jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
            String previous = System.getProperty(NATIVE_PATH_PROPERTY);
            System.setProperty(NATIVE_PATH_PROPERTY, natives.toAbsolutePath().toString());
            try {
                instance = new OrtRuntime(loader);
            } finally {
                if (previous == null) System.clearProperty(NATIVE_PATH_PROPERTY);
                else System.setProperty(NATIVE_PATH_PROPERTY, previous);
            }
            return instance;
        } catch (Throwable t) {
            Throwable cause = unwrap(t);
            failure = cause;
            if (cause instanceof Exception e) throw e;
            throw new IllegalStateException(cause.toString(), cause); // UnsatisfiedLinkError and friends
        }
    }

    /** Copies the platform's libraries out of the jar once; a complete copy (same sizes) is reused. */
    private static Path unpackNatives(Path jar, Path dir, String platform) throws IOException {
        String prefix = NATIVE_PREFIX + platform + "/";
        Files.createDirectories(dir);
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            List<ZipEntry> libs = new ArrayList<>();
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                ZipEntry entry = e.nextElement();
                String rest = entry.getName().startsWith(prefix) ? entry.getName().substring(prefix.length()) : "";
                if (!entry.isDirectory() && !rest.isEmpty() && rest.indexOf('/') < 0) libs.add(entry); // skips *.dSYM debug bundles
            }
            if (libs.isEmpty()) throw new IOException("the ONNX Runtime jar has no natives for " + platform);
            for (ZipEntry entry : libs) {
                Path target = dir.resolve(entry.getName().substring(prefix.length()));
                if (Files.isRegularFile(target) && Files.size(target) == entry.getSize()) continue;
                Path tmp = dir.resolve(target.getFileName() + ".tmp");
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return dir;
    }

    /** Opens a model on the CPU: {@code threads} intra-op threads that sleep between lines instead of spinning. */
    Session open(Path model, int threads) throws Exception {
        Object options = call(newOptions::newInstance);
        try {
            call(() -> setIntraOpThreads.invoke(options, threads));
            call(() -> setInterOpThreads.invoke(options, 1));
            call(() -> setExecutionMode.invoke(options, sequential));
            call(() -> setOptLevel.invoke(options, allOptimizations));
            // ORT's worker threads busy-wait after each run by default, stealing CPU from the game
            call(() -> addConfigEntry.invoke(options, "session.intra_op.allow_spinning", "0"));
            call(() -> addConfigEntry.invoke(options, "session.inter_op.allow_spinning", "0"));
            return new Session(call(() -> createSession.invoke(env, model.toAbsolutePath().toString(), options)));
        } finally {
            ((AutoCloseable) options).close();
        }
    }

    /** One loaded model. Runs may come from any thread, one at a time. */
    final class Session implements AutoCloseable {
        private final Object session;

        private Session(Object session) {
            this.session = session;
        }

        @SuppressWarnings("unchecked")
        Set<String> inputNames() throws Exception {
            return (Set<String>) call(() -> inputNames.invoke(session));
        }

        @SuppressWarnings("unchecked")
        Set<String> outputNames() throws Exception {
            return (Set<String>) call(() -> outputNames.invoke(session));
        }

        /** Runs the model on int64/float32 inputs ({@code long[]} or {@code float[]} values) and returns one float output. */
        float[] runFloat(Map<String, Object> inputs, Map<String, long[]> shapes, String output) throws Exception {
            Map<String, Object> tensors = new LinkedHashMap<>();
            try {
                for (Map.Entry<String, Object> e : inputs.entrySet()) {
                    long[] shape = shapes.get(e.getKey());
                    Object tensor = e.getValue() instanceof long[] l
                            ? call(() -> longTensor.invoke(null, env, LongBuffer.wrap(l), shape))
                            : call(() -> floatTensor.invoke(null, env, FloatBuffer.wrap((float[]) e.getValue()), shape));
                    tensors.put(e.getKey(), tensor);
                }
                try (AutoCloseable result = (AutoCloseable) call(() -> run.invoke(session, tensors))) {
                    Optional<?> value = (Optional<?>) call(() -> resultGet.invoke(result, output));
                    FloatBuffer data = (FloatBuffer) call(() -> floatBuffer.invoke(value.orElseThrow(() -> new IllegalStateException("model has no output " + output))));
                    float[] out = new float[data.remaining()];
                    data.get(out);
                    return out;
                }
            } finally {
                for (Object t : tensors.values()) ((AutoCloseable) t).close();
            }
        }

        @Override
        public void close() throws Exception {
            ((AutoCloseable) session).close();
        }
    }

    private interface Call<T> {
        T get() throws Exception;
    }

    /** Runs a reflective call, rethrowing what the runtime itself threw (OrtException, ...). */
    private static <T> T call(Call<T> c) throws Exception {
        try {
            return c.get();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) throw ex;
            if (cause instanceof Error err) throw err;
            throw e;
        }
    }

    private static Throwable unwrap(Throwable t) {
        while ((t instanceof InvocationTargetException || t instanceof ExceptionInInitializerError) && t.getCause() != null) t = t.getCause();
        return t;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object constant(Class<?> enumClass, String name) {
        return Enum.valueOf((Class) enumClass, name);
    }
}

package com.aigirlfriend.ai;

import com.aigirlfriend.config.GFConfig;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * API keys never live in source code or the main config. They are read from the environment variable named in
 * the config, or from config/aigirlfriend/secrets.properties (api_key=...), which server owners keep private.
 */
public final class SecretStore {
    private SecretStore() {}

    public static Path secretsFile() {
        return FMLPaths.CONFIGDIR.get().resolve("aigirlfriend").resolve("secrets.properties");
    }

    public static String apiKey() {
        String envName = GFConfig.AI_API_KEY_ENV.get();
        if (envName != null && !envName.isBlank()) {
            String v = System.getenv(envName.trim());
            if (v != null && !v.isBlank()) return v.trim();
        }
        Path f = secretsFile();
        if (Files.isRegularFile(f)) {
            Properties props = new Properties();
            try (Reader r = Files.newBufferedReader(f)) {
                props.load(r);
                String v = props.getProperty("api_key", "").trim();
                if (!v.isEmpty()) return v;
            } catch (IOException ignored) {
            }
        }
        return "";
    }

    /** Creates an empty secrets template (never overwrites an existing file). */
    public static void ensureTemplate() {
        Path f = secretsFile();
        if (Files.exists(f)) return;
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, "# Private credentials for AI Girlfriend Companion. Do not share or commit this file.\n# api_key=your-key-here\napi_key=\n");
        } catch (IOException ignored) {
        }
    }
}

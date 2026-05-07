package br.com.qasuite.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Context7Config {

    private static final String ENV_API_KEY = "CONTEXT7_API_KEY";

    private String apiKey;

    public Context7Config() {
        loadConfig();
    }

    private void loadConfig() {
        Path envPath = Paths.get(".env");
        if (Files.exists(envPath)) {
            try {
                String content = Files.readString(envPath, StandardCharsets.UTF_8);
                if (content.startsWith("\uFEFF")) {
                    content = content.substring(1);
                }
                String[] lines = content.split("\\r?\\n");
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith(ENV_API_KEY + "=")) {
                        this.apiKey = trimmed.substring(ENV_API_KEY.length() + 1).trim();
                        break;
                    }
                }
            } catch (IOException ignored) {
            }
        }

        if (this.apiKey == null || this.apiKey.isEmpty()) {
            this.apiKey = System.getenv(ENV_API_KEY);
        }
    }

    public boolean isValid() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("sua_chave_context7_aqui");
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getMaskedApiKey() {
        if (!isValid()) return "";
        int len = apiKey.length();
        int tail = Math.min(4, len);
        return "********" + apiKey.substring(len - tail);
    }
}

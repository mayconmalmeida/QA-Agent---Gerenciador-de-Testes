package br.com.qasuite.config;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Configuração da API OpenAI
 * Carrega de: .env > config.properties > environment variable
 */
public class OpenAIConfig {

    private static final String CONFIG_FILE = "config/openai.properties";
    private static final String ENV_API_KEY = "OPENAI_API_KEY";
    private static final String ENV_API_URL = "OPENAI_API_URL";

    private String apiKey;
    private String apiUrl;
    private String model;
    private double temperature;
    private int maxTokens;

    public OpenAIConfig() {
        this.apiUrl = "https://api.openai.com/v1/chat/completions";
        this.model = "gpt-4o-mini";
        this.temperature = 0.1;
        this.maxTokens = 2000;
        loadConfig();
    }

    /**
     * Carrega configuração na ordem de prioridade:
     * 1. Arquivo config/openai.properties
     * 2. Arquivo .env
     * 3. Environment variables
     */
    private void loadConfig() {
        // Tenta arquivo de configuração específico
        Path configPath = Paths.get(CONFIG_FILE);
        if (Files.exists(configPath)) {
            try (FileInputStream fis = new FileInputStream(configPath.toFile())) {
                Properties props = new Properties();
                props.load(fis);
                this.apiKey = props.getProperty("api.key", this.apiKey);
                this.apiUrl = props.getProperty("api.url", this.apiUrl);
                this.model = props.getProperty("model", this.model);
                this.temperature = Double.parseDouble(props.getProperty("temperature", String.valueOf(this.temperature)));
                this.maxTokens = Integer.parseInt(props.getProperty("max.tokens", String.valueOf(this.maxTokens)));
                System.out.println("[OpenAIConfig] Config loaded from: " + CONFIG_FILE);
                return;
            } catch (IOException e) {
                System.err.println("[OpenAIConfig] Error loading config file: " + e.getMessage());
            }
        }

        // Tenta arquivo .env
        Path envPath = Paths.get(".env");
        System.out.println("[OpenAIConfig] Procurando .env");
        if (Files.exists(envPath)) {
            try {
                String content = Files.readString(envPath, java.nio.charset.StandardCharsets.UTF_8);
                
                // Remove BOM se presente
                if (content.startsWith("\uFEFF")) {
                    content = content.substring(1);
                }
                
                String[] lines = content.split("\\r?\\n");
                
                for (String line : lines) {
                    if (line.startsWith(ENV_API_KEY + "=")) {
                        this.apiKey = line.substring(ENV_API_KEY.length() + 1).trim();
                    } else if (line.startsWith(ENV_API_URL + "=")) {
                        this.apiUrl = line.substring(ENV_API_URL.length() + 1).trim();
                    }
                }
                if (this.apiKey != null && !this.apiKey.isEmpty()) {
                    System.out.println("[OpenAIConfig] API key loaded from .env");
                    return;
                } else {
                    System.out.println("[OpenAIConfig] API key não encontrada no .env");
                }
            } catch (IOException e) {
                System.err.println("[OpenAIConfig] Error reading .env: " + e.getMessage());
            }
        } else {
            System.out.println("[OpenAIConfig] Arquivo .env não encontrado");
        }

        // Fallback: environment variable
        this.apiKey = System.getenv(ENV_API_KEY);
        String envUrl = System.getenv(ENV_API_URL);
        if (envUrl != null) {
            this.apiUrl = envUrl;
        }

        if (this.apiKey != null && !this.apiKey.isEmpty()) {
            System.out.println("[OpenAIConfig] API key loaded from environment variable");
        } else {
            System.err.println("[OpenAIConfig] WARNING: No API key found!");
        }
    }

    /**
     * Salva configuração no arquivo
     */
    public void saveConfig() throws IOException {
        Path configDir = Paths.get("config");
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir);
        }

        Properties props = new Properties();
        props.setProperty("api.key", this.apiKey != null ? this.apiKey : "");
        props.setProperty("api.url", this.apiUrl);
        props.setProperty("model", this.model);
        props.setProperty("temperature", String.valueOf(this.temperature));
        props.setProperty("max.tokens", String.valueOf(this.maxTokens));

        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "OpenAI Configuration");
        }
        System.out.println("[OpenAIConfig] Config saved to: " + CONFIG_FILE);
    }

    /**
     * Verifica se a configuração é válida
     */
    public boolean isValid() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("your-api-key-here");
    }

    // Getters e Setters
    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }
}

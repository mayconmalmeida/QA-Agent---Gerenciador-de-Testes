package br.com.qasuite.config;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Carregador de configurações do framework QA Agent.
 * Lê config.properties e sobrescreve com variáveis de ambiente (.env ou CI).
 */
public class ConfigLoader {

    private static final Properties properties = new Properties();
    private static final Dotenv dotenv;
    private static final Map<String, String> envMap = new HashMap<>();
    private static boolean loaded = false;

    static {
        // Tenta carregar .env se existir
        Dotenv tempDotenv = null;
        try {
            Path envPath = Paths.get(".env");
            if (Files.exists(envPath)) {
                // Lê manualmente para remover BOM
                String content = new String(Files.readAllBytes(envPath), StandardCharsets.UTF_8);
                // Remove BOM se presente
                if (content.startsWith("\uFEFF")) {
                    content = content.substring(1);
                }
                // Parse manual das linhas
                for (String line : content.split("\n")) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        String key = line.substring(0, eq).trim();
                        String value = line.substring(eq + 1).trim();
                        envMap.put(key, value);
                    }
                }
                System.out.println("[ConfigLoader] .env carregado manualmente com " + envMap.size() + " variáveis");
            }
        } catch (Exception e) {
            System.out.println("[ConfigLoader] Erro ao carregar .env: " + e.getMessage());
        }
        dotenv = null; // Não usamos mais o Dotenv
        
        // Carrega configurações
        loadConfig();
    }

    private static void loadConfig() {
        if (loaded) return;

        // Primeiro tenta carregar de config.properties na raiz (onde o painel web salva)
        Path rootConfigPath = Paths.get("config.properties");
        if (Files.exists(rootConfigPath)) {
            try (InputStream input = Files.newInputStream(rootConfigPath)) {
                properties.load(input);
                System.out.println("[ConfigLoader] config.properties carregado da raiz: " + rootConfigPath.toAbsolutePath());
            } catch (IOException e) {
                System.err.println("[ConfigLoader] Erro ao carregar config.properties da raiz: " + e.getMessage());
            }
        } else {
            // Fallback: carrega do classpath (src/test/resources)
            try (InputStream input = ConfigLoader.class.getClassLoader()
                    .getResourceAsStream("config.properties")) {
                if (input != null) {
                    properties.load(input);
                    System.out.println("[ConfigLoader] config.properties carregado do classpath");
                } else {
                    System.err.println("[ConfigLoader] Não foi possível encontrar config.properties");
                }
            } catch (IOException e) {
                System.err.println("[ConfigLoader] Erro ao carregar config.properties: " + e.getMessage());
            }
        }

        loaded = true;
    }

    /**
     * Obtém uma propriedade, priorizando: variável de ambiente > .env > config.properties
     */
    public static String get(String key) {
        // 1. Verifica variável de ambiente do sistema (para CI)
        String envValue = System.getenv(key.toUpperCase().replace(".", "_"));
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }

        // 2. Verifica .env (carregado manualmente)
        String dotenvValue = envMap.get(key.toUpperCase().replace(".", "_"));
        if (dotenvValue != null && !dotenvValue.isEmpty()) {
            return dotenvValue;
        }

        // 3. Retorna do config.properties
        return properties.getProperty(key);
    }

    /**
     * Obtém uma propriedade com valor padrão
     */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Obtém propriedade como boolean
     */
    public static boolean getBoolean(String key) {
        String value = get(key);
        return Boolean.parseBoolean(value);
    }

    /**
     * Obtém propriedade como boolean com valor padrão
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * Obtém propriedade como int
     */
    public static int getInt(String key) {
        String value = get(key);
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("[ConfigLoader] Valor inválido para inteiro: " + key + "=" + value);
            return 0;
        }
    }

    /**
     * Obtém propriedade como int com valor padrão
     */
    public static int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ==================== Propriedades específicas ====================

    public static String getBaseUrl() {
        return get("base.url", "https://example.test/login");
    }

    public static String getAmbiente() {
        return get("environment", "test");
    }

    public static String getUsuarioPadrao() {
        return get("user.username", "admin");
    }

    public static String getSenhaPadrao() {
        return get("user.password", "admin");
    }

    public static String getBrowser() {
        return get("browser.type", "chromium");
    }

    public static boolean isHeadless() {
        return getBoolean("headless", false);
    }

    public static int getSlowMotionMs() {
        return getInt("slow.motion.ms", 200);
    }

    public static int getTimeoutPadraoMs() {
        return getInt("timeout.default", 30) * 1000; // Convert seconds to milliseconds
    }

    public static int getNavigationTimeoutMs() {
        return getInt("timeout.navigation", 20) * 1000; // 20s default for navigation
    }

    public static boolean isScreenshotEmFalha() {
        return getBoolean("screenshot.em.falha", true);
    }

    public static boolean isScreenshotEmCadaPasso() {
        return getBoolean("screenshot.em.cada.passo", false);
    }

    public static String getScreenshotPasta() {
        return get("screenshot.pasta", "output/screenshots");
    }

    public static String getRelatorioOutput() {
        return get("relatorio.output", "output/reports/relatorio.html");
    }

    public static String getRelatorioTitulo() {
        return get("relatorio.titulo", "QA Agent - Relatório de Testes");
    }

    public static String getRelatorioAmbiente() {
        return get("relatorio.ambiente", "Testes");
    }

    public static boolean isRelatorioAbrirAposExecucao() {
        return getBoolean("relatorio.abrir.apos.execucao", true);
    }

    public static String getAnthropicModel() {
        return get("anthropic.model", "claude-sonnet-4-20250514");
    }

    public static String getAnthropicApiUrl() {
        return get("anthropic.api.url", "https://api.anthropic.com/v1/messages");
    }

    public static String getAnthropicApiKey() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isEmpty()) {
            key = envMap.get("ANTHROPIC_API_KEY");
        }
        return key;
    }
}

package br.com.qasuite.config;

import com.microsoft.playwright.*;

import java.nio.file.Paths;

/**
 * Fábrica de browsers para o Playwright.
 * Gerencia a criação e configuração de instâncias de browser.
 */
public class BrowserFactory {

    private static Playwright playwright;
    private static Browser browser;

    /**
     * Inicializa o Playwright e o browser conforme configuração
     */
    public static void initialize() {
        if (playwright != null) {
            return;
        }

        // Registra shutdown hook para fechar recursos quando JVM terminar
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[BrowserFactory] Shutdown hook acionado - fechando recursos...");
            close();
        }));

        String browserType = ConfigLoader.getBrowser();
        boolean headless = ConfigLoader.isHeadless();
        int slowMo = ConfigLoader.getSlowMotionMs();

        System.out.println("[BrowserFactory] Inicializando browser: " + browserType + " (headless=" + headless + ")");

        playwright = Playwright.create();

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setSlowMo(slowMo);

        switch (browserType.toLowerCase()) {
            case "firefox":
                browser = playwright.firefox().launch(launchOptions);
                break;
            case "webkit":
                browser = playwright.webkit().launch(launchOptions);
                break;
            case "chromium":
            default:
                browser = playwright.chromium().launch(launchOptions);
                break;
        }

        System.out.println("[BrowserFactory] Browser inicializado com sucesso");
    }

    /**
     * Cria uma nova página (aba) no browser
     */
    public static Page newPage() {
        if (browser == null) {
            initialize();
        }

        Browser.NewPageOptions pageOptions = new Browser.NewPageOptions()
                .setViewportSize(1920, 1080);

        Page page = browser.newPage(pageOptions);
        
        // Configura timeouts separados
        int timeout = ConfigLoader.getTimeoutPadraoMs(); // 5s para operações
        int navigationTimeout = ConfigLoader.getNavigationTimeoutMs(); // 20s para navegação
        page.setDefaultTimeout(timeout);
        page.setDefaultNavigationTimeout(navigationTimeout);

        return page;
    }

    /**
     * Cria um novo contexto de browser (isolado)
     */
    public static BrowserContext newContext() {
        if (browser == null) {
            initialize();
        }
        return browser.newContext();
    }

    /**
     * Fecha o browser e libera recursos
     */
    public static void close() {
        // Fecha browser primeiro com timeout
        if (browser != null) {
            try {
                browser.close();
                System.out.println("[BrowserFactory] Browser fechado");
            } catch (Exception e) {
                System.err.println("[BrowserFactory] Erro ao fechar browser: " + e.getMessage());
            }
            browser = null;
        }
        // Fecha playwright independente do browser
        if (playwright != null) {
            try {
                playwright.close();
                System.out.println("[BrowserFactory] Playwright encerrado");
            } catch (Exception e) {
                System.err.println("[BrowserFactory] Erro ao fechar playwright: " + e.getMessage());
            }
            playwright = null;
        }
    }

    /**
     * Verifica se o browser está inicializado
     */
    public static boolean isInitialized() {
        return browser != null && playwright != null;
    }
}

package br.com.sinncosaude.config;

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
        
        // Configura timeout padrão
        int timeout = ConfigLoader.getTimeoutPadraoMs();
        page.setDefaultTimeout(timeout);
        page.setDefaultNavigationTimeout(timeout);

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
        if (browser != null) {
            browser.close();
            browser = null;
            System.out.println("[BrowserFactory] Browser fechado");
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
            System.out.println("[BrowserFactory] Playwright encerrado");
        }
    }

    /**
     * Verifica se o browser está inicializado
     */
    public static boolean isInitialized() {
        return browser != null && playwright != null;
    }
}

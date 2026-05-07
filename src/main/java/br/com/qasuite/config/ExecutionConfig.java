package br.com.qasuite.config;

/**
 * Configuração de execução de testes
 * Permite definir modo visual (headful) ou headless
 */
public class ExecutionConfig {

    private boolean headless = true;
    private boolean recordVideo = true;
    private boolean takeScreenshots = true;
    private int slowMo = 0; // milliseconds de delay entre ações (para modo visual)
    private String browser = "chromium";
    private int viewportWidth = 1920;
    private int viewportHeight = 1080;

    public ExecutionConfig() {}

    public ExecutionConfig(boolean headless) {
        this.headless = headless;
        // Em modo visual, adiciona delay para usuário acompanhar
        if (!headless) {
            this.slowMo = 500; // 500ms entre ações
        }
    }

    // Modos predefinidos
    public static ExecutionConfig visualMode() {
        ExecutionConfig config = new ExecutionConfig(false);
        config.setRecordVideo(true);
        config.setTakeScreenshots(true);
        config.setSlowMo(500);
        return config;
    }

    public static ExecutionConfig headlessMode() {
        ExecutionConfig config = new ExecutionConfig(true);
        config.setRecordVideo(false);
        config.setTakeScreenshots(true);
        config.setSlowMo(0);
        return config;
    }

    public static ExecutionConfig debugMode() {
        ExecutionConfig config = new ExecutionConfig(false);
        config.setRecordVideo(true);
        config.setTakeScreenshots(true);
        config.setSlowMo(1000); // 1s entre ações para debug
        return config;
    }

    // Getters e Setters
    public boolean isHeadless() {
        return headless;
    }

    public void setHeadless(boolean headless) {
        this.headless = headless;
        // Ajusta slowMo automaticamente
        if (!headless && this.slowMo == 0) {
            this.slowMo = 500;
        } else if (headless) {
            this.slowMo = 0;
        }
    }

    public boolean isRecordVideo() {
        return recordVideo;
    }

    public void setRecordVideo(boolean recordVideo) {
        this.recordVideo = recordVideo;
    }

    public boolean isTakeScreenshots() {
        return takeScreenshots;
    }

    public void setTakeScreenshots(boolean takeScreenshots) {
        this.takeScreenshots = takeScreenshots;
    }

    public int getSlowMo() {
        return slowMo;
    }

    public void setSlowMo(int slowMo) {
        this.slowMo = slowMo;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public int getViewportWidth() {
        return viewportWidth;
    }

    public void setViewportWidth(int viewportWidth) {
        this.viewportWidth = viewportWidth;
    }

    public int getViewportHeight() {
        return viewportHeight;
    }

    public void setViewportHeight(int viewportHeight) {
        this.viewportHeight = viewportHeight;
    }

    public String getModeDescription() {
        if (headless) {
            return "Rápido (Headless)";
        } else if (slowMo >= 1000) {
            return "Debug (Lento)";
        } else {
            return "Visual (Acompanhar)";
        }
    }

    @Override
    public String toString() {
        return String.format("ExecutionConfig{headless=%s, browser=%s, slowMo=%dms, video=%s, screenshots=%s}",
                headless, browser, slowMo, recordVideo, takeScreenshots);
    }
}

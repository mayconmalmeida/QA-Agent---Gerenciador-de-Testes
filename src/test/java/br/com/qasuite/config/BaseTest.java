package br.com.qasuite.config;

import br.com.qasuite.core.ReportBuilder;
import br.com.qasuite.core.SmartLogin;
import br.com.qasuite.core.SmartPage;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Classe base para todos os testes de automação.
 * Gerencia o ciclo de vida do browser, login automático e captura de screenshots.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 10, unit = TimeUnit.MINUTES)
public abstract class BaseTest {

    protected Page page;
    protected SmartLogin smartLogin;

    private static final ThreadLocal<String> currentTestName = new ThreadLocal<>();
    private static final ThreadLocal<String> currentTestStatus = new ThreadLocal<>();
    private static final ThreadLocal<String> currentScreenshotPath = new ThreadLocal<>();
    private static final ThreadLocal<Long> testStartTime = new ThreadLocal<>();

    @RegisterExtension
    final BeforeEachCallback beforeEachCallback = context -> {
        // Timer será iniciado no @BeforeEach após o setup
        currentTestName.set(context.getDisplayName());
        currentTestStatus.set("RUNNING");
        currentScreenshotPath.set(null);
    };

    @RegisterExtension
    final AfterEachCallback afterEachCallback = context -> {
        long duration = System.currentTimeMillis() - testStartTime.get();
        
        if (context.getExecutionException().isPresent()) {
            currentTestStatus.set("FAILED");
            // Captura screenshot em caso de falha
            if (ConfigLoader.isScreenshotEmFalha() && page != null) {
                String screenshotPath = capturarScreenshotFalha(context.getDisplayName());
                currentScreenshotPath.set(screenshotPath);
            }
        } else if (context.getExecutionException().isEmpty()) {
            currentTestStatus.set("PASSED");
        }

        // Registra resultado para o relatório
        ReportBuilder.registrarResultado(
                context.getDisplayName(),
                getClass().getSimpleName(),
                getTipoTeste(),
                currentTestStatus.get(),
                duration,
                currentScreenshotPath.get(),
                context.getExecutionException().map(Throwable::getMessage).orElse(null)
        );
    };

    /**
     * Retorna o tipo de teste (smoke, regression, critico) baseado nas tags da classe
     */
    protected abstract String getTipoTeste();

    @BeforeAll
    void beforeAll() {
        System.out.println("[BaseTest] Inicializando ambiente de teste...");
        BrowserFactory.initialize();
    }

    @BeforeEach
    void beforeEach() {
        System.out.println("[BaseTest] Preparando novo teste: " + currentTestName.get());
        
        // Cria nova página
        page = BrowserFactory.newPage();
        
        // Navega para a URL base
        String baseUrl = ConfigLoader.getBaseUrl();
        System.out.println("[BaseTest] ==========================================");
        System.out.println("[BaseTest] URL BASE CARREGADA: " + baseUrl);
        System.out.println("[BaseTest] ==========================================");
        if (baseUrl == null || baseUrl.isEmpty()) {
            System.err.println("[BaseTest] ERRO: URL base está vazia!");
            throw new IllegalStateException("base.url não configurada em config.properties");
        }
        
        try {
            System.out.println("[BaseTest] Navegando para URL (timeout 30s)...");
            page.navigate(baseUrl, new Page.NavigateOptions().setTimeout(30000));
            System.out.println("[BaseTest] Navegação concluída");
        } catch (Exception e) {
            System.err.println("[BaseTest] ERRO ao navegar para URL: " + e.getMessage());
            System.err.println("[BaseTest] Verifique se a URL está acessível: " + baseUrl);
            throw new RuntimeException("Não foi possível acessar a URL: " + baseUrl, e);
        }
        
        // Realiza login automático usando SmartLogin (genérico)
        realizarLogin();
        
        // Aguarda carregamento da tela principal (navbar)
        aguardarTelaPrincipal();
        
        System.out.println("[BaseTest] Setup concluído com sucesso");
        
        // Inicia o timer após o setup completo (login + navegação)
        testStartTime.set(System.currentTimeMillis());
    }

    @AfterEach
    void afterEach() {
        System.out.println("[BaseTest] Finalizando teste...");
        if (page != null) {
            page.close();
            page = null;
        }
    }

    @AfterAll
    void afterAll() {
        System.out.println("[BaseTest] Finalizando suite de testes...");
        
        // Gera relatório HTML
        try {
            ReportBuilder.gerar();
        } catch (Exception e) {
            System.err.println("[BaseTest] Erro ao gerar relatório: " + e.getMessage());
        }
        
        BrowserFactory.close();
        System.out.println("[BaseTest] Suite finalizada");
    }

    /**
     * Realiza login automático no sistema usando SmartLogin (genérico)
     * Detecta campos de login automaticamente - funciona com qualquer sistema
     */
    private void realizarLogin() {
        String usuario = ConfigLoader.getUsuarioPadrao();
        String senha = ConfigLoader.getSenhaPadrao();
        
        System.out.println("[BaseTest] Iniciando login automático com SmartLogin...");
        
        smartLogin = new SmartLogin(page);
        boolean loginSucesso = smartLogin.loginCompleto(page.url(), usuario, senha);
        
        if (!loginSucesso) {
            throw new RuntimeException("Falha no login automático. Verifique credenciais e URL.");
        }
        
        System.out.println("[BaseTest] Login realizado com usuário: " + usuario);
    }

    /**
     * Aguarda o carregamento da tela principal (navbar)
     */
    private void aguardarTelaPrincipal() {
        try {
            // Aguarda apenas DOM carregar (mais rápido que NETWORKIDLE)
            page.waitForLoadState(LoadState.DOMCONTENTLOADED, 
                    new Page.WaitForLoadStateOptions()
                            .setTimeout(ConfigLoader.getTimeoutPadraoMs()));
            System.out.println("[BaseTest] Tela principal carregada (DOM ready)");
        } catch (Exception e) {
            System.out.println("[BaseTest] Aviso: Não foi possível confirmar carregamento da tela principal: " + e.getMessage());
        }
    }

    /**
     * Captura screenshot de uma falha
     */
    private String capturarScreenshotFalha(String testName) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String nomeArquivo = "FALHA_" + testName.replaceAll("[^a-zA-Z0-9]", "_") + "_" + timestamp + ".png";
            
            Path pastaScreenshots = Paths.get(ConfigLoader.getScreenshotPasta());
            if (!Files.exists(pastaScreenshots)) {
                Files.createDirectories(pastaScreenshots);
            }
            
            Path caminhoCompleto = pastaScreenshots.resolve(nomeArquivo);
            
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(caminhoCompleto)
                    .setFullPage(true));
            
            System.out.println("[BaseTest] Screenshot de falha salvo: " + caminhoCompleto);
            return caminhoCompleto.toString();
            
        } catch (IOException e) {
            System.err.println("[BaseTest] Erro ao salvar screenshot: " + e.getMessage());
            return null;
        }
    }

    // ==================== Métodos utilitários protegidos ====================

    protected void aguardar(int milissegundos) {
        try {
            Thread.sleep(milissegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected void capturarScreenshot(String nome) {
        if (!ConfigLoader.isScreenshotEmCadaPasso()) return;
        
        try {
            Path pastaScreenshots = Paths.get(ConfigLoader.getScreenshotPasta());
            if (!Files.exists(pastaScreenshots)) {
                Files.createDirectories(pastaScreenshots);
            }
            
            String nomeArquivo = nome + "_" + System.currentTimeMillis() + ".png";
            Path caminhoCompleto = pastaScreenshots.resolve(nomeArquivo);
            
            page.screenshot(new Page.ScreenshotOptions().setPath(caminhoCompleto));
            
        } catch (IOException e) {
            System.err.println("[BaseTest] Erro ao capturar screenshot: " + e.getMessage());
        }
    }
}

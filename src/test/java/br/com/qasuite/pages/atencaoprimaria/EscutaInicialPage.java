package br.com.qasuite.pages.atencaoprimaria;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

/**
 * Page Object para o fluxo de Escuta Inicial em Atenção Primária.
 */
public class EscutaInicialPage {

    private final Page page;
    private static final int TIMEOUT_MS = 10000; // 10 segundos timeout máximo

    // Seletores - ajustar conforme a aplicação real (DevExpress)
    private final String MENU_ATENCAO_PRIMARIA = "text=Atendimento da Atenção Primária";
    private final String SUBMENU_ESCUTA_INICIAL = "text=Escuta Inicial";
    private final String BOTAO_INSERIR = "button:has-text('Inserir'), .dx-button:has-text('Inserir')";
    private final String BOTAO_PROXIMO_PACIENTE = "button:has-text('Próximo Paciente'), .dx-button:has-text('Próximo Paciente')";
    private final String CAMPO_PESO = "input[placeholder*='Peso'], .dx-texteditor-input[aria-label*='peso' i]";
    private final String CAMPO_ALTURA = "input[placeholder*='Altura'], .dx-texteditor-input[aria-label*='altura' i]";
    private final String CAMPO_SUBJETIVO = "textarea[placeholder*='Subjetivo'], .dx-texteditor-input[aria-label*='subjetivo' i]";
    private final String BOTAO_FINALIZAR_ATENDIMENTO = "button:has-text('Finalizar Atendimento'), .dx-button:has-text('Finalizar')";
    private final String MENSAGEM_SUCESSO = ".dx-toast-message, .mensagem-sucesso, .success-message";

    public EscutaInicialPage(Page page) {
        this.page = page;
    }

    /**
     * Navega para o menu Atenção Primária > Escuta Inicial
     */
    public void navegarParaEscutaInicial() {
        System.out.println("[EscutaInicialPage] Navegando para Atenção Primária > Escuta Inicial");
        
        try {
            // Clica no menu Atenção Primária
            Locator menuAtencaoPrimaria = page.locator(MENU_ATENCAO_PRIMARIA).first();
            System.out.println("[EscutaInicialPage] Aguardando menu Atenção Primária...");
            menuAtencaoPrimaria.waitFor(new Locator.WaitForOptions().setTimeout(TIMEOUT_MS));
            System.out.println("[EscutaInicialPage] Menu encontrado, clicando...");
            menuAtencaoPrimaria.click();
            
            // Clica no submenu Escuta Inicial
            Locator submenuEscutaInicial = page.locator(SUBMENU_ESCUTA_INICIAL).first();
            System.out.println("[EscutaInicialPage] Aguardando submenu Escuta Inicial...");
            submenuEscutaInicial.waitFor(new Locator.WaitForOptions().setTimeout(TIMEOUT_MS));
            System.out.println("[EscutaInicialPage] Submenu encontrado, clicando...");
            submenuEscutaInicial.click();
            
            // Aguarda carregamento da página (timeout de 5s apenas)
            System.out.println("[EscutaInicialPage] Aguardando carregamento da página...");
            page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(5000));
            
            System.out.println("[EscutaInicialPage] Navegação concluída");
        } catch (Exception e) {
            System.out.println("[EscutaInicialPage] ERRO na navegação: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Erro ao navegar para Escuta Inicial: " + e.getMessage(), e);
        }
    }

    /**
     * Clica em Próximo Paciente
     */
    public void clicarProximoPaciente() {
        System.out.println("[EscutaInicialPage] Clicando em Próximo Paciente");
        try {
            Locator botaoProximo = page.locator(BOTAO_PROXIMO_PACIENTE).first();
            botaoProximo.waitFor(new Locator.WaitForOptions().setTimeout(TIMEOUT_MS));
            botaoProximo.click();
            System.out.println("[EscutaInicialPage] Botão Próximo Paciente clicado");
        } catch (Exception e) {
            System.out.println("[EscutaInicialPage] Botão Próximo Paciente não encontrado, continuando...");
        }
    }

    /**
     * Preenche os campos da escuta inicial
     */
    public void preencherEscutaInicial() {
        System.out.println("[EscutaInicialPage] Preenchendo campos da escuta inicial");
        
        try {
            // Preenche Peso
            Locator campoPeso = page.locator(CAMPO_PESO).first();
            campoPeso.waitFor(new Locator.WaitForOptions().setTimeout(TIMEOUT_MS));
            campoPeso.fill("80");
            System.out.println("[EscutaInicialPage] Peso preenchido: 80");
            
            // Preenche Altura
            Locator campoAltura = page.locator(CAMPO_ALTURA).first();
            campoAltura.fill("170");
            System.out.println("[EscutaInicialPage] Altura preenchida: 170");
            
            // Preenche Subjetivo
            Locator campoSubjetivo = page.locator(CAMPO_SUBJETIVO).first();
            campoSubjetivo.fill("Teste Teste");
            System.out.println("[EscutaInicialPage] Subjetivo preenchido: Teste Teste");
            
        } catch (Exception e) {
            System.out.println("[EscutaInicialPage] ERRO ao preencher campos: " + e.getMessage());
            throw new RuntimeException("Erro ao preencher escuta inicial", e);
        }
    }

    /**
     * Clica em Finalizar Atendimento
     */
    public void clicarFinalizarAtendimento() {
        System.out.println("[EscutaInicialPage] Clicando em Finalizar Atendimento");
        try {
            Locator botaoFinalizar = page.locator(BOTAO_FINALIZAR_ATENDIMENTO).first();
            botaoFinalizar.waitFor(new Locator.WaitForOptions().setTimeout(TIMEOUT_MS));
            botaoFinalizar.click();
            System.out.println("[EscutaInicialPage] Atendimento finalizado");
        } catch (Exception e) {
            System.out.println("[EscutaInicialPage] Botão Finalizar não encontrado: " + e.getMessage());
        }
    }

    /**
     * Executa o fluxo completo de escuta inicial
     */
    public void realizarEscutaInicial() {
        System.out.println("[EscutaInicialPage] Iniciando fluxo de Escuta Inicial");
        
        clicarProximoPaciente();
        preencherEscutaInicial();
        clicarFinalizarAtendimento();
        
        System.out.println("[EscutaInicialPage] Fluxo de Escuta Inicial concluído");
    }

    /**
     * Verifica se há mensagem de sucesso
     */
    public boolean temMensagemSucesso() {
        try {
            Locator mensagemSucesso = page.locator(MENSAGEM_SUCESSO).first();
            return mensagemSucesso.isVisible();
        } catch (Exception e) {
            return false;
        }
    }
}

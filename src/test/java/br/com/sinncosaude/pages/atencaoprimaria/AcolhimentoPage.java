package br.com.sinncosaude.pages.atencaoprimaria;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

/**
 * Page Object para o fluxo de Acolhimento em Atenção Primária.
 */
public class AcolhimentoPage {

    private final Page page;

    // Seletores - ajustar conforme a aplicação real (DevExpress)
    private final String MENU_ATENCAO_PRIMARIA = "text=Atendimento da Atenção Primária";
    private final String SUBMENU_ACOLHIMENTO = "text=Acolhimento";
    private final String BOTAO_INSERIR = ".dx-button-success:has-text('Inserir')";
    private final String CAMPO_BUSCA_PACIENTE = ".dx-lookup-field";
    private final String CHECKBOX_CONSULTA_MEDICA = ".dx-checkbox:has-text('Consulta Médica')";
    private final String BOTAO_FINALIZAR = ".dx-button-success:has-text('Finalizar')";
    private final String MENSAGEM_SUCESSO = ".mensagem-sucesso, .success-message, .alert-success, [role='alert']:has-text('sucesso' i)";

    public AcolhimentoPage(Page page) {
        this.page = page;
    }

    /**
     * Navega para o menu Atenção Primária > Acolhimento
     */
    public void navegarParaAcolhimento() {
        System.out.println("[AcolhimentoPage] Navegando para Atenção Primária > Acolhimento");
        
        try {
            // Clica no menu Atenção Primária
            Locator menuAtencaoPrimaria = page.locator(MENU_ATENCAO_PRIMARIA).first();
            menuAtencaoPrimaria.waitFor();
            menuAtencaoPrimaria.click();
            
            // Clica no submenu Acolhimento (aguarda automaticamente)
            Locator submenuAcolhimento = page.locator(SUBMENU_ACOLHIMENTO).first();
            submenuAcolhimento.waitFor();
            submenuAcolhimento.click();
            
            // Aguarda carregamento da página
            page.waitForLoadState(LoadState.NETWORKIDLE);
            
            System.out.println("[AcolhimentoPage] Navegação concluída");
        } catch (Exception e) {
            System.out.println("[AcolhimentoPage] Erro na navegação: " + e.getMessage());
            throw new RuntimeException("Erro ao navegar para Acolhimento", e);
        }
    }

    /**
     * Clica no botão Inserir para novo acolhimento
     */
    public void clicarInserir() {
        System.out.println("[AcolhimentoPage] Clicando em Inserir");
        Locator botaoInserir = page.locator(BOTAO_INSERIR).first();
        botaoInserir.waitFor();
        botaoInserir.click();
    }

    /**
     * Seleciona o primeiro paciente disponível na lista (DevExpress lookup)
     */
    public void selecionarPrimeiroPaciente() {
        System.out.println("[AcolhimentoPage] Selecionando primeiro paciente disponível");
        Locator campoBusca = page.locator(CAMPO_BUSCA_PACIENTE).first();
        campoBusca.waitFor();
        
        // Clica no lookup para abrir o dropdown
        campoBusca.click();
        
        // Aguarda dropdown carregar (timeout de 3s)
        page.waitForTimeout(800);
        
        // Usa seta para baixo e Enter para selecionar o primeiro item
        page.keyboard().press("ArrowDown");
        page.keyboard().press("Enter");
    }

    /**
     * Marca o checkbox de Consulta Médica (DevExpress checkbox)
     */
    public void marcarConsultaMedica() {
        System.out.println("[AcolhimentoPage] Marcando Consulta Médica");
        Locator checkbox = page.locator(CHECKBOX_CONSULTA_MEDICA).first();
        checkbox.waitFor();
        
        // DevExpress checkbox - click on the container to toggle
        Locator checkboxContainer = checkbox.locator(".dx-checkbox-container");
        if (checkboxContainer.isVisible()) {
            checkboxContainer.click();
        } else {
            checkbox.click();
        }
    }

    /**
     * Clica no botão Finalizar
     */
    public void clicarFinalizar() {
        System.out.println("[AcolhimentoPage] Clicando em Finalizar");
        Locator botaoFinalizar = page.locator(BOTAO_FINALIZAR).first();
        botaoFinalizar.waitFor();
        botaoFinalizar.click();
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

    /**
     * Obtém o texto da mensagem de sucesso
     */
    public String getMensagemSucesso() {
        Locator mensagemSucesso = page.locator(MENSAGEM_SUCESSO).first();
        if (mensagemSucesso.isVisible()) {
            return mensagemSucesso.textContent();
        }
        return null;
    }

    /**
     * Executa o fluxo completo de acolhimento
     */
    public void realizarAcolhimento() {
        navegarParaAcolhimento();
        clicarInserir();
        selecionarPrimeiroPaciente();
        marcarConsultaMedica();
        clicarFinalizar();
    }
}

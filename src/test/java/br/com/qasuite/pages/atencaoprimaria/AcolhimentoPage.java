package br.com.qasuite.pages.atencaoprimaria;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

/**
 * Page Object para o fluxo de Acolhimento em Atenção Primária.
 */
public class AcolhimentoPage {

    private final Page page;
    private static final int TIMEOUT_MS = 10000; // 10 segundos timeout máximo

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
            resolverModalUnidadeSePresente();

            if (clicarPrimeiroVisivel(
                    ".dx-treeview-item:has-text('Acolhimento'), " +
                    "[role='treeitem']:has-text('Acolhimento'), " +
                    "a:has-text('Acolhimento'), " +
                    "button:has-text('Acolhimento'), " +
                    SUBMENU_ACOLHIMENTO)) {
                page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(5000));
                System.out.println("[AcolhimentoPage] Navegacao concluida");
                return;
            }
            // Clica no menu Atenção Primária
            Locator menuAtencaoPrimaria = page.locator(MENU_ATENCAO_PRIMARIA).first();
            System.out.println("[AcolhimentoPage] Aguardando menu Atenção Primária...");
            menuAtencaoPrimaria.waitFor(new Locator.WaitForOptions().setTimeout(TIMEOUT_MS));
            System.out.println("[AcolhimentoPage] Menu encontrado, clicando...");
            menuAtencaoPrimaria.click();
            
            // Clica no submenu Acolhimento
            Locator submenuAcolhimento = page.locator(SUBMENU_ACOLHIMENTO).first();
            System.out.println("[AcolhimentoPage] Aguardando submenu Acolhimento...");
            submenuAcolhimento.waitFor(new Locator.WaitForOptions().setTimeout(TIMEOUT_MS));
            System.out.println("[AcolhimentoPage] Submenu encontrado, clicando...");
            submenuAcolhimento.click();
            
            // Aguarda carregamento da página (timeout de 5s apenas)
            System.out.println("[AcolhimentoPage] Aguardando carregamento da página...");
            page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(5000));
            
            System.out.println("[AcolhimentoPage] Navegação concluída");
        } catch (Exception e) {
            System.out.println("[AcolhimentoPage] ERRO na navegação: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Erro ao navegar para Acolhimento: " + e.getMessage(), e);
        }
    }

    /**
     * Clica no botão Inserir para novo acolhimento
     */
    private boolean clicarPrimeiroVisivel(String seletor) {
        try {
            Locator candidatos = page.locator(seletor);
            int count = candidatos.count();
            for (int i = 0; i < Math.min(count, 20); i++) {
                Locator candidato = candidatos.nth(i);
                if (candidato.isVisible()) {
                    System.out.println("[AcolhimentoPage] Clicando em Acolhimento");
                    candidato.click();
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void resolverModalUnidadeSePresente() {
        try {
            if (!page.locator("text=CNES").first().isVisible()) {
                return;
            }

            System.out.println("[AcolhimentoPage] Modal de unidade detectado; selecionando unidade");

            Locator busca = page.locator("input[placeholder*='Buscar'], [role='textbox'], input[type='text']").first();
            if (busca.isVisible()) {
                busca.click();
                busca.clear();
                busca.fill("unidade para uso interno");
                page.waitForTimeout(1000);
            }

            Locator unidade = page.locator(
                ".cursor-pointer:has-text('UNIDADE PARA USO INTERNO'), " +
                "[class*='cursor-pointer']:has-text('UNIDADE PARA USO INTERNO'), " +
                ".cursor-pointer:has-text('CNES'), " +
                "[class*='cursor-pointer']:has-text('CNES'), " +
                "[role='option']:has-text('CNES')"
            ).first();

            if (unidade.isVisible()) {
                unidade.scrollIntoViewIfNeeded();
                unidade.click(new Locator.ClickOptions().setTimeout(8000));
                page.waitForTimeout(500);
            }

            Locator continuar = page.locator("button:has-text('Continuar'), [role='button']:has-text('Continuar')").first();
            if (continuar.isVisible()) {
                continuar.click(new Locator.ClickOptions().setTimeout(8000));
                page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));
                page.waitForTimeout(1500);
            }
        } catch (Exception e) {
            System.out.println("[AcolhimentoPage] Aviso ao resolver modal de unidade: " + e.getMessage());
        }
    }

    public void clicarInserir() {
        System.out.println("[AcolhimentoPage] Clicando em Inserir");
        Locator botaoInserir = page.locator(BOTAO_INSERIR).first();
        botaoInserir.waitFor(new Locator.WaitForOptions().setTimeout(TIMEOUT_MS));
        botaoInserir.click();
        System.out.println("[AcolhimentoPage] Botão Inserir clicado");
    }

    /**
     * Seleciona o primeiro paciente disponível na lista (DevExpress lookup)
     */
    public void selecionarPrimeiroPaciente() {
        System.out.println("[AcolhimentoPage] Selecionando primeiro paciente disponível");
        Locator campoBusca = page.locator(CAMPO_BUSCA_PACIENTE).first();
        campoBusca.waitFor(new Locator.WaitForOptions().setTimeout(TIMEOUT_MS));
        
        // Clica no lookup para abrir o dropdown
        System.out.println("[AcolhimentoPage] Abrindo lookup de pacientes...");
        campoBusca.click();
        
        // Aguarda dropdown carregar
        page.waitForTimeout(800);
        
        // Usa seta para baixo e Enter para selecionar o primeiro item
        System.out.println("[AcolhimentoPage] Selecionando primeiro paciente...");
        page.keyboard().press("ArrowDown");
        page.keyboard().press("Enter");
        System.out.println("[AcolhimentoPage] Paciente selecionado");
    }

    /**
     * Marca o checkbox de Consulta Médica (DevExpress checkbox)
     */
    public void marcarConsultaMedica() {
        System.out.println("[AcolhimentoPage] Marcando Consulta Médica");
        Locator checkbox = page.locator(CHECKBOX_CONSULTA_MEDICA).first();
        checkbox.waitFor(new Locator.WaitForOptions().setTimeout(TIMEOUT_MS));
        
        // DevExpress checkbox - click on the container to toggle
        Locator checkboxContainer = checkbox.locator(".dx-checkbox-container");
        System.out.println("[AcolhimentoPage] Clicando no checkbox...");
        if (checkboxContainer.isVisible()) {
            checkboxContainer.click();
        } else {
            checkbox.click();
        }
        System.out.println("[AcolhimentoPage] Checkbox marcado");
    }

    /**
     * Clica no botão Finalizar
     */
    public void clicarFinalizar() {
        System.out.println("[AcolhimentoPage] Clicando em Finalizar");
        Locator botaoFinalizar = page.locator(BOTAO_FINALIZAR).first();
        botaoFinalizar.waitFor(new Locator.WaitForOptions().setTimeout(TIMEOUT_MS));
        botaoFinalizar.click();
        System.out.println("[AcolhimentoPage] Botão Finalizar clicado");
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

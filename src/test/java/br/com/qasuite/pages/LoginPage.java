package br.com.qasuite.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

/**
 * Page Object para a tela de Login do Sinnc Saúde.
 */
public class LoginPage {

    private final Page page;

    // Seletores - ajustar conforme a aplicação real
    private final String CAMPO_USUARIO = "input[type='text'], input[name='usuario'], input[name='login'], input[placeholder*='usuário' i], input[placeholder*='usuario' i], #txtUsuario";
    private final String CAMPO_SENHA = "input[type='password'], input[name='senha'], input[name='password'], #txtSenha";
    private final String BOTAO_ENTRAR = "button[type='submit'], input[type='submit'], button:has-text('Entrar'), button:has-text('Login'), button:has-text('Acessar'), #btnEntrar";
    private final String MENSAGEM_ERRO = ".mensagem-erro, .error-message, .alert-danger, [role='alert']";
    
    // Modal de seleção de unidade pós-login
    private final String MODAL_UNIDADE = ".bg-white.p-4.rounded-xl";
    private final String CARD_UNIDADE_INTERNA = ".cursor-pointer:has-text('UNIDADE PARA USO INTERNO')";
    private final String BOTAO_CONTINUAR = "button[id-test='btnContinuar']";

    public LoginPage(Page page) {
        this.page = page;
    }

    /**
     * Preenche o campo de usuário
     */
    public void preencherUsuario(String usuario) {
        System.out.println("[LoginPage] Preenchendo usuário: " + usuario);
        Locator campoUsuario = page.locator(CAMPO_USUARIO).first();
        campoUsuario.waitFor();
        campoUsuario.fill(usuario);
    }

    /**
     * Preenche o campo de senha
     */
    public void preencherSenha(String senha) {
        System.out.println("[LoginPage] Preenchendo senha");
        Locator campoSenha = page.locator(CAMPO_SENHA).first();
        campoSenha.waitFor();
        campoSenha.fill(senha);
    }

    /**
     * Clica no botão de entrar/login
     */
    public void clicarEntrar() {
        System.out.println("[LoginPage] Clicando em Entrar");
        Locator botaoEntrar = page.locator(BOTAO_ENTRAR).first();
        botaoEntrar.waitFor();
        botaoEntrar.click();
    }

    /**
     * Realiza o login completo
     */
    public void realizarLogin(String usuario, String senha) {
        System.out.println("[LoginPage] Realizando login");
        preencherUsuario(usuario);
        preencherSenha(senha);
        clicarEntrar();
        
        // Aguarda rapidamente para o modal aparecer (navegação)
        page.waitForLoadState(LoadState.NETWORKIDLE);
        
        // Verifica se houve erro de login
        if (temMensagemErro()) {
            String erro = getMensagemErro();
            System.out.println("[LoginPage] Erro de login detectado: " + erro);
            throw new RuntimeException("Erro de login: " + erro);
        }
        
        // Aguarda e seleciona unidade pós-login
        selecionarUnidadeInterna();
    }
    
    /**
     * Seleciona unidade interna no modal pós-login
     */
    public void selecionarUnidadeInterna() {
        try {
            System.out.println("[LoginPage] Aguardando modal de seleção de unidade...");
            // Timeout reduzido - se não aparecer em 5s, continua
            page.waitForSelector(MODAL_UNIDADE, new Page.WaitForSelectorOptions().setTimeout(5000));
            
            System.out.println("[LoginPage] Modal encontrado. Selecionando unidade interna...");
            Locator cardInterna = page.locator(CARD_UNIDADE_INTERNA).first();
            cardInterna.click();
            
            System.out.println("[LoginPage] Clicando em Continuar...");
            Locator botaoContinuar = page.locator(BOTAO_CONTINUAR).first();
            botaoContinuar.click();
            
            // Aguarda navegação completar
            page.waitForLoadState(LoadState.NETWORKIDLE);
            
            System.out.println("[LoginPage] Unidade selecionada com sucesso");
        } catch (Exception e) {
            System.out.println("[LoginPage] Aviso: Modal de unidade não encontrado ou não necessário");
        }
    }

    /**
     * Verifica se há mensagem de erro de login
     */
    public boolean temMensagemErro() {
        Locator mensagemErro = page.locator(MENSAGEM_ERRO).first();
        return mensagemErro.isVisible();
    }

    /**
     * Obtém o texto da mensagem de erro
     */
    public String getMensagemErro() {
        Locator mensagemErro = page.locator(MENSAGEM_ERRO).first();
        if (mensagemErro.isVisible()) {
            return mensagemErro.textContent();
        }
        return null;
    }

    /**
     * Verifica se está na página de login
     */
    public boolean estaNaPaginaLogin() {
        return page.locator(CAMPO_USUARIO).first().isVisible() &&
               page.locator(CAMPO_SENHA).first().isVisible();
    }

    /**
     * Aguarda a página de login carregar completamente
     */
    public void aguardarPaginaCarregar() {
        System.out.println("[LoginPage] Aguardando página carregar...");
        page.waitForSelector(CAMPO_USUARIO);
        page.waitForSelector(CAMPO_SENHA);
        page.waitForSelector(BOTAO_ENTRAR);
        System.out.println("[LoginPage] Página carregada");
    }
}

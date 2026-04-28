package br.com.qasuite.steps;

import br.com.qasuite.config.BaseTest;
import br.com.qasuite.pages.LoginPage;
import io.cucumber.java.pt.*;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Steps definitions para testes de Login.
 */
@Tag("smoke")
public class LoginSteps extends BaseTest {

    private LoginPage loginPage;

    @Override
    protected String getTipoTeste() {
        return "smoke";
    }

    @Dado("que estou na página de login")
    public void queEstouNaPaginaDeLogin() {
        loginPage = new LoginPage(page);
        loginPage.aguardarPaginaCarregar();
        assertTrue(loginPage.estaNaPaginaLogin(), "Deveria estar na página de login");
    }

    @Quando("eu insiro o usuário {string}")
    public void euInsiroOUsuario(String usuario) {
        loginPage.preencherUsuario(usuario);
    }

    @Quando("eu insiro a senha {string}")
    public void euInsiroASenha(String senha) {
        loginPage.preencherSenha(senha);
    }

    @Quando("eu clico no botão Entrar")
    public void euClicoNoBotaoEntrar() {
        loginPage.clicarEntrar();
    }

    @Então("devo ser redirecionado para a página principal")
    public void devoSerRedirecionadoParaAPaginaPrincipal() {
        // O login é feito automaticamente pelo BaseTest
        // Aqui verificamos que estamos na tela principal
        assertFalse(loginPage.estaNaPaginaLogin(), "Não deveria estar mais na página de login");
    }

    @Então("devo ver uma mensagem de erro de autenticação")
    public void devoVerUmaMensagemDeErroDeAutenticacao() {
        assertTrue(loginPage.temMensagemErro(), "Deveria exibir mensagem de erro");
    }
}

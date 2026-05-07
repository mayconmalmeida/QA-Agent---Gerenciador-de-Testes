package br.com.qasuite.core;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.util.Map;

/**
 * Login automático adaptável que detecta campos dinamicamente.
 * Funciona com qualquer sistema de login.
 */
public class SmartLogin {
    
    private final Page page;
    private final ElementDetector detector;
    private static final int TIMEOUT_MS = 10000;
    
    public SmartLogin(Page page) {
        this.page = page;
        this.detector = new ElementDetector(page);
        // Configura timeout padrão de 10 segundos para todas as operações
        page.setDefaultTimeout(TIMEOUT_MS);
    }
    
    /**
     * Realiza login automático detectando campos dinamicamente
     * @param url URL da página de login
     * @param usuario Nome de usuário
     * @param senha Senha
     * @return true se login foi bem-sucedido
     */
    public boolean login(String url, String usuario, String senha) {
        System.out.println("[SmartLogin] Iniciando login automático em: " + url);
        
        try {
            // Navega para URL
            page.navigate(url);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED, 
                new Page.WaitForLoadStateOptions().setTimeout(TIMEOUT_MS));
            
            // Aguarda elementos carregarem
            page.waitForTimeout(500);
            
            // Detecta campos de login automaticamente
            Map<String, UIElement> camposLogin = detector.detectarCamposLogin();
            UIElement botaoLogin = detector.detectarBotaoLogin();
            
            if (!camposLogin.containsKey("usuario")) {
                System.err.println("[SmartLogin] ERRO: Campo de usuário não detectado!");
                return false;
            }
            
            if (!camposLogin.containsKey("senha")) {
                System.err.println("[SmartLogin] ERRO: Campo de senha não detectado!");
                return false;
            }
            
            if (botaoLogin == null) {
                System.err.println("[SmartLogin] ERRO: Botão de login não detectado!");
                return false;
            }
            
            // Preenche usuário
            UIElement campoUsuario = camposLogin.get("usuario");
            System.out.println("[SmartLogin] Preenchendo usuário no campo: " + campoUsuario.getSeletorPlaywright());
            Locator locatorUsuario = page.locator(campoUsuario.getSeletorPlaywright()).first();
            locatorUsuario.fill(usuario);
            
            // Preenche senha
            UIElement campoSenha = camposLogin.get("senha");
            System.out.println("[SmartLogin] Preenchendo senha no campo: " + campoSenha.getSeletorPlaywright());
            Locator locatorSenha = page.locator(campoSenha.getSeletorPlaywright()).first();
            locatorSenha.fill(senha);
            
            // Clica no botão de login
            System.out.println("[SmartLogin] Clicando no botão: " + botaoLogin.getSeletorPlaywright());
            Locator locatorBotao = page.locator(botaoLogin.getSeletorPlaywright()).first();
            locatorBotao.click();

            // Aguarda navegação/não redirecionamento
            page.waitForLoadState(LoadState.NETWORKIDLE,
                new Page.WaitForLoadStateOptions().setTimeout(TIMEOUT_MS));

            // Aguarda mais tempo para o modal pós-login aparecer
            page.waitForTimeout(3000);

            // Verifica se ainda está na tela de login (campos de usuário/senha ainda visíveis)
            try {
                boolean aindaNaTelaLogin = page.locator("#inputCpf, #inputPassword, [name='username'], [type='password']").first().isVisible();
                if (aindaNaTelaLogin) {
                    System.out.println("[SmartLogin] Ainda na tela de login, aguardando mais tempo...");
                    page.waitForTimeout(5000);
                }
            } catch (Exception e) {
                System.out.println("[SmartLogin] Campos de login não encontrados - provavelmente saiu da tela de login");
            }

            System.out.println("[SmartLogin] Login realizado com sucesso!");
            return true;

        } catch (Exception e) {
            System.err.println("[SmartLogin] ERRO durante login: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Verifica se há modal de seleção pós-login (como seleção de unidade)
     * e tenta selecionar a primeira opção disponível
     */
    public boolean handlePostLoginModal() {
        System.out.println("[SmartLogin] Verificando modal/unidade pós-login...");
        
        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                new Page.WaitForLoadStateOptions().setTimeout(10000));
            page.waitForTimeout(1200);

            if (!isUnitSelectionModalVisible()) {
                System.out.println("[SmartLogin] Nenhum modal de unidade detectado");
                return true;
            }

            Locator scope = page.locator("body");

            String desired = "";
            try {
                Locator campoBusca = scope.locator("[role='textbox'], input[placeholder*='Buscar'], input[type='text'], input").first();
                if (campoBusca.isVisible()) {
                    try {
                        desired = campoBusca.inputValue();
                    } catch (Exception ignored) {}
                    if (desired != null) {
                        desired = desired.trim();
                    } else {
                        desired = "";
                    }
                }
            } catch (Exception ignored) {}

            if (desired.isEmpty()) {
                System.out.println("[SmartLogin] Modal de unidade presente, mas sem valor de busca; deixando para o executor do teste");
                return true;
            }

            System.out.println("[SmartLogin] Selecionando unidade via busca: '" + desired + "'");

            Locator campoBusca = scope.locator("[role='textbox'], input[placeholder*='Buscar'], input[type='text'], input").first();
            campoBusca.click();
            campoBusca.fill(desired);
            page.waitForTimeout(1200);

            Locator unidade = findBestUnitOption(scope, desired);
            if (unidade == null || !unidade.isVisible()) {
                System.out.println("[SmartLogin] Não encontrou opção de unidade para clicar");
                return false;
            }

            try {
                unidade.scrollIntoViewIfNeeded();
            } catch (Exception ignored) {}
            unidade.click(new Locator.ClickOptions().setTimeout(8000));

            if (!clickConfirmInScope(scope)) {
                System.out.println("[SmartLogin] Botão Continuar/Confirmar não encontrado ou desabilitado");
                return false;
            }

            if (!waitForUnitModalToClose(10000)) {
                System.out.println("[SmartLogin] Modal de unidade não fechou após seleção");
                return false;
            }

            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            return true;

        } catch (Exception e) {
            System.err.println("[SmartLogin] ERRO no modal pós-login: " + e.getMessage());
            return false;
        }
    }

    private boolean isUnitSelectionModalVisible() {
        try {
            Locator hasTextbox = page.locator("[role='textbox'], input[type='text'], input").first();
            if (!hasTextbox.isVisible()) return false;

            Locator hasCnes = page.locator("[role='option']:has-text('CNES'), text=CNES").first();
            if (!hasCnes.isVisible()) return false;

            Locator confirmar = page.locator(
                "button:has-text('Continuar'), [role='button']:has-text('Continuar'), [role='option']:has-text('Continuar'), " +
                "button:has-text('Selecionar'), [role='button']:has-text('Selecionar'), [role='option']:has-text('Selecionar')"
            ).first();
            if (confirmar.isVisible()) return true;

            Locator erro = page.getByText("Selecione uma unidade", new Page.GetByTextOptions().setExact(false)).first();
            return erro.isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean waitForUnitModalToClose(int timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (!isUnitSelectionModalVisible()) return true;
            page.waitForTimeout(300);
        }
        return !isUnitSelectionModalVisible();
    }

    private boolean clickConfirmInScope(Locator scope) {
        String[] labels = {"Continuar", "Selecionar", "Confirmar", "OK", "Próximo", "Avançar", "Prosseguir"};
        for (String textoBotao : labels) {
            try {
                Locator botao = scope.locator(
                    "button:has-text('" + textoBotao + "'), " +
                    "[role='button']:has-text('" + textoBotao + "'), " +
                    "[role='option']:has-text('" + textoBotao + "')"
                ).first();
                if (!botao.isVisible()) continue;

                long start = System.currentTimeMillis();
                boolean enabled = false;
                while (System.currentTimeMillis() - start < 4000) {
                    try {
                        enabled = botao.isEnabled();
                    } catch (Exception ignored) {
                        enabled = true;
                    }
                    if (enabled) break;
                    page.waitForTimeout(200);
                }
                if (!enabled) continue;

                System.out.println("[SmartLogin] Clicando em botão: " + textoBotao);
                botao.click(new Locator.ClickOptions().setTimeout(8000));
                page.waitForTimeout(500);
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private Locator findBestUnitOption(Locator scope, String unidadeDesejada) {
        try {
            Locator options = scope.locator("[role='option']:has-text('CNES'), .cursor-pointer:has-text('CNES'), [class*='cursor-pointer']:has-text('CNES'), [class*='option']:has-text('CNES'), [class*='item']:has-text('CNES'), [class*='card']:has-text('CNES'), li:has-text('CNES'), div:has-text('CNES')");
            try {
                options.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(6000));
            } catch (Exception ignored) {}

            int count = 0;
            try {
                count = options.count();
            } catch (Exception ignored) {}

            String desired = normalizeText(unidadeDesejada);
            Locator fallback = null;

            for (int i = 0; i < Math.min(count, 200); i++) {
                Locator opt = options.nth(i);
                String txt;
                try {
                    txt = opt.innerText();
                } catch (Exception e) {
                    continue;
                }

                String norm = normalizeText(txt);
                if (norm.isEmpty()) continue;
                if (norm.length() > 350) continue;
                if (norm.contains("desativado")) continue;
                if (norm.contains("continuar") || norm.contains("selecionar") || norm.contains("confirmar")) continue;

                if (fallback == null) fallback = opt;

                if (!desired.isEmpty() && norm.contains(desired)) {
                    return opt;
                }
            }

            if (fallback != null) return fallback;
        } catch (Exception ignored) {}

        return null;
    }

    private String normalizeText(String input) {
        if (input == null) return "";
        String s = input.replace("\n", " ").replace("\t", " ").trim().toLowerCase();
        try {
            s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        } catch (Exception ignored) {}
        s = s.replaceAll("\\s+", " ");
        return s;
    }
    
    /**
     * Executa fluxo completo de login com tratamento de modal
     */
    public boolean loginCompleto(String url, String usuario, String senha) {
        boolean loginOk = login(url, usuario, senha);
        if (!loginOk) return false;
        
        // Tenta lidar com modal pós-login se existir
        handlePostLoginModal();
        
        return true;
    }
}

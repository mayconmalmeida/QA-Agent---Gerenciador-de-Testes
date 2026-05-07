package br.com.qasuite.core;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.assertions.PlaywrightAssertions;

import java.util.function.Consumer;

/**
 * Executor de ações baseadas em decisões da IA.
 * Converte ActionDecision em comandos Playwright reais.
 */
public class SmartActionExecutor {

    private final Page page;
    private final int defaultTimeout;
    private Consumer<String> logCallback;

    public SmartActionExecutor(Page page, int defaultTimeoutMs) {
        this.page = page;
        this.defaultTimeout = defaultTimeoutMs;
        // Cria pasta para screenshots de debug
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get("output/screenshots"));
        } catch (Exception ignored) {}
    }

    public SmartActionExecutor withLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
        return this;
    }

    private void log(String message) {
        System.out.println(message);
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }

    /**
     * Executa uma ação decidida pela IA
     */
    public void execute(ActionDecision decision) throws Exception {
        log("[SmartAction] Executando: " + decision.getAction() +
            (decision.getReason() != null ? " (" + decision.getReason() + ")" : ""));

        try {
            switch (decision.getAction()) {
                case NAVIGATE:
                    executeNavigate(decision);
                    break;
                case CLICK:
                    executeClick(decision);
                    break;
                case CLICK_NTH:
                    executeClickNth(decision);
                    break;
                case DOUBLE_CLICK:
                    executeDoubleClick(decision);
                    break;
                case FILL:
                    executeFill(decision);
                    break;
                case FILL_PLACEHOLDER:
                    executeFillPlaceholder(decision);
                    break;
                case CHECK:
                    executeCheck(decision);
                    break;
                case SELECT:
                    executeSelect(decision);
                    break;
                case WAIT_TEXT:
                    executeWaitText(decision);
                    break;
                case WAIT_URL:
                    executeWaitUrl(decision);
                    break;
                case PRESS_KEY:
                    executePressKey(decision);
                    break;
                case ASSERT:
                    executeAssert(decision);
                    break;
                case WAIT:
                    executeWait(decision);
                    break;
                default:
                    throw new IllegalArgumentException("Ação não suportada: " + decision.getAction());
            }
        } catch (Exception e) {
            log("[SmartAction] ERRO ao executar " + decision.getAction() + ": " + e.getMessage());
            throw e;
        }
    }

    private void executeNavigate(ActionDecision decision) {
        String url = decision.getUrl();
        log("[SmartAction] Navegando para: " + url);
        page.navigate(url, new Page.NavigateOptions().setTimeout(defaultTimeout));
        page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(defaultTimeout));
        log("[SmartAction] Navegação concluída");
    }

    private void executeDoubleClick(ActionDecision decision) throws Exception {
        String role = decision.getRole();
        String text = decision.getText();

        log("[SmartAction] Duplo-clique em [role=" + role + ", text=" + text + "]");

        Locator locator = null;
        
        // Estratégia 1: por role + texto
        try {
            locator = resolveLocator(role, text);
            locator.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(defaultTimeout));
        } catch (Exception e) {
            log("[SmartAction] Estratégia 1 falhou (role+texto): " + e.getMessage());
            locator = null;
        }
        
        // Estratégia 2: procura por texto contido
        if (locator == null && text != null && !text.isEmpty()) {
            try {
                log("[SmartAction] Tentando encontrar por texto contido...");
                Locator byText = page.getByText(text, new Page.GetByTextOptions().setExact(false));
                if (byText.count() > 0) {
                    locator = byText.first();
                    locator.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(5000));
                }
            } catch (Exception e) {
                log("[SmartAction] Estratégia 2 falhou (texto): " + e.getMessage());
            }
        }
        
        // Estratégia 3: seletores CSS para cards/listas
        if (locator == null && text != null && !text.isEmpty()) {
            try {
                log("[SmartAction] Tentando seletores CSS para duplo-clique...");
                String[] seletores = {
                    "[class*='card']:has-text(\"" + text + "\")",
                    "[class*='item']:has-text(\"" + text + "\")",
                    "[class*='list']:has-text(\"" + text + "\")",
                    "li:has-text(\"" + text + "\")",
                    "tr:has-text(\"" + text + "\")"
                };
                for (String seletor : seletores) {
                    try {
                        Locator loc = page.locator(seletor).first();
                        if (loc.isVisible()) {
                            locator = loc;
                            log("[SmartAction] Elemento encontrado para duplo-clique: " + seletor);
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                log("[SmartAction] Estratégia 3 falhou (CSS): " + e.getMessage());
            }
        }
        
        if (locator == null) {
            throw new Exception("Não foi possível encontrar elemento para duplo-clique: " + text);
        }
        
        locator.dblclick();
        log("[SmartAction] Duplo-clique concluído");
    }

    private void executeClick(ActionDecision decision) throws Exception {
        String role = decision.getRole();
        String text = decision.getText();
        String reason = decision.getReason();
        String label = decision.getLabel();  // Usado para menu pai em navegação hierárquica
        boolean isSelectionWithConfirm = reason != null && 
            (reason.contains("Continuar") || reason.contains("selecionar"));

        log("[SmartAction] Clicando em [role=" + role + ", text=" + text + "]");

        // NAVEGAÇÃO HIERÁRQUICA DE MENU: se label contém menu pai, clica nele primeiro
        if (label != null && !label.isEmpty() && reason != null && reason.contains("hierárquica")) {
            log("[SmartAction] Menu hierárquico detectado - clicando no menu pai: " + label);
            try {
                Locator parentMenu = findMenuElement(label);
                if (parentMenu != null) {
                    parentMenu.click();
                    log("[SmartAction] Menu pai clicado, aguardando submenu aparecer...");
                    page.waitForTimeout(1200);  // Aguarda animação do submenu (aumentado)
                }
            } catch (Exception e) {
                log("[SmartAction] Menu pai não encontrado ou já expandido: " + e.getMessage());
            }
        }

        Locator locator = null;
        Exception lastError = null;
        
        // ESTRATÉGIA ESPECIAL PARA SUBMENU: Se for navegação hierárquica, tenta clicar no submenu diretamente
        if (label != null && !label.isEmpty() && reason != null && reason.contains("hierárquica")) {
            log("[SmartAction] Procurando submenu específico: " + text);
            try {
                // PRIMEIRO: Tenta getByRole que é mais confiável
                try {
                    Locator subByRole = page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(text)).first();
                    subByRole.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(3000));
                    if (subByRole.isVisible()) {
                        log("[SmartAction] Submenu encontrado com getByRole(OPTION, name='" + text + "')");
                        subByRole.click();
                        log("[SmartAction] Submenu clicado com sucesso via getByRole!");
                        return;
                    }
                } catch (Exception e) {
                    log("[SmartAction] getByRole(OPTION) falhou: " + e.getMessage());
                }
                
                // SEGUNDO: Tenta getByText
                try {
                    Locator subByText = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
                    subByText.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(3000));
                    if (subByText.isVisible()) {
                        log("[SmartAction] Submenu encontrado com getByText('" + text + "')");
                        subByText.click();
                        log("[SmartAction] Submenu clicado com sucesso via getByText!");
                        return;
                    }
                } catch (Exception e) {
                    log("[SmartAction] getByText falhou: " + e.getMessage());
                }
                
                // TERCEIRO: Tenta seletores CSS específicos
                String[] submenuSelectors = {
                    "[role='option']:has-text(\"" + text + "\")",
                    "[role='menuitem']:has-text(\"" + text + "\")",
                    "[class*='submenu']:has-text(\"" + text + "\")",
                    "[class*='item']:has-text(\"" + text + "\")",
                    "div:has-text(\"" + text + "\")",
                    "a:has-text(\"" + text + "\")",
                    "span:has-text(\"" + text + "\")",
                    "li:has-text(\"" + text + "\")"
                };
                for (String sel : submenuSelectors) {
                    try {
                        Locator subLoc = page.locator(sel).first();
                        if (subLoc.isVisible()) {
                            log("[SmartAction] Submenu encontrado com seletor: " + sel);
                            subLoc.click();
                            log("[SmartAction] Submenu clicado com sucesso via CSS!");
                            return;
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                log("[SmartAction] Erro ao clicar submenu: " + e.getMessage());
            }
        }
        
        // Estratégia 1: resolveLocator padrão
        try {
            locator = resolveLocator(role, text);
            locator.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(defaultTimeout));
        } catch (Exception e) {
            lastError = e;
            log("[SmartAction] Estratégia 1 falhou (role+texto): " + e.getMessage());
            locator = null;
        }
        
        // Estratégia 2: se for option/card, tenta clicar no primeiro disponível
        if (locator == null && ("option".equals(role) || "card".equals(role))) {
            try {
                log("[SmartAction] Tentando clicar no primeiro card/option disponível...");
                Locator firstOption = page.getByRole(AriaRole.OPTION).first();
                firstOption.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(5000));
                locator = firstOption;
            } catch (Exception e) {
                log("[SmartAction] Estratégia 2 falhou (primeiro option): " + e.getMessage());
            }
        }
        
        // Estratégia 3: tenta seletor CSS genérico para cards/listas
        if (locator == null) {
            try {
                log("[SmartAction] Tentando seletores CSS para cards/listas...");
                String[] seletores = {
                    "[class*='card']",
                    "[class*='item']",
                    "[class*='list-item']",
                    "[class*='list']",
                    "[role='option']",
                    "[role='listitem']",
                    "li",
                    ".list-group-item",
                    "[class*='unidade']",
                    "[class*='selectable']",
                    ".modal-body [class*='item']",
                    ".modal-body li",
                    "[class*='dropdown'] [class*='item']",
                    "[class*='menu']",
                    "[class*='nav']",
                    "[role='menuitem']",
                    "[role='button']"
                };
                // Se tem texto especificado, SÓ clica se encontrar elemento COM o texto
                if (text != null && !text.isEmpty()) {
                    for (String sel : seletores) {
                        try {
                            Locator locWithText = page.locator(sel + ":has-text(\"" + text + "\")").first();
                            if (locWithText.isVisible()) {
                                locator = locWithText;
                                log("[SmartAction] Elemento com texto encontrado: " + sel + ":has-text(\"" + text + "\")");
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                    // Se tem texto mas não achou em nenhum seletor, NÃO cai no fallback genérico
                    if (locator == null) {
                        log("[SmartAction] AVISO: Texto '" + text + "' não encontrado em nenhum elemento. Abortando busca genérica.");
                    }
                } else {
                    // Sem texto especificado: pode clicar no primeiro elemento visível
                    for (String sel : seletores) {
                        try {
                            Locator loc = page.locator(sel).first();
                            if (loc.isVisible()) {
                                locator = loc;
                                log("[SmartAction] Elemento encontrado com seletor: " + sel);
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                log("[SmartAction] Estratégia 3 falhou (seletores CSS): " + e.getMessage());
            }
        }
        
        // ESTRATÉGIA 3b: BOTÕES COMUNS - busca específica para botões de ação
        if (locator == null && text != null && !text.isEmpty()) {
            String[] botoesComuns = {"Inserir", "Finalizar", "Salvar", "Gravar", "Confirmar", "Cadastrar", "Adicionar", "Novo"};
            for (String botao : botoesComuns) {
                if (text.equalsIgnoreCase(botao)) {
                    log("[SmartAction] Botão comum detectado: " + botao + " - procurando com seletores específicos...");
                    
                    // PRIMEIRO: Tenta no contexto de modal/dialog (prioridade)
                    try {
                        String dialogSel = "[role='dialog'], .modal, [class*='modal'], [class*='dialog'], [class*='popup']";
                        Locator dialogBtn = page.locator(dialogSel)
                            .locator("button:has-text(\"" + botao + "\")");
                        Locator pick = pickFirstClickable(dialogBtn);
                        if (pick != null) {
                            locator = pick;
                            log("[SmartAction] Botão encontrado em modal/dialog: " + botao);
                            break;
                        }
                    } catch (Exception e) {
                        log("[SmartAction] Modal/dialog falhou para botão: " + e.getMessage());
                    }

                    // SEGUNDO: Tenta getByRole (exato)
                    try {
                        Locator btnByRole = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(botao).setExact(true));
                        Locator pick = pickFirstClickable(btnByRole);
                        if (pick != null) {
                            locator = pick;
                            log("[SmartAction] Botão encontrado com getByRole(BUTTON, name='" + botao + "', exact)");
                            break;
                        }
                    } catch (Exception e) {
                        log("[SmartAction] getByRole falhou para botão: " + e.getMessage());
                    }
                    
                    // TERCEIRO: Tenta getByRole (parcial)
                    if (locator == null) {
                        try {
                            Locator btnByRole = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(botao).setExact(false));
                            Locator pick = pickFirstClickable(btnByRole);
                            if (pick != null) {
                                locator = pick;
                                log("[SmartAction] Botão encontrado com getByRole(BUTTON, name~='" + botao + "')");
                                break;
                            }
                        } catch (Exception e) {
                            log("[SmartAction] getByRole parcial falhou para botão: " + e.getMessage());
                        }
                    }

                    // QUARTO: Tenta getByText (exato)
                    if (locator == null) {
                        try {
                            Locator btnByText = page.getByText(botao, new Page.GetByTextOptions().setExact(true));
                            Locator pick = pickFirstClickable(btnByText);
                            if (pick != null) {
                                locator = pick;
                                log("[SmartAction] Botão encontrado com getByText('" + botao + "', exact)");
                                break;
                            }
                        } catch (Exception e) {
                            log("[SmartAction] getByText falhou para botão: " + e.getMessage());
                        }
                    }
                    
                    // QUINTO: Tenta getByText (parcial)
                    if (locator == null) {
                        try {
                            Locator btnByText = page.getByText(botao, new Page.GetByTextOptions().setExact(false));
                            Locator pick = pickFirstClickable(btnByText);
                            if (pick != null) {
                                locator = pick;
                                log("[SmartAction] Botão encontrado com getByText('" + botao + "')");
                                break;
                            }
                        } catch (Exception e) {
                            log("[SmartAction] getByText parcial falhou para botão: " + e.getMessage());
                        }
                    }

                    // SEXTO: Tenta seletores CSS
                    if (locator == null) {
                        String[] botaoSelectors = {
                            "button:has-text(\"" + botao + "\")",
                            "[type='button']:has-text(\"" + botao + "\")",
                            "[class*='btn']:has-text(\"" + botao + "\")",
                            "[class*='button']:has-text(\"" + botao + "\")",
                            "a:has-text(\"" + botao + "\")",
                            "span:has-text(\"" + botao + "\")",
                            "div:has-text(\"" + botao + "\")",
                            "[role='button']:has-text(\"" + botao + "\")",
                            "[class*='action']:has-text(\"" + botao + "\")",
                            "[class*='toolbar']:has-text(\"" + botao + "\")",
                            "[class*='header']:has-text(\"" + botao + "\")",
                            "form button:has-text(\"" + botao + "\")",
                            ".modal button:has-text(\"" + botao + "\")",
                            "[class*='modal'] button:has-text(\"" + botao + "\")",
                            "[class*='dialog'] button:has-text(\"" + botao + "\")",
                            "[class*='popup'] button:has-text(\"" + botao + "\")",
                            "[class*='form'] button:has-text(\"" + botao + "\")",
                            "[class*='content'] button:has-text(\"" + botao + "\")"
                        };
                        for (String sel : botaoSelectors) {
                            try {
                                Locator pick = pickFirstClickable(page.locator(sel));
                                if (pick != null) {
                                    locator = pick;
                                    log("[SmartAction] Botão encontrado com seletor: " + sel);
                                    break;
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    if (locator != null) break;
                }
            }
        }
        
        // Estratégia 4: procura em elementos clicáveis comuns
        if (locator == null && text != null && !text.isEmpty()) {
            try {
                log("[SmartAction] Tentando encontrar item de lista por texto...");
                String[] seletoresComTexto = {
                    "div:has-text(\"" + text + "\")",
                    "span:has-text(\"" + text + "\")",
                    "a:has-text(\"" + text + "\")",
                    "button:has-text(\"" + text + "\")",
                    "[class*='item']:has-text(\"" + text + "\")",
                    "[class*='option']:has-text(\"" + text + "\")"
                };
                for (String sel : seletoresComTexto) {
                    try {
                        Locator loc = page.locator(sel).first();
                        if (loc.isVisible()) {
                            locator = loc;
                            log("[SmartAction] Elemento clicável encontrado: " + sel);
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                log("[SmartAction] Estratégia 4 falhou (elementos clicáveis): " + e.getMessage());
            }
        }
        
        // Estratégia 5: texto parcial na página
        if (locator == null && text != null && !text.isEmpty()) {
            try {
                log("[SmartAction] Tentando encontrar por texto parcial...");
                locator = page.getByText(text, new Page.GetByTextOptions().setExact(false));
                locator.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(5000));
            } catch (Exception e) {
                log("[SmartAction] Estratégia 5 falhou (texto parcial): " + e.getMessage());
            }
        }

        if (locator == null) {
            throw new Exception("Não foi possível encontrar elemento para clicar. Último erro: " + (lastError != null ? lastError.getMessage() : "desconhecido"));
        }

        // Captura estado antes do clique para verificar efeito
        String urlAntes = page.url();
        int elementosAntes = page.locator("body *:visible").count();
        
        locator.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(defaultTimeout));
        try { locator.scrollIntoViewIfNeeded(); } catch (Exception ignored) {}
        locator.click(new Locator.ClickOptions().setTimeout(defaultTimeout));

        // Aguarda estabilização após clique
        page.waitForLoadState(LoadState.DOMCONTENTLOADED,
            new Page.WaitForLoadStateOptions().setTimeout(5000));

        // Verifica se o clique teve efeito (mudança de URL ou elementos)
        String urlDepois = page.url();
        int elementosDepois = page.locator("body *:visible").count();
        boolean teveEfeito = !urlAntes.equals(urlDepois) || Math.abs(elementosDepois - elementosAntes) > 2;
        
        if (!teveEfeito && text != null && !text.isEmpty()) {
            log("[SmartAction] AVISO: Clique pode não ter tido efeito esperado em: " + text);
            // Captura screenshot para debug
            try {
                String screenshotPath = "output/screenshots/erro_clique_" + text.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis() + ".png";
                page.screenshot(new Page.ScreenshotOptions().setPath(java.nio.file.Paths.get(screenshotPath)));
                log("[SmartAction] Screenshot salvo: " + screenshotPath);
            } catch (Exception ignored) {}
        }

        log("[SmartAction] Clique concluído" + (teveEfeito ? " (mudança detectada)" : ""));
        
        // Após cliques em listas/opções, sempre verifica se precisa clicar em "Continuar"
        boolean isListSelection = "option".equals(role) || "card".equals(role) || 
                                (text != null && (text.contains("CNES") || text.contains("UPS")));
        if (isListSelection || isSelectionWithConfirm) {
            page.waitForTimeout(500);
            clickContinuarIfPresent();
            if (page.locator("[role='dialog'], .modal, [class*='modal'], [class*='dialog'], [class*='popup']").filter(new Locator.FilterOptions().setHasText("")).count() > 0) {
                clickContinuarIfPresent();
            }
        }
    }
    
    private void clickContinuarIfPresent() {
        String[] labels = {"Continuar", "Selecionar", "Confirmar", "OK", "Próximo", "Avançar", "Prosseguir"};
        Locator dialog = pickFirstVisible(page.locator("[role='dialog'], .modal, [class*='modal'], [class*='dialog'], [class*='popup']"));

        if (dialog != null) {
            if (clickConfirmButtonInScope(dialog, labels)) {
                return;
            }
        }

        clickConfirmButtonInScope(page.locator("body"), labels);
    }

    private Locator pickFirstClickable(Locator candidates) {
        if (candidates == null) {
            return null;
        }

        int count;
        try {
            count = candidates.count();
        } catch (Exception e) {
            return null;
        }

        if (count <= 0) {
            return null;
        }

        int limit = Math.min(count, 20);
        for (int i = 0; i < limit; i++) {
            try {
                Locator c = candidates.nth(i);
                if (c.isVisible() && c.isEnabled()) {
                    return c;
                }
            } catch (Exception ignored) {}
        }

        try {
            Locator c = candidates.first();
            if (c.isVisible()) {
                return c;
            }
        } catch (Exception ignored) {}

        return null;
    }

    private Locator pickFirstVisible(Locator candidates) {
        if (candidates == null) {
            return null;
        }

        int count;
        try {
            count = candidates.count();
        } catch (Exception e) {
            return null;
        }

        if (count <= 0) {
            return null;
        }

        int limit = Math.min(count, 10);
        for (int i = 0; i < limit; i++) {
            try {
                Locator c = candidates.nth(i);
                if (c.isVisible()) {
                    return c;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private boolean clickConfirmButtonInScope(Locator scope, String[] labels) {
        for (String label : labels) {
            try {
                Locator byRoleExact = scope.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(label).setExact(true));
                Locator pick = pickFirstClickable(byRoleExact);
                if (pick != null) {
                    pick.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(defaultTimeout));
                    if (!pick.isEnabled()) {
                        page.waitForTimeout(400);
                    }
                    if (pick.isEnabled()) {
                        log("[SmartAction] Clicando em botão de confirmação: " + label);
                        pick.click(new Locator.ClickOptions().setTimeout(defaultTimeout));
                        page.waitForTimeout(500);
                        log("[SmartAction] Botão de confirmação clicado");
                        return true;
                    }
                }
            } catch (Exception ignored) {}

            try {
                Locator byText = scope.getByText(label, new Locator.GetByTextOptions().setExact(true));
                Locator pick = pickFirstClickable(byText);
                if (pick != null) {
                    pick.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(defaultTimeout));
                    if (!pick.isEnabled()) {
                        page.waitForTimeout(400);
                    }
                    if (pick.isEnabled()) {
                        log("[SmartAction] Clicando em botão de confirmação: " + label);
                        pick.click(new Locator.ClickOptions().setTimeout(defaultTimeout));
                        page.waitForTimeout(500);
                        log("[SmartAction] Botão de confirmação clicado");
                        return true;
                    }
                }
            } catch (Exception ignored) {}
        }

        return false;
    }

    private void executeClickNth(ActionDecision decision) throws Exception {
        String role = decision.getRole();
        String text = decision.getText();
        int index = decision.getIndex() != null ? decision.getIndex() : 0;

        log("[SmartAction] Clicando no " + (index + 1) + "º elemento [role=" + role + ", text=" + text + "]");

        Locator locator = null;
        Exception lastError = null;

        // ESTRATÉGIA 1: Se tem texto curto/simples, tenta resolver normalmente
        boolean hasSimpleText = text != null && !text.isEmpty()
                && text.length() < 50
                && !text.contains("\n")
                && !text.contains("Nascimento:");

        if (hasSimpleText) {
            try {
                locator = resolveLocator(role, text).nth(index);
                locator.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(5000));
                locator.click();
                log("[SmartAction] Clique concluído (estrategia 1 - com texto)");
                return;
            } catch (Exception e) {
                lastError = e;
                log("[SmartAction] Estratégia 1 falhou (texto): " + e.getMessage());
            }
        }

        // ESTRATÉGIA 2: Usar apenas o índice sem filtro de texto (mais genérico)
        try {
            AriaRole ariaRole = parseAriaRole(role);
            if (ariaRole != null) {
                locator = page.getByRole(ariaRole).nth(index);
            } else {
                locator = page.locator("[role='" + role + "']").nth(index);
            }
            locator.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(5000));
            locator.click();
            log("[SmartAction] Clique concluído (estrategia 2 - indice puro)");
            return;
        } catch (Exception e) {
            lastError = e;
            log("[SmartAction] Estratégia 2 falhou (indice puro): " + e.getMessage());
        }

        // ESTRATÉGIA 3: Fallback para seletores CSS genéricos
        try {
            String[] seletores = {
                "[role='option']",
                "[role='listitem']",
                ".dx-list-item",
                ".dx-item",
                "[class*='item']",
                "[class*='option']",
                "[class*='list'] > div"
            };
            for (String sel : seletores) {
                try {
                    locator = page.locator(sel).nth(index);
                    if (locator.isVisible()) {
                        locator.click();
                        log("[SmartAction] Clique concluído (estrategia 3 - CSS: " + sel + ")");
                        return;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            lastError = e;
        }

        // Se chegou aqui, todas as estratégias falharam
        throw new Exception("Não foi possível clicar no elemento [role=" + role + ", index=" + index + "]: " + (lastError != null ? lastError.getMessage() : "elemento não encontrado"));
    }

    /**
     * Converte string de role para enum AriaRole
     */
    private AriaRole parseAriaRole(String role) {
        if (role == null || role.isEmpty()) {
            return null;
        }
        return switch (role.toLowerCase()) {
            case "button" -> AriaRole.BUTTON;
            case "option" -> AriaRole.OPTION;
            case "link" -> AriaRole.LINK;
            case "tab" -> AriaRole.TAB;
            case "menuitem" -> AriaRole.MENUITEM;
            case "checkbox" -> AriaRole.CHECKBOX;
            case "radio" -> AriaRole.RADIO;
            case "textbox" -> AriaRole.TEXTBOX;
            case "combobox" -> AriaRole.COMBOBOX;
            case "listbox" -> AriaRole.LISTBOX;
            case "listitem" -> AriaRole.LISTITEM;
            case "cell" -> AriaRole.CELL;
            case "row" -> AriaRole.ROW;
            case "dialog" -> AriaRole.DIALOG;
            case "alert" -> AriaRole.ALERT;
            case "progressbar" -> AriaRole.PROGRESSBAR;
            case "searchbox" -> AriaRole.SEARCHBOX;
            case "switch" -> AriaRole.SWITCH;
            default -> null;
        };
    }

    private void executeFill(ActionDecision decision) throws Exception {
        String label = decision.getLabel();
        String value = decision.getValue();

        log("[SmartAction] Preenchendo campo [label=" + label + "] com valor: " + value);

        // Tenta por label primeiro
        Locator locator = page.getByLabel(label, new Page.GetByLabelOptions().setExact(false));

        // Fallback: tenta por role textbox
        if (locator.count() == 0) {
            locator = page.getByRole(AriaRole.TEXTBOX,
                new Page.GetByRoleOptions().setName(label));
        }

        // Fallback: tenta por placeholder
        if (locator.count() == 0) {
            locator = page.getByPlaceholder(label,
                new Page.GetByPlaceholderOptions().setExact(false));
        }

        locator.waitFor(new Locator.WaitForOptions().setTimeout(defaultTimeout));
        locator.clear();
        locator.fill(value);

        log("[SmartAction] Campo preenchido");
    }

    private void executeFillPlaceholder(ActionDecision decision) throws Exception {
        String placeholder = decision.getPlaceholder();
        String value = decision.getValue();

        log("[SmartAction] Preenchendo campo [placeholder=" + placeholder + "] com: " + value);

        Locator locator = page.getByPlaceholder(placeholder,
            new Page.GetByPlaceholderOptions().setExact(false));

        locator.waitFor(new Locator.WaitForOptions().setTimeout(defaultTimeout));
        locator.clear();
        locator.fill(value);

        log("[SmartAction] Campo preenchido");
    }

    private void executeCheck(ActionDecision decision) throws Exception {
        String label = decision.getLabel();

        log("[SmartAction] Marcando checkbox [label=" + label + "]");

        Locator locator = null;
        Exception lastError = null;

        // Estratégia 1: getByLabel
        try {
            locator = page.getByLabel(label);
            if (locator.count() > 0) {
                locator.waitFor(new Locator.WaitForOptions().setTimeout(3000));
            } else {
                locator = null;
            }
        } catch (Exception e) {
            lastError = e;
            log("[SmartAction] Estratégia 1 falhou (getByLabel): " + e.getMessage());
            locator = null;
        }

        // Estratégia 2: getByRole CHECKBOX com setName
        if (locator == null) {
            try {
                locator = page.getByRole(AriaRole.CHECKBOX,
                    new Page.GetByRoleOptions().setName(label));
                locator.waitFor(new Locator.WaitForOptions().setTimeout(3000));
            } catch (Exception e) {
                log("[SmartAction] Estratégia 2 falhou (role=checkbox): " + e.getMessage());
                locator = null;
            }
        }

        // Estratégia 3: procura elemento com texto que contenha o label (pode ser label clicável)
        if (locator == null) {
            try {
                log("[SmartAction] Tentando encontrar por texto contido...");
                // Procura elementos que contenham o texto do label
                Locator byText = page.getByText(label, new Page.GetByTextOptions().setExact(false));
                if (byText.count() > 0) {
                    // Tenta clicar no elemento ou no checkbox associado
                    locator = byText.first();
                    locator.waitFor(new Locator.WaitForOptions().setTimeout(3000));
                }
            } catch (Exception e) {
                log("[SmartAction] Estratégia 3 falhou (texto contido): " + e.getMessage());
                locator = null;
            }
        }

        // Estratégia 4: seletores CSS para checkbox
        if (locator == null) {
            try {
                log("[SmartAction] Tentando seletores CSS para checkbox...");
                String[] seletores = {
                    "input[type='checkbox']",
                    "[role='checkbox']",
                    "[class*='checkbox']",
                    "label:has-text(\"" + label + "\")",
                    "[class*='check']:has-text(\"" + label + "\")"
                };
                for (String sel : seletores) {
                    try {
                        Locator loc = page.locator(sel).first();
                        if (loc.isVisible()) {
                            locator = loc;
                            log("[SmartAction] Checkbox encontrado com seletor: " + sel);
                            break;
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                log("[SmartAction] Estratégia 4 falhou (seletores CSS): " + e.getMessage());
            }
        }

        // Estratégia 5: clicar no label (que pode marcar o checkbox via HTML for/id)
        if (locator == null) {
            try {
                log("[SmartAction] Tentando clicar em elemento com texto do label...");
                // Tenta clicar diretamente em qualquer elemento visível com o texto
                Locator clickable = page.locator("text=" + label).first();
                if (clickable.isVisible()) {
                    clickable.click();
                    log("[SmartAction] Clique no label realizado (pode ter marcado checkbox)");
                    return; // Sai sem erro pois o clique foi feito
                }
            } catch (Exception e) {
                log("[SmartAction] Estratégia 5 falhou (clique no label): " + e.getMessage());
            }
        }

        if (locator == null) {
            throw new Exception("Não foi possível encontrar checkbox para: " + label + ". Último erro: " + (lastError != null ? lastError.getMessage() : "desconhecido"));
        }

        // Tenta marcar o checkbox encontrado
        try {
            locator.check();
        } catch (Exception e) {
            // Se check() falhar, tenta click()
            log("[SmartAction] check() falhou, tentando click(): " + e.getMessage());
            locator.click();
        }

        log("[SmartAction] Checkbox marcado");
    }

    private void executeSelect(ActionDecision decision) throws Exception {
        String label = decision.getLabel();
        String option = decision.getOption();

        log("[SmartAction] Selecionando [label=" + label + ", option=" + option + "]");

        page.getByLabel(label).selectOption(option);

        log("[SmartAction] Seleção concluída");
    }

    private void executeWaitText(ActionDecision decision) throws Exception {
        String text = decision.getText();

        log("[SmartAction] Aguardando texto aparecer: " + text);

        // Se o texto é genérico como "mensagem de sucesso", tenta padrões comuns
        if (isGenericMessagePattern(text)) {
            log("[SmartAction] Texto genérico detectado, procurando padrões de mensagem...");
            if (waitForGenericMessage()) {
                return; // Encontrou uma mensagem de sucesso/erro
            }
            // Se não achou padrões específicos, continua com o texto original
        }

        Locator locator = page.getByText(text,
            new Page.GetByTextOptions().setExact(false));

        locator.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(defaultTimeout));

        log("[SmartAction] Texto encontrado");
    }
    
    private boolean isGenericMessagePattern(String text) {
        String lower = text.toLowerCase();
        return lower.contains("mensagem de sucesso") || 
               lower.equals("sucesso") ||
               lower.equals("mensagem") ||
               lower.contains("mensagem de erro") ||
               lower.contains("notificação") ||
               (lower.contains("mensagem") && lower.length() < 20);
    }
    
    private boolean waitForGenericMessage() {
        // Padrões comuns de mensagens de sucesso/erro em sistemas
        String[] successPatterns = {
            "sucesso",
            "salvo",
            "cadastrado",
            "registrado",
            "atualizado",
            "excluído",
            "finalizado",
            "concluído",
            "realizado",
            "efetuado",
            "gravado",
            "enviado",
            "processado",
            "confirmado",
            "aprovado",
            "gerado",
            "com sucesso",
            "operacao realizada",
            "dados salvos"
        };
        
        String[] errorPatterns = {
            "erro",
            "falha",
            "não foi possível",
            "inválido",
            "obrigatório",
            "não encontrado",
            "já existe",
            "duplicado",
            "não autorizado"
        };
        
        long startTime = System.currentTimeMillis();
        long timeout = defaultTimeout;
        
        while (System.currentTimeMillis() - startTime < timeout) {
            // Tenta encontrar padrões de sucesso
            for (String pattern : successPatterns) {
                try {
                    Locator locator = page.getByText(pattern, 
                        new Page.GetByTextOptions().setExact(false));
                    if (locator.count() > 0) {
                        locator.first().waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(500));
                        log("[SmartAction] Mensagem de sucesso encontrada: '" + pattern + "'");
                        return true;
                    }
                } catch (Exception e) {
                    // ignora e continua
                }
            }
            
            // Tenta encontrar padrões de erro
            for (String pattern : errorPatterns) {
                try {
                    Locator locator = page.getByText(pattern, 
                        new Page.GetByTextOptions().setExact(false));
                    if (locator.count() > 0) {
                        locator.first().waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(500));
                        log("[SmartAction] Mensagem de erro/encontro: '" + pattern + "'");
                        return true;
                    }
                } catch (Exception e) {
                    // ignora e continua
                }
            }
            
            // Aguarda um pouco antes de tentar novamente
            page.waitForTimeout(500);
        }
        
        return false; // Não encontrou nenhum padrão
    }

    private void executeWaitUrl(ActionDecision decision) throws Exception {
        String contains = decision.getContains();

        log("[SmartAction] Aguardando URL conter: " + contains);

        page.waitForURL("**" + contains + "**",
            new Page.WaitForURLOptions().setTimeout(defaultTimeout));

        log("[SmartAction] URL verificada");
    }

    private void executePressKey(ActionDecision decision) throws Exception {
        String key = decision.getKey();

        log("[SmartAction] Pressionando tecla: " + key);

        page.keyboard().press(key);

        log("[SmartAction] Tecla pressionada");
    }

    private void executeAssert(ActionDecision decision) throws Exception {
        String text = decision.getText();

        log("[SmartAction] Validando presença de texto: " + text);

        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("ASSERT requer um texto para validação");
        }

        if (isSuccessMessagePattern(text)) {
            log("[SmartAction] Texto genérico de sucesso detectado, validando padrões comuns...");
            if (waitForSuccessMessage()) {
                log("[SmartAction] Validação de mensagem de sucesso concluída");
                return;
            }
            throw new AssertionError("Mensagem de sucesso não encontrada dentro do timeout");
        }

        Locator locator = page.getByText(text,
            new Page.GetByTextOptions().setExact(false));

        locator.first().waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(defaultTimeout));

        PlaywrightAssertions.assertThat(locator.first()).isVisible();

        log("[SmartAction] Validação concluída");
    }

    private boolean isSuccessMessagePattern(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("mensagem de erro") || lower.contains("erro") || lower.contains("falha")) {
            return false;
        }
        return lower.contains("mensagem de sucesso") ||
            lower.equals("sucesso") ||
            lower.contains("mensagem") && lower.contains("sucesso");
    }

    private boolean waitForSuccessMessage() {
        String[] successPatterns = {
            "sucesso",
            "salvo",
            "cadastrado",
            "registrado",
            "atualizado",
            "excluído",
            "excluido",
            "finalizado",
            "concluído",
            "concluido",
            "realizado",
            "efetuado",
            "gravado",
            "enviado",
            "processado",
            "confirmado",
            "aprovado",
            "gerado",
            "com sucesso",
            "operacao realizada",
            "dados salvos"
        };

        long startTime = System.currentTimeMillis();
        long timeout = Math.max(defaultTimeout, 15000);

        while (System.currentTimeMillis() - startTime < timeout) {
            try {
                Object match = page.evaluate(
                    "(patterns) => {" +
                        "const norm = s => (s || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').replace(/\\s+/g, ' ').trim().toLowerCase();" +
                        "const visible = el => {" +
                            "const r = el.getBoundingClientRect();" +
                            "const s = window.getComputedStyle(el);" +
                            "return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';" +
                        "};" +
                        "const selectors = [" +
                            "'.dx-toast-message'," +
                            "'.dx-toast-content'," +
                            "'[role=alert]'," +
                            "'[aria-live]'," +
                            "'div'," +
                            "'span'," +
                            "'p'" +
                        "];" +
                        "const nodes = [];" +
                        "for (const sel of selectors) nodes.push(...document.querySelectorAll(sel));" +
                        "for (const el of nodes) {" +
                            "if (!visible(el)) continue;" +
                            "const t = norm(el.innerText || el.textContent);" +
                            "if (!t || t.length < 3) continue;" +
                            "for (const p of patterns) {" +
                                "const pn = norm(p);" +
                                "if (pn && t.includes(pn)) return p;" +
                            "}" +
                        "}" +
                        "return null;" +
                    "}",
                    java.util.Arrays.asList(successPatterns)
                );
                if (match != null && !String.valueOf(match).isBlank() && !"null".equals(String.valueOf(match))) {
                    log("[SmartAction] Mensagem de sucesso encontrada: '" + match + "'");
                    return true;
                }
            } catch (Exception ignored) {}

            for (String pattern : successPatterns) {
                try {
                    Locator locator = page.getByText(pattern,
                        new Page.GetByTextOptions().setExact(false));
                    if (locator.count() > 0) {
                        locator.first().waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(500));
                        log("[SmartAction] Mensagem de sucesso encontrada: '" + pattern + "'");
                        return true;
                    }
                } catch (Exception ignored) {}
            }
            page.waitForTimeout(300);
        }

        return false;
    }

    private void executeWait(ActionDecision decision) throws InterruptedException {
        int ms = decision.getWaitMs() != null ? decision.getWaitMs() : 1000;

        log("[SmartAction] Aguardando " + ms + "ms");

        Thread.sleep(ms);

        log("[SmartAction] Aguardo concluído");
    }

    /**
     * Procura elemento de menu por texto (flexível para diferentes estruturas)
     */
    private Locator findMenuElement(String text) {
        // PRIMEIRO: Tenta getByRole(OPTION) - menus aparecem como option no contexto
        try {
            Locator byRole = page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(text)).first();
            byRole.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(2000));
            if (byRole.isVisible()) {
                log("[SmartAction] Menu encontrado com getByRole(OPTION, name='" + text + "')");
                return byRole;
            }
        } catch (Exception e) {
            log("[SmartAction] getByRole(OPTION) falhou para menu: " + e.getMessage());
        }
        
        // SEGUNDO: Tenta getByText
        try {
            Locator byText = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
            byText.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(2000));
            if (byText.isVisible()) {
                log("[SmartAction] Menu encontrado com getByText('" + text + "')");
                return byText;
            }
        } catch (Exception e) {
            log("[SmartAction] getByText falhou para menu: " + e.getMessage());
        }
        
        // TERCEIRO: Tenta vários seletores CSS
        String[] seletores = {
            "[role='option']:has-text(\"" + text + "\")",
            "[role='menuitem']:has-text(\"" + text + "\")",
            "button:has-text(\"" + text + "\")",
            "[class*='menu']:has-text(\"" + text + "\")",
            "[class*='nav']:has-text(\"" + text + "\")",
            "[class*='sidebar']:has-text(\"" + text + "\")",
            "[class*='accordion']:has-text(\"" + text + "\")",
            "[id*='accordion']:has-text(\"" + text + "\")",
            "a:has-text(\"" + text + "\")",
            "div:has-text(\"" + text + "\")",
            "span:has-text(\"" + text + "\")"
        };
        
        for (String sel : seletores) {
            try {
                Locator loc = page.locator(sel).first();
                if (loc.isVisible()) {
                    log("[SmartAction] Menu encontrado com seletor: " + sel);
                    return loc;
                }
            } catch (Exception ignored) {}
        }
        
        return null;
    }

    /**
     * Resolve locator por role + texto com fallbacks
     */
    private Locator resolveLocator(String role, String text) {
        if (role == null || text == null) {
            return page.getByText(text, new Page.GetByTextOptions().setExact(false));
        }

        return switch (role.toLowerCase()) {
            case "button" -> page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(text));
            case "link" -> page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName(text));
            case "tab" -> page.getByRole(AriaRole.TAB,
                new Page.GetByRoleOptions().setName(text));
            case "menuitem" -> page.getByRole(AriaRole.MENUITEM,
                new Page.GetByRoleOptions().setName(text));
            case "option" -> page.getByRole(AriaRole.OPTION,
                new Page.GetByRoleOptions().setName(text));
            case "checkbox" -> page.getByRole(AriaRole.CHECKBOX,
                new Page.GetByRoleOptions().setName(text));
            case "cell", "row" -> page.getByRole(AriaRole.CELL,
                new Page.GetByRoleOptions().setName(text));
            default -> page.getByText(text, new Page.GetByTextOptions().setExact(false));
        };
    }

    /**
     * Verifica se um texto existe na página (útil para validações)
     */
    public boolean hasText(String text) {
        return page.getByText(text, new Page.GetByTextOptions().setExact(false)).count() > 0;
    }

    /**
     * Aguarda e clica em um resultado de busca
     */
    public void clickSearchResult(String searchResultText) throws Exception {
        log("[SmartAction] Clicando em resultado de busca: " + searchResultText);

        // Procura como option (dropdown) ou como qualquer texto clicável
        Locator locator = page.getByRole(AriaRole.OPTION,
            new Page.GetByRoleOptions().setName(searchResultText));

        if (locator.count() == 0) {
            locator = page.getByText(searchResultText,
                new Page.GetByTextOptions().setExact(false));
        }

        locator.first().waitFor(new Locator.WaitForOptions().setTimeout(defaultTimeout));
        locator.first().click();

        log("[SmartAction] Resultado clicado");
    }
}

package br.com.qasuite.core;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import java.util.List;

/**
 * Page Object genérico que se adapta a qualquer formulário.
 * Executa ações baseadas em descrições textuais.
 */
public class SmartPage {
    
    private final Page page;
    private final ElementDetector detector;
    private static final int TIMEOUT_MS = 10000;
    
    public SmartPage(Page page) {
        this.page = page;
        this.detector = new ElementDetector(page);
    }
    
    /**
     * Navega para um menu/submenu específico
     * @param menuTexto Texto do menu principal
     * @param submenuTexto Texto do submenu (opcional)
     */
    public void navegarParaMenu(String menuTexto, String submenuTexto) {
        System.out.println("[SmartPage] Navegando para: " + menuTexto + 
            (submenuTexto != null ? " > " + submenuTexto : ""));
        
        try {
            // Tenta encontrar menu com busca flexível
            Locator menu = encontrarElementoPorTexto(menuTexto);
            if (menu == null) {
                throw new RuntimeException("Menu não encontrado: " + menuTexto);
            }
            menu.click();
            System.out.println("[SmartPage] Menu clicado: " + menuTexto);
            
            // Se tem submenu, clica nele
            if (submenuTexto != null && !submenuTexto.isEmpty()) {
                page.waitForTimeout(500); // Aguarda submenu abrir
                Locator submenu = encontrarElementoPorTexto(submenuTexto);
                if (submenu == null) {
                    throw new RuntimeException("Submenu não encontrado: " + submenuTexto);
                }
                submenu.click();
                System.out.println("[SmartPage] Submenu clicado: " + submenuTexto);
            }
            
            // Aguarda carregamento
            page.waitForLoadState(LoadState.DOMCONTENTLOADED, 
                new Page.WaitForLoadStateOptions().setTimeout(5000));
            
        } catch (Exception e) {
            throw new RuntimeException("Erro ao navegar para menu: " + e.getMessage(), e);
        }
    }
    
    /**
     * Encontra elemento por texto com busca flexível (case-insensitive, parcial)
     */
    private Locator encontrarElementoPorTexto(String texto) {
        System.out.println("[SmartPage] Procurando elemento com texto: " + texto);
        
        // Tenta diferentes estratégias de busca
        String[] estrategias = {
            "text=" + texto,                    // Exato
            "text=" + texto + " i",              // Case-insensitive
            "text=" + texto + " s",              // Case-sensitive
            ":text-is(\"" + texto + "\")",       // Texto exato
            ":text-matches(\"" + texto + "\", \"i\")" // Regex case-insensitive
        };
        
        for (String estrategia : estrategias) {
            try {
                Locator elemento = page.locator(estrategia).first();
                if (elemento.isVisible()) {
                    System.out.println("[SmartPage] Elemento encontrado com estratégia: " + estrategia);
                    return elemento;
                }
            } catch (Exception e) {
                // Tenta próxima estratégia
            }
        }
        
        // Tenta busca parcial em todos os elementos de texto
        try {
            Locator todosTextos = page.locator("*:visible");
            int count = (int) todosTextos.count();
            System.out.println("[SmartPage] Buscando em " + count + " elementos visíveis...");
            
            // Mostra os primeiros 20 textos para debug
            System.out.println("[SmartPage] Primeiros 20 textos visíveis:");
            for (int i = 0; i < Math.min(count, 20); i++) {
                Locator el = todosTextos.nth(i);
                String elTexto = el.textContent().trim();
                if (elTexto != null && !elTexto.isEmpty() && elTexto.length() < 50) {
                    System.out.println("  [" + i + "] '" + elTexto + "'");
                }
            }
            
            for (int i = 0; i < Math.min(count, 100); i++) {
                Locator el = todosTextos.nth(i);
                String elTexto = el.textContent().trim();
                if (elTexto != null && !elTexto.isEmpty() && 
                    elTexto.toLowerCase().contains(texto.toLowerCase())) {
                    System.out.println("[SmartPage] Elemento encontrado por busca parcial: '" + elTexto + "'");
                    return el;
                }
            }
        } catch (Exception e) {
            System.out.println("[SmartPage] Erro na busca parcial: " + e.getMessage());
        }
        
        System.out.println("[SmartPage] Elemento não encontrado: " + texto);
        return null;
    }
    
    /**
     * Clica em um elemento pelo texto
     * @param texto Texto do botão/link
     */
    public void clicarPorTexto(String texto) {
        System.out.println("[SmartPage] Clicando em: '" + texto + "'");
        try {
            Locator elemento = page.locator("text=" + texto).first();
            elemento.waitFor(new Locator.WaitForOptions().setTimeout(TIMEOUT_MS));
            elemento.click();
            System.out.println("[SmartPage] Clique realizado em: " + texto);
        } catch (Exception e) {
            throw new RuntimeException("Elemento não encontrado: " + texto, e);
        }
    }
    
    /**
     * Preenche um campo de formulário
     * @param nomeCampo Nome, placeholder ou label do campo
     * @param valor Valor a preencher
     */
    public void preencherCampo(String nomeCampo, String valor) {
        System.out.println("[SmartPage] Preenchendo '" + nomeCampo + "' com: " + valor);
        
        try {
            // Tenta encontrar por vários critérios
            String[] seletores = {
                "input[placeholder*='" + nomeCampo + "' i]",
                "input[name*='" + nomeCampo + "' i]",
                "input[id*='" + nomeCampo + "' i]",
                "textarea[placeholder*='" + nomeCampo + "' i]",
                "textarea[name*='" + nomeCampo + "' i]",
                "label:has-text('" + nomeCampo + "') + input",
                "label:has-text('" + nomeCampo + "') + textarea",
                "[aria-label*='" + nomeCampo + "' i]"
            };
            
            for (String seletor : seletores) {
                try {
                    Locator campo = page.locator(seletor).first();
                    if (campo.isVisible()) {
                        campo.fill(valor);
                        System.out.println("[SmartPage] Campo preenchido usando: " + seletor);
                        return;
                    }
                } catch (Exception e) {
                    // Tenta próximo seletor
                }
            }
            
            throw new RuntimeException("Campo não encontrado: " + nomeCampo);
            
        } catch (Exception e) {
            throw new RuntimeException("Erro ao preencher campo '" + nomeCampo + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Seleciona uma opção em dropdown/select
     * @param nomeCampo Nome do campo select
     * @param opcao Texto da opção a selecionar
     */
    public void selecionarOpcao(String nomeCampo, String opcao) {
        System.out.println("[SmartPage] Selecionando '" + opcao + "' em: " + nomeCampo);
        
        try {
            // Tenta encontrar select
            String[] seletoresSelect = {
                "select[name*='" + nomeCampo + "' i]",
                "select[id*='" + nomeCampo + "' i]",
                "label:has-text('" + nomeCampo + "') + select"
            };
            
            for (String seletor : seletoresSelect) {
                try {
                    Locator select = page.locator(seletor).first();
                    if (select.isVisible()) {
                        select.selectOption(opcao);
                        System.out.println("[SmartPage] Opção selecionada no select");
                        return;
                    }
                } catch (Exception e) {
                    // Tenta próximo
                }
            }
            
            // Se não for select padrão, pode ser DevExpress ou custom
            // Tenta clicar no campo e depois na opção
            Locator campo = page.locator("text=" + nomeCampo).first();
            if (campo.isVisible()) {
                campo.click();
                page.waitForTimeout(300);
                Locator opcaoElemento = page.locator("text=" + opcao).first();
                opcaoElemento.click();
                System.out.println("[SmartPage] Opção selecionada via clique");
                return;
            }
            
            throw new RuntimeException("Campo de seleção não encontrado: " + nomeCampo);
            
        } catch (Exception e) {
            throw new RuntimeException("Erro ao selecionar opção: " + e.getMessage(), e);
        }
    }
    
    /**
     * Marca/desmarca um checkbox
     * @param nomeCheckbox Nome ou label do checkbox
     * @param marcar true para marcar, false para desmarcar
     */
    public void marcarCheckbox(String nomeCheckbox, boolean marcar) {
        System.out.println("[SmartPage] " + (marcar ? "Marcando" : "Desmarcando") + " checkbox: " + nomeCheckbox);
        
        try {
            // Procura checkbox por vários critérios
            String[] seletores = {
                "input[type='checkbox'][name*='" + nomeCheckbox + "' i]",
                "input[type='checkbox'][id*='" + nomeCheckbox + "' i]",
                "label:has-text('" + nomeCheckbox + "') input[type='checkbox']",
                "text=" + nomeCheckbox + " >> xpath=../input[@type='checkbox']"
            };
            
            for (String seletor : seletores) {
                try {
                    Locator checkbox = page.locator(seletor).first();
                    if (checkbox.isVisible()) {
                        boolean estaMarcado = checkbox.isChecked();
                        if (marcar && !estaMarcado) {
                            checkbox.check();
                        } else if (!marcar && estaMarcado) {
                            checkbox.uncheck();
                        }
                        System.out.println("[SmartPage] Checkbox atualizado");
                        return;
                    }
                } catch (Exception e) {
                    // Tenta próximo
                }
            }
            
            throw new RuntimeException("Checkbox não encontrado: " + nomeCheckbox);
            
        } catch (Exception e) {
            throw new RuntimeException("Erro no checkbox: " + e.getMessage(), e);
        }
    }
    
    /**
     * Executa uma ação baseada em descrição textual
     * @param acao Descrição da ação (ex: "preencher campo Nome com João")
     */
    public void executarAcao(String acao) {
        System.out.println("[SmartPage] Executando ação: " + acao);
        
        String acaoLower = acao.toLowerCase();
        
        try {
            // Ação: clicar em X
            if (acaoLower.contains("clicar em") || acaoLower.contains("clicar no")) {
                String elemento = acao.replaceAll("(?i)clicar (em|no|na) ", "").trim();
                clicarPorTexto(elemento);
            }
            // Ação: preencher campo X com Y
            else if (acaoLower.contains("preencher") && acaoLower.contains("com")) {
                String[] partes = acao.split("(?i)com");
                if (partes.length >= 2) {
                    String campo = partes[0].replaceAll("(?i)preencher (campo|o campo)?", "").trim();
                    String valor = partes[1].trim();
                    preencherCampo(campo, valor);
                }
            }
            // Ação: selecionar X em Y
            else if (acaoLower.contains("selecionar") && acaoLower.contains("em")) {
                String[] partes = acao.split("(?i)em");
                if (partes.length >= 2) {
                    String opcao = partes[0].replaceAll("(?i)selecionar", "").trim();
                    String campo = partes[1].trim();
                    selecionarOpcao(campo, opcao);
                }
            }
            // Ação: marcar checkbox X
            else if (acaoLower.contains("marcar") || acaoLower.contains("checkbox")) {
                String checkbox = acao.replaceAll("(?i)(marcar|checkbox|o checkbox)", "").trim();
                marcarCheckbox(checkbox, true);
            }
            // Ação: navegar para X > Y
            else if (acaoLower.contains("navegar") && acaoLower.contains(">")) {
                String[] partes = acao.split(">");
                String menu = partes[0].replaceAll("(?i)navegar (para|em)?", "").trim();
                String submenu = partes.length > 1 ? partes[1].trim() : null;
                navegarParaMenu(menu, submenu);
            }
            else {
                System.out.println("[SmartPage] Ação não reconhecida: " + acao);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Erro ao executar ação '" + acao + "': " + e.getMessage(), e);
        }
    }
    
    /**
     * Verifica se existe mensagem de sucesso na página
     */
    public boolean temMensagemSucesso() {
        String[] seletoresSucesso = {
            ".success", ".sucesso", ".mensagem-sucesso",
            ".alert-success", ".toast-success", ".dx-toast-success"
        };
        
        for (String seletor : seletoresSucesso) {
            try {
                if (page.locator(seletor).first().isVisible()) {
                    return true;
                }
            } catch (Exception e) {
                // Ignora
            }
        }
        return false;
    }
    
    /**
     * Aguarda modal/dialog abrir ou fechar
     */
    public void aguardarModal(boolean abrir) {
        System.out.println("[SmartPage] Aguardando modal " + (abrir ? "abrir" : "fechar"));
        
        int tentativas = 0;
        while (tentativas < 10) {
            boolean modalVisivel = detector.detectarModal();
            if (abrir && modalVisivel) return;
            if (!abrir && !modalVisivel) return;
            
            page.waitForTimeout(500);
            tentativas++;
        }
        
        System.out.println("[SmartPage] Timeout aguardando modal");
    }
}

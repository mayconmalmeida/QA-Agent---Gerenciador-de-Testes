package br.com.qasuite.core;

import com.microsoft.playwright.Page;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.List;

/**
 * Captura o contexto visual da página atual para fornecer à IA.
 * Extrai elementos interativos visíveis com seus textos, roles e atributos.
 */
public class SmartContext {

    private static final Gson gson = new Gson();

    /**
     * Representa um elemento interativo encontrado na página
     */
    public static class ElementInfo {
        public String tag;
        public String role;
        public String texto;
        public String tipo;
        public String nome;
        public String id;
        public String placeholder;
        public String ariaLabel;
        public boolean disabled;
        public String className;
        public boolean insideDialog;
        public String dialogTitle;
        public String dialogId;
        public String dialogRole;
        public boolean ariaModal;
        public double top;
        public double left;
        public double width;
        public double height;
        public String testId;

        @Override
        public String toString() {
            return String.format("[%s] %s: '%s'", tag, role, texto);
        }
    }

    /**
     * Extrai elementos interativos visíveis da página atual
     */
    public static List<ElementInfo> capturar(Page page) {
        try {
            String json = (String) page.evaluate("""
                () => {
                    const interativos = [];
                    const seletores = [
                        // Elementos padrão interativos
                        'button:not([disabled])',
                        'a[href]',
                        'input:not([type="hidden"])',
                        'select',
                        'textarea',
                        // Roles ARIA
                        '[role="button"]',
                        '[role="tab"]',
                        '[role="menuitem"]',
                        '[role="option"]',
                        '[role="checkbox"]',
                        '[role="radio"]',
                        '[role="link"]',
                        '[role="dialog"]',
                        // Classes comuns de UI
                        '[class*="btn"]',
                        '[class*="button"]',
                        '[class*="card"]',
                        '[class*="item"]',
                        '[class*="list-item"]',
                        '[class*="option"]',
                        '[class*="selectable"]',
                        '[class*="unit"]',
                        '[class*="unidade"]',
                        // Divs clicáveis com texto
                        'div[onclick]',
                        '[class*="clickable"]',
                        // Elementos de lista
                        'li',
                        '.list-group-item',
                        '.dropdown-item',
                        // Labels e elementos de formulário
                        'label',
                        'th', 'td',
                        // Elementos de seleção customizada
                        '[class*="dx-list-item"]',
                        '[class*="dx-item"]',
                        '[class*="k-item"]',
                        '[class*="mat-list-item"]',
                        // Botões de ação comuns (toolbar, header, modal)
                        '[class*="action"]',
                        '[class*="toolbar"]',
                        '[class*="header"]',
                        '[class*="modal"] button',
                        '[class*="dialog"] button',
                        '[class*="popup"] button',
                        // Divs em modais e popups (para capturar elementos mesmo fora da viewport)
                        '[class*="modal"] div',
                        '[class*="dialog"] div',
                        '[class*="popup"] div',
                        '[class*="overlay"] div',
                        '[role="dialog"] div',
                        '[role="alertdialog"] div'
                    ];

                    seletores.forEach(sel => {
                        document.querySelectorAll(sel).forEach(el => {
                            const rect = el.getBoundingClientRect();
                            const style = window.getComputedStyle(el);
                            // Verifica se está visível (não apenas na viewport, mas em qualquer lugar da página)
                            // Isso é importante para modais e elementos fora da viewport inicial
                            const visivel = rect.width > 0 && rect.height > 0
                                            && style.display !== 'none'
                                            && style.visibility !== 'hidden'
                                            && style.opacity !== '0';
                            if (!visivel) return;

                            // Extrai texto de várias fontes possíveis
                            let texto = (el.innerText || '').trim();
                            if (!texto) {
                                texto = (el.value || '').trim();
                            }
                            if (!texto) {
                                texto = (el.placeholder || '').trim();
                            }
                            if (!texto) {
                                texto = (el.getAttribute('aria-label') || '').trim();
                            }
                            if (!texto) {
                                texto = (el.getAttribute('title') || '').trim();
                            }
                            if (!texto) {
                                texto = (el.getAttribute('data-text') || '').trim();
                            }
                            // Para cards/list items, tenta pegar primeiro filho de texto
                            if (!texto && el.children.length > 0) {
                                const primeiroTexto = el.querySelector('h1, h2, h3, h4, h5, h6, p, span, div');
                                if (primeiroTexto) {
                                    texto = primeiroTexto.innerText.trim();
                                }
                            }

                            if (!texto || texto.length < 1) return;

                            // Filtra textos muito longos (provavelmente conteúdo, não labels)
                            if (texto.length > 100) {
                                texto = texto.substring(0, 100) + '...';
                            }

                            const dialogEl = el.closest('[role="dialog"], [role="alertdialog"], .modal, [class*="modal"], [class*="dialog"], [class*="popup"], [class*="overlay"]');
                            let dialogTitle = '';
                            let dialogId = '';
                            let dialogRole = '';
                            let ariaModal = false;
                            if (dialogEl) {
                                dialogId = dialogEl.id || '';
                                dialogRole = dialogEl.getAttribute('role') || '';
                                ariaModal = dialogEl.getAttribute('aria-modal') === 'true';
                                const labelledby = dialogEl.getAttribute('aria-labelledby');
                                if (labelledby) {
                                    const labelEl = document.getElementById(labelledby);
                                    if (labelEl) {
                                        dialogTitle = (labelEl.innerText || '').trim();
                                    }
                                }
                                if (!dialogTitle) {
                                    const heading = dialogEl.querySelector('h1,h2,h3,h4,[class*="title"],[class*="header"]');
                                    if (heading) {
                                        dialogTitle = (heading.innerText || '').trim();
                                    }
                                }
                                if (!dialogTitle) {
                                    dialogTitle = (dialogEl.getAttribute('aria-label') || '').trim();
                                }
                                if (dialogTitle && dialogTitle.length > 60) {
                                    dialogTitle = dialogTitle.substring(0, 60) + '...';
                                }
                            }

                            const ariaDisabled = el.getAttribute('aria-disabled') === 'true';
                            const disabled = !!(el.disabled || el.getAttribute('disabled') === 'true' || ariaDisabled);
                            const testId = el.getAttribute('data-testid') || el.getAttribute('data-test') || el.getAttribute('data-cy') || '';

                            interativos.push({
                                tag: el.tagName.toLowerCase(),
                                role: el.getAttribute('role') || inferirRole(el),
                                texto: texto,
                                tipo: el.getAttribute('type') || '',
                                nome: el.getAttribute('name') || '',
                                id: el.id || '',
                                placeholder: el.placeholder || '',
                                ariaLabel: el.getAttribute('aria-label') || '',
                                disabled: disabled,
                                className: el.className || '',
                                insideDialog: !!dialogEl,
                                dialogTitle: dialogTitle,
                                dialogId: dialogId,
                                dialogRole: dialogRole,
                                ariaModal: ariaModal,
                                top: rect.top,
                                left: rect.left,
                                width: rect.width,
                                height: rect.height,
                                testId: testId
                            });
                        });
                    });

                    // Função auxiliar para inferir role baseado na aparência
                    function inferirRole(el) {
                        const className = el.className || '';
                        const tag = el.tagName.toLowerCase();

                        if (className.includes('btn') || className.includes('button')) return 'button';
                        if (className.includes('card') || className.includes('item')) return 'option';
                        if (className.includes('checkbox')) return 'checkbox';
                        if (className.includes('radio')) return 'radio';
                        if (className.includes('tab')) return 'tab';
                        if (className.includes('menu')) return 'menuitem';
                        if (tag === 'a') return 'link';
                        if (tag === 'input' || tag === 'textarea') return 'textbox';
                        if (tag === 'select') return 'combobox';
                        if (tag === 'li') return 'listitem';

                        return tag;
                    }

                    // Remove duplicados por texto + tag
                    const unicos = [];
                    const vistos = new Set();
                    interativos.forEach(el => {
                        const chave = el.texto.toLowerCase().substring(0, 50) + '|' + el.tag + '|' + (el.insideDialog ? 'dlg' : 'page');
                        if (!vistos.has(chave) && el.texto.length > 0) {
                            vistos.add(chave);
                            unicos.push(el);
                        }
                    });

                    // Ordena por posição vertical (topo para baixo)
                    unicos.sort((a, b) => {
                        return (a.top || 0) - (b.top || 0);
                    });

                    return JSON.stringify(unicos.slice(0, 80));
                }
            """);

            return gson.fromJson(json, new TypeToken<List<ElementInfo>>(){}.getType());
        } catch (Exception e) {
            System.err.println("[SmartContext] Erro ao capturar contexto: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Formata a lista de elementos para enviar à IA como string
     */
    public static String formatarParaPrompt(List<ElementInfo> elementos) {
        if (elementos == null || elementos.isEmpty()) {
            return "Nenhum elemento interativo visível encontrado.";
        }

        StringBuilder sb = new StringBuilder();
        boolean hasDialog = false;
        java.util.LinkedHashSet<String> dialogTitles = new java.util.LinkedHashSet<>();
        for (ElementInfo el : elementos) {
            if (el.insideDialog) {
                hasDialog = true;
                if (el.dialogTitle != null && !el.dialogTitle.isBlank()) {
                    dialogTitles.add(el.dialogTitle.trim());
                }
            }
        }

        if (hasDialog) {
            sb.append("Modal ativo detectado");
            if (!dialogTitles.isEmpty()) {
                sb.append(": ").append(String.join(" | ", dialogTitles.stream().limit(2).toList()));
            }
            sb.append("\n");
        }

        sb.append("Elementos interativos visíveis na tela:\n");

        for (ElementInfo el : elementos) {
            sb.append(String.format("- [%s] role=%s texto='%s'",
                el.tag, el.role, el.texto));
            if (!el.tipo.isEmpty()) sb.append(" tipo=").append(el.tipo);
            if (!el.id.isEmpty()) sb.append(" id=").append(el.id);
            if (el.insideDialog) sb.append(" modal=true");
            if (el.disabled) sb.append(" disabled=true");
            if (el.testId != null && !el.testId.isEmpty()) sb.append(" testId=").append(el.testId);
            if (el.placeholder != null && !el.placeholder.isEmpty() && (
                (el.role != null && el.role.equalsIgnoreCase("textbox")) ||
                (el.tag != null && (el.tag.equalsIgnoreCase("input") || el.tag.equalsIgnoreCase("textarea"))) ||
                (el.tipo != null && (el.tipo.equalsIgnoreCase("text") || el.tipo.equalsIgnoreCase("search")))
            )) {
                sb.append(" placeholder='").append(el.placeholder).append("'");
            }
            if (el.ariaLabel != null && !el.ariaLabel.isEmpty() && (el.texto == null || el.texto.equals("?") || el.texto.length() < 3)) {
                sb.append(" ariaLabel='").append(el.ariaLabel).append("'");
            }
            if (el.dialogTitle != null && !el.dialogTitle.isEmpty() && el.insideDialog) {
                sb.append(" dialog='").append(el.dialogTitle).append("'");
            }
            sb.append(" y=").append((int) Math.round(el.top));
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Captura e formata em uma chamada única
     */
    public static String capturarEFormatar(Page page) {
        return formatarParaPrompt(capturar(page));
    }
}

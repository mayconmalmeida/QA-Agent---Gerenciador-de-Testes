package br.com.qasuite.core;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import java.util.*;

/**
 * Detector inteligente de elementos de UI.
 * Analisa a página e identifica campos, botões, menus automaticamente.
 */
public class ElementDetector {
    
    private final Page page;
    private static final int TIMEOUT_MS = 5000;
    
    public ElementDetector(Page page) {
        this.page = page;
    }
    
    /**
     * Detecta campos de formulário (input, select, textarea)
     */
    public List<UIElement> detectarCamposFormulario() {
        List<UIElement> campos = new ArrayList<>();
        
        // Seletores comuns para campos de formulário
        String[] seletores = {
            "input[type='text']", "input[type='email']", "input[type='password']",
            "input[type='number']", "input[type='tel']", "input[type='date']",
            "input:not([type])", // inputs sem tipo (default text)
            "textarea", "select", "input[type='checkbox']", "input[type='radio']"
        };
        
        for (String seletor : seletores) {
            try {
                Locator elementos = page.locator(seletor);
                int count = elementos.count();
                
                for (int i = 0; i < count; i++) {
                    Locator elemento = elementos.nth(i);
                    if (elemento.isVisible()) {
                        UIElement uiElement = extrairInfoElemento(elemento, seletor, i);
                        if (uiElement != null) campos.add(uiElement);
                    }
                }
            } catch (Exception e) {
                // Ignora seletores não encontrados
            }
        }
        
        return campos;
    }
    
    /**
     * Detecta botões na página
     */
    public List<UIElement> detectarBotoes() {
        List<UIElement> botoes = new ArrayList<>();
        
        String[] seletores = {
            "button", "input[type='submit']", "input[type='button']",
            "[role='button']", ".btn", ".button", "a.btn", "a.button",
            ".dx-button", // DevExpress
            "[class*='button' i]"
        };
        
        for (String seletor : seletores) {
            try {
                Locator elementos = page.locator(seletor);
                int count = elementos.count();
                
                for (int i = 0; i < count; i++) {
                    Locator elemento = elementos.nth(i);
                    if (elemento.isVisible()) {
                        UIElement uiElement = extrairInfoElemento(elemento, seletor, i);
                        if (uiElement != null) botoes.add(uiElement);
                    }
                }
            } catch (Exception e) {
                // Ignora
            }
        }
        
        return botoes;
    }
    
    /**
     * Detecta links de menu/navegação
     */
    public List<UIElement> detectarMenus() {
        List<UIElement> menus = new ArrayList<>();
        
        String[] seletores = {
            "nav a", ".menu a", ".nav-link", "[role='menuitem']",
            "header a", ".sidebar a", ".navbar a", "ul.nav li a"
        };
        
        for (String seletor : seletores) {
            try {
                Locator elementos = page.locator(seletor);
                int count = elementos.count();
                
                for (int i = 0; i < count; i++) {
                    Locator elemento = elementos.nth(i);
                    if (elemento.isVisible()) {
                        UIElement uiElement = extrairInfoElemento(elemento, seletor, i);
                        if (uiElement != null) menus.add(uiElement);
                    }
                }
            } catch (Exception e) {
                // Ignora
            }
        }
        
        return menus;
    }
    
    /**
     * Detecta modal/popup na página
     */
    public boolean detectarModal() {
        String[] seletoresModal = {
            "[role='dialog']", ".modal", ".popup", ".dialog", ".overlay",
            ".dx-popup", ".dx-overlay", // DevExpress
            "[class*='modal' i]", "[class*='popup' i]"
        };
        
        for (String seletor : seletoresModal) {
            try {
                Locator modal = page.locator(seletor).first();
                if (modal.isVisible()) return true;
            } catch (Exception e) {
                // Ignora
            }
        }
        return false;
    }
    
    /**
     * Detecta campos de login (usuário e senha)
     */
    public Map<String, UIElement> detectarCamposLogin() {
        Map<String, UIElement> camposLogin = new HashMap<>();
        
        List<UIElement> todosCampos = detectarCamposFormulario();
        
        System.out.println("[ElementDetector] Total de campos detectados: " + todosCampos.size());
        
        for (UIElement campo : todosCampos) {
            String texto = campo.getTexto().toLowerCase();
            String placeholder = campo.getPlaceholder().toLowerCase();
            String ariaLabel = campo.getAriaLabel().toLowerCase();
            String tipo = campo.getTipo();
            String id = campo.getId().toLowerCase();
            String name = campo.getName().toLowerCase();
            
            System.out.println("[ElementDetector] Campo: id=" + id + ", name=" + name + ", type=" + tipo + ", placeholder=" + placeholder);
            
            // Detecta campo de usuário
            if (tipo.equals("email") || tipo.equals("text") ||
                texto.contains("usuário") || texto.contains("usuario") || 
                texto.contains("user") || texto.contains("login") ||
                placeholder.contains("usuário") || placeholder.contains("usuario") ||
                placeholder.contains("email") || placeholder.contains("login") ||
                placeholder.contains("cpf") || placeholder.contains("cnpj") ||
                ariaLabel.contains("usuário") || ariaLabel.contains("login") ||
                id.contains("usuario") || id.contains("user") || id.contains("login") || id.contains("email") ||
                name.contains("usuario") || name.contains("user") || name.contains("login") || name.contains("email")) {
                camposLogin.put("usuario", campo);
                System.out.println("[ElementDetector] Campo de usuário identificado: " + campo.getSeletorPlaywright());
            }
            
            // Detecta campo de senha
            if (tipo.equals("password") || 
                texto.contains("senha") || texto.contains("password") ||
                placeholder.contains("senha") || placeholder.contains("password") ||
                ariaLabel.contains("senha") || ariaLabel.contains("password") ||
                id.contains("senha") || id.contains("password") ||
                name.contains("senha") || name.contains("password")) {
                camposLogin.put("senha", campo);
                System.out.println("[ElementDetector] Campo de senha identificado: " + campo.getSeletorPlaywright());
            }
        }
        
        // Fallback: se não encontrou usuário, usa o primeiro campo text/email
        if (!camposLogin.containsKey("usuario") && !todosCampos.isEmpty()) {
            for (UIElement campo : todosCampos) {
                String tipo = campo.getTipo();
                if (tipo.equals("text") || tipo.equals("email")) {
                    camposLogin.put("usuario", campo);
                    System.out.println("[ElementDetector] Fallback: usando primeiro campo text como usuário");
                    break;
                }
            }
        }
        
        // Fallback: se não encontrou senha, usa o primeiro campo password
        if (!camposLogin.containsKey("senha") && !todosCampos.isEmpty()) {
            for (UIElement campo : todosCampos) {
                if (campo.getTipo().equals("password")) {
                    camposLogin.put("senha", campo);
                    System.out.println("[ElementDetector] Fallback: usando primeiro campo password como senha");
                    break;
                }
            }
        }
        
        return camposLogin;
    }
    
    /**
     * Detecta botão de submit/login
     */
    public UIElement detectarBotaoLogin() {
        List<UIElement> botoes = detectarBotoes();
        
        for (UIElement botao : botoes) {
            String texto = botao.getTexto().toLowerCase();
            
            if (texto.contains("entrar") || texto.contains("login") || 
                texto.contains("acessar") || texto.contains("sign in") ||
                texto.contains("entrar") || texto.contains("submit") ||
                texto.contains("continuar") || texto.contains("acessar")) {
                return botao;
            }
        }
        
        // Se não encontrar por texto, retorna o primeiro botão visível
        if (!botoes.isEmpty()) {
            return botoes.get(0);
        }
        
        return null;
    }
    
    /**
     * Extrai informações de um elemento
     */
    private UIElement extrairInfoElemento(Locator elemento, String seletor, int index) {
        try {
            String texto = elemento.textContent().trim();
            String placeholder = "";
            String ariaLabel = "";
            String tipo = "";
            String id = "";
            String name = "";
            
            try {
                placeholder = elemento.getAttribute("placeholder");
                if (placeholder == null) placeholder = "";
            } catch (Exception e) {}
            
            try {
                ariaLabel = elemento.getAttribute("aria-label");
                if (ariaLabel == null) ariaLabel = "";
            } catch (Exception e) {}
            
            try {
                tipo = elemento.getAttribute("type");
                if (tipo == null) tipo = "";
            } catch (Exception e) {}
            
            try {
                id = elemento.getAttribute("id");
                if (id == null) id = "";
            } catch (Exception e) {}
            
            try {
                name = elemento.getAttribute("name");
                if (name == null) name = "";
            } catch (Exception e) {}
            
            return new UIElement(seletor, index, texto, placeholder, ariaLabel, tipo, id, name);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Gera um relatório de análise da página
     */
    public String gerarRelatorio() {
        StringBuilder relatorio = new StringBuilder();
        relatorio.append("=== ANÁLISE DA PÁGINA ===\n\n");
        
        List<UIElement> campos = detectarCamposFormulario();
        relatorio.append("Campos de formulário: ").append(campos.size()).append("\n");
        for (UIElement campo : campos) {
            relatorio.append("  - ").append(campo.toString()).append("\n");
        }
        
        List<UIElement> botoes = detectarBotoes();
        relatorio.append("\nBotões: ").append(botoes.size()).append("\n");
        for (UIElement botao : botoes) {
            relatorio.append("  - ").append(botao.toString()).append("\n");
        }
        
        List<UIElement> menus = detectarMenus();
        relatorio.append("\nMenus: ").append(menus.size()).append("\n");
        for (UIElement menu : menus) {
            relatorio.append("  - ").append(menu.toString()).append("\n");
        }
        
        Map<String, UIElement> camposLogin = detectarCamposLogin();
        relatorio.append("\nCampos de Login detectados:\n");
        if (camposLogin.containsKey("usuario")) {
            relatorio.append("  ✓ Usuário: ").append(camposLogin.get("usuario").toString()).append("\n");
        }
        if (camposLogin.containsKey("senha")) {
            relatorio.append("  ✓ Senha: ").append(camposLogin.get("senha").toString()).append("\n");
        }
        
        UIElement botaoLogin = detectarBotaoLogin();
        if (botaoLogin != null) {
            relatorio.append("  ✓ Botão Login: ").append(botaoLogin.toString()).append("\n");
        }
        
        return relatorio.toString();
    }
}

package br.com.qasuite.core;

import com.google.gson.*;
import java.util.*;
import java.util.regex.*;

/**
 * Gera código de teste automaticamente baseado em descrições textuais.
 * Converte passos em português para código Java/Playwright executável.
 */
public class TestGenerator {
    
    /**
     * Representa um passo de teste parseado
     */
    public static class TestStep {
        public String action;      // tipo: click, fill, select, navigate
        public String target;      // elemento alvo
        public String value;       // valor a preencher/selecionar
        public String original;    // texto original
        
        public TestStep(String action, String target, String value, String original) {
            this.action = action;
            this.target = target;
            this.value = value;
            this.original = original;
        }
    }
    
    /**
     * Parseia uma descrição de teste em passos estruturados
     */
    public List<TestStep> parseSteps(String description) {
        List<TestStep> steps = new ArrayList<>();
        
        // Divide por números (1. 2. 3.) ou quebras de linha
        String[] lines = description.split("\\n|\\r");
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            // Remove número inicial (ex: "1. " ou "1- ")
            line = line.replaceFirst("^\\d+[.\\-\\)]\\s*", "").trim();
            
            TestStep step = parseSingleStep(line);
            if (step != null) {
                steps.add(step);
            }
        }
        
        return steps;
    }
    
    /**
     * Parseia um único passo
     */
    private TestStep parseSingleStep(String text) {
        String lower = text.toLowerCase();
        
        // 1. LOGIN
        if (lower.matches(".*(acessar|ir para|navegar para).*")) {
            String url = extractUrl(text);
            return new TestStep("navigate", url, null, text);
        }
        
        // 2. PREENCHER CAMPO
        if (lower.matches(".*(preencher|digitar|inserir).*campo.*com.*") ||
            lower.matches(".*(preencher|digitar|inserir).*com.*")) {
            return parseFillStep(text);
        }
        
        // 3. CLICAR
        if (lower.matches(".*(clicar|clique|pressionar).*")) {
            return parseClickStep(text);
        }
        
        // 4. SELECIONAR
        if (lower.matches(".*(selecionar|escolher|marcar).*")) {
            return parseSelectStep(text);
        }
        
        // 5. NAVEGAR MENU
        if (lower.matches(".*(navegar|ir|acessar).*menu.*") ||
            lower.contains(">")) {
            return parseNavigateStep(text);
        }
        
        // 6. VALIDAR/VERIFICAR
        if (lower.matches(".*(validar|verificar|checar|confirmar).*")) {
            return parseValidateStep(text);
        }
        
        // Passo não reconhecido
        System.out.println("[TestGenerator] Passo não reconhecido: " + text);
        return null;
    }
    
    /**
     * Extrai URL de um texto
     */
    private String extractUrl(String text) {
        Pattern pattern = Pattern.compile("(http[s]?://[^\\s]+)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    /**
     * Parseia passo de preenchimento
     */
    private TestStep parseFillStep(String text) {
        // "Preencher campo Nome com João"
        // "Preencher o campo 'Data' com '2024-01-01'"
        
        String campo = null;
        String valor = null;
        
        // Tenta extrair campo e valor
        Pattern pattern = Pattern.compile(
            "(?i)(preencher|digitar|inserir)\\s+(?:o\\s+)?(?:campo\\s+)?['\"]?([^'\"]+)['\"]?\\s+com\\s+['\"]?([^'\"]+)['\"]?");
        Matcher matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            campo = matcher.group(2).trim();
            valor = matcher.group(3).trim();
        } else {
            // Fallback: procura "campo X com Y"
            String[] parts = text.split("(?i)com");
            if (parts.length >= 2) {
                campo = parts[0].replaceAll("(?i)(preencher|campo)", "").trim();
                valor = parts[1].trim();
            }
        }
        
        return new TestStep("fill", campo, valor, text);
    }
    
    /**
     * Parseia passo de clique
     */
    private TestStep parseClickStep(String text) {
        // "Clicar em Inserir"
        // "Clicar no botão Salvar"
        
        String elemento = text.replaceAll("(?i)(clicar|clique|pressionar)\\s+(em|no|na)\\s+", "").trim();
        
        // Remove palavras desnecessárias
        elemento = elemento.replaceAll("(?i)^(botão|link|botao|menu|aba|guia)\\s+", "").trim();
        
        return new TestStep("click", elemento, null, text);
    }
    
    /**
     * Parseia passo de seleção
     */
    private TestStep parseSelectStep(String text) {
        // "Selecionar 'Opção 1' no campo Tipo"
        // "Marcar checkbox Ativo"
        
        String opcao = null;
        String campo = null;
        
        // Pattern: "selecionar X em Y" ou "selecionar X no campo Y"
        Pattern pattern = Pattern.compile(
            "(?i)selecionar\\s+['\"]?([^'\"]+)['\"]?\\s+(?:em|no campo|na)\\s+['\"]?([^'\"]+)['\"]?");
        Matcher matcher = pattern.matcher(text);
        
        if (matcher.find()) {
            opcao = matcher.group(1).trim();
            campo = matcher.group(2).trim();
        } else {
            // Fallback
            opcao = text.replaceAll("(?i)(selecionar|escolher|marcar)\\s+", "").trim();
        }
        
        return new TestStep("select", campo, opcao, text);
    }
    
    /**
     * Parseia passo de navegação
     */
    private TestStep parseNavigateStep(String text) {
        // "Navegar em Atenção Primária > Acolhimento"
        // "Ir para menu Configurações > Usuários"
        
        String caminho = text.replaceAll("(?i)(navegar|ir|acessar)\\s+(em|para|ao)?\\s+(menu\\s+)?", "").trim();
        
        return new TestStep("navigate_menu", caminho, null, text);
    }
    
    /**
     * Parseia passo de validação
     */
    private TestStep parseValidateStep(String text) {
        // "Validar mensagem de sucesso"
        // "Verificar que o campo Nome está preenchido"
        
        String alvo = text.replaceAll("(?i)(validar|verificar|checar|confirmar|que\\s+o|que\\s+a)\\s+", "").trim();
        
        return new TestStep("validate", alvo, null, text);
    }
    
    /**
     * Gera código Java a partir dos passos
     */
    public String generateJavaCode(String className, String description, List<TestStep> steps) {
        StringBuilder code = new StringBuilder();
        
        code.append("package br.com.qasuite.pages.generated;\n\n");
        code.append("import br.com.qasuite.config.BaseTest;\n");
        code.append("import br.com.qasuite.core.SmartLogin;\n");
        code.append("import br.com.qasuite.core.SmartPage;\n");
        code.append("import org.junit.jupiter.api.Test;\n");
        code.append("import org.junit.jupiter.api.Tag;\n\n");
        
        code.append("public class ").append(className).append(" extends BaseTest {\n\n");
        
        code.append("    @Override\n");
        code.append("    protected String getTipoTeste() {\n");
        code.append("        return \"smoke\";\n");
        code.append("    }\n\n");
        
        code.append("    @Test\n");
        code.append("    @Tag(\"smoke\")\n");
        code.append("    public void test").append(className.replace("Test", "")).append("() {\n");
        code.append("        System.out.println(\"[Test] Iniciando: ").append(description.replace("\"", "\\\"")).append("\");\n\n");
        
        code.append("        // Instancia helpers inteligentes\n");
        code.append("        SmartLogin smartLogin = new SmartLogin(page);\n");
        code.append("        SmartPage smartPage = new SmartPage(page);\n\n");
        
        // Gera código para cada passo
        for (TestStep step : steps) {
            code.append(generateStepCode(step)).append("\n");
        }
        
        code.append("\n        System.out.println(\"[Test] Teste concluído com sucesso!\");\n");
        code.append("    }\n");
        code.append("}\n");
        
        return code.toString();
    }
    
    /**
     * Gera código para um passo específico
     */
    private String generateStepCode(TestStep step) {
        StringBuilder code = new StringBuilder();
        
        switch (step.action) {
            case "navigate":
                code.append("        // ").append(step.original).append("\n");
                if (step.target != null) {
                    code.append("        page.navigate(\"").append(step.target).append("\");\n");
                }
                break;
                
            case "fill":
                code.append("        // ").append(step.original).append("\n");
                code.append("        smartPage.preencherCampo(\"").append(escape(step.target)).append("\", \"").append(escape(step.value)).append("\");\n");
                break;
                
            case "click":
                code.append("        // ").append(step.original).append("\n");
                code.append("        smartPage.clicarPorTexto(\"").append(escape(step.target)).append("\");\n");
                break;
                
            case "select":
                code.append("        // ").append(step.original).append("\n");
                code.append("        smartPage.selecionarOpcao(\"").append(escape(step.target)).append("\", \"").append(escape(step.value)).append("\");\n");
                break;
                
            case "navigate_menu":
                code.append("        // ").append(step.original).append("\n");
                String[] menuParts = step.target.split(">");
                if (menuParts.length >= 2) {
                    code.append("        smartPage.navegarParaMenu(\"").append(escape(menuParts[0].trim())).append("\", \"").append(escape(menuParts[1].trim())).append("\");\n");
                } else {
                    code.append("        smartPage.navegarParaMenu(\"").append(escape(step.target)).append("\", null);\n");
                }
                break;
                
            case "validate":
                code.append("        // ").append(step.original).append("\n");
                code.append("        assertTrue(smartPage.temMensagemSucesso(), \"Mensagem de sucesso não encontrada\");\n");
                break;
                
            default:
                code.append("        // [NÃO IMPLEMENTADO] ").append(step.original).append("\n");
        }
        
        return code.toString();
    }
    
    /**
     * Escapa strings para código Java
     */
    private String escape(String text) {
        if (text == null) return "";
        return text.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
    
    /**
     * Gera teste completo a partir de descrição JSON (formato do GUI)
     */
    public String generateFromJson(String jsonDescription) {
        try {
            JsonObject json = JsonParser.parseString(jsonDescription).getAsJsonObject();
            
            String name = json.get("name").getAsString();
            String description = json.get("description").getAsString();
            String className = sanitizeClassName(name) + "Test";
            
            List<TestStep> steps = parseSteps(description);
            
            return generateJavaCode(className, description, steps);
            
        } catch (Exception e) {
            System.err.println("[TestGenerator] ERRO ao parsear JSON: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Sanitiza nome para nome de classe Java válido
     */
    private String sanitizeClassName(String name) {
        // Remove acentos e caracteres especiais
        String sanitized = name.replaceAll("[^a-zA-Z0-9\\s]", "")  
                              .replaceAll("\\s+", "_")            
                              .replaceAll("_+", "_");             
        
        // Capitaliza cada palavra
        StringBuilder result = new StringBuilder();
        for (String part : sanitized.split("_")) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1).toLowerCase());
                }
            }
        }
        
        return result.toString();
    }
}

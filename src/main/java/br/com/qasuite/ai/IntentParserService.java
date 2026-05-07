package br.com.qasuite.ai;

import br.com.qasuite.config.OpenAIConfig;
import br.com.qasuite.domain.ActionType;
import br.com.qasuite.domain.TestPlan;
import br.com.qasuite.domain.TestStep;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço de interpretação de intenções usando OpenAI
 * Converte linguagem natural em DSL estruturada
 */
public class IntentParserService {

    private final OpenAIConfig config;
    private final HttpClient httpClient;
    private final Gson gson;

    public IntentParserService() {
        this.config = new OpenAIConfig();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();
        this.gson = new Gson();
    }

    public IntentParserService(String apiKey, String model) {
        this.config = new OpenAIConfig();
        if (apiKey != null && !apiKey.isEmpty()) {
            this.config.setApiKey(apiKey);
        }
        if (model != null && !model.isEmpty()) {
            this.config.setModel(model);
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();
        this.gson = new Gson();
    }

    /**
     * Verifica se a configuração é válida
     */
    public boolean isConfigured() {
        return config.isValid();
    }

    /**
     * Retorna mensagem de erro de configuração
     */
    public String getConfigError() {
        if (!config.isValid()) {
            return "OpenAI API key not configured. Please set OPENAI_API_KEY environment variable or configure via API.";
        }
        return null;
    }

    /**
     * Converte descrição em linguagem natural para TestPlan
     */
    public TestPlan parse(String description, String testName, String module, String menu) {
        System.out.println("[IntentParser] Parsing description...");

        // Verifica configuração
        if (!config.isValid()) {
            System.err.println("[IntentParser] ERROR: " + getConfigError());
            // Retorna plano vazio com erro
            TestPlan errorPlan = new TestPlan();
            errorPlan.setName(testName);
            errorPlan.setDescription("ERROR: " + getConfigError());
            return errorPlan;
        }

        try {
            String prompt = buildPrompt(description);
            String aiResponse = callOpenAI(prompt);
            List<TestStep> steps = extractStepsFromResponse(aiResponse);

            TestPlan plan = new TestPlan();
            plan.setName(testName);
            plan.setDescription(description);
            plan.setModule(module);
            plan.setMenu(menu);
            plan.setOriginalDescription(description);

            for (TestStep step : steps) {
                plan.addStep(step);
            }

            System.out.println("[IntentParser] Generated " + steps.size() + " steps");
            return plan;

        } catch (Exception e) {
            System.err.println("[IntentParser] Error: " + e.getMessage());
            // Fallback: cria plano com passo único
            return createFallbackPlan(description, testName, module, menu);
        }
    }

    /**
     * Constrói o prompt para a OpenAI
     */
    private String buildPrompt(String description) {
        return """
            You are a test automation expert. Convert the following test description into a structured JSON format.
            
            Available actions:
            - navigate: Navigate to URL (requires: url)
            - click: Click on element (requires: target)
            - fill: Fill input field (requires: target, value)
            - select: Select option from dropdown (requires: target, value)
            - checkbox: Check/uncheck checkbox (requires: target, value=true/false)
            - assert: Validate/assert something (requires: target, value, type=text_visible|element_exists|field_value)
            - wait: Wait for condition (requires: value=duration in seconds)
            - menu: Navigate through menu (requires: target=menu>submenu)
            
            Rules:
            1. Return ONLY a valid JSON array, no markdown, no explanations
            2. Each step must have: action, target (optional), value (optional), type (optional)
            3. Use Portuguese for target names as they appear in the UI
            4. Convert natural language to precise actions
            5. Break complex instructions into multiple steps
            6. Infer URL from context if mentioned
            
            Example input:
            "Acessar login, preencher usuário 'admin', senha '123', clicar em Entrar e validar mensagem de sucesso"
            
            Example output:
            [
              {"action":"navigate","target":"","value":"http://example.com/login"},
              {"action":"fill","target":"Usuário","value":"admin"},
              {"action":"fill","target":"Senha","value":"123"},
              {"action":"click","target":"Entrar"},
              {"action":"assert","target":"mensagem","value":"sucesso","type":"text_visible"}
            ]
            
            Now convert this description:
            """ + description + """
            
            JSON output:""";
    }

    /**
     * Chama a API da OpenAI
     */
    private String callOpenAI(String prompt) throws IOException, InterruptedException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.addProperty("temperature", config.getTemperature());
        requestBody.addProperty("max_tokens", config.getMaxTokens());

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);
        requestBody.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("API returned " + response.statusCode() + ": " + response.body());
        }

        return extractContentFromResponse(response.body());
    }

    /**
     * Extrai conteúdo da resposta da OpenAI
     */
    private String extractContentFromResponse(String responseBody) {
        JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonArray choices = jsonResponse.getAsJsonArray("choices");

        if (choices != null && !choices.isEmpty()) {
            JsonObject firstChoice = choices.get(0).getAsJsonObject();
            JsonObject message = firstChoice.getAsJsonObject("message");
            return message.get("content").getAsString().trim();
        }

        return "";
    }

    /**
     * Extrai passos do JSON retornado pela IA
     */
    private List<TestStep> extractStepsFromResponse(String aiResponse) {
        List<TestStep> steps = new ArrayList<>();

        try {
            // Remove markdown code blocks se houver
            String jsonStr = aiResponse;
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7);
            }
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3);
            }
            if (jsonStr.endsWith("```")) {
                jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
            }
            jsonStr = jsonStr.trim();

            JsonArray jsonArray = JsonParser.parseString(jsonStr).getAsJsonArray();

            for (int i = 0; i < jsonArray.size(); i++) {
                JsonObject stepObj = jsonArray.get(i).getAsJsonObject();
                TestStep step = parseStepFromJson(stepObj, i + 1);
                steps.add(step);
            }

        } catch (Exception e) {
            System.err.println("[IntentParser] Error parsing AI response: " + e.getMessage());
            System.err.println("[IntentParser] Raw response: " + aiResponse);
        }

        return steps;
    }

    /**
     * Converte JSON object para TestStep
     */
    private TestStep parseStepFromJson(JsonObject json, int order) {
        TestStep step = new TestStep();
        step.setOrder(order);

        // Parse action
        String actionStr = getStringOrEmpty(json, "action");
        step.setAction(ActionType.fromString(actionStr));

        // Parse target
        step.setTarget(getStringOrEmpty(json, "target"));

        // Parse value
        step.setValue(getStringOrEmpty(json, "value"));

        // Parse type
        step.setType(getStringOrEmpty(json, "type"));

        // Store original JSON
        step.setOriginalText(json.toString());

        return step;
    }

    private String getStringOrEmpty(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element != null && !element.isJsonNull()) {
            return element.getAsString();
        }
        return "";
    }

    /**
     * Cria plano fallback quando IA falha
     */
    private TestPlan createFallbackPlan(String description, String testName, String module, String menu) {
        TestPlan plan = new TestPlan();
        plan.setName(testName);
        plan.setDescription(description);
        plan.setModule(module);
        plan.setMenu(menu);

        // Parse manual simples
        String[] lines = description.split("\\r?\\n|\\.");
        int order = 1;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Remove números do início (1., 2., etc)
            line = line.replaceFirst("^\\d+\\.?\\s*", "");

            TestStep step = parseLineToStep(line, order++);
            if (step != null) {
                plan.addStep(step);
            }
        }

        return plan;
    }

    /**
     * Parse simples de linha para passo (fallback)
     */
    private TestStep parseLineToStep(String line, int order) {
        String lower = line.toLowerCase();
        TestStep step = new TestStep();
        step.setOrder(order);
        step.setOriginalText(line);

        if (lower.contains("acessar") || lower.contains("navegar")) {
            step.setAction(ActionType.NAVIGATE);
            step.setTarget(extractUrl(line));
        } else if (lower.contains("clicar")) {
            step.setAction(ActionType.CLICK);
            step.setTarget(extractTarget(line, "clicar"));
        } else if (lower.contains("preencher")) {
            step.setAction(ActionType.FILL);
            parseFillInstruction(line, step);
        } else if (lower.contains("selecionar")) {
            step.setAction(ActionType.SELECT);
            parseSelectInstruction(line, step);
        } else if (lower.contains("marcar") || lower.contains("checkbox")) {
            step.setAction(ActionType.CHECKBOX);
            step.setTarget(extractTarget(line, "marcar"));
        } else if (lower.contains("validar") || lower.contains("assert")) {
            step.setAction(ActionType.ASSERT);
            step.setTarget(extractTarget(line, "validar"));
        } else if (lower.contains("menu")) {
            step.setAction(ActionType.MENU);
            step.setTarget(extractTarget(line, "navegar"));
        } else {
            // Default: tentar detectar ação
            step.setAction(ActionType.CLICK);
            step.setTarget(line);
        }

        return step;
    }

    private String extractUrl(String text) {
        // Regex simples para extrair URL
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(https?://[^\\s]+)");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String extractTarget(String text, String keyword) {
        int idx = text.toLowerCase().indexOf(keyword);
        if (idx >= 0) {
            String after = text.substring(idx + keyword.length()).trim();
            after = after.replaceFirst("^(em|no|na|para|a|o|as|os)\\s+", "");
            return after.replaceFirst("^['\"]([^'\"]+)['\"].*$", "$1").trim();
        }
        return text;
    }

    private void parseFillInstruction(String text, TestStep step) {
        // "Preencher campo X com Y" ou "Preencher X com Y"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "preencher\\s+(?:o\\s+campo\\s+)?(.+?)\\s+com\\s+(.+)",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            step.setTarget(matcher.group(1).trim().replaceAll("['\"]", ""));
            step.setValue(matcher.group(2).trim().replaceAll("['\"]", ""));
        } else {
            step.setTarget(extractTarget(text, "preencher"));
        }
    }

    private void parseSelectInstruction(String text, TestStep step) {
        // "Selecionar X em Y" ou "Selecionar X"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "selecionar\\s+(.+?)\\s+(?:em|no|na)\\s+(.+)",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            step.setValue(matcher.group(1).trim().replaceAll("['\"]", ""));
            step.setTarget(matcher.group(2).trim().replaceAll("['\"]", ""));
        } else {
            step.setTarget(extractTarget(text, "selecionar"));
        }
    }

    // ============================================
    // NOVOS MÉTODOS PARA DECISÃO EM TEMPO REAL
    // ============================================

    /**
     * Decide qual ação executar baseado no passo e contexto atual da tela.
     * Usado durante a execução do teste, não durante o planejamento.
     */
    public br.com.qasuite.core.ActionDecision decideAction(String stepDescription,
                                                           String pageContext) {
        System.out.println("[IntentParser] Decidindo ação para: " + stepDescription);

        if (!config.isValid()) {
            System.err.println("[IntentParser] IA não configurada, usando fallback");
            return createFallbackDecision(stepDescription);
        }

        try {
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildDecisionPrompt(stepDescription, pageContext);

            // Debug: mostra o prompt enviado
            System.out.println("[IntentParser] === PROMPT ENVIADO ===");
            System.out.println("Passo: " + stepDescription);
            System.out.println("Contexto (primeiros 3000 chars): " + pageContext.substring(0, Math.min(3000, pageContext.length())));
            System.out.println("[IntentParser] === FIM DO PROMPT ===");

            String aiResponse = callOpenAIWithSystem(systemPrompt, userPrompt);
            
            // Debug: mostra resposta da IA
            System.out.println("[IntentParser] === RESPOSTA DA IA ===");
            System.out.println(aiResponse);
            System.out.println("[IntentParser] === FIM DA RESPOSTA ===");
            
            return br.com.qasuite.core.ActionDecision.fromJson(aiResponse);

        } catch (Exception e) {
            System.err.println("[IntentParser] Erro ao decidir ação: " + e.getMessage());
            return createFallbackDecision(stepDescription);
        }
    }

    /**
     * System prompt para a IA - define o comportamento do assistente
     */
    private String buildSystemPrompt() {
        return """
            Você é um robô de automação de testes web usando Playwright.
            Recebe um passo em português e a lista de elementos visíveis na tela atual,
            e decide exatamente como executar aquele passo.

            REGRAS OBRIGATÓRIAS - SIGA EXATAMENTE O PASSO DESCRITO:
            1. Use SEMPRE o texto EXATO que aparece na lista de elementos
            2. SIGA A DESCRIÇÃO DO PASSO À RISCA - não invente ações diferentes
            3. Para campos de busca/input: use FILL + aguarde resultados
            4. Para abas: role=tab + texto da aba
            5. Se houver "Modal ativo detectado" no contexto: interaja APENAS com elementos marcados com modal=true até o modal fechar
            6. Para menus hierárquicos "X > Y": clique primeiro no menu pai X (role=option), aguarde, depois clique no submenu Y (role=option)
            7. Para modais: sempre aguarde o modal abrir antes de interagir
            8. Para checkbox: use CHECK com o label exato
            9. IMPORTANTE: Após selecionar item em lista/modal, clique em 'Continuar' ou 'Confirmar' se disponível
            10. IMPORTANTE: Para botões de ação como 'Inserir', 'Finalizar', 'Salvar', sempre use role=button + texto exato
            11. Retorne APENAS JSON válido, sem markdown, sem explicações

            Formatos de ação disponíveis:
              NAVIGATE   → { "action": "NAVIGATE", "url": "..." }
              CLICK      → { "action": "CLICK", "role": "button|link|tab|menuitem|option|cell", "text": "texto exato" }
              CLICK_NTH  → { "action": "CLICK_NTH", "role": "...", "text": "...", "index": 0 }
              FILL       → { "action": "FILL", "label": "label do campo", "value": "valor" }
              FILL_PLACEHOLDER → { "action": "FILL_PLACEHOLDER", "placeholder": "...", "value": "..." }
              CHECK      → { "action": "CHECK", "label": "texto do checkbox" }
              SELECT     → { "action": "SELECT", "label": "label do select", "option": "opção" }
              WAIT_TEXT  → { "action": "WAIT_TEXT", "text": "texto que deve aparecer" }
              WAIT_URL   → { "action": "WAIT_URL", "contains": "parte da url" }
              PRESS_KEY  → { "action": "PRESS_KEY", "key": "Enter|Tab|Escape" }
              ASSERT     → { "action": "ASSERT", "text": "texto esperado na tela" }
              WAIT       → { "action": "WAIT", "waitMs": 800 }

            Exemplo de resposta válida:
            {"action":"CLICK","role":"button","text":"Inserir","reason":"Botão Inserir encontrado na tela"}
            """;
    }

    /**
     * Monta o prompt do usuário com o passo e contexto da tela
     */
    private String buildDecisionPrompt(String stepDescription, String pageContext) {
        return """
            Passo a executar:
            %s

            %s

            Qual ação Playwright executar? Retorne apenas JSON com a decisão.
            """.formatted(stepDescription, pageContext);
    }

    /**
     * Chama a API da OpenAI com system prompt
     */
    private String callOpenAIWithSystem(String systemPrompt, String userPrompt)
            throws IOException, InterruptedException {

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.addProperty("temperature", 0.2); // Mais determinístico
        requestBody.addProperty("max_tokens", 500);

        // response_format como objeto JSON
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        requestBody.add("response_format", responseFormat);

        JsonArray messages = new JsonArray();

        // System message
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);
        messages.add(systemMessage);

        // User message
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);
        messages.add(userMessage);

        requestBody.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("API returned " + response.statusCode() + ": " + response.body());
        }

        return extractContentFromResponse(response.body());
    }

    /**
     * Cria decisão de fallback quando IA falha - versão melhorada com extração inteligente
     */
    private br.com.qasuite.core.ActionDecision createFallbackDecision(String stepDescription) {
        String lower = stepDescription.toLowerCase();

        // 1. NAVEGAÇÃO - só navegar se tiver URL completa (http:// ou https://)
        if ((lower.contains("acessar") || lower.contains("navegar")) && extractUrl(stepDescription).startsWith("http")) {
            String url = extractUrl(stepDescription);
            return new br.com.qasuite.core.ActionDecision.Builder()
                    .action(br.com.qasuite.core.ActionDecision.ActionType.NAVIGATE)
                    .url(url)
                    .reason("Fallback: navegação para URL")
                    .build();
        }

        // 2. MENU - navegação de menu hierárquica (ex: "Navegar em Atendimento > Escuta Inicial")
        // Quando tem ">", primeiro clica no menu pai, depois no submenu
        if (lower.contains("navegar") || lower.contains("menu") || lower.contains("ir para")) {
            String[] menuParts = extractMenuPathFull(stepDescription);
            String menuPai = menuParts[0];
            String submenu = menuParts[1];
            
            if (submenu != null) {
                // Menu hierárquico: primeiro clica no pai, depois no submenu
                // O submenu aparece como role=option no contexto
                return new br.com.qasuite.core.ActionDecision.Builder()
                        .action(br.com.qasuite.core.ActionDecision.ActionType.CLICK)
                        .role("option")  // Submenu aparece como role=option no contexto
                        .text(submenu)
                        .label(menuPai)  // Usa label para guardar o menu pai
                        .reason("Fallback: navegação hierárquica - clicar em '" + menuPai + "' depois '" + submenu + "'")
                        .build();
            } else if (!menuPai.isEmpty()) {
                // Menu simples (sem submenu)
                return new br.com.qasuite.core.ActionDecision.Builder()
                        .action(br.com.qasuite.core.ActionDecision.ActionType.CLICK)
                        .role("menuitem")
                        .text(menuPai)
                        .reason("Fallback: navegação de menu simples - clicar em '" + menuPai + "'")
                        .build();
            }
        }

        // 3. PREENCHIMENTO - extrair campo e valor
        if (lower.contains("preencher")) {
            FillInfo fillInfo = extractFillInfo(stepDescription);
            return new br.com.qasuite.core.ActionDecision.Builder()
                    .action(br.com.qasuite.core.ActionDecision.ActionType.FILL)
                    .label(fillInfo.field)
                    .value(fillInfo.value)
                    .reason("Fallback: preenchimento de campo")
                    .build();
        }

        // 4. SELEÇÃO DE UNIDADE/MODAL/LISTA (PRIORIDADE ALTA - antes do checkbox)
        // Detecta: "selecionar unidade X", "selecionar item X", "selecionar X no modal"
        // Também detecta "duplo clique" ou "double click"
        boolean isDoubleClick = lower.contains("duplo clique") || 
                                lower.contains("duplo-clique") || 
                                lower.contains("double click") ||
                                lower.contains("double-click");
        
        // 4. SELEÇÃO DE UNIDADE/MODAL/LISTA (PRIORIDADE ALTA - antes do checkbox)
        // Detecta: "selecionar unidade X", "selecionar item X", "selecionar X no modal"
        // Tenta extrair o texto EXATO entre aspas primeiro
        String quotedItem = extractQuotedText(stepDescription);
        
        if (lower.contains("selecionar unidade") || 
            lower.contains("selecionar item") ||
            lower.contains("selecionar da lista") ||
            lower.contains("modal")) {
            
            // Usa o texto entre aspas se encontrado, senão tenta extrair de outra forma
            String item = !quotedItem.isEmpty() ? quotedItem : extractGenericTarget(stepDescription, "selecionar|unidade|item");
            
            // Se é duplo clique, usar DOUBLE_CLICK
            if (isDoubleClick && !item.isEmpty()) {
                return new br.com.qasuite.core.ActionDecision.Builder()
                        .action(br.com.qasuite.core.ActionDecision.ActionType.DOUBLE_CLICK)
                        .role("option")
                        .text(item)
                        .reason("Fallback: duplo-clique no item '" + item + "'")
                        .build();
            }
            
            if (!item.isEmpty()) {
                // Tenta clicar no item específico (como uma opção de lista)
                return new br.com.qasuite.core.ActionDecision.Builder()
                        .action(br.com.qasuite.core.ActionDecision.ActionType.CLICK)
                        .role("option")
                        .text(item)
                        .reason("Fallback: selecionar '" + item + "' e clicar em Continuar")
                        .build();
            } else {
                // Se não especificou item, clica na primeira disponível
                return new br.com.qasuite.core.ActionDecision.Builder()
                        .action(br.com.qasuite.core.ActionDecision.ActionType.CLICK_NTH)
                        .role("option")
                        .index(0)
                        .reason("Fallback: clicar no primeiro item disponível")
                        .build();
            }
        }

        // 5. CLIQUE - extrair apenas o nome do elemento
        if (lower.contains("clicar")) {
            String target = extractClickTarget(stepDescription);
            return new br.com.qasuite.core.ActionDecision.Builder()
                    .action(br.com.qasuite.core.ActionDecision.ActionType.CLICK)
                    .role("button")
                    .text(target)
                    .reason("Fallback: clique em botão")
                    .build();
        }

        // 5b. BOTÕES COMUNS - tratamento especial para botões frequentes em formulários
        String[] botoesComuns = {"inserir", "finalizar", "salvar", "gravar", "confirmar", "cadastrar", "adicionar", "novo"};
        for (String botao : botoesComuns) {
            if (lower.contains(botao)) {
                // Capitaliza primeira letra
                String botaoCapitalizado = botao.substring(0, 1).toUpperCase() + botao.substring(1);
                return new br.com.qasuite.core.ActionDecision.Builder()
                        .action(br.com.qasuite.core.ActionDecision.ActionType.CLICK)
                        .role("button")
                        .text(botaoCapitalizado)
                        .reason("Fallback: clique no botão comum '" + botaoCapitalizado + "'")
                        .build();
            }
        }

        // 6. CHECKBOX (apenas quando explicitamente é checkbox, não "selecionar" genérico)
        if (lower.contains("marcar") || lower.contains("checkbox") || lower.contains("check")) {
            String target = extractCheckboxTarget(stepDescription);
            return new br.com.qasuite.core.ActionDecision.Builder()
                    .action(br.com.qasuite.core.ActionDecision.ActionType.CHECK)
                    .label(target)
                    .reason("Fallback: marcação de checkbox")
                    .build();
        }

        // 7. VALIDAÇÃO
        if (lower.contains("validar") || lower.contains("verificar") || lower.contains("aguardar")) {
            String target = extractValidationTarget(stepDescription);
            return new br.com.qasuite.core.ActionDecision.Builder()
                    .action(br.com.qasuite.core.ActionDecision.ActionType.WAIT_TEXT)
                    .text(target)
                    .reason("Fallback: validação de texto")
                    .build();
        }

        // 8. SELEÇÃO DE PRIMEIRO ITEM/RESULTADO (fallback para "selecionar" sozinho)
        if (lower.contains("selecionar") && (lower.contains("primeiro") || lower.contains("primeira"))) {
            return new br.com.qasuite.core.ActionDecision.Builder()
                    .action(br.com.qasuite.core.ActionDecision.ActionType.CLICK_NTH)
                    .role("option")
                    .index(0)
                    .reason("Fallback: clique no primeiro item da lista")
                    .build();
        }

        // 9. SELEÇÃO GENÉRICA - "selecionar X" como clique (último fallback antes de erro)
        if (lower.contains("selecionar")) {
            String target = extractQuotedText(stepDescription);
            if (target.isEmpty()) {
                target = extractGenericTarget(stepDescription, "selecionar");
            }
            return new br.com.qasuite.core.ActionDecision.Builder()
                    .action(br.com.qasuite.core.ActionDecision.ActionType.CLICK)
                    .role("option")
                    .text(target)
                    .reason("Fallback: selecionar item '" + target + "'")
                    .build();
        }

        // Default: WAIT (mais seguro que clique aleatório)
        return new br.com.qasuite.core.ActionDecision.Builder()
                .action(br.com.qasuite.core.ActionDecision.ActionType.WAIT)
                .waitMs(1000)
                .reason("Fallback: aguardando (ação não reconhecida: " + stepDescription.substring(0, Math.min(30, stepDescription.length())) + "...)")
                .build();
    }

    /**
     * Extrai informações de preenchimento (campo e valor)
     */
    private FillInfo extractFillInfo(String text) {
        FillInfo info = new FillInfo();
        info.field = "";
        info.value = "";

        // Padrões comuns:
        // "Preencher o campo X com o valor Y"
        // "Preencher X com Y"
        // "Preencher campo X com Y"

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "preencher\\s+(?:o\\s+)?(?:campo\\s+)?(.+?)\\s+com\\s+(?:o\\s+)?(?:valor\\s+)?(.+?)$",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            info.field = matcher.group(1).trim().replaceAll("['\"]", "");
            info.value = matcher.group(2).trim().replaceAll("['\"]", "");

            // Limpar termos extras do campo
            info.field = info.field.replaceAll("(?i)^(campo|field|input)\\s*", "");

            return info;
        }

        // Fallback: extrair apenas o nome do campo
        String afterKeyword = extractAfterKeyword(text, "preencher");
        info.field = afterKeyword.replaceAll("(?i)^(o\\s+campo|campo)\\s*", "");

        return info;
    }

    private String extractClickTarget(String text) {
        String afterClick = extractAfterKeyword(text, "clicar");

        // Remover termos comuns
        afterClick = afterClick.replaceAll("(?i)^(em|no|na|a|o|as|os)\\s*", "");
        afterClick = afterClick.replaceAll("(?i)^(botão|botao|button|link|menu|aba|tab)\\s*", "");

        // Extrair texto entre aspas se houver
        String quoted = extractQuotedText(afterClick);
        if (!quoted.isEmpty()) {
            return quoted;
        }

        return afterClick.trim();
    }

    private String extractCheckboxTarget(String text) {
        String target = extractAfterKeyword(text, "marcar");
        target = target.replaceAll("(?i)^(o|a|os|as)\\s*", "");
        target = target.replaceAll("(?i)^checkbox\\s*", "");

        String quoted = extractQuotedText(target);
        if (!quoted.isEmpty()) {
            return quoted;
        }

        return target.trim();
    }

    private String extractValidationTarget(String text) {
        String target = extractAfterKeyword(text, "validar|verificar|aguardar");
        target = target.replaceAll("(?i)^(a|o|as|os)\\s*", "");
        // Não remove "mensagem" sozinho - só remove se vier com outras palavras de conexão
        target = target.replaceAll("(?i)^(texto|elemento|presença de)\\s*", "");

        String quoted = extractQuotedText(target);
        if (!quoted.isEmpty()) {
            return quoted;
        }

        // Se o target termina sendo muito curto (ex: "de sucesso"), 
        // tenta capturar mais contexto da frase original
        String trimmed = target.trim();
        if (trimmed.length() < 10 && !trimmed.contains("sucesso") && !trimmed.contains("erro")) {
            // Tenta extrair a frase completa após a palavra-chave
            String fullContext = extractGenericTarget(text, "validar|verificar|aguardar");
            if (!fullContext.isEmpty() && fullContext.length() > trimmed.length()) {
                return fullContext;
            }
        }

        return trimmed;
    }

    /**
     * Extrai o caminho completo do menu (pai > submenu)
     * Retorna array onde [0]=menu pai, [1]=submenu (se existir)
     */
    private String[] extractMenuPathFull(String text) {
        String afterNav = extractAfterKeyword(text, "navegar|menu|ir para");
        afterNav = afterNav.replaceAll("(?i)^(em|para|a|ao)\\s*", "");
        
        if (afterNav.contains(">")) {
            String[] parts = afterNav.split(">");
            String pai = parts[0].trim();
            String submenu = parts[parts.length - 1].trim();
            submenu = submenu.replaceAll("(?i)^(menu|submenu|item)\\s*", "");
            return new String[]{pai, submenu};
        }
        
        return new String[]{afterNav.trim(), null};
    }
    
    private String extractMenuPath(String text) {
        String[] parts = extractMenuPathFull(text);
        return parts[1] != null ? parts[1] : parts[0];
    }

    /**
     * Extrai texto genérico após uma palavra-chave
     * Ex: "selecionar Unidade para uso interno" -> "Unidade para uso interno"
     */
    private String extractGenericTarget(String text, String keyword) {
        String lower = text.toLowerCase();
        int idx = lower.indexOf(keyword.toLowerCase());
        if (idx >= 0) {
            String after = text.substring(idx + keyword.length()).trim();
            // Remove palavras comuns de conexão no início
            after = after.replaceAll("^(o|a|os|as|um|uma|de|do|da|em|no|na)\\s+", "");
            // Pega até 4 primeiras palavras ou até encontrar preposição
            String[] words = after.split("\\s+");
            StringBuilder result = new StringBuilder();
            int wordCount = 0;
            for (String word : words) {
                String lowerWord = word.toLowerCase();
                // Para se encontrar preposição ou conectivo após a primeira palavra
                if (wordCount > 0 && (lowerWord.equals("no") || lowerWord.equals("na") || 
                    lowerWord.equals("do") || lowerWord.equals("da") || lowerWord.equals("em"))) {
                    break;
                }
                if (wordCount > 0) result.append(" ");
                result.append(word);
                wordCount++;
                // Limita a 6 palavras para não pegar frases muito longas
                if (wordCount >= 6) break;
            }
            return result.toString().trim();
        }
        return "";
    }

    private String extractQuotedText(String text) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("['\"]([^'\"]+)['\"]");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String extractAfterKeyword(String text, String keywords) {
        String lower = text.toLowerCase();
        String[] keywordArray = keywords.split("\\|");

        for (String keyword : keywordArray) {
            int idx = lower.indexOf(keyword);
            if (idx >= 0) {
                return text.substring(idx + keyword.length()).trim();
            }
        }
        return text;
    }

    private static class FillInfo {
        String field;
        String value;
    }

    /**
     * Decide ação após preencher campo de busca (para selecionar resultado)
     */
    public br.com.qasuite.core.ActionDecision decideSearchResult(String searchValue,
                                                                  String pageContext) {
        System.out.println("[IntentParser] Decidindo resultado da busca para: " + searchValue);

        if (!config.isValid()) {
            // Fallback: clica no primeiro resultado que contém parte do valor
            return new br.com.qasuite.core.ActionDecision.Builder()
                    .action(br.com.qasuite.core.ActionDecision.ActionType.CLICK_NTH)
                    .role("option")
                    .text(searchValue)
                    .index(0)
                    .reason("Fallback: primeiro resultado")
                    .build();
        }

        try {
            String systemPrompt = buildSystemPrompt();
            String userPrompt = """
                Campo de busca foi preenchido com: %s

                %s

                Qual resultado da busca deve ser clicado? Retorne apenas JSON.
                """.formatted(searchValue, pageContext);

            String aiResponse = callOpenAIWithSystem(systemPrompt, userPrompt);
            return br.com.qasuite.core.ActionDecision.fromJson(aiResponse);

        } catch (Exception e) {
            System.err.println("[IntentParser] Erro ao decidir resultado: " + e.getMessage());
            return new br.com.qasuite.core.ActionDecision.Builder()
                    .action(br.com.qasuite.core.ActionDecision.ActionType.CLICK_NTH)
                    .role("option")
                    .text(searchValue)
                    .index(0)
                    .reason("Fallback após erro: primeiro resultado")
                    .build();
        }
    }
}

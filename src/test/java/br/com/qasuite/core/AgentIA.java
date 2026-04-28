package br.com.qasuite.core;

import br.com.qasuite.config.ConfigLoader;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Agente de IA para geração automática de artefatos de teste.
 * Suporta múltiplos provedores: OpenAI, Anthropic, Groq, Ollama (local)
 */
public class AgentIA {

    // Provedores suportados
    public enum Provedor {
        OPENAI,      // GPT-3.5/4 - necessita de OPENAI_API_KEY
        ANTHROPIC,   // Claude - necessita de ANTHROPIC_API_KEY
        GROQ,        // Llama/Mistral via Groq - tier gratuito disponível
        OLLAMA       // Modelos locais - totalmente gratuito
    }

    private static final String SYSTEM_PROMPT = """
            Você é um SDET especialista em sistemas hospitalares. O sistema Sinnc Saúde 
            é uma aplicação web hospitalar brasileira (SUS/convênios). Gere artefatos de 
            QA profissionais em português (pt-BR) considerando: LGPD, dados sensíveis de 
            pacientes, regras ANS/TISS, criticidade de prontuários e fluxos clínicos.
            
            Retorne APENAS JSON válido, sem markdown, sem explicações, sem código de formatação.
            O JSON deve seguir exatamente esta estrutura:
            {
              "gherkin": "arquivo .feature completo em pt-BR com # language: pt, Feature, Background (com login já feito pelo BaseTest), Scenario e/ou Scenario Outline cobrindo: caminho feliz, negativos e edge cases hospitalares",
              "java": "classe Java completa estendendo BaseTest, usa Page Objects correspondentes ao módulo, @Tag com o tipoTeste (smoke/regression/critico), comentários em português explicando cada bloco",
              "jira": "caso de teste formatado para Jira/Xray: ID: TC-[timestamp], Título, Módulo, Tipo, Prioridade, Pré-condições, Passos numerados (Ação | Resultado Esperado), Pós-condições, Critérios de Aceite, Tags, Risco Hospitalar",
              "cenarios": ["array", "com", "nome", "de", "cada", "cenário"],
              "tipos_cobertura": {
                "caminho_feliz": true,
                "negativo": true,
                "limite": false,
                "seguranca": false,
                "lgpd": false
              },
              "risco": "descrição do risco hospitalar se este teste falhar"
            }
            """;

    private final OkHttpClient httpClient;
    private final Gson gson;
    private final Provedor provedor;
    private final String apiKey;
    private final String model;
    private final String apiUrl;

    public AgentIA() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        
        // Detecta provedor baseado nas variáveis disponíveis
        this.provedor = detectarProvedor();
        
        // Configurações específicas por provedor
        switch (this.provedor) {
            case OPENAI -> {
                this.apiKey = ConfigLoader.get("OPENAI_API_KEY");
                this.model = ConfigLoader.get("OPENAI_MODEL", "gpt-3.5-turbo");
                this.apiUrl = ConfigLoader.get("OPENAI_API_URL", "https://api.openai.com/v1/chat/completions");
            }
            case ANTHROPIC -> {
                this.apiKey = ConfigLoader.get("ANTHROPIC_API_KEY");
                this.model = ConfigLoader.get("ANTHROPIC_MODEL", "claude-sonnet-4-20250514");
                this.apiUrl = ConfigLoader.get("ANTHROPIC_API_URL", "https://api.anthropic.com/v1/messages");
            }
            case GROQ -> {
                this.apiKey = ConfigLoader.get("GROQ_API_KEY");
                this.model = ConfigLoader.get("GROQ_MODEL", "llama-3.1-70b-versatile");
                this.apiUrl = ConfigLoader.get("GROQ_API_URL", "https://api.groq.com/openai/v1/chat/completions");
            }
            case OLLAMA -> {
                this.apiKey = ""; // Ollama não precisa de API key local
                this.model = ConfigLoader.get("OLLAMA_MODEL", "codellama");
                this.apiUrl = ConfigLoader.get("OLLAMA_API_URL", "http://localhost:11434/api/generate");
            }
            default -> throw new IllegalStateException("Provedor de IA não configurado");
        }

        if (this.provedor != Provedor.OLLAMA && (this.apiKey == null || this.apiKey.isEmpty())) {
            throw new IllegalStateException("API Key não configurada para " + this.provedor + 
                ". Configure no arquivo .env ou variável de ambiente.");
        }
        
        System.out.println("[AgentIA] Usando provedor: " + this.provedor + " com modelo: " + this.model);
    }
    
    private Provedor detectarProvedor() {
        // Prioridade: OpenAI > Anthropic > Groq > Ollama
        if (temChave("OPENAI_API_KEY")) return Provedor.OPENAI;
        if (temChave("ANTHROPIC_API_KEY")) return Provedor.ANTHROPIC;
        if (temChave("GROQ_API_KEY")) return Provedor.GROQ;
        return Provedor.OLLAMA; // Padrão: tenta Ollama local
    }
    
    private boolean temChave(String nome) {
        String valor = ConfigLoader.get(nome);
        return valor != null && !valor.isEmpty();
    }

    /**
     * Gera artefatos de teste a partir da descrição fornecida.
     *
     * @param descricao Descrição do teste
     * @return Artefatos gerados (Gherkin, Java, Jira)
     * @throws IOException em caso de erro na API
     */
    public ArtefatosTest gerarArtefatos(DescricaoTeste descricao) throws IOException {
        System.out.println("[AgentIA] Gerando artefatos para: " + descricao.getRequisito());
        
        String userPrompt = construirPromptUsuario(descricao);
        String respostaJson = chamarAPI(userPrompt);
        
        return parseResposta(respostaJson);
    }

    private String construirPromptUsuario(DescricaoTeste descricao) {
        StringBuilder sb = new StringBuilder();
        sb.append("Gere artefatos de teste para o seguinte requisito do sistema Sinnc Saúde:\n\n");
        sb.append("MÓDULO: ").append(descricao.getModulo()).append("\n");
        sb.append("REQUISITO: ").append(descricao.getRequisito()).append("\n");
        sb.append("PRIORIDADE: ").append(descricao.getPrioridade()).append("\n");
        sb.append("TIPO DE TESTE: ").append(descricao.getTipoTeste()).append("\n\n");
        
        if (descricao.getPassos() != null && !descricao.getPassos().isEmpty()) {
            sb.append("PASSOS DO FLUXO:\n");
            for (int i = 0; i < descricao.getPassos().size(); i++) {
                sb.append(i + 1).append(". ").append(descricao.getPassos().get(i)).append("\n");
            }
            sb.append("\n");
        }
        
        if (descricao.getTags() != null && !descricao.getTags().isEmpty()) {
            sb.append("TAGS ADICIONAIS: ").append(String.join(", ", descricao.getTags())).append("\n\n");
        }
        
        sb.append("IMPORTANTE:\n");
        sb.append("- O Gherkin DEVE ter '# language: pt' na primeira linha\n");
        sb.append("- O Background DEVE assumir que o login já foi feito pelo BaseTest\n");
        sb.append("- A classe Java DEVE estender BaseTest e ter a anotação @Tag(\"")
          .append(descricao.getTipoTeste()).append("\")\n");
        sb.append("- Use seletores por texto, role ou label (evite XPath fixo)\n");
        sb.append("- Considere aspectos de LGPD e dados sensíveis de saúde\n");
        sb.append("- Identifique riscos hospitalares se o teste falhar\n");
        
        return sb.toString();
    }

    private String chamarAPI(String userPrompt) throws IOException {
        return switch (provedor) {
            case OPENAI, GROQ -> chamarOpenAIFormat(userPrompt);
            case ANTHROPIC -> chamarAnthropicAPI(userPrompt);
            case OLLAMA -> chamarOllama(userPrompt);
        };
    }
    
    private String chamarOpenAIFormat(String userPrompt) throws IOException {
        // Formato compatível com OpenAI e Groq
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("max_tokens", 4096);
        requestBody.addProperty("temperature", 0.2);
        
        JsonArray messages = new JsonArray();
        
        // System message
        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", SYSTEM_PROMPT);
        messages.add(systemMsg);
        
        // User message
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);
        messages.add(userMsg);
        
        requestBody.add("messages", messages);
        
        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
        );
        
        Request.Builder requestBuilder = new Request.Builder()
                .url(apiUrl)
                .header("content-type", "application/json");
        
        // Autenticação diferente por provedor
        if (provedor == Provedor.GROQ) {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        } else {
            requestBuilder.header("Authorization", "Bearer " + apiKey);
        }
        
        Request request = requestBuilder.post(body).build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "N/A";
                throw new IOException("Erro na API " + provedor + ": " + response.code() + " - " + errorBody);
            }
            
            String responseBody = response.body().string();
            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
            
            // Extrair conteúdo - formato OpenAI/Groq
            if (jsonResponse.has("choices") && jsonResponse.get("choices").isJsonArray()) {
                return jsonResponse.getAsJsonArray("choices")
                        .get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString();
            }
            
            throw new IOException("Resposta inesperada da API: " + responseBody);
        }
    }

    private String chamarAnthropicAPI(String userPrompt) throws IOException {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("max_tokens", 4096);
        requestBody.addProperty("system", SYSTEM_PROMPT);
        
        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        
        JsonArray contentArray = new JsonArray();
        JsonObject content = new JsonObject();
        content.addProperty("type", "text");
        content.addProperty("text", userPrompt);
        contentArray.add(content);
        
        userMessage.add("content", contentArray);
        messages.add(userMessage);
        requestBody.add("messages", messages);
        
        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
                .url(apiUrl)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(body)
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "N/A";
                throw new IOException("Erro na API Anthropic: " + response.code() + " - " + errorBody);
            }
            
            String responseBody = response.body().string();
            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
            
            if (jsonResponse.has("content") && jsonResponse.get("content").isJsonArray()) {
                return jsonResponse.getAsJsonArray("content")
                        .get(0).getAsJsonObject()
                        .get("text").getAsString();
            }
            
            throw new IOException("Resposta inesperada da API: " + responseBody);
        }
    }
    
    private String chamarOllama(String userPrompt) throws IOException {
        // Ollama usa formato diferente
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        
        String fullPrompt = SYSTEM_PROMPT + "\n\n" + userPrompt;
        requestBody.addProperty("prompt", fullPrompt);
        requestBody.addProperty("stream", false);
        
        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
        );
        
        Request request = new Request.Builder()
                .url(apiUrl)
                .header("content-type", "application/json")
                .post(body)
                .build();
        
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "N/A";
                throw new IOException("Erro no Ollama: " + response.code() + " - " + errorBody + 
                    "\nVerifique se o Ollama está rodando: ollama serve");
            }
            
            String responseBody = response.body().string();
            JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
            
            if (jsonResponse.has("response")) {
                return jsonResponse.get("response").getAsString();
            }
            
            throw new IOException("Resposta inesperada do Ollama: " + responseBody);
        }
    }

    private ArtefatosTest parseResposta(String respostaJson) {
        try {
            // Limpar possíveis marcadores de código
            String cleaned = respostaJson
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();
            
            return gson.fromJson(cleaned, ArtefatosTest.class);
        } catch (Exception e) {
            System.err.println("[AgentIA] Erro ao parsear resposta: " + e.getMessage());
            System.err.println("[AgentIA] Resposta recebida: " + respostaJson);
            throw new RuntimeException("Falha ao parsear resposta da IA", e);
        }
    }
}

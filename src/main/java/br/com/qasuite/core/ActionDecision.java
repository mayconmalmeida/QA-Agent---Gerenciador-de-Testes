package br.com.qasuite.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Representa uma decisão de ação tomada pela IA.
 * Mapeia diretamente para comandos Playwright.
 */
public class ActionDecision {

    // Tipos de ação suportados
    public enum ActionType {
        NAVIGATE,       // Navegar para URL
        CLICK,          // Clicar em elemento por role + texto
        CLICK_NTH,      // Clicar no n-ésimo elemento
        DOUBLE_CLICK,   // Duplo-clique em elemento
        FILL,           // Preencher campo por label
        FILL_PLACEHOLDER, // Preencher por placeholder
        CHECK,          // Marcar checkbox
        SELECT,         // Selecionar opção em dropdown
        WAIT_TEXT,      // Aguardar texto aparecer
        WAIT_URL,       // Aguardar URL mudar
        PRESS_KEY,      // Pressionar tecla
        ASSERT,         // Validar elemento visível
        WAIT,           // Aguardar tempo fixo
        UNKNOWN         // Ação não reconhecida
    }

    private final ActionType action;
    private final String url;
    private final String role;
    private final String text;
    private final String label;
    private final String value;
    private final String placeholder;
    private final String option;
    private final Integer index;
    private final String key;
    private final String contains;
    private final Integer waitMs;
    private final String reason;      // Explicação da IA sobre a decisão
    private final boolean needsRetry; // Se a ação pode precisar de retry

    private ActionDecision(Builder builder) {
        this.action = builder.action;
        this.url = builder.url;
        this.role = builder.role;
        this.text = builder.text;
        this.label = builder.label;
        this.value = builder.value;
        this.placeholder = builder.placeholder;
        this.option = builder.option;
        this.index = builder.index;
        this.key = builder.key;
        this.contains = builder.contains;
        this.waitMs = builder.waitMs;
        this.reason = builder.reason;
        this.needsRetry = builder.needsRetry;
    }

    // Factory method para criar a partir de JSON da IA
    public static ActionDecision fromJson(String jsonString) {
        try {
            JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();

            Builder builder = new Builder();

            // Action type
            String actionStr = getString(json, "action", "UNKNOWN").toUpperCase();
            builder.action(ActionType.valueOf(actionStr));

            // Campos comuns
            builder.url(getString(json, "url", null));
            builder.role(getString(json, "role", null));
            builder.text(getString(json, "text", null));
            builder.label(getString(json, "label", null));
            builder.value(getString(json, "value", null));
            builder.placeholder(getString(json, "placeholder", null));
            builder.option(getString(json, "option", null));
            builder.key(getString(json, "key", null));
            builder.contains(getString(json, "contains", null));
            builder.reason(getString(json, "reason", null));

            // Integer fields
            if (json.has("index") && !json.get("index").isJsonNull()) {
                builder.index(json.get("index").getAsInt());
            }
            if (json.has("waitMs") && !json.get("waitMs").isJsonNull()) {
                builder.waitMs(json.get("waitMs").getAsInt());
            }

            // Boolean
            if (json.has("needsRetry") && !json.get("needsRetry").isJsonNull()) {
                builder.needsRetry(json.get("needsRetry").getAsBoolean());
            }

            return builder.build();
        } catch (Exception e) {
            System.err.println("[ActionDecision] Erro ao parsear JSON: " + e.getMessage());
            System.err.println("[ActionDecision] JSON recebido: " + jsonString);
            return new Builder()
                .action(ActionType.UNKNOWN)
                .reason("Falha ao parsear: " + e.getMessage())
                .build();
        }
    }

    private static String getString(JsonObject json, String key, String defaultValue) {
        if (json.has(key) && !json.get(key).isJsonNull()) {
            return json.get(key).getAsString();
        }
        return defaultValue;
    }

    // Getters
    public ActionType getAction() { return action; }
    public String getUrl() { return url; }
    public String getRole() { return role; }
    public String getText() { return text; }
    public String getLabel() { return label; }
    public String getValue() { return value; }
    public String getPlaceholder() { return placeholder; }
    public String getOption() { return option; }
    public Integer getIndex() { return index; }
    public String getKey() { return key; }
    public String getContains() { return contains; }
    public Integer getWaitMs() { return waitMs; }
    public String getReason() { return reason; }
    public boolean isNeedsRetry() { return needsRetry; }

    /**
     * Verifica se é um campo de busca que precisa de tratamento especial
     */
    public boolean isSearchField() {
        if (action != ActionType.FILL && action != ActionType.FILL_PLACEHOLDER) {
            return false;
        }
        String searchLabel = (label != null ? label : placeholder != null ? placeholder : "").toLowerCase();
        return searchLabel.contains("busca") ||
               searchLabel.contains("buscar") ||
               searchLabel.contains("pesquisa") ||
               searchLabel.contains("pesquisar") ||
               searchLabel.contains("paciente") ||
               searchLabel.contains("procurar") ||
               searchLabel.contains("search");
    }

    @Override
    public String toString() {
        return String.format("ActionDecision[%s: %s %s %s]", action, role, text, label);
    }

    // Builder pattern
    public static class Builder {
        private ActionType action = ActionType.UNKNOWN;
        private String url;
        private String role;
        private String text;
        private String label;
        private String value;
        private String placeholder;
        private String option;
        private Integer index;
        private String key;
        private String contains;
        private Integer waitMs;
        private String reason;
        private boolean needsRetry = false;

        public Builder action(ActionType action) {
            this.action = action;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder value(String value) {
            this.value = value;
            return this;
        }

        public Builder placeholder(String placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        public Builder option(String option) {
            this.option = option;
            return this;
        }

        public Builder index(Integer index) {
            this.index = index;
            return this;
        }

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder contains(String contains) {
            this.contains = contains;
            return this;
        }

        public Builder waitMs(Integer waitMs) {
            this.waitMs = waitMs;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder needsRetry(boolean needsRetry) {
            this.needsRetry = needsRetry;
            return this;
        }

        public ActionDecision build() {
            return new ActionDecision(this);
        }
    }
}

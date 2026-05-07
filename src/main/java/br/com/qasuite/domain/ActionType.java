package br.com.qasuite.domain;

/**
 * Tipos de ações suportados pela DSL
 */
public enum ActionType {
    NAVIGATE("navigate", "Navegar para URL"),
    CLICK("click", "Clicar em elemento"),
    FILL("fill", "Preencher campo"),
    SELECT("select", "Selecionar opção"),
    CHECKBOX("checkbox", "Marcar checkbox"),
    ASSERT("assert", "Validar/Assert"),
    WAIT("wait", "Aguardar"),
    HOVER("hover", "Passar mouse"),
    UPLOAD("upload", "Upload de arquivo"),
    SCROLL("scroll", "Rolar página"),
    MENU("menu", "Navegar menu");

    private final String code;
    private final String description;

    ActionType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Converte string para ActionType (case-insensitive)
     */
    public static ActionType fromString(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String normalized = text.trim().toLowerCase();
        for (ActionType type : values()) {
            if (type.code.equals(normalized) || type.name().toLowerCase().equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}

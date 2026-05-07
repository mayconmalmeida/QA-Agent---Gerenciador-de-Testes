package br.com.qasuite.domain;

/**
 * Status possíveis de um passo de teste
 */
public enum StepStatus {
    PENDING("pending", "Pendente", "⏳"),
    RUNNING("running", "Executando", "▶️"),
    SUCCESS("success", "Sucesso", "✅"),
    FAILED("failed", "Falhou", "❌"),
    SKIPPED("skipped", "Ignorado", "⏭️"),
    WARNING("warning", "Aviso", "⚠️");

    private final String code;
    private final String description;
    private final String icon;

    StepStatus(String code, String description, String icon) {
        this.code = code;
        this.description = description;
        this.icon = icon;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == SKIPPED;
    }
}

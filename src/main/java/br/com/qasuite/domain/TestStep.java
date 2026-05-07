package br.com.qasuite.domain;

import java.util.Map;

/**
 * Representa um passo individual de teste na DSL
 */
public class TestStep {
    private int order;
    private ActionType action;
    private String target;
    private String value;
    private String type;
    private Map<String, Object> metadata;
    private String originalText;

    public TestStep() {}

    public TestStep(int order, ActionType action, String target, String value) {
        this.order = order;
        this.action = action;
        this.target = target;
        this.value = value;
    }

    // Getters e Setters
    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public ActionType getAction() {
        return action;
    }

    public void setAction(ActionType action) {
        this.action = action;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    /**
     * Retorna descrição legível do passo
     */
    public String getDescription() {
        if (originalText != null && !originalText.isEmpty()) {
            return originalText;
        }

        StringBuilder desc = new StringBuilder();
        desc.append(action != null ? action.getDescription() : "Ação");

        if (target != null && !target.isEmpty()) {
            desc.append(" em '").append(target).append("'");
        }

        if (value != null && !value.isEmpty()) {
            if (action == ActionType.FILL || action == ActionType.SELECT) {
                desc.append(" com '").append(value).append("'");
            } else if (action == ActionType.ASSERT) {
                desc.append(" esperando '").append(value).append("'");
            }
        }

        return desc.toString();
    }

    @Override
    public String toString() {
        return "TestStep{" +
                "order=" + order +
                ", action=" + action +
                ", target='" + target + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}

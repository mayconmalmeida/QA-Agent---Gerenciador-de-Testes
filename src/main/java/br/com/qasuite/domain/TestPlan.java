package br.com.qasuite.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Plano de teste completo - agrupa todos os passos
 */
public class TestPlan {
    private String id;
    private String name;
    private String description;
    private String module;
    private String menu;
    private String testType;
    private List<TestStep> steps;
    private String originalDescription;

    public TestPlan() {
        this.id = UUID.randomUUID().toString();
        this.steps = new ArrayList<>();
    }

    public TestPlan(String name, String description) {
        this();
        this.name = name;
        this.description = description;
    }

    /**
     * Adiciona um passo ao plano
     */
    public void addStep(TestStep step) {
        step.setOrder(steps.size() + 1);
        steps.add(step);
    }

    /**
     * Retorna passos ordenados
     */
    public List<TestStep> getSteps() {
        List<TestStep> ordered = new ArrayList<>(steps);
        ordered.sort(Comparator.comparingInt(TestStep::getOrder));
        return Collections.unmodifiableList(ordered);
    }

    /**
     * Retorna número total de passos
     */
    public int getTotalSteps() {
        return steps.size();
    }

    /**
     * Valida se o plano está completo
     */
    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();

        if (name == null || name.trim().isEmpty()) {
            errors.add("Nome do teste é obrigatório");
        }

        if (steps.isEmpty()) {
            errors.add("Teste deve ter pelo menos um passo");
        }

        for (int i = 0; i < steps.size(); i++) {
            TestStep step = steps.get(i);
            if (step.getAction() == null) {
                errors.add("Passo " + (i + 1) + " não tem ação definida");
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    // Getters e Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getMenu() {
        return menu;
    }

    public void setMenu(String menu) {
        this.menu = menu;
    }

    public String getTestType() {
        return testType;
    }

    public void setTestType(String testType) {
        this.testType = testType;
    }

    public String getOriginalDescription() {
        return originalDescription;
    }

    public void setOriginalDescription(String originalDescription) {
        this.originalDescription = originalDescription;
    }

    @Override
    public String toString() {
        return "TestPlan{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", steps=" + steps.size() +
                '}';
    }

    /**
     * Resultado de validação
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors != null ? errors : new ArrayList<>();
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }
    }
}

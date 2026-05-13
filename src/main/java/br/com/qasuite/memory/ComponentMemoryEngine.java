package br.com.qasuite.memory;

import br.com.qasuite.core.ActionDecision;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

public class ComponentMemoryEngine {
    private static final Gson gson = new Gson();

    private final ComponentMemoryRepository repository;
    private final ComponentNameExtractor nameExtractor;

    public ComponentMemoryEngine() {
        this(new ComponentMemoryRepository(), new ComponentNameExtractor());
    }

    public ComponentMemoryEngine(ComponentMemoryRepository repository, ComponentNameExtractor nameExtractor) {
        this.repository = repository;
        this.nameExtractor = nameExtractor;
    }

    public Optional<String> extractComponentName(String step) {
        return nameExtractor.extract(step);
    }

    public Optional<ActionDecision> lookupDecision(String step, String moduleName, String screenName) {
        Optional<String> comp = extractComponentName(step);
        if (comp.isEmpty()) {
            return Optional.empty();
        }

        ComponentMemoryEntry best = repository.findBest(comp.get(), moduleName, screenName);
        if (best == null) {
            return Optional.empty();
        }
        String strategy = best.getExecutionStrategy();
        if (strategy == null || strategy.trim().isEmpty()) {
            return Optional.empty();
        }
        if (!isStrongEnoughToAutoReuse(best)) {
            return Optional.empty();
        }

        try {
            return Optional.of(ActionDecision.fromJson(strategy));
        } catch (Exception e) {
            System.err.println("[ComponentMemoryEngine] Erro ao parsear strategy JSON: " + e.getMessage());
            return Optional.empty();
        }
    }

    public String buildContextHints(String step, String moduleName, String screenName) {
        Optional<String> comp = extractComponentName(step);
        if (comp.isEmpty()) {
            return "";
        }
        ComponentMemoryEntry best = repository.findBest(comp.get(), moduleName, screenName);
        if (best == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Memória de Componentes (priorizar se fizer sentido):\n");
        sb.append("- componente='").append(best.getComponentName()).append("'");
        if (best.getComponentType() != null) {
            sb.append(" tipo=").append(best.getComponentType().name());
        }
        if (best.getBehaviorType() != null) {
            sb.append(" comportamento=").append(best.getBehaviorType().name());
        }
        sb.append(" sucesso=").append((int) Math.round(best.getSuccessRate())).append("%");
        sb.append("\n");

        if (best.getExecutionStrategy() != null && !best.getExecutionStrategy().trim().isEmpty()) {
            sb.append("- execution_strategy_json=").append(best.getExecutionStrategy().trim()).append("\n");
        }
        if (best.getFallbackStrategy() != null && !best.getFallbackStrategy().trim().isEmpty()) {
            sb.append("- fallback_strategy_json=").append(best.getFallbackStrategy().trim()).append("\n");
        }
        if (best.getNotes() != null && !best.getNotes().trim().isEmpty()) {
            sb.append("- notes=").append(best.getNotes().trim()).append("\n");
        }

        return sb.toString();
    }

    public void onStepSuccess(String step, String moduleName, String screenName, ActionDecision decision) {
        Optional<String> comp = extractComponentName(step);
        if (comp.isEmpty()) {
            return;
        }
        if (decision == null || decision.getAction() == null || decision.getAction() == ActionDecision.ActionType.UNKNOWN) {
            return;
        }

        ComponentMemoryEntry current = repository.findBest(comp.get(), moduleName, screenName);
        if (current == null || !sameLogicalComponent(comp.get(), current)) {
            ComponentMemoryEntry created = new ComponentMemoryEntry();
            created.setComponentName(comp.get());
            created.setModuleName(blankToNull(moduleName));
            created.setScreenName(blankToNull(screenName));
            created.setComponentType(guessComponentType(decision));
            created.setBehaviorType(guessBehaviorType(decision));
            created.setExecutionStrategy(normalizeStrategyJson(decision));
            created.setSuccessfulAttempts(1);
            created.setFailedAttempts(0);
            created.setLastSuccess(Instant.now().toString());
            created.setLearnedFromUser(false);
            created.setCreatedAt(Instant.now().toString());
            repository.upsert(created);
            return;
        }

        if (current.getExecutionStrategy() == null || current.getExecutionStrategy().trim().isEmpty()) {
            current.setExecutionStrategy(normalizeStrategyJson(decision));
        }
        if (current.getComponentType() == null) {
            current.setComponentType(guessComponentType(decision));
        }
        if (current.getBehaviorType() == null) {
            current.setBehaviorType(guessBehaviorType(decision));
        }
        current.setLastSuccess(Instant.now().toString());
        repository.upsert(current);
        if (current.getId() != null) {
            repository.recordSuccess(current.getId());
        }
    }

    public void onStepFailure(String step, String moduleName, String screenName, ActionDecision decision, String error) {
        Optional<String> comp = extractComponentName(step);
        if (comp.isEmpty()) {
            return;
        }

        ComponentMemoryEntry current = repository.findBest(comp.get(), moduleName, screenName);
        if (current == null || !sameLogicalComponent(comp.get(), current)) {
            ComponentMemoryEntry created = new ComponentMemoryEntry();
            created.setComponentName(comp.get());
            created.setModuleName(blankToNull(moduleName));
            created.setScreenName(blankToNull(screenName));
            created.setComponentType(decision != null ? guessComponentType(decision) : null);
            created.setBehaviorType(decision != null ? guessBehaviorType(decision) : null);
            if (decision != null && decision.getAction() != null && decision.getAction() != ActionDecision.ActionType.UNKNOWN) {
                created.setExecutionStrategy(normalizeStrategyJson(decision));
            }
            created.setSuccessfulAttempts(0);
            created.setFailedAttempts(1);
            created.setLearnedFromUser(false);
            created.setNotes(composeFailureNotes(error));
            created.setCreatedAt(Instant.now().toString());
            repository.upsert(created);
            return;
        }

        repository.recordFailure(current.getId(), composeFailureNotes(error));
    }

    public ComponentMemoryEntry saveLearnedFromUser(ComponentMemoryEntry entry) {
        if (entry == null) {
            return null;
        }
        entry.setLearnedFromUser(true);
        if (entry.getCreatedAt() == null || entry.getCreatedAt().isBlank()) {
            entry.setCreatedAt(Instant.now().toString());
        }
        return repository.upsert(entry);
    }

    public ComponentMemoryRepository getRepository() {
        return repository;
    }

    private static boolean isStrongEnoughToAutoReuse(ComponentMemoryEntry entry) {
        if (entry == null) {
            return false;
        }
        int successes = entry.getSuccessfulAttempts();
        int failures = entry.getFailedAttempts();
        if (successes <= 0) {
            return false;
        }
        if (failures == 0) {
            return true;
        }
        return entry.getSuccessRate() >= 70.0;
    }

    private static boolean sameLogicalComponent(String extracted, ComponentMemoryEntry current) {
        if (current == null) {
            return false;
        }
        String n1 = ComponentNameExtractor.normalizeKey(extracted);
        String n2 = ComponentNameExtractor.normalizeKey(current.getComponentName());
        if (!n1.isEmpty() && n1.equals(n2)) {
            return true;
        }
        String alias = current.getComponentAlias() == null ? "" : current.getComponentAlias().toLowerCase(Locale.ROOT);
        return !n1.isEmpty() && alias.contains(n1.toLowerCase(Locale.ROOT));
    }

    private static ComponentType guessComponentType(ActionDecision decision) {
        if (decision == null || decision.getAction() == null) {
            return null;
        }
        return switch (decision.getAction()) {
            case FILL, FILL_PLACEHOLDER -> ComponentType.INPUT;
            case SELECT -> ComponentType.DROPDOWN;
            case CHECK -> ComponentType.CHECKBOX;
            case CLICK, CLICK_NTH, DOUBLE_CLICK -> ComponentType.BUTTON;
            default -> null;
        };
    }

    private static BehaviorType guessBehaviorType(ActionDecision decision) {
        if (decision == null || decision.getAction() == null) {
            return null;
        }
        return switch (decision.getAction()) {
            case FILL, FILL_PLACEHOLDER -> BehaviorType.TYPE_AND_SELECT;
            case SELECT -> BehaviorType.CLICK_AND_SELECT;
            case CLICK, CLICK_NTH, DOUBLE_CLICK -> BehaviorType.WAIT_AND_CLICK;
            default -> null;
        };
    }

    private static String composeFailureNotes(String error) {
        if (error == null || error.isBlank()) {
            return null;
        }
        String cleaned = error.trim();
        if (cleaned.length() > 500) {
            cleaned = cleaned.substring(0, 500);
        }
        return "Última falha: " + cleaned;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String normalizeStrategyJson(ActionDecision decision) {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("action", decision.getAction() != null ? decision.getAction().name() : "UNKNOWN");
            if (decision.getUrl() != null) o.addProperty("url", decision.getUrl());
            if (decision.getRole() != null) o.addProperty("role", decision.getRole());
            if (decision.getText() != null) o.addProperty("text", decision.getText());
            if (decision.getLabel() != null) o.addProperty("label", decision.getLabel());
            if (decision.getValue() != null) o.addProperty("value", decision.getValue());
            if (decision.getPlaceholder() != null) o.addProperty("placeholder", decision.getPlaceholder());
            if (decision.getOption() != null) o.addProperty("option", decision.getOption());
            if (decision.getKey() != null) o.addProperty("key", decision.getKey());
            if (decision.getContains() != null) o.addProperty("contains", decision.getContains());
            if (decision.getWaitMs() != null) o.addProperty("waitMs", decision.getWaitMs());
            if (decision.getTestId() != null) o.addProperty("testId", decision.getTestId());
            if (decision.getSelector() != null) o.addProperty("selector", decision.getSelector());
            return gson.toJson(o);
        } catch (Exception ignored) {
            return gson.toJson(decision);
        }
    }

    public static boolean isValidStrategyJson(String raw) {
        if (raw == null) {
            return false;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return false;
        }
        try {
            JsonParser.parseString(t);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}


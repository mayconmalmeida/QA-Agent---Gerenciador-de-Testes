package br.com.qasuite.rag;

import br.com.qasuite.memory.ComponentMemoryEntry;
import br.com.qasuite.memory.ComponentNameExtractor;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class SystemKnowledgeRagService {
    private static final Gson gson = new Gson();

    private final KnowledgeRetriever retriever;
    private final ComponentNameExtractor componentNameExtractor;

    public SystemKnowledgeRagService(KnowledgeRetriever retriever) {
        this(retriever, new ComponentNameExtractor());
    }

    public SystemKnowledgeRagService(KnowledgeRetriever retriever, ComponentNameExtractor componentNameExtractor) {
        this.retriever = retriever;
        this.componentNameExtractor = componentNameExtractor;
    }

    public RagContextResult retrieveContext(String stepDescription, String module, String screen) {
        String step = stepDescription == null ? "" : stepDescription.trim();
        String mod = safeTrim(module);
        String scr = safeTrim(screen);

        Optional<String> extracted = componentNameExtractor.extract(step);
        String query = step;
        if (extracted.isPresent()) {
            String c = extracted.get();
            if (!c.isBlank()) {
                query = step + " " + c;
            }
        }

        List<KnowledgeMatch> matches = retriever.retrieve(query, mod, scr);
        KnowledgeMatch selected = matches.isEmpty() ? null : matches.get(0);

        String promptContext = buildPromptContext(matches);

        return new RagContextResult(step, mod, scr, matches, selected, promptContext);
    }

    public static String buildPromptContext(List<KnowledgeMatch> matches) {
        if (matches == null || matches.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("CONHECIMENTO JÁ APRENDIDO DO SISTEMA:\n");
        for (int i = 0; i < matches.size(); i++) {
            KnowledgeMatch m = matches.get(i);
            ComponentMemoryEntry e = m.getEntry();
            if (e == null) {
                continue;
            }
            int successRate = (int) Math.round(e.getSuccessRate());
            sb.append("- Componente: ").append(nullToDash(e.getComponentName())).append("\n");
            if (e.getComponentType() != null) {
                sb.append("  Tipo: ").append(e.getComponentType().name()).append("\n");
            }
            if (e.getBehaviorType() != null) {
                sb.append("  Estratégia recomendada: ").append(e.getBehaviorType().name()).append("\n");
            }
            if (e.getExecutionStrategy() != null && !e.getExecutionStrategy().isBlank()) {
                sb.append("  execution_strategy_json: ").append(e.getExecutionStrategy().trim()).append("\n");
            }
            if (e.getModuleName() != null && !e.getModuleName().isBlank()) {
                sb.append("  Módulo: ").append(e.getModuleName().trim()).append("\n");
            }
            if (e.getScreenName() != null && !e.getScreenName().isBlank()) {
                sb.append("  Tela: ").append(e.getScreenName().trim()).append("\n");
            }
            sb.append("  Taxa de sucesso: ").append(successRate).append("%\n");
            if (e.getNotes() != null && !e.getNotes().isBlank()) {
                sb.append("  Observação: ").append(e.getNotes().trim()).append("\n");
            } else if (e.getDescription() != null && !e.getDescription().isBlank()) {
                sb.append("  Observação: ").append(e.getDescription().trim()).append("\n");
            }
            sb.append("  Score: ").append(String.format(Locale.ROOT, "%.1f", m.getScore())).append("\n");
            if (i != matches.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString().trim();
    }

    public static String toRetrievedComponentsJson(List<KnowledgeMatch> matches) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (matches != null) {
            for (KnowledgeMatch m : matches) {
                ComponentMemoryEntry e = m.getEntry();
                if (e == null) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", e.getId());
                item.put("componentName", e.getComponentName());
                item.put("componentAlias", e.getComponentAlias());
                item.put("moduleName", e.getModuleName());
                item.put("screenName", e.getScreenName());
                item.put("componentType", e.getComponentType() != null ? e.getComponentType().name() : null);
                item.put("behaviorType", e.getBehaviorType() != null ? e.getBehaviorType().name() : null);
                item.put("successRate", (int) Math.round(e.getSuccessRate()));
                item.put("confidenceScore", e.getConfidenceScore());
                item.put("score", m.getScore());
                list.add(item);
            }
        }
        return gson.toJson(list);
    }

    private static String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String nullToDash(String value) {
        if (value == null) {
            return "—";
        }
        String t = value.trim();
        return t.isEmpty() ? "—" : t;
    }
}


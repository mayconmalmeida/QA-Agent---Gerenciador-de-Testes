package br.com.qasuite.rag;

import br.com.qasuite.memory.ComponentMemoryEntry;
import br.com.qasuite.memory.ComponentMemoryRepository;
import br.com.qasuite.memory.ComponentNameExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SqliteKnowledgeRetriever implements KnowledgeRetriever {
    private final ComponentMemoryRepository repository;

    public SqliteKnowledgeRetriever(ComponentMemoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<KnowledgeMatch> retrieve(String query, String module, String screen) {
        String qn = TextNormalizer.normalize(query);
        if (qn.isBlank()) {
            return List.of();
        }

        String mod = safeKey(module);
        String scr = safeKey(screen);

        List<ComponentMemoryEntry> candidates = repository.listRagEnabled(2000);
        List<KnowledgeMatch> matches = new ArrayList<>();

        for (ComponentMemoryEntry e : candidates) {
            double score = score(e, qn, mod, scr);
            if (score <= 0) {
                continue;
            }
            matches.add(new KnowledgeMatch(e, score));
        }

        matches.sort(Comparator.comparingDouble(KnowledgeMatch::getScore).reversed());
        if (matches.size() > 3) {
            return matches.subList(0, 3);
        }
        return matches;
    }

    private static double score(ComponentMemoryEntry entry, String normalizedQuery, String module, String screen) {
        if (entry == null) {
            return 0;
        }

        String name = TextNormalizer.normalize(entry.getComponentName());
        String alias = TextNormalizer.normalize(entry.getComponentAlias());
        String notes = TextNormalizer.normalize(entry.getNotes());
        String mod = safeKey(entry.getModuleName());
        String scr = safeKey(entry.getScreenName());
        String embed = TextNormalizer.normalize(entry.getEmbeddingText());
        String tags = TextNormalizer.normalize(entry.getSemanticTags());

        double score = 0;

        if (!name.isBlank() && name.equals(normalizedQuery)) {
            score += 50;
        }
        if (!alias.isBlank() && containsWhole(alias, normalizedQuery)) {
            score += 35;
        }

        if (!module.isBlank() || !screen.isBlank()) {
            boolean moduleMatch = !module.isBlank() && !mod.isBlank() && mod.equals(module);
            boolean screenMatch = !screen.isBlank() && !scr.isBlank() && scr.equals(screen);
            if (moduleMatch || screenMatch) {
                score += 20;
            }
        }

        if (score <= 0) {
            boolean partial = partialMatch(normalizedQuery, name)
                || partialMatch(normalizedQuery, alias)
                || partialMatch(normalizedQuery, notes)
                || partialMatch(normalizedQuery, embed)
                || partialMatch(normalizedQuery, tags);
            if (partial) {
                score += 15;
            }
        }

        if (containsAny(notes, normalizedQuery) || containsAny(embed, normalizedQuery) || containsAny(tags, normalizedQuery)) {
            score += 5;
        }

        if (entry.getSuccessRate() >= 80.0) {
            score += 10;
        }
        if (entry.getSuccessfulAttempts() >= 2) {
            score += 10;
        }

        double conf = Math.max(0, Math.min(1, entry.getConfidenceScore()));
        score += conf * 10.0;

        if (entry.getLastUsedAsContext() != null && !entry.getLastUsedAsContext().isBlank()) {
            score += 2.0;
        }

        return score;
    }

    private static boolean partialMatch(String query, String field) {
        if (query == null || query.isBlank() || field == null || field.isBlank()) {
            return false;
        }
        if (field.contains(query)) {
            return true;
        }
        if (query.contains(field) && field.length() >= 3) {
            return true;
        }
        return tokenOverlap(query, field) >= 0.6;
    }

    private static double tokenOverlap(String a, String b) {
        String[] ta = a.split(" ");
        String[] tb = b.split(" ");
        if (ta.length == 0 || tb.length == 0) {
            return 0;
        }
        int hit = 0;
        for (String t : ta) {
            if (t.length() < 2) {
                continue;
            }
            for (String u : tb) {
                if (t.equals(u)) {
                    hit++;
                    break;
                }
            }
        }
        return (double) hit / (double) Math.max(1, ta.length);
    }

    private static boolean containsAny(String field, String query) {
        if (field == null || field.isBlank() || query == null || query.isBlank()) {
            return false;
        }
        for (String token : query.split(" ")) {
            if (token.length() < 3) {
                continue;
            }
            if (field.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWhole(String field, String query) {
        if (field == null || field.isBlank() || query == null || query.isBlank()) {
            return false;
        }
        if (field.equals(query)) {
            return true;
        }
        return (" " + field + " ").contains(" " + query + " ");
    }

    private static String safeKey(String value) {
        if (value == null) {
            return "";
        }
        String n = ComponentNameExtractor.normalizeKey(value);
        return n == null ? "" : n.trim().toLowerCase(Locale.ROOT);
    }
}


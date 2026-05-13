package br.com.qasuite.memory;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ComponentNameExtractor {

    private static final Pattern QUOTED = Pattern.compile("[\"“”']([^\"“”']{2,80})[\"“”']");
    private static final Pattern CAMPO = Pattern.compile("(?i)\\b(?:no\\s+)?campo\\s+([\\p{L}0-9][\\p{L}0-9\\s\\-_/]{1,60})");
    private static final Pattern LABEL = Pattern.compile("(?i)\\b(?:preencher|informar|digitar|selecionar|marcar|desmarcar)\\s+([\\p{L}0-9][\\p{L}0-9\\s\\-_/]{1,60})");

    public Optional<String> extract(String step) {
        if (step == null) {
            return Optional.empty();
        }
        String raw = step.trim();
        if (raw.isEmpty()) {
            return Optional.empty();
        }

        Matcher q = QUOTED.matcher(raw);
        if (q.find()) {
            String candidate = cleanupCandidate(q.group(1));
            if (isGoodCandidate(candidate)) {
                return Optional.of(candidate);
            }
        }

        Matcher c = CAMPO.matcher(raw);
        if (c.find()) {
            String candidate = cleanupCandidate(c.group(1));
            candidate = trimAtKeywords(candidate);
            if (isGoodCandidate(candidate)) {
                return Optional.of(candidate);
            }
        }

        Matcher l = LABEL.matcher(raw);
        if (l.find()) {
            String candidate = cleanupCandidate(l.group(1));
            candidate = trimAtKeywords(candidate);
            if (isGoodCandidate(candidate)) {
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }

    private static String trimAtKeywords(String candidate) {
        if (candidate == null) {
            return null;
        }
        String lower = candidate.toLowerCase(Locale.ROOT);
        int cut = -1;
        for (String kw : new String[]{" com ", " para ", " até ", " ate ", " e ", " em ", " no ", " na "}) {
            int idx = lower.indexOf(kw);
            if (idx > 0) {
                if (cut == -1 || idx < cut) {
                    cut = idx;
                }
            }
        }
        if (cut > 0) {
            return candidate.substring(0, cut).trim();
        }
        return candidate.trim();
    }

    private static String cleanupCandidate(String candidate) {
        if (candidate == null) {
            return null;
        }
        String cleaned = candidate.trim()
            .replaceAll("\\s{2,}", " ")
            .replaceAll("[:;,.]+$", "")
            .trim();
        return cleaned;
    }

    private static boolean isGoodCandidate(String candidate) {
        if (candidate == null) {
            return false;
        }
        String c = candidate.trim();
        if (c.length() < 2 || c.length() > 60) {
            return false;
        }
        String normalized = normalizeKey(c);
        if (normalized.length() < 2) {
            return false;
        }
        if (normalized.equals("sim") || normalized.equals("nao") || normalized.equals("ok")) {
            return false;
        }
        return true;
    }

    public static String normalizeKey(String value) {
        if (value == null) {
            return "";
        }
        String n = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .trim();
        n = n.replaceAll("[^a-z0-9\\s\\-_/]", "");
        n = n.replaceAll("\\s{2,}", " ");
        return n;
    }
}


package br.com.qasuite.rag;

import java.text.Normalizer;

public final class TextNormalizer {
    private TextNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String s = value.trim().toLowerCase();
        if (s.isEmpty()) {
            return "";
        }
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        s = s.replaceAll("[^a-z0-9 ]", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }
}


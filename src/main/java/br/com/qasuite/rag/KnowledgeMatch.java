package br.com.qasuite.rag;

import br.com.qasuite.memory.ComponentMemoryEntry;

public class KnowledgeMatch {
    private final ComponentMemoryEntry entry;
    private final double score;

    public KnowledgeMatch(ComponentMemoryEntry entry, double score) {
        this.entry = entry;
        this.score = score;
    }

    public ComponentMemoryEntry getEntry() {
        return entry;
    }

    public double getScore() {
        return score;
    }
}


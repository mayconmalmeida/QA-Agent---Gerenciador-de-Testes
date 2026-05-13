package br.com.qasuite.rag;

import java.util.List;

public class VectorKnowledgeRetriever implements KnowledgeRetriever {
    @Override
    public List<KnowledgeMatch> retrieve(String query, String module, String screen) {
        return List.of();
    }
}


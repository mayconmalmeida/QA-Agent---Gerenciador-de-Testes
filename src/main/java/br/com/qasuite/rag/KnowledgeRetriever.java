package br.com.qasuite.rag;

import java.util.List;

public interface KnowledgeRetriever {
    List<KnowledgeMatch> retrieve(String query, String module, String screen);
}


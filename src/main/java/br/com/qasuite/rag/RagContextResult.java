package br.com.qasuite.rag;

import br.com.qasuite.memory.ComponentMemoryEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RagContextResult {
    private final String stepDescription;
    private final String moduleName;
    private final String screenName;
    private final List<KnowledgeMatch> matches;
    private final KnowledgeMatch selected;
    private final String promptContext;

    public RagContextResult(String stepDescription,
                            String moduleName,
                            String screenName,
                            List<KnowledgeMatch> matches,
                            KnowledgeMatch selected,
                            String promptContext) {
        this.stepDescription = stepDescription;
        this.moduleName = moduleName;
        this.screenName = screenName;
        this.matches = matches != null ? new ArrayList<>(matches) : new ArrayList<>();
        this.selected = selected;
        this.promptContext = promptContext;
    }

    public String getStepDescription() {
        return stepDescription;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getScreenName() {
        return screenName;
    }

    public List<KnowledgeMatch> getMatches() {
        return Collections.unmodifiableList(matches);
    }

    public KnowledgeMatch getSelected() {
        return selected;
    }

    public String getPromptContext() {
        return promptContext;
    }

    public boolean isUsedInPrompt() {
        return promptContext != null && !promptContext.isBlank();
    }

    public ComponentMemoryEntry getSelectedEntry() {
        return selected != null ? selected.getEntry() : null;
    }

    public double getSelectedScore() {
        return selected != null ? selected.getScore() : 0.0;
    }
}


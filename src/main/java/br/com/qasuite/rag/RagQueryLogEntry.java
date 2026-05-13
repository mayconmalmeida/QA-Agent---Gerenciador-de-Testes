package br.com.qasuite.rag;

public class RagQueryLogEntry {
    private Long id;
    private String testExecutionId;
    private String stepDescription;
    private String moduleName;
    private String screenName;
    private String retrievedComponents;
    private String selectedComponent;
    private double score;
    private boolean usedInPrompt;
    private String promptContext;
    private String llmDecision;
    private Boolean executionSuccess;
    private String createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTestExecutionId() {
        return testExecutionId;
    }

    public void setTestExecutionId(String testExecutionId) {
        this.testExecutionId = testExecutionId;
    }

    public String getStepDescription() {
        return stepDescription;
    }

    public void setStepDescription(String stepDescription) {
        this.stepDescription = stepDescription;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getScreenName() {
        return screenName;
    }

    public void setScreenName(String screenName) {
        this.screenName = screenName;
    }

    public String getRetrievedComponents() {
        return retrievedComponents;
    }

    public void setRetrievedComponents(String retrievedComponents) {
        this.retrievedComponents = retrievedComponents;
    }

    public String getSelectedComponent() {
        return selectedComponent;
    }

    public void setSelectedComponent(String selectedComponent) {
        this.selectedComponent = selectedComponent;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public boolean isUsedInPrompt() {
        return usedInPrompt;
    }

    public void setUsedInPrompt(boolean usedInPrompt) {
        this.usedInPrompt = usedInPrompt;
    }

    public String getPromptContext() {
        return promptContext;
    }

    public void setPromptContext(String promptContext) {
        this.promptContext = promptContext;
    }

    public String getLlmDecision() {
        return llmDecision;
    }

    public void setLlmDecision(String llmDecision) {
        this.llmDecision = llmDecision;
    }

    public Boolean getExecutionSuccess() {
        return executionSuccess;
    }

    public void setExecutionSuccess(Boolean executionSuccess) {
        this.executionSuccess = executionSuccess;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}


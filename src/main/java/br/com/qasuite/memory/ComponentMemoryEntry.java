package br.com.qasuite.memory;

public class ComponentMemoryEntry {
    private Long id;
    private String componentName;
    private String componentAlias;
    private ComponentType componentType;
    private String moduleName;
    private String screenName;
    private BehaviorType behaviorType;
    private String executionStrategy;
    private String fallbackStrategy;
    private int successfulAttempts;
    private int failedAttempts;
    private String lastSuccess;
    private boolean learnedFromUser;
    private String notes;
    private String description;
    private String examples;
    private String semanticTags;
    private String embeddingText;
    private boolean ragEnabled = true;
    private double confidenceScore;
    private String lastUsedAsContext;
    private String createdAt;
    private String updatedAt;

    public ComponentMemoryEntry() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getComponentName() {
        return componentName;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    public String getComponentAlias() {
        return componentAlias;
    }

    public void setComponentAlias(String componentAlias) {
        this.componentAlias = componentAlias;
    }

    public ComponentType getComponentType() {
        return componentType;
    }

    public void setComponentType(ComponentType componentType) {
        this.componentType = componentType;
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

    public BehaviorType getBehaviorType() {
        return behaviorType;
    }

    public void setBehaviorType(BehaviorType behaviorType) {
        this.behaviorType = behaviorType;
    }

    public String getExecutionStrategy() {
        return executionStrategy;
    }

    public void setExecutionStrategy(String executionStrategy) {
        this.executionStrategy = executionStrategy;
    }

    public String getFallbackStrategy() {
        return fallbackStrategy;
    }

    public void setFallbackStrategy(String fallbackStrategy) {
        this.fallbackStrategy = fallbackStrategy;
    }

    public int getSuccessfulAttempts() {
        return successfulAttempts;
    }

    public void setSuccessfulAttempts(int successfulAttempts) {
        this.successfulAttempts = successfulAttempts;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public String getLastSuccess() {
        return lastSuccess;
    }

    public void setLastSuccess(String lastSuccess) {
        this.lastSuccess = lastSuccess;
    }

    public boolean isLearnedFromUser() {
        return learnedFromUser;
    }

    public void setLearnedFromUser(boolean learnedFromUser) {
        this.learnedFromUser = learnedFromUser;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExamples() {
        return examples;
    }

    public void setExamples(String examples) {
        this.examples = examples;
    }

    public String getSemanticTags() {
        return semanticTags;
    }

    public void setSemanticTags(String semanticTags) {
        this.semanticTags = semanticTags;
    }

    public String getEmbeddingText() {
        return embeddingText;
    }

    public void setEmbeddingText(String embeddingText) {
        this.embeddingText = embeddingText;
    }

    public boolean isRagEnabled() {
        return ragEnabled;
    }

    public void setRagEnabled(boolean ragEnabled) {
        this.ragEnabled = ragEnabled;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getLastUsedAsContext() {
        return lastUsedAsContext;
    }

    public void setLastUsedAsContext(String lastUsedAsContext) {
        this.lastUsedAsContext = lastUsedAsContext;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public double getSuccessRate() {
        int total = successfulAttempts + failedAttempts;
        if (total <= 0) {
            return 0.0;
        }
        return ((double) successfulAttempts / (double) total) * 100.0;
    }
}


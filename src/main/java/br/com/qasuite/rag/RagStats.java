package br.com.qasuite.rag;

public class RagStats {
    private int ragEnabledCount;
    private int usedAsContextCount;
    private double avgConfidence;
    private int ragQueriesToday;
    private int ragUsedInPromptToday;
    private int ragSuccessWithContextToday;
    private int ragHitRateToday;
    private int ragDistinctComponentsToday;

    public int getRagEnabledCount() {
        return ragEnabledCount;
    }

    public void setRagEnabledCount(int ragEnabledCount) {
        this.ragEnabledCount = ragEnabledCount;
    }

    public int getUsedAsContextCount() {
        return usedAsContextCount;
    }

    public void setUsedAsContextCount(int usedAsContextCount) {
        this.usedAsContextCount = usedAsContextCount;
    }

    public double getAvgConfidence() {
        return avgConfidence;
    }

    public void setAvgConfidence(double avgConfidence) {
        this.avgConfidence = avgConfidence;
    }

    public int getRagQueriesToday() {
        return ragQueriesToday;
    }

    public void setRagQueriesToday(int ragQueriesToday) {
        this.ragQueriesToday = ragQueriesToday;
    }

    public int getRagUsedInPromptToday() {
        return ragUsedInPromptToday;
    }

    public void setRagUsedInPromptToday(int ragUsedInPromptToday) {
        this.ragUsedInPromptToday = ragUsedInPromptToday;
    }

    public int getRagSuccessWithContextToday() {
        return ragSuccessWithContextToday;
    }

    public void setRagSuccessWithContextToday(int ragSuccessWithContextToday) {
        this.ragSuccessWithContextToday = ragSuccessWithContextToday;
    }

    public int getRagHitRateToday() {
        return ragHitRateToday;
    }

    public void setRagHitRateToday(int ragHitRateToday) {
        this.ragHitRateToday = ragHitRateToday;
    }

    public int getRagDistinctComponentsToday() {
        return ragDistinctComponentsToday;
    }

    public void setRagDistinctComponentsToday(int ragDistinctComponentsToday) {
        this.ragDistinctComponentsToday = ragDistinctComponentsToday;
    }
}


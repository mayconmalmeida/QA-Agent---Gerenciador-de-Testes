package br.com.qasuite.learning;

public class LearningModeConfig {
    public boolean isEnabled() {
        String sys = System.getProperty("qa.learningMode");
        if (sys != null && !sys.isBlank()) {
            return "true".equalsIgnoreCase(sys.trim()) || "1".equals(sys.trim());
        }
        String env = System.getenv("QA_LEARNING_MODE");
        if (env != null && !env.isBlank()) {
            return "true".equalsIgnoreCase(env.trim()) || "1".equals(env.trim());
        }
        return false;
    }

    public int maxWaitMs() {
        String sys = System.getProperty("qa.learningModeWaitMs");
        if (sys != null && !sys.isBlank()) {
            try {
                return Math.max(10_000, Integer.parseInt(sys.trim()));
            } catch (Exception ignored) {
            }
        }
        String env = System.getenv("QA_LEARNING_MODE_WAIT_MS");
        if (env != null && !env.isBlank()) {
            try {
                return Math.max(10_000, Integer.parseInt(env.trim()));
            } catch (Exception ignored) {
            }
        }
        return 180_000;
    }
}


package br.com.qasuite.core;

import br.com.qasuite.domain.*;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Motor de execução de testes
 * Controla fluxo, retry, logs e callbacks
 */
public class ExecutionEngine {

    private final SmartPage smartPage;
    private final ExecutionContext context;
    private final AssertionEngine assertionEngine;
    private final List<StepResult> results;

    // Configurações
    private int maxRetries = 2;
    private long stepDelayMs = 500;
    private boolean continueOnError = true;
    private boolean takeScreenshotOnError = true;

    // Callbacks
    private Consumer<StepResult> onStepStart;
    private Consumer<StepResult> onStepComplete;
    private Consumer<TestPlan> onExecutionStart;
    private Consumer<List<StepResult>> onExecutionComplete;

    public ExecutionEngine(Page page) {
        this.smartPage = new SmartPage(page);
        this.context = new ExecutionContext(page);
        this.assertionEngine = new AssertionEngine(page);
        this.results = new ArrayList<>();
    }

    /**
     * Executa um plano de teste completo
     */
    public List<StepResult> execute(TestPlan plan) {
        results.clear();

        if (onExecutionStart != null) {
            onExecutionStart.accept(plan);
        }

        System.out.println("[ExecutionEngine] Starting execution: " + plan.getName());
        System.out.println("[ExecutionEngine] Total steps: " + plan.getTotalSteps());

        List<TestStep> steps = plan.getSteps();

        for (TestStep step : steps) {
            StepResult result = executeStep(step);
            results.add(result);

            // Se falhou e não deve continuar, para aqui
            if (result.isFailed() && !continueOnError) {
                System.out.println("[ExecutionEngine] Stopping on failure");
                break;
            }

            // Delay entre passos
            if (stepDelayMs > 0) {
                try {
                    Thread.sleep(stepDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        if (onExecutionComplete != null) {
            onExecutionComplete.accept(results);
        }

        return new ArrayList<>(results);
    }

    /**
     * Executa um passo individual
     */
    private StepResult executeStep(TestStep step) {
        StepResult result = new StepResult(step);

        // Callback de início
        if (onStepStart != null) {
            onStepStart.accept(result);
        }

        result.markRunning();
        System.out.println("[ExecutionEngine] Step " + step.getOrder() + ": " + step.getDescription());

        // Tenta executar com retry
        boolean success = false;
        String lastError = null;

        for (int attempt = 0; attempt <= maxRetries && !success; attempt++) {
            if (attempt > 0) {
                System.out.println("[ExecutionEngine] Retry attempt " + attempt);
                result.incrementRetry();
                try {
                    Thread.sleep(1000); // Espera antes de retry
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            try {
                executeAction(step, result);
                success = true;
            } catch (Exception e) {
                lastError = e.getMessage();
                System.err.println("[ExecutionEngine] Error (attempt " + (attempt + 1) + "): " + lastError);
            }
        }

        // Finaliza resultado
        if (success) {
            result.markSuccess("Executed successfully");
        } else {
            // Screenshot se habilitado
            if (takeScreenshotOnError) {
                try {
                    String screenshot = smartPage.takeScreenshot("error_step_" + step.getOrder());
                    result.setScreenshotPath(screenshot);
                } catch (Exception e) {
                    System.err.println("[ExecutionEngine] Failed to take screenshot: " + e.getMessage());
                }
            }
            result.markFailed(lastError);
        }

        // Callback de conclusão
        if (onStepComplete != null) {
            onStepComplete.accept(result);
        }

        return result;
    }

    /**
     * Executa ação específica baseada no tipo
     */
    private void executeAction(TestStep step, StepResult result) throws Exception {
        ActionType action = step.getAction();

        if (action == null) {
            throw new IllegalStateException("Action not defined for step " + step.getOrder());
        }

        String target = step.getTarget();
        String value = step.getValue();

        switch (action) {
            case NAVIGATE:
                smartPage.navigate(value != null ? value : target);
                context.pushNavigation(value != null ? value : target);
                break;

            case CLICK:
                smartPage.clickWithFallback(target, result);
                context.setLastElementUsed(target);
                break;

            case FILL:
                smartPage.fillWithFallback(target, value, result);
                context.storeFilledValue(target, value);
                break;

            case SELECT:
                smartPage.selectWithFallback(target, value, result);
                break;

            case CHECKBOX:
                boolean check = value == null || value.equalsIgnoreCase("true") || value.equalsIgnoreCase("marcar");
                smartPage.checkboxWithFallback(target, check, result);
                break;

            case MENU:
                String[] parts = target.split(">");
                if (parts.length >= 2) {
                    smartPage.navegarParaMenu(parts[0].trim(), parts[1].trim());
                } else {
                    smartPage.navegarParaMenu(target, null);
                }
                break;

            case ASSERT:
                String assertType = step.getType() != null ? step.getType() : "text_visible";
                assertionEngine.assertWithFallback(assertType, target, value, result);
                break;

            case WAIT:
                long waitMs = value != null ? Long.parseLong(value) * 1000 : 1000;
                Thread.sleep(waitMs);
                break;

            case HOVER:
                smartPage.hover(target);
                break;

            default:
                throw new UnsupportedOperationException("Action not implemented: " + action);
        }
    }

    /**
     * Configurações
     */
    public ExecutionEngine withMaxRetries(int retries) {
        this.maxRetries = retries;
        return this;
    }

    public ExecutionEngine withStepDelay(long ms) {
        this.stepDelayMs = ms;
        return this;
    }

    public ExecutionEngine withContinueOnError(boolean continueOnError) {
        this.continueOnError = continueOnError;
        return this;
    }

    public ExecutionEngine withScreenshotOnError(boolean enabled) {
        this.takeScreenshotOnError = enabled;
        return this;
    }

    /**
     * Callbacks
     */
    public ExecutionEngine onStepStart(Consumer<StepResult> callback) {
        this.onStepStart = callback;
        return this;
    }

    public ExecutionEngine onStepComplete(Consumer<StepResult> callback) {
        this.onStepComplete = callback;
        return this;
    }

    public ExecutionEngine onExecutionStart(Consumer<TestPlan> callback) {
        this.onExecutionStart = callback;
        return this;
    }

    public ExecutionEngine onExecutionComplete(Consumer<List<StepResult>> callback) {
        this.onExecutionComplete = callback;
        return this;
    }

    /**
     * Retorna contexto de execução
     */
    public ExecutionContext getContext() {
        return context;
    }

    /**
     * Retorna resultados
     */
    public List<StepResult> getResults() {
        return new ArrayList<>(results);
    }

    /**
     * Estatísticas da execução
     */
    public ExecutionStats getStats() {
        return new ExecutionStats(results);
    }

    /**
     * Estatísticas de execução
     */
    public static class ExecutionStats {
        private final int total;
        private final int success;
        private final int failed;
        private final int skipped;

        public ExecutionStats(List<StepResult> results) {
            this.total = results.size();
            this.success = (int) results.stream().filter(StepResult::isSuccess).count();
            this.failed = (int) results.stream().filter(StepResult::isFailed).count();
            this.skipped = (int) results.stream().filter(r -> r.getStatus() == StepStatus.SKIPPED).count();
        }

        public int getTotal() { return total; }
        public int getSuccess() { return success; }
        public int getFailed() { return failed; }
        public int getSkipped() { return skipped; }
        public double getSuccessRate() { return total > 0 ? (double) success / total * 100 : 0; }
    }
}

package br.com.qasuite.core;

import br.com.qasuite.domain.TestPlan;
import br.com.qasuite.domain.StepResult;
import com.microsoft.playwright.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Motor de execução paralela de testes
 * Permite executar múltiplos testes simultaneamente usando threads
 */
public class ParallelExecutionEngine {

    private final int maxParallelTests;
    private final ExecutorService executor;
    private final List<TestExecution> executions;
    private final Map<String, TestResult> results;
    private Consumer<TestExecutionProgress> progressCallback;

    public ParallelExecutionEngine(int maxParallelTests) {
        this.maxParallelTests = maxParallelTests;
        this.executor = Executors.newFixedThreadPool(maxParallelTests);
        this.executions = new ArrayList<>();
        this.results = new ConcurrentHashMap<>();
    }

    /**
     * Adiciona um teste para execução
     */
    public void addTest(String testId, TestPlan testPlan, String browserType) {
        executions.add(new TestExecution(testId, testPlan, browserType));
    }

    /**
     * Define callback para progresso
     */
    public ParallelExecutionEngine onProgress(Consumer<TestExecutionProgress> callback) {
        this.progressCallback = callback;
        return this;
    }

    /**
     * Executa todos os testes em paralelo
     */
    public Map<String, TestResult> executeAll() throws InterruptedException {
        System.out.println("[ParallelExecution] Starting " + executions.size() + " tests with " + maxParallelTests + " threads");

        List<Future<TestResult>> futures = new ArrayList<>();
        AtomicInteger completed = new AtomicInteger(0);

        for (TestExecution execution : executions) {
            Future<TestResult> future = executor.submit(() -> {
                try {
                    TestResult result = executeSingleTest(execution);
                    int done = completed.incrementAndGet();

                    if (progressCallback != null) {
                        progressCallback.accept(new TestExecutionProgress(
                            execution.testId,
                            done,
                            executions.size(),
                            result.status,
                            result.durationMs
                        ));
                    }

                    return result;
                } catch (Exception e) {
                    System.err.println("[ParallelExecution] Error executing test " + execution.testId + ": " + e.getMessage());
                    return new TestResult("failed", 0, e.getMessage());
                }
            });
            futures.add(future);
        }

        // Aguarda todos terminarem
        for (int i = 0; i < futures.size(); i++) {
            try {
                TestResult result = futures.get(i).get();
                results.put(executions.get(i).testId, result);
            } catch (ExecutionException e) {
                results.put(executions.get(i).testId, new TestResult("failed", 0, e.getMessage()));
            }
        }

        System.out.println("[ParallelExecution] All tests completed");
        return results;
    }

    /**
     * Executa um único teste
     */
    private TestResult executeSingleTest(TestExecution execution) {
        long startTime = System.currentTimeMillis();
        String testId = execution.testId;

        System.out.println("[ParallelExecution] Starting test: " + testId + " (" + execution.browserType + ")");

        try (Playwright playwright = Playwright.create()) {
            // Cria browser com base no tipo
            Browser browser = createBrowser(playwright, execution.browserType);

            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setRecordVideoDir(Paths.get("output/videos/" + testId))
            );

            Page page = context.newPage();

            // Configura timeout padrão
            page.setDefaultTimeout(30000);
            page.setDefaultNavigationTimeout(30000);

            // Executa o teste
            ExecutionEngine engine = new ExecutionEngine(page)
                .withMaxRetries(2)
                .withStepDelay(500)
                .withContinueOnError(true)
                .withScreenshotOnError(true)
                .onStepStart(result -> {
                    System.out.println("[" + testId + "] ▶️ Step " + result.getStep().getOrder());
                })
                .onStepComplete(result -> {
                    String icon = result.isSuccess() ? "✅" : result.isFailed() ? "❌" : "⏭️";
                    System.out.println("[" + testId + "] " + icon + " Step " + result.getStep().getOrder() + " " + result.getStatus());
                });

            List<StepResult> stepResults = engine.execute(execution.testPlan);

            // Fecha contexto e salva video
            Path videoPath = page.video() != null ? page.video().path() : null;
            context.close();
            browser.close();

            long duration = System.currentTimeMillis() - startTime;

            // Calcula estatísticas
            long successCount = stepResults.stream().filter(StepResult::isSuccess).count();
            long failedCount = stepResults.stream().filter(StepResult::isFailed).count();

            String status = failedCount == 0 ? "passed" : successCount > 0 ? "partial" : "failed";

            return new TestResult(status, duration, null, stepResults, videoPath);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            System.err.println("[ParallelExecution] Test " + testId + " failed: " + e.getMessage());
            return new TestResult("failed", duration, e.getMessage());
        }
    }

    /**
     * Cria browser baseado no tipo
     */
    private Browser createBrowser(Playwright playwright, String browserType) {
        return switch (browserType.toLowerCase()) {
            case "firefox" -> playwright.firefox().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(Arrays.asList("--no-sandbox", "--disable-setuid-sandbox")));
            case "webkit" -> playwright.webkit().launch(new BrowserType.LaunchOptions()
                .setHeadless(true));
            default -> playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(Arrays.asList("--no-sandbox", "--disable-setuid-sandbox", "--disable-dev-shm-usage")));
        };
    }

    /**
     * Retorna resultados
     */
    public Map<String, TestResult> getResults() {
        return new HashMap<>(results);
    }

    /**
     * Gera relatório consolidado
     */
    public ExecutionSummary generateSummary() {
        int total = results.size();
        long passed = results.values().stream().filter(r -> "passed".equals(r.status)).count();
        long failed = results.values().stream().filter(r -> "failed".equals(r.status)).count();
        long partial = results.values().stream().filter(r -> "partial".equals(r.status)).count();

        long totalDuration = results.values().stream().mapToLong(r -> r.durationMs).sum();

        return new ExecutionSummary(total, (int) passed, (int) failed, (int) partial, totalDuration);
    }

    /**
     * Encerra executor
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    // Classes auxiliares

    public static class TestExecution {
        public final String testId;
        public final TestPlan testPlan;
        public final String browserType;

        public TestExecution(String testId, TestPlan testPlan, String browserType) {
            this.testId = testId;
            this.testPlan = testPlan;
            this.browserType = browserType;
        }
    }

    public static class TestResult {
        public final String status;
        public final long durationMs;
        public final String error;
        public final List<StepResult> stepResults;
        public final Path videoPath;

        public TestResult(String status, long durationMs, String error) {
            this(status, durationMs, error, null, null);
        }

        public TestResult(String status, long durationMs, String error, List<StepResult> stepResults, Path videoPath) {
            this.status = status;
            this.durationMs = durationMs;
            this.error = error;
            this.stepResults = stepResults;
            this.videoPath = videoPath;
        }
    }

    public static class TestExecutionProgress {
        public final String testId;
        public final int completed;
        public final int total;
        public final String status;
        public final long durationMs;

        public TestExecutionProgress(String testId, int completed, int total, String status, long durationMs) {
            this.testId = testId;
            this.completed = completed;
            this.total = total;
            this.status = status;
            this.durationMs = durationMs;
        }
    }

    public static class ExecutionSummary {
        public final int totalTests;
        public final int passed;
        public final int failed;
        public final int partial;
        public final long totalDurationMs;
        public final double successRate;

        public ExecutionSummary(int totalTests, int passed, int failed, int partial, long totalDurationMs) {
            this.totalTests = totalTests;
            this.passed = passed;
            this.failed = failed;
            this.partial = partial;
            this.totalDurationMs = totalDurationMs;
            this.successRate = totalTests > 0 ? (double) (passed + partial) / totalTests * 100 : 0;
        }
    }
}

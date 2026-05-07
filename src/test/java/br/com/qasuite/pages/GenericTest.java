package br.com.qasuite.pages;

import br.com.qasuite.config.BaseTest;
import br.com.qasuite.core.ExecutionEngine;
import br.com.qasuite.core.ExecutionEngine.ExecutionStats;
import br.com.qasuite.core.TestExecutor;
import br.com.qasuite.domain.TestPlan;
import br.com.qasuite.domain.StepResult;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Teste genérico que executa qualquer descrição textual ou DSL.
 * Funciona com qualquer sistema sem necessidade de código específico.
 */
public class GenericTest extends BaseTest {

    @Override
    protected String getTipoTeste() {
        return "generic";
    }

    @Test
    public void testGeneric() throws Exception {
        System.out.println("[GenericTest] Iniciando teste genérico");

        // Lê o metadata do arquivo
        Path metadataFile = Path.of("data/current_test_metadata.json");
        if (!Files.exists(metadataFile)) {
            throw new RuntimeException("Arquivo de metadata não encontrado: " + metadataFile);
        }

        String metadataContent = Files.readString(metadataFile);
        JsonObject metadata = JsonParser.parseString(metadataContent).getAsJsonObject();

        String description = metadata.get("description").getAsString();
        String testType = metadata.has("testType") ? metadata.get("testType").getAsString() : "generic";
        String testName = metadata.has("name") ? metadata.get("name").getAsString() : "Generic Test";

        System.out.println("[GenericTest] Tipo de teste: " + testType);
        System.out.println("[GenericTest] Nome: " + testName);
        System.out.println("[GenericTest] Descrição: " + description.substring(0, Math.min(100, description.length())) + "...");

        // Verifica se existe testData (DSL format)
        if (metadata.has("testData") && !metadata.get("testData").isJsonNull()) {
            String testData = metadata.get("testData").getAsString();
            if (testData != null && !testData.isEmpty() && !testData.equals("{}")) {
                System.out.println("[GenericTest] Executando com DSL (TestPlan)");
                executeWithDSL(testData, testName);
                return;
            }
        }

        // Fallback: executa com descrição textual (modo legacy)
        System.out.println("[GenericTest] Executando com descrição textual (legacy)");
        TestExecutor executor = new TestExecutor(page);
        TestExecutor.ExecutionResult result = executor.executar(description);

        // Verificar se houve falhas
        if (result.hasErrors()) {
            System.err.println("[GenericTest] Teste falhou com " + result.getErrorCount() + " erro(s)");
            throw new AssertionError("Teste falhou: " + result.getErrorCount() + " passo(s) com erro. Último erro: " + result.getLastError());
        }

        System.out.println("[GenericTest] Teste genérico concluído com sucesso!");
    }

    private void executeWithDSL(String testDataJson, String testName) throws Exception {
        Gson gson = new Gson();

        // Parse the TestPlan from JSON
        TestPlan testPlan = gson.fromJson(testDataJson, TestPlan.class);
        testPlan.setName(testName);

        System.out.println("[GenericTest] TestPlan carregado: " + testPlan.getName());
        System.out.println("[GenericTest] Total de passos: " + testPlan.getTotalSteps());

        // Create ExecutionEngine with callbacks for real-time reporting
        ExecutionEngine engine = new ExecutionEngine(page)
            .withMaxRetries(2)
            .withStepDelay(500)
            .withContinueOnError(true)
            .withScreenshotOnError(true)
            .onStepStart(result -> {
                System.out.println("[Execution] ▶️ Step " + result.getStep().getOrder() + 
                    ": " + result.getStep().getDescription());
            })
            .onStepComplete(result -> {
                String icon = result.isSuccess() ? "✅" : result.isFailed() ? "❌" : "⏭️";
                System.out.println("[Execution] " + icon + " Step " + result.getStep().getOrder() + 
                    " " + result.getStatus().getDescription() + 
                    " (" + result.getDurationMs() + "ms)");
                if (result.getErrorDetails() != null) {
                    System.out.println("[Execution]    Erro: " + result.getErrorDetails());
                }
            })
            .onExecutionStart(plan -> {
                System.out.println("[Execution] 🚀 Iniciando execução: " + plan.getName());
            })
            .onExecutionComplete(results -> {
                ExecutionStats stats = new ExecutionStats(results);
                System.out.println("[Execution] 📊 Estatísticas:");
                System.out.println("[Execution]    Total: " + stats.getTotal());
                System.out.println("[Execution]    Sucesso: " + stats.getSuccess());
                System.out.println("[Execution]    Falhas: " + stats.getFailed());
                System.out.println("[Execution]    Taxa: " + String.format("%.1f", stats.getSuccessRate()) + "%");
            });

        // Execute the test
        List<StepResult> results = engine.execute(testPlan);

        // Check results
        ExecutionStats stats = engine.getStats();
        System.out.println("[GenericTest] Execução concluída: " + stats.getSuccess() + "/" + stats.getTotal() + " passos com sucesso");

        // Fail test if all steps failed
        if (stats.getFailed() == stats.getTotal() && stats.getTotal() > 0) {
            throw new AssertionError("Todos os passos falharam");
        }

        // Save execution results to file for report generation
        saveExecutionResults(results, testPlan);
    }

    private void saveExecutionResults(List<StepResult> results, TestPlan plan) {
        try {
            JsonObject report = new JsonObject();
            report.addProperty("testName", plan.getName());
            report.addProperty("executionTime", System.currentTimeMillis());
            report.addProperty("totalSteps", results.size());

            Gson gson = new Gson();
            JsonArray stepsArray = new JsonArray();
            for (StepResult result : results) {
                JsonObject stepObj = new JsonObject();
                stepObj.addProperty("order", result.getStep().getOrder());
                stepObj.addProperty("action", result.getStep().getAction() != null ? result.getStep().getAction().name() : "UNKNOWN");
                stepObj.addProperty("description", result.getStep().getDescription());
                stepObj.addProperty("status", result.getStatus().name());
                stepObj.addProperty("durationMs", result.getDurationMs());
                stepObj.addProperty("error", result.getErrorDetails());
                stepObj.addProperty("screenshot", result.getScreenshotPath());
                stepsArray.add(stepObj);
            }
            report.add("steps", stepsArray);

            Path reportFile = Path.of("data/execution_results.json");
            Files.writeString(reportFile, gson.toJson(report));
            System.out.println("[GenericTest] Resultados salvos em: " + reportFile);
        } catch (Exception e) {
            System.err.println("[GenericTest] Erro ao salvar resultados: " + e.getMessage());
        }
    }
}

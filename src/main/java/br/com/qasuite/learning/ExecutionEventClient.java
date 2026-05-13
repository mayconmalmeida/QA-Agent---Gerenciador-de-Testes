package br.com.qasuite.learning;

import com.google.gson.Gson;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class ExecutionEventClient {
    private static final Gson gson = new Gson();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final GuiServerLocator locator;

    public ExecutionEventClient() {
        this(new GuiServerLocator());
    }

    public ExecutionEventClient(GuiServerLocator locator) {
        this.locator = locator;
        this.http = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(10))
            .build();
    }

    public void send(String executionId, Map<String, Object> payload) {
        if (executionId == null || executionId.isBlank()) {
            return;
        }
        if (payload == null || payload.isEmpty()) {
            return;
        }

        String url = locator.getBaseUrl() + "/api/executions/" + LearningModeClient.encode(executionId) + "/events";
        RequestBody body = RequestBody.create(gson.toJson(payload), JSON);
        Request req = new Request.Builder().url(url).post(body).build();
        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) {
                String msg = res.body() != null ? res.body().string() : "";
                System.err.println("[ExecutionEvent] Falha ao enviar evento (" + res.code() + "): " + msg);
            }
        } catch (Exception e) {
            System.err.println("[ExecutionEvent] Falha ao enviar evento: " + e.getMessage());
        }
    }

    public void sendStart(String executionId, String testName, int totalSteps) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "execution_start");
        payload.put("testName", testName);
        payload.put("totalSteps", totalSteps);
        payload.put("timestamp", System.currentTimeMillis());
        send(executionId, payload);
    }

    public void sendStepProgress(String executionId, int stepNumber, int totalSteps, String action, String description,
                                 String status, long durationMs, String error, String screenshotPath) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "step_progress");
        payload.put("stepNumber", stepNumber);
        payload.put("totalSteps", totalSteps);
        payload.put("action", action != null ? action : "UNKNOWN");
        payload.put("description", description != null ? description : "");
        payload.put("status", status != null ? status : "UNKNOWN");
        payload.put("durationMs", durationMs);
        payload.put("progress", totalSteps > 0 ? (int) (((double) stepNumber / (double) totalSteps) * 100) : 0);
        payload.put("timestamp", System.currentTimeMillis());
        if (error != null && !error.isBlank()) {
            payload.put("error", error);
        }
        if (screenshotPath != null && !screenshotPath.isBlank()) {
            payload.put("screenshot", screenshotPath);
        }
        send(executionId, payload);
    }

    public void sendComplete(String executionId, int successCount, int failedCount, int totalCount) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "execution_complete");
        payload.put("successCount", successCount);
        payload.put("failedCount", failedCount);
        payload.put("totalCount", totalCount);
        payload.put("successRate", totalCount > 0 ? (int) (((double) successCount / (double) totalCount) * 100) : 0);
        payload.put("timestamp", System.currentTimeMillis());
        send(executionId, payload);
    }

    public void sendError(String executionId, String error) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "execution_error");
        payload.put("error", error != null ? error : "Erro desconhecido");
        payload.put("timestamp", System.currentTimeMillis());
        send(executionId, payload);
    }
}


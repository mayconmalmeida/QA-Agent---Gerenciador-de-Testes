package br.com.qasuite.server;

import br.com.qasuite.domain.StepResult;
import br.com.qasuite.domain.TestPlan;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.javalin.websocket.WsCloseStatus;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket para transmissão de execução de testes em tempo real
 */
public class ExecutionWebSocket {

    private static final Gson gson = new Gson();
    private static final Map<String, WsContext> sessions = new ConcurrentHashMap<>();
    private static final Map<String, String> executionStatuses = new ConcurrentHashMap<>();

    /**
     * Configura o WebSocket
     */
    public static void configure(WsConfig wsConfig) {
        wsConfig.onConnect(ctx -> {
            String executionId = ctx.pathParam("executionId");
            sessions.put(executionId, ctx);
            executionStatuses.put(executionId, "connected");
            System.out.println("[WebSocket] Client connected: " + executionId);

            // Send initial connection confirmation
            JsonObject message = new JsonObject();
            message.addProperty("type", "connected");
            message.addProperty("executionId", executionId);
            message.addProperty("timestamp", System.currentTimeMillis());
            ctx.send(gson.toJson(message));
        });

        wsConfig.onClose(ctx -> {
            String executionId = ctx.pathParam("executionId");
            sessions.remove(executionId);
            executionStatuses.remove(executionId);
            System.out.println("[WebSocket] Client disconnected: " + executionId + " (status: " + ctx.status() + ")");
        });

        wsConfig.onError(ctx -> {
            String executionId = ctx.pathParam("executionId");
            System.err.println("[WebSocket] Error for " + executionId + ": " + ctx.error().getMessage());
        });

        wsConfig.onMessage(ctx -> {
            // Handle client messages if needed
            System.out.println("[WebSocket] Message from client: " + ctx.message());
        });
    }

    /**
     * Envia atualização de status da execução
     */
    public static void sendExecutionUpdate(String executionId, String status, String message, Object data) {
        WsContext ctx = sessions.get(executionId);
        if (ctx == null || !ctx.session.isOpen()) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("type", "execution_update");
        payload.addProperty("executionId", executionId);
        payload.addProperty("status", status);
        payload.addProperty("message", message);
        payload.addProperty("timestamp", System.currentTimeMillis());

        if (data != null) {
            payload.add("data", gson.toJsonTree(data));
        }

        ctx.send(gson.toJson(payload));
    }

    /**
     * Envia início de execução
     */
    public static void sendExecutionStart(String executionId, TestPlan plan) {
        WsContext ctx = sessions.get(executionId);
        if (ctx == null || !ctx.session.isOpen()) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("type", "execution_start");
        payload.addProperty("executionId", executionId);
        payload.addProperty("testName", plan.getName());
        payload.addProperty("totalSteps", plan.getTotalSteps());
        payload.addProperty("timestamp", System.currentTimeMillis());

        ctx.send(gson.toJson(payload));

        executionStatuses.put(executionId, "running");
    }

    /**
     * Envia progresso de um passo
     */
    public static void sendStepProgress(String executionId, StepResult result, int currentStep, int totalSteps) {
        WsContext ctx = sessions.get(executionId);
        if (ctx == null || !ctx.session.isOpen()) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("type", "step_progress");
        payload.addProperty("executionId", executionId);
        payload.addProperty("stepNumber", currentStep);
        payload.addProperty("totalSteps", totalSteps);
        payload.addProperty("action", result.getStep().getAction() != null ? result.getStep().getAction().name() : "UNKNOWN");
        payload.addProperty("description", result.getStep().getDescription());
        payload.addProperty("status", result.getStatus().name());
        payload.addProperty("durationMs", result.getDurationMs());
        payload.addProperty("progress", (int) (((double) currentStep / totalSteps) * 100));
        payload.addProperty("timestamp", System.currentTimeMillis());

        if (result.getErrorDetails() != null) {
            payload.addProperty("error", result.getErrorDetails());
        }
        if (result.getScreenshotPath() != null) {
            payload.addProperty("screenshot", result.getScreenshotPath());
        }

        ctx.send(gson.toJson(payload));
    }

    /**
     * Envia conclusão da execução
     */
    public static void sendExecutionComplete(String executionId, int successCount, int failedCount, int totalCount) {
        WsContext ctx = sessions.get(executionId);
        if (ctx == null || !ctx.session.isOpen()) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("type", "execution_complete");
        payload.addProperty("executionId", executionId);
        payload.addProperty("successCount", successCount);
        payload.addProperty("failedCount", failedCount);
        payload.addProperty("totalCount", totalCount);
        payload.addProperty("successRate", totalCount > 0 ? (int) (((double) successCount / totalCount) * 100) : 0);
        payload.addProperty("timestamp", System.currentTimeMillis());

        ctx.send(gson.toJson(payload));

        executionStatuses.put(executionId, "completed");
    }

    /**
     * Envia erro na execução
     */
    public static void sendExecutionError(String executionId, String error) {
        WsContext ctx = sessions.get(executionId);
        if (ctx == null || !ctx.session.isOpen()) {
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("type", "execution_error");
        payload.addProperty("executionId", executionId);
        payload.addProperty("error", error);
        payload.addProperty("timestamp", System.currentTimeMillis());

        ctx.send(gson.toJson(payload));

        executionStatuses.put(executionId, "error");
    }

    /**
     * Verifica se há clientes conectados
     */
    public static boolean hasConnectedClient(String executionId) {
        WsContext ctx = sessions.get(executionId);
        return ctx != null && ctx.session.isOpen();
    }

    /**
     * Retorna status atual da execução
     */
    public static String getExecutionStatus(String executionId) {
        return executionStatuses.getOrDefault(executionId, "unknown");
    }

    /**
     * Lista todas as execuções ativas
     */
    public static Set<String> getActiveExecutions() {
        return sessions.keySet();
    }
}

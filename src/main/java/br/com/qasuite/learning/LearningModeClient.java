package br.com.qasuite.learning;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.time.Duration;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LearningModeClient {
    private static final Gson gson = new Gson();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final GuiServerLocator locator;
    private final LearningModeConfig config;

    public LearningModeClient() {
        this(new GuiServerLocator(), new LearningModeConfig());
    }

    public LearningModeClient(GuiServerLocator locator, LearningModeConfig config) {
        this.locator = locator;
        this.config = config;
        this.http = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(20))
            .build();
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    public Map<String, Object> askAndWait(String executionId, String componentName, String moduleName, String screenName,
                                         String step, String reason) {
        String questionId = UUID.randomUUID().toString();
        Map<String, Object> question = new HashMap<>();
        question.put("questionId", questionId);
        question.put("componentName", componentName);
        question.put("moduleName", moduleName);
        question.put("screenName", screenName);
        question.put("step", step);
        question.put("reason", reason);

        boolean posted = postQuestion(executionId, question);
        if (!posted) {
            return null;
        }

        long start = System.currentTimeMillis();
        int maxWait = config.maxWaitMs();
        while (System.currentTimeMillis() - start < maxWait) {
            Map<String, Object> answer = pollAnswer(executionId, questionId, 15000);
            if (answer != null) {
                return answer;
            }
        }
        return null;
    }

    private boolean postQuestion(String executionId, Map<String, Object> question) {
        String url = locator.getBaseUrl() + "/api/executions/" + encode(executionId) + "/learning/question";
        RequestBody body = RequestBody.create(gson.toJson(question), JSON);
        Request req = new Request.Builder().url(url).post(body).build();
        try (Response res = http.newCall(req).execute()) {
            return res.isSuccessful();
        } catch (Exception e) {
            System.err.println("[LearningMode] Falha ao postar pergunta: " + e.getMessage());
            return false;
        }
    }

    private Map<String, Object> pollAnswer(String executionId, String questionId, int waitMs) {
        String url = locator.getBaseUrl() + "/api/executions/" + encode(executionId) + "/learning/answer/" + encode(questionId)
            + "?waitMs=" + Math.max(0, Math.min(waitMs, 15000));
        Request req = new Request.Builder().url(url).get().build();
        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) {
                return null;
            }
            String raw = res.body() != null ? res.body().string() : null;
            if (raw == null || raw.isBlank()) {
                return null;
            }
            return gson.fromJson(raw, new TypeToken<Map<String, Object>>(){}.getType());
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String encode(String raw) {
        if (raw == null) {
            return "";
        }
        return URLEncoder.encode(raw, StandardCharsets.UTF_8);
    }
}


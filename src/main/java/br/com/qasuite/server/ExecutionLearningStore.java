package br.com.qasuite.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExecutionLearningStore {
    private final Map<String, Map<String, Object>> questionsByExecution = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> answersByExecution = new ConcurrentHashMap<>();

    public void putQuestion(String executionId, String questionId, Map<String, Object> question) {
        questionsByExecution.computeIfAbsent(executionId, k -> new ConcurrentHashMap<>()).put(questionId, question);
    }

    public Map<String, Object> getQuestion(String executionId, String questionId) {
        Map<String, Object> questions = questionsByExecution.get(executionId);
        if (questions == null) {
            return null;
        }
        Object q = questions.get(questionId);
        if (q instanceof Map) {
            return (Map<String, Object>) q;
        }
        return null;
    }

    public void clearQuestion(String executionId, String questionId) {
        Map<String, Object> questions = questionsByExecution.get(executionId);
        if (questions != null) {
            questions.remove(questionId);
        }
    }

    public void putAnswer(String executionId, String questionId, Map<String, Object> answer) {
        answersByExecution.computeIfAbsent(executionId, k -> new ConcurrentHashMap<>()).put(questionId, answer);
    }

    public Map<String, Object> getAnswer(String executionId, String questionId) {
        Map<String, Object> answers = answersByExecution.get(executionId);
        if (answers == null) {
            return null;
        }
        Object a = answers.get(questionId);
        if (a instanceof Map) {
            return (Map<String, Object>) a;
        }
        return null;
    }

    public Map<String, Object> consumeAnswer(String executionId, String questionId) {
        Map<String, Object> answers = answersByExecution.get(executionId);
        if (answers == null) {
            return null;
        }
        Object a = answers.remove(questionId);
        if (a instanceof Map) {
            return (Map<String, Object>) a;
        }
        return null;
    }
}


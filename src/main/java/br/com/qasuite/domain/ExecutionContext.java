package br.com.qasuite.domain;

import com.microsoft.playwright.Page;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * Contexto de execução - mantém estado durante o teste
 */
public class ExecutionContext {
    private Page page;
    private String currentUrl;
    private String lastElementUsed;
    private Map<String, String> filledValues;
    private Map<String, Object> variables;
    private Stack<String> navigationStack;
    private boolean isLoggedIn;
    private String loggedUser;
    private long startTime;

    public ExecutionContext() {
        this.filledValues = new HashMap<>();
        this.variables = new HashMap<>();
        this.navigationStack = new Stack<>();
        this.isLoggedIn = false;
        this.startTime = System.currentTimeMillis();
    }

    public ExecutionContext(Page page) {
        this();
        this.page = page;
    }

    /**
     * Armazena valor preenchido em campo
     */
    public void storeFilledValue(String fieldName, String value) {
        filledValues.put(fieldName, value);
    }

    /**
     * Recupera valor preenchido anteriormente
     */
    public String getFilledValue(String fieldName) {
        return filledValues.get(fieldName);
    }

    /**
     * Armazena variável genérica
     */
    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }

    /**
     * Recupera variável
     */
    public Object getVariable(String key) {
        return variables.get(key);
    }

    /**
     * Registra navegação para página
     */
    public void pushNavigation(String url) {
        navigationStack.push(url);
        this.currentUrl = url;
    }

    /**
     * Volta para página anterior
     */
    public String popNavigation() {
        if (!navigationStack.isEmpty()) {
            navigationStack.pop();
            return navigationStack.isEmpty() ? null : navigationStack.peek();
        }
        return null;
    }

    /**
     * Marca login como realizado
     */
    public void markLoggedIn(String username) {
        this.isLoggedIn = true;
        this.loggedUser = username;
    }

    /**
     * Verifica se já está logado
     */
    public boolean isLoggedIn() {
        return isLoggedIn;
    }

    /**
     * Tempo de execução em ms
     */
    public long getExecutionTimeMs() {
        return System.currentTimeMillis() - startTime;
    }

    // Getters e Setters
    public Page getPage() {
        return page;
    }

    public void setPage(Page page) {
        this.page = page;
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public void setCurrentUrl(String currentUrl) {
        this.currentUrl = currentUrl;
    }

    public String getLastElementUsed() {
        return lastElementUsed;
    }

    public void setLastElementUsed(String lastElementUsed) {
        this.lastElementUsed = lastElementUsed;
    }

    public String getLoggedUser() {
        return loggedUser;
    }

    public Map<String, String> getFilledValues() {
        return new HashMap<>(filledValues);
    }

    public void reset() {
        filledValues.clear();
        variables.clear();
        navigationStack.clear();
        isLoggedIn = false;
        loggedUser = null;
        startTime = System.currentTimeMillis();
    }
}

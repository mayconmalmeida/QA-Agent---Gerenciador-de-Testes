package br.com.qasuite.core;

import java.util.List;
import java.util.Map;

/**
 * DTO que representa os artefatos gerados pela IA para um teste.
 */
public class ArtefatosTest {

    private String gherkin;
    private String java;
    private String jira;
    private List<String> cenarios;
    private Map<String, Boolean> tiposCobertura;
    private String risco;

    public ArtefatosTest() {
    }

    public ArtefatosTest(String gherkin, String java, String jira, 
                         List<String> cenarios, Map<String, Boolean> tiposCobertura, String risco) {
        this.gherkin = gherkin;
        this.java = java;
        this.jira = jira;
        this.cenarios = cenarios;
        this.tiposCobertura = tiposCobertura;
        this.risco = risco;
    }

    // Getters e Setters
    public String getGherkin() {
        return gherkin;
    }

    public void setGherkin(String gherkin) {
        this.gherkin = gherkin;
    }

    public String getJava() {
        return java;
    }

    public void setJava(String java) {
        this.java = java;
    }

    public String getJira() {
        return jira;
    }

    public void setJira(String jira) {
        this.jira = jira;
    }

    public List<String> getCenarios() {
        return cenarios;
    }

    public void setCenarios(List<String> cenarios) {
        this.cenarios = cenarios;
    }

    public Map<String, Boolean> getTiposCobertura() {
        return tiposCobertura;
    }

    public void setTiposCobertura(Map<String, Boolean> tiposCobertura) {
        this.tiposCobertura = tiposCobertura;
    }

    public String getRisco() {
        return risco;
    }

    public void setRisco(String risco) {
        this.risco = risco;
    }

    /**
     * Verifica se um tipo de cobertura está presente
     */
    public boolean hasCobertura(String tipo) {
        if (tiposCobertura == null) return false;
        return tiposCobertura.getOrDefault(tipo, false);
    }

    @Override
    public String toString() {
        return "ArtefatosTest{" +
                "cenarios=" + cenarios +
                ", tiposCobertura=" + tiposCobertura +
                ", risco='" + risco + '\'' +
                '}';
    }
}

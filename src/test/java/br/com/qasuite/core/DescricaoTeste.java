package br.com.qasuite.core;

import java.util.List;

/**
 * DTO que representa a descrição de um teste para geração via IA.
 */
public class DescricaoTeste {

    private String modulo;
    private String requisito;
    private String prioridade;
    private String tipoTeste;
    private List<String> passos;
    private List<String> tags;

    public DescricaoTeste() {
    }

    public DescricaoTeste(String modulo, String requisito, String prioridade, 
                          String tipoTeste, List<String> passos, List<String> tags) {
        this.modulo = modulo;
        this.requisito = requisito;
        this.prioridade = prioridade;
        this.tipoTeste = tipoTeste;
        this.passos = passos;
        this.tags = tags;
    }

    // Getters e Setters
    public String getModulo() {
        return modulo;
    }

    public void setModulo(String modulo) {
        this.modulo = modulo;
    }

    public String getRequisito() {
        return requisito;
    }

    public void setRequisito(String requisito) {
        this.requisito = requisito;
    }

    public String getPrioridade() {
        return prioridade;
    }

    public void setPrioridade(String prioridade) {
        this.prioridade = prioridade;
    }

    public String getTipoTeste() {
        return tipoTeste;
    }

    public void setTipoTeste(String tipoTeste) {
        this.tipoTeste = tipoTeste;
    }

    public List<String> getPassos() {
        return passos;
    }

    public void setPassos(List<String> passos) {
        this.passos = passos;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    /**
     * Retorna o nome do módulo formatado para uso em paths
     */
    public String getModuloPath() {
        if (modulo == null) return "geral";
        return modulo.toLowerCase()
                .replace(" ", "_")
                .replace("ã", "a")
                .replace("á", "a")
                .replace("â", "a")
                .replace("é", "e")
                .replace("ê", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ô", "o")
                .replace("õ", "o")
                .replace("ú", "u")
                .replace("ç", "c")
                .replace("/", "_")
                .replace("\\", "_");
    }

    @Override
    public String toString() {
        return "DescricaoTeste{" +
                "modulo='" + modulo + '\'' +
                ", requisito='" + requisito + '\'' +
                ", prioridade='" + prioridade + '\'' +
                ", tipoTeste='" + tipoTeste + '\'' +
                ", passos=" + passos +
                ", tags=" + tags +
                '}';
    }
}

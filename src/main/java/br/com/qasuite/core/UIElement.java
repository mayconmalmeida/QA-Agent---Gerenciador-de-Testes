package br.com.qasuite.core;

/**
 * Representa um elemento de UI detectado na página.
 */
public class UIElement {
    private final String seletor;
    private final int index;
    private final String texto;
    private final String placeholder;
    private final String ariaLabel;
    private final String tipo;
    private final String id;
    private final String name;
    
    public UIElement(String seletor, int index, String texto, String placeholder, 
                     String ariaLabel, String tipo, String id, String name) {
        this.seletor = seletor;
        this.index = index;
        this.texto = texto != null ? texto : "";
        this.placeholder = placeholder != null ? placeholder : "";
        this.ariaLabel = ariaLabel != null ? ariaLabel : "";
        this.tipo = tipo != null ? tipo : "";
        this.id = id != null ? id : "";
        this.name = name != null ? name : "";
    }
    
    public String getSeletor() { return seletor; }
    public int getIndex() { return index; }
    public String getTexto() { return texto; }
    public String getPlaceholder() { return placeholder; }
    public String getAriaLabel() { return ariaLabel; }
    public String getTipo() { return tipo; }
    public String getId() { return id; }
    public String getName() { return name; }
    
    /**
     * Retorna um seletor Playwright único para este elemento
     */
    public String getSeletorPlaywright() {
        // Prioridade: ID > Name > Texto > Seletor+Index
        if (!id.isEmpty()) {
            return "#" + id;
        }
        if (!name.isEmpty()) {
            return "[name='" + name + "']";
        }
        if (!texto.isEmpty() && texto.length() < 50) {
            return "text='" + texto + "'";
        }
        return seletor + ":nth-of-type(" + (index + 1) + ")";
    }
    
    @Override
    public String toString() {
        return String.format("%s [texto='%s', placeholder='%s', id='%s', name='%s']", 
            seletor, texto, placeholder, id, name);
    }
}

package br.com.sinncosaude.pages.atencao_primaria;

import br.com.sinncosaude.config.BaseTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class AcolhimentoTest extends BaseTest {

  @Override
  protected String getTipoTeste() {
    return "smoke";
  }

  @Test
  @Tag("smoke")
  public void testAcolhimento() {
    // Implementar teste para acolhimento
    System.out.println("Executando teste: Acolhimento");
  }
}

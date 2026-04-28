package br.com.qasuite.pages.atencao_primaria;

import br.com.qasuite.config.BaseTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class EscutaInicialTest extends BaseTest {

  @Override
  protected String getTipoTeste() {
    return "smoke";
  }

  @Test
  @Tag("smoke")
  public void testEscutaInicial() {
    // Implementar teste para escuta_inicial
    System.out.println("Executando teste: Escuta Inicial");
  }
}

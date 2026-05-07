package br.com.qasuite.pages.atencao_primaria;

import br.com.qasuite.config.BaseTest;
import br.com.qasuite.pages.atencaoprimaria.EscutaInicialPage;
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
    System.out.println("[EscutaInicialTest] Iniciando teste de escuta inicial");

    EscutaInicialPage escutaInicialPage = new EscutaInicialPage(page);
    escutaInicialPage.navegarParaEscutaInicial();
    escutaInicialPage.realizarEscutaInicial();

    System.out.println("[EscutaInicialTest] Teste concluído");
  }
}

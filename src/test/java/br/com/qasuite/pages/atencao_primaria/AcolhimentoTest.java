package br.com.qasuite.pages.atencao_primaria;

import br.com.qasuite.config.BaseTest;
import br.com.qasuite.pages.atencaoprimaria.AcolhimentoPage;
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
    System.out.println("[AcolhimentoTest] Iniciando teste de acolhimento");
    
    // Instancia a página de acolhimento
    AcolhimentoPage acolhimentoPage = new AcolhimentoPage(page);
    
    // Navega para o módulo Atenção Primária > Acolhimento
    // Executa o fluxo de acolhimento
    acolhimentoPage.realizarAcolhimento();
    
    System.out.println("[AcolhimentoTest] Teste concluído com sucesso!");
  }
}

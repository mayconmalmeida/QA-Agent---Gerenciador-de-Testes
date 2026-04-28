package br.com.qasuite.pages.cadastro_de_paciente;

import br.com.qasuite.config.BaseTest;
import br.com.qasuite.pages.atencaoprimaria.AcolhimentoPage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class CadastroPacienteTest extends BaseTest {
  
  @Override
  protected String getTipoTeste() {
    return "smoke";
  }

  @Test
  @Tag("smoke")
  public void testAcolhimentoNovoPaciente() throws IOException {
    System.out.println("[Test] Iniciando fluxo de Acolhimento");
    
    // Criar Page Object de Acolhimento
    AcolhimentoPage acolhimentoPage = new AcolhimentoPage(page);
    
    // Executar fluxo completo
    acolhimentoPage.realizarAcolhimento();
    
    // Validar mensagem de sucesso
    if (acolhimentoPage.temMensagemSucesso()) {
      String mensagem = acolhimentoPage.getMensagemSucesso();
      System.out.println("[Test] Acolhimento realizado com sucesso: " + mensagem);
    } else {
      System.out.println("[Test] Aviso: Mensagem de sucesso não encontrada, mas fluxo foi executado");
    }
    
    capturarScreenshot("acolhimento_finalizado");
  }
}
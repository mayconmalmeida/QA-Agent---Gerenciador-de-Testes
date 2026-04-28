package br.com.qasuite.core;

import br.com.qasuite.config.ConfigLoader;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Construtor de relatórios HTML para os testes executados.
 * Gera um relatório self-contained com CSS e JS inline.
 */
public class ReportBuilder {

    private static final List<ResultadoTeste> resultados = new ArrayList<>();
    private static long inicioExecucao = System.currentTimeMillis();

    /**
     * Registra o resultado de um teste
     */
    public static synchronized void registrarResultado(String nome, String modulo, 
                                                        String tipo, String status, 
                                                        long duracaoMs, String screenshot,
                                                        String mensagemErro) {
        // Try to load metadata from frontend
        String nomePersonalizado = null;
        String moduloFrontend = null;
        String menuFrontend = null;
        String descricao = null;
        
        try {
            // Tenta múltiplos caminhos possíveis
            Path[] possiblePaths = {
                Paths.get("data", "current_test_metadata.json"),  // relativo ao projeto
                Paths.get(System.getProperty("user.dir"), "data", "current_test_metadata.json"), // com user.dir
                Paths.get("sinnc-qa-agent", "data", "current_test_metadata.json"), // subpasta
            };
            
            Path metadataFile = null;
            for (Path path : possiblePaths) {
                System.out.println("[ReportBuilder] Checking path: " + path.toAbsolutePath());
                if (Files.exists(path)) {
                    metadataFile = path;
                    System.out.println("[ReportBuilder] Found metadata file at: " + path.toAbsolutePath());
                    break;
                }
            }
            
            if (metadataFile != null) {
                String content = new String(Files.readAllBytes(metadataFile));
                System.out.println("[ReportBuilder] Metadata content: " + content);
                
                JsonObject metadata = new Gson().fromJson(content, JsonObject.class);
                
                if (metadata.has("name")) {
                    nomePersonalizado = metadata.get("name").getAsString();
                    System.out.println("[ReportBuilder] Using custom name: " + nomePersonalizado);
                }
                if (metadata.has("module")) {
                    moduloFrontend = metadata.get("module").getAsString();
                    System.out.println("[ReportBuilder] Using custom module: " + moduloFrontend);
                }
                if (metadata.has("menu")) {
                    menuFrontend = metadata.get("menu").getAsString();
                }
                if (metadata.has("description")) {
                    descricao = metadata.get("description").getAsString();
                }
                // Use testType from metadata if available
                if (metadata.has("testType")) {
                    tipo = metadata.get("testType").getAsString();
                    System.out.println("[ReportBuilder] Using custom type: " + tipo);
                }
            } else {
                System.out.println("[ReportBuilder] Metadata file not found, using Java defaults");
                System.out.println("[ReportBuilder] Current dir: " + System.getProperty("user.dir"));
            }
        } catch (Exception e) {
            System.err.println("[ReportBuilder] Could not load metadata: " + e.getMessage());
            e.printStackTrace();
        }
        
        resultados.add(new ResultadoTeste(nome, modulo, tipo, status, duracaoMs, screenshot, mensagemErro,
                                         nomePersonalizado, moduloFrontend, menuFrontend, descricao));
    }

    /**
     * Gera o relatório HTML
     */
    public static void gerar() {
        try {
            String html = construirHtml();
            
            Path caminhoRelatorio = Paths.get(ConfigLoader.getRelatorioOutput());
            Files.createDirectories(caminhoRelatorio.getParent());
            
            try (FileWriter writer = new FileWriter(caminhoRelatorio.toFile())) {
                writer.write(html);
            }
            
            System.out.println("[ReportBuilder] Relatório gerado: " + caminhoRelatorio.toAbsolutePath());
            
            if (ConfigLoader.isRelatorioAbrirAposExecucao()) {
                abrirNavegador(caminhoRelatorio);
            }
            
        } catch (Exception e) {
            System.err.println("[ReportBuilder] Erro ao gerar relatório: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String construirHtml() {
        long duracaoTotal = System.currentTimeMillis() - inicioExecucao;
        
        int total = resultados.size();
        long passaram = resultados.stream().filter(r -> "PASSED".equals(r.status)).count();
        long falharam = resultados.stream().filter(r -> "FAILED".equals(r.status)).count();
        long pulados = resultados.stream().filter(r -> "SKIPPED".equals(r.status)).count();

        String dataHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        String usuario = System.getProperty("user.name");
        String ambiente = ConfigLoader.getRelatorioAmbiente();
        
        // Obter info do Git
        String branch = executarComandoGit("git rev-parse --abbrev-ref HEAD");
        String commit = executarComandoGit("git rev-parse --short HEAD");
        String versao = "1.0.0-SNAPSHOT";

        StringBuilder html = new StringBuilder();
        
        // HTML Header
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"pt-BR\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>").append(ConfigLoader.getRelatorioTitulo()).append("</title>\n");
        html.append("    <style>").append(getCss()).append("</style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        
        // Header
        html.append("    <header>\n");
        html.append("        <div class=\"logo\">QA Agent - Gerenciador de Testes</div>\n");
        html.append("        <div class=\"info\">\n");
        html.append("            <span>Data: ").append(dataHora).append("</span>\n");
        html.append("            <span>Ambiente: ").append(ambiente).append("</span>\n");
        html.append("            <span>Usuário: ").append(usuario).append("</span>\n");
        html.append("        </div>\n");
        html.append("    </header>\n");
        
        // Cards de Resumo
        html.append("    <section class=\"resumo\">\n");
        html.append("        <div class=\"card total\">\n");
        html.append("            <div class=\"numero\">").append(total).append("</div>\n");
        html.append("            <div class=\"label\">Total</div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"card passou\">\n");
        html.append("            <div class=\"numero\">").append(passaram).append("</div>\n");
        html.append("            <div class=\"label\">Passou</div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"card falhou\">\n");
        html.append("            <div class=\"numero\">").append(falharam).append("</div>\n");
        html.append("            <div class=\"label\">Falhou</div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"card pulado\">\n");
        html.append("            <div class=\"numero\">").append(pulados).append("</div>\n");
        html.append("            <div class=\"label\">Pulado</div>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"card tempo\">\n");
        html.append("            <div class=\"numero\">").append(formatarDuracao(duracaoTotal)).append("</div>\n");
        html.append("            <div class=\"label\">Tempo Total</div>\n");
        html.append("        </div>\n");
        html.append("    </section>\n");
        
        // Filtros
        html.append("    <section class=\"filtros\">\n");
        html.append("        <label>Filtrar por status:\n");
        html.append("            <select id=\"filtroStatus\" onchange=\"filtrarTabela()\">\n");
        html.append("                <option value=\"todos\">Todos</option>\n");
        html.append("                <option value=\"PASSED\">Passou</option>\n");
        html.append("                <option value=\"FAILED\">Falhou</option>\n");
        html.append("                <option value=\"SKIPPED\">Pulado</option>\n");
        html.append("            </select>\n");
        html.append("        </label>\n");
        html.append("        <label>Filtrar por tipo:\n");
        html.append("            <select id=\"filtroTipo\" onchange=\"filtrarTabela()\">\n");
        html.append("                <option value=\"todos\">Todos</option>\n");
        html.append("                <option value=\"smoke\">Smoke</option>\n");
        html.append("                <option value=\"regression\">Regression</option>\n");
        html.append("                <option value=\"critico\">Crítico</option>\n");
        html.append("            </select>\n");
        html.append("        </label>\n");
        html.append("    </section>\n");
        
        // Tabela de Resultados
        html.append("    <section class=\"resultados\">\n");
        html.append("        <h2>Resultados dos Testes</h2>\n");
        html.append("        <table id=\"tabelaResultados\">\n");
        html.append("            <thead>\n");
        html.append("                <tr>\n");
        html.append("                    <th>#</th>\n");
        html.append("                    <th>Nome do Teste</th>\n");
        html.append("                    <th>Módulo</th>\n");
        html.append("                    <th>Tipo</th>\n");
        html.append("                    <th>Status</th>\n");
        html.append("                    <th>Duração</th>\n");
        html.append("                    <th>Screenshot</th>\n");
        html.append("                </tr>\n");
        html.append("            </thead>\n");
        html.append("            <tbody>\n");
        
        for (int i = 0; i < resultados.size(); i++) {
            ResultadoTeste r = resultados.get(i);
            String statusClass = r.status.toLowerCase();
            String screenshotLink = r.screenshot != null 
                ? "<a href=\"file:///" + r.screenshot.replace("\\", "/") + "\" target=\"_blank\">Ver</a>"
                : "-";
            
            // Use frontend data if available, otherwise fallback to Java class data
            String displayName = r.nomePersonalizado != null ? r.nomePersonalizado : r.nome;
            String displayModulo = r.moduloFrontend != null ? r.moduloFrontend : r.modulo;
            
            html.append("                <tr class=\"").append(statusClass).append("\" data-status=\"").append(r.status).append("\" data-tipo=\"").append(r.tipo).append("\">\n");
            html.append("                    <td>").append(i + 1).append("</td>\n");
            html.append("                    <td>").append(escapeHtml(displayName)).append("</td>\n");
            html.append("                    <td>").append(escapeHtml(displayModulo)).append("</td>\n");
            html.append("                    <td>").append(r.tipo).append("</td>\n");
            html.append("                    <td class=\"status\">").append(r.status).append("</td>\n");
            html.append("                    <td>").append(formatarDuracao(r.duracaoMs)).append("</td>\n");
            html.append("                    <td>").append(screenshotLink).append("</td>\n");
            html.append("                </tr>\n");
        }
        
        html.append("            </tbody>\n");
        html.append("        </table>\n");
        html.append("    </section>\n");
        
        // Seção de Falhas
        if (falharam > 0) {
            html.append("    <section class=\"falhas\">\n");
            html.append("        <h2>Detalhes das Falhas</h2>\n");
            
            for (ResultadoTeste r : resultados) {
                if ("FAILED".equals(r.status)) {
                    html.append("        <div class=\"falha-detalhe\">\n");
                    html.append("            <h3>").append(escapeHtml(r.nome)).append("</h3>\n");
                    if (r.mensagemErro != null) {
                        html.append("            <p class=\"erro\">").append(escapeHtml(r.mensagemErro)).append("</p>\n");
                    }
                    if (r.screenshot != null) {
                        String screenshotPath = r.screenshot.replace("\\", "/");
                        html.append("            <div class=\"screenshot\">\n");
                        html.append("                <a href=\"file:///").append(screenshotPath).append("\" target=\"_blank\">\n");
                        html.append("                    <img src=\"file:///").append(screenshotPath).append("\" alt=\"Screenshot\" />\n");
                        html.append("                </a>\n");
                        html.append("            </div>\n");
                    }
                    html.append("        </div>\n");
                }
            }
            html.append("    </section>\n");
        }
        
        // Rodapé
        html.append("    <footer>\n");
        html.append("        <div class=\"versao\">Versão: ").append(versao).append("</div>\n");
        html.append("        <div class=\"git\">Branch: ").append(branch).append(" | Commit: ").append(commit).append("</div>\n");
        html.append("    </footer>\n");
        
        // JavaScript
        html.append("    <script>").append(getJs()).append("</script>\n");
        
        html.append("</body>\n");
        html.append("</html>");
        
        return html.toString();
    }

    private static String getCss() {
        return """
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; 
                   background: #f5f5f5; color: #333; line-height: 1.6; }
            
            header { background: linear-gradient(135deg, #1e5799 0%, #2989d8 100%); 
                     color: white; padding: 20px; }
            .logo { font-size: 24px; font-weight: bold; }
            .info { margin-top: 10px; font-size: 14px; opacity: 0.9; }
            .info span { margin-right: 20px; }
            
            .resumo { display: flex; gap: 20px; padding: 20px; flex-wrap: wrap; }
            .card { background: white; border-radius: 8px; padding: 20px; min-width: 120px; 
                    box-shadow: 0 2px 4px rgba(0,0,0,0.1); text-align: center; }
            .card .numero { font-size: 36px; font-weight: bold; }
            .card .label { color: #666; font-size: 14px; margin-top: 5px; }
            .card.total { border-top: 4px solid #3498db; }
            .card.passou { border-top: 4px solid #27ae60; }
            .card.passou .numero { color: #27ae60; }
            .card.falhou { border-top: 4px solid #e74c3c; }
            .card.falhou .numero { color: #e74c3c; }
            .card.pulado { border-top: 4px solid #f39c12; }
            .card.pulado .numero { color: #f39c12; }
            .card.tempo { border-top: 4px solid #9b59b6; }
            .card.tempo .numero { color: #9b59b6; }
            
            .filtros { padding: 0 20px 20px; }
            .filtros label { margin-right: 20px; }
            .filtros select { padding: 5px 10px; border-radius: 4px; border: 1px solid #ddd; }
            
            .resultados { padding: 0 20px 20px; }
            .resultados h2 { margin-bottom: 15px; color: #2c3e50; }
            table { width: 100%; background: white; border-radius: 8px; overflow: hidden; 
                    box-shadow: 0 2px 4px rgba(0,0,0,0.1); border-collapse: collapse; }
            th, td { padding: 12px; text-align: left; border-bottom: 1px solid #eee; }
            th { background: #34495e; color: white; font-weight: 600; }
            tr:hover { background: #f8f9fa; }
            tr.passed { background: #d4edda; }
            tr.failed { background: #f8d7da; }
            tr.skipped { background: #fff3cd; }
            .status { font-weight: bold; }
            
            .falhas { padding: 0 20px 20px; }
            .falhas h2 { margin-bottom: 15px; color: #e74c3c; }
            .falha-detalhe { background: white; border-radius: 8px; padding: 20px; 
                             margin-bottom: 15px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);
                             border-left: 4px solid #e74c3c; }
            .falha-detalhe h3 { color: #e74c3c; margin-bottom: 10px; }
            .falha-detalhe .erro { background: #fee; padding: 10px; border-radius: 4px; 
                                    font-family: monospace; font-size: 13px; margin-bottom: 10px; }
            .falha-detalhe .screenshot img { max-width: 300px; border-radius: 4px; cursor: pointer; }
            
            footer { background: #2c3e50; color: white; padding: 20px; text-align: center; 
                     font-size: 14px; margin-top: 20px; }
            footer .versao, footer .git { margin: 5px 0; opacity: 0.8; }
            
            a { color: #2980b9; text-decoration: none; }
            a:hover { text-decoration: underline; }
            """;
    }

    private static String getJs() {
        return """
            function filtrarTabela() {
                var statusFiltro = document.getElementById('filtroStatus').value;
                var tipoFiltro = document.getElementById('filtroTipo').value;
                var linhas = document.querySelectorAll('#tabelaResultados tbody tr');
                
                linhas.forEach(function(linha) {
                    var status = linha.getAttribute('data-status');
                    var tipo = linha.getAttribute('data-tipo');
                    
                    var mostrarStatus = statusFiltro === 'todos' || status === statusFiltro;
                    var mostrarTipo = tipoFiltro === 'todos' || tipo === tipoFiltro;
                    
                    linha.style.display = (mostrarStatus && mostrarTipo) ? '' : 'none';
                });
            }
            """;
    }

    private static void abrirNavegador(Path caminho) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + caminho.toUri());
            } else if (os.contains("mac")) {
                Desktop.getDesktop().browse(caminho.toUri());
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", caminho.toString()});
            }
        } catch (IOException e) {
            System.err.println("[ReportBuilder] Não foi possível abrir o navegador: " + e.getMessage());
        }
    }

    private static String formatarDuracao(long ms) {
        if (ms < 1000) {
            return ms + "ms";
        }
        long segundos = TimeUnit.MILLISECONDS.toSeconds(ms);
        if (segundos < 60) {
            return segundos + "s";
        }
        long minutos = TimeUnit.MILLISECONDS.toMinutes(ms);
        long segRestantes = segundos % 60;
        return minutos + "m " + segRestantes + "s";
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    private static String executarComandoGit(String comando) {
        try {
            Process process = Runtime.getRuntime().exec(comando);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String linha = reader.readLine();
            process.waitFor();
            return linha != null ? linha : "N/A";
        } catch (Exception e) {
            return "N/A";
        }
    }

    /**
     * Classe interna para armazenar resultado de um teste
     */
    private static class ResultadoTeste {
        final String nome;
        final String modulo;
        final String tipo;
        final String status;
        final long duracaoMs;
        final String screenshot;
        final String mensagemErro;
        // Dados do frontend
        final String nomePersonalizado;
        final String moduloFrontend;
        final String menuFrontend;
        final String descricao;

        ResultadoTeste(String nome, String modulo, String tipo, String status, 
                       long duracaoMs, String screenshot, String mensagemErro,
                       String nomePersonalizado, String moduloFrontend, String menuFrontend, String descricao) {
            this.nome = nome;
            this.modulo = modulo;
            this.tipo = tipo;
            this.status = status;
            this.duracaoMs = duracaoMs;
            this.screenshot = screenshot;
            this.mensagemErro = mensagemErro;
            this.nomePersonalizado = nomePersonalizado;
            this.moduloFrontend = moduloFrontend;
            this.menuFrontend = menuFrontend;
            this.descricao = descricao;
        }
    }
}

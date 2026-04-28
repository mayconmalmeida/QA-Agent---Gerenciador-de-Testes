package br.com.sinncosaude.cli;

import br.com.sinncosaude.config.ConfigLoader;
import br.com.sinncosaude.core.AgentIA;
import br.com.sinncosaude.core.ArtefatosTest;
import br.com.sinncosaude.core.DescricaoTeste;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * CLI interativo para geração de testes via IA.
 * Coleta informações do usuário e gera artefatos automaticamente.
 */
public class GerarTeste {

    private static final Scanner scanner = new Scanner(System.in);

    private static final String[] MODULOS = {
        "Cadastro de Paciente",
        "Agendamento",
        "Prontuário Eletrônico",
        "Prescrição Médica",
        "Faturamento / TISS",
        "Internação",
        "Farmácia Hospitalar",
        "Laudos e Exames"
    };

    private static final String[] TIPOS_TESTE = {
        "smoke",
        "regression",
        "critico"
    };

    private static final String[] PRIORIDADES = {
        "Alta",
        "Média",
        "Baixa"
    };

    public static void main(String[] args) {
        exibirCabecalho();

        try {
            DescricaoTeste descricao = coletarInformacoes();
            
            System.out.println("\nGerando artefatos com IA...");
            exibirProgresso();
            
            AgentIA agent = new AgentIA();
            ArtefatosTest artefatos = agent.gerarArtefatos(descricao);
            
            salvarArtefatos(artefatos, descricao);
            
            System.out.println("\n✓ .feature salvo em output/artefatos/" + descricao.getTipoTeste() + "/gherkin/" + descricao.getModuloPath() + "/");
            System.out.println("✓ Java salvo em output/artefatos/" + descricao.getTipoTeste() + "/java/" + descricao.getModuloPath() + "/");
            System.out.println("✓ Jira salvo em output/artefatos/" + descricao.getTipoTeste() + "/jira/" + descricao.getModuloPath() + "/");
            System.out.println("✓ .feature copiado para src/test/resources/features/" + descricao.getTipoTeste() + "/" + descricao.getModuloPath() + "/");
            
            System.out.print("\nDeseja executar o teste agora? (s/n): ");
            String resposta = scanner.nextLine().trim().toLowerCase();
            
            if (resposta.equals("s") || resposta.equals("sim")) {
                executarTeste(descricao.getTipoTeste());
            }
            
        } catch (Exception e) {
            System.err.println("\n✗ Erro: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void exibirCabecalho() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║   Sinnc Saúde — Gerador de Testes   ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println();
    }

    private static DescricaoTeste coletarInformacoes() {
        DescricaoTeste descricao = new DescricaoTeste();

        // [1] Módulo
        System.out.println("[1] Módulo:");
        for (int i = 0; i < MODULOS.length; i++) {
            System.out.println("    " + (i + 1) + ". " + MODULOS[i]);
        }
        System.out.print("> escolha: ");
        int opcaoModulo = lerInteiro(1, MODULOS.length);
        descricao.setModulo(MODULOS[opcaoModulo - 1]);
        System.out.println();

        // [2] Tipo de teste
        System.out.println("[2] Tipo de teste:");
        System.out.println("    1. smoke     (rápido, roda em todo deploy)");
        System.out.println("    2. regression (suite completa)");
        System.out.println("    3. critico   (fluxos assistenciais críticos)");
        System.out.print("> escolha: ");
        int opcaoTipo = lerInteiro(1, TIPOS_TESTE.length);
        descricao.setTipoTeste(TIPOS_TESTE[opcaoTipo - 1]);
        System.out.println();

        // [3] Prioridade
        System.out.println("[3] Prioridade: Alta / Média / Baixa");
        System.out.println("    1. Alta");
        System.out.println("    2. Média");
        System.out.println("    3. Baixa");
        System.out.print("> escolha: ");
        int opcaoPrioridade = lerInteiro(1, PRIORIDADES.length);
        descricao.setPrioridade(PRIORIDADES[opcaoPrioridade - 1]);
        System.out.println();

        // [4] Requisito
        System.out.println("[4] Descreva o requisito ou regra de negócio:");
        System.out.print("> ");
        descricao.setRequisito(scanner.nextLine().trim());
        System.out.println();

        // [5] Passos
        System.out.println("[5] Passos do fluxo (enter vazio para encerrar):");
        List<String> passos = new ArrayList<>();
        int passoNum = 1;
        while (true) {
            System.out.print("    Passo " + passoNum + ": ");
            String passo = scanner.nextLine().trim();
            if (passo.isEmpty()) {
                break;
            }
            passos.add(passo);
            passoNum++;
        }
        descricao.setPassos(passos);
        System.out.println();

        // [6] Tags
        System.out.println("[6] Tags adicionais (separadas por vírgula, ou enter para pular):");
        System.out.print("> ");
        String tagsInput = scanner.nextLine().trim();
        List<String> tags = new ArrayList<>();
        if (!tagsInput.isEmpty()) {
            for (String tag : tagsInput.split(",")) {
                tags.add(tag.trim());
            }
        }
        descricao.setTags(tags);
        System.out.println();

        return descricao;
    }

    private static int lerInteiro(int min, int max) {
        while (true) {
            try {
                String input = scanner.nextLine().trim();
                int valor = Integer.parseInt(input);
                if (valor >= min && valor <= max) {
                    return valor;
                }
                System.out.print("Opção inválida. Escolha entre " + min + " e " + max + ": ");
            } catch (NumberFormatException e) {
                System.out.print("Entrada inválida. Digite um número: ");
            }
        }
    }

    private static void exibirProgresso() {
        try {
            for (int i = 0; i <= 100; i += 10) {
                System.out.print("\r    " + criarBarraProgresso(i) + " " + i + "%");
                Thread.sleep(200);
            }
            System.out.println();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String criarBarraProgresso(int percentual) {
        int blocos = percentual / 10;
        StringBuilder barra = new StringBuilder("[");
        for (int i = 0; i < 10; i++) {
            barra.append(i < blocos ? "█" : "░");
        }
        barra.append("]");
        return barra.toString();
    }

    private static void salvarArtefatos(ArtefatosTest artefatos, DescricaoTeste descricao) throws IOException {
        String tipoTeste = descricao.getTipoTeste();
        String moduloPath = descricao.getModuloPath();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        
        // Criar diretórios
        Path dirGherkin = Paths.get("output/artefatos", tipoTeste, "gherkin", moduloPath);
        Path dirJava = Paths.get("output/artefatos", tipoTeste, "java", moduloPath);
        Path dirJira = Paths.get("output/artefatos", tipoTeste, "jira", moduloPath);
        Path dirFeatures = Paths.get("src/test/resources/features", tipoTeste, moduloPath);
        
        Files.createDirectories(dirGherkin);
        Files.createDirectories(dirJava);
        Files.createDirectories(dirJira);
        Files.createDirectories(dirFeatures);
        
        // Nome base para os arquivos
        String nomeBase = descricao.getRequisito().toLowerCase()
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_+", "_")
                .substring(0, Math.min(30, descricao.getRequisito().length()));
        
        // Extrair nome da classe Java do código
        String nomeClasse = extrairNomeClasse(artefatos.getJava());
        if (nomeClasse == null) {
            nomeClasse = nomeBase + "Test";
        }
        
        // Salvar Gherkin
        Path arquivoFeature = dirGherkin.resolve(nomeBase + ".feature");
        try (FileWriter writer = new FileWriter(arquivoFeature.toFile())) {
            writer.write(artefatos.getGherkin());
        }
        
        // Salvar Java
        Path arquivoJava = dirJava.resolve(nomeClasse + ".java");
        try (FileWriter writer = new FileWriter(arquivoJava.toFile())) {
            writer.write(artefatos.getJava());
        }
        
        // Salvar Jira
        Path arquivoJira = dirJira.resolve("TC_" + timestamp + ".txt");
        try (FileWriter writer = new FileWriter(arquivoJira.toFile())) {
            writer.write(artefatos.getJira());
        }
        
        // Copiar .feature para src/test/resources
        Path arquivoFeatureSrc = dirFeatures.resolve(nomeBase + ".feature");
        try (FileWriter writer = new FileWriter(arquivoFeatureSrc.toFile())) {
            writer.write(artefatos.getGherkin());
        }
    }

    private static String extrairNomeClasse(String codigoJava) {
        if (codigoJava == null) return null;
        
        // Procura por "public class NomeClasse"
        int idxClass = codigoJava.indexOf("public class ");
        if (idxClass >= 0) {
            int inicio = idxClass + "public class ".length();
            int fim = codigoJava.indexOf(" ", inicio);
            if (fim < 0) {
                fim = codigoJava.indexOf("{", inicio);
            }
            if (fim > inicio) {
                return codigoJava.substring(inicio, fim).trim();
            }
        }
        return null;
    }

    private static void executarTeste(String tipoTeste) {
        System.out.println("\nRodando... mvn test -Dcucumber.filter.tags=@" + tipoTeste);
        
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "mvn", "test", "-Dcucumber.filter.tags=@" + tipoTeste
            );
            pb.inheritIO();
            Process process = pb.start();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            System.err.println("Erro ao executar teste: " + e.getMessage());
        }
    }
}

package br.com.sinncosaude.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.javalin.Javalin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Servidor web para interface de geração de testes.
 */
public class GuiServer {

    private static final Gson gson = new Gson();
    private static final int PORT = 8080;
    private static final Path DATA_DIR = Paths.get("data");
    private static final Path PROJECT_DIR = Paths.get(".").toAbsolutePath().normalize();
    private static final String MVN_CMD = "C:\\ProgramData\\chocolatey\\lib\\maven\\apache-maven-3.9.15\\bin\\mvn.cmd";

    public static void main(String[] args) {
        // Create data directory for persistence
        try {
            if (!Files.exists(DATA_DIR)) {
                Files.createDirectories(DATA_DIR);
                System.out.println("Diretório de dados criado: " + DATA_DIR.toAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("Erro ao criar diretório de dados: " + e.getMessage());
        }

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("gui");
        }).start(PORT);

        // Add global CORS headers
        app.before(ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        });

        // Handle OPTIONS requests for CORS
        app.options("/*", ctx -> {
            ctx.status(200);
        });

        System.out.println("=================================================");
        System.out.println("  Sinnc QA Agent - GUI Server");
        System.out.println("=================================================");
        System.out.println("  Acesse: http://localhost:" + PORT);
        System.out.println("=================================================");

        // API: Generate test with AI
        app.post("/api/generate-test", ctx -> {
            try {
                JsonObject request = gson.fromJson(ctx.body(), JsonObject.class);
                
                String module = request.get("module").getAsString();
                String menu = request.get("menu").getAsString();
                String testType = request.get("testType").getAsString();
                String priority = request.get("priority").getAsString();
                String testName = request.get("testName").getAsString();
                String description = request.get("description").getAsString();
                String testData = request.has("testData") ? request.get("testData").getAsString() : null;

                // Call the CLI GerarTeste via process
                String jsonInput = gson.toJson(request);
                Path tempFile = Files.createTempFile("test-request", ".json");
                Files.writeString(tempFile, jsonInput, java.nio.charset.StandardCharsets.UTF_8);

                ProcessBuilder pb = new ProcessBuilder(
                    "C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.17.10-hotspot\\bin\\java.exe",
                    "-cp",
                    "target\\classes;target\\test-classes",
                    "br.com.sinncosaude.cli.GerarTeste",
                    tempFile.toString()
                );
                pb.directory(PROJECT_DIR.toFile());
                Process process = pb.start();
                
                StringBuilder output = new StringBuilder();
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }
                
                process.waitFor();
                Files.deleteIfExists(tempFile);

                // Parse the output (simplified - just return mock for now)
                JsonObject response = new JsonObject();
                response.addProperty("feature", generateMockFeature(module, menu, testName, description));
                response.addProperty("java", generateMockJava(module, menu, testName));
                response.addProperty("jira", description);

                ctx.result(gson.toJson(response));
            } catch (Exception e) {
                ctx.status(500).result("Erro ao gerar teste: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: Apply test to project
        app.post("/api/apply-test", ctx -> {
            try {
                JsonObject request = gson.fromJson(ctx.body(), JsonObject.class);
                
                String module = request.get("module").getAsString();
                String menu = request.get("menu").getAsString();
                String testType = request.get("testType").getAsString();
                String testName = request.get("testName").getAsString();
                String featureContent = request.get("feature").getAsString();
                String javaContent = request.get("java").getAsString();

                // Convert menu key to folder name
                String menuFolder = menu.toLowerCase().replace(" ", "_");
                String moduleFolder = module.toLowerCase().replace(" ", "_");

                // Create feature file
                Path featurePath = Paths.get("src/test/resources/features", testType, moduleFolder, menuFolder + ".feature");
                Files.createDirectories(featurePath.getParent());
                Files.writeString(featurePath, featureContent, java.nio.charset.StandardCharsets.UTF_8);

                // Create Java test file
                String className = testName.replaceAll("[^a-zA-Z0-9]", "") + "Test";
                Path javaPath = Paths.get("src/test/java/br/com/sinncosaude/pages", moduleFolder, className + ".java");
                Files.createDirectories(javaPath.getParent());
                Files.writeString(javaPath, javaContent, java.nio.charset.StandardCharsets.UTF_8);

                JsonObject response = new JsonObject();
                response.addProperty("status", "success");
                response.addProperty("featurePath", featurePath.toString());
                response.addProperty("javaPath", javaPath.toString());

                ctx.result(gson.toJson(response));
            } catch (Exception e) {
                ctx.status(500).result("Erro ao aplicar teste: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: Save test metadata before execution
        app.post("/api/save-test-metadata", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Methods", "POST, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type");
            
            try {
                String body = ctx.body();
                JsonObject metadata = gson.fromJson(body, JsonObject.class);
                
                // Save metadata to file for Java tests to read (use absolute path)
                Path metadataFile = DATA_DIR.toAbsolutePath().resolve("current_test_metadata.json");
                Files.createDirectories(metadataFile.getParent());
                Files.write(metadataFile, gson.toJson(metadata).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                
                System.out.println("[GuiServer] Metadata saved to: " + metadataFile);
                System.out.println("[GuiServer] Metadata content: " + gson.toJson(metadata));
                
                JsonObject response = new JsonObject();
                response.addProperty("status", "saved");
                response.addProperty("path", metadataFile.toString());
                ctx.result(gson.toJson(response));
            } catch (Exception e) {
                ctx.status(500).result("Erro ao salvar metadados: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: Run test
        app.post("/api/run-test/{testId}", ctx -> {
            // Add CORS headers
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type, Authorization");

            try {
                String testId = ctx.pathParam("testId");

                // Read test metadata
                Path metadataFile = DATA_DIR.toAbsolutePath().resolve("current_test_metadata.json");
                String testName = "";
                String module = "";
                String menu = "";
                String description = "";
                String testClassName = "CadastroPacienteTest"; // fallback

                if (Files.exists(metadataFile)) {
                    String metadataContent = Files.readString(metadataFile, java.nio.charset.StandardCharsets.UTF_8);
                    JsonObject metadata = gson.fromJson(metadataContent, JsonObject.class);

                    if (metadata.has("name")) {
                        testName = metadata.get("name").getAsString();
                        testClassName = generateTestClassName(testName);
                    }
                    if (metadata.has("module")) module = metadata.get("module").getAsString();
                    if (metadata.has("menu")) menu = metadata.get("menu").getAsString();
                    if (metadata.has("description")) description = metadata.get("description").getAsString();
                }

                // Check if test class exists, if not generate it automatically
                String moduleFolder = module.toLowerCase().replace(" ", "_");
                Path javaPath = Paths.get("src/test/java/br/com/sinncosaude/pages", moduleFolder, testClassName + ".java");

                if (!Files.exists(javaPath)) {
                    System.out.println("[GuiServer] Test class not found, generating: " + javaPath);

                    // Generate test class code
                    String javaContent = generateMockJava(module, menu, testName);

                    // Create directory if needed
                    Files.createDirectories(javaPath.getParent());

                    // Write test file
                    Files.writeString(javaPath, javaContent, java.nio.charset.StandardCharsets.UTF_8);
                    System.out.println("[GuiServer] Generated test class at: " + javaPath.toAbsolutePath());
                }

                // First compile test classes, then run the test synchronously
                // Step 1: Compile
                ProcessBuilder compilePb = new ProcessBuilder(
                    MVN_CMD,
                    "test-compile",
                    "-o",
                    "-q"
                );
                compilePb.directory(PROJECT_DIR.toFile());
                compilePb.inheritIO();

                System.out.println("[GuiServer] Compiling test classes...");
                Process compileProcess = compilePb.start();
                int compileExit = compileProcess.waitFor();

                if (compileExit != 0) {
                    JsonObject response = new JsonObject();
                    response.addProperty("status", "error");
                    response.addProperty("testId", testId);
                    response.addProperty("testClass", testClassName);
                    response.addProperty("message", "Erro na compilação. Verifique o console do servidor.");
                    ctx.result(gson.toJson(response));
                    return;
                }

                // Step 2: Run the test (in background so browser doesn't hang)
                ProcessBuilder testPb = new ProcessBuilder(
                    MVN_CMD,
                    "test",
                    "-Dtest=" + testClassName,
                    "-o",
                    "-q",
                    "-DskipTests=false"
                );
                testPb.directory(PROJECT_DIR.toFile());
                testPb.inheritIO();

                System.out.println("[GuiServer] Starting test: " + testClassName);
                testPb.start();

                JsonObject response = new JsonObject();
                response.addProperty("status", "started");
                response.addProperty("testId", testId);
                response.addProperty("testClass", testClassName);
                response.addProperty("compiled", true);
                response.addProperty("message", "Teste " + testClassName + " compilado e iniciado. O Playwright será aberto automaticamente. Verifique o console do servidor.");

                ctx.result(gson.toJson(response));
            } catch (Exception e) {
                ctx.status(500).result("Erro ao executar teste: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: Run all tests
        app.post("/api/run-all-tests", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    MVN_CMD,
                    "test",
                    "-o", // Offline mode
                    "-q"  // Quiet
                );
                pb.directory(PROJECT_DIR.toFile());
                pb.inheritIO();
                pb.start(); // Start in background

                JsonObject response = new JsonObject();
                response.addProperty("status", "started");
                response.addProperty("message", "Todos os testes iniciados em background. Verifique o console do servidor.");

                ctx.result(gson.toJson(response));
            } catch (Exception e) {
                ctx.status(500).result("Erro ao executar testes: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: Run menu tests
        app.post("/api/run-menu/{moduleKey}/{menuKey}", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");

            try {
                String moduleKey = ctx.pathParam("moduleKey");
                String menuKey = ctx.pathParam("menuKey");

                // Execute all tests in the module package (wildcard)
                String packagePattern = "br.com.sinncosaude.pages." + moduleKey.toLowerCase().replace(" ", "_") + ".*Test";

                ProcessBuilder pb = new ProcessBuilder(
                    MVN_CMD,
                    "test",
                    "-Dtest=" + packagePattern,
                    "-o", // Offline mode
                    "-q"  // Quiet
                );
                pb.directory(PROJECT_DIR.toFile());
                pb.inheritIO();
                pb.start(); // Start in background

                JsonObject response = new JsonObject();
                response.addProperty("status", "started");
                response.addProperty("module", moduleKey);
                response.addProperty("menu", menuKey);
                response.addProperty("testPattern", packagePattern);
                response.addProperty("message", "Testes do menu " + menuKey + " iniciados em background.");

                ctx.result(gson.toJson(response));
            } catch (Exception e) {
                ctx.status(500).result("Erro ao executar testes do menu: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: Run module tests
        app.post("/api/run-module/{moduleKey}", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");

            try {
                String moduleKey = ctx.pathParam("moduleKey");

                // Execute all tests in the module package (wildcard)
                String packagePattern = "br.com.sinncosaude.pages." + moduleKey.toLowerCase().replace(" ", "_") + ".*Test";

                ProcessBuilder pb = new ProcessBuilder(
                    MVN_CMD,
                    "test",
                    "-Dtest=" + packagePattern,
                    "-o", // Offline mode
                    "-q"  // Quiet
                );
                pb.directory(PROJECT_DIR.toFile());
                pb.inheritIO();
                pb.start(); // Start in background

                JsonObject response = new JsonObject();
                response.addProperty("status", "started");
                response.addProperty("module", moduleKey);
                response.addProperty("testPattern", packagePattern);
                response.addProperty("message", "Testes do módulo " + moduleKey + " iniciados em background.");

                ctx.result(gson.toJson(response));
            } catch (Exception e) {
                ctx.status(500).result("Erro ao executar testes do módulo: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // Serve reports
        app.get("/reports", ctx -> {
            Path reportPath = Paths.get("output/reports/relatorio.html");
            if (Files.exists(reportPath)) {
                ctx.html(Files.readString(reportPath, java.nio.charset.StandardCharsets.UTF_8));
            } else {
                ctx.result("Relatório não encontrado. Execute os testes primeiro.");
            }
        });

        // API: Save localStorage data to disk
        app.post("/api/save-data", ctx -> {
            try {
                JsonObject request = gson.fromJson(ctx.body(), JsonObject.class);
                String key = request.get("key").getAsString();
                String value = request.get("value").getAsString();

                Path dataFile = DATA_DIR.resolve(key + ".json");
                Files.writeString(dataFile, value, java.nio.charset.StandardCharsets.UTF_8);

                JsonObject response = new JsonObject();
                response.addProperty("status", "success");
                response.addProperty("path", dataFile.toString());

                ctx.result(gson.toJson(response));
            } catch (Exception e) {
                ctx.status(500).result("Erro ao salvar dados: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: Load localStorage data from disk
        app.get("/api/load-data/{key}", ctx -> {
            try {
                String key = ctx.pathParam("key");
                Path dataFile = DATA_DIR.resolve(key + ".json");

                if (Files.exists(dataFile)) {
                    String value = Files.readString(dataFile, java.nio.charset.StandardCharsets.UTF_8);
                    ctx.result(value);
                } else {
                    ctx.status(404).result("Dados não encontrados");
                }
            } catch (Exception e) {
                ctx.status(500).result("Erro ao carregar dados: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: List all saved data keys
        app.get("/api/list-data", ctx -> {
            try {
                JsonObject response = new JsonObject();
                if (Files.exists(DATA_DIR)) {
                    Files.list(DATA_DIR)
                        .filter(path -> path.toString().endsWith(".json"))
                        .forEach(path -> {
                            String key = path.getFileName().toString().replace(".json", "");
                            response.addProperty(key, path.toString());
                        });
                }
                ctx.result(gson.toJson(response));
            } catch (Exception e) {
                ctx.status(500).result("Erro ao listar dados: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: Get server configuration
        app.get("/api/config", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                JsonObject config = new JsonObject();
                config.addProperty("status", "ok");
                config.addProperty("version", "1.0.0");
                config.addProperty("serverPort", PORT);
                config.addProperty("projectDir", PROJECT_DIR.toString());
                config.addProperty("dataDir", DATA_DIR.toString());
                ctx.result(gson.toJson(config));
            } catch (Exception e) {
                ctx.status(500).result("Erro ao carregar configurações: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private static String generateMockFeature(String module, String menu, String testName, String description) {
        return String.format("""
            Feature: %s
              Como usuário do sistema %s
              Quero realizar a operação de %s
              Para validar o funcionamento do sistema

              Scenario: %s
                Given estou logado no sistema
                And navego para %s > %s
                When realizo as ações necessárias
                Then o sistema deve exibir o resultado esperado
            """, testName, module, menu, testName, module, menu);
    }

    private static String generateMockJava(String module, String menu, String testName) {
        String className = generateTestClassName(testName);
        String packageName = "br.com.sinncosaude.pages." + module.toLowerCase().replace(" ", "_");
        // Sanitize method name - remove spaces and special chars, capitalize words
        String methodName = testName.replaceAll("[^a-zA-Z0-9\\s]", "").trim();
        methodName = Arrays.stream(methodName.split("\\s+"))
            .filter(s -> !s.isEmpty())
            .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase())
            .collect(Collectors.joining());

        return String.format("""
            package %s;

            import br.com.sinncosaude.config.BaseTest;
            import org.junit.jupiter.api.Tag;
            import org.junit.jupiter.api.Test;

            public class %s extends BaseTest {

              @Override
              protected String getTipoTeste() {
                return "smoke";
              }

              @Test
              @Tag("smoke")
              public void test%s() {
                // Implementar teste para %s
                System.out.println("Executando teste: %s");
              }
            }
            """, packageName, className, methodName, menu, testName);
    }

    /**
     * Gera o nome da classe de teste a partir do nome do teste.
     * Remove caracteres especiais e adiciona sufixo "Test".
     */
    private static String generateTestClassName(String testName) {
        if (testName == null || testName.isEmpty()) {
            return "CadastroPacienteTest";
        }
        // Remove acentos e caracteres especiais, mantém apenas letras e números
        String normalized = testName
            .replaceAll("[^a-zA-Z0-9\\s]", "") // Remove caracteres especiais
            .trim()
            .replaceAll("\\s+", ""); // Remove espaços
        return normalized + "Test";
    }
}

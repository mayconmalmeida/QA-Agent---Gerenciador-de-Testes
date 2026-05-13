package br.com.qasuite.server;

import br.com.qasuite.ai.IntentParserService;
import br.com.qasuite.config.ExecutionConfig;
import br.com.qasuite.domain.TestPlan;
import br.com.qasuite.memory.BehaviorType;
import br.com.qasuite.memory.ComponentMemoryEngine;
import br.com.qasuite.memory.ComponentMemoryEntry;
import br.com.qasuite.memory.ComponentType;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Servidor web para interface de geração de testes.
 */
public class GuiServer {

    private static final Gson gson = new Gson();
    private static final int START_PORT = 8081;
    private static final int MAX_PORT = 8091;
    private static int currentPort = START_PORT;
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

        // Tenta iniciar em portas de 8081 a 8091
        Javalin app = null;
        for (int port = START_PORT; port <= MAX_PORT; port++) {
            try {
                app = Javalin.create(config -> {
                    config.staticFiles.add("gui");
                    config.showJavalinBanner = false;
                }).start(port);
                currentPort = port;
                // Salva porta usada para o frontend
                Files.writeString(DATA_DIR.resolve("server.port"), String.valueOf(port));
                System.out.println("Servidor iniciado na porta: " + port);
                break;
            } catch (Exception e) {
                System.out.println("Porta " + port + " em uso, tentando próxima...");
                if (port == MAX_PORT) {
                    throw new RuntimeException("Nenhuma porta disponível de " + START_PORT + " a " + MAX_PORT);
                }
            }
        }

        // Add global CORS and anti-cache headers
        app.before(ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
            // Anti-cache headers
            ctx.header("Cache-Control", "no-cache, no-store, must-revalidate");
            ctx.header("Pragma", "no-cache");
            ctx.header("Expires", "0");
        });

        // Handle OPTIONS requests for CORS
        app.options("/*", ctx -> {
            ctx.status(200);
        });

        // WebSocket: Real-time execution updates
        app.ws("/ws/execution/{executionId}", ExecutionWebSocket::configure);

        System.out.println("=================================================");
        System.out.println("  QA Agent - GUI Server");
        System.out.println("=================================================");
        System.out.println("  Acesse: http://localhost:" + currentPort);
        System.out.println("=================================================");

        Context7CliService context7Cli = new Context7CliService();
        ComponentMemoryEngine componentMemoryEngine = new ComponentMemoryEngine();
        ExecutionLearningStore learningStore = new ExecutionLearningStore();

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
                    "br.com.qasuite.cli.GerarTeste",
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
                Path javaPath = Paths.get("src/test/java/br/com/qasuite/pages", moduleFolder, className + ".java");
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
                DatabaseManager db = new DatabaseManager();
                Map<String, Object> testData = db.loadTest(testId);
                if (testData == null) {
                    ctx.status(404);
                    java.util.Map<String, String> response = new java.util.HashMap<>();
                    response.put("status", "error");
                    response.put("message", "Teste nao encontrado: " + testId);
                    ctx.json(response);
                    return;
                }

                String testName = String.valueOf(testData.getOrDefault("name", "GenericTest"));
                String testClassName = generateTestClassName(testName);
                System.out.println("[GuiServer] Executando teste solicitado pelo ID: " + testId + " - " + testName);
                saveMetadataAndExecute(testData);

                java.util.Map<String, String> response = new java.util.HashMap<>();
                response.put("status", "started");
                response.put("testId", testId);
                response.put("testClass", testClassName);
                response.put("message", "Teste iniciado em background. Acompanhe no console do servidor.");
                ctx.json(response);
            } catch (Exception e) {
                java.util.Map<String, String> response = new java.util.HashMap<>();
                response.put("status", "error");
                response.put("message", "Erro ao executar teste: " + e.getMessage());
                ctx.json(response);
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

                DatabaseManager db = new DatabaseManager();
                List<Map<String, Object>> tests = db.loadTests().stream()
                    .filter(test -> moduleKey.equalsIgnoreCase(String.valueOf(test.get("module"))))
                    .filter(test -> menuKey.equalsIgnoreCase(String.valueOf(test.get("menu"))))
                    .collect(Collectors.toList());

                if (tests.isEmpty()) {
                    ctx.status(404).result("Nenhum teste encontrado para o menu " + menuKey);
                    return;
                }

                saveMetadataAndExecute(tests.get(0));

                JsonObject response = new JsonObject();
                response.addProperty("status", "started");
                response.addProperty("module", moduleKey);
                response.addProperty("menu", menuKey);
                response.addProperty("testId", String.valueOf(tests.get(0).get("id")));
                response.addProperty("testName", String.valueOf(tests.get(0).get("name")));
                response.addProperty("message", "Teste do menu " + menuKey + " iniciado em background.");

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

                DatabaseManager db = new DatabaseManager();
                List<Map<String, Object>> tests = db.loadTests().stream()
                    .filter(test -> moduleKey.equalsIgnoreCase(String.valueOf(test.get("module"))))
                    .collect(Collectors.toList());

                if (tests.isEmpty()) {
                    ctx.status(404).result("Nenhum teste encontrado para o modulo " + moduleKey);
                    return;
                }

                if (tests.size() != 1) {
                    ctx.status(409).result("Modulo possui " + tests.size() + " testes. Execute pelo botao do teste ou do submenu para evitar executar o fluxo errado.");
                    return;
                }

                saveMetadataAndExecute(tests.get(0));

                JsonObject response = new JsonObject();
                response.addProperty("status", "started");
                response.addProperty("module", moduleKey);
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
            try {
                if (Files.exists(reportPath)) {
                    byte[] bytes = Files.readAllBytes(reportPath);
                    String html = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    if (html.contains("\uFFFD")) {
                        html = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                    }
                    ctx.contentType("text/html; charset=utf-8").result(html);
                    return;
                }

                Path reportsDir = Paths.get("output/reports");
                if (Files.exists(reportsDir)) {
                    try (var stream = Files.list(reportsDir)) {
                        var latest = stream
                            .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".html"))
                            .sorted((a, b) -> {
                                try {
                                    return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                                } catch (Exception e) {
                                    return 0;
                                }
                            })
                            .findFirst()
                            .orElse(null);
                        if (latest != null) {
                            byte[] bytes = Files.readAllBytes(latest);
                            String html = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                            if (html.contains("\uFFFD")) {
                                html = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                            }
                            ctx.contentType("text/html; charset=utf-8").result(html);
                            return;
                        }
                    }
                }

                ctx.status(404).result("Relatório não encontrado. Execute os testes primeiro.");
            } catch (Exception e) {
                ctx.status(500).result("Erro ao carregar relatório: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // Serve timeline report
        app.get("/timeline-report", ctx -> {
            Path reportPath = Paths.get("output/reports/timeline_report.html");
            try {
                if (Files.exists(reportPath)) {
                    byte[] bytes = Files.readAllBytes(reportPath);
                    String html = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    if (html.contains("\uFFFD")) {
                        html = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
                    }
                    ctx.contentType("text/html; charset=utf-8").result(html);
                    return;
                }
                ctx.status(404).result("Relatório não encontrado. Execute os testes primeiro.");
            } catch (Exception e) {
                ctx.status(500).result("Erro ao carregar relatório: " + e.getMessage());
                e.printStackTrace();
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

        // API: SQLite - Load all tests
        app.get("/api/load-tests", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                DatabaseManager db = new DatabaseManager();
                var tests = db.loadTests();
                ctx.json(tests);
            } catch (Exception e) {
                ctx.status(500).result("Erro ao carregar testes: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: SQLite - Save test
        app.post("/api/save-test", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                String body = ctx.body();
                var test = gson.fromJson(body, java.util.Map.class);
                System.out.println("[GuiServer] Salvando teste ID: " + test.get("id"));
                System.out.println("[GuiServer] Nome: " + test.get("name"));
                System.out.println("[GuiServer] Descricao recebida: " + test.get("description"));
                System.out.println("[GuiServer] testType recebido: " + test.get("testType"));
                DatabaseManager db = new DatabaseManager();
                Object id = test.get("id");
                if (id != null) {
                    var existing = db.loadTest(String.valueOf(id));
                    if (existing != null) {
                        if (isBlank(test.get("module"))) {
                            test.put("module", existing.get("module"));
                        }
                        if (isBlank(test.get("menu"))) {
                            test.put("menu", existing.get("menu"));
                        }
                        if (isBlank(test.get("createdAt"))) {
                            test.put("createdAt", existing.get("created_at"));
                        }
                    }
                }
                db.saveTest(test);
                java.util.Map<String, String> response = new java.util.HashMap<>();
                response.put("status", "success");
                ctx.json(response);
            } catch (Exception e) {
                System.err.println("[GuiServer] ERRO ao salvar teste: " + e.getMessage());
                e.printStackTrace();
                ctx.status(500).result("Erro ao salvar teste: " + e.getMessage());
            }
        });

        // API: SQLite - Delete test
        app.post("/api/delete-test", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                var request = gson.fromJson(ctx.body(), java.util.Map.class);
                String testId = (String) request.get("id");
                DatabaseManager db = new DatabaseManager();
                db.deleteTest(testId);
                java.util.Map<String, String> response = new java.util.HashMap<>();
                response.put("status", "success");
                ctx.json(response);
            } catch (Exception e) {
                ctx.status(500).result("Erro ao deletar teste: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: SQLite - Load menu structure
        app.get("/api/load-menu-structure", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                DatabaseManager db = new DatabaseManager();
                var structure = db.loadMenuStructure();
                ctx.json(structure);
            } catch (Exception e) {
                ctx.status(500).result("Erro ao carregar estrutura: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: SQLite - Save menu structure
        app.post("/api/save-menu-structure", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                var structure = gson.fromJson(ctx.body(), java.util.Map.class);
                DatabaseManager db = new DatabaseManager();
                db.saveMenuStructure(structure);
                java.util.Map<String, String> response = new java.util.HashMap<>();
                response.put("status", "success");
                ctx.json(response);
            } catch (Exception e) {
                ctx.status(500).result("Erro ao salvar estrutura: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: SQLite - Load config
        app.get("/api/load-config", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                DatabaseManager db = new DatabaseManager();
                var config = db.loadConfig();
                System.out.println("[GuiServer] /api/load-config - Configurações carregadas: " + config.size() + " itens");
                System.out.println("[GuiServer] /api/load-config - Chaves: " + config.keySet());
                ctx.json(config);
            } catch (Exception e) {
                System.err.println("[GuiServer] Erro em /api/load-config: " + e.getMessage());
                ctx.status(500).result("Erro ao carregar config: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: SQLite - Save config
        app.post("/api/save-config", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                var config = gson.fromJson(ctx.body(), java.util.Map.class);
                System.out.println("[GuiServer] /api/save-config - Recebido: " + config.size() + " itens");
                System.out.println("[GuiServer] Chaves recebidas: " + config.keySet());
                DatabaseManager db = new DatabaseManager();
                db.saveConfig(config);
                java.util.Map<String, String> response = new java.util.HashMap<>();
                response.put("status", "success");
                ctx.json(response);
            } catch (Exception e) {
                System.err.println("[GuiServer] Erro em /api/save-config: " + e.getMessage());
                ctx.status(500).result("Erro ao salvar config: " + e.getMessage());
                e.printStackTrace();
            }
        });

        app.get("/api/component-memory", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                String q = ctx.queryParam("q");
                String module = ctx.queryParam("module");
                String screen = ctx.queryParam("screen");
                int limit = 200;
                try {
                    String limitRaw = ctx.queryParam("limit");
                    if (limitRaw != null && !limitRaw.isBlank()) {
                        limit = Integer.parseInt(limitRaw.trim());
                    }
                } catch (Exception ignored) {
                }
                var list = componentMemoryEngine.getRepository().list(q, module, screen, limit);
                ctx.json(list);
            } catch (Exception e) {
                ctx.status(500).result("Erro ao carregar memoria de componentes: " + e.getMessage());
            }
        });

        app.post("/api/component-memory/save", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                JsonObject request = gson.fromJson(ctx.body(), JsonObject.class);
                ComponentMemoryEntry entry = new ComponentMemoryEntry();
                if (request.has("id") && !request.get("id").isJsonNull()) {
                    try {
                        entry.setId(request.get("id").getAsLong());
                    } catch (Exception ignored) {
                    }
                }
                entry.setComponentName(request.has("componentName") && !request.get("componentName").isJsonNull()
                    ? request.get("componentName").getAsString()
                    : null);
                entry.setComponentAlias(request.has("componentAlias") && !request.get("componentAlias").isJsonNull()
                    ? request.get("componentAlias").getAsString()
                    : null);
                entry.setModuleName(request.has("moduleName") && !request.get("moduleName").isJsonNull()
                    ? request.get("moduleName").getAsString()
                    : null);
                entry.setScreenName(request.has("screenName") && !request.get("screenName").isJsonNull()
                    ? request.get("screenName").getAsString()
                    : null);
                entry.setExecutionStrategy(request.has("executionStrategy") && !request.get("executionStrategy").isJsonNull()
                    ? request.get("executionStrategy").getAsString()
                    : null);
                entry.setFallbackStrategy(request.has("fallbackStrategy") && !request.get("fallbackStrategy").isJsonNull()
                    ? request.get("fallbackStrategy").getAsString()
                    : null);
                entry.setNotes(request.has("notes") && !request.get("notes").isJsonNull()
                    ? request.get("notes").getAsString()
                    : null);
                entry.setDescription(request.has("description") && !request.get("description").isJsonNull()
                    ? request.get("description").getAsString()
                    : null);
                entry.setExamples(request.has("examples") && !request.get("examples").isJsonNull()
                    ? request.get("examples").getAsString()
                    : null);
                entry.setSemanticTags(request.has("semanticTags") && !request.get("semanticTags").isJsonNull()
                    ? request.get("semanticTags").getAsString()
                    : null);
                entry.setEmbeddingText(request.has("embeddingText") && !request.get("embeddingText").isJsonNull()
                    ? request.get("embeddingText").getAsString()
                    : null);
                if (request.has("ragEnabled") && !request.get("ragEnabled").isJsonNull()) {
                    try {
                        entry.setRagEnabled(request.get("ragEnabled").getAsBoolean());
                    } catch (Exception ignored) {
                    }
                }
                if (request.has("confidenceScore") && !request.get("confidenceScore").isJsonNull()) {
                    try {
                        entry.setConfidenceScore(request.get("confidenceScore").getAsDouble());
                    } catch (Exception ignored) {
                    }
                }

                if (request.has("componentType") && !request.get("componentType").isJsonNull()) {
                    try {
                        entry.setComponentType(ComponentType.valueOf(request.get("componentType").getAsString()));
                    } catch (Exception ignored) {
                    }
                }
                if (request.has("behaviorType") && !request.get("behaviorType").isJsonNull()) {
                    try {
                        entry.setBehaviorType(BehaviorType.valueOf(request.get("behaviorType").getAsString()));
                    } catch (Exception ignored) {
                    }
                }

                boolean learnedFromUser = request.has("learnedFromUser") && !request.get("learnedFromUser").isJsonNull()
                    && request.get("learnedFromUser").getAsBoolean();
                entry.setLearnedFromUser(learnedFromUser);

                if (entry.getExecutionStrategy() != null && !entry.getExecutionStrategy().isBlank()
                    && !ComponentMemoryEngine.isValidStrategyJson(entry.getExecutionStrategy())) {
                    ctx.status(400).result("executionStrategy JSON inválido");
                    return;
                }
                if (entry.getFallbackStrategy() != null && !entry.getFallbackStrategy().isBlank()
                    && !ComponentMemoryEngine.isValidStrategyJson(entry.getFallbackStrategy())) {
                    ctx.status(400).result("fallbackStrategy JSON inválido");
                    return;
                }

                ComponentMemoryEntry saved = learnedFromUser
                    ? componentMemoryEngine.saveLearnedFromUser(entry)
                    : componentMemoryEngine.getRepository().upsert(entry);
                ctx.json(saved);
            } catch (Exception e) {
                ctx.status(500).result("Erro ao salvar memoria de componentes: " + e.getMessage());
            }
        });

        app.get("/api/rag/logs", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                String executionId = ctx.queryParam("executionId");
                String component = ctx.queryParam("component");
                int limit = 50;
                try {
                    String limitRaw = ctx.queryParam("limit");
                    if (limitRaw != null && !limitRaw.isBlank()) {
                        limit = Integer.parseInt(limitRaw.trim());
                    }
                } catch (Exception ignored) {
                }
                var list = componentMemoryEngine.getRepository().listRagQueryLogs(executionId, component, limit);
                ctx.json(list);
            } catch (Exception e) {
                ctx.status(500).result("Erro ao carregar logs RAG: " + e.getMessage());
            }
        });

        app.post("/api/component-memory/delete", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                JsonObject request = gson.fromJson(ctx.body(), JsonObject.class);
                long id = request.get("id").getAsLong();
                boolean ok = componentMemoryEngine.getRepository().deleteById(id);
                JsonObject response = new JsonObject();
                response.addProperty("status", ok ? "success" : "not_found");
                ctx.result(gson.toJson(response));
            } catch (Exception e) {
                ctx.status(500).result("Erro ao deletar memoria de componentes: " + e.getMessage());
            }
        });

        app.get("/api/component-memory/stats", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                var list = componentMemoryEngine.getRepository().list(null, null, null, 500);
                int total = list.size();
                long learned = list.stream().filter(ComponentMemoryEntry::isLearnedFromUser).count();
                long withStrategy = list.stream().filter(e -> e.getExecutionStrategy() != null && !e.getExecutionStrategy().isBlank()).count();
                double avgRate = list.stream().mapToDouble(ComponentMemoryEntry::getSuccessRate).average().orElse(0.0);

                JsonObject response = new JsonObject();
                response.addProperty("total", total);
                response.addProperty("learnedFromUser", learned);
                response.addProperty("withStrategy", withStrategy);
                response.addProperty("avgSuccessRate", (int) Math.round(avgRate));

                var rag = componentMemoryEngine.getRepository().getRagStats();
                response.addProperty("ragEnabledCount", rag.getRagEnabledCount());
                response.addProperty("usedAsContextCount", rag.getUsedAsContextCount());
                response.addProperty("avgConfidence", (int) Math.round(rag.getAvgConfidence() * 100.0));
                response.addProperty("ragQueriesToday", rag.getRagQueriesToday());
                response.addProperty("ragUsedInPromptToday", rag.getRagUsedInPromptToday());
                response.addProperty("ragSuccessWithContextToday", rag.getRagSuccessWithContextToday());
                response.addProperty("ragHitRateToday", rag.getRagHitRateToday());
                response.addProperty("ragDistinctComponentsToday", rag.getRagDistinctComponentsToday());
                ctx.result(gson.toJson(response));
            } catch (Exception e) {
                ctx.status(500).result("Erro ao gerar stats: " + e.getMessage());
            }
        });

        app.post("/api/executions/{executionId}/events", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                String executionId = ctx.pathParam("executionId");
                String raw = ctx.body();
                if (raw == null || raw.isBlank()) {
                    ctx.status(400).result("Body JSON obrigatório");
                    return;
                }
                JsonObject payload = JsonParser.parseString(raw).getAsJsonObject();
                if (!payload.has("type") || payload.get("type").isJsonNull() || payload.get("type").getAsString().isBlank()) {
                    ctx.status(400).result("Campo 'type' obrigatório");
                    return;
                }
                payload.addProperty("executionId", executionId);
                if (!payload.has("timestamp")) {
                    payload.addProperty("timestamp", System.currentTimeMillis());
                }
                ExecutionWebSocket.sendPayload(executionId, payload);
                JsonObject response = new JsonObject();
                response.addProperty("status", "sent");
                ctx.result(gson.toJson(response));
            } catch (Exception e) {
                ctx.status(500).result("Erro ao processar evento: " + e.getMessage());
            }
        });

        app.post("/api/executions/{executionId}/learning/question", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                String executionId = ctx.pathParam("executionId");
                Map<String, Object> request = gson.fromJson(ctx.body(), Map.class);
                Object qIdObj = request != null ? request.get("questionId") : null;
                String questionId = qIdObj != null ? String.valueOf(qIdObj) : null;
                if (questionId == null || questionId.isBlank()) {
                    ctx.status(400).result("questionId obrigatório");
                    return;
                }
                learningStore.putQuestion(executionId, questionId, request);
                ExecutionWebSocket.sendCustom(executionId, "learning_question", request);
                JsonObject response = new JsonObject();
                response.addProperty("status", "queued");
                response.addProperty("executionId", executionId);
                response.addProperty("questionId", questionId);
                ctx.result(gson.toJson(response));
            } catch (Exception e) {
                ctx.status(500).result("Erro ao registrar pergunta: " + e.getMessage());
            }
        });

        app.post("/api/executions/{executionId}/learning/answer", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                String executionId = ctx.pathParam("executionId");
                Map<String, Object> request = gson.fromJson(ctx.body(), Map.class);
                Object qIdObj = request != null ? request.get("questionId") : null;
                String questionId = qIdObj != null ? String.valueOf(qIdObj) : null;
                if (questionId == null || questionId.isBlank()) {
                    ctx.status(400).result("questionId obrigatório");
                    return;
                }
                learningStore.putAnswer(executionId, questionId, request);
                learningStore.clearQuestion(executionId, questionId);
                JsonObject response = new JsonObject();
                response.addProperty("status", "received");
                response.addProperty("executionId", executionId);
                response.addProperty("questionId", questionId);
                ctx.result(gson.toJson(response));
            } catch (Exception e) {
                ctx.status(500).result("Erro ao registrar resposta: " + e.getMessage());
            }
        });

        app.get("/api/executions/{executionId}/learning/answer/{questionId}", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                String executionId = ctx.pathParam("executionId");
                String questionId = ctx.pathParam("questionId");
                int waitMs = 0;
                try {
                    String waitRaw = ctx.queryParam("waitMs");
                    if (waitRaw != null && !waitRaw.isBlank()) {
                        waitMs = Integer.parseInt(waitRaw.trim());
                    }
                } catch (Exception ignored) {
                }
                waitMs = Math.max(0, Math.min(waitMs, 15000));

                long start = System.currentTimeMillis();
                Map<String, Object> answer;
                do {
                    answer = learningStore.consumeAnswer(executionId, questionId);
                    if (answer != null) {
                        ctx.json(answer);
                        return;
                    }
                    if (waitMs <= 0) {
                        break;
                    }
                    Thread.sleep(250);
                } while (System.currentTimeMillis() - start < waitMs);

                ctx.status(404).result("pending");
            } catch (Exception e) {
                ctx.status(500).result("Erro ao buscar resposta: " + e.getMessage());
            }
        });

        // API: OpenAI Config - Get current config (without API key)
        app.get("/api/openai-config", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                br.com.qasuite.config.OpenAIConfig config = new br.com.qasuite.config.OpenAIConfig();
                JsonObject response = new JsonObject();
                response.addProperty("configured", config.isValid());
                response.addProperty("model", config.getModel());
                response.addProperty("apiUrl", config.getApiUrl());
                response.addProperty("temperature", config.getTemperature());
                response.addProperty("maxTokens", config.getMaxTokens());
                // API key is masked for security
                response.addProperty("apiKey", config.isValid() ? "********" + config.getApiKey().substring(Math.max(0, config.getApiKey().length() - 4)) : "");
                ctx.json(response);
            } catch (Exception e) {
                ctx.status(500).result("Erro ao carregar config OpenAI: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: OpenAI Config - Save config
        app.post("/api/openai-config", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                JsonObject request = gson.fromJson(ctx.body(), JsonObject.class);
                br.com.qasuite.config.OpenAIConfig config = new br.com.qasuite.config.OpenAIConfig();

                if (request.has("apiKey") && !request.get("apiKey").getAsString().isEmpty()) {
                    String apiKey = request.get("apiKey").getAsString();
                    // Only update if not masked
                    if (!apiKey.contains("********")) {
                        config.setApiKey(apiKey);
                    }
                }
                if (request.has("model")) {
                    config.setModel(request.get("model").getAsString());
                }
                if (request.has("apiUrl")) {
                    config.setApiUrl(request.get("apiUrl").getAsString());
                }
                if (request.has("temperature")) {
                    config.setTemperature(request.get("temperature").getAsDouble());
                }
                if (request.has("maxTokens")) {
                    config.setMaxTokens(request.get("maxTokens").getAsInt());
                }

                config.saveConfig();

                JsonObject response = new JsonObject();
                response.addProperty("status", "success");
                response.addProperty("message", "OpenAI configuration saved");
                response.addProperty("configured", config.isValid());
                ctx.json(response);
            } catch (Exception e) {
                ctx.status(500).result("Erro ao salvar config OpenAI: " + e.getMessage());
                e.printStackTrace();
            }
        });

        app.get("/api/context7-config", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.contentType("application/json");
            ctx.result(gson.toJson(context7Cli.status()));
        });

        app.get("/api/context7/library", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            String name = ctx.queryParam("name");
            String query = ctx.queryParam("query");
            if (name == null || name.isBlank() || query == null || query.isBlank()) {
                ctx.status(400).result("Parâmetros obrigatórios: name, query");
                return;
            }
            if (name.length() > 120 || query.length() > 600) {
                ctx.status(400).result("Parâmetros muito longos");
                return;
            }
            ctx.contentType("application/json");
            ctx.result(gson.toJson(context7Cli.library(name, query)));
        });

        app.get("/api/context7/docs", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            String libraryId = ctx.queryParam("libraryId");
            String query = ctx.queryParam("query");
            boolean research = "true".equalsIgnoreCase(ctx.queryParam("research"));
            if (libraryId == null || libraryId.isBlank() || query == null || query.isBlank()) {
                ctx.status(400).result("Parâmetros obrigatórios: libraryId, query");
                return;
            }
            if (libraryId.length() > 160 || query.length() > 1200) {
                ctx.status(400).result("Parâmetros muito longos");
                return;
            }
            ctx.contentType("application/json");
            ctx.result(gson.toJson(context7Cli.docs(libraryId, query, research)));
        });

        // API: Generate TestPlan from natural language description
        app.post("/api/tests/generate", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                JsonObject request = gson.fromJson(ctx.body(), JsonObject.class);
                String description = request.get("description").getAsString();
                String testName = request.has("name") ? request.get("name").getAsString() : "Generated Test";
                String module = request.has("module") ? request.get("module").getAsString() : "default";
                String menu = request.has("menu") ? request.get("menu").getAsString() : "";

                // Create IntentParser - uses config from file/env by default
                IntentParserService parser = new IntentParserService();

                // Check if configured
                if (!parser.isConfigured()) {
                    JsonObject error = new JsonObject();
                    error.addProperty("error", true);
                    error.addProperty("message", parser.getConfigError());
                    error.addProperty("configured", false);
                    ctx.status(400).json(error);
                    return;
                }

                TestPlan plan = parser.parse(description, testName, module, menu);

                // Return as JSON
                ctx.json(gson.toJson(plan));
            } catch (Exception e) {
                ctx.status(500).result("Error generating test: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: Execute test
        app.post("/api/tests/execute", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                JsonObject request = gson.fromJson(ctx.body(), JsonObject.class);
                String testId = request.get("testId").getAsString();

                // Generate unique execution ID
                String executionId = "exec_" + System.currentTimeMillis() + "_" + testId;

                // Load test from database
                DatabaseManager db = new DatabaseManager();
                var testData = db.loadTest(testId);

                if (testData == null) {
                    ctx.status(404).result("Test not found: " + testId);
                    return;
                }

                // Add executionId to test data
                testData.put("executionId", executionId);

                // Save metadata and execute via GenericTest
                saveMetadataAndExecute(testData);

                JsonObject response = new JsonObject();
                response.addProperty("status", "started");
                response.addProperty("testId", testId);
                response.addProperty("executionId", executionId);
                response.addProperty("websocketUrl", "ws://localhost:" + currentPort + "/ws/execution/" + executionId);
                response.addProperty("message", "Test execution started. Connect to WebSocket for real-time updates.");
                ctx.json(response);

            } catch (Exception e) {
                ctx.status(500).result("Error executing test: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: Execute tests in parallel
        app.post("/api/tests/execute-parallel", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                JsonObject request = gson.fromJson(ctx.body(), JsonObject.class);
                JsonArray testIds = request.getAsJsonArray("testIds");
                int maxParallel = request.has("maxParallel") ? request.get("maxParallel").getAsInt() : 3;

                if (testIds == null || testIds.size() == 0) {
                    ctx.status(400).result("No test IDs provided");
                    return;
                }

                // Generate batch execution ID
                String batchId = "batch_" + System.currentTimeMillis();

                // Load all tests
                DatabaseManager db = new DatabaseManager();
                List<br.com.qasuite.domain.TestPlan> testPlans = new ArrayList<>();
                List<String> testIdList = new ArrayList<>();

                for (JsonElement elem : testIds) {
                    String testId = elem.getAsString();
                    var testData = db.loadTest(testId);
                    if (testData != null && testData.containsKey("testData")) {
                        Object testDataValue = testData.get("testData");
                        String testDataJson = testDataValue instanceof String ? (String) testDataValue : gson.toJson(testDataValue);
                        TestPlan plan = tryParseTestPlan(testDataJson, (String) testData.getOrDefault("name", "Test " + testId));
                        if (plan != null) {
                            testPlans.add(plan);
                            testIdList.add(testId);
                        }
                    }
                }

                if (testPlans.isEmpty()) {
                    ctx.status(400).result("No valid test plans found");
                    return;
                }

                // Start parallel execution in background
                CompletableFuture.runAsync(() -> {
                    try {
                        br.com.qasuite.core.ParallelExecutionEngine engine =
                            new br.com.qasuite.core.ParallelExecutionEngine(Math.min(maxParallel, testPlans.size()));

                        // Add tests
                        for (int i = 0; i < testPlans.size(); i++) {
                            engine.addTest(testIdList.get(i), testPlans.get(i), "chromium");
                        }

                        // Execute with progress
                        engine.onProgress(progress -> {
                            System.out.println("[Parallel] " + progress.completed + "/" + progress.total +
                                " - " + progress.testId + ": " + progress.status);
                        });

                        Map<String, br.com.qasuite.core.ParallelExecutionEngine.TestResult> results = engine.executeAll();

                        // Generate summary
                        var summary = engine.generateSummary();
                        System.out.println("[Parallel] Batch " + batchId + " completed: " +
                            summary.passed + " passed, " + summary.failed + " failed, " +
                            summary.partial + " partial");

                        engine.shutdown();

                    } catch (Exception e) {
                        System.err.println("[Parallel] Batch " + batchId + " error: " + e.getMessage());
                    }
                });

                JsonObject response = new JsonObject();
                response.addProperty("status", "started");
                response.addProperty("batchId", batchId);
                response.addProperty("totalTests", testPlans.size());
                response.addProperty("maxParallel", maxParallel);
                response.addProperty("message", "Parallel execution started in background");
                ctx.json(response);

            } catch (Exception e) {
                ctx.status(500).result("Error starting parallel execution: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: Get parallel execution status
        app.get("/api/parallel-execution/{batchId}", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            String batchId = ctx.pathParam("batchId");

            // Return status (in production, would query from a status store)
            JsonObject response = new JsonObject();
            response.addProperty("batchId", batchId);
            response.addProperty("status", "running"); // or completed
            ctx.json(response);
        });

        // API: Execute single test with visual mode option
        app.post("/api/tests/execute-visual", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                JsonObject request = gson.fromJson(ctx.body(), JsonObject.class);
                String testId = request.get("testId").getAsString();
                boolean visualMode = request.has("visualMode") ? request.get("visualMode").getAsBoolean() : false;

                // Load test
                DatabaseManager db = new DatabaseManager();
                var testData = db.loadTest(testId);

                if (testData == null) {
                    ctx.status(404).result("Test not found: " + testId);
                    return;
                }

                // Generate execution ID
                String executionId = "exec_" + System.currentTimeMillis() + "_" + testId;

                // Save metadata with execution config
                testData.put("executionId", executionId);
                testData.put("visualMode", visualMode);

                // Save execution config
                ExecutionConfig execConfig = visualMode ? ExecutionConfig.visualMode() : ExecutionConfig.headlessMode();
                if (request.has("slowMo")) {
                    execConfig.setSlowMo(request.get("slowMo").getAsInt());
                }
                if (request.has("recordVideo")) {
                    execConfig.setRecordVideo(request.get("recordVideo").getAsBoolean());
                }

                // Save config to file for test to read
                Path configFile = DATA_DIR.resolve("execution_config_" + executionId + ".json");
                Files.writeString(configFile, gson.toJson(execConfig));

                // Save metadata and execute
                saveMetadataAndExecute(testData);

                JsonObject response = new JsonObject();
                response.addProperty("status", "started");
                response.addProperty("testId", testId);
                response.addProperty("executionId", executionId);
                response.addProperty("visualMode", visualMode);
                response.addProperty("modeDescription", execConfig.getModeDescription());
                response.addProperty("websocketUrl", "ws://localhost:" + currentPort + "/ws/execution/" + executionId);
                response.addProperty("message", visualMode
                    ? "Test execution started in VISUAL mode. Browser will open for you to follow."
                    : "Test execution started in HEADLESS mode (fast).");
                ctx.json(response);

            } catch (Exception e) {
                ctx.status(500).result("Error executing test: " + e.getMessage());
                e.printStackTrace();
            }
        });

        // API: Get execution config for a test
        app.get("/api/execution-config/{executionId}", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            String executionId = ctx.pathParam("executionId");

            try {
                Path configFile = DATA_DIR.resolve("execution_config_" + executionId + ".json");
                if (Files.exists(configFile)) {
                    String configJson = Files.readString(configFile);
                    ctx.result(configJson);
                } else {
                    // Return default config
                    ctx.json(gson.toJson(ExecutionConfig.headlessMode()));
                }
            } catch (Exception e) {
                ctx.status(500).result("Error loading config: " + e.getMessage());
            }
        });

        // API: Get server configuration
        app.get("/api/config", ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            try {
                JsonObject config = new JsonObject();
                config.addProperty("status", "ok");
                config.addProperty("version", "1.0.0");
                config.addProperty("serverPort", currentPort);
                config.addProperty("projectDir", PROJECT_DIR.toString());
                config.addProperty("dataDir", DATA_DIR.toString());
                config.addProperty("parallelSupported", true);
                config.addProperty("visualModeSupported", true);
                ctx.result(gson.toJson(config));
            } catch (Exception e) {
                ctx.status(500).result("Erro ao carregar configurações: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private static void saveMetadataAndExecute(java.util.Map<String, Object> testData) {
        try {
            String testId = (String) testData.get("id");
            String testName = (String) testData.get("name");
            String module = (String) testData.get("module");
            String menu = (String) testData.get("menu");
            String description = (String) testData.get("description");
            String testType = (String) testData.getOrDefault("testType", "smoke");
            String priority = (String) testData.getOrDefault("priority", "Média");
            String executionId = (String) testData.get("executionId");
            Object visualMode = testData.get("visualMode");

            // Create metadata JSON
            JsonObject metadata = new JsonObject();
            metadata.addProperty("id", testId);
            metadata.addProperty("name", testName);
            metadata.addProperty("module", module);
            metadata.addProperty("menu", menu);
            metadata.addProperty("description", description);
            metadata.addProperty("testType", testType);
            metadata.addProperty("priority", priority);
            if (!isBlank(executionId)) {
                metadata.addProperty("executionId", executionId);
            }
            if (visualMode instanceof Boolean) {
                metadata.addProperty("visualMode", (Boolean) visualMode);
            }

            Object testDataValue = testData.get("testData");
            String testDataJson = testDataValue instanceof String ? (String) testDataValue : (testDataValue != null ? gson.toJson(testDataValue) : null);
            TestPlan plan = tryParseTestPlan(testDataJson, testName);
            if (plan != null) {
                metadata.addProperty("testData", gson.toJson(plan));
            }

            // Save to file
            Path metadataFile = DATA_DIR.resolve("current_test_metadata.json");
            Files.writeString(metadataFile, gson.toJson(metadata));
            System.out.println("[GuiServer] Metadata saved to: " + metadataFile);

            DatabaseManager db = new DatabaseManager();
            db.syncConfigToPropertiesFile();

            // Execute via Maven
            executeMavenTest();

        } catch (Exception e) {
            System.err.println("[GuiServer] Error saving metadata: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static TestPlan tryParseTestPlan(String rawJson, String fallbackName) {
        if (rawJson == null) {
            return null;
        }
        String trimmed = rawJson.trim();
        if (trimmed.isEmpty() || "{}".equals(trimmed) || "null".equalsIgnoreCase(trimmed)) {
            return null;
        }

        try {
            JsonElement element = JsonParser.parseString(trimmed);

            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                String inner = element.getAsString();
                if (inner != null && !inner.trim().isEmpty()) {
                    element = JsonParser.parseString(inner.trim());
                }
            }

            if (element.isJsonArray()) {
                JsonObject wrapper = new JsonObject();
                wrapper.add("steps", element.getAsJsonArray());
                TestPlan plan = gson.fromJson(wrapper, TestPlan.class);
                plan.setName(fallbackName);
                return plan.getTotalSteps() > 0 ? plan : null;
            }

            if (element.isJsonObject()) {
                JsonObject obj = element.getAsJsonObject();

                if (obj.has("testData") && obj.get("testData").isJsonPrimitive() && obj.get("testData").getAsJsonPrimitive().isString()) {
                    String inner = obj.get("testData").getAsString();
                    return tryParseTestPlan(inner, fallbackName);
                }

                if (obj.has("steps") && obj.get("steps").isJsonPrimitive() && obj.get("steps").getAsJsonPrimitive().isString()) {
                    String inner = obj.get("steps").getAsString();
                    JsonElement innerElem = JsonParser.parseString(inner.trim());
                    JsonObject wrapper = new JsonObject();
                    wrapper.add("steps", innerElem.isJsonArray() ? innerElem.getAsJsonArray() : new JsonArray());
                    TestPlan plan = gson.fromJson(wrapper, TestPlan.class);
                    plan.setName(fallbackName);
                    return plan.getTotalSteps() > 0 ? plan : null;
                }

                TestPlan plan = gson.fromJson(obj, TestPlan.class);
                plan.setName(fallbackName);
                return plan.getTotalSteps() > 0 ? plan : null;
            }
        } catch (Exception ignored) {
            return null;
        }

        return null;
    }

    private static void executeMavenTest() {
        try {
            System.out.println("[GuiServer] Starting test execution...");

            ProcessBuilder pb = new ProcessBuilder(
                    MVN_CMD, "test", "-Dtest=GenericTest", "-q"
            );
            pb.directory(PROJECT_DIR.toFile());
            try {
                Path playwrightBrowsersDir = DATA_DIR.toAbsolutePath().resolve("ms-playwright");
                Files.createDirectories(playwrightBrowsersDir);
                pb.environment().put("PLAYWRIGHT_BROWSERS_PATH", playwrightBrowsersDir.toString());
            } catch (Exception ignored) {
            }
            pb.inheritIO();

            Process process = pb.start();
            System.out.println("[GuiServer] Test process started");

        } catch (Exception e) {
            System.err.println("[GuiServer] Error executing test: " + e.getMessage());
        }
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).trim().isEmpty();
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
        String packageName = "br.com.qasuite.pages." + module.toLowerCase().replace(" ", "_");
        // Sanitize method name - remove spaces and special chars, capitalize words
        String methodName = testName.replaceAll("[^a-zA-Z0-9\\s]", "").trim();
        methodName = Arrays.stream(methodName.split("\\s+"))
            .filter(s -> !s.isEmpty())
            .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase())
            .collect(Collectors.joining());

        return String.format("""
            package %s;

            import br.com.qasuite.config.BaseTest;
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

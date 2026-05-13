package br.com.qasuite.core;

import br.com.qasuite.ai.IntentParserService;
import br.com.qasuite.learning.ExecutionEventClient;
import br.com.qasuite.learning.LearningModeClient;
import br.com.qasuite.memory.ComponentMemoryEngine;
import br.com.qasuite.memory.ComponentMemoryEntry;
import br.com.qasuite.memory.BehaviorType;
import br.com.qasuite.memory.ComponentType;
import br.com.qasuite.rag.RagContextResult;
import br.com.qasuite.rag.RagQueryLogEntry;
import br.com.qasuite.rag.SqliteKnowledgeRetriever;
import br.com.qasuite.rag.SystemKnowledgeRagService;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Executor genérico que usa IA para decidir ações baseadas no contexto atual da tela.
 * Captura o DOM visível a cada passo e consulta a IA para decidir como executar.
 */
public class TestExecutor {

    private static final Gson gson = new Gson();
    private final Page page;
    private final SmartActionExecutor actionExecutor;
    private final IntentParserService intentParser;
    private final ComponentMemoryEngine componentMemoryEngine;
    private final SystemKnowledgeRagService ragService;
    private final LearningModeClient learningClient;
    private final ExecutionEventClient eventClient;
    private boolean finalizationPopupArmed = false;
    private final int DEFAULT_TIMEOUT = 8000;  // Reduzido de 15000 para 8000
    private final int MAX_RETRIES = 1;  // Reduzido de 2 para 1
    private boolean consultorioSelecionado = false;

    private Consumer<StepLog> logCallback;
    private String currentModuleKey;
    private String currentMenuKey;
    private String currentExecutionId;
    private String currentTestName;

    /**
     * Log de execução de um passo
     */
    public static class StepLog {
        public final int stepNumber;
        public final String description;
        public final String decision;
        public final String playwrightAction;
        public final boolean success;
        public final long durationMs;
        public final String error;
        public final int retryCount;

        public StepLog(int stepNumber, String description, String decision,
                       String playwrightAction, boolean success, long durationMs,
                       String error, int retryCount) {
            this.stepNumber = stepNumber;
            this.description = description;
            this.decision = decision;
            this.playwrightAction = playwrightAction;
            this.success = success;
            this.durationMs = durationMs;
            this.error = error;
            this.retryCount = retryCount;
        }

        @Override
        public String toString() {
            String icon = success ? "✓" : "✗";
            return String.format("Passo %d: %s %s (%dms)%s",
                stepNumber, icon, description, durationMs,
                error != null ? " - " + error : "");
        }
    }

    /**
     * Resultado da execução do teste
     */
    public static class ExecutionResult {
        private final int totalSteps;
        private final int errorCount;
        private final String lastError;
        private final List<StepLog> stepLogs;

        public ExecutionResult(int totalSteps, int errorCount, String lastError, List<StepLog> stepLogs) {
            this.totalSteps = totalSteps;
            this.errorCount = errorCount;
            this.lastError = lastError;
            this.stepLogs = stepLogs != null ? stepLogs : new ArrayList<>();
        }

        public int getTotalSteps() { return totalSteps; }
        public int getErrorCount() { return errorCount; }
        public String getLastError() { return lastError; }
        public boolean hasErrors() { return errorCount > 0; }
        public List<StepLog> getStepLogs() { return stepLogs; }
    }

    public TestExecutor(Page page) {
        this.page = page;
        this.actionExecutor = new SmartActionExecutor(page, DEFAULT_TIMEOUT);
        this.intentParser = new IntentParserService();
        this.componentMemoryEngine = new ComponentMemoryEngine();
        this.ragService = new SystemKnowledgeRagService(new SqliteKnowledgeRetriever(this.componentMemoryEngine.getRepository()));
        this.learningClient = new LearningModeClient();
        this.eventClient = new ExecutionEventClient();
        loadCurrentTestMetadata();
    }

    private void loadCurrentTestMetadata() {
        try {
            Path metadataFile = Path.of("data/current_test_metadata.json");
            if (!Files.exists(metadataFile)) return;
            String content = Files.readString(metadataFile);
            JsonObject metadata = JsonParser.parseString(content).getAsJsonObject();
            if (metadata.has("module") && !metadata.get("module").isJsonNull()) {
                currentModuleKey = metadata.get("module").getAsString();
            }
            if (metadata.has("menu") && !metadata.get("menu").isJsonNull()) {
                currentMenuKey = metadata.get("menu").getAsString();
            }
            if (metadata.has("executionId") && !metadata.get("executionId").isJsonNull()) {
                currentExecutionId = metadata.get("executionId").getAsString();
            }
            if (metadata.has("name") && !metadata.get("name").isJsonNull()) {
                currentTestName = metadata.get("name").getAsString();
            }
        } catch (Exception ignored) {
        }
    }

    private boolean isEscutaInicialContext() {
        String normalized = normalizeText(currentMenuKey);
        return normalized.equals("escuta_inicial") || normalized.equals("escuta inicial");
    }

    public TestExecutor withLogCallback(Consumer<StepLog> callback) {
        this.logCallback = callback;
        this.actionExecutor.withLogCallback(msg -> {}); // Callback interno já não usado
        return this;
    }

    private void logStep(StepLog log) {
        System.out.println(log.toString());
        if (logCallback != null) {
            logCallback.accept(log);
        }
    }

    /**
     * Executa um teste a partir de uma descrição textual
     */
    public ExecutionResult executar(String descricao) {
        System.out.println("[TestExecutor] Iniciando execução inteligente com IA");
        System.out.println("[TestExecutor] Cada passo consultará a IA com o contexto atual da tela");

        try {
            String[] linhas = descricao.split("\\n");
            boolean jaLogado = true;
            List<String> passos = new ArrayList<>();

            for (int i = 0; i < linhas.length; i++) {
                String linha = linhas[i].trim();
                if (linha.isEmpty()) continue;

                String passoLimpo = linha.replaceFirst("^\\d+[.\\-\\)]\\s*", "").trim();

                if (passoLimpo.toLowerCase().contains("acessar") && passoLimpo.contains("http") && jaLogado) {
                    System.out.println("[TestExecutor] Ignorando navegação - já logado pelo BaseTest");
                    continue;
                }
                if (passoLimpo.toLowerCase().contains("preencher usuário") &&
                    passoLimpo.toLowerCase().contains("senha") && jaLogado) {
                    System.out.println("[TestExecutor] Ignorando login - já realizado pelo BaseTest");
                    continue;
                }

                passos.add(passoLimpo);
            }

            int totalSteps = passos.size();
            int errorCount = 0;
            String lastError = null;
            List<StepLog> stepLogs = new ArrayList<>();

            if (currentExecutionId != null && !currentExecutionId.isBlank()) {
                String testName = currentTestName != null && !currentTestName.isBlank() ? currentTestName : "Execução";
                eventClient.sendStart(currentExecutionId, testName, totalSteps);
            }

            for (int i = 0; i < passos.size(); i++) {
                int stepNumber = i + 1;
                String passo = passos.get(i);
                StepLog log = executarPassoInteligente(stepNumber, passo);
                stepLogs.add(log);

                if (!log.success) {
                    errorCount++;
                    lastError = log.error;
                }

                if (currentExecutionId != null && !currentExecutionId.isBlank()) {
                    eventClient.sendStepProgress(
                        currentExecutionId,
                        stepNumber,
                        totalSteps,
                        parseActionFromDecision(log.decision),
                        log.description,
                        log.success ? "SUCCESS" : "FAILED",
                        log.durationMs,
                        log.error,
                        null
                    );
                }
            }

            System.out.println("[TestExecutor] Teste concluído - " + totalSteps + " passos, " + errorCount + " erro(s)");
            if (currentExecutionId != null && !currentExecutionId.isBlank()) {
                eventClient.sendComplete(currentExecutionId, Math.max(0, totalSteps - errorCount), errorCount, totalSteps);
            }
            return new ExecutionResult(totalSteps, errorCount, lastError, stepLogs);
        } catch (Exception e) {
            if (currentExecutionId != null && !currentExecutionId.isBlank()) {
                eventClient.sendError(currentExecutionId, e.getMessage());
            }
            throw new RuntimeException(e);
        }
    }

    private static String parseActionFromDecision(String decision) {
        if (decision == null) {
            return "UNKNOWN";
        }
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("ActionDecision\\[(\\w+):").matcher(decision);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception ignored) {
        }
        return "UNKNOWN";
    }

    /**
     * Executa um passo consultando a IA com o contexto atual da tela
     */
    private StepLog executarPassoInteligente(int stepNumber, String passo) {
        System.out.println("\n[TestExecutor] === Passo " + stepNumber + ": " + passo + " ===");

        long startTime = System.currentTimeMillis();
        int retryCount = 0;
        ActionDecision lastDecision = null;
        RagContextResult lastRag = null;
        boolean lastDecisionFromLlm = false;

        while (retryCount <= MAX_RETRIES) {
            if (retryCount > 0) {
                System.out.println("[TestExecutor] Retry " + retryCount + "/" + MAX_RETRIES + "...");
                try {
                    Thread.sleep(1000); // Aguarda 1s antes de retry
                } catch (InterruptedException ignored) {}
            }

            try {
                // 1. Captura contexto atual da tela
                String contexto = SmartContext.capturarEFormatar(page);
                System.out.println("[SmartContext] Capturados " + SmartContext.capturar(page).size() + " elementos");

                // 2. Consulta memória para decidir/restringir ação
                var memoryDecision = componentMemoryEngine.lookupDecision(passo, currentModuleKey, currentMenuKey);
                ActionDecision decision;
                if (memoryDecision.isPresent()) {
                    decision = memoryDecision.get();
                    System.out.println("[Memory Decision] " + decision.getAction());
                    lastDecisionFromLlm = false;
                    lastRag = null;
                } else {
                    String hints = componentMemoryEngine.buildContextHints(passo, currentModuleKey, currentMenuKey);
                    if (hints != null && !hints.isBlank()) {
                        contexto = contexto + "\n\n" + hints;
                    }
                    // 3. Consulta IA para decidir ação
                    lastRag = ragService.retrieveContext(passo, currentModuleKey, currentMenuKey);
                    String ragContext = lastRag != null ? lastRag.getPromptContext() : null;
                    decision = intentParser.decideAction(passo, contexto, ragContext);
                    System.out.println("[IA Decision] " + decision.getAction() + " - " + decision.getReason());
                    lastDecisionFromLlm = true;
                }
                lastDecision = decision;

                // 4. Executa a ação
                executarComTratamentoEspecial(decision, passo, contexto);

                // 5. Aguarda estabilização
                page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions().setTimeout(5000));

                long duration = System.currentTimeMillis() - startTime;
                componentMemoryEngine.onStepSuccess(passo, currentModuleKey, currentMenuKey, decision);
                if (lastDecisionFromLlm) {
                    persistRagLog(passo, lastRag, decision, true);
                }

                StepLog log = new StepLog(
                    stepNumber, passo,
                    decision.toString(),
                    formatPlaywrightAction(decision),
                    true, duration, null, retryCount
                );
                logStep(log);
                return log;

            } catch (Exception e) {
                retryCount++;
                System.err.println("[TestExecutor] ERRO (tentativa " + retryCount + "): " + e.getMessage());

                if (retryCount > MAX_RETRIES) {
                    long duration = System.currentTimeMillis() - startTime;
                    componentMemoryEngine.onStepFailure(passo, currentModuleKey, currentMenuKey, lastDecision, e.getMessage());
                    if (lastDecisionFromLlm) {
                        persistRagLog(passo, lastRag, lastDecision, false);
                    }

                    if (learningClient.isEnabled() && currentExecutionId != null && !currentExecutionId.isBlank()) {
                        try {
                            var componentOpt = componentMemoryEngine.extractComponentName(passo);
                            if (componentOpt.isPresent()) {
                                String componentName = componentOpt.get();
                                System.out.println("[LearningMode] Solicitando ajuda para componente: " + componentName);

                                var answer = learningClient.askAndWait(
                                    currentExecutionId,
                                    componentName,
                                    currentModuleKey,
                                    currentMenuKey,
                                    passo,
                                    e.getMessage()
                                );

                                if (answer != null) {
                                    Object execStrObj = answer.get("executionStrategy");
                                    String execStr = toJsonString(execStrObj);
                                    if (execStr != null && !execStr.isBlank() && ComponentMemoryEngine.isValidStrategyJson(execStr)) {
                                        ComponentMemoryEntry learned = new ComponentMemoryEntry();
                                        learned.setComponentName(componentName);
                                        learned.setModuleName(currentModuleKey);
                                        learned.setScreenName(currentMenuKey);
                                        learned.setLearnedFromUser(true);
                                        learned.setExecutionStrategy(execStr);

                                        Object fbObj = answer.get("fallbackStrategy");
                                        String fbStr = toJsonString(fbObj);
                                        if (fbStr != null && !fbStr.isBlank() && ComponentMemoryEngine.isValidStrategyJson(fbStr)) {
                                            learned.setFallbackStrategy(fbStr);
                                        }

                                        Object notesObj = answer.get("notes");
                                        if (notesObj != null) {
                                            learned.setNotes(String.valueOf(notesObj));
                                        }

                                        Object ctObj = answer.get("componentType");
                                        if (ctObj != null) {
                                            try {
                                                learned.setComponentType(ComponentType.valueOf(String.valueOf(ctObj)));
                                            } catch (Exception ignored) {
                                            }
                                        }
                                        Object btObj = answer.get("behaviorType");
                                        if (btObj != null) {
                                            try {
                                                learned.setBehaviorType(BehaviorType.valueOf(String.valueOf(btObj)));
                                            } catch (Exception ignored) {
                                            }
                                        }

                                        componentMemoryEngine.saveLearnedFromUser(learned);

                                        ActionDecision learnedDecision = ActionDecision.fromJson(execStr);
                                        String novoContexto = SmartContext.capturarEFormatar(page);
                                        executarComTratamentoEspecial(learnedDecision, passo, novoContexto);
                                        page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                                            new Page.WaitForLoadStateOptions().setTimeout(5000));

                                        componentMemoryEngine.onStepSuccess(passo, currentModuleKey, currentMenuKey, learnedDecision);

                                        StepLog recovered = new StepLog(
                                            stepNumber, passo,
                                            "LearningMode: " + learnedDecision.toString(),
                                            formatPlaywrightAction(learnedDecision),
                                            true, System.currentTimeMillis() - startTime, null, retryCount
                                        );
                                        logStep(recovered);
                                        return recovered;
                                    }
                                }
                            }
                        } catch (Exception lmErr) {
                            System.err.println("[LearningMode] Falha ao aplicar aprendizado: " + lmErr.getMessage());
                        }
                    }

                    StepLog log = new StepLog(
                        stepNumber, passo,
                        "Falhou após " + MAX_RETRIES + " tentativas",
                        "N/A",
                        false, duration, e.getMessage(), retryCount
                    );
                    logStep(log);
                    return log;
                }
            }
        }

        // Nunca deve chegar aqui
        return new StepLog(stepNumber, passo, "Erro inesperado", "N/A",
            false, 0, "Fluxo de retry falhou", MAX_RETRIES);
    }

    private void persistRagLog(String step, RagContextResult rag, ActionDecision decision, boolean success) {
        try {
            RagQueryLogEntry log = new RagQueryLogEntry();
            log.setTestExecutionId(currentExecutionId);
            log.setStepDescription(step);
            log.setModuleName(currentModuleKey);
            log.setScreenName(currentMenuKey);
            log.setRetrievedComponents(SystemKnowledgeRagService.toRetrievedComponentsJson(rag != null ? rag.getMatches() : null));
            if (rag != null && rag.getSelectedEntry() != null) {
                log.setSelectedComponent(rag.getSelectedEntry().getComponentName());
                log.setScore(rag.getSelectedScore());
            }
            log.setUsedInPrompt(rag != null && rag.isUsedInPrompt());
            log.setPromptContext(rag != null ? rag.getPromptContext() : null);
            log.setLlmDecision(decision != null ? decision.toString() : null);
            log.setExecutionSuccess(success);
            componentMemoryEngine.getRepository().insertRagQueryLog(log);

            if (rag != null && rag.isUsedInPrompt() && rag.getSelectedEntry() != null && rag.getSelectedEntry().getId() != null) {
                componentMemoryEngine.getRepository().markUsedAsContext(rag.getSelectedEntry().getId(), success);
            }
        } catch (Exception ignored) {
        }
    }

    private static String toJsonString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return ((String) value).trim();
        }
        try {
            return gson.toJson(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
     * Executa ação com tratamento especial para campos de busca e navegação hierárquica
     */
    private void executarComTratamentoEspecial(ActionDecision decision, String passo, String contexto)
            throws Exception {

        // Se um modal de seleção de unidade estiver bloqueando a tela, resolve antes de qualquer outra ação
        if (!isUnitSelectionStep(passo) && isUnitSelectionModalVisible()) {
            System.out.println("[TestExecutor] Modal de unidade detectado bloqueando a tela - resolvendo antes de continuar...");
            executarSelecaoUnidade("Selecionar unidade");
            page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                new Page.WaitForLoadStateOptions().setTimeout(5000));
        }

        if (!consultorioSelecionado && !isRoomSelectionStep(passo) && isNextPatientStep(passo) && isConsultorioSelectionVisible()) {
            System.out.println("[TestExecutor] Consultório pendente antes de Próximo Paciente - selecionando sala disponível...");
            executarSelecaoConsultorio();
        }

        // Tratamento especial para navegação hierárquica de menus
        if (isSuccessValidationStep(passo)) {
            System.out.println("[TestExecutor] Validacao de sucesso - verificando toast/mensagem real");
            executarValidacaoMensagemSucesso();
            return;
        }

        if (isNextPatientStep(passo)) {
            System.out.println("[TestExecutor] Detectado Proximo Paciente - tratamento especial");
            executarProximoPacienteOuAtenderDaLista();
            return;
        }

        if (isMenuNavigationStep(passo)) {
            System.out.println("[TestExecutor] Detectada navegação de menu - tratamento especial");
            executarNavegacaoMenu(passo);
            return;
        }

        // Tratamento especial para seleção de unidade no modal
        if (isUnitSelectionStep(passo)) {
            System.out.println("[TestExecutor] Detectada seleção de unidade - tratamento especial");
            executarSelecaoUnidade(passo);
            return;
        }

        if (isRoomSelectionStep(passo)) {
            System.out.println("[TestExecutor] Detectada seleção de sala/consultório - tratamento especial");
            executarSelecaoConsultorio();
            return;
        }

        if (isEscutaInicialTabStep(passo)) {
            System.out.println("[TestExecutor] Detectada navegacao para aba Escuta Inicial - tratamento especial");
            ensureEscutaInicialAttendanceOpen();
            executarNavegacaoAbaEscutaInicial();
            return;
        }

        if (isEscutaInicialFieldFillStep(passo)) {
            System.out.println("[TestExecutor] Detectado preenchimento de campo da Escuta Inicial - tratamento especial");
            ensureEscutaInicialAttendanceOpen();
            executarPreenchimentoCampoEscutaInicial(passo, decision);
            return;
        }

        if (isClinicalTextFieldFillStep(passo)) {
            System.out.println("[TestExecutor] Detectado preenchimento de campo clinico - tratamento especial");
            executarPreenchimentoCampoClinico(passo, decision);
            return;
        }

        if (isEsusSectionInsertStep(passo, "motivacao")) {
            System.out.println("[TestExecutor] Detectado Inserir de Motivacao e-SUS - tratamento especial");
            executarAbrirPopupEsus("Motivacao");
            return;
        }

        if (isEsusSectionInsertStep(passo, "procedimentos")) {
            System.out.println("[TestExecutor] Detectado Inserir de Procedimentos e-SUS - tratamento especial");
            executarAbrirPopupEsus("Procedimentos");
            return;
        }

        if (isEsusPopupSelectionStep(passo)) {
            System.out.println("[TestExecutor] Detectada selecao em popup e-SUS - tratamento especial");
            executarSelecaoPopupEsus(passo);
            return;
        }

        if (isEsusSaveAndCloseStep(passo)) {
            System.out.println("[TestExecutor] Detectado Salvar e Fechar em popup e-SUS - tratamento especial");
            executarSalvarFecharPopupEsus();
            return;
        }

        if (isEsusPopupVisible() && normalizeText(passo).contains("finalizar")) {
            System.out.println("[TestExecutor] Popup e-SUS aberto antes de finalizar - salvando e fechando popup");
            executarSalvarFecharPopupEsus();
            if (normalizeText(passo).contains("finalizar atendimento")) {
                executarAbrirPopupFinalizarAtendimento();
            }
            return;
        }

        if (isCiapInsertStep(passo)) {
            System.out.println("[TestExecutor] Detectado clique em Inserir do campo CIAP - tratamento especial");
            ensureEscutaInicialAttendanceOpen();
            executarAbrirPopupCiap();
            return;
        }

        if (isCiapPopupSelectionStep(passo)) {
            System.out.println("[TestExecutor] Detectada selecao de CIAP no popup - tratamento especial");
            executarSelecaoCiapSalvarFechar();
            return;
        }

        if (isFinishAttendanceOpenStep(passo)) {
            System.out.println("[TestExecutor] Detectado clique em Finalizar Atendimento - tratamento especial");
            ensureEscutaInicialAttendanceOpen();
            executarAbrirPopupFinalizarAtendimento();
            return;
        }

        if (isFinalizationCheckboxStep(passo)) {
            System.out.println("[TestExecutor] Detectado checkbox no popup de finalizacao - tratamento especial");
            executarMarcarCheckboxFinalizacao(passo);
            return;
        }

        if (isFinalizationSubmitStep(passo) && finalizationPopupArmed) {
            System.out.println("[TestExecutor] Detectado clique final no popup de finalizacao - tratamento especial");
            executarCliqueFinalizarPopup();
            return;
        }

        // Tratamento especial para busca de paciente
        if (isPatientSearchStep(passo)) {
            System.out.println("[TestExecutor] Detectada busca de paciente - tratamento especial");
            executarBuscaPaciente(passo);
            return;
        }

        // Tratamento especial para campos de busca
        if (decision.isSearchField()) {
            System.out.println("[TestExecutor] Detectado campo de busca - tratamento especial");

            // 1. Preenche o campo
            actionExecutor.execute(decision);

            // 2. Aguarda resultados aparecerem (debounce da busca)
            System.out.println("[TestExecutor] Aguardando resultados da busca (800ms)...");
            Thread.sleep(800);

            // 3. Recaptura contexto após resultados
            String novoContexto = SmartContext.capturarEFormatar(page);
            String valorBusca = decision.getValue() != null ? decision.getValue() : "";

            // 4. Consulta IA qual resultado clicar
            System.out.println("[TestExecutor] Consultando IA qual resultado clicar...");
            ActionDecision clickDecision = intentParser.decideSearchResult(valorBusca, novoContexto);

            // 5. Clica no resultado
            System.out.println("[IA Decision] Clicar em resultado: " + clickDecision.getText());
            actionExecutor.execute(clickDecision);

        } else {
            // Execução normal
            actionExecutor.execute(decision);
        }
    }

    /**
     * Verifica se o passo é uma navegação de menu hierárquico
     */
    private boolean isMenuNavigationStep(String passo) {
        String lower = passo.toLowerCase();
        return lower.contains("navegar em") && lower.contains(">");
    }

    /**
     * Verifica se o passo é seleção de unidade no modal
     */
    private boolean isUnitSelectionStep(String passo) {
        String lower = passo.toLowerCase();
        return (lower.contains("selecionar") && lower.contains("unidade")) ||
               (lower.contains("unidade") && lower.contains("modal"));
    }

    private boolean isRoomSelectionStep(String passo) {
        String lower = passo == null ? "" : passo.toLowerCase();
        String normalized = normalizeText(passo);
        if (normalized.contains("consulta medica") || lower.contains("consulta médica")) {
            return false;
        }

        boolean mentionsRoom = normalized.contains("consultorio") || lower.contains("consultório") ||
            lower.contains("consultorio") || lower.contains("consultÃ") ||
            lower.contains("sala de atendimento") || normalized.contains("sala de atendimento") ||
            lower.contains("sala dispon") || normalized.contains("sala dispon");
        boolean asksSelection = lower.contains("selecion") || normalized.contains("selecion") ||
            lower.contains("escolh") || normalized.contains("escolh") ||
            lower.contains("opção") || normalized.contains("opcao") ||
            lower.contains("dispon") || normalized.contains("dispon");
        return mentionsRoom && asksSelection;
    }

    private boolean isNextPatientStep(String passo) {
        return normalizeText(passo).contains("proximo paciente");
    }

    private void executarProximoPacienteOuAtenderDaLista() throws Exception {
        if (isAttendanceEditScreenVisible()) {
            return;
        }

        boolean clickedNext = clickButtonTextScript("Proximo Paciente", "PrÃ³ximo Paciente");
        if (!clickedNext) {
            System.out.println("[TestExecutor] Botao Proximo Paciente nao clicavel; tentando Atender na grade...");
        }

        if (clickedNext && waitForAttendanceEditScreen(10000)) {
            return;
        }

        if (isNoWaitingPatientsMessageVisible()) {
            System.out.println("[TestExecutor] Sistema informou ausencia de proximo paciente; tentando paciente disponivel na grade...");
        }

        if (clickAtenderFromGridScript()) {
            if (waitForAttendanceEditScreen(12000)) {
                return;
            }
        }

        if (isNoWaitingPatientsMessageVisible() && !hasAtenderInGrid()) {
            throw new RuntimeException("Nao ha pacientes na Lista de Espera e nenhum botao Atender esta disponivel na grade");
        }

        throw new RuntimeException("Nao foi possivel abrir a tela de atendimento apos Proximo Paciente/Atender");
    }

    private void ensureEscutaInicialAttendanceOpen() throws Exception {
        if (isAttendanceEditScreenVisible()) {
            return;
        }

        System.out.println("[TestExecutor] Tela de atendimento ainda nao esta aberta; tentando abrir paciente da grade...");
        if (clickAtenderFromGridScript() && waitForAttendanceEditScreen(12000)) {
            return;
        }

        if (isNoWaitingPatientsMessageVisible() && !hasAtenderInGrid()) {
            throw new RuntimeException("Nao ha paciente disponivel para abrir Escuta Inicial");
        }

        throw new RuntimeException("Tela de atendimento nao abriu; campos de Escuta Inicial ainda nao estao disponiveis");
    }

    private boolean waitForAttendanceEditScreen(int timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (isAttendanceEditScreenVisible()) {
                return true;
            }
            page.waitForTimeout(300);
        }
        return isAttendanceEditScreenVisible();
    }

    private boolean isAttendanceEditScreenVisible() {
        try {
            String body = normalizeText(page.locator("body").innerText());
            return (body.contains("editando escuta inicial") ||
                    body.contains("editando consulta medica") ||
                    body.contains("finalizar atendimento")) &&
                (body.contains("inicio") || body.contains("evolucao") || body.contains("esus") || body.contains("escuta inicial"));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isNoWaitingPatientsMessageVisible() {
        try {
            String body = normalizeText(page.locator("body").innerText());
            return body.contains("nao ha pacientes na lista de espera");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasAtenderInGrid() {
        try {
            String body = normalizeText(page.locator("body").innerText());
            return body.contains("atender");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean clickButtonTextScript(String... labels) {
        try {
            Object result = page.evaluate(
                "(labels) => {" +
                    "const wanted = labels.map(v => (v || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').toLowerCase());" +
                    "const norm = s => (s || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').replace(/\\s+/g, ' ').trim().toLowerCase();" +
                    "const visible = el => {" +
                        "const r = el.getBoundingClientRect();" +
                        "const st = window.getComputedStyle(el);" +
                        "return r.width > 0 && r.height > 0 && st.visibility !== 'hidden' && st.display !== 'none';" +
                    "};" +
                    "const buttons = Array.from(document.querySelectorAll('button, [role=button], [title], [aria-label], span'))" +
                        ".filter(visible);" +
                    "for (const btn of buttons) {" +
                        "const text = norm(btn.innerText || btn.textContent || btn.getAttribute('title') || btn.getAttribute('aria-label'));" +
                        "if (wanted.some(w => text === w || text.includes(w))) {" +
                            "btn.scrollIntoView({block:'center', inline:'nearest'});" +
                            "btn.click();" +
                            "return true;" +
                        "}" +
                    "}" +
                    "return false;" +
                "}",
                java.util.Arrays.asList(labels)
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            System.out.println("[TestExecutor] Clique por texto falhou: " + e.getMessage());
            return false;
        }
    }

    private boolean clickAtenderFromGridScript() {
        try {
            Object result = page.evaluate(
                "() => {" +
                    "const norm = s => (s || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').replace(/\\s+/g, ' ').trim().toLowerCase();" +
                    "const visible = el => {" +
                        "const r = el.getBoundingClientRect();" +
                        "const st = window.getComputedStyle(el);" +
                        "return r.width > 0 && r.height > 0 && st.visibility !== 'hidden' && st.display !== 'none';" +
                    "};" +
                    "const candidates = Array.from(document.querySelectorAll('a, button, [role=button], span, div')).filter(visible);" +
                    "for (const el of candidates) {" +
                        "const text = norm(el.innerText || el.textContent || el.getAttribute('title') || el.getAttribute('aria-label'));" +
                        "if (text === 'atender') {" +
                            "el.scrollIntoView({block:'center', inline:'nearest'});" +
                            "el.click();" +
                            "return true;" +
                        "}" +
                    "}" +
                    "return false;" +
                "}"
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            System.out.println("[TestExecutor] Clique em Atender da grade falhou: " + e.getMessage());
            return false;
        }
    }

    private boolean isEscutaInicialTabStep(String passo) {
        String normalized = normalizeText(passo);
        if (!isEscutaInicialContext()) return false;
        return normalized.contains("escuta inicial") &&
            (normalized.contains("aba") || normalized.contains("tab"));
    }

    private boolean isEscutaInicialFieldFillStep(String passo) {
        String normalized = normalizeText(passo);
        if (!normalized.contains("preencher")) {
            return false;
        }

        if (!isEscutaInicialContext()) return false;

        return normalized.contains("peso") ||
            normalized.contains("altura");
    }

    private boolean isClinicalTextFieldFillStep(String passo) {
        String normalized = normalizeText(passo);
        if (!normalized.contains("preencher")) return false;
        if (normalized.contains("peso") || normalized.contains("altura") || normalized.contains("antropometria")) {
            return false;
        }
        return normalized.contains("anamnese") ||
            normalized.contains("subjetivo") ||
            normalized.contains("objetivo") ||
            normalized.contains("avaliacao");
    }

    private boolean isEscutaInicialAnthropometriaCombinedFillStep(String passo) {
        String normalized = normalizeText(passo);
        if (!normalized.contains("preencher")) return false;
        if (!normalized.contains("antropometria")) return false;
        return normalized.contains("peso") && normalized.contains("altura");
    }

    private String getEscutaInicialFieldName(String passo) {
        String normalized = normalizeText(passo);
        if (normalized.contains("subjetivo")) return "Subjetivo";
        if (normalized.contains("altura")) return "Altura";
        if (normalized.contains("peso")) return "Peso";
        return "";
    }

    private String getClinicalFieldName(String passo) {
        String normalized = normalizeText(passo);
        if (normalized.contains("anamnese")) return "Anamnese";
        if (normalized.contains("subjetivo")) return "Subjetivo";
        if (normalized.contains("objetivo")) return "Objetivo";
        if (normalized.contains("avaliacao")) return "Avaliacao";
        return "";
    }

    private boolean isEsusSectionInsertStep(String passo, String section) {
        String normalized = normalizeText(passo);
        if (!normalized.contains("inserir")) return false;
        if (normalized.contains("popup") || normalized.contains("salvar")) return false;
        if ("motivacao".equals(section)) {
            return normalized.contains("motivacao");
        }
        return normalized.contains("procedimento");
    }

    private boolean isEsusPopupSelectionStep(String passo) {
        String normalized = normalizeText(passo);
        if (!(normalized.contains("selecion") || normalized.contains("qualquer") || normalized.contains("opcao valida"))) {
            return false;
        }
        return normalized.contains("cid10") || normalized.contains("procedimento");
    }

    private boolean isEsusSaveAndCloseStep(String passo) {
        String normalized = normalizeText(passo);
        return normalized.contains("salvar e fechar") && isEsusPopupVisible();
    }

    private boolean isCiapInsertStep(String passo) {
        String normalized = normalizeText(passo);
        return normalized.contains("ciap") &&
            normalized.contains("inserir") &&
            !normalized.contains("popup") &&
            !normalized.contains("salvar");
    }

    private boolean isCiapPopupSelectionStep(String passo) {
        String normalized = normalizeText(passo);
        return normalized.contains("ciap") &&
            (normalized.contains("popup") || normalized.contains("lista") || normalized.contains("disponivel")) &&
            (normalized.contains("selecion") || normalized.contains("qualquer") || normalized.contains("primeiro item"));
    }

    private boolean isFinishAttendanceOpenStep(String passo) {
        String normalized = normalizeText(passo);
        return normalized.contains("clicar") && normalized.contains("finalizar atendimento");
    }

    private boolean isFinalizationCheckboxStep(String passo) {
        String normalized = normalizeText(passo);
        return (normalized.contains("consulta medica") || normalized.contains("alta do episodio")) &&
            (normalized.contains("finalizar atendimento") || normalized.contains("editando finalizar atendimento")) &&
            (normalized.contains("checkbox") || normalized.contains("marcar"));
    }

    private boolean isFinalizationSubmitStep(String passo) {
        String normalized = normalizeText(passo);
        return normalized.contains("clicar") &&
            normalized.contains("finalizar") &&
            !normalized.contains("finalizar atendimento");
    }

    private boolean isSuccessValidationStep(String passo) {
        String normalized = normalizeText(passo);
        return normalized.contains("validar") &&
            normalized.contains("mensagem") &&
            normalized.contains("sucesso");
    }

    private void executarNavegacaoAbaEscutaInicial() throws Exception {
        Locator aba = findEscutaInicialTab();
        if (aba == null) {
            throw new RuntimeException("Aba Escuta Inicial nao encontrada na tela de atendimento");
        }

        try {
            aba.scrollIntoViewIfNeeded();
        } catch (Exception ignored) {}

        try {
            aba.click(new Locator.ClickOptions().setTimeout(8000));
        } catch (Exception e) {
            System.out.println("[TestExecutor] Clique normal na aba Escuta Inicial falhou, tentando clique forcado: " + e.getMessage());
            aba.click(new Locator.ClickOptions().setTimeout(8000).setForce(true));
        }

        page.waitForTimeout(1000);
    }

    private void executarPreenchimentoCampoEscutaInicial(String passo, ActionDecision decision) throws Exception {
        if (isEscutaInicialAnthropometriaCombinedFillStep(passo)) {
            System.out.println("[TestExecutor] Detectado preenchimento combinado de Antropometria (Peso/Altura)");
            executarPreenchimentoAntropometriaPesoAltura(passo);
            return;
        }

        String fieldName = getEscutaInicialFieldName(passo);
        if (fieldName.isEmpty()) {
            throw new RuntimeException("Campo da Escuta Inicial nao identificado no passo: " + passo);
        }

        String value = extractFillValueFromStep(passo, decision);
        if (value.isEmpty()) {
            throw new RuntimeException("Valor para preencher " + fieldName + " nao identificado no passo: " + passo);
        }

        executarNavegacaoAbaEscutaInicial();

        Locator field = findEscutaInicialInput(fieldName);
        if (field == null) {
            throw new RuntimeException("Campo " + fieldName + " nao encontrado na aba Escuta Inicial");
        }

        System.out.println("[TestExecutor] Preenchendo " + fieldName + " com valor: " + value);
        fillLocator(field, value, fieldName);
        page.waitForTimeout(400);
    }

    private void executarPreenchimentoCampoClinico(String passo, ActionDecision decision) throws Exception {
        String fieldName = getClinicalFieldName(passo);
        if (fieldName.isEmpty()) {
            throw new RuntimeException("Campo clinico nao identificado no passo: " + passo);
        }

        String value = extractFillValueFromStep(passo, decision);
        if (value.isEmpty()) {
            throw new RuntimeException("Valor para preencher " + fieldName + " nao identificado no passo: " + passo);
        }

        if (isEvolutionField(fieldName)) {
            clickTabIfVisible("Evolucao");
        }

        Locator clinicalField = findClinicalFieldByLabel(fieldName);
        if (clinicalField != null) {
            System.out.println("[TestExecutor] Preenchendo " + fieldName + " via campo marcado por label");
            fillLocator(clinicalField, value, fieldName);
            page.waitForTimeout(400);
            if (!fieldContainsValue(clinicalField, value)) {
                throw new RuntimeException("Campo " + fieldName + " foi preenchido, mas o valor nao ficou no componente");
            }
            return;
        }

        Locator field = findEscutaInicialInput(fieldName);
        if (field != null) {
            System.out.println("[TestExecutor] Preenchendo " + fieldName + " com valor: " + value);
            fillLocator(field, value, fieldName);
            page.waitForTimeout(400);
            if (!fieldContainsValue(field, value)) {
                throw new RuntimeException("Campo " + fieldName + " foi encontrado, mas o valor nao permaneceu preenchido");
            }
            return;
        }

        if (fillFieldByLabelScript(fieldName, value)) {
            System.out.println("[TestExecutor] Campo " + fieldName + " preenchido via script por label");
            page.waitForTimeout(400);
            return;
        }

        throw new RuntimeException("Campo " + fieldName + " nao encontrado para preenchimento clinico");
    }

    private boolean isEvolutionField(String fieldName) {
        String normalized = normalizeText(fieldName);
        return normalized.contains("anamnese") ||
            normalized.contains("subjetivo") ||
            normalized.contains("objetivo") ||
            normalized.contains("avaliacao");
    }

    private void clickTabIfVisible(String tabText) {
        String normalizedTab = normalizeText(tabText);
        String[] labels = normalizedTab.contains("evolucao")
            ? new String[] {"Evolucao", "EvoluÃ§Ã£o", "Evolução"}
            : new String[] {tabText};

        for (String label : labels) {
            String[] selectors = {
                "[role='tab']:has-text('" + label + "')",
                "[role='button']:has-text('" + label + "')",
                "button:has-text('" + label + "')",
                "span:has-text('" + label + "')",
                "div:has-text('" + label + "')"
            };

            for (String selector : selectors) {
                Locator candidate = pickExactOrShortButton(page.locator(selector), normalizedTab);
                if (candidate == null) continue;
                try {
                    candidate.click(new Locator.ClickOptions().setTimeout(3000));
                    page.waitForTimeout(500);
                    return;
                } catch (Exception e) {
                    try {
                        candidate.click(new Locator.ClickOptions().setTimeout(3000).setForce(true));
                        page.waitForTimeout(500);
                        return;
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private Locator findClinicalFieldByLabel(String fieldName) {
        String marker = "clinical-" + System.currentTimeMillis();
        try {
            Object result = page.evaluate(
                "(args) => {" +
                    "const wanted = args.field.normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').replace(/\\s+/g, ' ').trim().toLowerCase();" +
                    "const marker = args.marker;" +
                    "const norm = s => (s || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').replace(/\\s+/g, ' ').trim().toLowerCase();" +
                    "const visible = el => {" +
                        "const r = el.getBoundingClientRect();" +
                        "const s = window.getComputedStyle(el);" +
                        "return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';" +
                    "};" +
                    "const editables = 'textarea, input:not([type=hidden]), [role=textbox], [contenteditable=true], .dx-texteditor-input';" +
                    "const labels = Array.from(document.querySelectorAll('label, span, div, td'))" +
                        ".filter(el => {" +
                            "if (!visible(el)) return false;" +
                            "const t = norm(el.innerText || el.textContent);" +
                            "return t === wanted || (t.startsWith(wanted) && t.length <= wanted.length + 18);" +
                        "});" +
                    "const candidates = [];" +
                    "const pushField = (field, label) => {" +
                        "if (!field || !visible(field)) return;" +
                        "const fb = field.getBoundingClientRect();" +
                        "const lb = label.getBoundingClientRect();" +
                        "if (fb.y < lb.y - 12 || fb.y > lb.y + 520) return;" +
                        "candidates.push({field, dy: Math.abs(fb.y - lb.y), area: fb.width * fb.height});" +
                    "};" +
                    "for (const label of labels) {" +
                        "let scope = label;" +
                        "for (let i = 0; scope && i < 7; i++, scope = scope.parentElement) {" +
                            "Array.from(scope.querySelectorAll(editables)).forEach(field => pushField(field, label));" +
                        "}" +
                        "let next = label.nextElementSibling;" +
                        "for (let i = 0; next && i < 80; i++, next = next.nextElementSibling) {" +
                            "if (next.matches && next.matches(editables)) pushField(next, label);" +
                            "if (next.querySelectorAll) Array.from(next.querySelectorAll(editables)).forEach(field => pushField(field, label));" +
                        "}" +
                    "}" +
                    "candidates.sort((a, b) => a.dy - b.dy || b.area - a.area);" +
                    "const target = candidates[0] && candidates[0].field;" +
                    "if (!target) return false;" +
                    "document.querySelectorAll('[data-qa-agent-clinical-field]').forEach(el => el.removeAttribute('data-qa-agent-clinical-field'));" +
                    "target.setAttribute('data-qa-agent-clinical-field', marker);" +
                    "target.scrollIntoView({block:'center', inline:'nearest'});" +
                    "return true;" +
                "}",
                java.util.Map.of("field", fieldName, "marker", marker)
            );
            if (!Boolean.TRUE.equals(result)) {
                return null;
            }
            Locator field = page.locator("[data-qa-agent-clinical-field='" + marker + "']").first();
            return isVisible(field) ? field : null;
        } catch (Exception e) {
            System.out.println("[TestExecutor] Localizacao do campo clinico por label falhou para " + fieldName + ": " + e.getMessage());
            return null;
        }
    }

    private boolean fillFieldByLabelScript(String fieldName, String value) {
        try {
            Object result = page.evaluate(
                "(args) => {" +
                    "const wanted = args.field.normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').toLowerCase();" +
                    "const value = args.value;" +
                    "const norm = s => (s || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').replace(/\\s+/g, ' ').trim().toLowerCase();" +
                    "const visible = el => {" +
                        "const r = el.getBoundingClientRect();" +
                        "const s = window.getComputedStyle(el);" +
                        "return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';" +
                    "};" +
                    "const editables = 'textarea, input:not([type=hidden]), [role=textbox], [contenteditable=true], .dx-texteditor-input';" +
                    "const setValue = el => {" +
                        "el.scrollIntoView({block:'center', inline:'nearest'});" +
                        "el.focus();" +
                        "if (el.isContentEditable) {" +
                            "el.textContent = value;" +
                        "} else {" +
                            "const proto = el.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;" +
                            "const desc = Object.getOwnPropertyDescriptor(proto, 'value');" +
                            "if (desc && desc.set) desc.set.call(el, value); else el.value = value;" +
                        "}" +
                        "el.dispatchEvent(new InputEvent('input', {bubbles:true, data:value, inputType:'insertText'}));" +
                        "el.dispatchEvent(new Event('change', {bubbles:true}));" +
                        "el.dispatchEvent(new Event('blur', {bubbles:true}));" +
                        "el.blur();" +
                        "const actual = el.isContentEditable ? (el.textContent || '') : (el.value || el.getAttribute('value') || '');" +
                        "return actual.trim() === value.trim();" +
                    "};" +
                    "const labels = Array.from(document.querySelectorAll('label, span, div, td'))" +
                        ".filter(el => {" +
                            "if (!visible(el)) return false;" +
                            "const t = norm(el.innerText || el.textContent);" +
                            "if (!t) return false;" +
                            "if (t === wanted) return true;" +
                            "if (t.startsWith(wanted) && t.length <= wanted.length + 18) return true;" +
                            "return false;" +
                        "});" +
                    "for (const label of labels) {" +
                        "let scope = label;" +
                        "for (let i = 0; scope && i < 7; i++, scope = scope.parentElement) {" +
                            "const field = Array.from(scope.querySelectorAll(editables)).find(visible);" +
                            "if (field) return setValue(field);" +
                        "}" +
                        "let next = label;" +
                        "for (let i = 0; next && i < 80; i++, next = next.nextElementSibling) {" +
                            "const field = next.matches && next.matches(editables) ? next : (next.querySelector ? next.querySelector(editables) : null);" +
                            "if (field && visible(field)) return setValue(field);" +
                        "}" +
                    "}" +
                    "const contextual = Array.from(document.querySelectorAll(editables)).find(el => {" +
                        "if (!visible(el)) return false;" +
                        "let n = el;" +
                        "for (let i = 0; n && i < 8; i++, n = n.parentElement) {" +
                            "const t = norm(n.innerText || '');" +
                            "if (t.includes(wanted) && !t.includes('buscar menus')) return true;" +
                        "}" +
                        "return false;" +
                    "});" +
                    "return contextual ? setValue(contextual) : false;" +
                "}",
                java.util.Map.of("field", fieldName, "value", value)
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            System.out.println("[TestExecutor] Preenchimento via script falhou para " + fieldName + ": " + e.getMessage());
            return false;
        }
    }

    private void executarPreenchimentoAntropometriaPesoAltura(String passo) throws Exception {
        executarNavegacaoAbaEscutaInicial();
        ensureAntropometriaSectionVisible();

        String peso = extractValueForField(passo, "peso");
        String altura = extractValueForField(passo, "altura");
        if (peso.isEmpty() || altura.isEmpty()) {
            throw new RuntimeException("Nao foi possivel extrair Peso/Altura do passo: " + passo);
        }

        Locator pesoField = findEscutaInicialInputInScope(findAntropometriaScope(), "Peso");
        if (pesoField == null) {
            pesoField = findEscutaInicialInput("Peso");
        }
        Locator alturaField = findEscutaInicialInputInScope(findAntropometriaScope(), "Altura");
        if (alturaField == null) {
            alturaField = findEscutaInicialInput("Altura");
        }

        if (pesoField == null) throw new RuntimeException("Campo Peso nao encontrado em Antropometria");
        if (alturaField == null) throw new RuntimeException("Campo Altura nao encontrado em Antropometria");

        System.out.println("[TestExecutor] Preenchendo Peso=" + peso + " e Altura=" + altura + " (Antropometria)");
        fillLocator(pesoField, peso, "Peso");
        page.waitForTimeout(250);
        fillLocator(alturaField, altura, "Altura");
        page.waitForTimeout(400);
    }

    private void ensureAntropometriaSectionVisible() throws Exception {
        Locator scope = findAntropometriaScope();
        if (scope != null && scope.isVisible()) {
            if (isVisible(scope.getByText("Peso", new Locator.GetByTextOptions().setExact(true)).first()) ||
                isVisible(scope.getByText("Altura", new Locator.GetByTextOptions().setExact(true)).first())) {
                return;
            }
        }

        Locator header = page.getByText("Antropometria", new Page.GetByTextOptions().setExact(false)).first();
        if (isVisible(header)) {
            try {
                header.scrollIntoViewIfNeeded();
            } catch (Exception ignored) {}

            try {
                Locator clickable = header;
                try {
                    Locator roleBtn = header.locator("xpath=ancestor-or-self::*[@role='button'][1]").first();
                    if (isVisible(roleBtn)) clickable = roleBtn;
                } catch (Exception ignored) {}
                clickable.click(new Locator.ClickOptions().setTimeout(8000));
            } catch (Exception e) {
                System.out.println("[TestExecutor] Clique em Antropometria falhou, tentando force: " + e.getMessage());
                header.click(new Locator.ClickOptions().setTimeout(8000).setForce(true));
            }

            page.waitForTimeout(500);
        }
    }

    private Locator findAntropometriaScope() {
        String[] selectors = {
            "[role='group']:has-text('Antropometria')",
            "[role='region']:has-text('Antropometria')",
            "[class*='accordion']:has-text('Antropometria')",
            "[class*='panel']:has-text('Antropometria')",
            "[class*='card']:has-text('Antropometria')",
            "fieldset:has-text('Antropometria')",
            "div:has-text('Antropometria')"
        };

        for (String selector : selectors) {
            Locator pick = pickVisibleScopeContaining(page.locator(selector), "antropometria");
            if (pick != null) {
                return pick;
            }
        }

        return null;
    }

    private Locator findEscutaInicialInputInScope(Locator scope, String fieldName) {
        if (scope == null) return null;
        Locator direct = findEscutaInicialInputByDirectSelectorsInScope(scope, fieldName);
        if (direct != null) return direct;

        Locator nearText = findInputNearText(scope, fieldName);
        if (nearText != null) return nearText;

        Locator label = findEscutaInicialFieldLabel(fieldName);
        if (label != null) {
            Locator afterLabel = findEditableAfterLabel(label, fieldName);
            if (afterLabel != null) return afterLabel;
        }
        return null;
    }

    private Locator findEscutaInicialInputByDirectSelectorsInScope(Locator scope, String fieldName) {
        String lower = (fieldName == null ? "" : fieldName).toLowerCase();
        String[] selectors = {
            "input[placeholder*='" + lower + "' i]",
            "textarea[placeholder*='" + lower + "' i]",
            "input[aria-label*='" + lower + "' i]",
            "textarea[aria-label*='" + lower + "' i]",
            "[role='textbox'][aria-label*='" + lower + "' i]",
            "[name*='" + lower + "' i]",
            "[id*='" + lower + "' i]",
            ".dx-texteditor-input[aria-label*='" + lower + "' i]",
            ".dx-texteditor-input[name*='" + lower + "' i]",
            ".dx-texteditor-input[id*='" + lower + "' i]"
        };
        for (String selector : selectors) {
            Locator pick = pickFirstEditable(scope.locator(selector), 40);
            if (pick != null) return pick;
        }
        return null;
    }

    private Locator findInputNearText(Locator scope, String fieldName) {
        String[] labelSelectors = {
            "label",
            "span",
            "div",
            "td"
        };
        for (String sel : labelSelectors) {
            Locator labels = scope.locator(sel).filter(new Locator.FilterOptions().setHasText(fieldName));
            int count;
            try {
                count = Math.min(labels.count(), 40);
            } catch (Exception e) {
                continue;
            }
            for (int i = 0; i < count; i++) {
                try {
                    Locator label = labels.nth(i);
                    if (!isVisible(label)) continue;
                    Locator container = label.locator("xpath=ancestor-or-self::*[.//input|.//textarea|.//*[@role='textbox']|.//*[@contenteditable='true']][1]").first();
                    if (!isVisible(container)) continue;
                    Locator pick = pickFirstEditable(container.locator("input, textarea, [role='textbox'], [contenteditable='true'], .dx-texteditor-input"), 10);
                    if (pick != null) return pick;
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private String extractValueForField(String passo, String field) {
        String text = passo == null ? "" : passo;
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?i)" + field + ".*?(\\d+(?:[\\.,]\\d+)?)")
            .matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    private String extractFillValueFromStep(String passo, ActionDecision decision) {
        if (decision != null && decision.getValue() != null && !decision.getValue().trim().isEmpty()) {
            return decision.getValue().trim();
        }

        String quoted = extractQuotedText(passo);
        if (!quoted.isEmpty()) {
            return quoted;
        }

        java.util.regex.Matcher afterKeyword = java.util.regex.Pattern
            .compile("(?i)(?:valor|texto)\\s+(.+)$")
            .matcher(passo == null ? "" : passo);
        if (afterKeyword.find()) {
            String value = afterKeyword.group(1).trim();
            value = value.replaceFirst("^[\"']", "").replaceFirst("[\"']$", "").trim();
            if (!value.isEmpty()) return value;
        }

        java.util.regex.Matcher number = java.util.regex.Pattern
            .compile("(\\d+(?:[\\.,]\\d+)?)")
            .matcher(passo == null ? "" : passo);
        if (number.find()) {
            return number.group(1);
        }

        return "";
    }

    private Locator findEscutaInicialInput(String fieldName) {
        String normalizedField = normalizeText(fieldName);

        Locator direct = findEscutaInicialInputByDirectSelectors(fieldName);
        if (direct != null) {
            return direct;
        }

        Locator label = findEscutaInicialFieldLabel(fieldName);
        if (label != null) {
            Locator afterLabel = findEditableAfterLabel(label, fieldName);
            if (afterLabel != null) {
                return afterLabel;
            }
        }

        Locator contextual = findEscutaInicialInputByContext(normalizedField);
        if (contextual != null) {
            return contextual;
        }

        return null;
    }

    private Locator findEscutaInicialInputByDirectSelectors(String fieldName) {
        String lower = (fieldName == null ? "" : fieldName).toLowerCase();
        String[] selectors = {
            "input[placeholder*='" + fieldName + "']",
            "textarea[placeholder*='" + fieldName + "']",
            "input[placeholder*='" + lower + "' i]",
            "textarea[placeholder*='" + lower + "' i]",
            "input[aria-label*='" + fieldName + "']",
            "textarea[aria-label*='" + fieldName + "']",
            "input[aria-label*='" + lower + "' i]",
            "textarea[aria-label*='" + lower + "' i]",
            "[role='textbox'][aria-label*='" + fieldName + "']",
            "[role='textbox'][aria-label*='" + lower + "' i]",
            "[name*='" + lower + "' i]",
            "[id*='" + lower + "' i]",
            ".dx-texteditor-input[aria-label*='" + lower + "' i]",
            ".dx-texteditor-input[name*='" + lower + "' i]",
            ".dx-texteditor-input[id*='" + lower + "' i]"
        };

        for (String selector : selectors) {
            Locator pick = pickFirstEditable(page.locator(selector), 40);
            if (pick != null) {
                return pick;
            }
        }

        return null;
    }

    private Locator findEscutaInicialFieldLabel(String fieldName) {
        String normalizedField = normalizeText(fieldName);
        String[] selectors = {
            "label",
            "span",
            "div",
            "td"
        };

        for (String selector : selectors) {
            Locator candidates = page.locator(selector);
            int count;
            try {
                count = Math.min(candidates.count(), 250);
            } catch (Exception e) {
                continue;
            }

            for (int i = 0; i < count; i++) {
                try {
                    Locator candidate = candidates.nth(i);
                    if (!isVisible(candidate)) continue;

                    String text = candidate.innerText();
                    String normalized = normalizeText(text);
                    if (normalized.isEmpty()) continue;
                    if (!normalized.equals(normalizedField) && !normalized.contains(normalizedField)) continue;
                    if (normalized.length() > normalizedField.length() + 24) continue;

                    BoundingBox box = candidate.boundingBox();
                    if (box == null) continue;
                    if (box.y < 150) continue;

                    return candidate;
                } catch (Exception ignored) {}
            }
        }

        return null;
    }

    private Locator findEditableAfterLabel(Locator label, String fieldName) {
        boolean multiline = normalizeText(fieldName).contains("subjetivo");
        String[] selectors = multiline ? new String[] {
            "xpath=following::textarea[1]",
            "xpath=following::*[@role='textbox'][1]",
            "xpath=following::*[@contenteditable='true'][1]",
            "xpath=following::input[1]"
        } : new String[] {
            "xpath=following::input[1]",
            "xpath=following::textarea[1]",
            "xpath=following::*[@role='textbox'][1]",
            "xpath=following::*[@contenteditable='true'][1]"
        };

        BoundingBox labelBox = null;
        try {
            labelBox = label.boundingBox();
        } catch (Exception ignored) {}

        for (String selector : selectors) {
            try {
                Locator candidate = label.locator(selector).first();
                if (!isVisible(candidate)) continue;

                if (labelBox != null) {
                    BoundingBox fieldBox = candidate.boundingBox();
                    if (fieldBox != null && fieldBox.y < labelBox.y - 10) continue;
                    if (fieldBox != null && fieldBox.y > labelBox.y + 450) continue;
                }

                return candidate;
            } catch (Exception ignored) {}
        }

        return null;
    }

    private Locator findEscutaInicialInputByContext(String normalizedField) {
        Locator candidates = page.locator(
            "input, textarea, [role='textbox'], [contenteditable='true'], .dx-texteditor-input"
        );

        int count;
        try {
            count = Math.min(candidates.count(), 250);
        } catch (Exception e) {
            return null;
        }

        for (int i = 0; i < count; i++) {
            try {
                Locator candidate = candidates.nth(i);
                if (!isVisible(candidate)) continue;

                String context = String.valueOf(candidate.evaluate(
                    "el => { let n = el; for (let i = 0; n && i < 8; i++, n = n.parentElement) {" +
                        " const t = (n.innerText || '').replace(/\\s+/g, ' ').trim();" +
                        " if (t) return t;" +
                    " } return ''; }"
                ));
                String normalizedContext = normalizeText(context);
                if (!normalizedContext.contains(normalizedField)) continue;
                if (normalizedContext.contains("buscar menus")) continue;
                if (normalizedContext.contains("folha de rosto") && !normalizedContext.contains("subjetivo")) continue;

                return candidate;
            } catch (Exception ignored) {}
        }

        return null;
    }

    private Locator pickFirstEditable(Locator candidates, int maxCount) {
        int count;
        try {
            count = Math.min(candidates.count(), maxCount);
        } catch (Exception e) {
            return null;
        }

        for (int i = 0; i < count; i++) {
            try {
                Locator candidate = candidates.nth(i);
                if (isVisible(candidate)) {
                    return candidate;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private void fillLocator(Locator field, String value, String description) throws Exception {
        try {
            field.scrollIntoViewIfNeeded();
        } catch (Exception ignored) {}

        try {
            field.click(new Locator.ClickOptions().setTimeout(8000));
        } catch (Exception e) {
            System.out.println("[TestExecutor] Clique normal no campo " + description + " falhou, tentando forcar: " + e.getMessage());
            field.click(new Locator.ClickOptions().setTimeout(8000).setForce(true));
        }

        try {
            field.fill(value, new Locator.FillOptions().setTimeout(8000));
            try {
                field.press("Tab");
            } catch (Exception ignored) {}
            return;
        } catch (Exception e) {
            System.out.println("[TestExecutor] fill() falhou no campo " + description + ", tentando teclado: " + e.getMessage());
        }

        field.press("Control+A");
        field.type(value);
        try {
            field.press("Tab");
        } catch (Exception ignored) {}
    }

    private boolean fieldContainsValue(Locator field, String expected) {
        try {
            String actual;
            try {
                actual = field.inputValue();
            } catch (Exception ignored) {
                actual = field.innerText();
            }
            return normalizeText(actual).contains(normalizeText(expected));
        } catch (Exception e) {
            return false;
        }
    }

    private void executarValidacaoMensagemSucesso() throws Exception {
        long start = System.currentTimeMillis();
        long timeoutMs = 15000;

        while (System.currentTimeMillis() - start < timeoutMs) {
            String visibleMessage = findVisibleOutcomeMessage();
            String normalized = normalizeText(visibleMessage);

            if (normalized.contains("erro") || normalized.contains("falha") || normalized.contains("obrigatorio")) {
                throw new RuntimeException("Mensagem de erro encontrada ao validar sucesso: " + visibleMessage);
            }

            if (normalized.contains("sucesso") ||
                normalized.contains("com sucesso") ||
                normalized.contains("salvo") ||
                normalized.contains("finalizado") ||
                normalized.contains("concluido")) {
                System.out.println("[TestExecutor] Mensagem de sucesso validada: " + visibleMessage);
                return;
            }

            page.waitForTimeout(300);
        }

        throw new RuntimeException("Mensagem de sucesso nao encontrada dentro do timeout");
    }

    private String findVisibleOutcomeMessage() {
        try {
            Object result = page.evaluate(
                "() => {" +
                    "const visible = el => {" +
                        "const r = el.getBoundingClientRect();" +
                        "const s = window.getComputedStyle(el);" +
                        "return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';" +
                    "};" +
                    "const selectors = ['.dx-toast-message', '.dx-toast-content', '[role=alert]', '[aria-live]', '.toast', '.alert', 'div', 'span'];" +
                    "const nodes = [];" +
                    "for (const selector of selectors) nodes.push(...document.querySelectorAll(selector));" +
                    "for (const node of nodes) {" +
                        "if (!visible(node)) continue;" +
                        "const text = (node.innerText || node.textContent || '').replace(/\\s+/g, ' ').trim();" +
                        "const lower = text.normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').toLowerCase();" +
                        "if (!text || text.length < 3 || text.length > 260) continue;" +
                        "if (lower.includes('erro') || lower.includes('falha') || lower.includes('obrigatorio') || lower.includes('sucesso') || lower.includes('salvo') || lower.includes('finalizado') || lower.includes('concluido')) return text;" +
                    "}" +
                    "return '';" +
                "}"
            );
            return result == null ? "" : String.valueOf(result);
        } catch (Exception e) {
            return "";
        }
    }

    private Locator findEscutaInicialTab() {
        String[] selectors = {
            "[role='tab']:has-text('Escuta Inicial')",
            "[role='button'][aria-label='Escuta Inicial']",
            "[role='button']:has-text('Escuta Inicial')",
            "button:has-text('Escuta Inicial')",
            "[class*='tab']:has-text('Escuta Inicial')",
            "span:has-text('Escuta Inicial')",
            "div:has-text('Escuta Inicial')"
        };

        for (String selector : selectors) {
            Locator pick = pickEscutaInicialTabCandidate(page.locator(selector), true);
            if (pick != null) {
                return pick;
            }
        }

        for (String selector : selectors) {
            Locator pick = pickEscutaInicialTabCandidate(page.locator(selector), false);
            if (pick != null) {
                return pick;
            }
        }

        return null;
    }

    private Locator pickEscutaInicialTabCandidate(Locator candidates, boolean requireTabBarPosition) {
        int count;
        try {
            count = Math.min(candidates.count(), 120);
        } catch (Exception e) {
            return null;
        }

        for (int i = 0; i < count; i++) {
            try {
                Locator candidate = candidates.nth(i);
                if (!isVisible(candidate)) continue;

                String text = "";
                try {
                    text = candidate.innerText();
                } catch (Exception ignored) {}

                String normalized = normalizeText(text);
                String ariaLabel = "";
                try {
                    ariaLabel = normalizeText(candidate.getAttribute("aria-label"));
                } catch (Exception ignored) {}

                boolean isEscutaInicial = normalized.contains("escuta inicial") || ariaLabel.equals("escuta inicial");
                if (!isEscutaInicial) continue;
                if (normalized.length() > 80) continue;
                if (normalized.contains("fechar tab")) continue;

                BoundingBox box = null;
                try {
                    box = candidate.boundingBox();
                } catch (Exception ignored) {}

                if (requireTabBarPosition) {
                    if (box == null) continue;
                    if (box.y < 100 || box.y > 260) continue;
                }

                return candidate;
            } catch (Exception ignored) {}
        }

        return null;
    }

    private void executarAbrirPopupEsus(String section) throws Exception {
        if (!clickInsertButtonInSectionScript(section)) {
            throw new RuntimeException("Botao Inserir da secao e-SUS nao encontrado: " + section);
        }

        String expected = normalizeText(section).contains("motivacao") ? "inserindo motivacao" : "inserindo procedimentos";
        if (!waitForPopupContaining(expected, 8000)) {
            throw new RuntimeException("Popup e-SUS nao abriu para a secao: " + section);
        }
    }

    private void executarSelecaoPopupEsus(String passo) throws Exception {
        String normalized = normalizeText(passo);
        String field = normalized.contains("procedimento") && !normalized.contains("cid10") ? "Procedimento" : "CID10";

        if (!isEsusPopupVisible()) {
            throw new RuntimeException("Popup e-SUS nao esta visivel para selecionar " + field);
        }

        boolean clicked = normalizeText(field).contains("cid10") && isProcedurePopupVisible()
            ? (clickLastCid10FieldInPopupScript() || clickPopupFieldByLabelScript(field))
            : clickPopupFieldByLabelScript(field);
        if (!clicked) {
            throw new RuntimeException("Campo " + field + " nao encontrado dentro do popup e-SUS");
        }

        page.waitForTimeout(700);
        if (!selectFirstValidOverlayOptionScript(field)) {
            page.keyboard().type("A");
            page.waitForTimeout(1200);
            if (!selectFirstValidOverlayOptionScript(field)) {
                page.keyboard().press("Alt+ArrowDown");
                page.waitForTimeout(700);
                page.keyboard().press("ArrowDown");
                page.waitForTimeout(250);
                page.keyboard().press("Enter");
                page.waitForTimeout(800);
            }
        }

        page.waitForTimeout(500);
        if (!isPopupFieldSelectedScript(field)) {
            throw new RuntimeException("Campo " + field + " do popup e-SUS nao ficou selecionado");
        }
    }

    private void executarSalvarFecharPopupEsus() throws Exception {
        if (!isEsusPopupVisible()) {
            throw new RuntimeException("Popup e-SUS nao esta visivel para Salvar e Fechar");
        }

        preencherCid10ObrigatorioPopupEsusSeNecessario();

        if (!clickPopupButtonByTextScript("Salvar e Fechar")) {
            throw new RuntimeException("Botao Salvar e Fechar nao encontrado no popup e-SUS");
        }
        waitForEsusPopupToClose(3500);

        if (isEsusPopupVisible() && hasRequiredMessage("cid10")) {
            preencherCid10ObrigatorioPopupEsusSeNecessario();
            if (!clickPopupButtonByTextScript("Salvar e Fechar")) {
                throw new RuntimeException("Botao Salvar e Fechar nao encontrado no popup e-SUS apos preencher CID10");
            }
            waitForEsusPopupToClose(4000);
        }

        if (isEsusPopupVisible()) {
            tryCloseEsusPopupFallback();
            waitForEsusPopupToClose(4000);
        }

        if (isEsusPopupVisible()) {
            throw new RuntimeException("Popup e-SUS permaneceu aberto apos Salvar e Fechar");
        }
    }

    private void waitForEsusPopupToClose(int timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (!isEsusPopupVisible()) return;
            page.waitForTimeout(250);
        }
    }

    private void tryCloseEsusPopupFallback() {
        try {
            page.keyboard().press("Escape");
        } catch (Exception ignored) {}
        page.waitForTimeout(500);
        if (!isEsusPopupVisible()) return;

        clickPopupButtonByTextScript("Cancelar");
        page.waitForTimeout(700);
        if (!isEsusPopupVisible()) return;

        clickPopupCloseIconScript();
        page.waitForTimeout(700);
    }

    private boolean clickPopupCloseIconScript() {
        try {
            Object result = page.evaluate(
                "() => {" +
                    "const norm = s => (s || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').replace(/\\s+/g, ' ').trim().toLowerCase();" +
                    "const visible = el => {" +
                        "const r = el.getBoundingClientRect();" +
                        "const s = window.getComputedStyle(el);" +
                        "return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';" +
                    "};" +
                    "const roots = Array.from(document.querySelectorAll('.dx-overlay-content, [role=dialog], .modal, [class*=popup]')).filter(visible).reverse();" +
                    "for (const root of (roots.length ? roots : [document.body])) {" +
                        "const buttons = Array.from(root.querySelectorAll('button, [role=button], [aria-label], [title]')).filter(visible);" +
                        "for (const btn of buttons) {" +
                            "const t = norm(btn.getAttribute('aria-label') || btn.getAttribute('title') || btn.innerText || btn.textContent);" +
                            "if (!t) continue;" +
                            "if (t === 'x' || t === 'fechar' || t.includes('fechar') || t.includes('close')) {" +
                                "btn.scrollIntoView({block:'center', inline:'nearest'});" +
                                "btn.click();" +
                                "return true;" +
                            "}" +
                        "}" +
                    "}" +
                    "return false;" +
                "}"
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void preencherCid10ObrigatorioPopupEsusSeNecessario() {
        if (!hasRequiredMessage("cid10") && !isProcedurePopupVisible()) {
            return;
        }

        boolean clicked = clickLastCid10FieldInPopupScript() || clickPopupFieldByLabelScript("CID10");
        if (!clicked) {
            return;
        }

        page.waitForTimeout(700);
        if (!selectFirstValidOverlayOptionScript("CID10")) {
            page.keyboard().type("A");
            page.waitForTimeout(1200);
            if (!selectFirstValidOverlayOptionScript("CID10")) {
                page.keyboard().press("Alt+ArrowDown");
                page.waitForTimeout(700);
                page.keyboard().press("ArrowDown");
                page.waitForTimeout(250);
                page.keyboard().press("Enter");
                page.waitForTimeout(800);
            }
        }
    }

    private boolean clickInsertButtonInSectionScript(String section) {
        try {
            Object result = page.evaluate(
                "(section) => {" +
                    "const wanted = section.normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').toLowerCase();" +
                    "const norm = s => (s || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').replace(/\\s+/g, ' ').trim().toLowerCase();" +
                    "const visible = el => {" +
                        "const r = el.getBoundingClientRect();" +
                        "const s = window.getComputedStyle(el);" +
                        "return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';" +
                    "};" +
                    "const buttons = Array.from(document.querySelectorAll('[title=\"Inserir\"], [aria-label=\"Inserir\"], [role=button], button'))" +
                        ".filter(el => visible(el) && (norm(el.getAttribute('title')) === 'inserir' || norm(el.getAttribute('aria-label')) === 'inserir' || norm(el.innerText) === 'inserir'));" +
                    "for (const btn of buttons) {" +
                        "let scope = btn;" +
                        "for (let i = 0; scope && i < 7; i++, scope = scope.parentElement) {" +
                            "const text = norm(scope.innerText || '');" +
                            "if (text.includes(wanted) && text.includes('inserir')) {" +
                                "btn.scrollIntoView({block:'center', inline:'nearest'});" +
                                "btn.click();" +
                                "return true;" +
                            "}" +
                        "}" +
                    "}" +
                    "return false;" +
                "}",
                section
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            System.out.println("[TestExecutor] Clique no Inserir e-SUS falhou: " + e.getMessage());
            return false;
        }
    }

    private boolean clickPopupFieldByLabelScript(String label) {
        try {
            Object result = page.evaluate(
                "(label) => {" +
                    "const wanted = label.normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').toLowerCase();" +
                    "const norm = s => (s || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').replace(/\\s+/g, ' ').trim().toLowerCase();" +
                    "const visible = el => {" +
                        "const r = el.getBoundingClientRect();" +
                        "const s = window.getComputedStyle(el);" +
                        "return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';" +
                    "};" +
                    "const overlays = Array.from(document.querySelectorAll('.dx-overlay-content, [role=dialog], .modal, [class*=popup]')).filter(visible).reverse();" +
                    "const scopes = overlays.length ? overlays : [document.body];" +
                    "const targets = '.dx-dropdowneditor-button, .dx-dropdowneditor-input-wrapper, .dx-texteditor-input, .dx-texteditor-container, [role=combobox], input';" +
                    "for (const root of scopes) {" +
                        "const labels = Array.from(root.querySelectorAll('label, div, span, td')).filter(el => visible(el) && norm(el.innerText || el.textContent).includes(wanted));" +
                        "for (const labelEl of labels) {" +
                            "let scope = labelEl;" +
                            "for (let i = 0; scope && i < 8; i++, scope = scope.parentElement) {" +
                                "const target = Array.from(scope.querySelectorAll(targets)).find(visible);" +
                                "if (target) {" +
                                    "target.scrollIntoView({block:'center', inline:'nearest'});" +
                                    "target.click();" +
                                    "return true;" +
                                "}" +
                            "}" +
                            "const next = labelEl.parentElement ? Array.from(labelEl.parentElement.querySelectorAll(targets)).find(visible) : null;" +
                            "if (next) { next.scrollIntoView({block:'center', inline:'nearest'}); next.click(); return true; }" +
                        "}" +
                    "}" +
                    "return false;" +
                "}",
                label
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            System.out.println("[TestExecutor] Clique no campo do popup e-SUS falhou: " + e.getMessage());
            return false;
        }
    }

    private boolean clickLastCid10FieldInPopupScript() {
        try {
            Object result = page.evaluate(
                "() => {" +
                    "const norm = s => (s || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').replace(/\\s+/g, ' ').trim().toLowerCase();" +
                    "const visible = el => {" +
                        "const r = el.getBoundingClientRect();" +
                        "const s = window.getComputedStyle(el);" +
                        "return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';" +
                    "};" +
                    "const roots = Array.from(document.querySelectorAll('.dx-overlay-content, [role=dialog], .modal, [class*=popup]'))" +
                        ".filter(el => visible(el) && /procedimento|motivacao|cid10/.test(norm(el.innerText || el.textContent))).reverse();" +
                    "const root = roots[0] || document.body;" +
                    "const targets = '.dx-dropdowneditor-button, .dx-dropdowneditor-input-wrapper, .dx-texteditor-input, .dx-texteditor-container, [role=combobox], input';" +
                    "const matches = [];" +
                    "for (const label of Array.from(root.querySelectorAll('label, div, span, td')).filter(visible)) {" +
                        "if (!norm(label.innerText || label.textContent).includes('cid10')) continue;" +
                        "let scope = label;" +
                        "for (let i = 0; scope && i < 8; i++, scope = scope.parentElement) {" +
                            "const target = Array.from(scope.querySelectorAll(targets)).find(visible);" +
                            "if (target) {" +
                                "matches.push(target);" +
                                "break;" +
                            "}" +
                        "}" +
                    "}" +
                    "const target = matches[matches.length - 1];" +
                    "if (!target) return false;" +
                    "target.scrollIntoView({block:'center', inline:'nearest'});" +
                    "target.click();" +
                    "return true;" +
                "}"
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            System.out.println("[TestExecutor] Clique no ultimo campo CID10 do popup falhou: " + e.getMessage());
            return false;
        }
    }

    private boolean isPopupFieldSelectedScript(String field) {
        try {
            Object result = page.evaluate(
                "(field) => {" +
                    "const wanted = (field || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').toLowerCase();" +
                    "const norm = s => (s || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').replace(/\\s+/g, ' ').trim().toLowerCase();" +
                    "const visible = el => {" +
                        "const r = el.getBoundingClientRect();" +
                        "const s = window.getComputedStyle(el);" +
                        "return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';" +
                    "};" +
                    "const roots = Array.from(document.querySelectorAll('.dx-overlay-content, [role=dialog], .modal, [class*=popup]'))" +
                        ".filter(el => visible(el) && /inserindo motivacao|inserindo procedimentos|procedimento|motivacao/.test(norm(el.innerText || el.textContent))).reverse();" +
                    "const fields = wanted.includes('cid10') ? ['cid10'] : ['procedimento'];" +
                    "for (const root of roots) {" +
                        "const nodes = Array.from(root.querySelectorAll('label, div, span, td')).filter(visible);" +
                        "for (const node of nodes) {" +
                            "const text = norm(node.innerText || node.textContent);" +
                            "if (!fields.some(f => text.includes(f))) continue;" +
                            "let scope = node;" +
                            "for (let i = 0; scope && i < 8; i++, scope = scope.parentElement) {" +
                                "const input = Array.from(scope.querySelectorAll('input, textarea, .dx-texteditor-input, [role=combobox]')).find(visible);" +
                                "const value = input ? norm(input.value || input.getAttribute('value') || input.innerText || '') : '';" +
                                "const scopeText = norm(scope.innerText || scope.textContent);" +
                                "if (value && !value.includes('selecione') && value !== 'cid10' && value !== 'procedimento') return true;" +
                                "if (scopeText.includes(' - ') || /^[a-z][0-9]/.test(scopeText)) return true;" +
                            "}" +
                        "}" +
                    "}" +
                    "return false;" +
                "}",
                field
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            System.out.println("[TestExecutor] Validacao de campo do popup e-SUS falhou: " + e.getMessage());
            return false;
        }
    }

    private boolean selectFirstValidOverlayOptionScript(String field) {
        try {
            Object result = page.evaluate(
                "(field) => {" +
                    "const wanted = (field || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').toLowerCase();" +
                    "const norm = s => (s || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').replace(/\\s+/g, ' ').trim().toLowerCase();" +
                    "const visible = el => {" +
                        "const r = el.getBoundingClientRect();" +
                        "const s = window.getComputedStyle(el);" +
                        "return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';" +
                    "};" +
                    "const bad = t => !t || t.length < 2 || t.length > 220 || ['selecione','selecione ...','cid10','procedimentos','procedimento','consulta medica','escuta inicial','nao urgente','fundo municipal de saude de urussanga sc'].includes(t) || t.includes('buscar menus') || t.includes('finalizar atendimento') || t.includes('salvar') || t.includes('cancelar') || t.includes('favoritos') || t.includes('administracao') || t.includes('atendimento da atencao primaria') || t.includes('novidades da versao');" +
                    "const okForField = t => {" +
                        "if (bad(t)) return false;" +
                        "if (wanted.includes('cid10')) return /^[a-z][0-9]/.test(t) || /^[a-z][0-9]{2}/.test(t) || t.includes(' - ');" +
                        "return /\\d/.test(t) || t.includes(' - ') || t.length > 6;" +
                    "};" +
                    "const selectors = ['.dx-dropdownlist-popup-wrapper .dx-list-item', '.dx-selectbox-popup-wrapper .dx-list-item', '.dx-dropdownlist-popup-wrapper [role=option]', '.dx-selectbox-popup-wrapper [role=option]', '[role=listbox] [role=option]'];" +
                    "for (const selector of selectors) {" +
                        "const options = Array.from(document.querySelectorAll(selector)).filter(visible);" +
                        "for (const option of options) {" +
                            "const t = norm(option.innerText || option.textContent);" +
                            "if (!okForField(t)) continue;" +
                            "option.scrollIntoView({block:'center', inline:'nearest'});" +
                            "option.click();" +
                            "return true;" +
                        "}" +
                    "}" +
                    "return false;" +
                "}",
                field
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            System.out.println("[TestExecutor] Selecao de opcao e-SUS falhou: " + e.getMessage());
            return false;
        }
    }

    private boolean clickPopupButtonByTextScript(String text) {
        try {
            Object result = page.evaluate(
                "(text) => {" +
                    "const wanted = text.normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').toLowerCase();" +
                    "const norm = s => (s || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').replace(/\\s+/g, ' ').trim().toLowerCase();" +
                    "const visible = el => {" +
                        "const r = el.getBoundingClientRect();" +
                        "const s = window.getComputedStyle(el);" +
                        "return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';" +
                    "};" +
                    "const roots = Array.from(document.querySelectorAll('.dx-overlay-content, [role=dialog], .modal, [class*=popup]')).filter(visible).reverse();" +
                    "for (const root of (roots.length ? roots : [document.body])) {" +
                        "const buttons = Array.from(root.querySelectorAll('button, [role=button], [title], [aria-label]')).filter(visible);" +
                        "for (const btn of buttons) {" +
                            "const t = norm(btn.innerText || btn.textContent || btn.getAttribute('title') || btn.getAttribute('aria-label'));" +
                            "if (t === wanted || t.includes(wanted)) {" +
                                "btn.scrollIntoView({block:'center', inline:'nearest'});" +
                                "btn.click();" +
                                "return true;" +
                            "}" +
                        "}" +
                    "}" +
                    "return false;" +
                "}",
                text
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            System.out.println("[TestExecutor] Clique em botao do popup e-SUS falhou: " + e.getMessage());
            return false;
        }
    }

    private boolean isEsusPopupVisible() {
        return waitForPopupContaining("inserindo motivacao", 250) ||
            waitForPopupContaining("inserindo procedimentos", 250) ||
            isProcedurePopupVisible();
    }

    private boolean isProcedurePopupVisible() {
        try {
            Object result = page.evaluate(
                "() => {" +
                    "const norm = s => (s || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').replace(/\\s+/g, ' ').trim().toLowerCase();" +
                    "const visible = el => {" +
                        "const r = el.getBoundingClientRect();" +
                        "const s = window.getComputedStyle(el);" +
                        "return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';" +
                    "};" +
                    "return Array.from(document.querySelectorAll('.dx-overlay-content, [role=dialog], .modal, [class*=popup]'))" +
                        ".some(el => visible(el) && norm(el.innerText || el.textContent).includes('procedimento') && norm(el.innerText || el.textContent).includes('salvar e fechar'));" +
                "}"
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean waitForPopupContaining(String normalizedText, int timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                Object result = page.evaluate(
                    "(wanted) => {" +
                        "const norm = s => (s || '').normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').replace(/\\s+/g, ' ').trim().toLowerCase();" +
                        "const visible = el => {" +
                            "const r = el.getBoundingClientRect();" +
                            "const s = window.getComputedStyle(el);" +
                            "return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';" +
                        "};" +
                        "return Array.from(document.querySelectorAll('.dx-overlay-content, [role=dialog], .modal, [class*=popup]'))" +
                            ".some(el => visible(el) && norm(el.innerText || el.textContent).includes(wanted));" +
                    "}",
                    normalizedText
                );
                if (Boolean.TRUE.equals(result)) return true;
            } catch (Exception ignored) {}
            page.waitForTimeout(250);
        }
        return false;
    }

    private boolean hasRequiredMessage(String normalizedField) {
        try {
            String body = normalizeText(page.locator("body").innerText())
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
            return body.contains(normalizedField) && body.contains("preenchimento obrigatorio");
        } catch (Exception e) {
            return false;
        }
    }

    private void executarAbrirPopupCiap() throws Exception {
        Locator botaoInserir = findCiapInsertButton();
        if (botaoInserir == null) {
            throw new RuntimeException("Botao Inserir do campo CIAP nao encontrado");
        }

        clickLocator(botaoInserir, "Inserir CIAP");
        if (!waitForCiapPopup(8000)) {
            throw new RuntimeException("Popup Inserindo CIAP nao abriu apos clicar em Inserir");
        }
    }

    private Locator findCiapInsertButton() {
        String[] selectors = {
            "[title='Inserir']",
            "[aria-label='Inserir']",
            "[role='button']:has-text('Inserir')",
            "button:has-text('Inserir')"
        };

        for (String selector : selectors) {
            Locator pick = pickCiapInsertCandidate(page.locator(selector));
            if (pick != null) {
                return pick;
            }
        }

        return null;
    }

    private Locator pickCiapInsertCandidate(Locator candidates) {
        int count;
        try {
            count = Math.min(candidates.count(), 120);
        } catch (Exception e) {
            return null;
        }

        for (int i = 0; i < count; i++) {
            try {
                Locator candidate = candidates.nth(i);
                if (!isVisible(candidate)) continue;

                String context = "";
                try {
                    context = String.valueOf(candidate.evaluate(
                        "el => { let n = el; for (let i = 0; n && i < 7; i++, n = n.parentElement) {" +
                            " const t = (n.innerText || '').replace(/\\s+/g, ' ').trim();" +
                            " if (t.toLowerCase().includes('ciap')) return t;" +
                        " } return ''; }"
                    ));
                } catch (Exception ignored) {}

                String normalized = normalizeText(context);
                if (!normalized.contains("ciap")) continue;
                if (normalized.contains("problemas conhecidos")) continue;
                if (normalized.contains("lembretes")) continue;
                if (normalized.contains("alergias")) continue;

                return candidate;
            } catch (Exception ignored) {}
        }

        return null;
    }

    private void executarSelecaoCiapSalvarFechar() throws Exception {
        if (!waitForCiapPopup(5000)) {
            throw new RuntimeException("Popup Inserindo CIAP nao esta visivel para selecionar item");
        }

        Locator campoCiap = findCiapPopupField();
        if (campoCiap == null) {
            if (!clickCiapFieldByScript()) {
                throw new RuntimeException("Campo CIAP nao encontrado dentro do popup");
            }
        } else {
            System.out.println("[TestExecutor] Abrindo lista do campo CIAP...");
            clickLocator(campoCiap, "Campo CIAP do popup");
        }

        page.waitForTimeout(1000);

        boolean selected = false;
        Locator opcao = findFirstValidCiapOption();
        if (opcao != null) {
            selected = clickCiapOption(opcao);
        }

        if (!selected) {
            selected = selectCiapWithKeyboard(campoCiap);
        }

        if (!selected) {
            if (campoCiap != null) {
                try {
                    campoCiap.fill("A");
                } catch (Exception e) {
                    try {
                        campoCiap.press("A");
                    } catch (Exception ignored) {}
                }
            } else {
                page.keyboard().type("A");
            }
            page.waitForTimeout(1200);
            opcao = findFirstValidCiapOption();
            if (opcao != null) {
                selected = clickCiapOption(opcao);
            }
        }

        if (!selected && !isCiapValueSelected()) {
            System.out.println("[TestExecutor] Nenhuma opcao visivel de CIAP foi encontrada; tentando salvar para validar selecao do componente");
        }

        Locator salvarFechar = findButtonByText("Salvar e Fechar", findCiapPopupScope());
        if (salvarFechar == null) {
            throw new RuntimeException("Botao Salvar e Fechar nao encontrado no popup de CIAP");
        }

        clickLocator(salvarFechar, "Salvar e Fechar CIAP");
        if (!waitForCiapPopupToClose(10000)) {
            throw new RuntimeException("Popup Inserindo CIAP nao fechou apos Salvar e Fechar; CIAP provavelmente nao foi selecionado");
        }
    }

    private boolean clickCiapOption(Locator opcao) throws Exception {
        String textoOpcao = "";
        try {
            textoOpcao = opcao.innerText().replace("\n", " ").trim();
        } catch (Exception ignored) {}
        System.out.println("[TestExecutor] Selecionando CIAP disponivel: " + textoOpcao);
        clickLocator(opcao, "Opcao CIAP");
        page.waitForTimeout(800);
        return true;
    }

    private boolean selectCiapWithKeyboard(Locator campoCiap) {
        try {
            System.out.println("[TestExecutor] Tentando selecionar CIAP via teclado...");
            if (campoCiap != null) {
                clickLocator(campoCiap, "Campo CIAP do popup");
            } else {
                clickCiapFieldByScript();
            }
            page.waitForTimeout(300);
            page.keyboard().press("Alt+ArrowDown");
            page.waitForTimeout(700);

            Locator opcao = findFirstValidCiapOption();
            if (opcao != null) {
                return clickCiapOption(opcao);
            }

            page.keyboard().press("ArrowDown");
            page.waitForTimeout(250);
            page.keyboard().press("Enter");
            page.waitForTimeout(800);
            return isCiapValueSelected();
        } catch (Exception e) {
            System.out.println("[TestExecutor] Selecao de CIAP via teclado falhou: " + e.getMessage());
            return false;
        }
    }

    private Locator findCiapPopupScope() {
        String[] selectors = {
            ".dx-popup-content:has-text('Inserindo CIAP')",
            ".dx-popup-wrapper:has-text('Inserindo CIAP') .dx-overlay-content",
            "[role='dialog']:has-text('Inserindo CIAP')",
            ".modal:has-text('Inserindo CIAP')",
            "[class*='modal']:has-text('Inserindo CIAP')",
            "[class*='dialog']:has-text('Inserindo CIAP')",
            "[class*='popup']:has-text('Inserindo CIAP')",
            ".dx-overlay-content:has-text('Inserindo CIAP')",
            "div:has-text('Inserindo CIAP')"
        };

        for (String selector : selectors) {
            Locator pick = pickVisiblePopupScope(page.locator(selector));
            if (pick != null) {
                return pick;
            }
        }

        return page.locator("body");
    }

    private Locator pickVisiblePopupScope(Locator candidates) {
        int count;
        try {
            count = Math.min(candidates.count(), 40);
        } catch (Exception e) {
            return null;
        }

        Locator fallback = null;
        double bestArea = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            try {
                Locator candidate = candidates.nth(i);
                if (!isVisible(candidate)) continue;

                String normalized = normalizeText(candidate.innerText());
                if (!normalized.contains("inserindo ciap")) continue;
                if (!normalized.contains("salvar e fechar")) continue;

                BoundingBox box = candidate.boundingBox();
                if (box == null) continue;
                if (box.width < 240 || box.width > 1400) continue;
                if (box.height < 70 || box.height > 1000) continue;

                double area = box.width * box.height;
                if (area < bestArea) {
                    fallback = candidate;
                    bestArea = area;
                }
            } catch (Exception ignored) {}
        }
        return fallback;
    }

    private Locator findCiapPopupField() {
        Locator scope = findCiapPopupScope();
        String[] selectors = {
            "xpath=.//*[normalize-space()='Ciap' or normalize-space()='CIAP']/ancestor::*[contains(@class,'dx-field-item') or contains(@class,'dx-form-group') or contains(@class,'form-group') or contains(@class,'dx-item')][1]//*[contains(@class,'dx-dropdowneditor-button') or contains(@class,'dx-texteditor-input') or contains(@class,'dx-texteditor-container') or @role='combobox' or self::input][1]",
            "xpath=.//*[normalize-space()='Ciap' or normalize-space()='CIAP']/following::*[contains(@class,'dx-dropdowneditor-button') or contains(@class,'dx-texteditor-input') or contains(@class,'dx-texteditor-container') or @role='combobox' or self::input][1]",
            "input[placeholder*='Ciap'], input[placeholder*='CIAP']",
            ".dx-dropdowneditor-button",
            ".dx-dropdowneditor-input-wrapper",
            "[role='combobox']:has-text('Ciap'), [role='combobox']:has-text('CIAP')",
            "[role='button']:has-text('Ciap'), [role='button']:has-text('CIAP')",
            "[class*='select']:has-text('Ciap'), [class*='select']:has-text('CIAP')",
            "[class*='texteditor']:has-text('Ciap'), [class*='texteditor']:has-text('CIAP')",
            ".dx-texteditor-input",
            ".dx-texteditor-container"
        };

        for (String selector : selectors) {
            Locator pick = pickFirstVisibleSmall(scope.locator(selector), 120);
            if (pick != null) {
                return pick;
            }
        }

        return null;
    }

    private Locator findFirstValidCiapOption() {
        String[] selectors = {
            ".dx-dropdownlist-popup-wrapper .dx-list-item",
            ".dx-dropdownlist-popup-wrapper .dx-item",
            ".dx-selectbox-popup-wrapper .dx-list-item",
            ".dx-list .dx-list-item",
            ".dx-scrollview-content .dx-list-item",
            ".dx-scrollview-content .dx-item",
            ".dx-item-content",
            ".dx-overlay-content .dx-list-item",
            ".dx-overlay-content [role='option']",
            ".dx-overlay-content .dx-item",
            "[role='listbox'] [role='option']",
            "[class*='dropdown'] [class*='item']",
            "[class*='popover'] [class*='item']",
            "[class*='select-content'] [role='option']"
        };

        for (String selector : selectors) {
            Locator option = pickFirstValidCiapOption(page.locator(selector));
            if (option != null) {
                return option;
            }
        }

        return null;
    }

    private Locator pickFirstValidCiapOption(Locator candidates) {
        int count;
        try {
            count = Math.min(candidates.count(), 180);
        } catch (Exception e) {
            return null;
        }

        for (int i = 0; i < count; i++) {
            try {
                Locator candidate = candidates.nth(i);
                if (!isVisible(candidate)) continue;

                String text = candidate.innerText();
                String normalized = normalizeText(text);
                if (!isValidCiapOption(normalized)) continue;

                return candidate;
            } catch (Exception ignored) {}
        }

        return null;
    }

    private boolean isValidCiapOption(String normalized) {
        if (normalized == null || normalized.isEmpty()) return false;
        if (normalized.length() < 2 || normalized.length() > 240) return false;
        if (normalized.equals("ciap") || normalized.equals("selecione")) return false;
        if (normalized.contains("inserindo ciap")) return false;
        if (normalized.contains("salvar")) return false;
        if (normalized.contains("cancelar")) return false;
        if (normalized.contains("fechar")) return false;
        if (normalized.contains("buscar menus")) return false;
        if (normalized.contains("folha de rosto")) return false;
        if (normalized.contains("escuta inicial")) return false;
        if (normalized.contains("finalizar atendimento")) return false;
        return true;
    }

    private boolean clickCiapFieldByScript() {
        try {
            Object result = page.evaluate(
                "() => {" +
                    "const visible = el => {" +
                        "const r = el.getBoundingClientRect();" +
                        "const s = window.getComputedStyle(el);" +
                        "return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';" +
                    "};" +
                    "const labels = Array.from(document.querySelectorAll('label, div, span'))" +
                        ".filter(el => visible(el) && /^ciap$/i.test((el.innerText || el.textContent || '').trim()));" +
                    "for (const label of labels) {" +
                        "let scope = label;" +
                        "for (let i = 0; scope && i < 8; i++, scope = scope.parentElement) {" +
                            "const text = (scope.innerText || '').toLowerCase();" +
                            "if (!text.includes('ciap')) continue;" +
                            "const target = Array.from(scope.querySelectorAll('.dx-dropdowneditor-button, .dx-dropdowneditor-input-wrapper, .dx-texteditor-input, .dx-texteditor-container, [role=combobox], input')).find(visible);" +
                            "if (target) {" +
                                "target.click();" +
                                "return true;" +
                            "}" +
                        "}" +
                    "}" +
                    "return false;" +
                "}"
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            System.out.println("[TestExecutor] Clique JS no campo CIAP falhou: " + e.getMessage());
            return false;
        }
    }

    private boolean isCiapValueSelected() {
        try {
            Object result = page.evaluate(
                "() => {" +
                    "const visible = el => {" +
                        "const r = el.getBoundingClientRect();" +
                        "const s = window.getComputedStyle(el);" +
                        "return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';" +
                    "};" +
                    "const labels = Array.from(document.querySelectorAll('label, div, span'))" +
                        ".filter(el => visible(el) && /^ciap$/i.test((el.innerText || el.textContent || '').trim()));" +
                    "for (const label of labels) {" +
                        "let scope = label;" +
                        "for (let i = 0; scope && i < 8; i++, scope = scope.parentElement) {" +
                            "const text = (scope.innerText || '').replace(/\\s+/g, ' ').trim().toLowerCase();" +
                            "if (!text.includes('ciap')) continue;" +
                            "const input = Array.from(scope.querySelectorAll('input, .dx-texteditor-input')).find(visible);" +
                            "const value = input ? (input.value || input.getAttribute('value') || '').trim() : '';" +
                            "if (value && !/^selecione/i.test(value)) return true;" +
                            "if (text.includes('ciap') && !text.includes('selecione ...') && !text.includes('selecione')) return true;" +
                        "}" +
                    "}" +
                    "return false;" +
                "}"
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    private Locator findButtonByText(String text, Locator scope) {
        String[] selectors = {
            "button:has-text('" + text + "')",
            "[role='button']:has-text('" + text + "')",
            "[title='" + text + "']",
            "[aria-label='" + text + "']",
            "span:has-text('" + text + "')",
            "div:has-text('" + text + "')"
        };

        for (String selector : selectors) {
            Locator pick = pickFirstVisibleSmall(scope.locator(selector), 80);
            if (pick != null) {
                return pick;
            }
        }

        return null;
    }

    private void clickLocator(Locator locator, String description) throws Exception {
        try {
            locator.scrollIntoViewIfNeeded();
        } catch (Exception ignored) {}
        try {
            locator.click(new Locator.ClickOptions().setTimeout(8000));
        } catch (Exception e) {
            System.out.println("[TestExecutor] Clique normal falhou em " + description + ", tentando forcar: " + e.getMessage());
            locator.click(new Locator.ClickOptions().setTimeout(8000).setForce(true));
        }
    }

    private boolean waitForCiapPopup(int timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                Locator title = page.getByText("Inserindo CIAP", new Page.GetByTextOptions().setExact(false)).first();
                if (isVisible(title)) return true;
            } catch (Exception ignored) {}
            page.waitForTimeout(250);
        }
        return false;
    }

    private boolean waitForCiapPopupToClose(int timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (!waitForCiapPopup(250)) {
                return true;
            }
            page.waitForTimeout(250);
        }
        return !waitForCiapPopup(250);
    }

    private void executarAbrirPopupFinalizarAtendimento() throws Exception {
        if (waitForCiapPopup(300)) {
            throw new RuntimeException("Popup de CIAP ainda esta aberto; nao e seguro clicar em Finalizar Atendimento");
        }
        if (isEsusPopupVisible()) {
            throw new RuntimeException("Popup e-SUS ainda esta aberto; nao e seguro clicar em Finalizar Atendimento");
        }

        Locator botao = findFinalizarAtendimentoButton();
        if (botao == null) {
            throw new RuntimeException("Botao Finalizar Atendimento nao encontrado");
        }

        clickLocator(botao, "Finalizar Atendimento");
        if (!waitForFinalizationPopup(8000)) {
            throw new RuntimeException("Popup Editando Finalizar Atendimento nao abriu apos clicar em Finalizar Atendimento");
        }
        finalizationPopupArmed = true;
    }

    private Locator findFinalizarAtendimentoButton() {
        String[] selectors = {
            "[title='Finalizar Atendimento']",
            "[aria-label='Finalizar Atendimento']",
            "[role='button']:has-text('Finalizar Atendimento')",
            "button:has-text('Finalizar Atendimento')",
            "span:has-text('Finalizar Atendimento')"
        };

        for (String selector : selectors) {
            Locator pick = pickExactOrShortButton(page.locator(selector), "finalizar atendimento");
            if (pick != null) {
                return pick;
            }
        }

        return null;
    }

    private void executarMarcarConsultaMedicaFinalizacao() throws Exception {
        executarMarcarCheckboxFinalizacao("Consulta Medica");
    }

    private void executarMarcarCheckboxFinalizacao(String passo) throws Exception {
        String normalizedStep = normalizeText(passo);
        String label = normalizedStep.contains("alta do episodio") ? "Alta do episodio" : "Consulta Medica";

        if (!waitForFinalizationPopup(8000)) {
            throw new RuntimeException("Popup Editando Finalizar Atendimento nao esta visivel para marcar " + label);
        }
        finalizationPopupArmed = true;

        Locator checkbox = findFinalizationCheckbox(label);
        if (checkbox == null) {
            if (ensureFinalizationCheckboxCheckedByScript(label)) {
                page.waitForTimeout(500);
                return;
            }
            throw new RuntimeException("Checkbox " + label + " nao encontrado no popup de finalizacao");
        }

        try {
            checkbox.scrollIntoViewIfNeeded();
        } catch (Exception ignored) {}

        if (isFinalizationCheckboxCheckedByScript(label)) {
            System.out.println("[TestExecutor] Checkbox " + label + " ja estava marcado; mantendo estado");
            page.waitForTimeout(300);
            return;
        }

        try {
            checkbox.check(new Locator.CheckOptions().setTimeout(8000));
        } catch (Exception e) {
            System.out.println("[TestExecutor] check() falhou em " + label + ", usando ensure por script: " + e.getMessage());
            if (!ensureFinalizationCheckboxCheckedByScript(label)) {
                throw new RuntimeException("Nao foi possivel marcar checkbox " + label + " sem alternar estado");
            }
        }

        page.waitForTimeout(500);
        if (!isFinalizationCheckboxCheckedByScript(label)) {
            if (!ensureFinalizationCheckboxCheckedByScript(label)) {
                throw new RuntimeException("Checkbox " + label + " nao ficou marcado apos tentativa de marcacao");
            }
            page.waitForTimeout(500);
        }
    }

    private Locator findFinalizationCheckbox(String label) {
        String normalizedLabel = normalizeText(label);
        if (normalizedLabel.contains("consulta medica")) {
            return findConsultaMedicaCheckbox();
        }

        Locator scope = findFinalizationPopupScope();
        Locator byRoleText = pickFirstVisibleSmall(scope.locator("[role='checkbox']:has-text('Alta')"), 80);
        if (byRoleText != null) {
            return byRoleText;
        }

        Locator direct = pickFinalizationCheckbox(scope.locator("input[type='checkbox'], [role='checkbox'], [class*='checkbox']"), normalizedLabel);
        if (direct != null) {
            return direct;
        }

        String[] labelSelectors = {
            "label:has-text('Alta')",
            "div:has-text('Alta')",
            "span:has-text('Alta')"
        };

        for (String selector : labelSelectors) {
            Locator textNode = pickFinalizationLabel(scope.locator(selector), normalizedLabel);
            if (textNode == null) continue;

            try {
                Locator nearest = textNode.locator("xpath=ancestor-or-self::*[.//input[@type='checkbox']][1]//input[@type='checkbox']").first();
                if (isVisible(nearest)) {
                    return nearest;
                }
            } catch (Exception ignored) {}

            return textNode;
        }

        return null;
    }

    private Locator pickFinalizationCheckbox(Locator candidates, String normalizedLabel) {
        int count;
        try {
            count = Math.min(candidates.count(), 80);
        } catch (Exception e) {
            return null;
        }

        for (int i = 0; i < count; i++) {
            try {
                Locator candidate = candidates.nth(i);
                if (!isVisible(candidate)) continue;

                String selfText = "";
                try {
                    selfText = candidate.innerText();
                } catch (Exception ignored) {}
                String selfAria = "";
                try {
                    selfAria = candidate.getAttribute("aria-label");
                } catch (Exception ignored) {}
                String selfTitle = "";
                try {
                    selfTitle = candidate.getAttribute("title");
                } catch (Exception ignored) {}

                String normalizedSelf = normalizeText(selfText + " " + selfAria + " " + selfTitle);
                if (normalizedSelf.contains(normalizedLabel)) {
                    return candidate;
                }

                String context = String.valueOf(candidate.evaluate(
                    "el => { let n = el; for (let i = 0; n && i < 6; i++, n = n.parentElement) {" +
                        " const t = (n.innerText || '').replace(/\\s+/g, ' ').trim();" +
                        " if (t) return t;" +
                    " } return ''; }"
                ));
                String normalized = normalizeText(context);
                if (normalized.contains(normalizedLabel)) {
                    return candidate;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private Locator pickFinalizationLabel(Locator candidates, String normalizedLabel) {
        int count;
        try {
            count = Math.min(candidates.count(), 80);
        } catch (Exception e) {
            return null;
        }

        for (int i = 0; i < count; i++) {
            try {
                Locator candidate = candidates.nth(i);
                if (!isVisible(candidate)) continue;

                String normalized = normalizeText(candidate.innerText());
                if (normalized.contains(normalizedLabel) && normalized.length() < 220) {
                    return candidate;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private boolean isFinalizationCheckboxCheckedByScript(String label) {
        try {
            Object result = page.evaluate(
                "(label) => {" +
                    "const norm = (s) => (s || '').toString().normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').toLowerCase().replace(/\\s+/g, ' ').trim();" +
                    "const wanted = norm(label);" +
                    "const visible = (el) => {" +
                        "if (!el) return false;" +
                        "const style = getComputedStyle(el);" +
                        "const rect = el.getBoundingClientRect();" +
                        "return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;" +
                    "};" +
                    "const roots = Array.from(document.querySelectorAll('.dx-overlay-content, [role=\"dialog\"], .modal, [class*=\"modal\"], [class*=\"dialog\"], [class*=\"popup\"]')).filter(visible).reverse();" +
                    "let popup = null;" +
                    "for (const root of (roots.length ? roots : [document.body])) {" +
                        "const t = norm(root.innerText || root.textContent);" +
                        "if (t.includes('finalizar atendimento') || t.includes('editando finalizar atendimento') || t.includes('consulta medica') || t.includes('alta do episodio')) {" +
                            "popup = root;" +
                            "break;" +
                        "}" +
                    "}" +
                    "popup = popup || document.body;" +
                    "const nodes = [...popup.querySelectorAll('label, div, span, [role=\"checkbox\"], input[type=\"checkbox\"], [class*=\"checkbox\"]')].filter(visible);" +
                    "const checked = (el) => {" +
                        "if (!el) return false;" +
                        "if (el.matches && el.matches('input[type=\"checkbox\"]') && el.checked) return true;" +
                        "if (el.getAttribute && el.getAttribute('aria-checked') === 'true') return true;" +
                        "const cls = el.className || '';" +
                        "if (typeof cls === 'string' && (cls.includes('dx-checkbox-checked') || cls.includes('dx-state-selected') || cls.includes('dx-list-item-selected'))) return true;" +
                        "return !!(el.querySelector && el.querySelector('input[type=\"checkbox\"]:checked, [aria-checked=\"true\"], .dx-checkbox-checked, .dx-state-selected, .dx-list-item-selected'));" +
                    "};" +
                    "const matches = [];" +
                    "for (const node of nodes) {" +
                        "const own = norm([node.innerText, node.getAttribute('aria-label'), node.getAttribute('title')].join(' '));" +
                        "let context = own;" +
                        "for (let parent = node.parentElement, i = 0; parent && i < 5; parent = parent.parentElement, i++) {" +
                            "context += ' ' + norm(parent.innerText);" +
                        "}" +
                        "if (!context.includes(wanted)) continue;" +
                        "const checkbox = node.matches('input[type=\"checkbox\"], [role=\"checkbox\"], [class*=\"checkbox\"]') ? node : " +
                            "node.querySelector('input[type=\"checkbox\"], [role=\"checkbox\"], [class*=\"checkbox\"]') || " +
                            "(node.closest('label, div, tr, li') && node.closest('label, div, tr, li').querySelector('input[type=\"checkbox\"], [role=\"checkbox\"], [class*=\"checkbox\"]'));" +
                        "const target = checkbox || node;" +
                        "const container = target.closest && target.closest('label, .dx-list-item, .dx-item, div, tr, li') || target;" +
                        "const r = container.getBoundingClientRect();" +
                        "matches.push({target, container, area: r.width * r.height});" +
                    "}" +
                    "matches.sort((a, b) => a.area - b.area);" +
                    "for (const item of matches) {" +
                        "if (checked(item.target) || checked(item.container)) return true;" +
                    "}" +
                    "return false;" +
                "}",
                label
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            System.out.println("[TestExecutor] Validacao por script do checkbox de finalizacao falhou: " + e.getMessage());
            return false;
        }
    }

    private boolean ensureFinalizationCheckboxCheckedByScript(String label) {
        try {
            Object result = page.evaluate(
                "(label) => {" +
                    "const norm = (s) => (s || '').toString().normalize('NFD').replace(/[\\u0300-\\u036f]/g, '').toLowerCase().replace(/\\s+/g, ' ').trim();" +
                    "const wanted = norm(label);" +
                    "const visible = (el) => {" +
                        "if (!el) return false;" +
                        "const style = getComputedStyle(el);" +
                        "const rect = el.getBoundingClientRect();" +
                        "return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;" +
                    "};" +
                    "const checked = (el) => {" +
                        "if (!el) return false;" +
                        "if (el.matches && el.matches('input[type=\"checkbox\"]') && el.checked) return true;" +
                        "if (el.getAttribute && el.getAttribute('aria-checked') === 'true') return true;" +
                        "const cls = el.className || '';" +
                        "if (typeof cls === 'string' && (cls.includes('dx-checkbox-checked') || cls.includes('dx-state-selected') || cls.includes('dx-list-item-selected'))) return true;" +
                        "return !!(el.querySelector && el.querySelector('input[type=\"checkbox\"]:checked, [aria-checked=\"true\"], .dx-checkbox-checked, .dx-state-selected, .dx-list-item-selected'));" +
                    "};" +
                    "const roots = Array.from(document.querySelectorAll('.dx-overlay-content, [role=\"dialog\"], .modal, [class*=\"modal\"], [class*=\"dialog\"], [class*=\"popup\"]')).filter(visible).reverse();" +
                    "let popup = null;" +
                    "for (const root of (roots.length ? roots : [document.body])) {" +
                        "const t = norm(root.innerText || root.textContent);" +
                        "if (t.includes('finalizar atendimento') || t.includes('editando finalizar atendimento') || t.includes('consulta medica') || t.includes('alta do episodio')) {" +
                            "popup = root;" +
                            "break;" +
                        "}" +
                    "}" +
                    "popup = popup || document.body;" +
                    "const nodes = [...popup.querySelectorAll('label, div, span, [role=\"checkbox\"], input[type=\"checkbox\"], [class*=\"checkbox\"]')].filter(visible);" +
                    "const matches = [];" +
                    "for (const node of nodes) {" +
                        "const own = norm([node.innerText, node.getAttribute('aria-label'), node.getAttribute('title')].join(' '));" +
                        "let context = own;" +
                        "for (let parent = node.parentElement, i = 0; parent && i < 5; parent = parent.parentElement, i++) {" +
                            "context += ' ' + norm(parent.innerText);" +
                        "}" +
                        "if (!context.includes(wanted)) continue;" +
                        "const checkbox = node.matches('input[type=\"checkbox\"], [role=\"checkbox\"], [class*=\"checkbox\"]') ? node : " +
                            "node.querySelector('input[type=\"checkbox\"], [role=\"checkbox\"], [class*=\"checkbox\"]') || " +
                            "(node.closest('label, div, tr, li') && node.closest('label, div, tr, li').querySelector('input[type=\"checkbox\"], [role=\"checkbox\"], [class*=\"checkbox\"]'));" +
                        "const target = checkbox || node;" +
                        "const container = target.closest && target.closest('label, .dx-list-item, .dx-item, div, tr, li') || target;" +
                        "const r = container.getBoundingClientRect();" +
                        "matches.push({target, container, area: r.width * r.height});" +
                    "}" +
                    "matches.sort((a, b) => a.area - b.area);" +
                    "for (const item of matches) {" +
                        "if (checked(item.target) || checked(item.container)) return true;" +
                        "item.target.scrollIntoView({block: 'center', inline: 'center'});" +
                        "item.target.click();" +
                        "return true;" +
                    "}" +
                    "return false;" +
                "}",
                label
            );
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            System.out.println("[TestExecutor] Ensure por script no checkbox de finalizacao falhou: " + e.getMessage());
            return false;
        }
    }

    private Locator findConsultaMedicaCheckbox() {
        Locator scope = findFinalizationPopupScope();

        Locator byRoleText = pickFirstVisibleSmall(scope.locator("[role='checkbox']:has-text('Consulta Médica')"), 20);
        if (byRoleText == null) {
            byRoleText = pickFirstVisibleSmall(scope.locator("[role='checkbox']:has-text('Consulta Medica')"), 20);
        }
        if (byRoleText != null) {
            return byRoleText;
        }

        Locator direct = pickConsultaMedicaCheckbox(scope.locator("input[type='checkbox'], [role='checkbox'], [class*='checkbox']"));
        if (direct != null) {
            return direct;
        }

        String[] labelSelectors = {
            "label:has-text('Consulta Medica')",
            "label:has-text('Consulta Médica')",
            "div:has-text('Consulta Medica')",
            "div:has-text('Consulta Médica')",
            "span:has-text('Consulta Medica')",
            "span:has-text('Consulta Médica')"
        };

        for (String selector : labelSelectors) {
            Locator label = pickFirstVisibleSmall(scope.locator(selector), 120);
            if (label == null) continue;

            try {
                Locator nearest = label.locator("xpath=ancestor-or-self::*[.//input[@type='checkbox']][1]//input[@type='checkbox']").first();
                if (isVisible(nearest)) {
                    return nearest;
                }
            } catch (Exception ignored) {}

            return label;
        }

        return null;
    }

    private Locator pickConsultaMedicaCheckbox(Locator candidates) {
        int count;
        try {
            count = Math.min(candidates.count(), 80);
        } catch (Exception e) {
            return null;
        }

        for (int i = 0; i < count; i++) {
            try {
                Locator candidate = candidates.nth(i);
                if (!isVisible(candidate)) continue;

                String selfText = "";
                try {
                    selfText = candidate.innerText();
                } catch (Exception ignored) {}
                String selfAria = "";
                try {
                    selfAria = candidate.getAttribute("aria-label");
                } catch (Exception ignored) {}
                String selfTitle = "";
                try {
                    selfTitle = candidate.getAttribute("title");
                } catch (Exception ignored) {}

                String normalizedSelf = normalizeText(selfText + " " + selfAria + " " + selfTitle);
                if (normalizedSelf.contains("consulta medica") && !normalizedSelf.contains("escuta inicial")) {
                    return candidate;
                }

                String context = String.valueOf(candidate.evaluate(
                    "el => { let n = el; for (let i = 0; n && i < 6; i++, n = n.parentElement) {" +
                        " const t = (n.innerText || '').replace(/\\s+/g, ' ').trim();" +
                        " if (t) return t;" +
                    " } return ''; }"
                ));
                String normalized = normalizeText(context);
                if (normalized.contains("consulta medica") && !normalized.contains("escuta inicial")) {
                    return candidate;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private void executarCliqueFinalizarPopup() throws Exception {
        if (!waitForFinalizationPopup(8000)) {
            if (hasRequiredMessage("soap subjetivo")) {
                throw new RuntimeException("Nao foi possivel finalizar: Soap Subjetivo com preenchimento obrigatorio");
            }
            throw new RuntimeException("Popup Editando Finalizar Atendimento nao esta visivel para finalizar");
        }

        Locator scope = findFinalizationPopupScope();
        Locator finalizar = findExactButtonByText("Finalizar", scope);
        if (finalizar == null) {
            throw new RuntimeException("Botao Finalizar do popup de finalizacao nao encontrado");
        }

        clickLocator(finalizar, "Finalizar popup de atendimento");
        if (!waitForFinalizationPopupToClose(10000)) {
            if (hasRequiredMessage("soap subjetivo")) {
                throw new RuntimeException("Nao foi possivel finalizar: Soap Subjetivo com preenchimento obrigatorio");
            }
            tryCloseFinalizationPopupFallback();
        }
        if (!waitForFinalizationPopupToClose(5000)) {
            throw new RuntimeException("Popup Editando Finalizar Atendimento permaneceu aberto apos clicar em Finalizar");
        }
        finalizationPopupArmed = false;
    }

    private void tryCloseFinalizationPopupFallback() {
        try {
            page.keyboard().press("Escape");
        } catch (Exception ignored) {}
        page.waitForTimeout(400);
        if (!waitForFinalizationPopup(250)) return;

        clickPopupButtonByTextScript("Cancelar");
        page.waitForTimeout(400);
        if (!waitForFinalizationPopup(250)) return;

        clickPopupCloseIconScript();
        page.waitForTimeout(400);
    }

    private Locator findFinalizationPopupScope() {
        Locator pick = findFinalizationPopupScopeOrNull();
        return pick != null ? pick : page.locator("body");
    }

    private Locator findFinalizationPopupScopeOrNull() {
        String[] selectors = {
            ".dx-overlay-content",
            "[role='dialog']",
            ".modal",
            "[class*='modal']",
            "[class*='dialog']",
            "[class*='popup']"
        };

        for (String selector : selectors) {
            Locator pick = pickVisibleFinalizationPopupScope(page.locator(selector));
            if (pick == null) continue;
            return pick;
        }

        return null;
    }

    private Locator pickVisibleFinalizationPopupScope(Locator candidates) {
        int count;
        try {
            count = Math.min(candidates.count(), 50);
        } catch (Exception e) {
            return null;
        }

        Locator best = null;
        int bestScore = -1;
        int bestIndex = -1;
        for (int i = 0; i < count; i++) {
            try {
                Locator candidate = candidates.nth(i);
                if (!isVisible(candidate)) continue;

                Locator finalizar = findExactButtonByText("Finalizar", candidate);
                if (finalizar == null) continue;

                String normalized = normalizeText(candidate.innerText());
                int score = 0;

                if (normalized.contains("consulta medica")) score += 4;
                if (normalized.contains("alta do episodio")) score += 4;
                if (normalized.contains("finalizar atendimento")) score += 3;
                if (normalized.contains("editando finalizar atendimento")) score += 3;
                if (normalized.contains("consulta")) score += 2;
                if (normalized.contains("alta")) score += 2;

                if (score <= 0) continue;

                if (score > bestScore || (score == bestScore && i > bestIndex)) {
                    best = candidate;
                    bestScore = score;
                    bestIndex = i;
                }
            } catch (Exception ignored) {}
        }

        return best;
    }

    private Locator pickVisibleScopeContaining(Locator candidates, String requiredText) {
        int count;
        try {
            count = Math.min(candidates.count(), 50);
        } catch (Exception e) {
            return null;
        }

        Locator fallback = null;
        double bestArea = -1;
        for (int i = 0; i < count; i++) {
            try {
                Locator candidate = candidates.nth(i);
                if (!isVisible(candidate)) continue;

                String normalized = normalizeText(candidate.innerText());
                if (!normalized.contains(requiredText)) continue;

                BoundingBox box = candidate.boundingBox();
                if (box == null) continue;
                double area = box.width * box.height;
                if (area > bestArea) {
                    fallback = candidate;
                    bestArea = area;
                }
            } catch (Exception ignored) {}
        }
        return fallback;
    }

    private Locator findExactButtonByText(String text, Locator scope) {
        String normalizedExpected = normalizeText(text);
        String[] selectors = {
            "button",
            "[role='button']",
            "[title]",
            "[aria-label]",
            "span",
            "div"
        };

        for (String selector : selectors) {
            Locator pick = pickExactOrShortButton(scope.locator(selector), normalizedExpected);
            if (pick != null) {
                return pick;
            }
        }

        return null;
    }

    private Locator pickExactOrShortButton(Locator candidates, String normalizedExpected) {
        int count;
        try {
            count = Math.min(candidates.count(), 120);
        } catch (Exception e) {
            return null;
        }

        Locator containsFallback = null;
        for (int i = 0; i < count; i++) {
            try {
                Locator candidate = candidates.nth(i);
                if (!isVisible(candidate)) continue;

                String text = "";
                try {
                    text = candidate.innerText();
                } catch (Exception ignored) {}

                String title = "";
                try {
                    title = candidate.getAttribute("title");
                } catch (Exception ignored) {}

                String aria = "";
                try {
                    aria = candidate.getAttribute("aria-label");
                } catch (Exception ignored) {}

                String normalizedText = normalizeText(text);
                String normalizedTitle = normalizeText(title);
                String normalizedAria = normalizeText(aria);

                if (normalizedText.equals(normalizedExpected) ||
                    normalizedTitle.equals(normalizedExpected) ||
                    normalizedAria.equals(normalizedExpected)) {
                    return candidate;
                }

                boolean shortText = normalizedText.length() <= normalizedExpected.length() + 14;
                if (shortText &&
                    (normalizedText.contains(normalizedExpected) ||
                     normalizedTitle.contains(normalizedExpected) ||
                     normalizedAria.contains(normalizedExpected))) {
                    containsFallback = candidate;
                }
            } catch (Exception ignored) {}
        }

        return containsFallback;
    }

    private boolean waitForFinalizationPopup(int timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                Locator scope = findFinalizationPopupScopeOrNull();
                if (scope != null && isVisible(scope)) return true;
            } catch (Exception ignored) {}
            page.waitForTimeout(250);
        }
        return false;
    }

    private boolean waitForFinalizationPopupToClose(int timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (findFinalizationPopupScopeOrNull() == null) {
                return true;
            }
            page.waitForTimeout(250);
        }
        return findFinalizationPopupScopeOrNull() == null;
    }

    private void executarSelecaoConsultorio() throws Exception {
        Locator campoConsultorio = findConsultorioField();
        if (campoConsultorio == null) {
            throw new RuntimeException("Campo Consultório não encontrado para selecionar sala de atendimento");
        }

        System.out.println("[TestExecutor] Abrindo campo Consultório...");
        try {
            campoConsultorio.scrollIntoViewIfNeeded();
        } catch (Exception ignored) {}
        try {
            campoConsultorio.click(new Locator.ClickOptions().setTimeout(8000));
        } catch (Exception e) {
            System.out.println("[TestExecutor] Clique normal no Consultório falhou, tentando clique forçado: " + e.getMessage());
            campoConsultorio.click(new Locator.ClickOptions().setTimeout(8000).setForce(true));
        }
        page.waitForTimeout(1000);

        Locator opcao = findFirstValidConsultorioOption();
        if (opcao == null) {
            throw new RuntimeException("Nenhuma opção válida de consultório foi encontrada na lista");
        }

        String texto = "";
        try {
            texto = opcao.innerText().replace("\n", " ").trim();
        } catch (Exception ignored) {}
        System.out.println("[TestExecutor] Selecionando consultório disponível: " + texto);

        try {
            opcao.scrollIntoViewIfNeeded();
        } catch (Exception ignored) {}
        opcao.click(new Locator.ClickOptions().setTimeout(8000));
        consultorioSelecionado = true;
        page.waitForTimeout(1000);
    }

    private Locator findConsultorioField() {
        String[] selectors = {
            "input[placeholder*='Consultório'], input[placeholder*='Consultorio']",
            "[role='combobox']:has-text('Consultório'), [role='combobox']:has-text('Consultorio')",
            "[role='button']:has-text('Consultório'), [role='button']:has-text('Consultorio')",
            "[class*='select']:has-text('Consultório'), [class*='select']:has-text('Consultorio')",
            "[class*='texteditor']:has-text('Consultório'), [class*='texteditor']:has-text('Consultorio')",
            "div:has-text('Consultório'), div:has-text('Consultorio')"
        };

        for (String selector : selectors) {
            Locator pick = pickFirstVisibleSmall(page.locator(selector), 80);
            if (pick != null) {
                return pick;
            }
        }
        return null;
    }

    private Locator findFirstValidConsultorioOption() {
        String[] scopedSelectors = {
            ".dx-overlay-content .dx-list-item",
            ".dx-overlay-content [role='option']",
            ".dx-overlay-content [class*='item']",
            "[role='listbox'] [role='option']",
            "[class*='dropdown'] [class*='item']",
            "[class*='popover'] [class*='item']",
            "[class*='select-content'] [role='option']"
        };

        for (String selector : scopedSelectors) {
            Locator option = pickFirstValidConsultorioOption(page.locator(selector));
            if (option != null) {
                return option;
            }
        }

        String[] fallbackSelectors = {
            "[role='option']",
            ".dx-list-item",
            "[class*='item']",
            "li",
            "div"
        };

        for (String selector : fallbackSelectors) {
            Locator option = pickFirstValidConsultorioOption(page.locator(selector));
            if (option != null) {
                return option;
            }
        }

        return null;
    }

    private Locator pickFirstValidConsultorioOption(Locator candidates) {
        int count;
        try {
            count = Math.min(candidates.count(), 250);
        } catch (Exception e) {
            return null;
        }

        for (int i = 0; i < count; i++) {
            try {
                Locator candidate = candidates.nth(i);
                if (!isVisible(candidate)) continue;

                String text = candidate.innerText();
                String norm = normalizeText(text);
                if (!isValidConsultorioOption(norm)) continue;

                return candidate;
            } catch (Exception ignored) {}
        }

        return null;
    }

    private boolean isValidConsultorioOption(String norm) {
        if (norm == null || norm.isEmpty()) return false;
        if (norm.length() > 160) return false;
        if (norm.contains("consultorio") && norm.length() <= 20) return false;
        if (norm.contains("selecione")) return false;
        if (norm.contains("sala de atendimento")) return false;
        if (norm.contains("proximo paciente")) return false;
        if (norm.contains("lista de espera")) return false;
        if (norm.contains("pacientes aguardando")) return false;
        if (norm.contains("buscar menus")) return false;
        if (norm.contains("favoritos")) return false;
        if (norm.contains("administracao")) return false;
        if (norm.contains("assistencia farmaceutica")) return false;
        if (norm.contains("atendimento da atencao primaria")) return false;
        if (norm.contains("escuta inicial")) return false;
        if (norm.contains("acolhimento")) return false;
        if (norm.contains("conduta e-sus")) return false;
        return true;
    }

    private boolean isConsultorioSelectionVisible() {
        Locator field = findConsultorioField();
        if (field == null || !isVisible(field)) {
            return false;
        }

        try {
            String value;
            try {
                value = field.inputValue();
            } catch (Exception ignored) {
                value = field.innerText();
            }
            String norm = normalizeText(value);
            return norm.isEmpty() || norm.contains("consultorio") || norm.contains("selecione");
        } catch (Exception e) {
            return true;
        }
    }

    private Locator pickFirstVisibleSmall(Locator candidates, int maxTextLength) {
        int count;
        try {
            count = Math.min(candidates.count(), 100);
        } catch (Exception e) {
            return null;
        }

        for (int i = 0; i < count; i++) {
            try {
                Locator candidate = candidates.nth(i);
                if (!isVisible(candidate)) continue;

                String text = "";
                try {
                    text = candidate.innerText();
                } catch (Exception ignored) {}

                if (text != null && text.trim().length() > maxTextLength) continue;
                return candidate;
            } catch (Exception ignored) {}
        }

        return null;
    }

    /**
     * Executa seleção de unidade no modal com estratégia de busca inteligente
     */
    private void executarSelecaoUnidade(String passo) throws Exception {
        Locator scope = page.locator("body");
        Locator dialog = page.locator("[role='dialog'], .modal, [class*='modal'], [class*='dialog'], [class*='popup']").first();
        if (isVisible(dialog)) {
            scope = dialog;
        }

        // Extrai o nome da unidade entre aspas
        String unidadeDesejada = extractQuotedText(passo);
        if (unidadeDesejada.isEmpty()) {
            // Tenta extrair de padrões comuns
            unidadeDesejada = extractGenericTarget(passo, "unidade|selecionar");
        }

        // Se não veio no passo, tenta aproveitar o valor já presente no campo (comum em modais pós-login)
        if (unidadeDesejada.isEmpty()) {
            try {
                Locator campo = scope.locator("[role='textbox'], input[type='text'], input").first();
                if (campo.isVisible()) {
                    String valor = campo.inputValue();
                    if (valor != null && !valor.trim().isEmpty()) {
                        unidadeDesejada = valor.trim();
                    }
                }
            } catch (Exception ignored) {}
        }

        System.out.println("[TestExecutor] Selecionando unidade: '" + unidadeDesejada + "'");

        // ESTRATÉGIA 1: Tenta usar o campo de busca para filtrar a unidade
        try {
            Locator campoBusca = scope.locator(
                "input[placeholder*='Buscar'], input[placeholder*='Unidade'], " +
                "[role='textbox'][placeholder], [role='textbox'], input[type='text']"
            ).first();
            if (campoBusca.isVisible()) {
                System.out.println("[TestExecutor] Usando campo de busca para filtrar unidade...");

                // Limpa e preenche o campo de busca
                campoBusca.click();
                if (unidadeDesejada != null && !unidadeDesejada.isEmpty()) {
                    campoBusca.clear();
                    campoBusca.fill(unidadeDesejada);
                }
                page.waitForTimeout(1200); // Aguarda filtragem

                // Tenta clicar na unidade filtrada
                try {
                    Locator unidadeFiltrada = findBestUnitOption(scope, unidadeDesejada);
                    if (unidadeFiltrada != null && unidadeFiltrada.isVisible()) {
                        try {
                            unidadeFiltrada.scrollIntoViewIfNeeded();
                        } catch (Exception ignored) {}
                        unidadeFiltrada.click(new Locator.ClickOptions().setTimeout(8000));
                        System.out.println("[TestExecutor] Unidade encontrada e clicada via busca");

                        if (confirmarEFecharModalUnidade(scope, unidadeDesejada)) {
                            return;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[TestExecutor] Unidade não encontrada via busca: " + e.getMessage());
                }

                // Se não encontrou a unidade específica, limpa a busca e tenta selecionar a primeira
                if (unidadeDesejada != null && !unidadeDesejada.isEmpty()) {
                    campoBusca.clear();
                    page.waitForTimeout(500);
                }
            }
        } catch (Exception e) {
            System.out.println("[TestExecutor] Campo de busca não disponível: " + e.getMessage());
        }

        // ESTRATÉGIA 2: Seleciona a primeira unidade disponível (fallback)
        System.out.println("[TestExecutor] Selecionando primeira unidade disponível...");
        try {
            Locator primeiraUnidade = findBestUnitOption(scope, null);
            if (primeiraUnidade != null && primeiraUnidade.isVisible()) {
                String textoUnidade = primeiraUnidade.textContent();
                System.out.println("[TestExecutor] Primeira unidade encontrada: " + textoUnidade.substring(0, Math.min(50, textoUnidade.length())));
                try {
                    primeiraUnidade.scrollIntoViewIfNeeded();
                } catch (Exception ignored) {}
                primeiraUnidade.click(new Locator.ClickOptions().setTimeout(8000));

                if (confirmarEFecharModalUnidade(scope, null)) {
                    return;
                }
            }
        } catch (Exception e) {
            System.out.println("[TestExecutor] Erro ao selecionar primeira unidade: " + e.getMessage());
        }

        // ESTRATÉGIA 3: Tenta apenas clicar em Continuar (se já tiver uma unidade pré-selecionada)
        System.out.println("[TestExecutor] Tentando apenas clicar em Continuar...");
        if (!confirmarEFecharModalUnidade(scope, null)) {
            throw new RuntimeException("Não foi possível selecionar a unidade e fechar o modal antes de continuar");
        }
    }

    private boolean confirmarEFecharModalUnidade(Locator scope, String unidadeDesejada) {
        String[] botoesConfirmar = {"Continuar", "Selecionar", "Confirmar", "OK", "Próximo", "Avançar", "Prosseguir", "Salvar"};

        if (!clickConfirmInScope(scope, botoesConfirmar)) {
            clickContinuarIfPresent();
        }

        if (waitForUnitModalToClose(8000)) {
            return true;
        }

        System.out.println("[TestExecutor] Modal de unidade ainda presente após confirmar - tentando novamente (double click)...");

        try {
            Locator opcao = findBestUnitOption(scope, unidadeDesejada);
            if (opcao != null && opcao.isVisible()) {
                try {
                    opcao.scrollIntoViewIfNeeded();
                } catch (Exception ignored) {}
                opcao.dblclick();
                page.waitForTimeout(500);
            }
        } catch (Exception ignored) {}

        clickConfirmInScope(scope, botoesConfirmar);
        if (waitForUnitModalToClose(8000)) {
            return true;
        }

        System.out.println("[TestExecutor] Modal de unidade não fechou após tentativas");
        return false;
    }

    private Locator findBestUnitOption(Locator scope, String unidadeDesejada) {
        try {
            Locator options = scope.locator("[role='option']:has-text('CNES'), .cursor-pointer:has-text('CNES'), [class*='cursor-pointer']:has-text('CNES'), [class*='option']:has-text('CNES'), [class*='item']:has-text('CNES'), [class*='card']:has-text('CNES'), li:has-text('CNES'), div:has-text('CNES')");
            try {
                options.first().waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(6000));
            } catch (Exception ignored) {}

            int count = 0;
            try {
                count = options.count();
            } catch (Exception ignored) {}
            System.out.println("[TestExecutor] Opções com CNES encontradas: " + count);

            String desired = normalizeText(unidadeDesejada);
            Locator fallback = null;

            for (int i = 0; i < Math.min(count, 200); i++) {
                Locator opt = options.nth(i);
                String txt;
                try {
                    txt = opt.innerText();
                } catch (Exception e) {
                    continue;
                }

                String norm = normalizeText(txt);
                if (norm.isEmpty()) continue;
                if (norm.length() > 350) continue;
                if (norm.contains("desativado")) continue;
                if (norm.contains("continuar") || norm.contains("selecionar") || norm.contains("confirmar")) continue;

                if (fallback == null) {
                    fallback = opt;
                }

                if (!desired.isEmpty() && norm.contains(desired)) {
                    System.out.println("[TestExecutor] Unidade selecionada por match: idx=" + i + " texto='" + txt.replace("\n", " ").trim() + "'");
                    return opt;
                }
            }

            if (fallback != null) {
                try {
                    String txt = fallback.innerText();
                    System.out.println("[TestExecutor] Unidade selecionada por fallback: texto='" + txt.replace("\n", " ").trim() + "'");
                } catch (Exception ignored) {}
                return fallback;
            }
        } catch (Exception ignored) {}

        try {
            Locator firstNonButtonOption = scope.locator(
                "[role='option']:not(:has-text('Continuar')):not(:has-text('Confirmar')):not(:has-text('Selecionar')):not(:has-text('OK'))"
            ).first();
            if (firstNonButtonOption.isVisible()) {
                try {
                    String txt = firstNonButtonOption.innerText();
                    System.out.println("[TestExecutor] Unidade selecionada por fallback genérico: texto='" + txt.replace("\n", " ").trim() + "'");
                } catch (Exception ignored) {}
                return firstNonButtonOption;
            }
        } catch (Exception ignored) {}

        System.out.println("[TestExecutor] Nenhuma opção de unidade encontrada para clicar");
        return null;
    }

    private String normalizeText(String input) {
        if (input == null) return "";
        String s = input.replace("\n", " ").replace("\t", " ").trim().toLowerCase();
        try {
            s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        } catch (Exception ignored) {}
        s = s.replaceAll("\\s+", " ");
        return s;
    }

    private boolean waitForUnitModalToClose(int timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                Locator msg = page.getByText("Selecione uma unidade", new Page.GetByTextOptions().setExact(false)).first();
                if (isVisible(msg)) {
                    page.waitForTimeout(300);
                    continue;
                }
            } catch (Exception ignored) {}

            if (!isUnitSelectionModalVisible()) {
                return true;
            }

            page.waitForTimeout(300);
        }
        return !isUnitSelectionModalVisible();
    }

    /**
     * Clica no botão Continuar/Confirmar se estiver presente
     */
    private void clickContinuarIfPresent() {
        String[] botoesConfirmar = {"Continuar", "Selecionar", "Confirmar", "OK", "Próximo", "Avançar", "Prosseguir", "Salvar"};

        Locator dialog = page.locator("[role='dialog'], .modal, [class*='modal'], [class*='dialog'], [class*='popup']").first();
        if (isVisible(dialog)) {
            if (clickConfirmInScope(dialog, botoesConfirmar)) {
                return;
            }
        }

        if (clickConfirmInScope(page.locator("body"), botoesConfirmar)) {
            return;
        }

        System.out.println("[TestExecutor] Nenhum botão de confirmação encontrado");
    }

    private boolean clickConfirmInScope(Locator scope, String[] labels) {
        for (String textoBotao : labels) {
            try {
                Locator botao = scope.locator(
                    "button:has-text('" + textoBotao + "'), " +
                    "[role='button']:has-text('" + textoBotao + "'), " +
                    "[role='option']:has-text('" + textoBotao + "')"
                ).first();
                if (botao.isVisible()) {
                    long start = System.currentTimeMillis();
                    boolean enabled = false;
                    while (System.currentTimeMillis() - start < 4000) {
                        try {
                            enabled = botao.isEnabled();
                        } catch (Exception ignored) {
                            enabled = true;
                        }
                        if (enabled) break;
                        page.waitForTimeout(200);
                    }
                    if (!enabled) continue;
                    System.out.println("[TestExecutor] Clicando em botão: " + textoBotao);
                    botao.click();
                    page.waitForTimeout(500);
                    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    private boolean isVisible(Locator loc) {
        try {
            return loc != null && loc.isVisible();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isUnitSelectionModalVisible() {
        try {
            Locator hasTextbox = page.locator("[role='textbox'], input[type='text'], input").first();
            if (!isVisible(hasTextbox)) {
                return false;
            }

            Locator hasCnes = page.locator("[role='option']:has-text('CNES'), text=CNES").first();
            if (!isVisible(hasCnes)) {
                return false;
            }

            Locator confirmar = page.locator(
                "button:has-text('Continuar'), [role='button']:has-text('Continuar'), [role='option']:has-text('Continuar'), " +
                "button:has-text('Selecionar'), [role='button']:has-text('Selecionar'), [role='option']:has-text('Selecionar')"
            ).first();
            if (isVisible(confirmar)) {
                return true;
            }

            Locator erro = page.getByText("Selecione uma unidade", new Page.GetByTextOptions().setExact(false)).first();
            return isVisible(erro);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Executa navegação hierárquica de menu (ex: "Atendimento > Acolhimento")
     */
    private void executarNavegacaoMenu(String passo) throws Exception {
        // Extrai menu pai e submenu do passo
        String[] parts = extractMenuPath(passo);
        if (parts == null || parts.length < 2) {
            System.out.println("[TestExecutor] Formato de navegação de menu inválido: " + passo);
            return;
        }

        String menuPai = parts[0].trim();
        String submenu = parts[1].trim();

        System.out.println("[TestExecutor] Navegando: '" + menuPai + "' > '" + submenu + "'");

        // PASSO 1: Clica no menu pai
        System.out.println("[TestExecutor] Passo 1: Clicando no menu pai '" + menuPai + "'...");
        ActionDecision clickParent = new ActionDecision.Builder()
            .action(ActionDecision.ActionType.CLICK)
            .role("option")
            .text(menuPai)
            .reason("Navegação menu: clicar no pai")
            .build();
        actionExecutor.execute(clickParent);

        // Aguarda o submenu expandir
        System.out.println("[TestExecutor] Aguardando submenu expandir (1000ms)...");
        Thread.sleep(1000);

        // Recaptura contexto após expandir menu pai
        String novoContexto = SmartContext.capturarEFormatar(page);
        System.out.println("[SmartContext] Após expandir menu pai: " + SmartContext.capturar(page).size() + " elementos");

        // PASSO 2: Clica no submenu
        System.out.println("[TestExecutor] Passo 2: Clicando no submenu '" + submenu + "'...");
        ActionDecision clickSubmenu = new ActionDecision.Builder()
            .action(ActionDecision.ActionType.CLICK)
            .role("option")
            .text(submenu)
            .reason("Navegação menu: clicar no submenu")
            .build();
        actionExecutor.execute(clickSubmenu);

        System.out.println("[TestExecutor] Navegação de menu concluída!");
    }

    /**
     * Extrai caminho do menu do passo (ex: "Navegar em X > Y" retorna ["X", "Y"])
     */
    private String[] extractMenuPath(String passo) {
        // Remove "Navegar em" ou similar
        String clean = passo.replaceAll("(?i)navegar em", "").trim();
        clean = clean.replaceAll("(?i)menu", "").trim();
        clean = clean.replaceAll("(?i)para", "").trim();

        // Divide por >
        String[] parts = clean.split(">");
        if (parts.length >= 2) {
            return parts;
        }

        // Tenta dividir por / ou \
        parts = clean.split("[/\\|]");
        if (parts.length >= 2) {
            return parts;
        }

        return null;
    }

    /**
     * Verifica se o passo é uma busca de paciente
     */
    private boolean isPatientSearchStep(String passo) {
        String lower = passo.toLowerCase();
        return (lower.contains("paciente") || lower.contains("buscar paciente")) &&
               (lower.contains("json") || lower.contains("selecionar") || lower.contains("buscar"));
    }

    /**
     * Executa busca de paciente - suporta campos de texto e dropdowns
     */
    private void executarBuscaPaciente(String passo) throws Exception {
        // Tenta extrair nome do arquivo JSON mencionado
        String nomePaciente = extrairNomePacienteDoPasso(passo);

        if (nomePaciente == null || nomePaciente.isEmpty()) {
            nomePaciente = "paciente";
        }

        System.out.println("[TestExecutor] Buscando paciente: '" + nomePaciente + "'");

        String contexto = SmartContext.capturarEFormatar(page);

        // Verifica se há um dropdown/combobox na tela
        if (contexto.toLowerCase().contains("combobox") ||
            contexto.toLowerCase().contains("selecione") ||
            contexto.toLowerCase().contains("dropdown")) {
            System.out.println("[TestExecutor] Detectado dropdown/combobox - usando estratégia de clique");
            executarBuscaPacienteDropdown();
        } else {
            executarBuscaPacienteCampoTexto(nomePaciente, contexto);
        }

        System.out.println("[TestExecutor] Busca de paciente concluída!");
    }

    /**
     * Busca paciente em campo de texto (input tradicional)
     */
    private void executarBuscaPacienteCampoTexto(String nomePaciente, String contexto) throws Exception {
        // PASSO 1: Preenche campo de busca
        String instrucaoEspecifica = "Preencher campo de busca de paciente com: " + nomePaciente;
        ActionDecision fillDecision = intentParser.decideAction(instrucaoEspecifica, contexto);

        System.out.println("[TestExecutor] Preenchendo campo de busca...");
        actionExecutor.execute(fillDecision);

        // Aguarda resultados aparecerem
        System.out.println("[TestExecutor] Aguardando resultados da busca (1500ms)...");
        Thread.sleep(1500);

        // Recaptura contexto com resultados
        String novoContexto = SmartContext.capturarEFormatar(page);
        System.out.println("[SmartContext] Após busca: " + SmartContext.capturar(page).size() + " elementos");

        // PASSO 2: Consulta IA qual resultado clicar
        String instrucaoClique = "Selecionar PRIMEIRO paciente da lista de resultados (NÃO clicar em menus). Paciente: " + nomePaciente;
        System.out.println("[TestExecutor] Consultando IA qual paciente clicar...");
        ActionDecision clickDecision = intentParser.decideAction(instrucaoClique, novoContexto);

        // Verifica se a IA tentou clicar em um menu - se sim, força a usar o primeiro resultado
        String textoClicar = clickDecision.getText();
        if (textoClicar != null && (textoClicar.toLowerCase().contains("prontuário") ||
                                     textoClicar.toLowerCase().contains("menu") ||
                                     textoClicar.toLowerCase().contains("acolhimento"))) {
            System.out.println("[TestExecutor] IA tentou clicar em menu, corrigindo para primeiro resultado...");
            clickDecision = new ActionDecision.Builder()
                .action(ActionDecision.ActionType.CLICK)
                .role("option")
                .index(0)
                .reason("Selecionando primeiro paciente da lista")
                .build();
        }

        System.out.println("[TestExecutor] Clicando no paciente: " + clickDecision.getText());
        actionExecutor.execute(clickDecision);
    }

    /**
     * Busca paciente em dropdown/combobox
     */
    private void executarBuscaPacienteDropdown() throws Exception {
        // PASSO 1: Clica no dropdown para abrir
        System.out.println("[TestExecutor] Passo 1: Clicando no dropdown de paciente...");
        ActionDecision openDropdown = new ActionDecision.Builder()
            .action(ActionDecision.ActionType.CLICK)
            .role("combobox")
            .text("Selecione")
            .reason("Abrir dropdown de seleção de paciente")
            .build();

        try {
            actionExecutor.execute(openDropdown);
        } catch (Exception e) {
            System.out.println("[TestExecutor] Falha ao clicar em combobox 'Selecione', tentando genérico...");
            // Tenta clicar no primeiro combobox disponível
            page.locator("[role='combobox']").first().click();
        }

        // Aguarda opções aparecerem
        System.out.println("[TestExecutor] Aguardando opções do dropdown (2000ms)...");
        Thread.sleep(2000);

        // Recaptura contexto
        String novoContexto = SmartContext.capturarEFormatar(page);
        System.out.println("[SmartContext] Após abrir dropdown: " + SmartContext.capturar(page).size() + " elementos");

        // PASSO 2: Consulta IA qual opção selecionar
        String instrucaoClique = "Selecionar PRIMEIRA opção da lista dropdown (paciente). NÃO clicar em menus.";
        System.out.println("[TestExecutor] Consultando IA qual opção selecionar...");
        ActionDecision clickDecision = intentParser.decideAction(instrucaoClique, novoContexto);

        // Se a IA não retornou um texto útil, usa o primeiro resultado
        String textoClicar = clickDecision.getText();
        if (textoClicar == null || textoClicar.isEmpty() ||
            textoClicar.toLowerCase().contains("prontuário") ||
            textoClicar.toLowerCase().contains("menu")) {
            System.out.println("[TestExecutor] IA não identificou opção válida, selecionando primeira opção...");
            clickDecision = new ActionDecision.Builder()
                .action(ActionDecision.ActionType.CLICK)
                .role("option")
                .index(0)
                .reason("Selecionando primeira opção do dropdown")
                .build();
        }

        System.out.println("[TestExecutor] Selecionando opção: " + clickDecision.getText());
        actionExecutor.execute(clickDecision);

        // Aguarda seleção ser aplicada
        System.out.println("[TestExecutor] Aguardando seleção ser aplicada (1000ms)...");
        Thread.sleep(1000);
    }

    /**
     * Extrai nome do paciente do arquivo mencionado no passo
     */
    private String extrairNomePacienteDoPasso(String passo) {
        // Tenta extrair nome entre aspas ou após "do arquivo"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(?:buscar|selecionar|preencher).*?(?:paciente|nome).*?['\"]([^'\"]+)['\"]|" +
            "(?:do arquivo|do json).*?([\\w_]+\\.json)|" +
            "paciente[_\\s]?([\\w]+)"
        );
        java.util.regex.Matcher matcher = pattern.matcher(passo.toLowerCase());

        if (matcher.find()) {
            if (matcher.group(1) != null) {
                return matcher.group(1);
            }
        }

        // Se menciona arquivo JSON, tenta carregar
        if (passo.contains(".json")) {
            // Extrai nome do arquivo
            java.util.regex.Pattern jsonPattern = java.util.regex.Pattern.compile("([\\w_]+\\.json)");
            java.util.regex.Matcher jsonMatcher = jsonPattern.matcher(passo);
            if (jsonMatcher.find()) {
                String arquivo = jsonMatcher.group(1);
                // Tenta carregar do arquivo
                return carregarNomePacienteDoArquivo(arquivo);
            }
        }

        return null;
    }

    /**
     * Carrega nome do paciente do arquivo JSON
     */
    private String carregarNomePacienteDoArquivo(String arquivo) {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("testdata", arquivo);
            if (java.nio.file.Files.exists(path)) {
                String content = new String(java.nio.file.Files.readAllBytes(path));
                // Parse simples - procura por campos comuns de nome
                if (content.toLowerCase().contains("nome")) {
                    // Extrai valor após "nome":
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                        "\"nome\"\\s*:\\s*['\"]([^'\"]+)['\"]",
                        java.util.regex.Pattern.CASE_INSENSITIVE
                    );
                    java.util.regex.Matcher matcher = pattern.matcher(content);
                    if (matcher.find()) {
                        String nome = matcher.group(1);
                        System.out.println("[TestExecutor] Nome do paciente carregado do arquivo: " + nome);
                        return nome;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[TestExecutor] Erro ao carregar arquivo de paciente: " + e.getMessage());
        }
        return null;
    }

    /**
     * Formata ação da IA em texto legível para logs
     */
    private String formatPlaywrightAction(ActionDecision decision) {
        return switch (decision.getAction()) {
            case CLICK -> String.format("page.getByRole(%s, '%s').click()", decision.getRole(), decision.getText());
            case FILL -> String.format("page.getByLabel('%s').fill('%s')", decision.getLabel(), decision.getValue());
            case CHECK -> String.format("page.getByLabel('%s').check()", decision.getLabel());
            case NAVIGATE -> String.format("page.navigate('%s')", decision.getUrl());
            default -> decision.getAction().toString();
        };
    }

    /**
     * Extrai URL de um texto
     */
    private String extrairUrl(String text) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(http[s]?://[^\\s]+)");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extrai texto entre aspas simples ou duplas
     */
    private String extractQuotedText(String text) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("['\"]([^'\"]+)['\"]");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    /**
     * Extrai texto genérico após uma palavra-chave
     * Ex: "selecionar Unidade para uso interno" -> "Unidade para uso interno"
     */
    private String extractGenericTarget(String text, String keyword) {
        String lower = text.toLowerCase();
        String[] keywords = keyword.split("\\|");
        for (String kw : keywords) {
            int idx = lower.indexOf(kw.toLowerCase());
            if (idx >= 0) {
                String after = text.substring(idx + kw.length()).trim();
                after = after.replaceAll("^(em|no|na|de|do|da|para|no\\s+modal)\\s*", "");
                after = after.replaceAll("['\"]", "");
                if (!after.isEmpty()) {
                    return after.trim();
                }
            }
        }
        return "";
    }
}

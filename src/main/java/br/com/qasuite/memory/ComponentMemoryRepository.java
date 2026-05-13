package br.com.qasuite.memory;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import br.com.qasuite.rag.RagQueryLogEntry;
import br.com.qasuite.rag.RagStats;

public class ComponentMemoryRepository {
    private static final String DB_PATH = "data/qa_agent.db";

    public ComponentMemoryRepository() {
        initDatabase();
    }

    private void initDatabase() {
        try {
            File dataDir = new File("data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }
            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS component_memory (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        component_name TEXT NOT NULL,
                        component_alias TEXT,
                        component_type TEXT,
                        module_name TEXT,
                        screen_name TEXT,
                        behavior_type TEXT,
                        execution_strategy TEXT,
                        fallback_strategy TEXT,
                        successful_attempts INTEGER DEFAULT 0,
                        failed_attempts INTEGER DEFAULT 0,
                        last_success TEXT,
                        learned_from_user INTEGER DEFAULT 0,
                        notes TEXT,
                        created_at TEXT,
                        updated_at TEXT
                    )
                    """);

                Set<String> columns = readColumns(conn, "component_memory");
                ensureColumn(conn, columns, "component_memory", "description", "TEXT");
                ensureColumn(conn, columns, "component_memory", "examples", "TEXT");
                ensureColumn(conn, columns, "component_memory", "semantic_tags", "TEXT");
                ensureColumn(conn, columns, "component_memory", "embedding_text", "TEXT");
                ensureColumn(conn, columns, "component_memory", "rag_enabled", "INTEGER DEFAULT 1");
                ensureColumn(conn, columns, "component_memory", "confidence_score", "REAL DEFAULT 0");
                ensureColumn(conn, columns, "component_memory", "last_used_as_context", "TEXT");

                stmt.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS ux_component_memory_key
                    ON component_memory (component_name, module_name, screen_name)
                    """);
                stmt.execute("""
                    CREATE INDEX IF NOT EXISTS ix_component_memory_component
                    ON component_memory (component_name)
                    """);
                stmt.execute("""
                    CREATE INDEX IF NOT EXISTS ix_component_memory_module
                    ON component_memory (module_name)
                    """);
                stmt.execute("""
                    CREATE INDEX IF NOT EXISTS ix_component_memory_rag_enabled
                    ON component_memory (rag_enabled)
                    """);

                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS rag_query_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        test_execution_id TEXT,
                        step_description TEXT,
                        module_name TEXT,
                        screen_name TEXT,
                        retrieved_components TEXT,
                        selected_component TEXT,
                        score REAL DEFAULT 0,
                        used_in_prompt INTEGER DEFAULT 0,
                        prompt_context TEXT,
                        llm_decision TEXT,
                        execution_success INTEGER,
                        created_at TEXT
                    )
                    """);
                stmt.execute("""
                    CREATE INDEX IF NOT EXISTS ix_rag_query_logs_created_at
                    ON rag_query_logs (created_at)
                    """);
                stmt.execute("""
                    CREATE INDEX IF NOT EXISTS ix_rag_query_logs_execution
                    ON rag_query_logs (test_execution_id)
                    """);
            }
        } catch (SQLException e) {
            System.err.println("[ComponentMemoryRepository] Erro ao inicializar SQLite: " + e.getMessage());
        }
    }

    private static Set<String> readColumns(Connection conn, String table) throws SQLException {
        Set<String> cols = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(" + table + ")")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    if (name != null) {
                        cols.add(name.trim().toLowerCase());
                    }
                }
            }
        }
        return cols;
    }

    private static void ensureColumn(Connection conn, Set<String> columns, String table, String column, String definition) throws SQLException {
        if (columns.contains(column.toLowerCase())) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            columns.add(column.toLowerCase());
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
    }

    public List<ComponentMemoryEntry> list(String query, String moduleName, String screenName, int limit) {
        List<ComponentMemoryEntry> list = new ArrayList<>();
        String q = query == null ? null : query.trim();
        String sql = """
            SELECT *
            FROM component_memory
            WHERE ( ? IS NULL OR lower(component_name) LIKE '%' || lower(?) || '%' OR lower(ifnull(component_alias,'')) LIKE '%' || lower(?) || '%' )
              AND ( ? IS NULL OR lower(ifnull(module_name,'')) = lower(?) )
              AND ( ? IS NULL OR lower(ifnull(screen_name,'')) = lower(?) )
            ORDER BY (successful_attempts * 1.0) / (successful_attempts + failed_attempts + 1) DESC,
                     datetime(ifnull(last_success,'1970-01-01T00:00:00Z')) DESC,
                     id DESC
            LIMIT ?
            """;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            String qParam = (q == null || q.isEmpty()) ? null : q;
            ps.setString(1, qParam);
            ps.setString(2, qParam);
            ps.setString(3, qParam);
            ps.setString(4, moduleName == null || moduleName.isBlank() ? null : moduleName);
            ps.setString(5, moduleName == null || moduleName.isBlank() ? null : moduleName);
            ps.setString(6, screenName == null || screenName.isBlank() ? null : screenName);
            ps.setString(7, screenName == null || screenName.isBlank() ? null : screenName);
            ps.setInt(8, Math.max(1, Math.min(limit, 500)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ComponentMemoryRepository] Erro ao listar: " + e.getMessage());
        }
        return list;
    }

    public ComponentMemoryEntry findBest(String componentName, String moduleName, String screenName) {
        String name = componentName == null ? "" : componentName.trim();
        if (name.isEmpty()) {
            return null;
        }

        String normalized = ComponentNameExtractor.normalizeKey(name);

        List<ComponentMemoryEntry> candidates = new ArrayList<>();

        String sqlExact = """
            SELECT *
            FROM component_memory
            WHERE lower(component_name) = lower(?)
              AND ( ? IS NULL OR lower(ifnull(module_name,'')) = lower(?) )
              AND ( ? IS NULL OR lower(ifnull(screen_name,'')) = lower(?) )
            ORDER BY (successful_attempts * 1.0) / (successful_attempts + failed_attempts + 1) DESC,
                     datetime(ifnull(last_success,'1970-01-01T00:00:00Z')) DESC,
                     id DESC
            LIMIT 5
            """;

        String sqlAlias = """
            SELECT *
            FROM component_memory
            WHERE lower(ifnull(component_alias,'')) LIKE '%' || lower(?) || '%'
              AND ( ? IS NULL OR lower(ifnull(module_name,'')) = lower(?) )
              AND ( ? IS NULL OR lower(ifnull(screen_name,'')) = lower(?) )
            ORDER BY (successful_attempts * 1.0) / (successful_attempts + failed_attempts + 1) DESC,
                     datetime(ifnull(last_success,'1970-01-01T00:00:00Z')) DESC,
                     id DESC
            LIMIT 5
            """;

        String mod = moduleName == null || moduleName.isBlank() ? null : moduleName;
        String scr = screenName == null || screenName.isBlank() ? null : screenName;

        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sqlExact)) {
                ps.setString(1, name);
                ps.setString(2, mod);
                ps.setString(3, mod);
                ps.setString(4, scr);
                ps.setString(5, scr);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        candidates.add(map(rs));
                    }
                }
            }

            if (candidates.isEmpty()) {
                try (PreparedStatement ps = conn.prepareStatement(sqlAlias)) {
                    ps.setString(1, normalized.isEmpty() ? name : normalized);
                    ps.setString(2, mod);
                    ps.setString(3, mod);
                    ps.setString(4, scr);
                    ps.setString(5, scr);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            candidates.add(map(rs));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[ComponentMemoryRepository] Erro ao buscar melhor match: " + e.getMessage());
            return null;
        }

        if (candidates.isEmpty()) {
            return null;
        }

        ComponentMemoryEntry best = candidates.get(0);
        double bestScore = score(best, normalized, mod, scr);
        for (int i = 1; i < candidates.size(); i++) {
            ComponentMemoryEntry c = candidates.get(i);
            double s = score(c, normalized, mod, scr);
            if (s > bestScore) {
                best = c;
                bestScore = s;
            }
        }
        return best;
    }

    public ComponentMemoryEntry upsert(ComponentMemoryEntry entry) {
        if (entry == null || entry.getComponentName() == null || entry.getComponentName().trim().isEmpty()) {
            return null;
        }
        String now = Instant.now().toString();

        String componentName = entry.getComponentName().trim();
        String moduleName = safeTrim(entry.getModuleName());
        String screenName = safeTrim(entry.getScreenName());
        String embeddingText = safeTrim(entry.getEmbeddingText());
        if (embeddingText == null) {
            embeddingText = buildEmbeddingText(entry, componentName, moduleName, screenName);
        }

        String sql = """
            INSERT INTO component_memory
              (component_name, component_alias, component_type, module_name, screen_name, behavior_type,
               execution_strategy, fallback_strategy, successful_attempts, failed_attempts, last_success,
               learned_from_user, notes, description, examples, semantic_tags, embedding_text,
               rag_enabled, confidence_score, last_used_as_context, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(component_name, module_name, screen_name) DO UPDATE SET
              component_alias = excluded.component_alias,
              component_type = excluded.component_type,
              behavior_type = excluded.behavior_type,
              execution_strategy = excluded.execution_strategy,
              fallback_strategy = excluded.fallback_strategy,
              learned_from_user = excluded.learned_from_user,
              notes = excluded.notes,
              description = excluded.description,
              examples = excluded.examples,
              semantic_tags = excluded.semantic_tags,
              embedding_text = excluded.embedding_text,
              rag_enabled = excluded.rag_enabled,
              confidence_score = excluded.confidence_score,
              last_used_as_context = excluded.last_used_as_context,
              updated_at = excluded.updated_at
            """;

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, componentName);
            ps.setString(2, safeTrim(entry.getComponentAlias()));
            ps.setString(3, entry.getComponentType() != null ? entry.getComponentType().name() : null);
            ps.setString(4, moduleName);
            ps.setString(5, screenName);
            ps.setString(6, entry.getBehaviorType() != null ? entry.getBehaviorType().name() : null);
            ps.setString(7, safeTrim(entry.getExecutionStrategy()));
            ps.setString(8, safeTrim(entry.getFallbackStrategy()));
            ps.setInt(9, Math.max(0, entry.getSuccessfulAttempts()));
            ps.setInt(10, Math.max(0, entry.getFailedAttempts()));
            ps.setString(11, safeTrim(entry.getLastSuccess()));
            ps.setInt(12, entry.isLearnedFromUser() ? 1 : 0);
            ps.setString(13, safeTrim(entry.getNotes()));
            ps.setString(14, safeTrim(entry.getDescription()));
            ps.setString(15, safeTrim(entry.getExamples()));
            ps.setString(16, safeTrim(entry.getSemanticTags()));
            ps.setString(17, embeddingText);
            ps.setInt(18, entry.isRagEnabled() ? 1 : 0);
            ps.setDouble(19, entry.getConfidenceScore());
            ps.setString(20, safeTrim(entry.getLastUsedAsContext()));
            ps.setString(21, safeTrim(entry.getCreatedAt()) != null ? entry.getCreatedAt() : now);
            ps.setString(22, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ComponentMemoryRepository] Erro ao upsert: " + e.getMessage());
            return null;
        }

        return findBest(componentName, moduleName, screenName);
    }

    public List<ComponentMemoryEntry> listRagEnabled(int limit) {
        List<ComponentMemoryEntry> list = new ArrayList<>();
        String sql = """
            SELECT *
            FROM component_memory
            WHERE ifnull(rag_enabled, 1) = 1
            ORDER BY (successful_attempts * 1.0) / (successful_attempts + failed_attempts + 1) DESC,
                     datetime(ifnull(last_success,'1970-01-01T00:00:00Z')) DESC,
                     id DESC
            LIMIT ?
            """;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, Math.min(limit, 2000)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ComponentMemoryRepository] Erro ao listar RAG: " + e.getMessage());
        }
        return list;
    }

    public void markUsedAsContext(long id, boolean success) {
        String now = Instant.now().toString();
        double delta = success ? 0.05 : -0.05;
        String sql = """
            UPDATE component_memory
            SET last_used_as_context = ?,
                confidence_score = max(0, min(1, ifnull(confidence_score, 0) + ?)),
                updated_at = ?
            WHERE id = ?
            """;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, now);
            ps.setDouble(2, delta);
            ps.setString(3, now);
            ps.setLong(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ComponentMemoryRepository] Erro ao marcar uso RAG: " + e.getMessage());
        }
    }

    public long insertRagQueryLog(RagQueryLogEntry log) {
        if (log == null) {
            return -1;
        }
        String now = Instant.now().toString();
        String createdAt = safeTrim(log.getCreatedAt()) != null ? log.getCreatedAt() : now;

        String sql = """
            INSERT INTO rag_query_logs
              (test_execution_id, step_description, module_name, screen_name,
               retrieved_components, selected_component, score, used_in_prompt,
               prompt_context, llm_decision, execution_success, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, safeTrim(log.getTestExecutionId()));
            ps.setString(2, safeTrim(log.getStepDescription()));
            ps.setString(3, safeTrim(log.getModuleName()));
            ps.setString(4, safeTrim(log.getScreenName()));
            ps.setString(5, safeTrim(log.getRetrievedComponents()));
            ps.setString(6, safeTrim(log.getSelectedComponent()));
            ps.setDouble(7, log.getScore());
            ps.setInt(8, log.isUsedInPrompt() ? 1 : 0);
            ps.setString(9, safeTrim(log.getPromptContext()));
            ps.setString(10, safeTrim(log.getLlmDecision()));
            if (log.getExecutionSuccess() == null) {
                ps.setNull(11, Types.INTEGER);
            } else {
                ps.setInt(11, log.getExecutionSuccess() ? 1 : 0);
            }
            ps.setString(12, createdAt);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[ComponentMemoryRepository] Erro ao inserir log RAG: " + e.getMessage());
        }
        return -1;
    }

    public List<RagQueryLogEntry> listRagQueryLogs(String testExecutionId, String selectedComponent, int limit) {
        List<RagQueryLogEntry> list = new ArrayList<>();
        String sql = """
            SELECT *
            FROM rag_query_logs
            WHERE ( ? IS NULL OR lower(ifnull(test_execution_id,'')) = lower(?) )
              AND ( ? IS NULL OR lower(ifnull(selected_component,'')) = lower(?) )
            ORDER BY id DESC
            LIMIT ?
            """;
        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            String exec = (testExecutionId == null || testExecutionId.isBlank()) ? null : testExecutionId.trim();
            String sel = (selectedComponent == null || selectedComponent.isBlank()) ? null : selectedComponent.trim();
            ps.setString(1, exec);
            ps.setString(2, exec);
            ps.setString(3, sel);
            ps.setString(4, sel);
            ps.setInt(5, Math.max(1, Math.min(limit, 500)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RagQueryLogEntry e = new RagQueryLogEntry();
                    e.setId(rs.getLong("id"));
                    e.setTestExecutionId(rs.getString("test_execution_id"));
                    e.setStepDescription(rs.getString("step_description"));
                    e.setModuleName(rs.getString("module_name"));
                    e.setScreenName(rs.getString("screen_name"));
                    e.setRetrievedComponents(rs.getString("retrieved_components"));
                    e.setSelectedComponent(rs.getString("selected_component"));
                    e.setScore(rs.getDouble("score"));
                    e.setUsedInPrompt(rs.getInt("used_in_prompt") != 0);
                    e.setPromptContext(rs.getString("prompt_context"));
                    e.setLlmDecision(rs.getString("llm_decision"));
                    int execSuccess = rs.getInt("execution_success");
                    if (rs.wasNull()) {
                        e.setExecutionSuccess(null);
                    } else {
                        e.setExecutionSuccess(execSuccess != 0);
                    }
                    e.setCreatedAt(rs.getString("created_at"));
                    list.add(e);
                }
            }
        } catch (SQLException e) {
            System.err.println("[ComponentMemoryRepository] Erro ao listar logs RAG: " + e.getMessage());
        }
        return list;
    }

    public RagStats getRagStats() {
        RagStats stats = new RagStats();
        try (Connection conn = getConnection()) {
            stats.setRagEnabledCount(queryInt(conn, "SELECT count(*) FROM component_memory WHERE ifnull(rag_enabled, 1) = 1"));
            stats.setUsedAsContextCount(queryInt(conn, "SELECT count(*) FROM component_memory WHERE ifnull(trim(last_used_as_context), '') != ''"));
            stats.setAvgConfidence(queryDouble(conn, "SELECT ifnull(avg(ifnull(confidence_score, 0)), 0) FROM component_memory"));

            stats.setRagQueriesToday(queryInt(conn, """
                SELECT count(*)
                FROM rag_query_logs
                WHERE date(created_at) = date('now')
                """));
            stats.setRagUsedInPromptToday(queryInt(conn, """
                SELECT count(*)
                FROM rag_query_logs
                WHERE date(created_at) = date('now')
                  AND used_in_prompt = 1
                """));
            stats.setRagSuccessWithContextToday(queryInt(conn, """
                SELECT count(*)
                FROM rag_query_logs
                WHERE date(created_at) = date('now')
                  AND used_in_prompt = 1
                  AND execution_success = 1
                """));
            stats.setRagDistinctComponentsToday(queryInt(conn, """
                SELECT count(DISTINCT selected_component)
                FROM rag_query_logs
                WHERE date(created_at) = date('now')
                  AND used_in_prompt = 1
                  AND ifnull(trim(selected_component),'') != ''
                """));

            int used = stats.getRagUsedInPromptToday();
            int ok = stats.getRagSuccessWithContextToday();
            int hitRate = used <= 0 ? 0 : (int) Math.round(((double) ok / (double) used) * 100.0);
            stats.setRagHitRateToday(hitRate);
        } catch (SQLException e) {
            System.err.println("[ComponentMemoryRepository] Erro ao gerar stats RAG: " + e.getMessage());
        }
        return stats;
    }

    private static int queryInt(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static double queryDouble(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        }
    }

    public boolean deleteById(long id) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM component_memory WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ComponentMemoryRepository] Erro ao deletar: " + e.getMessage());
            return false;
        }
    }

    public void recordSuccess(long id) {
        recordAttempt(id, true, null);
    }

    public void recordFailure(long id, String error) {
        recordAttempt(id, false, error);
    }

    private void recordAttempt(long id, boolean success, String error) {
        String now = Instant.now().toString();
        String sql = success
            ? """
                UPDATE component_memory
                SET successful_attempts = successful_attempts + 1,
                    last_success = ?,
                    updated_at = ?,
                    notes = CASE WHEN ? IS NULL OR trim(?) = '' THEN notes ELSE ? END
                WHERE id = ?
                """
            : """
                UPDATE component_memory
                SET failed_attempts = failed_attempts + 1,
                    updated_at = ?,
                    notes = CASE WHEN ? IS NULL OR trim(?) = '' THEN notes ELSE ? END
                WHERE id = ?
                """;

        try (Connection conn = getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (success) {
                ps.setString(1, now);
                ps.setString(2, now);
                ps.setString(3, error);
                ps.setString(4, error);
                ps.setString(5, error);
                ps.setLong(6, id);
            } else {
                ps.setString(1, now);
                ps.setString(2, error);
                ps.setString(3, error);
                ps.setString(4, error);
                ps.setLong(5, id);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ComponentMemoryRepository] Erro ao registrar tentativa: " + e.getMessage());
        }
    }

    private static String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static double score(ComponentMemoryEntry entry, String normalizedQuery, String moduleName, String screenName) {
        double rate = entry.getSuccessRate();
        double attempts = Math.min(20.0, (double) (entry.getSuccessfulAttempts() + entry.getFailedAttempts()));
        double conf = (attempts / 20.0) * 10.0;
        double modBoost = Objects.equals(safeLower(entry.getModuleName()), safeLower(moduleName)) ? 6.0 : 0.0;
        double scrBoost = Objects.equals(safeLower(entry.getScreenName()), safeLower(screenName)) ? 4.0 : 0.0;
        double nameBoost = Objects.equals(ComponentNameExtractor.normalizeKey(entry.getComponentName()), normalizedQuery) ? 8.0 : 0.0;
        return rate + conf + modBoost + scrBoost + nameBoost;
    }

    private static String safeLower(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private static ComponentMemoryEntry map(ResultSet rs) throws SQLException {
        ComponentMemoryEntry e = new ComponentMemoryEntry();
        e.setId(rs.getLong("id"));
        e.setComponentName(rs.getString("component_name"));
        e.setComponentAlias(rs.getString("component_alias"));
        e.setComponentType(parseEnum(ComponentType.class, rs.getString("component_type")));
        e.setModuleName(rs.getString("module_name"));
        e.setScreenName(rs.getString("screen_name"));
        e.setBehaviorType(parseEnum(BehaviorType.class, rs.getString("behavior_type")));
        e.setExecutionStrategy(rs.getString("execution_strategy"));
        e.setFallbackStrategy(rs.getString("fallback_strategy"));
        e.setSuccessfulAttempts(rs.getInt("successful_attempts"));
        e.setFailedAttempts(rs.getInt("failed_attempts"));
        e.setLastSuccess(rs.getString("last_success"));
        e.setLearnedFromUser(rs.getInt("learned_from_user") == 1);
        e.setNotes(rs.getString("notes"));
        e.setDescription(rs.getString("description"));
        e.setExamples(rs.getString("examples"));
        e.setSemanticTags(rs.getString("semantic_tags"));
        e.setEmbeddingText(rs.getString("embedding_text"));
        e.setRagEnabled(rs.getInt("rag_enabled") != 0);
        e.setConfidenceScore(rs.getDouble("confidence_score"));
        e.setLastUsedAsContext(rs.getString("last_used_as_context"));
        e.setCreatedAt(rs.getString("created_at"));
        e.setUpdatedAt(rs.getString("updated_at"));
        return e;
    }

    private static String buildEmbeddingText(ComponentMemoryEntry entry, String componentName, String moduleName, String screenName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Componente ").append(componentName);
        if (screenName != null && !screenName.isBlank()) {
            sb.append(" na tela ").append(screenName);
        }
        if (moduleName != null && !moduleName.isBlank()) {
            sb.append(" do módulo ").append(moduleName);
        }
        if (entry.getComponentType() != null) {
            sb.append(". Tipo ").append(entry.getComponentType().name());
        }
        if (entry.getBehaviorType() != null) {
            sb.append(". Comportamento ").append(entry.getBehaviorType().name());
        }
        if (entry.getDescription() != null && !entry.getDescription().isBlank()) {
            sb.append(". ").append(entry.getDescription().trim());
        }
        if (entry.getSemanticTags() != null && !entry.getSemanticTags().isBlank()) {
            sb.append(". Tags: ").append(entry.getSemanticTags().trim());
        }
        if (entry.getNotes() != null && !entry.getNotes().isBlank()) {
            sb.append(". ").append(entry.getNotes().trim());
        }
        if (entry.getExamples() != null && !entry.getExamples().isBlank()) {
            sb.append(". Exemplos: ").append(entry.getExamples().trim());
        }
        String raw = sb.toString().trim();
        return raw.isEmpty() ? null : raw;
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return Enum.valueOf(type, t);
        } catch (Exception ignored) {
            return null;
        }
    }
}


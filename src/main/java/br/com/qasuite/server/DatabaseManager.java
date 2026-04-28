package br.com.qasuite.server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.sql.*;
import java.util.*;

public class DatabaseManager {
    private static final String DB_PATH = "data/qa_agent.db";
    private static final Gson gson = new Gson();

    public DatabaseManager() {
        initDatabase();
    }

    private void initDatabase() {
        try {
            // Create data directory if not exists
            File dataDir = new File("data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }

            try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
                // Tests table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS tests (
                        id TEXT PRIMARY KEY,
                        module TEXT NOT NULL,
                        menu TEXT NOT NULL,
                        test_type TEXT,
                        priority TEXT,
                        name TEXT NOT NULL,
                        description TEXT,
                        test_data TEXT,
                        feature TEXT,
                        java TEXT,
                        status TEXT,
                        created_at TEXT,
                        updated_at TEXT
                    )
                    """);

                // Menu structure table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS menu_structure (
                        key TEXT PRIMARY KEY,
                        name TEXT NOT NULL,
                        structure_json TEXT NOT NULL
                    )
                    """);

                // Config table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS config (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """);

                System.out.println("Database initialized successfully!");
            }
        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
    }

    // ==================== TESTS ====================

    public List<Map<String, Object>> loadTests() {
        List<Map<String, Object>> tests = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM tests")) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> test = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    test.put(columnName, value);
                }
                // Convert JSON fields
                if (test.get("test_data") != null) {
                    test.put("testData", gson.fromJson((String) test.get("test_data"), Map.class));
                    test.remove("test_data");
                }
                if (test.get("test_type") != null) {
                    test.put("testType", test.get("test_type"));
                    test.remove("test_type");
                }
                tests.add(test);
            }
        } catch (SQLException e) {
            System.err.println("Error loading tests: " + e.getMessage());
        }
        return tests;
    }

    public void saveTest(Map<String, Object> test) {
        String sql = """
            INSERT OR REPLACE INTO tests 
            (id, module, menu, test_type, priority, name, description, test_data, feature, java, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, (String) test.get("id"));
            pstmt.setString(2, (String) test.get("module"));
            pstmt.setString(3, (String) test.get("menu"));
            pstmt.setString(4, (String) test.get("testType"));
            pstmt.setString(5, (String) test.get("priority"));
            pstmt.setString(6, (String) test.get("name"));
            pstmt.setString(7, (String) test.get("description"));
            pstmt.setString(8, gson.toJson(test.get("testData")));
            pstmt.setString(9, (String) test.get("feature"));
            pstmt.setString(10, (String) test.get("java"));
            pstmt.setString(11, (String) test.getOrDefault("status", "draft"));
            pstmt.setString(12, (String) test.get("createdAt"));
            pstmt.setString(13, (String) test.get("updatedAt"));

            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving test: " + e.getMessage());
        }
    }

    public void deleteTest(String testId) {
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM tests WHERE id = ?")) {
            pstmt.setString(1, testId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error deleting test: " + e.getMessage());
        }
    }

    // ==================== MENU STRUCTURE ====================

    public Map<String, Object> loadMenuStructure() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT structure_json FROM menu_structure WHERE key = 'main'")) {

            if (rs.next()) {
                String json = rs.getString("structure_json");
                return gson.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
            }
        } catch (SQLException e) {
            System.err.println("Error loading menu structure: " + e.getMessage());
        }
        return new HashMap<>();
    }

    public void saveMenuStructure(Map<String, Object> structure) {
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "INSERT OR REPLACE INTO menu_structure (key, name, structure_json) VALUES (?, ?, ?)")) {

            pstmt.setString(1, "main");
            pstmt.setString(2, "Menu Structure");
            pstmt.setString(3, gson.toJson(structure));
            pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error saving menu structure: " + e.getMessage());
        }
    }

    // ==================== CONFIG ====================

    public Map<String, Object> loadConfig() {
        Map<String, Object> config = new HashMap<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT key, value FROM config")) {

            while (rs.next()) {
                String key = rs.getString("key");
                String value = rs.getString("value");
                // Try to parse as JSON, otherwise store as string
                try {
                    Object parsed = gson.fromJson(value, Object.class);
                    config.put(key, parsed);
                } catch (Exception e) {
                    config.put(key, value);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading config: " + e.getMessage());
        }
        return config;
    }

    public void saveConfig(Map<String, Object> config) {
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                 "INSERT OR REPLACE INTO config (key, value) VALUES (?, ?)")) {

            for (Map.Entry<String, Object> entry : config.entrySet()) {
                pstmt.setString(1, entry.getKey());
                Object value = entry.getValue();
                if (value instanceof Map || value instanceof List) {
                    pstmt.setString(2, gson.toJson(value));
                } else {
                    pstmt.setString(2, String.valueOf(value));
                }
                pstmt.executeUpdate();
            }

        } catch (SQLException e) {
            System.err.println("Error saving config: " + e.getMessage());
        }
    }
}

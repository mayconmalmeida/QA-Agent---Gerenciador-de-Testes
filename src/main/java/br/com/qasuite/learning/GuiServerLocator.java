package br.com.qasuite.learning;

import java.nio.file.Files;
import java.nio.file.Path;

public class GuiServerLocator {
    public String getBaseUrl() {
        int port = readPortFromFile();
        return "http://localhost:" + port;
    }

    private int readPortFromFile() {
        try {
            Path p = Path.of("data/server.port");
            if (Files.exists(p)) {
                String content = Files.readString(p).trim();
                if (!content.isBlank()) {
                    int port = Integer.parseInt(content);
                    if (port >= 1 && port <= 65535) {
                        return port;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return 8081;
    }
}


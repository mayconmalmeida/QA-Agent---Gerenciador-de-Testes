package br.com.qasuite.core;

import br.com.qasuite.domain.StepResult;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.Desktop;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Construtor de relatórios HTML moderno com timeline visual
 * Gera relatórios self-contained com CSS moderno e interatividade
 */
public class TimelineReportBuilder {

    private static final Gson gson = new Gson();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * Gera relatório a partir dos resultados de execução salvos em JSON
     */
    public static void generateFromExecutionResults() {
        try {
            Path resultsFile = Paths.get("data/execution_results.json");
            if (!Files.exists(resultsFile)) {
                System.err.println("[TimelineReportBuilder] No execution results found");
                return;
            }

            String content = Files.readString(resultsFile);
            JsonObject data = JsonParser.parseString(content).getAsJsonObject();

            String testName = data.get("testName").getAsString();
            long executionTime = data.get("executionTime").getAsLong();
            int totalSteps = data.get("totalSteps").getAsInt();
            JsonArray steps = data.getAsJsonArray("steps");

            // Convert to StepResult objects
            List<StepResult> results = new ArrayList<>();
            for (int i = 0; i < steps.size(); i++) {
                JsonObject step = steps.get(i).getAsJsonObject();
                // Create StepResult from JSON data
                results.add(parseStepResult(step));
            }

            // Generate report
            String html = generateTimelineReport(testName, executionTime, results);

            // Save report
            Path outputPath = Paths.get("output/reports/timeline_report.html");
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, html);

            System.out.println("[TimelineReportBuilder] Report generated: " + outputPath);

            // Open in browser if configured
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(outputPath.toUri());
            }

        } catch (Exception e) {
            System.err.println("[TimelineReportBuilder] Error generating report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Parse StepResult from JSON
     */
    private static StepResult parseStepResult(JsonObject json) {
        // Create a minimal StepResult for display
        // Note: In production, you'd deserialize properly
        return new StepResult(null); // Simplified for now
    }

    /**
     * Gera HTML do relatório com timeline
     */
    public static String generateTimelineReport(String testName, long executionTime, List<StepResult> steps) {
        LocalDateTime execDate = LocalDateTime.ofInstant(Instant.ofEpochMilli(executionTime), ZoneId.systemDefault());

        int successCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        long totalDuration = 0;

        StringBuilder timelineHtml = new StringBuilder();

        for (int i = 0; i < steps.size(); i++) {
            StepResult step = steps.get(i);
            totalDuration += step.getDurationMs();

            String status = step.getStatus().name();
            switch (status) {
                case "SUCCESS":
                    successCount++;
                    break;
                case "FAILED":
                    failedCount++;
                    break;
                default:
                    skippedCount++;
                    break;
            }

            String statusColor = getStatusColor(status);
            String statusIcon = getStatusIcon(status);
            String actionColor = getActionColor(step.getStep().getAction().name());

            timelineHtml.append(String.format("""
                <div class="timeline-item %s" data-step="%d">
                    <div class="timeline-marker" style="background: %s; border-color: %s;">
                        <span class="timeline-icon">%s</span>
                    </div>
                    <div class="timeline-content">
                        <div class="step-header">
                            <span class="step-number">#%d</span>
                            <span class="step-action" style="background: %s20; color: %s;">%s</span>
                            <span class="step-status %s">%s</span>
                            <span class="step-duration">⏱ %dms</span>
                        </div>
                        <div class="step-description">%s</div>
                        %s
                        %s
                    </div>
                </div>
                """,
                status.toLowerCase(),
                i + 1,
                statusColor,
                statusColor,
                statusIcon,
                i + 1,
                actionColor,
                actionColor,
                step.getStep().getAction().name(),
                status.toLowerCase(),
                status,
                step.getDurationMs(),
                step.getStep().getDescription(),
                step.getStep().getTarget() != null ? String.format("<div class='step-target'>🎯 Target: %s</div>", step.getStep().getTarget()) : "",
                step.getStep().getValue() != null ? String.format("<div class='step-value'>📝 Value: %s</div>", step.getStep().getValue()) : ""
            ));
        }

        double successRate = steps.size() > 0 ? (double) successCount / steps.size() * 100 : 0;

        return String.format("""
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>QA Agent - Timeline Report</title>
                <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    
                    body {
                        font-family: 'Segoe UI', -apple-system, BlinkMacSystemFont, sans-serif;
                        background: linear-gradient(135deg, #1a1a2e 0%%, #16213e 100%%);
                        color: #e4e4e4;
                        min-height: 100vh;
                        padding: 20px;
                    }
                    
                    .container {
                        max-width: 1200px;
                        margin: 0 auto;
                    }
                    
                    .header {
                        text-align: center;
                        padding: 40px 20px;
                        background: rgba(255,255,255,0.05);
                        border-radius: 20px;
                        margin-bottom: 30px;
                        backdrop-filter: blur(10px);
                    }
                    
                    .header h1 {
                        font-size: 2.5rem;
                        margin-bottom: 10px;
                        background: linear-gradient(90deg, #00d4ff, #7b2cbf);
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                    }
                    
                    .header .subtitle {
                        color: #888;
                        font-size: 1.1rem;
                    }
                    
                    .stats-grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                        gap: 20px;
                        margin-bottom: 30px;
                    }
                    
                    .stat-card {
                        background: rgba(255,255,255,0.05);
                        border-radius: 15px;
                        padding: 25px;
                        text-align: center;
                        backdrop-filter: blur(10px);
                        border: 1px solid rgba(255,255,255,0.1);
                        transition: transform 0.3s;
                    }
                    
                    .stat-card:hover {
                        transform: translateY(-5px);
                    }
                    
                    .stat-card.success { border-top: 4px solid #10b981; }
                    .stat-card.failed { border-top: 4px solid #ef4444; }
                    .stat-card.total { border-top: 4px solid #3b82f6; }
                    .stat-card.rate { border-top: 4px solid #f59e0b; }
                    
                    .stat-value {
                        font-size: 2.5rem;
                        font-weight: 700;
                        margin-bottom: 5px;
                    }
                    
                    .stat-card.success .stat-value { color: #10b981; }
                    .stat-card.failed .stat-value { color: #ef4444; }
                    .stat-card.total .stat-value { color: #3b82f6; }
                    .stat-card.rate .stat-value { color: #f59e0b; }
                    
                    .stat-label {
                        color: #888;
                        font-size: 0.9rem;
                        text-transform: uppercase;
                        letter-spacing: 1px;
                    }
                    
                    .timeline {
                        background: rgba(255,255,255,0.05);
                        border-radius: 20px;
                        padding: 30px;
                        backdrop-filter: blur(10px);
                    }
                    
                    .timeline-title {
                        font-size: 1.5rem;
                        margin-bottom: 25px;
                        display: flex;
                        align-items: center;
                        gap: 10px;
                    }
                    
                    .timeline-item {
                        display: flex;
                        margin-bottom: 20px;
                        position: relative;
                        animation: slideIn 0.5s ease-out forwards;
                        opacity: 0;
                    }
                    
                    @keyframes slideIn {
                        from {
                            opacity: 0;
                            transform: translateX(-20px);
                        }
                        to {
                            opacity: 1;
                            transform: translateX(0);
                        }
                    }
                    
                    .timeline-item:nth-child(1) { animation-delay: 0.1s; }
                    .timeline-item:nth-child(2) { animation-delay: 0.2s; }
                    .timeline-item:nth-child(3) { animation-delay: 0.3s; }
                    .timeline-item:nth-child(4) { animation-delay: 0.4s; }
                    .timeline-item:nth-child(5) { animation-delay: 0.5s; }
                    
                    .timeline-marker {
                        width: 40px;
                        height: 40px;
                        border-radius: 50%%;
                        border: 3px solid;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        margin-right: 20px;
                        flex-shrink: 0;
                        font-size: 1.1rem;
                    }
                    
                    .timeline-content {
                        flex: 1;
                        background: rgba(255,255,255,0.03);
                        border-radius: 12px;
                        padding: 20px;
                        border-left: 4px solid;
                    }
                    
                    .timeline-item.success .timeline-content { border-color: #10b981; }
                    .timeline-item.failed .timeline-content { border-color: #ef4444; }
                    .timeline-item.pending .timeline-content { border-color: #6b7280; }
                    .timeline-item.skipped .timeline-content { border-color: #f59e0b; }
                    
                    .step-header {
                        display: flex;
                        align-items: center;
                        gap: 15px;
                        margin-bottom: 10px;
                        flex-wrap: wrap;
                    }
                    
                    .step-number {
                        background: rgba(255,255,255,0.1);
                        padding: 5px 12px;
                        border-radius: 20px;
                        font-weight: 600;
                        font-size: 0.85rem;
                    }
                    
                    .step-action {
                        padding: 4px 10px;
                        border-radius: 6px;
                        font-size: 0.75rem;
                        font-weight: 600;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                    }
                    
                    .step-status {
                        padding: 4px 10px;
                        border-radius: 6px;
                        font-size: 0.75rem;
                        font-weight: 600;
                        text-transform: uppercase;
                    }
                    
                    .step-status.success { background: #10b98120; color: #10b981; }
                    .step-status.failed { background: #ef444420; color: #ef4444; }
                    .step-status.pending { background: #6b728020; color: #6b7280; }
                    .step-status.skipped { background: #f59e0b20; color: #f59e0b; }
                    
                    .step-duration {
                        color: #888;
                        font-size: 0.85rem;
                        margin-left: auto;
                    }
                    
                    .step-description {
                        color: #ccc;
                        font-size: 1rem;
                        margin-bottom: 8px;
                    }
                    
                    .step-target, .step-value {
                        color: #888;
                        font-size: 0.9rem;
                        margin-top: 5px;
                    }
                    
                    .progress-bar {
                        width: 100%%;
                        height: 8px;
                        background: rgba(255,255,255,0.1);
                        border-radius: 4px;
                        overflow: hidden;
                        margin: 20px 0;
                    }
                    
                    .progress-fill {
                        height: 100%%;
                        background: linear-gradient(90deg, #10b981, #3b82f6);
                        border-radius: 4px;
                        transition: width 1s ease-out;
                    }
                    
                    .filters {
                        display: flex;
                        gap: 10px;
                        margin-bottom: 20px;
                        flex-wrap: wrap;
                    }
                    
                    .filter-btn {
                        padding: 8px 16px;
                        border: none;
                        border-radius: 20px;
                        background: rgba(255,255,255,0.1);
                        color: #fff;
                        cursor: pointer;
                        transition: all 0.3s;
                    }
                    
                    .filter-btn:hover, .filter-btn.active {
                        background: #3b82f6;
                    }
                    
                    .timestamp {
                        text-align: center;
                        color: #888;
                        margin-top: 30px;
                        font-size: 0.9rem;
                    }
                    
                    @media (max-width: 768px) {
                        .stats-grid {
                            grid-template-columns: repeat(2, 1fr);
                        }
                        
                        .step-header {
                            flex-direction: column;
                            align-items: flex-start;
                            gap: 8px;
                        }
                        
                        .step-duration {
                            margin-left: 0;
                        }
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1><i class="fas fa-robot"></i> QA Agent</h1>
                        <div class="subtitle">Timeline Report - %s</div>
                        <div style="margin-top: 15px; color: #666;">
                            <i class="far fa-calendar-alt"></i> Executed on %s
                        </div>
                    </div>
                    
                    <div class="stats-grid">
                        <div class="stat-card total">
                            <div class="stat-value">%d</div>
                            <div class="stat-label">Total Steps</div>
                        </div>
                        <div class="stat-card success">
                            <div class="stat-value">%d</div>
                            <div class="stat-label">Passed</div>
                        </div>
                        <div class="stat-card failed">
                            <div class="stat-value">%d</div>
                            <div class="stat-label">Failed</div>
                        </div>
                        <div class="stat-card rate">
                            <div class="stat-value">%.1f%%</div>
                            <div class="stat-label">Success Rate</div>
                        </div>
                    </div>
                    
                    <div class="progress-bar">
                        <div class="progress-fill" style="width: %.1f%%;"></div>
                    </div>
                    
                    <div class="timeline">
                        <div class="timeline-title">
                            <i class="fas fa-stream"></i>
                            Execution Timeline
                        </div>
                        
                        <div class="filters">
                            <button class="filter-btn active" onclick="filterSteps('all')">All</button>
                            <button class="filter-btn" onclick="filterSteps('success')">Passed</button>
                            <button class="filter-btn" onclick="filterSteps('failed')">Failed</button>
                        </div>
                        
                        %s
                    </div>
                    
                    <div class="timestamp">
                        <i class="far fa-clock"></i> Total Duration: %dms | Generated at %s
                    </div>
                </div>
                
                <script>
                    function filterSteps(type) {
                        const items = document.querySelectorAll('.timeline-item');
                        const buttons = document.querySelectorAll('.filter-btn');
                        
                        buttons.forEach(btn => btn.classList.remove('active'));
                        event.target.classList.add('active');
                        
                        items.forEach(item => {
                            if (type === 'all' || item.classList.contains(type)) {
                                item.style.display = 'flex';
                            } else {
                                item.style.display = 'none';
                            }
                        });
                    }
                    
                    // Animate progress bar on load
                    window.addEventListener('load', () => {
                        const progressFill = document.querySelector('.progress-fill');
                        const width = progressFill.style.width;
                        progressFill.style.width = '0%%';
                        setTimeout(() => {
                            progressFill.style.width = width;
                        }, 100);
                    });
                </script>
            </body>
            </html>
            """,
            testName,
            execDate.format(formatter),
            steps.size(),
            successCount,
            failedCount,
            successRate,
            successRate,
            timelineHtml.toString(),
            totalDuration,
            LocalDateTime.now().format(formatter)
        );
    }

    private static String getStatusColor(String status) {
        return switch (status) {
            case "SUCCESS" -> "#10b981";
            case "FAILED" -> "#ef4444";
            case "PENDING" -> "#6b7280";
            case "SKIPPED" -> "#f59e0b";
            default -> "#6b7280";
        };
    }

    private static String getStatusIcon(String status) {
        return switch (status) {
            case "SUCCESS" -> "✓";
            case "FAILED" -> "✗";
            case "PENDING" -> "○";
            case "SKIPPED" -> "→";
            default -> "○";
        };
    }

    private static String getActionColor(String action) {
        return switch (action) {
            case "NAVIGATE" -> "#3b82f6";
            case "CLICK" -> "#10b981";
            case "FILL" -> "#f59e0b";
            case "SELECT" -> "#8b5cf6";
            case "ASSERT" -> "#ef4444";
            case "WAIT" -> "#6b7280";
            default -> "#6b7280";
        };
    }
}

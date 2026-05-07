package br.com.qasuite.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Resultado da execução de um passo de teste
 */
public class StepResult {
    private TestStep step;
    private StepStatus status;
    private String message;
    private String errorDetails;
    private String screenshotPath;
    private Instant startTime;
    private Instant endTime;
    private int retryCount;
    private String elementUsed;

    public StepResult(TestStep step) {
        this.step = step;
        this.status = StepStatus.PENDING;
        this.startTime = Instant.now();
        this.retryCount = 0;
    }

    public void markRunning() {
        this.status = StepStatus.RUNNING;
        this.startTime = Instant.now();
    }

    public void markSuccess(String message) {
        this.status = StepStatus.SUCCESS;
        this.message = message;
        this.endTime = Instant.now();
    }

    public void markFailed(String error) {
        this.status = StepStatus.FAILED;
        this.errorDetails = error;
        this.endTime = Instant.now();
    }

    public void markFailed(String error, String screenshotPath) {
        markFailed(error);
        this.screenshotPath = screenshotPath;
    }

    public void markSkipped(String reason) {
        this.status = StepStatus.SKIPPED;
        this.message = reason;
        this.endTime = Instant.now();
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    public Duration getDuration() {
        if (startTime == null) return Duration.ZERO;
        Instant end = endTime != null ? endTime : Instant.now();
        return Duration.between(startTime, end);
    }

    public long getDurationMs() {
        return getDuration().toMillis();
    }

    // Getters e Setters
    public TestStep getStep() {
        return step;
    }

    public StepStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public String getScreenshotPath() {
        return screenshotPath;
    }

    public void setScreenshotPath(String screenshotPath) {
        this.screenshotPath = screenshotPath;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getElementUsed() {
        return elementUsed;
    }

    public void setElementUsed(String elementUsed) {
        this.elementUsed = elementUsed;
    }

    public boolean isSuccess() {
        return status == StepStatus.SUCCESS;
    }

    public boolean isFailed() {
        return status == StepStatus.FAILED;
    }
}

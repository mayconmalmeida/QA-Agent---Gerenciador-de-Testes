# QA Agent - Start Server Script (Java + SQLite)
Write-Host "=================================================="
Write-Host "  QA Agent - Starting Java Server"
Write-Host "=================================================="
Write-Host ""

# Check if Maven is installed
$mavenCheck = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mavenCheck) {
    Write-Host "[WARNING] Maven not found in PATH" -ForegroundColor Yellow
    Write-Host "Using Maven from IntelliJ IDEA..."
    $env:MAVEN_HOME = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3"
    $env:PATH = "$env:MAVEN_HOME\bin;$env:PATH"
}

# Create data directory if it doesn't exist
if (-not (Test-Path "data")) {
    New-Item -ItemType Directory -Path "data" | Out-Null
}

# Compile project
Write-Host "[INFO] Compiling project..."
$compileResult = & mvn clean compile -q
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Failed to compile" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Start Java server
Write-Host ""
Write-Host "[INFO] Starting Java server with SQLite..." -ForegroundColor Green
Write-Host "[INFO] Access: http://localhost:8080"
Write-Host "[INFO] Database: data\qa_agent.db"
Write-Host ""

mvn exec:java -Dexec.mainClass="br.com.qasuite.server.GuiServer" -q

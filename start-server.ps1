# QA Agent - Start Server Script
Write-Host "=================================================="
Write-Host "  QA Agent - Starting Server"
Write-Host "=================================================="
Write-Host ""

# Check if Java is installed
$javaCheck = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCheck) {
    Write-Host "[ERROR] Java not found in PATH" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Check if Maven is installed
$mavenCheck = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mavenCheck) {
    Write-Host "[WARNING] Maven not found in PATH" -ForegroundColor Yellow
    Write-Host "Using Maven from IntelliJ IDEA..."
    $env:MAVEN_HOME = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3"
    $env:PATH = "$env:MAVEN_HOME\bin;$env:PATH"
}

# Compile project
Write-Host "[INFO] Compiling project..."
$compileResult = & mvn clean compile
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Failed to compile" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# Create data directory if it doesn't exist
if (-not (Test-Path "data")) {
    New-Item -ItemType Directory -Path "data" | Out-Null
}

# Start Java server
Write-Host ""
Write-Host "[INFO] Starting Java server..."
Write-Host "[INFO] Access: http://localhost:8080"
Write-Host ""

java -cp "target\classes;target\dependency\*" br.com.sinncosaude.server.GuiServer

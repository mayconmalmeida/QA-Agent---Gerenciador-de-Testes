# QA Agent - Start Server Script (Java + SQLite)
Write-Host "=================================================="
Write-Host "  QA Agent - Starting Java Server"
Write-Host "=================================================="
Write-Host ""

# Check if Maven is installed
$mavenCheck = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mavenCheck) {
    Write-Host "[ERROR] Maven not found in PATH" -ForegroundColor Red
    Write-Host "Instale o Maven 3.9+ e garanta que o comando 'mvn' funciona no terminal." -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit 1
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
Write-Host "[INFO] Tentando portas: 8080-8090 (primeira disponível)"
Write-Host "[INFO] Database: data\qa_agent.db"
Write-Host ""
Write-Host "Aguarde o console do Java mostrar a porta real..."
Write-Host ""

$env:MAVEN_OPTS=""
mvn --% exec:java -Dexec.mainClass=br.com.qasuite.server.GuiServer -Dexec.classpathScope=compile

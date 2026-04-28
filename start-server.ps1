# QA Agent - Start Server Script (Python)
Write-Host "=================================================="
Write-Host "  QA Agent - Starting Python Server"
Write-Host "=================================================="
Write-Host ""

# Check if Python is installed
$pythonCheck = Get-Command python -ErrorAction SilentlyContinue
if (-not $pythonCheck) {
    Write-Host "[ERROR] Python not found in PATH" -ForegroundColor Red
    Write-Host "Please install Python from https://python.org" -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit 1
}

# Create data directory if it doesn't exist
if (-not (Test-Path "data")) {
    New-Item -ItemType Directory -Path "data" | Out-Null
}

# Start Python server
Write-Host ""
Write-Host "[INFO] Starting Python server with SQLite..." -ForegroundColor Green
Write-Host "[INFO] Access: http://localhost:8080"
Write-Host "[INFO] Database: data\qa_agent.db"
Write-Host ""

python gui\server.py

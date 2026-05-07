@echo off
echo =================================================
echo   QA Agent Server (Java + SQLite)
echo =================================================
echo.

if not exist data mkdir data

echo [0/3] Stopping any existing server...
echo.

REM Mata qualquer processo Java existente silenciosamente
taskkill /F /IM java.exe 2>nul

echo [OK] Servers stopped
echo.

echo [1/3] Cleaning and compiling project...
call mvn clean compile -q
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed
    pause
    exit /b 1
)
echo [OK] Compiled successfully!
echo.

echo [2/3] Starting Java server on NEW PORT...
start http://localhost:8081
echo.
echo =================================================
echo   IMPORTANTE: LIMPE O CACHE DO NAVEGADOR!
echo =================================================
echo.
echo Para ver as novas funcionalidades:
echo   1. Pressione Ctrl + Shift + R no navegador
echo   2. Ou abra: Ctrl + Shift + N (modo anonimo)
echo   3. Ou limpe o cache: Ctrl + Shift + Del
echo.
echo Novas funcionalidades:
echo   - Configuracoes de Execucao (Visual/Headless)
echo   - Preview de Passos com IA
echo   - Timeline Report
echo   - Execucao Paralela
echo   - WebSocket em tempo real
echo.
echo =================================================
echo [3/3] Running server...

call mvn exec:java -Dexec.mainClass="br.com.qasuite.server.GuiServer" -q

pause

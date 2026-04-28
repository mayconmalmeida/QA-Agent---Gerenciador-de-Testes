@echo off
echo Starting QA Agent Server (Java + SQLite)...
echo.

if not exist data mkdir data

echo Compiling project...
call mvn clean compile -q
if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed
    pause
    exit /b 1
)

echo Starting Java server with SQLite...
call mvn exec:java -Dexec.mainClass="br.com.qasuite.server.GuiServer" -q

pause

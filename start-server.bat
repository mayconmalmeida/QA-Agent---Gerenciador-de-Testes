@echo off
echo Starting QA Agent Server...
echo.

if not exist data mkdir data

set MAVEN_HOME=C:\Program Files\JetBrains\IntelliJ IDEA 2026.1\plugins\maven\lib\maven3
set PATH=%MAVEN_HOME%\bin;%PATH%

echo Compiling project...
call mvn clean compile -X
if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed
    pause
    exit /b 1
)

echo Starting server...
call mvn exec:java -Dexec.mainClass="br.com.sinncosaude.server.GuiServer"

pause

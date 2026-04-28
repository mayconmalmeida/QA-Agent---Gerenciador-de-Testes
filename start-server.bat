@echo off
echo Starting QA Agent Server (Python)...
echo.

if not exist data mkdir data

echo Iniciando servidor Python com SQLite...
python gui\server.py

pause

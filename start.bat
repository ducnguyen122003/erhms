@echo off
cd /d "%~dp0"
if not exist .env copy .env.example .env >nul
docker compose up --build -d
if errorlevel 1 (
 echo Khoi dong that bai. Kiem tra Docker Desktop va file .env.
 pause
 exit /b 1
)
echo Mo http://localhost:5173
start http://localhost:5173
pause

@echo off
cd /d "%~dp0"
if not exist .env copy .env.example .env >nul
docker compose up --build -d
if errorlevel 1 (
 echo Khoi dong that bai. Kiem tra Docker Desktop va file .env.
 pause
 exit /b 1
)
echo Web tu van: http://localhost:8081
echo Web quan ly: http://localhost:8082
start http://localhost:8081
pause

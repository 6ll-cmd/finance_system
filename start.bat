@echo off
cd /d "%~dp0"

echo.
echo ========================================
echo    Invoice Manager - Starting...
echo ========================================
echo.

where node >nul 2>&1
if %errorlevel% neq 0 (
  echo [X] Node.js not found.
  echo     Download: https://nodejs.org
  echo.
  pause
  exit
)

if not exist "node_modules" (
  echo [!] First run - installing...
  npm install
  echo.
)

REM Kill any existing server on port 3456
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3456 "') do (
  echo [!] Old server found, stopping...
  taskkill /f /pid %%a >nul 2>&1
  timeout /t 2 /nobreak >nul
)

echo [*] Starting server...
echo [*] Browser: http://localhost:3456
echo.

node --no-warnings server.js
pause

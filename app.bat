@echo off
cd /d "%~dp0"
title 发票管家

REM Kill any existing server on port 3456
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3456 "') do taskkill /f /pid %%a >nul 2>&1

REM Start server
if exist "发票管家.exe" (
  start /b "" "发票管家.exe"
) else (
  where node >nul 2>&1 || (echo Node.js not found - install from https://nodejs.org && pause && exit)
  if not exist "node_modules" (echo Installing... && call npm install)
  start /b "" node --no-warnings server.js
)

REM Wait for server to start
echo Starting... waiting for server...
timeout /t 4 /nobreak >nul

REM Launch as standalone app window (no browser chrome)
start msedge --app=http://localhost:3456
echo Invoice Manager ready!

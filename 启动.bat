@echo off
cd /d "%~dp0"
title Invoice Manager

echo Starting Invoice Manager...
echo.

:: PostgreSQL
set PGDIR=C:\tools\postgresql-16\pgsql
set PGDATA=C:\tools\postgresql-16\data
echo [1/3] PostgreSQL
"%PGDIR%\bin\pg_ctl.exe" -D "%PGDATA%" status >nul 2>&1
if errorlevel 1 (
    "%PGDIR%\bin\pg_ctl.exe" -D "%PGDATA%" -l C:\tools\postgresql-16\pg.log start >nul 2>&1
)
echo        OK

:: Kill old Java
echo [2/3] Port 3456
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3456 " 2^>nul') do taskkill /f /pid %%a >nul 2>&1
echo        OK

:: Start Java
echo [3/3] Spring Boot
set JAVA_HOME=C:\tools\jdk-21\jdk-21.0.11+10
start /b "" "%JAVA_HOME%\bin\java" -jar "%~dp0target\finance-system-4.0.0.jar" > "%~dp0app.log" 2>&1

echo Waiting 15s for startup...
timeout /t 8 /nobreak >nul
timeout /t 7 /nobreak >nul

start http://127.0.0.1:3456

echo.
echo ========================================
echo  Ready: http://127.0.0.1:3456
echo  Login: admin / admin123
echo ========================================
pause

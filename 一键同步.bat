@echo off
chcp 65001 >nul
cd /d "%~dp0"
title 一键同步并启动

echo ============================================
echo   正在同步财务系统（编译打包 + 重启）
echo ============================================
echo.

echo [1/4] 杀掉旧后端进程（3456端口）...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3456 " 2^>nul') do taskkill /f /pid %%a >nul 2>&1
echo      完成
echo.

echo [2/4] 确保 PostgreSQL 运行...
set "PGDIR=C:\tools\postgresql-16\pgsql"
set "PGDATA=C:\tools\postgresql-16\data"
"%PGDIR%\bin\pg_ctl.exe" -D "%PGDATA%" status >nul 2>&1
if errorlevel 1 (
    "%PGDIR%\bin\pg_ctl.exe" -D "%PGDATA%" -l C:\tools\postgresql-16\pg.log start >nul 2>&1
    timeout /t 3 /nobreak >nul
)
echo      完成
echo.

echo [3/4] Maven 打包后端（含前端已构建的静态资源）...
set "JAVA_HOME=C:\tools\jdk-21\jdk-21.0.11+10"
set "PATH=%JAVA_HOME%\bin;C:\tools\maven\apache-maven-3.9.8\bin;%PATH%"
call mvn package -DskipTests
if errorlevel 1 (
    echo.
    echo  [错误] Maven 打包失败，请把上方报错截图发给 Codex
    echo.
    pause
    exit /b 1
)
echo      打包成功
echo.

echo [4/4] 启动后端服务...
set "JAVA_HOME=C:\tools\jdk-21\jdk-21.0.11+10"
start "" "%JAVA_HOME%\bin\java" -jar "%~dp0target\finance-system-4.0.0.jar"

echo      等待启动（10秒）...
timeout /t 10 /nobreak >nul

start http://127.0.0.1:3456

echo.
echo ============================================
echo   同步完成！
echo   地址: http://127.0.0.1:3456
echo   账号: admin / admin123
echo   首次使用请先到 [系统设置] 设置操作密码
echo ============================================
echo.
echo （此窗口可关闭，服务在后台运行）
pause

@echo off
rem ============================================================
rem 可选：在本机 3307 端口启动一个"独立"MySQL 实例并以此启动后端
rem - 不影响你已有的 MySQL 服务（默认 3306 不受影响）
rem - 数据保存在 backend\data\mysql3307\（已被 .gitignore 忽略）
rem - root 空密码、仅监听 127.0.0.1，仅用于本地开发演示
rem ============================================================
setlocal

rem 按需修改为你机器上的 MySQL bin 目录（MySQL 8.x）
set "MYSQL_BIN=D:\mysql-8.0.34-winx64\bin"
if not exist "%MYSQL_BIN%\mysqld.exe" (
    echo [错误] 未找到 MySQL：%MYSQL_BIN%
    echo        请把本文件顶部的 MYSQL_BIN 改成你机器上的 MySQL bin 目录。
    pause
    exit /b 1
)

set "DATADIR=%~dp0backend\data\mysql3307"
if not exist "%DATADIR%\mysql" (
    echo [信息] 首次运行，初始化数据目录（root 空密码，仅本机回环）...
    if not exist "%DATADIR%" mkdir "%DATADIR%"
    "%MYSQL_BIN%\mysqld.exe" --no-defaults --initialize-insecure --datadir="%DATADIR%" --console
    if errorlevel 1 (
        echo [错误] 数据目录初始化失败。
        pause
        exit /b 1
    )
)

netstat -ano | findstr /C:":3307 " | findstr /C:"LISTENING" >nul
if errorlevel 1 (
    echo [信息] 启动 MySQL 实例（3307 端口，最小化窗口为日志）...
    start "jobsim-mysql-3307" /min "%MYSQL_BIN%\mysqld.exe" --no-defaults --datadir="%DATADIR%" --port=3307 --bind-address=127.0.0.1 --console
    timeout /t 6 /nobreak >nul
)

"%MYSQL_BIN%\mysqladmin.exe" -h127.0.0.1 -P3307 -uroot ping >nul 2>nul
if errorlevel 1 (
    echo [错误] MySQL 未就绪，请查看 jobsim-mysql-3307 窗口里的日志。
    pause
    exit /b 1
)

"%MYSQL_BIN%\mysql.exe" -h127.0.0.1 -P3307 -uroot --skip-password -e "CREATE DATABASE IF NOT EXISTS jobsim DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
echo [信息] MySQL 就绪（127.0.0.1:3307），以 MySQL 模式启动后端...
endlocal & set "MYSQL_HOST=127.0.0.1" & set "MYSQL_PORT=3307" & set "MYSQL_USERNAME=root" & set "MYSQL_PASSWORD="
call "%~dp0start-backend.cmd" mysql

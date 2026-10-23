@echo off
rem JobSim AI 一键启动前端（首次运行自动 npm install）
setlocal
cd /d "%~dp0frontend"

where node >nul 2>nul
if errorlevel 1 (
    echo [错误] 未找到 Node.js，请安装 Node 20 或更高版本：https://nodejs.org/
    pause
    exit /b 1
)

if not exist node_modules (
    echo [信息] 首次运行，安装依赖（约 1-2 分钟）...
    call npm install --no-fund --no-audit
    if errorlevel 1 (
        echo [错误] 依赖安装失败，请检查网络后重试。
        pause
        exit /b 1
    )
)

echo [信息] 启动开发服务器。默认 http://localhost:5173 ；若端口被占用 Vite 会自动
echo        换用 5174 等端口，请以本窗口实际输出的地址为准。
call npm run dev

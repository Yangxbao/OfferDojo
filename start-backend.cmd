@echo off
rem ============================================================
rem JobSim AI backend launcher (auto-detects a JDK 21)
rem Usage:
rem   start-backend.cmd          demo profile (embedded H2, no DB needed)
rem   start-backend.cmd mysql    MySQL profile (set MYSQL_* env vars first,
rem                              or run start-mysql.cmd)
rem Note: keep this file ASCII-only and CRLF; cmd.exe batch parsing
rem       breaks with multibyte comments on some codepages.
rem ============================================================
setlocal

set "JDK21="
call :pick "%JAVA_HOME%" && goto :found
call :pick "%USERPROFILE%\.jdks\jbr-21.0.11" && goto :found
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do (call :pick "%%~D" && goto :found)
for /d %%D in ("C:\Program Files\Java\jdk-21*") do (call :pick "%%~D" && goto :found)
for /d %%D in ("C:\Program Files\BellSoft\LibericaJDK-21*") do (call :pick "%%~D" && goto :found)

echo [ERROR] JDK 21 not found on this machine.
echo         Install Temurin 21: https://adoptium.net/temurin/releases/?version=21
echo         Or set JAVA_HOME to an existing JDK 21 installation.
pause
exit /b 1

:found
rem endlocal restores the previous working directory as well, so cd AFTER it.
endlocal & set "JAVA_HOME=%JDK21%" & set "PATH=%JDK21%\bin;%PATH%"

cd /d "%~dp0backend"
if not exist pom.xml (
    echo [ERROR] backend\pom.xml not found. Run this script from the project root.
    pause
    exit /b 1
)

set "PROFILE=%~1"
if "%PROFILE%"=="" set "PROFILE=demo"

echo [INFO] Using JDK: %JAVA_HOME%
echo [INFO] Working dir: %CD%
echo [INFO] Starting backend (profile=%PROFILE%) ...
mvn spring-boot:run "-Dspring-boot.run.profiles=%PROFILE%"
goto :eof

:pick
rem A candidate is accepted when java.exe exists and version string contains 21.
if "%~1"=="" exit /b 1
if not exist "%~1\bin\java.exe" exit /b 1
"%~1\bin\java.exe" -version 2>&1 | findstr /C:"21." >nul || exit /b 1
set "JDK21=%~1"
exit /b 0

@echo off
rem Stops the local system. The database keeps its data (reset it with dev-reset-db.bat).
setlocal EnableExtensions
cd /d "%~dp0"

rem Started by double-click? Then keep the window open at the end, so messages can be read.
set "KEEP_OPEN="
echo %cmdcmdline% | find /i "%~nx0" >nul && set "KEEP_OPEN=1"

call :stop_port 4300 frontend
call :stop_port 8090 backend
rem The minimized windows opened by dev-start.bat, if still there
taskkill /FI "WINDOWTITLE eq XenonFix backend*" /T /F >nul 2>nul
taskkill /FI "WINDOWTITLE eq XenonFix frontend*" /T /F >nul 2>nul

if not exist .env (
  echo No .env, so the database was never started.
  if defined KEEP_OPEN pause
  exit /b 0
)

docker info >nul 2>nul || (set "MSG=Docker is not running, so the database could not be stopped." & goto :fail)

echo Stopping database...
docker compose stop
if errorlevel 1 (set "MSG=Could not stop the database. See the lines above." & goto :fail)
echo Stopped. Data is kept; start again with dev-start.bat
if defined KEEP_OPEN pause
exit /b 0

:fail
echo.
echo ERROR: %MSG%
if defined KEEP_OPEN pause
exit /b 1

rem Stops whatever listens on the port: the backend JVM (8090) or the Angular server (4300)
:stop_port
set "found="
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /R /C:":%1 .*LISTENING"') do (
  taskkill /PID %%p /T /F >nul 2>nul
  set "found=1"
)
if defined found (echo Stopped %2.) else (echo %2 was not running.)
exit /b 0

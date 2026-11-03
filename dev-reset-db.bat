@echo off
rem Deletes the LOCAL database with all its data. The next dev-start.bat creates a fresh one.
rem Uploaded files and .env stay.
setlocal EnableExtensions
cd /d "%~dp0"

rem Started by double-click? Then keep the window open at the end, so messages can be read.
set "KEEP_OPEN="
echo %cmdcmdline% | find /i "%~nx0" >nul && set "KEEP_OPEN=1"

set /p "answer=Delete ALL local XenonFix data? Type YES to continue: "
if not "%answer%"=="YES" (
  echo Cancelled.
  if defined KEEP_OPEN pause
  exit /b 0
)

rem Runs inside this window; dev-stop.bat only pauses when double-clicked itself
call "%~dp0dev-stop.bat"
if errorlevel 1 (set "MSG=The database was not deleted." & goto :fail)
if exist .env (
  docker compose down -v
  if errorlevel 1 (set "MSG=Could not delete the database. See the lines above." & goto :fail)
)
echo Local database deleted. Run dev-start.bat to start with a fresh one.
if defined KEEP_OPEN pause
exit /b 0

:fail
echo.
echo ERROR: %MSG%
if defined KEEP_OPEN pause
exit /b 1

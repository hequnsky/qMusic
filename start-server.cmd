@echo off
REM qMusic - start backend services (musiclib + netease + gateway + cloudflare tunnel)
REM Double-click this file. It hands off to scripts\restart-services.sh in a new window,
REM so the services keep running after this window closes.
REM Keep the ASCII only rule: cmd.exe parses .cmd in GBK, non-ASCII breaks if() blocks.

setlocal
set ROOT=F:\project\AiCoding\deepseek\qMusic
set BASH=C:\Users\Administrator\.workbuddy\binaries\PortableGit\versions\1.3.0\bin\bash.exe

if not exist "%ROOT%\scripts\restart-services.sh" (
  echo [ERROR] restart-services.sh not found under %ROOT%\scripts
  pause
  exit /b 1
)

echo Starting qMusic services in a separate window...
start "qMusic services" /min "%BASH%" -c "bash /f/project/AiCoding/deepseek/qMusic/scripts/restart-services.sh"

echo.
echo A minimized window named "qMusic services" is now running the restart script.
echo It will print the new tunnel URL and push it to server-address.json.
echo Logs: %ROOT%\.workbuddy\logs\
echo.
timeout /t 8 /nobreak >nul
pause

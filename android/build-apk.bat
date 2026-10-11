@echo off
REM ==================================================================
REM qMusic 一键打包（Windows）
REM   1) 版本号 +0.01（versionName）/ +1（versionCode）
REM   2) 构建 Release APK
REM   3) 复制到 android\dist\ 并打印产物路径
REM
REM 用法：
REM   build-apk.bat            生产包（release，已签名 + R8 混淆）
REM   build-apk.bat debug      调试包（debug，安装快、便于排查）
REM ==================================================================
setlocal enabledelayedexpansion
cd /d "%~dp0"

set BUILD_TYPE=release
if /I "%~1"=="debug" set BUILD_TYPE=debug

echo.
echo [1/3] 版本号自增（+0.01）
node scripts\bump-version.js
if errorlevel 1 (
  echo [ERROR] 版本号自增失败，请确认已安装 Node.js
  exit /b 1
)

set /p VER=<.build-version
for /f "tokens=2 delims==" %%a in ('findstr /b "appName=" version.properties') do set APPNAME=%%a
set APK=%APPNAME%-%VER%-%BUILD_TYPE%.apk

echo.
echo [2/3] 构建 %BUILD_TYPE% APK（版本 %VER%）...
if /I "%BUILD_TYPE%"=="release" (
  call gradlew.bat assembleRelease --console=plain
) else (
  call gradlew.bat assembleDebug --console=plain
)
if errorlevel 1 (
  echo [ERROR] 构建失败
  exit /b 1
)

echo.
echo [3/3] 复制产物到 dist\
if not exist dist mkdir dist
copy /Y "app\build\outputs\apk\%BUILD_TYPE%\%APK%" "dist\%APK%" >nul
if errorlevel 1 (
  echo [ERROR] 未找到产物 app\build\outputs\apk\%BUILD_TYPE%\%APK%
  exit /b 1
)

echo.
echo ==================================================================
echo  打包完成
echo    APK    : %cd%\dist\%APK%
echo    版本   : %VER%  (%BUILD_TYPE%)
echo ==================================================================
endlocal

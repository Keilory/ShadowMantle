@echo off
setlocal EnableExtensions
cd /d "%~dp0\..\.."

echo ========================================
echo ShadowMantle - update and launch
echo ========================================
echo.

echo [1/3] Updating main branch...
git pull --ff-only origin main
if errorlevel 1 (
    echo.
    echo [ERROR] Git update failed. Resolve the Git state above and try again.
    pause
    exit /b 1
)

echo.
echo [2/3] Stopping old Gradle daemons...
call gradlew.bat --stop
if errorlevel 1 (
    echo [WARNING] Gradle --stop returned an error. Continuing...
)

echo.
echo [3/3] Starting ShadowMantle development client...
call gradlew.bat runClient
set "EXIT_CODE=%ERRORLEVEL%"

echo.
echo ========================================
if not "%EXIT_CODE%"=="0" (
    echo ShadowMantle failed to start or exited with code %EXIT_CODE%.
) else (
    echo ShadowMantle closed normally.
)
echo ========================================

pause
exit /b %EXIT_CODE%

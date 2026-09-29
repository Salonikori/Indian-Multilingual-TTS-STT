@echo off
echo ========================================
echo  iTantra Quick Installer
echo  ISRO Hackathon 2024 Submission
echo ========================================
echo.

echo Checking ADB connection...
adb devices
if %errorlevel% neq 0 (
    echo ERROR: ADB not found or no devices connected
    echo.
    echo Please ensure:
    echo 1. Android phone connected via USB
    echo 2. Developer Options enabled  
    echo 3. USB Debugging enabled
    echo 4. ADB drivers installed
    pause
    exit /b 1
)

echo.
echo Installing iTantra Debug APK...
adb install -r "app\build\outputs\apk\debug\app-debug.apk"
if %errorlevel% neq 0 (
    echo ERROR: Installation failed
    echo Try enabling "Install from Unknown Sources"
    pause
    exit /b 1
)

echo.
echo ========================================
echo  Installation Complete! ✅
echo ========================================
echo.
echo iTantra has been installed successfully.
echo.
echo Next steps:
echo 1. Open iTantra app on your phone
echo 2. Select English or Hindi language  
echo 3. Click "Load Active Language"
echo 4. For two-phone demo: install on second phone
echo.
echo Enjoy your iTantra experience! 🚀
echo.
pause
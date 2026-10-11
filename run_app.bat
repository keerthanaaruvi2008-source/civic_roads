@echo off
set "JAVA_HOME=C:\Users\Subramanian\jdks\jdk-21.0.4"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"

echo =========================================================================
echo  BUILDING & COMPILING CIVIC ROADS PLATFORM WITH -Werror
echo =========================================================================
call .\gradlew.bat compileJava
if errorlevel 1 (
    echo.
    echo [ERROR] Compilation failed.
    pause
    exit /b 1
)

echo.
echo =========================================================================
echo  STARTING ROAD DAMAGE INSPECTOR ^& WEB PLATFORM (http://127.0.0.1:8000)
echo =========================================================================
start "" /B powershell -NoProfile -WindowStyle Hidden -Command "$c = 0; while ($c -lt 60) { try { $t = New-Object System.Net.Sockets.TcpClient('127.0.0.1', 8000); if ($t.Connected) { $t.Close(); Start-Process 'http://127.0.0.1:8000'; break } } catch {} Start-Sleep -Milliseconds 500; $c++ }"
call .\gradlew.bat run
if errorlevel 1 pause

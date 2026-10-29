@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0"

echo ============================================================
echo   KIEM TRA MOI TRUONG JAVA CHO DO AN CSD201 (verify_env.bat)
echo ============================================================

REM 1. Tim Java 8 runtime
set "JAVA8_BIN="
if defined JAVA8_HOME (
    if exist "%JAVA8_HOME%\bin\java.exe" set "JAVA8_BIN=%JAVA8_HOME%\bin"
)

if "%JAVA8_BIN%"=="" (
    if exist "C:\Program Files\Eclipse Adoptium\jdk-8.0.412.8-hotspot\bin\java.exe" (
        set "JAVA8_BIN=C:\Program Files\Eclipse Adoptium\jdk-8.0.412.8-hotspot\bin"
    )
)

if "%JAVA8_BIN%"=="" (
    for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-8*" "C:\Program Files\Java\jdk1.8*") do (
        if exist "%%D\bin\java.exe" set "JAVA8_BIN=%%D\bin"
    )
)

if "%JAVA8_BIN%"=="" (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\java.exe" set "JAVA8_BIN=%JAVA_HOME%\bin"
    )
)

if "%JAVA8_BIN%"=="" (
    echo [CANH BAO] Khong tim thay JAVA8_HOME hoac duong dan JDK 8 mac dinh. Su dung java trong PATH:
    set "JAVA_CMD=java"
) else (
    echo [OK] Su dung Java runtime tai: %JAVA8_BIN%
    set "JAVA_CMD=%JAVA8_BIN%\java.exe"
)

REM 2. Kiem tra phien ban Java
echo.
echo --- 1. Phien ban Java:
"%JAVA_CMD%" -version
if errorlevel 1 (
    echo [LOI] Khong the thuc thi lenh java!
    exit /b 1
)

REM 3. Kiem tra Garbage Collector dang su dung
echo.
echo --- 2. Garbage Collector mac dinh:
"%JAVA_CMD%" -XX:+PrintFlagsFinal -version 2>&1 | findstr /i "UseParallelGC UseG1GC UseSerialGC UseConcMarkSweepGC"

REM 4. Build neu chua co hashbench.jar
if not exist "hashbench\out\hashbench.jar" (
    echo.
    echo --- Build hashbench.jar...
    call build.bat
    if errorlevel 1 (
        echo [LOI] Build that bai!
        exit /b 1
    )
)

REM 5. Kiem tra bytecode version (phai la major version 52 = Java 8)
echo.
echo --- 3. Kiem tra Bytecode Version (Major version 52 = Java 8):
set "JAVAP_CMD="
if not "%JAVA8_BIN%"=="" if exist "%JAVA8_BIN%\javap.exe" set "JAVAP_CMD=%JAVA8_BIN%\javap.exe"
if "%JAVAP_CMD%"=="" set "JAVAP_CMD=javap"

"%JAVAP_CMD%" -verbose -cp hashbench\out hashbench.Main 2>&1 | findstr /i "major version"
if errorlevel 1 (
    echo [CANH BAO] Khong the kiem tra javap. Bo qua buoc kiem tra bytecode.
)

REM 6. Chay --selftest de dam bao jar chay duoc
echo.
echo --- 4. Chay hashbench.jar --selftest:
"%JAVA_CMD%" -jar hashbench\out\hashbench.jar --selftest
if errorlevel 1 (
    echo [LOI] --selftest that bai!
    exit /b 1
)

REM 7. Chay TV2SelfTest de kiem tra cac module TV2
echo.
echo --- 5. Chay hashbench.TV2SelfTest:
"%JAVA_CMD%" -Dfile.encoding=UTF-8 -cp hashbench\out hashbench.TV2SelfTest
if errorlevel 1 (
    echo [LOI] TV2SelfTest that bai!
    exit /b 1
)

echo.
echo ============================================================
echo   [PASS] MOI TRUONG VA CODE DAT CHUAN YEU CAU DE TAI 2!
echo ============================================================
exit /b 0

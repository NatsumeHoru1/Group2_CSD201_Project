@echo off
rem TV4 - build + chay test. Dung (thu muc goc du an): tv4_test.bat [--self] [--ops N] [--only TEN] [--require-all]
rem   --require-all: coi cau hinh chua co (SKIP) la that bai -> dung truoc khi gan tag rc1
setlocal
cd /d "%~dp0"
chcp 65001 >nul
if not exist "hashbench\out" mkdir "hashbench\out"
set "SOURCE_LIST=%TEMP%\tv4_sources_%RANDOM%.txt"
if exist "%SOURCE_LIST%" del /q "%SOURCE_LIST%"
for /R "hashbench\src" %%F in (*.java) do call :append_source "%%F"
javac -encoding UTF-8 -source 1.8 -target 1.8 -d "hashbench\out" @"%SOURCE_LIST%"
set "R=%ERRORLEVEL%"
del /q "%SOURCE_LIST%"
if not "%R%"=="0" exit /b %R%
java -version
java -Xmx1g -cp "hashbench\out" hashbench.test.AllTests %*
exit /b %ERRORLEVEL%

:append_source
set "JAVA_FILE=%~1"
set "JAVA_FILE=%JAVA_FILE:\=/%"
>>"%SOURCE_LIST%" echo "%JAVA_FILE%"
exit /b

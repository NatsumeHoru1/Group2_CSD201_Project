@echo off
setlocal
cd /d "%~dp0"

if not exist "hashbench\out" mkdir "hashbench\out"
set "SOURCE_LIST=%TEMP%\hashbench_sources_%RANDOM%_%RANDOM%.txt"
for /R "hashbench\src" %%F in (*.java) do call :append_source "%%F"

javac -encoding UTF-8 -source 1.8 -target 1.8 -d "hashbench\out" @"%SOURCE_LIST%"
set "BUILD_RESULT=%ERRORLEVEL%"
del /q "%SOURCE_LIST%"
if not "%BUILD_RESULT%"=="0" exit /b %BUILD_RESULT%

if exist "hashbench\out\hashbench.jar" del /q "hashbench\out\hashbench.jar"
jar cfe "hashbench\out\hashbench.jar" hashbench.Main -C "hashbench\out" .
if errorlevel 1 exit /b 1
echo Build completed: hashbench\out\hashbench.jar
exit /b 0

:append_source
set "JAVA_FILE=%~1"
set "JAVA_FILE=%JAVA_FILE:\=/%"
>>"%SOURCE_LIST%" echo "%JAVA_FILE%"
exit /b

@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"

if not exist "hashbench\out" mkdir "hashbench\out"
set "SOURCE_LIST=%TEMP%\hashbench_sources_%RANDOM%_%RANDOM%.txt"
for /R "hashbench\src" %%F in (*.java) do (
  set "JAVA_FILE=%%F"
  set "JAVA_FILE=!JAVA_FILE:\=/!"
  echo "!JAVA_FILE!">>"%SOURCE_LIST%"
)

javac -encoding UTF-8 -source 1.8 -target 1.8 -d "hashbench\out" @"%SOURCE_LIST%"
set "BUILD_RESULT=%ERRORLEVEL%"
del /q "%SOURCE_LIST%"
if not "%BUILD_RESULT%"=="0" exit /b %BUILD_RESULT%

jar cfe "hashbench\out\hashbench.jar" hashbench.Main -C "hashbench\out" .
if errorlevel 1 exit /b 1
echo Build completed: hashbench\out\hashbench.jar

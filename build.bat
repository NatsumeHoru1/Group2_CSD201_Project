@echo off
setlocal
cd /d "%~dp0"

if not exist "hashbench\out" mkdir "hashbench\out"
javac -encoding UTF-8 -source 1.8 -target 1.8 -d "hashbench\out" ^
  "hashbench\src\hashbench\HashIndex.java" ^
  "hashbench\src\hashbench\HashStats.java" ^
  "hashbench\src\hashbench\StubHashIndex.java" ^
  "hashbench\src\hashbench\Counters.java" ^
  "hashbench\src\hashbench\RunConfig.java" ^
  "hashbench\src\hashbench\Runner.java" ^
  "hashbench\src\hashbench\CsvSchema.java" ^
  "hashbench\src\hashbench\CsvWriter.java" ^
  "hashbench\src\hashbench\Main.java"
if errorlevel 1 exit /b 1

jar cfe "hashbench\out\hashbench.jar" hashbench.Main -C "hashbench\out" .
if errorlevel 1 exit /b 1
echo Build completed: hashbench\out\hashbench.jar

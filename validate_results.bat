@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0"

REM TV6: validate toan bo CSV trong results\raw\ bang hashbench.CsvValidator
REM Cach dung: validate_results.bat  (chay o thu muc goc project)
REM Exit code: 0 = PASS / khong co file, 1 = co vi pham / loi build

if not exist "hashbench\out\hashbench\CsvValidator.class" (
    echo [INFO] Chua thay class CsvValidator - dang build...
    call "%~dp0build.bat"
    if errorlevel 1 (
        echo [LOI] Build that bai, khong chay validator duoc.
        exit /b 1
    )
)

if not exist "results\raw" (
    echo [WARN] Chua co thu muc results\raw - chua co file CSV nao de kiem tra.
    echo [GOI Y] Chay bench truoc, vi du:
    echo   java -Xmx2g -jar hashbench/out/hashbench.jar --bench --rq RQ1 --algo rh --variant base --dataset rq1a --p 10 --alpha 0.75 --seed 1 --queries 20000 --repeat 1 --xmx 2g --timeout-sec 60 --machine-id dev --out results/raw/dev/bench.csv
    exit /b 0
)

set FOUND=0
set FAIL=0
for /R "results\raw" %%F in (*.csv) do (
    set FOUND=1
    echo.
    echo === %%F ===
    java -Dfile.encoding=UTF-8 -cp "hashbench\out" hashbench.CsvValidator "%%F"
    if errorlevel 1 set FAIL=1
)

if "!FOUND!"=="0" (
    echo [WARN] Khong tim thay file *.csv nao trong results\raw\
    exit /b 0
)

echo.
if "!FAIL!"=="1" (
    echo KET QUA TONG: FAIL - xem VI PHAM o tren
    exit /b 1
) else (
    echo KET QUA TONG: PASS - tat ca CSV hop le
    exit /b 0
)

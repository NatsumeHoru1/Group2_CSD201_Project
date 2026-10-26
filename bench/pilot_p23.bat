@echo off
setlocal
cd /d "%~dp0\.."

call build.bat
if errorlevel 1 exit /b %ERRORLEVEL%

java -Xmx2g -jar hashbench\out\hashbench.jar --bench --rq RQ1 --algo rh --variant base --dataset rq1a --p 23 --alpha 0.75 --seed 1 --queries 5000000 --repeat 1 --xmx 2g --timeout-sec 600 --machine-id pilot-p23 --out results\raw\dev\pilot_p23.csv
exit /b %ERRORLEVEL%

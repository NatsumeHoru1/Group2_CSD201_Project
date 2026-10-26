@echo off
rem TV4 - kiem tra cay ma nguon sach truoc khi do / gan tag (QDC: git_commit phai khop). Chi kiem hashbench va bench.
for /f %%i in ('git status --porcelain -- hashbench bench') do (
  echo CAY MA NGUON CHUA SACH: & git status --porcelain -- hashbench bench & exit /b 1
)
echo OK: hashbench va bench sach. Commit hien tai:
git rev-parse --short HEAD
exit /b 0

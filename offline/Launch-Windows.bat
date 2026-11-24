@echo off
setlocal
set "DIR=%~dp0"
set "STANDALONE=%DIR%ExamSet-Standalone.html"

where python >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  echo Starting local ExamSet server on http://127.0.0.1:4173/ExamSet-Standalone.html
  start "" "http://127.0.0.1:4173/ExamSet-Standalone.html"
  python -m http.server 4173 --bind 127.0.0.1 --directory "%DIR%"
  goto :eof
)

echo Opening ExamSet-Standalone.html directly in default browser...
start "" "%STANDALONE%"

@echo off
setlocal
set "POWERSHELL_TELEMETRY_OPTOUT=1"
set "DOTNET_CLI_TELEMETRY_OPTOUT=1"
set "POWERSHELL_UPDATECHECK=Off"
set "VSCMD_SKIP_SENDTELEMETRY=1"
set "HTTP_PROXY=http://127.0.0.1:1"
set "HTTPS_PROXY=%HTTP_PROXY%"
set "ALL_PROXY=%HTTP_PROXY%"
set "NO_PROXY=localhost,127.0.0.1,::1"
if not defined FNR3_QA_PYTHON set "FNR3_QA_PYTHON=python"
if not defined FNR3_QA_ADB set "FNR3_QA_ADB=%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools\adb.exe"
"%FNR3_QA_PYTHON%" "%~dp0offline_thor_qa.py" %*
exit /b %errorlevel%

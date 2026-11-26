param([Parameter(Mandatory=$true)][string] $Iso)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'offline_qa.ps1')
$project = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$kit = Join-Path $project 'Thor-Stutter-Captures\USB-Sweep-Kit-Postfight'
$hashes = Join-Path $kit 'hashes.json'
if ((Get-FileHash -LiteralPath $hashes -Algorithm SHA256).Hash -ne 'd5c1f80a63d46ce414739fdefe303fab1426bb80377f0ffc1135feedd9c4a869') { throw 'Unexpected USB sweep kit.' }
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$python = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
if (!(Test-Path -LiteralPath $python)) { $python = (Get-Command python.exe -ErrorAction Stop).Source }
if (!(Test-Path -LiteralPath $Iso -PathType Leaf)) { throw "Original Xbox ISO is unavailable at $Iso" }
& $python (Join-Path $PSScriptRoot 'thor_sweep.py') --adb $adb --kit $kit --iso $Iso --out (Join-Path $project 'Thor-Stutter-Captures\Hardware-Sweep')
if ($LASTEXITCODE -ne 0) { throw 'See events.jsonl and results.json in the printed USB sweep folder for the failing stage.' }

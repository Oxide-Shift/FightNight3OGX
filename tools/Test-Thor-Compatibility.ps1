param([ValidateRange(20,600)][int] $Seconds=150)
$ErrorActionPreference='Stop'
$project=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$apk=Join-Path $project 'FightNight3OGX-Alpha78-USB-Postfight.apk'
$adb=Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$python=Join-Path $env:USERPROFILE '.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
if (!(Test-Path -LiteralPath $python)) { $python=(Get-Command python.exe -ErrorAction Stop).Source }
$expected='097baa9237969994023b42d94b4658e99a31d119717a6648f3148a23690484ee'
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash -ne $expected) { throw 'Unexpected compatibility APK.' }
$serial=(& $adb -d get-serialno | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or !$serial) { throw 'Connect one authorized USB Thor.' }
$model=(& $adb -s $serial shell getprop ro.product.model | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $model -notmatch 'Thor') { throw 'The connected device is not a Thor.' }
$installedPath=(& $adb -s $serial shell pm path org.oxideshift.corner | Select-Object -First 1)
$installedHash=''
if ($installedPath -match '^package:(/data/app/[^\s]+/base\.apk)\s*$') {
    $installedHash=(& $adb -s $serial shell sha256sum $Matches[1] | Out-String).Trim().Split(' ')[0]
}
if ($installedHash -ne $expected) {
    & $adb -s $serial install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'In-place installation failed. Existing app data was retained.' }
}
$package=(& $adb -s $serial shell dumpsys package org.oxideshift.corner | Out-String)
if ($LASTEXITCODE -ne 0 -or !$package.Contains('versionName=0.4.0-alpha.78.3') -or $package.Contains('android.permission.INTERNET')) { throw 'USB-only candidate verification failed.' }
$output=Join-Path $project 'Thor-Stutter-Captures/Presentation/Alpha78-Compatibility'
Write-Host 'Fight Night will prepare and launch the existing import, then record the startup automatically.'
& $python (Join-Path $PSScriptRoot 'thor_startup_qa.py') --adb $adb --serial $serial --out $output --seconds $Seconds
if ($LASTEXITCODE -ne 0) { throw 'The USB startup check reported a failure. Diagnostics are under the printed evidence directory.' }

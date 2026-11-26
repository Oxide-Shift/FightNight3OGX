# Dot-source in the test shell. All settings expire with the process.
# Set the same opt-outs in the parent when starting a fresh PowerShell.
$env:POWERSHELL_TELEMETRY_OPTOUT='1'
$env:DOTNET_CLI_TELEMETRY_OPTOUT='1'
$env:POWERSHELL_UPDATECHECK='Off'
$env:VSCMD_SKIP_SENDTELEMETRY='1'
$env:HTTP_PROXY='http://127.0.0.1:1'
$env:HTTPS_PROXY=$env:HTTP_PROXY
$env:ALL_PROXY=$env:HTTP_PROXY
$env:NO_PROXY='localhost,127.0.0.1,::1'
if ($PSVersionTable.PSEdition -eq 'Core') {
    # An already-running Work shell may have initialized the SDK before
    # processing the opt-out. Fail closed if that SDK configuration changes.
    $qaTelemetryType=[System.Management.Automation.PSObject].Assembly.GetType('Microsoft.PowerShell.Telemetry.ApplicationInsightsTelemetry',$true)
    $qaFlags=[Reflection.BindingFlags]'Static,NonPublic,Public'
    $qaClient=$qaTelemetryType.GetProperty('s_telemetryClient',$qaFlags).GetValue($null)
    if ($qaClient) {
        $qaConfig=$qaClient.TelemetryConfiguration
        $qaConfig.DisableTelemetry=$true
        $qaConfig.TelemetryChannel.EndpointAddress='http://127.0.0.1:1/v2/track'
        if (!$qaConfig.DisableTelemetry -or $qaConfig.TelemetryChannel.EndpointAddress -ne 'http://127.0.0.1:1/v2/track') {
            throw 'Unable to isolate the running PowerShell telemetry client.'
        }
    } elseif ($qaTelemetryType.GetProperty('CanSendTelemetry',$qaFlags).GetValue($null)) {
        throw 'PowerShell can send telemetry and has no configurable client.'
    }
}

param(
    [string]$SdkRoot = $env:ANDROID_SDK_ROOT,
    [string]$AvdHome = $env:ANDROID_AVD_HOME,
    [string]$AvdName = 'gwm-api30-arm-translation',
    [int]$Port = 5580,
    [switch]$StartEmulator
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($SdkRoot)) { $SdkRoot = $env:ANDROID_HOME }
if ([string]::IsNullOrWhiteSpace($SdkRoot)) { throw 'Set ANDROID_SDK_ROOT or pass -SdkRoot.' }
if (-not [string]::IsNullOrWhiteSpace($AvdHome)) { $env:ANDROID_AVD_HOME = $AvdHome }
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskAdb = Join-Path $SdkRoot 'platform-tools\adb.exe'
$taskEmulator = Join-Path $SdkRoot 'emulator\emulator.exe'
$taskApk = Join-Path $taskRoot 'app\build\outputs\apk\demo\app-demo.apk'
$taskSerial = "emulator-$Port"
foreach ($taskFile in @($taskAdb, $taskEmulator, $taskApk)) {
    if (-not (Test-Path -LiteralPath $taskFile)) { throw "Missing file: $taskFile" }
}
if ($Port -lt 5554 -or $Port -gt 5682 -or ($Port % 2) -ne 0) {
    throw 'Use an even, dedicated Android Emulator port between 5554 and 5682.'
}

function Invoke-DemoAdb {
    param([string[]]$Arguments)
    $ErrorActionPreference = 'Continue'
    $taskResult = & $taskAdb -s $taskSerial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0 -or ($taskResult -join "`n") -match '(?m)^KO:') {
        throw ($taskResult -join "`n")
    }
    return ($taskResult -join "`n")
}

function Get-DemoDisplayIds {
    param([string]$Dump)
    @([regex]::Matches($Dump, '(?m)^\s*mDisplayId=(\d+)\s*$') |
        ForEach-Object { [int]$_.Groups[1].Value })
}

& $taskAdb start-server | Out-Null
$taskDevices = & $taskAdb devices
if (-not $StartEmulator -and ($taskDevices -join "`n") -notmatch "(?m)^$taskSerial\s+device\s*$") {
    throw "Emulator $taskSerial is not running. Pass -StartEmulator explicitly to launch it."
}
if ($StartEmulator) {
    if (($taskDevices -join "`n") -match '(?m)^emulator-\d+\s') {
        throw 'An emulator is already running. Keep only the intended test instance.'
    }
    # Interactive emulator requested explicitly by -StartEmulator. Read-only
    # mode leaves the existing AVD data unchanged after this test session.
    Start-Process -FilePath $taskEmulator -ArgumentList @(
        '-avd', $AvdName, '-port', $Port, '-read-only',
        '-memory', '2048', '-cores', '2',
        '-no-snapshot-load', '-no-snapshot-save', '-no-boot-anim',
        '-vsync-rate', '30', '-gpu', 'auto'
    ) | Out-Null
}

$taskDeadline = (Get-Date).AddMinutes(3)
do {
    try { $taskBooted = Invoke-DemoAdb -Arguments @('shell','getprop','sys.boot_completed') }
    catch { $taskBooted = '' }
    if ($taskBooted.Trim() -eq '1') { break }
    if ((Get-Date) -gt $taskDeadline) { throw "Emulator $taskSerial did not boot." }
    Start-Sleep -Seconds 2
} while ($true)

$taskApi = [int](Invoke-DemoAdb -Arguments @('shell','getprop','ro.build.version.sdk')).Trim()
$taskRunningAvd = Invoke-DemoAdb -Arguments @('emu','avd','name')
if (($taskRunningAvd -split '\r?\n')[0].Trim() -ne $AvdName) {
    throw "Port $Port belongs to another AVD. Its displays were not changed."
}
$taskFeatures = Invoke-DemoAdb -Arguments @('shell','pm','list','features')
if ($taskApi -lt 29 -or $taskFeatures -notmatch 'android.software.activities_on_secondary_displays') {
    throw 'This test requires Android 10+ with activities_on_secondary_displays. Do not enable a mirroring overlay.'
}

# Remove the developer-options overlay. The native emulator display below
# occupies its own host display area, not a window inside the primary display.
Invoke-DemoAdb -Arguments @('shell','settings','delete','global','overlay_display_devices') | Out-Null
Start-Sleep -Milliseconds 700
$taskInitialIds = @(Get-DemoDisplayIds (Invoke-DemoAdb -Arguments @('shell','dumpsys','display')))
if ($taskInitialIds.Count -ne 1 -or $taskInitialIds[0] -ne 0) {
    throw 'Start with only the primary display. Existing native secondary displays were not changed.'
}

$taskCreatedDisplay = $false
try {
    for ($taskAttempt = 0; $taskAttempt -lt 2; $taskAttempt++) {
        # Native index and Android logical Display ID need not be identical.
        # A fresh guest may allocate ID 1 first. Release it and create ID 2.
        # Default emulator flags include PUBLIC and OWN_CONTENT_ONLY.
        Invoke-DemoAdb -Arguments @('emu','multidisplay','add','2','1920','720','240','0') | Out-Null
        $taskCreatedDisplay = $true
        $taskDisplayDeadline = (Get-Date).AddSeconds(15)
        do {
            Start-Sleep -Milliseconds 500
            $taskDisplays = Invoke-DemoAdb -Arguments @('shell','dumpsys','display')
            $taskIds = @(Get-DemoDisplayIds $taskDisplays)
        } while ($taskIds.Count -lt 2 -and (Get-Date) -lt $taskDisplayDeadline)
        if ($taskIds.Count -eq 2 -and $taskIds -contains 0 -and $taskIds -contains 2) { break }
        Invoke-DemoAdb -Arguments @('emu','multidisplay','del','2') | Out-Null
        $taskCreatedDisplay = $false
        Start-Sleep -Seconds 1
    }
    if ($taskIds.Count -ne 2 -or $taskIds -notcontains 0 -or $taskIds -notcontains 2) {
        throw 'The emulator did not provide exactly Display 0 and Display 2. No dashboard was launched.'
    }
    $taskSecondaryInfo = ($taskDisplays -split '\r?\n' | Where-Object {
        $_ -match 'mBaseDisplayInfo=.*displayId 2,'
    }) -join "`n"
    if ($taskSecondaryInfo -notmatch '1920 x 720' -or
        $taskSecondaryInfo -notmatch 'density 240' -or
        $taskDisplays -notmatch 'DisplayDeviceInfo\{"Emulator 2D Display":.*FLAG_OWN_CONTENT_ONLY') {
        throw 'Display 2 must be 1920x720 at 240 dpi with OWN_CONTENT_ONLY. No dashboard was launched.'
    }
} catch {
    if ($taskCreatedDisplay) {
        Invoke-DemoAdb -Arguments @('emu','multidisplay','del','2') | Out-Null
    }
    throw
}

Invoke-DemoAdb -Arguments @('install','-r',$taskApk) | Write-Output
Invoke-DemoAdb -Arguments @('shell','am','start','--display','0','-W','-n',
    'net.adminrunet.h9cluster.demo/net.adminrunet.h9cluster.SettingsActivity') | Write-Output
Write-Output 'Two displays are ready. Select Sculpted Titanium and press Save and launch on the primary screen.'
Write-Output "Verify PreviewActivity on Display 2 with: adb -s $taskSerial shell dumpsys activity activities"

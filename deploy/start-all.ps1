# DhruvAR: start (or stop) everything with ONE command, printing each step and saving a log.
#
#   Start:  powershell -ExecutionPolicy Bypass -File "<this file>"
#   Stop:   powershell -ExecutionPolicy Bypass -File "<this file>" -Stop
#   Options: -NoBrowser (do not open the dashboard)  -NoPhone (skip the USB phone step)
#
# Data is never deleted: this script never uses "down -v".
param(
    [switch]$Stop,
    [switch]$NoBrowser,
    [switch]$NoPhone
)

$ErrorActionPreference = 'Continue'

# ---- paths (edit here if folders move) ----
$MapDir     = 'E:\map\map-server'
$MapOverride = 'C:\Users\itcell\AndroidStudioProjects\DhruvAR-infra\deploy\mapserver.override.yml'
$StackDir   = 'C:\Users\itcell\AndroidStudioProjects\DhruvAR-integration\deploy'
$Adb        = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$Url        = 'http://localhost:8000'

# ---- log file (also shown on screen) ----
$LogDir = Join-Path ([Environment]::GetFolderPath('Desktop')) 'dhruvar-logs'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$mode = if ($Stop) { 'stop' } else { 'start' }
$LogFile = Join-Path $LogDir "$mode-$stamp.log"
Start-Transcript -Path $LogFile | Out-Null

$script:failed = @()

function Step([string]$title) {
    Write-Host ''
    Write-Host ('=== ' + $title + ' ===') -ForegroundColor Cyan
}
function Ok([string]$msg)   { Write-Host ('[OK]   ' + $msg) -ForegroundColor Green }
function Warn([string]$msg) { Write-Host ('[WARN] ' + $msg) -ForegroundColor Yellow }
function Fail([string]$msg) { Write-Host ('[FAIL] ' + $msg) -ForegroundColor Red; $script:failed += $msg }

function Finish {
    Write-Host ''
    if ($script:failed.Count -eq 0) {
        Write-Host 'All steps finished without errors.' -ForegroundColor Green
    } else {
        Write-Host 'Problems:' -ForegroundColor Red
        $script:failed | ForEach-Object { Write-Host (' - ' + $_) -ForegroundColor Red }
    }
    Write-Host ('Log saved to: ' + $LogFile)
    Stop-Transcript | Out-Null
}

# ---- 0. Docker must be running ----
Step '0. Checking Docker'
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Fail 'Docker is not running. Start Docker Desktop, wait until it says running, then run this script again.'
    Finish
    exit 1
}
Ok 'Docker is running'

# ================= STOP =================
if ($Stop) {
    Step '1. Stopping the stack (data stays)'
    Set-Location $StackDir
    docker compose down
    if ($LASTEXITCODE -eq 0) { Ok 'Stack stopped' } else { Fail 'Stack did not stop cleanly' }

    Step '2. Stopping the map server (data stays)'
    Set-Location $MapDir
    docker compose -f docker-compose.yml -f $MapOverride down
    if ($LASTEXITCODE -eq 0) { Ok 'Map server stopped' } else { Fail 'Map server did not stop cleanly' }
    Finish
    exit 0
}

# ================= START =================
Step '1. Starting the map server'
if (-not (Test-Path $MapDir))      { Fail "Map folder not found: $MapDir" }
elseif (-not (Test-Path $MapOverride)) { Fail "Override file not found: $MapOverride" }
else {
    Set-Location $MapDir
    docker compose -f docker-compose.yml -f $MapOverride up -d
    if ($LASTEXITCODE -eq 0) { Ok 'Map server started' } else { Fail 'Map server failed to start (see the lines above)' }
}

Step '2. Starting the stack (database, API, dashboard, Nginx)'
if (-not (Test-Path $StackDir)) { Fail "Stack folder not found: $StackDir" }
else {
    Set-Location $StackDir
    docker compose up -d
    if ($LASTEXITCODE -eq 0) { Ok 'Stack started' } else { Fail 'Stack failed to start (see the lines above)' }
}

Step '3. Waiting for the API to answer (up to 90 seconds)'
$healthy = $false
for ($i = 1; $i -le 30; $i++) {
    try {
        $r = Invoke-RestMethod -Uri "$Url/api/health" -TimeoutSec 3
        if ($r.ok) { $healthy = $true; break }
    } catch { }
    Write-Host ("  waiting... ({0}/30)" -f $i)
    Start-Sleep -Seconds 3
}
if ($healthy) { Ok "API answers at $Url/api/health" } else { Fail 'API did not answer. Check: docker compose logs django (in the stack folder).' }

Step '4. Containers'
docker ps --format "table {{.Names}}`t{{.Status}}"

Step '5. Recent log lines of the API (last 8)'
Set-Location $StackDir
docker compose logs --tail 8 django

Step '6. Phone (USB)'
if ($NoPhone) {
    Warn 'Skipped (-NoPhone)'
} elseif (-not (Test-Path $Adb)) {
    Warn "adb not found at $Adb. Skipping the phone step."
} else {
    & $Adb start-server *> $null
    $devices = & $Adb devices | Select-String -Pattern "`tdevice$"
    if ($devices) {
        & $Adb reverse tcp:8000 tcp:8000
        if ($LASTEXITCODE -eq 0) { Ok 'Phone connected: it can reach the stack at http://localhost:8000' } else { Fail 'adb reverse failed' }
    } else {
        Warn 'No phone found. Plug it in by USB, unlock it, allow USB debugging, then run:'
        Warn ('  & "' + $Adb + '" reverse tcp:8000 tcp:8000')
    }
}

Step '7. Dashboard'
Write-Host ("Dashboard address: $Url")
if (-not $NoBrowser) { Start-Process $Url }

Finish

# Builds the dashboard into web/dist with the map basemap switched on (use PowerShell, not Git Bash: Git Bash rewrites /map/style.json).
$ErrorActionPreference = 'Continue'  # npm writes warnings to stderr; failures are caught by the exit-code checks below
Set-Location (Join-Path $PSScriptRoot '..\web')
npm ci
if ($LASTEXITCODE) { exit 1 }
$env:VITE_MAP_STYLE_URL = '/map/style.json'
npm run build
if ($LASTEXITCODE) { exit 1 }
if (Select-String -Path dist\assets\*.js -Pattern '/map/style.json' -Quiet) { Write-Host 'ok: dist contains /map/style.json' } else { Write-Host 'WARNING: basemap address not found in dist'; exit 1 }

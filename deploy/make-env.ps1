# Creates deploy/.env with random secrets if it does not exist. Prints nothing secret.
$dir = $PSScriptRoot
$target = Join-Path $dir '.env'
if (Test-Path $target) { Write-Host ".env already exists; not touching it"; exit 0 }
function New-Secret { $b = New-Object byte[] 36; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b).Replace('+','-').Replace('/','_') }
$text = Get-Content (Join-Path $dir '.env.example') -Raw
$text = $text -replace '(?m)^POSTGRES_PASSWORD=.*', "POSTGRES_PASSWORD=$(New-Secret)"
$text = $text -replace '(?m)^DJANGO_SECRET_KEY=.*', "DJANGO_SECRET_KEY=$(New-Secret)"
[IO.File]::WriteAllText($target, ($text -replace "`r`n", "`n"))
Write-Host "created deploy/.env (random secrets, not shown). Edit LAN_HOST in it."

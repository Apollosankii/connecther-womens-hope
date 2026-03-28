# ConnectHer Admin Portal — run script (PowerShell)
# Run from backend/AdminPortal folder.

$PortalDir = if (Test-Path "$PSScriptRoot\app.py") { $PSScriptRoot } else { Get-Location }
Set-Location $PortalDir
if (!(Test-Path "app.py")) {
    Write-Error "Run from woman-global\backend\AdminPortal (app.py not found)."
    exit 1
}

$env:FLASK_APP = "app"
$env:FLASK_ENV = "development"

Write-Host "ConnectHer Admin Portal" -ForegroundColor Cyan
Write-Host "URL: http://127.0.0.1:5020"
Write-Host "Preview: http://127.0.0.1:5020/preview"
Write-Host ""
python -m flask run --port 5020

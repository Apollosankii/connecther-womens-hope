# Copy canonical shared modules into edge functions that bundle only their own folder.
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$shared = Join-Path $root "_shared"
$targets = @("iap-sync", "revenuecat-webhook", "paystack-express")
foreach ($name in $targets) {
  $dest = Join-Path $root "$name\_shared"
  New-Item -ItemType Directory -Force -Path $dest | Out-Null
  Copy-Item (Join-Path $shared "*.ts") $dest -Force
  Write-Host "Synced _shared -> $name"
}

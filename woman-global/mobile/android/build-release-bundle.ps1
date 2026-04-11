# Build release AAB (bundleRelease). Run from repo root or from this android/ folder.
# Prerequisites:
#   - JDK 17 (JAVA_HOME valid, or Android Studio JBR detected below)
#   - keystore.properties with storeFile path to keystore whose SHA1 matches Play upload key
#   - woman-global/.env with PAYSTACK_PUBLIC_KEY (required for release in this project)

$ErrorActionPreference = "Stop"
$androidRoot = $PSScriptRoot
Set-Location $androidRoot

if (-not (Test-Path "$androidRoot\keystore.properties")) {
    Write-Error "Missing keystore.properties. Copy keystore.properties.example and fill in values."
}

$props = @{}
Get-Content "$androidRoot\keystore.properties" | Where-Object { $_ -match "^\s*([^#=]+)=(.*)$" } | ForEach-Object {
    $props[$matches[1].Trim()] = $matches[2].Trim()
}
$storeRel = $props["storeFile"]
if (-not $storeRel) { Write-Error "keystore.properties: missing storeFile" }
$storePath = if ([System.IO.Path]::IsPathRooted($storeRel)) { $storeRel } else { Join-Path $androidRoot $storeRel }
if (-not (Test-Path $storePath)) {
    Write-Error "Keystore not found: $storePath`nPlace your Play upload keystore there or fix storeFile in keystore.properties."
}

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $jbrCandidates = @(
        "${env:ProgramFiles}\Android\Android Studio\jbr",
        "${env:LocalAppData}\Programs\Android\Android Studio\jbr",
        "${env:ProgramFiles}\JetBrains\Android Studio\jbr"
    )
    foreach ($c in $jbrCandidates) {
        if (Test-Path "$c\bin\java.exe") {
            $env:JAVA_HOME = $c
            Write-Host "Using JAVA_HOME=$c"
            break
        }
    }
}

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    Write-Error "JAVA_HOME is not set to a valid JDK. Install JDK 17 or set JAVA_HOME to Android Studio's jbr folder."
}

Write-Host "Verifying release signing (check SHA1 vs Play Console upload key)..."
& "$androidRoot\gradlew.bat" signingReport --no-daemon 2>&1 | Select-String -Pattern "Variant:|SHA1|SHA-1|Config:|validity"

Write-Host "`nBuilding bundleRelease..."
& "$androidRoot\gradlew.bat" bundleRelease --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$bundle = Get-ChildItem "$androidRoot\app\build\outputs\bundle\release\*.aab" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($bundle) {
    Write-Host "`nAAB: $($bundle.FullName)"
} else {
    Write-Warning "Build finished but .aab not found in app\build\outputs\bundle\release\"
}

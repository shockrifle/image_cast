<#
    Downloads a self-contained, portable build toolchain into
    C:\workspace\android-toolchain -- no installers, nothing added to PATH or
    the registry, and deleting that one folder removes all of it.

        jdk17        Eclipse Temurin 17   (AGP 8.7 requires JDK 17)
        gradle-8.9   Gradle               (the version AGP 8.7 expects)
        android-sdk  platform 35, build-tools 35.0.0, platform-tools

    Running this accepts the Android SDK Terms and Conditions
    (https://developer.android.com/studio/terms) non-interactively.

    Re-running is safe: existing pieces are reused.
#>
param([string] $Toolchain = 'C:\workspace\android-toolchain')

$ErrorActionPreference = 'Stop'

$downloads = Join-Path $Toolchain 'downloads'
New-Item -ItemType Directory -Force -Path $downloads | Out-Null

function Get-File($Name, $Url) {
    $out = Join-Path $downloads $Name
    if (Test-Path $out) { Write-Host "have    $Name"; return $out }
    Write-Host "fetch   $Name"
    & curl.exe -sSL --retry 3 -o $out $Url
    if ($LASTEXITCODE -ne 0) { throw "Download failed: $Url" }
    return $out
}

$gradleZip = Get-File 'gradle-8.9-bin.zip' 'https://services.gradle.org/distributions/gradle-8.9-bin.zip'
$toolsZip  = Get-File 'commandlinetools-win.zip' 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip'
$jdkZip    = Get-File 'jdk17-win-x64.zip' 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse'

# --- Gradle ---------------------------------------------------------------
if (-not (Test-Path "$Toolchain\gradle-8.9")) {
    Write-Host 'expand  gradle'
    Expand-Archive -Path $gradleZip -DestinationPath $Toolchain -Force
}

# --- JDK 17 ---------------------------------------------------------------
if (-not (Test-Path "$Toolchain\jdk17")) {
    Write-Host 'expand  jdk17'
    Expand-Archive -Path $jdkZip -DestinationPath "$Toolchain\jdk-extract" -Force
    $inner = (Get-ChildItem "$Toolchain\jdk-extract" -Directory | Select-Object -First 1).FullName
    Move-Item $inner "$Toolchain\jdk17"
    Remove-Item "$Toolchain\jdk-extract" -Recurse -Force
}

# --- Android SDK ----------------------------------------------------------
$sdk = Join-Path $Toolchain 'android-sdk'
if (-not (Test-Path "$sdk\cmdline-tools\latest")) {
    Write-Host 'expand  cmdline-tools'
    # sdkmanager insists on living at <sdk>\cmdline-tools\latest\bin
    New-Item -ItemType Directory -Force -Path "$sdk\cmdline-tools" | Out-Null
    Expand-Archive -Path $toolsZip -DestinationPath "$Toolchain\cmdline-extract" -Force
    Move-Item "$Toolchain\cmdline-extract\cmdline-tools" "$sdk\cmdline-tools\latest"
    Remove-Item "$Toolchain\cmdline-extract" -Recurse -Force
}

$env:JAVA_HOME = "$Toolchain\jdk17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$sdkmanager = "$sdk\cmdline-tools\latest\bin\sdkmanager.bat"

# sdkmanager reads its prompts from real stdin, which a PowerShell pipeline
# does not satisfy -- feed it from a file through cmd instead.
$answers = Join-Path $downloads 'yes.txt'
Set-Content -Path $answers -Value ((1..80 | ForEach-Object { 'y' }) -join "`r`n") -Encoding ascii -NoNewline

Write-Host 'accept  sdk licenses'
cmd /c "`"$sdkmanager`" --sdk_root=`"$sdk`" --licenses < `"$answers`"" | Out-Null

Write-Host 'install sdk packages'
cmd /c "`"$sdkmanager`" --sdk_root=`"$sdk`" platform-tools `"platforms;android-35`" `"build-tools;35.0.0`" < `"$answers`"" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'sdkmanager failed to install the packages' }

# --- local.properties -----------------------------------------------------
$localProps = Join-Path $PSScriptRoot 'local.properties'
"sdk.dir=$($sdk -replace '\\', '\\' -replace ':', '\:')" | Set-Content -Path $localProps -Encoding ascii
Write-Host "wrote   $localProps"

Write-Host ''
Write-Host 'Toolchain ready. Build with:  .\build-local.ps1'

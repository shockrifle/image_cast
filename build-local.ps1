<#
    Builds the app with the portable toolchain in C:\workspace\android-toolchain.

    Nothing is installed system-wide: JAVA_HOME, ANDROID_HOME and the Gradle
    user home are all pointed at that folder for the lifetime of this script.

    Usage:  .\build-local.ps1              # assembleDebug
            .\build-local.ps1 assembleRelease
            .\build-local.ps1 tasks
#>
param([Parameter(ValueFromRemainingArguments = $true)] [string[]] $GradleArgs = @('assembleDebug'))

$ErrorActionPreference = 'Stop'

$toolchain = 'C:\workspace\android-toolchain'
$env:JAVA_HOME = Join-Path $toolchain 'jdk17'
$env:ANDROID_HOME = Join-Path $toolchain 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
# Keep the dependency cache beside the toolchain instead of in %USERPROFILE%.
$env:GRADLE_USER_HOME = Join-Path $toolchain 'gradle-home'

$gradle = Join-Path $toolchain 'gradle-8.9\bin\gradle.bat'

foreach ($path in @($env:JAVA_HOME, $env:ANDROID_HOME, $gradle)) {
    if (-not (Test-Path $path)) {
        throw "Missing toolchain component: $path (run setup-toolchain.ps1 first)"
    }
}

$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "JAVA_HOME    = $env:JAVA_HOME"
Write-Host "ANDROID_HOME = $env:ANDROID_HOME"
Write-Host "gradle       = $gradle"
Write-Host ''

& $gradle --project-dir $PSScriptRoot @GradleArgs
exit $LASTEXITCODE

param([switch]$Offline, [switch]$WithAndroidTestCompile)
$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
$toolsDir = Join-Path $projectDir '.tooling'
if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $toolsDir 'java'))) {
    $env:JAVA_HOME = (Get-ChildItem -LiteralPath (Join-Path $toolsDir 'java') -Directory | Select-Object -First 1).FullName
}
if (-not $env:JAVA_HOME) { throw 'JDK 17をインストールし、JAVA_HOMEを設定してください。Android StudioのGradle JDKも利用できます。' }
if (-not $env:ANDROID_HOME) {
    if (Test-Path -LiteralPath (Join-Path $toolsDir 'sdk')) { $env:ANDROID_HOME = Join-Path $toolsDir 'sdk' }
    elseif (Test-Path -LiteralPath (Join-Path $env:LOCALAPPDATA 'Android/Sdk')) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
}
if (-not $env:ANDROID_HOME) { throw 'Android StudioのSDK ManagerでAPI 36、Build-Tools 36.0.0、Platform-Toolsを用意し、ANDROID_HOMEを設定してください。' }
$env:GRADLE_USER_HOME = Join-Path $toolsDir 'gradle-cache'
$env:ANDROID_USER_HOME = Join-Path $toolsDir 'android-user'
$env:DEBUG = ''
[System.IO.File]::WriteAllText((Join-Path $projectDir 'local.properties'), ('sdk.dir=' + $env:ANDROID_HOME.Replace('\', '/').Replace(':', '\:') + "`n"), [System.Text.Encoding]::ASCII)
$gradleArgs = @('testDebugUnitTest', 'lintDebug', 'assembleDebug', '--no-daemon', '--max-workers=4', '--console=plain')
if ($Offline) { $gradleArgs += '--offline' }
if ($WithAndroidTestCompile) { $gradleArgs += 'compileDebugAndroidTestKotlin' }
Push-Location $projectDir
try {
    $ErrorActionPreference = 'Continue'
    if (Test-Path -LiteralPath (Join-Path $toolsDir 'gradle-8.13/bin/gradle.bat')) {
        & (Join-Path $toolsDir 'gradle-8.13/bin/gradle.bat') @gradleArgs
    } else { & (Join-Path $projectDir 'gradlew.bat') @gradleArgs }
    $buildExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($buildExit -ne 0) { throw "ビルドに失敗しました（終了コード $buildExit）。" }
    $artifactsDir = Join-Path $projectDir 'artifacts'
    New-Item -ItemType Directory -Force -Path $artifactsDir | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectDir 'app/build/outputs/apk/debug/app-debug.apk') -Destination (Join-Path $artifactsDir 'tsuzutore-debug.apk') -Force
    Write-Output ('APK: ' + (Join-Path $artifactsDir 'tsuzutore-debug.apk'))
} finally { Pop-Location }

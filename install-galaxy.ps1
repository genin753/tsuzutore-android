param([string]$Serial, [switch]$UseDebugBuild)
$ErrorActionPreference = 'Stop'
$sdkCandidates = @($env:ANDROID_HOME, (Join-Path $PSScriptRoot '.tooling/sdk'), (Join-Path $env:LOCALAPPDATA 'Android/Sdk'))
$adb = $null
foreach ($candidate in $sdkCandidates) {
    if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'platform-tools/adb.exe'))) {
        $adb = Join-Path $candidate 'platform-tools/adb.exe'
        break
    }
}
if (-not $adb) { $adb = (Get-Command adb.exe -ErrorAction SilentlyContinue).Source }
if (-not $adb) { throw 'Android SDK Platform-Tools（adb）が見つかりません。ANDROID_HOMEを設定してください。' }
# The release build is the everyday app (not debuggable, faster). -UseDebugBuild installs the debug build for testing.
$candidates = if ($UseDebugBuild) {
    @('artifacts/tsuzutore-debug.apk', 'app/build/outputs/apk/debug/app-debug.apk')
} else {
    @('artifacts/tsuzutore-release.apk', 'app/build/outputs/apk/release/app-release.apk')
}
$apk = $candidates | ForEach-Object { Join-Path $PSScriptRoot $_ } | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $apk) { throw 'APKがありません。先にbuild.ps1を実行してください。' }
Write-Output "インストールするAPK: $apk"
& $adb devices -l
$deviceArgs = @()
if ($Serial) { $deviceArgs = @('-s', $Serial) }
& $adb @deviceArgs install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'インストールできませんでした。USBデバッグの許可、接続台数、Galaxy側の設定を確認してください。古い版を新しい版の上に入れることはできません。' }
Write-Output 'つづトレをインストールしました。Galaxyのアプリ一覧から起動できます。'

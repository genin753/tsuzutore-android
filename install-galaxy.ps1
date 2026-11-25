param([string]$Serial)
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
$apk = Join-Path $PSScriptRoot 'artifacts/tsuzutore-debug.apk'
if (-not (Test-Path -LiteralPath $apk)) { $apk = Join-Path $PSScriptRoot 'app/build/outputs/apk/debug/app-debug.apk' }
if (-not (Test-Path -LiteralPath $apk)) { throw 'APKがありません。先にbuild.ps1を実行してください。' }
& $adb devices -l
$deviceArgs = @()
if ($Serial) { $deviceArgs = @('-s', $Serial) }
& $adb @deviceArgs install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'インストールできませんでした。USBデバッグの許可、接続台数、Galaxy側の設定を確認してください。' }
Write-Output 'つづトレをインストールしました。Galaxyのアプリ一覧から起動できます。'

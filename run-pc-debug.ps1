param(
    [switch]$Rebuild,
    [switch]$Debug,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
$pcProjectDir = Join-Path $projectDir 'pc-debug'
$toolsDir = Join-Path $projectDir '.tooling'
$jdkDir = Join-Path $toolsDir 'java/jdk-17.0.20.1+1'
$javaExe = Join-Path $jdkDir 'bin/java.exe'
$gradleBat = Join-Path $toolsDir 'gradle-8.13/bin/gradle.bat'
$classPathFile = Join-Path $pcProjectDir 'build/pc-runtime-classpath.txt'
$dataFile = Join-Path $toolsDir 'pc-debug-data/state.json'
$logsDir = Join-Path $toolsDir 'pc-debug-logs'
$buildLog = Join-Path $logsDir 'prepare-pc-debug.log'
$runLog = Join-Path $logsDir 'run-pc-debug.log'
$previousJavaHome = $env:JAVA_HOME
$previousGradleUserHome = $env:GRADLE_USER_HOME
$previousDebug = $env:DEBUG
$exitCode = 0

try {
    if (-not (Test-Path -LiteralPath $javaExe -PathType Leaf)) {
        throw "プロジェクト付属のJavaがありません: $javaExe"
    }
    if (-not (Test-Path -LiteralPath $gradleBat -PathType Leaf)) {
        throw "プロジェクト付属のGradleがありません: $gradleBat"
    }
    if (-not (Test-Path -LiteralPath (Join-Path $pcProjectDir 'settings.gradle.kts') -PathType Leaf)) {
        throw 'PCデバッグ用プロジェクトがありません。pc-debugフォルダを確認してください。'
    }
    New-Item -ItemType Directory -Force -Path $logsDir | Out-Null
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $dataFile) | Out-Null
    $env:JAVA_HOME = $jdkDir
    $env:GRADLE_USER_HOME = Join-Path $toolsDir 'gradle-cache'
    $env:DEBUG = ''

    $classPathEntries = @()
    $needsBuild = $Rebuild.IsPresent -or -not (Test-Path -LiteralPath $classPathFile -PathType Leaf)
    if (-not $needsBuild) {
        $classPathEntries = @(Get-Content -LiteralPath $classPathFile -Encoding UTF8 | ForEach-Object { $_.Trim() } | Where-Object { $_ })
        if ($classPathEntries.Count -eq 0) { $needsBuild = $true }
        foreach ($entry in $classPathEntries) {
            if (-not [System.IO.Path]::IsPathRooted($entry) -or -not (Test-Path -LiteralPath $entry)) {
                $needsBuild = $true
                break
            }
        }
        $preparedAt = (Get-Item -LiteralPath $classPathFile).LastWriteTimeUtc
        $trackedFiles = @(
            (Join-Path $pcProjectDir 'build.gradle.kts'),
            (Join-Path $pcProjectDir 'settings.gradle.kts'),
            (Join-Path $pcProjectDir 'gradle.properties')
        )
        foreach ($sourceDir in @((Join-Path $projectDir 'app/src/main'), (Join-Path $pcProjectDir 'src/main'))) {
            if (Test-Path -LiteralPath $sourceDir -PathType Container) {
                $trackedFiles += @(Get-ChildItem -LiteralPath $sourceDir -Recurse -File | Select-Object -ExpandProperty FullName)
            }
        }
        foreach ($sourceFile in $trackedFiles) {
            if ((Test-Path -LiteralPath $sourceFile -PathType Leaf) -and (Get-Item -LiteralPath $sourceFile).LastWriteTimeUtc -gt $preparedAt) {
                $needsBuild = $true
                break
            }
        }
    }

    if ($needsBuild) {
        Write-Host 'PC版を準備しています。初回はライブラリの取得に数分かかります。'
        $gradleArgs = @(
            '-p', $pcProjectDir, 'preparePcDebug', '--no-daemon', '--no-parallel',
            '--max-workers=2', '--console=plain',
            '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8'
        )
        if ($Offline) { $gradleArgs += '--offline' }
        $ErrorActionPreference = 'Continue'
        & $gradleBat @gradleArgs 2>&1 | Tee-Object -FilePath $buildLog | Out-Host
        $buildExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($buildExit -ne 0) { throw "PC版の準備に失敗しました（終了コード $buildExit）。ログ: $buildLog" }
        if (-not (Test-Path -LiteralPath $classPathFile -PathType Leaf)) { throw "クラスパスが生成されませんでした: $classPathFile" }
        $classPathEntries = @(Get-Content -LiteralPath $classPathFile -Encoding UTF8 | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    }
    if ($classPathEntries.Count -eq 0) { throw '起動に必要なクラスパスが空です。-Rebuildを付けて再実行してください。' }
    foreach ($entry in $classPathEntries) {
        if (-not [System.IO.Path]::IsPathRooted($entry) -or -not (Test-Path -LiteralPath $entry)) {
            throw "起動に必要なファイルがありません。-Rebuildを付けて再実行してください: $entry"
        }
    }

    $javaArgs = @('-Dfile.encoding=UTF-8', ('-Dtsuzutore.desktop.data=' + $dataFile))
    if ($Debug) {
        $javaArgs += '-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:5005'
        Write-Host 'デバッガ接続先: localhost:5005（Remote JVM Debug）'
    }
    $javaArgs += @('-cp', ($classPathEntries -join [System.IO.Path]::PathSeparator), 'jp.tsuzutore.app.desktop.DesktopMainKt')
    Write-Host 'つづトレのPC版を起動します。アプリを閉じると終了します。'
    Write-Host ('PC専用データ: ' + $dataFile)
    $ErrorActionPreference = 'Continue'
    & $javaExe @javaArgs 2>&1 | Tee-Object -FilePath $runLog | Out-Host
    $appExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($appExit -ne 0) { throw "PC版が終了しました（終了コード $appExit）。ログ: $runLog" }
} catch {
    $exitCode = 1
    Write-Host ''
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host '手順・ログの場所はPC-DEBUG.mdを確認してください。'
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:GRADLE_USER_HOME = $previousGradleUserHome
    $env:DEBUG = $previousDebug
}
exit $exitCode
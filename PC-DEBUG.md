# PCでつづトレを動かす

プロジェクト直下の **run-pc-debug.cmd をダブルクリック**します。初回はライブラリ取得とビルドに数分かかります。2回目からは準備済みのPC版を起動し、画面や処理のソースを変更した場合は自動でビルドし直します。

付属のJavaとGradleを使うため、Android SDKやエミュレーターの設定は不要です。初回のライブラリ取得にはインターネット接続が必要です。アプリ画面を閉じると終了します。

PC版は`pc-debug/`を独立したGradleプロジェクトとし、Compose Multiplatform 1.9.3で動かします。Android版の実際のCompose画面と`domain/TimerEngine.kt`、バックアップ検証処理のソースを直接共有するPC用アプリです。APKをエミュレーターで動かす方式ではありません。Android固有の通知・振動・バックグラウンドサービス・画面消灯時の動作はスマホで確認してください。

## ソースを止めて調べる

1. プロジェクトのターミナルで `./run-pc-debug.cmd -Debug` を実行します。
2. Android Studioで **Run → Edit Configurations → ＋ → Remote JVM Debug** を追加します。
3. **Debugger mode: Attach to remote JVM / Host: localhost / Port: 5005** にして保存します。
4. 共有している画面やタイマー処理のソースにブレークポイントを置き、作成した構成で **Debug** を押します。

デバッグ接続はこのPCのみに公開します。5005番ポートが使用中の場合は、以前起動したPCデバッグ版を終了してください。

## データとログ

PCのデータは `.tooling/pc-debug-data/state.json` に保存します。スマホの記録とは独立しており、自動同期しません。PC側で試してもスマホのデータは変更されません。バックアップ機能でデータを移す場合は、取り込み先の画面と確認内容を確認してください。

起動に失敗した場合、コマンド画面を閉じずに表示されたエラーを確認してください。ビルドログは `.tooling/pc-debug-logs/prepare-pc-debug.log`、実行ログは `.tooling/pc-debug-logs/run-pc-debug.log` です。

- 強制的にビルドし直す: `./run-pc-debug.cmd -Rebuild`
- 取得済みのライブラリだけでビルドする: `./run-pc-debug.cmd -Offline -Rebuild`

新しいソースを反映するには、開いているPC版を閉じてから起動し直してください。
## 確認した範囲（2026年10月22日）

起動スクリプト`run-pc-debug.ps1 -Offline`から「つづトレ — PCデバッグ」の実ウィンドウが生成され、PC専用JSONへ保存できることを確認しました。PC版のビルドと**13件のテストが成功し、失敗・エラーは0件**でした。

| 対象 | 成功件数 | 主な確認内容 |
| --- | ---: | --- |
| DesktopControllerTest | 3 | 実際のTimerEngineによる即開始、重複操作の抑止、終了後の一時停止復元、不正ファイルの保持 |
| DesktopStoreTest | 6 | JSON保存と再読込、設定の保持、不正データで既存ファイルを壊さないこと、実行中状態の復元、二重起動の排他 |
| DesktopUiTest | 4 | 起動時の開始ボタン、プランクを選んで即開始、カレンダーの月移動と予定ダイアログ、一時停止中の±10秒 |

ホームとカレンダーは共有Compose画面から画像を出力し、見た目も確認しました。画像はPC画面全体のキャプチャではなく、テスト中のアプリ自身の描画です。

- テストレポート: `pc-debug/build/reports/tests/test/index.html`
- テスト結果XML: `pc-debug/build/test-results/test/TEST-*.xml`
- ホーム画像: `pc-debug/build/reports/pc-ui/home.png`
- カレンダー画像: `pc-debug/build/reports/pc-ui/calendar.png`

この13件はPC用の追加検証です。Android版の機能追加時に成功した102件のJVMテスト、Android計測テストの端末実行状況とは別に記録しています。Android固有の通知・振動・サービスの実機動作を、PCテストの成功で確認済みとは扱いません。
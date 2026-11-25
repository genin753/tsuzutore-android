# 検証記録

検証日：2026年10月22日（日本時間）。PCデバッグ版の追加検証と、同日に行ったAndroid追加機能版の検証記録を分けて記載します。Android版の記録は、カレンダー、カウントダウン中の秒数調整、起動直後の開始導線、好きな順番での種目選択を追加した時点のものです。Windows、JDK 17.0.20.1、Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21、Android SDK 36を使用しています。

## PCデバッグ版の追加検証（2026年10月22日）

`pc-debug/`を独立したGradleプロジェクトとして追加し、Compose Multiplatform 1.9.3でAndroid版の実際のCompose画面とTimerEngine、バックアップ検証処理を共有しました。PC版のビルドと13件のテストが成功しました。XMLレポート上の失敗・エラーは0件です。

| PCテストクラス | 成功件数 |
| --- | ---: |
| DesktopControllerTest | 3 |
| DesktopStoreTest | 6 |
| DesktopUiTest | 4 |
| 合計 | **13** |

DesktopControllerTestでは、実際のTimerEngineによる即開始、重複するセット操作の抑止、最初の実施日の保存、終了・再読込後に明示的な再開を待つこと、不正な保存ファイルの保持を確認しました。DesktopStoreTestでは、JSON保存の往復、設定・実行状態の復元、無効な書込時の既存データ保護、二重起動の排他を確認しました。これらは一時フォルダーで動かし、ユーザーのPC記録やスマホの記録を使いません。

DesktopUiTestの4件は、共有している実際の画面で、ホームの即開始ボタン、プランクを選んで準備運動なしに開始する要求、カレンダーの月移動と予定ダイアログ、一時停止中の±10秒の操作を確認しました。Composeのルート描画からホームとカレンダーのPNGを保存し、目視でも表示を確認しました。

| 証跡 | 保存先 |
| --- | --- |
| HTMLテストレポート | `pc-debug/build/reports/tests/test/index.html` |
| JUnit XML | `pc-debug/build/test-results/test/TEST-*.xml` |
| ホームの描画画像 | `pc-debug/build/reports/pc-ui/home.png` |
| カレンダーの描画画像 | `pc-debug/build/reports/pc-ui/calendar.png` |

起動スクリプト`run-pc-debug.ps1 -Offline`からJavaプロセスと「つづトレ — PCデバッグ」というタイトルの実ウィンドウが生成され、PC専用JSONへ保存されることを確認しました。PCでの起動入口は`run-pc-debug.cmd`のダブルクリックです。`-Debug`付きの場合は、このPCの`127.0.0.1:5005`でRemote JVM Debugを受け付けます。操作・ログの手順は`PC-DEBUG.md`に記載しています。PCの保存先は`.tooling/pc-debug-data/state.json`で、スマホの保存領域とは独立しています。

この追加検証はPC版の範囲です。Android固有の通知、振動、Foreground Service、WakeLock、画面消灯中やGalaxy省電力下の動作は対象に含めません。以下のAndroid版102件の成功記録、未実施の端末操作、過去の実機記録はそれぞれの時点の証跡として保持します。

画面共有のために分離したAndroid側の戻る操作・画面点灯制御・フォント生成について、Androidプロジェクトの `assembleDebug --offline` も再実行し成功しました（4分56秒、41タスク中8件実行・33件は最新）。Android版の設定・タイマー処理は維持しています。この分離後にAndroidのユニットテスト・Lint・端末テストを再実行した記録ではありません。新しいAPKの署名と16KBアラインメントを確認し、配布APKを更新しています。PC版とAndroid再ビルドの証跡は `artifacts/verification/pc-debug/` に保存しています。
## Android追加機能版の検証（PCデバッグ対応前）

**当該Android版のビルド成功**

カレンダー等を追加した時点のAndroid版ソースに対して、次の一括検証が成功しました。JVMユニットテストはXMLレポートで102件すべて成功、失敗0件・エラー0件です。Lint、APK生成、Android計測テストのKotlinコンパイルも成功しています。Gradleログ上の所要時間は42分19秒で、70タスク中16件を実行、54件は最新でした。

```powershell
.\.tooling\gradle-8.13\bin\gradle.bat testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin --offline --no-daemon --no-parallel --max-workers=2 "-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8"
```

| JVMテストクラス | 成功件数 |
| --- | ---: |
| TimerEngineTest | 38 |
| WorkoutRulesTest | 13 |
| BackupCodecTest | 28 |
| ReminderTimeTest | 10 |
| ScheduleTest | 9 |
| QuickStartPlanTest | 4 |
| 合計 | **102** |

主な追加検証は、次のとおりです。

- 計測中／一時停止中の残り時間変更、最低1秒、調整後の完了記録、準備・休憩・運動の期限と合図。
- 好きな種目の選択、種目ごとのセット・左右の再開位置、完了済み実績の重複防止、旧来の順次進行との整合性。
- 月曜始まりの月表示、4〜6週の月、閏日・年境界、日付・時刻・ID・件数の検証と予定の並び順。
- 毎週の予定と日付指定予定からの最早リマインド候補、時刻省略時の設定値、過去日時、ローカル日付、夏時間。
- ホームからの開始と最初の種目の選択、短縮や初週設定を含む開始用プラン。
- JSONの予定・時間調整・任意順進捗の往復、旧JSONの既定値、不正値・参照・進行状態の拒否。

`schemaVersion: 1`とアプリのversionName `1.0.0`／versionCode `1`は維持しています。旧JSONで`scheduledWorkouts`が省略された場合は空リスト、セット実績・進行状態の`timeAdjustmentMs`が省略された場合は0となります。旧バックアップの互換性も今回のテスト対象です。

Android計測テストのソースは、Compose UI 4件と既存のRepository 6件の計10件です。`compileDebugAndroidTestKotlin`によるコンパイルは成功していますが、端末上での実行は行っていません。UIテストの存在・コンパイル成功を実機操作の成功として扱いません。

Android Lintはエラー0件・警告7件です。警告は依存バージョンの更新提案4件と、Modifier引数・KAPT・KTXの改善提案3件です。APK生成、apksignerのv2署名検証、zipalign -c -P 16 4の終了コード0を確認しました。最終APKは artifacts/tsuzutore-debug.apk です。証跡の保存先は`app/build/reports/`、`app/build/test-results/testDebugUnitTest/`、`artifacts/verification/`です。配布物のSHA-256は`artifacts/SHA256SUMS.txt`に記録します。

## 今回未実施の確認

今回はスマホが未接続で、ユーザーがAPKでの受け取りを選択しました。新機能版を実端末へインストールする操作と、端末上の新画面の操作確認は行っていません。未実施の範囲は次のとおりです。

- カレンダーの予定追加・編集・削除、月移動、実施済み表示、予定からの開始の実機操作
- 起動直後の開始導線と、任意順でのトレーニング一巡
- カウントダウン中／停止中の±10秒の実機操作と、合図・完了記録の確認
- Compose UI 4件・Repository 6件のAndroid計測テストの端末実行
- 回転、文字拡大、TalkBack、別アプリへの切り替え、画面消灯中の合図
- 通知拒否、音声データなし、Doze、Galaxy固有の省電力制限とリマインド配送
- プロセス終了・端末再起動後の実機復元

端末接続後の実行コマンドと手動確認手順はREADMEに記載しています。ストア公開は行っていません。

## 過去の実機確認との区別

2026年10月21日のスポーツデザイン版では、Galaxy（SC-51G）への上書きインストール・MainActivityの起動と、ホーム・一時停止中の計測・完了画面の表示を確認しました。これは今回追加したカレンダー、秒数調整、開始導線、任意順選択を実機確認した記録ではありません。

以下のAndroid Studio修復記録も、2026年10月21日に実施した過去の作業記録です。
## Android Studioのビルド環境修復（2026年10月21日）

Android Studioの実行時に `zip END header not found` が発生しました。ユーザー用Gradle 8.13配布物の `lib/plugins` にある104個のJARで、ZIP終端レコードを読み出せない状態を確認しました。

プロジェクト内の公式Gradle ZIPをwrapper指定のSHA-256と照合した後、展開済み配布物313ファイルを比較し、不一致の104ファイルを退避して修復しました。修復後は307個すべてのJARを正常に開けています。

Android Studioと同じJBR 21.0.11・ユーザー用Gradle保存先で `:app:assembleDebug` を実行し、4分45秒で成功しました（41タスク中9件実行、32件は最新）。証跡は `artifacts/verification/build-studio-repair.log` に保存しています。

修正版APKはGalaxy（SC-51G）へ `adb install -r` で上書きし、成功を確認しました。MainActivityがResumedになり、アプリ自身のプロセスが動作していることを確認しています。トレーニング一巡の端末操作は未検証です。
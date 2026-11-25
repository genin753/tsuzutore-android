# 検証記録

最新の検証は2026年11月25日の改善版（1.1.0）です。以前の記録は、その下に当時のまま残しています。

## 改善版の検証（2026年11月25日）

コードレビューで見つかった問題を直した1.1.0（versionCode 2）の記録です。修正前の状態は、Gitの最初のコミット「既存のつづトレを取り込み（修正前の基準）」に保存しています。修正前にも同じ環境でユニットテストを実行し、Android 102件・PC版13件がすべて成功することを確認してから修正しました。

### 直した内容

| 問題 | 修正 |
| --- | --- |
| 通知の許可がないと、リマインドをONにしても何も言わずに届かない | リマインドなどをONにした時点で許可を確認。許可がない間は設定画面に理由と「通知を許可する」「端末の通知設定を開く」を表示し、保存時にも知らせる |
| 許可の結果で「通知から一時停止・再開する」が書き換わり、未保存の設定変更が消える | 許可の結果では設定を変えない。設定画面は未保存の変更を保持し、保存は画面で編集した項目だけを最新の設定へ反映する（カレンダーの予定を上書きしない） |
| 回数を記録できず、前回比較・負荷の目安・フォーム確認が動かない | セット中に任意で回数を記録できる（前回の回数から開始）。記録画面から回数の入力・修正・未記録に戻すことも可能 |
| 記録を削除・修正できない | 記録画面から確認のうえ削除できる。表示中の終了済みの記録を消した場合は実行状態も消す。進行中の記録は削除・修正できない |
| 0.2秒ごとにアプリ全体を再描画し、5秒ごとに全履歴を読み直す | 画面へは表示上の秒が変わったときだけ渡す。5秒ごとの保存は実行状態の1件だけを更新する。記録画面は20日分ずつ表示し、集計は記録が変わったときだけ行う |
| debug版を常用している | デバッグ不可のrelease版を追加（同じローカル鍵で署名、コード圧縮なし）。ビルドとインストールのスクリプトはrelease版を標準にした |
| マナーモードだと効果音・音声が鳴らない。合図がサービスの中でしか鳴らない | イヤホン接続中は鳴らし、「マナーモード中も音を鳴らす」設定を追加。合図はアプリ全体の`CueCoordinator`から鳴らす |
| 休憩終了の合図の直前にWakeLockを解放している | 区間の終了後8秒はWakeLockを保持。トレーニング終了時は最後の合図のため5秒待ってからサービスを終了 |
| 保存データの形式が変わると起動できなくなるおそれ | アプリ内部の保存データは未知の項目を無視して読み込む。設定が読めない場合は初期値で起動。バックグラウンド処理の例外でアプリを落とさない。versionCodeを2に上げ、古い版の上書きを防ぐ |
| 回数制のセットを24時間以上放置すると、バックアップの書き出しが失敗し続ける | 経過時間を24時間で止め、記録を有効なまま残す |
| 「カレンダー」と「記録」のタブアイコンが同じ | 「記録」を棒グラフのアイコンに変更 |
| 日付・時刻を手で入力する | 予定と初回開始日はカレンダー、時刻は時刻入力から選ぶ |
| 準備運動の初期値がメインの開始導線で使われないのに、説明がない | 設定に、どの開始方法で使われるかを明記 |
| 入力の上限が画面と内部でずれている | 上限を`Limits`にまとめ、画面と保存時の検証で同じ値を使う |
| READMEの手順どおり普段のスマホで計測テストを実行すると、テスト後にアプリがアンインストールされて記録が消える（修正中に発見） | `gradle.properties`でテスト後のアンインストールを止め、READMEに事前のバックアップとrelease版の入れ直しを明記 |

署名鍵が`.tooling/debug.keystore`の1か所にしかない点は、置き場所を決められないため自動では直していません。新しい鍵が作られたときにビルドで警告を出すようにし、別の場所へのコピーをREADMEで案内しています。

### 検証結果

実行環境は以前と同じです（Windows、JDK 17.0.20.1、Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21、Android SDK 36）。`build.ps1`と同じく`.tooling`のJDK・SDK・Gradleを使い、ネット接続なしで実行しました。

```powershell
.\.tooling\gradle-8.13\bin\gradle.bat testDebugUnitTest lintDebug assembleDebug assembleRelease compileDebugAndroidTestKotlin --offline
.\.tooling\gradle-8.13\bin\gradle.bat -p pc-debug test --offline
```

| JVMテストクラス | 成功件数 | 内容 |
| --- | ---: | --- |
| TimerEngineTest | 38 | 以前と同じ |
| BackupCodecTest | 28 | 以前と同じ |
| WorkoutRulesTest | 13 | 以前と同じ |
| ReminderTimeTest | 10 | 以前と同じ |
| ScheduleTest | 9 | 以前と同じ |
| QuickStartPlanTest | 4 | 以前と同じ |
| HistoryEditTest | 5 | 追加。完了した回数制セットの回数の入力・変更・未記録化、対象外のセットと範囲外の値の拒否、回数の候補（今回の記録→履歴→目標の下限）、終了済みの記録だけを修正できること、24時間を超えて放置したセットが有効な記録・バックアップのまま残ること |
| StorageCodecTest | 3 | 追加。別バージョンの項目を含む保存データの読み込み、以前の整形済みデータとの互換、バックアップの取り込みは引き続き厳格なこと |
| UiLogicTest | 3 | 追加。設定の保存が画面の項目だけを反映してカレンダーの予定を残すこと、設定フォームの入力チェック、同じ表示秒の更新を画面の変化として扱わないこと |
| 合計 | **113** | 失敗0件・エラー0件 |

PC版のテストは17件成功（DesktopControllerTest 3件、DesktopStoreTest 6件、DesktopUiTest 8件）、失敗・エラー0件です。DesktopUiTestに、回数の任意記録、記録の削除の確認、通知が未許可のときのリマインド設定と保存、予定の日付・時刻の選択の4件を追加しました。

Android計測テストは、Compose UI 8件（PC版と同じ4件を追加）とRepository 8件（終了済みの記録を削除すると実績と実行状態も消えること、進行中の記録は削除できないことの2件を追加）の計16件です。`compileDebugAndroidTestKotlin`のコンパイルは成功しましたが、端末では実行していません。

Android Lintはエラー0件・警告3件です。警告はModifier引数の順序、KAPTの代わりにKSPを使う提案、KTXの`SharedPreferences.edit`の提案で、いずれも以前からあるものです。以前出ていた依存ライブラリの更新提案4件は、ネット接続なしで実行したため今回は出ていません（ライブラリは更新していません）。

| APK | 結果 |
| --- | --- |
| release（`artifacts/tsuzutore-release.apk`） | versionCode 2・versionName 1.1.0、デバッグ不可。v2署名を確認、`zipalign -c -P 16 4`は終了コード0 |
| debug（`artifacts/tsuzutore-debug.apk`） | v2署名を確認、`zipalign -c -P 16 4`は終了コード0 |
| 署名証明書 | 両方ともSHA-256 `833744fb81da4dc58ec0c23250825a05c89169674dc6d5f489def1820103ce64`。以前のAPKと同じ証明書のため、スマホの既存アプリへデータを残したまま上書きできる |

配布物のSHA-256は`artifacts/SHA256SUMS.txt`、証跡（ビルドログ、テスト結果XML、Lintレポート、APKの署名・アラインメントの確認結果）は`artifacts/verification/2026-11-25/`に保存しています。

### 今回未実施の確認

スマホが未接続のため、次の確認は行っていません。手順はREADMEの手動QAの表に追加しています。

- 改善版（release版）の実機インストールと、全体の操作
- Android 13以降の通知許可の画面、拒否したあとの「端末の通知設定を開く」、リマインドの配送
- マナーモード・サイレント中のイヤホン接続時の合図と、「マナーモード中も音を鳴らす」
- 画面オフ時の区切りの合図（区間終了後にWakeLockを8秒保持する変更の効果）と、終了時の最後の合図
- 日付・時刻の選択（DatePicker・TimeInput）のAndroid上の表示と操作
- Android計測テスト16件の端末実行

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
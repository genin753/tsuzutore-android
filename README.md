# つづトレ

自宅向けの「筋トレタイマー＋習慣化」Androidアプリです。日本語のKotlin／Jetpack Composeアプリで、登録、広告、課金、サーバー、有料APIを使いません。初回から全身メニューを利用できます。アプリの基本動作にネット接続は不要です。

2026年11月25日の改善版（1.1.0）では、リマインドの通知許可、回数の任意記録、記録の削除・修正、動作の重さ、マナーモード中の合図、画面オフ時の合図の途切れ、保存データの互換性などを直しました。同日のデザイン刷新版（1.2.0）では、見た目をGoogleのMaterial Design 3を土台に作り直し、Android 12以降は端末の壁紙の色に合わせるようにしました。11月26日のデザイン変更版（1.3.0）では、デザインを「ダーク・アスレチック」に変えました。常にダーク表示で、黒い背景に蛍光色の差し色、英字の大見出し、大きな数字を使います（詳しくは「デザイン」）。スマホが未接続のため、いずれも実機インストールと画面操作は未実施です。ビルド結果と検証範囲は、本書の「検証状況」と`VERIFICATION.md`に記載しています。

PC上で画面とタイマーを操作できるデバッグ版も追加しました。この作業フォルダーでは **run-pc-debug.cmd をダブルクリック**して起動します。Android版の実際のCompose画面とTimerEngineを共有し、PC専用のデータを使います。詳しい手順は[PC-DEBUG.md](PC-DEBUG.md)に記載しています。

## できること

- 月・水・金を初期予定とする全身メニュー。腕立て伏せ、リュックロー、スプリットスクワット、ヒップリフト、サイドレイズ、プランクを収録。
- メニューの追加・編集・複製・並べ替え・削除と種目の並べ替え。回数制／時間制、目標範囲、セット数、左右、休憩、負荷・難度を設定。
- 初回開始日から7暦日の初週モード。全種目2セットとし、適用期間を表示。設定で初回日・有効／無効を変更可能。
- ホームの「トレーニングを開始」から種目をタップして即開始。準備運動・短縮メニューは必要なときだけ調整。終了済みの画面はアプリ起動時に自動表示しません。
- 3分を初期値とする準備運動、開始待ち、時間制の準備カウント、運動、左右切替、休憩、一時停止、終了を区別。
- 回数制は「セット完了」を押すだけ。毎セットの回数・負荷の入力は不要で、セットの完了を自動保存。左右種目は左→右で1セットとし、右側は準備完了を待つ。
- 回数を残したいときだけ「＋ 回数も記録する（任意）」を押し、前回の回数（なければ目標の下限）から±で合わせて完了。記録画面から、あとで回数を入力・修正・未記録に戻すこともできる。
- 記録画面から、間違えて付けた記録を確認のうえ削除できる。進行中の記録は終了後に削除・修正する。
- 運動・休憩・準備のカウントダウン中／一時停止中に±10秒。残り最低1秒とし、短縮後の完了時間もそのまま記録。セット／種目スキップ、確認後の途中終了。通常メニューは休憩終了後に開始を待ち、種目の最終セット後に余分な休憩を入れません。
- 自動進行する自由インターバルタイマー。初期値は20秒運動＋10秒休憩×8回。研究上のタバタと同じ強度・効果を示すものではありません。
- 効果音・振動・日本語音声を個別に設定し、設定画面で試聴。マナーモード中は鳴らさないのが初期値で、イヤホン接続中は鳴らす。「マナーモード中も音を鳴らす」で常に鳴らすこともできる。画面点灯の維持は別設定で、初期値はOFF。
- 週間実施日数、日付別履歴、種目ごとの完了セットと時間を記録。以前に入力した回数・負荷の履歴も保持。終了後の追加入力は不要。
- 月表示のカレンダーで日付ごとにメニュー・任意の時刻を登録し、予定を追加・編集・削除。日付と時刻は手入力ではなく、カレンダーと時刻入力から選ぶ。毎週の予定曜日と実施日も表示。
- セット間は好きな種目を選んで直接開始。セット数・左右の進捗を引き継ぎ、完了済みのセットは重複記録しません。
- 毎週／日付指定の予定日リマインドと、予定を含むJSONバックアップの書き出し／取り込み。リマインドをONにすると通知の許可を確認し、許可がないときは設定画面に理由と「通知を許可する」「端末の通知設定を開く」を表示する。

回数を入力せず完了したセットは、履歴に「完了」と表示します。実際の回数は未記録として扱い、目標回数や0回を実績として保存しません。回数を記録したセットがある場合に、前回との回数比較・「フォームを保てた」・負荷を上げる目安を表示します。

## 固定した開発環境

| 項目 | バージョン／値 |
| --- | --- |
| applicationId／名前空間 | `jp.tsuzutore.app` |
| バージョン | `1.3.0` / versionCode `4` |
| minSdk | 26（Android 8.0） |
| compileSdk / targetSdk | 36 / 36 |
| Android Build Tools | 36.0.0 |
| JDK | 17（作業環境で用意したものは17.0.20.1） |
| Gradle / Android Gradle Plugin | 8.13 / 8.13.2 |
| Kotlin／Composeコンパイラ／serializationプラグイン | 2.2.21 |
| Compose BOM | 2025.11.01 |
| Core KTX / Activity Compose | 1.17.0 / 1.12.0 |
| Lifecycle | 2.9.4 |
| Room | 2.8.4 |
| DataStore | 1.2.0 |
| Coroutines | 1.10.2 |
| kotlinx.serialization JSON | 1.9.0 |
| JUnit | 4.13.2 |
| AndroidX Test JUnit / Runner | 1.3.0 / 1.7.0 |

バージョンの正本はルートと`app/`の`build.gradle.kts`、Gradle配布物の固定値は`gradle/wrapper/gradle-wrapper.properties`です。Gradle配布物のSHA-256を固定しています。

公式資料の互換表を確認し、API 36とKotlinプラグインを組み合わせて検証できる安定版を固定しました。すべてを最新版とする選定ではありません。コンパイラはGradleプロセス内で実行し、Kotlinの別プロセス用ファイルをユーザープロファイルへ作る必要をなくしています。

## スマホをつながずPCでデバッグする

**プロジェクト直下の`run-pc-debug.cmd`をダブルクリック**します。この作業フォルダーのJava・Gradleを使い、初回はPC版をビルドします。Android SDK・エミュレーター・スマホの接続は不要です。以降はソース変更を検出したときにビルドし直します。

PC版は`pc-debug/`の独立したGradleプロジェクトです。Compose Multiplatform 1.9.3を利用し、Android版の`ui/`、`domain/`のTimerEngine、バックアップ検証のソースを直接共有します。画面の開始導線、カレンダー、種目選択、残り時間調整をPCで操作できます。Androidの通知・振動・サービスや画面消灯時の動作は実機で確認してください。

PCの設定・メニュー・予定・記録は`.tooling/pc-debug-data/state.json`に保存し、スマホとは別に管理します。スマホの記録を自動で読み書きすることはありません。`run-pc-debug.cmd -Debug`で起動すると、Android StudioのRemote JVM Debugから`localhost:5005`へ接続して共有ソースのブレークポイントを使えます。起動・ログ・詳細手順は[PC-DEBUG.md](PC-DEBUG.md)を参照してください。

起動スクリプトで「つづトレ — PCデバッグ」の実ウィンドウが生成され、PC専用JSONへ保存できることを確認しました。PC版のテストはデザイン変更版（1.3.0）で**20件成功、失敗・エラー0件**です。内訳はDesktopController 3件、DesktopStore 6件、共有Compose UI 8件、デザイン確認用の画面書き出し3件です。書き出した主な画面は目視で確認しました。テストレポートは`pc-debug/build/reports/tests/test/index.html`、画像は`pc-debug/build/reports/pc-ui/design/`にあります。Android端末での確認範囲は後述の記録と区別しています。

## Windowsで開く・ビルドする

### Android Studio

1. Android Studioで、この`tsuzutore-android`フォルダーをプロジェクトとして開きます。
2. SDK ManagerでAndroid SDK Platform 36、Build-Tools 36.0.0、Android SDK Platform-Toolsをインストールします。
3. Gradle JDKをJDK 17へ設定し、Gradle Syncを実行します。
4. `app`を選択してRunするか、Gradleで`testDebugUnitTest`、`lintDebug`、`assembleDebug`を実行します。

初回のGradle／依存ライブラリ／SDKの取得にはネット接続が必要です。アプリ実行時には不要です。

### PowerShell

JDK 17とAndroid SDKを用意し、プロジェクト直下で実行します。パスは使用環境に合わせて変更してください。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
powershell -ExecutionPolicy Bypass -File .\build.ps1 -WithAndroidTestCompile
```

`build.ps1`は`testDebugUnitTest`、`lintDebug`、`assembleDebug`、`assembleRelease`を実行し、指定時にはAndroid計測テストのKotlinコンパイルも行います。成功した場合だけ、普段使うrelease版を`artifacts/tsuzutore-release.apk`、検証用のdebug版を`artifacts/tsuzutore-debug.apk`へコピーします。端末上の計測テストは、このコマンドでは実行しません。

release版はデバッグ不可のビルドで、debug版より画面の動作が軽くなります。debug版と同じローカル鍵で署名するため、debug版を入れたスマホにもデータを残したまま上書きできます（コード圧縮は使っていません）。

この作業フォルダーの`.tooling/`にJDK・SDK・Gradleを用意してある場合は、スクリプトがそれらを利用します。SDKは通常の`%LOCALAPPDATA%\Android\Sdk`も探索します。`.tooling/`はソース配布に含める前提ではないため、別PCでは上記の環境を用意してください。スクリプトはローカルのSDKパスを`local.properties`へ記録します。

必要な依存物がキャッシュに揃った後は、`-Offline`を付けて再実行できます。未取得の依存物がある状態では失敗します。

直接Gradleを使う場合：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat compileDebugAndroidTestKotlin
```

出力先は次のとおりです。**現在の生成・検証結果は後述の検証状況を確認してください。**

| 成果物 | パス |
| --- | --- |
| Gradleが生成するrelease APK（普段使う版） | `app/build/outputs/apk/release/app-release.apk` |
| Gradleが生成するdebug APK（検証用） | `app/build/outputs/apk/debug/app-debug.apk` |
| ビルドスクリプトがコピーするAPK | `artifacts/tsuzutore-release.apk`、`artifacts/tsuzutore-debug.apk` |
| ソース一式（鍵・SDK・キャッシュを除外） | `artifacts/tsuzutore-source.zip` |
| 今回の検証結果 | `VERIFICATION.md` |
| ユニットテストレポート | `app/build/reports/tests/testDebugUnitTest/index.html` |
| Android Lintレポート | `app/build/reports/lint-results-debug.html` |
| 端末計測テストレポート | `app/build/reports/androidTests/connected/debug/index.html` |
| Roomのスキーマ出力 | `app/schemas/` |

debug用の秘密鍵は初回ビルド時にJDKの`keytool`で`.tooling/debug.keystore`へローカル生成します。鍵、`.tooling/`、`local.properties`、ビルド出力はGit管理対象外です。配布用のrelease署名や署名資格情報は含めていません。

**別の秘密鍵で再ビルドしたAPKでは、既存アプリへ上書き更新できません。** 鍵は`.tooling/debug.keystore`の1か所にしかないため、USBメモリやクラウドなど別の場所にもコピーしておいてください。鍵がなくなってビルド時に新しい鍵が作られた場合は、ビルドログに警告が出ます。鍵が異なる場合は、アプリ内からJSONを保存してから既存アプリをアンインストールし、新APKをインストールしてJSONを取り込みます。アンインストールすると端末内のアプリデータは消えます。

versionCodeは1.1.0で`2`、1.2.0で`3`、デザイン変更版の1.3.0で`4`に上げました。古い版のAPKを新しい版の上に誤って入れることはできません。次に配布するときも、versionCodeを1つ以上上げてください。

## WindowsからGalaxyへインストール

1. Galaxyの「設定 → 端末情報 → ソフトウェア情報」でビルド番号を7回タップし、開発者向けオプションを有効にします。端末のバージョンによって項目名は異なります。
2. 開発者向けオプションで「USBデバッグ」をONにし、データ通信対応USBケーブルでPCへ接続します。
3. 端末に表示された、このPCからのUSBデバッグ許可を確認します。Windowsで認識されない場合はケーブル・USBモード・SamsungのUSBドライバーを確認してください。
4. APKの生成後、プロジェクト直下で実行します。

```powershell
powershell -ExecutionPolicy Bypass -File .\install-galaxy.ps1
```

このスクリプトは普段使うrelease版（`artifacts/tsuzutore-release.apk`）を入れます。検証用のdebug版を入れる場合は`-UseDebugBuild`を付けます。

複数端末が接続されている場合は、`adb devices -l`で表示されたシリアルを指定します。

```powershell
powershell -ExecutionPolicy Bypass -File .\install-galaxy.ps1 -Serial '端末のシリアル'
```

直接実行する場合：

```powershell
$taskAdb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
& $taskAdb devices -l
& $taskAdb install -r .\artifacts\tsuzutore-release.apk
& $taskAdb shell am start -n jp.tsuzutore.app/.MainActivity
```

端末側の保護設定でUSBインストールが制限されている場合は、端末に出る案内を確認してください。アプリは初回起動時に登録を要求せず、通知権限も最初の開始条件にしません。

## Gitでの管理

このフォルダーは2026年11月25日からGitで管理しています。最初のコミット「既存のつづトレを取り込み（修正前の基準）」が修正前の状態です。`.tooling/`、署名鍵、ビルド出力、`artifacts/`はGitに入れません。

このフォルダーの所有者は、以前の作業で使われた別のWindowsアカウントになっています。そのため、そのまま`git`を実行すると「dubious ownership」で止まります。自分で使う場合は、次のどちらかを行ってください。

- このフォルダーだけを信頼する設定を追加する: `git config --global --add safe.directory "<このフォルダーの完全なパス（区切りは />"`
- エクスプローラーのフォルダーのプロパティ → セキュリティ → 詳細設定から、所有者を自分のアカウントに変更する

## 構成と保存の扱い

```text
app/src/main/java/jp/tsuzutore/app/
  domain/       不変モデル、入力検証、計時状態機械、初週・週間集計・負荷提案
  data/         Room、DataStore、バックアップ検証と置換
  platform/     単一タイマー所有者、Foreground Service、合図の再生（CueCoordinator）、音・振動・音声、リマインド
  ui/           Compose画面、アプリ用の色・文字・余白・部品
  AppViewModel.kt / MainActivity.kt / TsuzuToreApplication.kt
```

UIはViewModelとFlowから状態を受け取ります。Application内の`WorkoutController`が1つの`TimerEngine`を所有し、画面・サービス・通知が同じ状態へ操作を送ります。画面回転で新しいタイマーは作りません。操作の直列化とリビジョン照合により、古い操作や連打が次のセットを二重完了しないようにしています。

タイマーは合図のために0.2秒ごとに状態を更新しますが、画面へ渡すのは表示上の秒やセットが変わったときだけです。合図はアプリ全体の`CueCoordinator`が鳴らすため、Foreground Serviceを開始できなかった場合も画面を開いていれば合図が出ます。起動時に復元した状態や、取り込んだ終了済みの記録で合図を鳴らし直すことはありません。

計時は`SystemClock.elapsedRealtime()`の期限から残り時間を計算します。毎秒1を減らす方式ではなく、端末の日時を運動時間へ混ぜません。回数制では時間経過を回数や完了へ変換しません。

テンプレートと種目、セッション、セット実績をRoomへ分離して保存し、セッションには開始時のメニューをスナップショットとして保持します。後日の編集やテンプレート削除で履歴は変わりません。安定したセッションID・セットIDを使い、セッション・実績・実行状態を同じRoomトランザクションで保存します。設定とカレンダーの予定はDataStoreへ保存します。

アプリ内部の保存データは、別のバージョンが追加・削除した項目を無視して読み込みます。アプリの更新や古いAPKへの入れ替えで、起動のたびに読み込みに失敗することを防ぐためです。設定が読めない場合は初期値で起動し、次の保存で置き換えます。バックアップの取り込みは引き続き、未知の項目を含むファイルを拒否します。

主要な状態遷移はすぐに保存し、計測中はおよそ5秒ごとにチェックポイントを保存します。チェックポイントで更新するのは実行状態の1件だけです。セット実績は状態遷移のときに保存するため、計測中に全期間の履歴を読み直すことはありません。回数制のセットを24時間以上放置した場合、経過時間は24時間で止まり、記録とバックアップは有効なまま残ります。プロセス終了／再起動後は、最後に保存した状態を**一時停止**として読み込み、再開を押すまで進行や合図を始めません。最後のチェックポイント以降の未保存時間や、停止中に経過した時間は実施済みへ補完しません。通常終了・明示的な中断でも、入力中で未確定の回数は自動保存しません。

週は端末のローカル日付で月曜始まりです。完了ボタンで確定した回数制セット、または時間の実績がある筋トレセットを1件以上行った日を数え、同日の複数セッションは1日です。自由タイマーは別の履歴として保持し、筋トレの週間日数には含めません。スキップ・未実施は達成にせず、通常版／短縮版、完了／一部実施／中断を区別します。回数未記録でも「セット完了」を押した分は実施済みです。開始日のローカル日付とタイムゾーンを保存します。

### バックアップ

設定画面からStorage Access Frameworkのファイル選択でJSONを書き出し／取り込みます。`schemaVersion: 1`を維持し、設定（日付指定の予定を含む）、テンプレート、セッション、セット実績、実行状態を保存します。旧JSONで`scheduledWorkouts`が省略されている場合は空の予定、`timeAdjustmentMs`が省略されている場合は0として読み込むため、以前のバックアップも利用できます。任意順に選んだ種目のセット・左右の進捗と時間調整値も保存します。最大8MBで、形式、範囲、ID重複、参照、実績の整合性を検証します。プレビューと置換確認の後に取り込み、読み込んだ実行状態は一時停止になります。

不正な入力は保存前に拒否します。Roomの置換はトランザクションで行い、DataStoreとの間はインポート記録を使って途中終了後の設定を整合させます。Androidの自動バックアップは無効です。端末移行・アンインストール前には、アプリのJSONバックアップを別の場所へ保存してください。

## バックグラウンド・権限

明示的に開始したタイマーはForeground Serviceで維持します。センサー計測、位置情報、継続的なメディア再生を行わず、短い合図を主目的に`mediaPlayback`を宣言したり、長時間の運動へ`shortService`を流用したりしない構成です。ほかの用途別種別に該当しないユーザー開始の運動・休憩タイマーとして`specialUse`を選びました。Manifestに種別、権限、`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`の用途説明を記載しています。ストアでの種別適合性・審査通過は保証しません。

部分WakeLockは準備運動・開始前カウント・時間制運動・休憩の、期限を持つ区間に限定し、残り時間＋15秒のタイムアウト付きで取得します。区間が終わった直後は、その区切りの合図（「準備ができたら開始してください」など）が画面オフでも途切れないよう、8秒だけ保持してから自動で解放します。トレーニング終了時は最後の合図のために5秒待ってからサービスを終了し、その間に次のトレーニングを始めた場合は続けて使います。一時停止・例外・サービス終了時はすぐに解放します。回数制の実施中に常時取得せず、画面点灯の設定とも分離しています。

| 権限 | 用途 |
| --- | --- |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | ユーザー開始の運動・休憩タイマーをバックグラウンドで維持 |
| `WAKE_LOCK` | 上記の計測区間だけCPUの休止を抑える |
| `VIBRATE` | ユーザーが有効にした合図 |
| `POST_NOTIFICATIONS` | Android 13以降の通知表示。通知を有効にする操作の文脈で要求 |
| `RECEIVE_BOOT_COMPLETED` | 有効なリマインドを再設定。運動を自動再開しない |

`INTERNET`、広範なストレージ、センサー、位置情報、正確なアラームの権限は要求しません。通知を拒否してもメニュー、画面上のタイマー、履歴、バックアップを利用できます。Android 13以降で通知が拒否された場合、Foreground Serviceの実行表示はOSの仕様に従い、通常の通知欄に表示されない場合があります。

通知の許可は、設定画面で「習慣リマインド」または「通知から一時停止・再開する」をONにしたときに確認します。許可がない間は、設定画面の「通知」欄とリマインドの下に、届かない理由と「通知を許可する」「端末の通知設定を開く」を表示します。許可の結果で他の設定を書き換えることはありません。アプリに戻るたびに許可の状態を読み直します。設定の保存は、設定画面で編集した項目だけを最新の設定へ反映するため、カレンダーの予定を上書きしません。保存前の変更は、保存や取り込みの完了で消えません。

実行中通知と習慣リマインドは別の通知チャネルです。通知を許可した場合、実行中通知から種目の確認とアプリへの復帰ができます。「通知から一時停止・再開する」を有効にすると操作ボタンも表示します。バックグラウンド計時中の常駐通知自体は、サービスの実行表示として必要です。

リマインドは初期OFFです。有効時は毎週の予定曜日とカレンダーの日付指定予定から、次の最も早い日時を選びます。日付指定で時刻を省略した場合は設定の通知時刻を使います。`AlarmManager`の不正確な単発アラームによる**目安**の通知で、設定・日時・タイムゾーン・再起動・アプリ更新時に再設定します。同日は1回までとし、その日に実施済みの筋トレがある場合は抑止します。

音声は端末にインストール済みの、ネット接続を要求しない日本語TextToSpeech音声だけを選びます。音声が未導入／初期化中でも画面・有効な効果音・振動で操作できます。アプリから音量・サイレント設定は変更しません。マナーモード・サイレント中は音声・効果音を抑えますが、有線・Bluetoothのイヤホン接続中は鳴らします。設定の「マナーモード中も音を鳴らす」をONにすると、常に鳴らします。

Foreground ServiceとWakeLockでも、強制停止、メーカー固有の省電力、すべてのDoze制約への対応を保証しません。強制停止後に運動や通知が自動復活する扱いにはしていません。Galaxyの省電力設定を含む実機確認が必要です。

## デザイン

デザインシステムは「ダーク・アスレチック」です。Googleの[Material Design 3](https://m3.material.io/)のダークテーマを土台に、Nike Training ClubやStravaのようなトレーニングアプリの見せ方（黒い背景、蛍光色の差し色、大きな数字）を取り入れています。端末の設定に関係なく常にダーク表示で、夜のトレーニングでもまぶしくならないようにしています。

| 項目 | 内容 |
| --- | --- |
| 色 | 背景はほぼ黒の #0A0A0A、タイマー画面は純黒の #000000 です。カードは一段明るいグレーにしています。差し色は蛍光イエローグリーンの「Volt」#D4FF3A だけで、操作ボタン・運動中・進み具合に使います。休憩中はシアン #3DDCFF、残り3秒はオレンジレッド #FF5A1F で表します。文字は純白ではなく #EDEDED にしてまぶしさを抑え、どの文字色も背景とのコントラスト比4.5:1以上（WCAG AA）です。グレーには色みを付けず、端末の壁紙の色にも合わせません |
| 形 | Material 3 Expressiveの角丸スケール（4・8・12・16・20・28・32・48dp、完全な丸）です。値はCompose Material3 1.4の`ShapeTokens`と同じで、ボタンは丸いピル型です |
| 文字 | 英字の大見出しと数字には[Anton](https://fonts.google.com/specimen/Anton)（SIL Open Font License 1.1）を使います。フォントは`app/src/main/res/font/`に同梱し、ライセンス文を`app/src/main/assets/licenses/OFL-Anton.txt`としてAPKに入れています。Antonには日本語の文字がないため、日本語は端末のフォントのままです。日本語の本文は、行の高さを文字サイズの150%以上にしています（デジタル庁デザインシステムの基準） |
| アイコン | Googleの[Material Symbols](https://github.com/google/material-design-icons)（Rounded、Apache License 2.0）から、使う40個のパスだけを`ui/Symbols.kt`に収録しています。アイコン用ライブラリは追加していません |
| アプリアイコン | 黒地にVoltの「timer」を置いたアダプティブアイコンです。Android 13以降の「テーマアイコン」にも対応しています |

ホームは「TODAY」「REST DAY」などの英字の大見出しで始まり、その下に今日のメニューのカードを置きます。カードには今日の進み具合を示す進捗リング（予定したセットのうち実施した割合）と、種目数・セット数の大きな数字を並べます。今週の実施日は丸いチップと進捗バーで示します。

タイマーは全面黒で、残り時間を画面の幅いっぱいの数字で表示します。数字の色は運動中がVolt、休憩中がシアンです。残り3秒になるとオレンジレッドに変わり、背景が1秒ごとに暗い赤で脈打って、数字も小さく弾みます。白く光らせないので、暗い部屋でもまぶしくなりません。Antonは「1」だけ幅が狭く、等幅数字の機能もありません。そのため1桁ずつ同じ幅の枠に入れて、数字が変わっても左右に揺れないようにしています。開始前は、次のセットの目標（回数または秒数）を大きく表示します。

英字の見出しは日本語の見出しを大きく見せるための飾りです。TalkBackでは英字を読み上げず、日本語の見出しと状態（「運動中」など）を読み上げます。操作領域は最小48dpです。日本語の操作名、TalkBackの状態説明、画面幅に合わせて大きさを決めるタイマーの数字（文字の拡大設定でもはみ出さない）を維持しています。毎セットの回数・負荷の入力は不要です。

色・文字・部品は`ui/Theme.kt`にまとめています（`Athletic`・`AthleticColors`、`TtType`、`TtShapes`、`MainButton`、`ProgressRing`、`StableNumber`など）。

PC版のテストでは、主な画面をスマホと同じ大きさ（412×892dp）で画像に書き出し、`pc-debug/build/reports/pc-ui/design/`に保存します。残り3秒の演出は、テスト用の時計を止め、脈打つ途中の画面を書き出しています。

1.2.0（Material 3・壁紙の色に合わせる版）のAPKとソースは、`artifacts/archive/`に残しています。

## 検証状況

### デザイン変更版（2026年11月26日・1.3.0）

デザインを「ダーク・アスレチック」に変えた1.3.0の記録です。**ビルド成功**。詳細は`VERIFICATION.md`に記録しています。

| 検証対象 | 結果 |
| --- | --- |
| JVMユニットテスト | **113件成功、失敗・エラー0件** |
| PC版テスト | **20件成功、失敗・エラー0件**。ホーム・タイマー（残り3秒を含む）・各ページを画像に書き出して確認 |
| Android Lint | **エラー0件・警告3件**（1.2.0と同じ提案のみ） |
| APK | debug版・release版とも生成。v2署名・16KBアラインメント・以前と同じ証明書を確認。release版はversionCode 4。フォントとライセンス文の同梱を確認 |
| Android計測テスト | 16件のKotlinコンパイル成功、端末実行は未実施 |
| 実機での確認 | **未実施**（タイマーの数字の大きさ、残り3秒の演出、フォント、起動画面とシステムバーの色の実機表示は未確認） |

### デザイン刷新版（2026年11月25日・1.2.0）

Material Design 3を土台にデザインを作り直した1.2.0の記録です。**ビルド成功**。詳細は`VERIFICATION.md`に記録しています。

| 検証対象 | 結果 |
| --- | --- |
| JVMユニットテスト | **113件成功、失敗・エラー0件** |
| PC版テスト | **20件成功、失敗・エラー0件**。主な画面をライト・ダーク・壁紙の色の例で画像に書き出して確認 |
| Android Lint | **エラー0件・警告3件**（アイコンフォルダー名の`-v26`の提案と、以前からあるKAPT・KTXの提案） |
| APK | debug版・release版とも生成。v2署名・16KBアラインメント・以前と同じ証明書を確認。release版はversionCode 3 |
| Android計測テスト | 16件のKotlinコンパイル成功、端末実行は未実施 |
| 実機での確認 | **未実施**（壁紙の色・ダークモード・テーマアイコンの実機表示は未確認） |

### 改善版（2026年11月25日）

レビューで見つかった問題を直した1.1.0の記録です。**ビルド成功**。詳細は`VERIFICATION.md`に記録しています。

| 検証対象 | 結果 |
| --- | --- |
| JVMユニットテスト | **113件成功、失敗・エラー0件**。以前の102件に、記録の修正・回数の候補・放置したセット（5件）、保存データの読み込み（3件）、設定の保存・画面更新の間引き（3件）を追加 |
| PC版テスト | **17件成功、失敗・エラー0件**。共有画面で、回数の任意記録、記録の削除、通知が未許可のときのリマインド、日付・時刻の選択を操作 |
| Android Lint | **エラー0件・警告3件**。以前からあるModifier引数・KAPT・KTXの提案。以前出ていた依存ライブラリの更新提案4件は、ネット接続なしで実行したため今回は出ていない（ライブラリは更新していない） |
| APK | debug版・release版とも生成。v2署名、16KBアラインメント、以前のAPKと同じ署名証明書（スマホの既存アプリへ上書きできる）を確認。release版はversionCode 2・デバッグ不可 |
| Android計測テスト | Compose UI 8件＋Repository 8件の計16件。Kotlinコンパイル成功、端末実行は未実施 |
| 実機での確認 | **未実施**（端末未接続）。通知許可の画面、マナーモードとイヤホンでの合図、画面オフ時の合図、release版の実機動作、日付・時刻選択のAndroid上の表示は未確認 |

### 追加機能版（2026年10月22日）の記録

以下は2026年10月22日にカレンダー・秒数調整・開始導線・自由な種目選択を追加した時点のAndroid版の記録です。**当該版のビルド成功**。ユニットテスト102件、Lint、APK作成、Android計測テストのKotlinコンパイルが成功しました。詳細は`VERIFICATION.md`に記録しています。

| 検証対象 | 今回の構成・範囲 |
| --- | --- |
| JVMユニットテスト | **102件成功、失敗・エラー0件**。TimerEngine 38件、WorkoutRules 13件、BackupCodec 28件、ReminderTime 10件、Schedule 9件、QuickStartPlan 4件 |
| 時間調整・任意順 | 計測中／停止中の±10秒、最低1秒、調整後の完了記録、任意順のセット・左右進捗、二重保存の防止 |
| カレンダー | 月曜始まりの月表示、閏日と年境界、日付・時刻・ID・件数の検証、予定の並び順 |
| JSONバックアップ | 旧形式からの既定値、予定・時間調整・任意順の進捗の往復、不正な実績・進行状態・参照の拒否 |
| リマインド | 毎週と日付指定の最早候補、時刻省略時の設定値、過去予定、端末のローカル日付と夏時間 |
| Android Lint・APK | **成功。Lintエラー0件・警告7件**。APK署名・16KBアラインメントも確認 |
| Android計測テスト | Compose UI 4件＋Repository 6件の計10件。Kotlinコンパイル成功、端末実行は未実施 |
| 実機での更新・操作 | 今回は未実施。端末未接続のため、APKを提供する方針 |

ユニットテストは、Androidの実端末での動作を検証するものではありません。端末が必要な確認は以下の手順で実施できます。

接続端末または起動済みエミュレーターを用意した後、計測テストを実行します。

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

**普段使っているスマホで実行する前に、アプリからJSONバックアップを書き出してください。** Android Gradle Pluginは標準では計測テストの後にアプリをアンインストールし、そのとき端末内の記録も消えます。このプロジェクトでは`gradle.properties`の`android.injected.androidTest.leaveApksInstalledAfterRun=true`でアンインストールを止めていますが、テスト後はdebug版が入った状態になります。終わったら`install-galaxy.ps1`でrelease版を入れ直してください。テスト自体は一時的なデータベースと設定ファイルを使い、アプリの記録には触れません。エミュレーターで実行するのが最も安全です。

### 手動QAの手順と確認点

テスト用に「時間制5秒×2セット・休憩5秒」と「左右の回数制2セット」のメニューを作ると短時間で確認できます。破壊的な操作や端末再起動の前に、必要な記録をJSONへ保存してください。

| ケース | 手順 | 確認点 |
| --- | --- | --- |
| 開始導線 | 起動直後のホームから「トレーニングを開始」を押し、好きな種目をタップ | 最初の画面から開始操作に進める。終了済み画面が勝手に再表示されない |
| 通常操作・任意順 | プランク→リュックローなど順番を変え、途中で元の種目に戻る。左右種目も行う | 種目ごとのセット・左右の進捗が維持され、同じセットが重複しない。回数・負荷の追加入力が不要 |
| 残り時間の調整 | 運動・休憩・準備中と一時停止中に「＋10秒」「−10秒」を押す | 残り時間に即反映される。最低1秒を保ち、調整後の実施時間が記録・JSON往復でも維持される |
| カレンダー | 月を移動し、同じ日に2件の予定を追加。時刻・メニュー・日付を編集し、1件を削除 | 選択日・今日・予定・実施済みを識別でき、再起動後も予定が残る。予定からメニューを開始できる |
| 連打 | セット完了・開始・スキップを素早く連続タップ | 同じセットの実績が重複せず、意図せず次のセットまで進まない |
| 回転・文字拡大 | 計測中に端末の自動回転を有効にして縦横へ回す。OSの文字サイズを大きくする | 同じセット・残り時間へ再接続し、二重進行しない。主要操作へスクロールで到達できる |
| 別アプリ | 時間制計測中にホームへ戻り、別アプリを開く | 期限に沿って進行し、許可時は通知へ現在状態が出る。アプリ復帰で状態が一致 |
| 画面消灯 | 時間制／休憩中に電源ボタンで消灯し、数回の切替後に戻る | 状態・合図を維持できる範囲を端末上で確認。遅延があっても表示更新回数による累積誤差がない |
| 通知拒否 | Android 13以降で通知を有効にする操作に対して拒否し、タイマーを開始 | 画面上の計時、編集、履歴が使える。通知欄の表示はOSの拒否時仕様に従う |
| 音声なし | 日本語オフライン音声を利用できない状態／機内モードで音声ON、効果音・振動ONにする | 音声がなくても画面・効果音・振動で区切りが分かり、エラーで止まらない |
| 一時停止 | 時間制の途中と休憩中に停止し、30秒後に再開 | 一時停止中の時間を加算しない。残り時間を維持し、待機中・停止中にWakeLockを保持しない |
| 途中終了・短縮 | 種目・セットを減らした短縮版を完了。別の回では1セット行って途中終了 | 短縮版、通常版、中断を区別。実績がある日は1日、ゼロ／全スキップは0日として数える |
| JSON | 書き出し→プレビュー→取り込み。さらにJSONの回数を負数へ変更して取り込み | 正常データは復元される。不正データは日本語エラーとなり、元の記録・設定を壊さない |
| TalkBack | OSのTalkBackを有効にして画面・主要ボタンを移動 | 操作名と状態が分かり、残り秒数の自動読み上げが毎秒割り込まない |
| リマインドと通知許可 | 通知を許可していない状態で、設定の「習慣リマインド」をONにする。許可・拒否の両方を試し、保存する | 許可の確認が出る。拒否時は届かない理由と「端末の通知設定を開く」が表示され、ONにしたリマインドの切り替えが元に戻らない。許可後は予定日に通知が届く |
| 回数の任意記録 | 回数制のセット中に「＋ 回数も記録する（任意）」→±→「セット完了」。別のセットは回数なしで完了 | 回数を入れたセットだけ回数が記録され、入れないセットは「完了」と表示される。2回目以降は前回の回数から始まる |
| 記録の削除・修正 | 記録画面で「回数を入力」「この記録を削除」を使う。進行中の記録も開く | 回数の修正が前回比較に反映される。削除は確認後だけ実行され、進行中の記録は削除・修正できない |
| マナーモードとイヤホン | マナーモードで、イヤホンなし／ありで合図を鳴らす。「マナーモード中も音を鳴らす」もONにする | イヤホンなしは振動のみ、イヤホンありと設定ONでは効果音・音声も出る |
| 画面オフの合図 | 休憩中に画面を消し、休憩終了を待つ | 「準備ができたら開始してください」の音声が最後まで再生される |

プロセス終了の確認はdebugビルドで行います（`run-as`はrelease版では使えないため、`install-galaxy.ps1 -UseDebugBuild`で入れます）。計測中に5秒以上待ち、現在のセット・残り時間を控えた後、PowerShellからアプリ自身のプロセスだけを終了します。

```powershell
$taskAdb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
$taskAppPid = (& $taskAdb shell pidof jp.tsuzutore.app).Trim()
if ($taskAppPid) { & $taskAdb shell run-as jp.tsuzutore.app kill -9 $taskAppPid }
& $taskAdb shell am start -n jp.tsuzutore.app/.MainActivity
```

再起動後は保存チェックポイントから一時停止で開き、失われた時間をセット完了にせず、再開まで合図が鳴らないことを確認します。`run-as`が利用できない環境ではAndroid Studioのプロセス停止でも確認できます。`adb shell am force-stop jp.tsuzutore.app`は「強制停止」の別ケースであり、OSがサービス・通知を止めるため、自動復活を期待しません。手動起動後の一時停止復元を確認してください。

端末再起動も別に確認します。計測中の状態を保存した後に`adb reboot`または端末の再起動を行い、ロック解除後にアプリを手動起動します。運動が自動再開せず、実績が増えないこと、有効なリマインドだけが再設定されることを確認します。

Dozeの確認では、テスト端末を使用して画面を消灯し、以下を実行します。

```powershell
& $taskAdb shell dumpsys battery unplug
& $taskAdb shell dumpsys deviceidle force-idle
```

タイマーと予定通知の挙動を観察し、復帰後に保存状態・経過時間・合図の遅延の有無を記録してください。通知は目安時刻のため、秒単位の到達を合格条件にしません。テスト後は必ず解除します。

```powershell
& $taskAdb shell dumpsys deviceidle unforce
& $taskAdb shell dumpsys battery reset
```

Galaxyの省電力ON／OFF、アプリのバックグラウンド制限、通知許可、サイレント、音声データの有無を記録すると、端末固有の差を追跡できます。これらの未実施項目を「確認済み」と扱わないでください。

## 参照した公式資料

- [Android SystemClock](https://developer.android.com/reference/android/os/SystemClock)
- [Foreground Serviceの種別とspecialUse](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Android通知権限](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [WakeLock](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock)
- [DozeとApp Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files)
- [Android StudioのSDK管理](https://developer.android.com/studio/intro/update#sdk-manager)
- [adb](https://developer.android.com/tools/adb)
- [Android Gradle Plugin 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom)
- [Material Design 3](https://m3.material.io/)
- [Material Design 3の色の仕組み（ダークテーマを含む）](https://m3.material.io/styles/color/system/overview)
- [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [material-color-utilities](https://github.com/material-foundation/material-color-utilities)
- [Material Symbols](https://github.com/google/material-design-icons)
- [Anton（Google Fonts）](https://fonts.google.com/specimen/Anton)と[SIL Open Font License 1.1](https://openfontlicense.org/)
- [WCAG 2.1 コントラスト（最低限）](https://www.w3.org/TR/WCAG21/#contrast-minimum)
- [デジタル庁デザインシステム](https://design.digital.go.jp/dads/)
- [アプリ専用デザインシステムの定義方針](https://design.digital.go.jp/dads/guidance/local-design-system/)

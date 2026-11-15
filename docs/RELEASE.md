# APKリリース手順

GitHub Releasesに、同一の署名鍵で署名したAPKを配置する手順。

## 1. 署名鍵を作成する（初回のみ）

開発マシンのターミナルで次を実行する。

    keytool -genkeypair -v -keystore meshi-release.jks -alias meshi -keyalg RSA -keysize 3072 -validity 10000

作成したキーストアとパスワードは安全な場所でバックアップする。既存のAndroidアプリを上書き更新するときは同じ署名鍵を使う。

## 2. GitHub Environment Secretsを設定する

Settings → Environments → New environment で release 環境を作り、以下を登録する。

| Secret | 値 |
| --- | --- |
| MESHI_RELEASE_KEYSTORE_BASE64 | キーストアのBase64エンコード文字列 |
| MESHI_RELEASE_STORE_PASSWORD | キーストアパスワード |
| MESHI_RELEASE_KEY_ALIAS | 鍵のエイリアス（例：meshi） |
| MESHI_RELEASE_KEY_PASSWORD | 鍵のパスワード |

Linuxでは base64 -w0 meshi-release.jks を利用できる。macOSでは base64 < meshi-release.jks | tr -d '\n' を使う。

Base64文字列も秘密情報として保管する。Environmentの承認ルールは必要に応じて設定する。

## 3. バージョンとタグ

app/build.gradle.kts の versionName と versionCode を更新してコミットする。

    git tag v0.1.0
    git push origin v0.1.0

versionName はタグから v を除いた値に一致させる。versionCode はリリースのたびに増加させる。

タグをpushすると Android Release ワークフローが実行され、署名済みAPK、署名検証、権限チェック、SHA-256チェックサムを作成する。

- meshi-vX.Y.Z.apk
- SHA256SUMS

## 4. インストール・更新

AndroidのブラウザからGitHub ReleasesのAPKを取得してインストールする。初回のみAndroidのインストール許可が必要になる場合がある。

同じapplicationId・署名鍵でversionCodeを上げれば、データを保持した上書き更新ができる。端末での更新テストは必須。

## 現状

ワークフローは設定済み。署名鍵とEnvironment Secretsは未設定。暗号化バックアップ・アプリロックも未実装のため、正式なリリースはまだ作成していない。

# APKの更新とデータ引き継ぎ

`meshi` は `dev.hokutaka.meshi` というアプリIDで配布する。**同じアプリID・同じ署名証明書・より大きいversionCode**のAPKなら、アンインストールせず上書き更新できる。アプリ内のRoomデータとAndroid Keystoreの鍵もそのまま残る。

## 現在インストールしているDebug APKからの移行

以前のGitHub Actions成果物 `app-debug.apk` は、実行ごとに新しいDebug署名鍵でビルドされていた可能性が高い。署名証明書が異なるAPKはそのまま上書きできない。

**データを残している端末での移行手順**

1. 現在のアプリで「設定」→「バックアップ」を選び、12文字以上のパスフレーズを設定して、端末外にもバックアップファイルを保管する
2. 保存したファイルを、同じアプリの「復元」でいったん読み戻せることをテストする場合、現在のデータが置換されることに注意する。バックアップのファイルサイズ・保管先・パスフレーズも確認する
3. 本番用の**固定署名鍵**で作ったAPKの配布を開始する
4. 署名が異なる場合に限り、バックアップを確認した後で旧Debugアプリをアンインストールする
5. 本番用APKをインストールし、「設定」→「復元」で暗号化バックアップを読み戻す
6. 履歴・月次集計・カテゴリーを確認する

**重要:** アンインストールで消えた記録は、既にエクスポートしたバックアップがなければ、通常は復元できない。KeystoreのDB鍵もアンインストール時に失われるため、アプリの暗号化DBだけのコピーでは新しいインストールへ移せない。

一度固定署名鍵版へ移行できれば、次のリリースからは上書き更新だけでよい。

## 1. 固定署名鍵を作成する（最初の1回）

開発マシンのターミナルで次を実行。

```sh
keytool -genkeypair -v \
  -keystore meshi-release.jks \
  -alias meshi \
  -keyalg RSA -keysize 3072 -validity 10000
```

キーストアとそのパスワードは、リポジトリの外で安全にバックアップする。**次回以降も同じ鍵を利用する。** 鍵を紛失すると既存アプリを上書き更新できなくなる。

## 2. GitHubのリリース環境に秘密情報を登録する

リポジトリの **Settings → Environments** で `release` 環境を作成し、Environment secretsを登録する。

| Secret名 | 内容 |
| --- | --- |
| `MESHI_RELEASE_KEYSTORE_BASE64` | `.jks` ファイルをBase64にした値 |
| `MESHI_RELEASE_STORE_PASSWORD` | キーストアのパスワード |
| `MESHI_RELEASE_KEY_ALIAS` | 鍵のエイリアス（例: `meshi`） |
| `MESHI_RELEASE_KEY_PASSWORD` | 鍵のパスワード |

```sh
# Linux
base64 -w0 meshi-release.jks

# macOS
base64 < meshi-release.jks | tr -d '\n'
```

Base64は暗号化ではないので、生成した文字列も秘密として扱う。Environment承認ルールも推奨。

## 3. リリースを作る

```sh
git tag v0.1.0
git push origin v0.1.0
```

`vX.Y.Z` 形式のタグをpushすると `.github/workflows/release.yml` が走る。署名済みAPKとSHA256SUMSをGitHub Releasesへ発行する。

- `versionName` はタグ名から自動設定
- `versionCode` はワークフロー実行番号から自動生成。次のリリースではさらに大きい番号を使用する
- 署名に使用するキーストアは全リリースで共通

`versionCode` の重複や逆転があると更新できない。ワークフローを別名・別リポジトリに移行するときや、古いタグを再実行するときは番号を確認する。

## 4. 更新の仕方

1. スマホにインストール済みの固定署名鍵版 `meshi` を残したまま、GitHub Releasesから新しいAPKをダウンロード
2. APKを開いて「更新」を選択（**アンインストールはしない**）
3. 起動後、取引履歴・カテゴリー・月次集計を確認

更新前に暗号化バックアップを取るとさらに安全。Androidが署名不一致・versionCode不足などで更新を拒否したら、アンインストールせずにAPKの署名とversionCodeを確認する。

## 現状

ワークフローは用意済みで、GitHub Secretsへの固定署名鍵登録はまだ必要。実機での「固定署名APK → 次の固定署名APK」更新テストも未実施。したがって現時点でデータ保持を実機保証する段階ではない。

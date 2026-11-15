# meshi

Androidネイティブの個人用家計簿アプリ。

## 実装済み（開発版）

- 支出・収入のクイック入力（金額・カテゴリーで保存、日付とメモは任意で展開）
- 取引履歴の閲覧・編集・削除
- カテゴリーの追加・名前変更・並び替え・未使用カテゴリーの削除
- 月別の支出・収入・差引の集計
- Roomへの取引保存
- SQLCipherによるDB暗号化とAndroid Keystoreを用いた鍵保護
- GitHub ActionsによるAndroidビルド・単体テスト・権限チェック
- パスフレーズで暗号化したバックアップファイルの保存と復元（実機動作は未検証）

## 実装予定

- 購入明細・商品マスタ・入力テンプレート
- 生体認証・端末認証によるアプリロック（優先度低）
- 署名済みリリースAPKのGitHub Releases配布

> **注意:** バックアップ・復元コードは追加済みですが、実機での往復検証およびアップデート時のデータ保持テストは未完了です。大切な実データを長期保存する用途にはまだ使用しないでください。

画面下の「記録」「設定」タブで切り替えます。カテゴリー管理と暗号化バックアップは「設定」タブから操作できます。

## 技術スタック

| 項目 | 技術 |
| --- | --- |
| プラットフォーム | Android（minSdk 26） |
| UI | Kotlin / Jetpack Compose / Material 3 |
| 状態管理 | ViewModel / StateFlow |
| 永続化 | Room (SQLite) |
| DB暗号化 | SQLCipher for Android |
| 鍵保護 | Android Keystore |
| テスト・ビルド | Gradle / GitHub Actions |
| 配布方針 | GitHub Releases（署名済みAPK） |

## 開発・ビルド

Android SDK 36 とJDK 17を使用します。Android Studioで開き、Gradle同期後に `:app:assembleDebug` を実行します。CIではGradle 8.13を使用しています。

ビルド検証状況は [Android CI](https://github.com/Hokutaka/meshi/actions/workflows/android.yml) で確認できます。CIのDebug APKは毎回異なる鍵で署名されることがあり、上書き更新に適さないため成果物として配布しません。

更新用APKは **[Android Release](https://github.com/Hokutaka/meshi/actions/workflows/release.yml)** で固定署名鍵を使用してビルドします。初回だけ署名鍵・GitHub Secretsの設定が必要で、現時点では未設定です。既存のDebugアプリから移行するときは **先に暗号化バックアップを作成** してください。詳しくは [APKの更新とデータ引き継ぎ](docs/RELEASE.md) を参照。

## 設計ドキュメント

- [要件定義](docs/REQUIREMENTS.md)
- [アーキテクチャ](docs/ARCHITECTURE.md)
- [APKリリース手順](docs/RELEASE.md)

## ライセンス

未決定。

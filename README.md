# meshi

Androidネイティブの個人用家計簿アプリ。

## 実装済み（開発版）

- 支出・収入の登録（円・日付・カテゴリー・メモ）
- 取引履歴の閲覧・編集・削除
- 月別の支出・収入・差引の集計
- Roomへの取引保存
- SQLCipherによるDB暗号化とAndroid Keystoreを用いた鍵保護
- GitHub ActionsによるAndroidビルド・単体テスト・権限チェック（CIの動作確認中）

## 実装予定

- カテゴリー管理
- 購入明細・商品マスタ・入力テンプレート
- 生体認証・端末認証によるアプリロック
- パスフレーズ付き暗号化バックアップ・復元
- 署名済みリリースAPKのGitHub Releases配布

> **注意:** 暗号化バックアップと復元は未実装です。現段階では大切な実データの長期運用には使用しないでください。デバイス故障やアプリ削除で記録を失う可能性があります。

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

APKのビルド検証状況は [Android CI](https://github.com/Hokutaka/meshi/actions/workflows/android.yml) で確認できます。成功したCIではdebug APKがArtifactとして添付されます。正式な署名済みリリースではありません。

## 設計ドキュメント

- [要件定義](docs/REQUIREMENTS.md)
- [アーキテクチャ](docs/ARCHITECTURE.md)

## ライセンス

未決定。

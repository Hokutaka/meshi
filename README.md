# meshi

個人向け Android 家計簿アプリ。

## 機能

- 支出・収入の記録、編集、履歴表示
- 月別・カテゴリー別の集計
- 合計金額によるクイック入力
- 任意の購入明細、商品マスタ、入力テンプレート
- ローカルデータ保存、アプリロック、暗号化バックアップ

## 技術スタック

| 用途 | 技術 |
| --- | --- |
| Android UI | Kotlin / Jetpack Compose |
| 画面状態 | ViewModel / StateFlow |
| DB | Room (SQLite) |
| DB暗号化 | SQLCipher（Roomとの統合を検証予定） |
| 鍵管理 | Android Keystore |
| 認証 | BiometricPrompt |
| 設定 | DataStore |
| 配布 | GitHub Actions / GitHub Releases（署名済みAPK） |

## 設計

- [要件定義](docs/REQUIREMENTS.md)
- [アーキテクチャ](docs/ARCHITECTURE.md)

## 開発状況

設計中。次にAndroidアプリの雛形と暗号化DBの技術検証を行う。

## ライセンス

検討中。

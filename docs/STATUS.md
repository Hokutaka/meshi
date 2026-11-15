# 開発状況

## 実装済み（v0.1.0）

- 支出・収入のクイック入力（金額・カテゴリーを選んで保存。日付とメモは必要なときに入力）
- 取引履歴の閲覧・編集・削除
- カテゴリーの追加・名前変更・並び替え・未使用カテゴリーの削除
- 月別の支出・収入・差引の集計
- Roomへの保存、SQLCipherによるDB暗号化、Android Keystoreによる鍵保護
- パスフレーズで暗号化したバックアップの書き出しと復元
- 「記録」「設定」の2タブ構成
- GitHub Actionsでの単体テスト・APKビルド・権限チェック
- 固定署名鍵によるリリースAPKのビルド・署名検証とGitHub Releasesへの公開
- v0.1.0バックアップの互換性テストとRoom DB v1スキーマの固定（CIで差分検出）

初回リリース：**[v0.1.0](https://github.com/Hokutaka/meshi/releases/tag/v0.1.0)**

## 今後の実装

- カテゴリー別の集計表示
- 購入明細・商品マスタ・入力テンプレート
- DBバージョン2以降のマイグレーション実装と実機・計装テスト
- 生体認証・端末認証によるアプリロック（任意、優先度低）

## 検証待ち

- バックアップと復元の実機での往復確認
- 同じ署名鍵によるAPK上書き更新後のデータ保持確認
- 古いDB・バックアップ形式からの引き継ぎテスト

バックアップ機能は実装済みですが、実機での復元確認はまだ完了していません。データを保持したい場合は、バックアップファイルとパスフレーズを安全に保管してください。

## 技術・ビルド

- Android（minSdk 26）、Kotlin、Jetpack Compose / Material 3
- ViewModel / StateFlow、Room / SQLCipher、Android Keystore
- Android SDK 36、JDK 17、Gradle 8.13
- Android StudioでGradle同期後、`:app:assembleDebug` で開発用APKをビルド
- [Android CI](https://github.com/Hokutaka/meshi/actions/workflows/android.yml) で単体テスト・ビルド・権限検査
- [Android Release](https://github.com/Hokutaka/meshi/actions/workflows/release.yml) で固定署名鍵のAPKを配布

更新と旧Debug版からのデータ移行は [APKの更新とデータ引き継ぎ](RELEASE.md) を参照。

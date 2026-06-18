# 生成されるJSONレポートの形式について

Intent Surface Explorer は、診断結果を以下の構造を持つJSONファイルとして出力します。この形式はバージョン 5 のスキーマを採用しています。

## ルート構造 (IntentSurfaceReport)

レポートファイルのルートオブジェクトには、以下のフィールドが含まれます。

| フィールド名 | 型 | 説明 |
| :--- | :--- | :--- |
| `schema` | Int | スキーマバージョン (現在は 5) |
| `schema_version` | Int | スキーマバージョン (現在は 5) |
| `schema_id` | String | スキーマID ("work.moukaeritai.intent-surface-report.schema.v5") |
| `report_kind` | String | レポートの種類 ("moukaeritai_intent_surface") |
| `run_id` | String | 診断実行のユニークID |
| `file_name` | String | 生成されたファイル名 |
| `generated_at_epoch_millis`| Long | 診断実行時のタイムスタンプ (ミリ秒) |
| `app` | Object | アプリおよびビルド情報 (AppInfo) |
| `intent_invocation_catalog` | Object | 外部ランチャーアプリへ引き渡すことを想定した、再構築可能なIntent仕様のリスト (後述) |
| `summary` | Object | 診断全体の統計情報 |
| `intent_surface_probes` | List | 各インテントプローブの診断結果リスト |
| `component_surface_summary` | List | 各コンポーネントごとの詳細な診断結果リスト |

---

## Intent Invocation Catalog (重要)
Schema 5 より導入された `intent_invocation_catalog` は、サードパーティのランチャー機能等でそのまま使用できる再構築可能な Intent レシピを提供します。

### カタログ candidate (_IntentInvocationCandidate_)
- `candidate_id`: `cand.<probe_id>.<sanitized_pkg>.<sanitized_act>` という決定的ID。
- `target`: パッケージ名および ComponentName 等。
- `intent_recipe`: Intent 再構築のための詳細なレシピ情報（Action, URI, MIME type 等）。
- `evidence`: 暗黙的解決、静的解決などのテスト結果。
- `safety`: このタスクでは `start_activity_attempted = false` であり、実際に起動テストを行ったかどうか等の安全情報。

---

## 明示的な評価モードに関する用語
レポート内では、インテントの解決と評価について以下の用語を使用し、それぞれを明確に区別しています。

- **IMPLICIT_RESOLUTION:** `PackageManager` によるベースインテントの暗黙的解決。
- **PACKAGE_TARGETED_RESOLUTION:** ベースインテントに `setPackage(packageName)` を指定して行われた解決。
- **COMPONENT_EXPLICIT_STATIC_ASSESSMENT:** ターゲットの `ComponentName` が特定され、静的な `ActivityInfo` に基づいて評価された状態。この状態（`EXPLICIT_COMPONENT_STATIC_OK`）は `startActivity` が成功することを証明するものではありません。
- **COMPONENT_EXPLICIT_LAUNCH_RESULT:** 将来の自動・手動起動テストのために予約されたステータスであり、現在は一律 `START_ACTIVITY_NOT_TESTED` となります。

---

## JSON Validation
スキーマ 5 からは、アプリ内で Kotlin による厳密な Semantic Validation が実行されます。整合性エラーが検出された場合、内部保存はスキップされずとも、`ExportState` 上でエラーとしてカウントされ外部公開する前に気づくことが可能になります。JSON Schema 形式 (`docs/schemas/`) の定義も同梱されています。

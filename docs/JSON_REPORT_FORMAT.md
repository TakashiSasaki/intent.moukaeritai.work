# 生成されるJSONレポートの形式について

Intent Surface Explorer は、診断結果を以下の構造を持つJSONファイルとして出力します。この形式はバージョン 5 のスキーマを採用しています。

## ルート構造 (IntentSurfaceReport)

レポートファイルのルートオブジェクトには、以下のフィールドが含まれます。

| フィールド名 | 型 | 説明 |
| :--- | :--- | :--- |
| `schema` | Int | レガシーなスキーマバージョン (現在は 5)。下位互換性のために保持されています。 |
| `schema_version` | Int | キャノニカル（正規の）整数スキーマバージョン (現在は 5)。 |
| `schema_id` | String | スキーマのユニークなキャノニカルID ("urn:uuid:8a69ce28-18d7-4720-b78f-1ab11cc52233") |
| `run_id` | String | 診断実行のユニークID |
| `file_name` | String | 生成されたファイル名 |
| `generated_at_epoch_millis`| Long | 診断実行時のタイムスタンプ (ミリ秒) |
| `app` | Object | アプリおよびビルド情報 (AppInfo) |
| `intent_invocation_catalog` | Object | 外部ランチャーアプリへ引き渡すことを想定した、再構築可能なIntent仕様のリスト (後述) |
| `summary` | Object | 診断全体の統計情報 |
| `intent_surface_probes` | List | 各インテントプローブの診断結果リスト |
| `component_surface_summary` | List | 各コンポーネントごとの詳細な診断結果リスト |

*注意：スキーマ自体およびスキーマファイルの作成日/変更日は Git 履歴を正当なソース・オブ・トゥルース（信頼できる唯一の情報源）とするため、JSON スキーマファイル内には作成日時に関するメタデータは含みません。*

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

## JSON Validation & CLI Schema Check

スキーマ 5 からは、アプリ内で Kotlin による厳密な Semantic Validation が実行されます。整合性エラーが検出された場合、外部への出力が制限され品質が保たれます。また、JSON Schema 形式 (`docs/schemas/`) の整合性を検証するための定義ファイルも同梱されています。

### コマンドラインでの検証方法 (Node.js & ajv-cli)

出力された JSON レポートがスキーマ定義に 100% 準拠しているかをローカル環境で検証するには、以下の npm コマンドを使用してください。

1. **AJV CLI ツールを使用して検証を実行する場合:**
   ```bash
   npx ajv-cli validate \
     --spec=draft2020 \
     -s docs/schemas/android-intent-surface-report.schema.v5.json \
     -r docs/schemas/intent-invocation-catalog.schema.v1.json \
     -d path/to/your_report.json
   ```
   *(`-r` フラグで参照先のカタログスキーマを同時に読み込み、`--spec=draft2020` で Draft 2020-12 仕様を指定します。レポートが正しい場合は `YOUR_REPORT_FILE.json valid` と出力されます)*

---

## カタログ構造の安全対策と `runtime_requirements`

外部インフラでの誤動作を防ぐため、`intent_recipe` は以下の安全基準を満たします：

- **`data.set_api`**: インテント生成時の API 呼び出し方法を明示します。
  - `setData`: 直接指定可能な、不変で安全な URI（例: `https://...`）が存在する場合。
  - `setType`: MIME タイプのみを適用し、URI は指定しない場合。
  - `setDataAndType`: 安全な不変 URI と MIME タイプを両方適用する場合。
  - `runtimeProvidedData` または `runtimeProvidedDataAndType`: 検出された URI がプレースホルダー（例： `content://com.example.fileprovider/file` 等）や一時的・コンテキスト依存（`content://`）のものである場合。この場合不完全な URI は埋め込まず、代わりに `runtime_requirements` が追加されます。
  - `none`: データ指定が不要な場合。

- **`runtime_requirements` (Runtime Requirements)**:
  `data.set_api` を安全に処理するために、起動側のアクションにおいて実行時に要求されるリソースを定義します。
  - `requirement_type`: `CALLER_SUPPLIED_URI` / `CALLER_SUPPLIED_TEXT` / `CALLER_SUPPLIED_EXTRA` / `CALLER_SUPPLIED_CLIP_DATA` / `GENERATED_TEMP_CONTENT_URI` / `USER_SELECTED_CONTENT_URI` / `UNKNOWN_RUNTIME_VALUE`
  - `expected_value_type`: `URI_STRING` / `STRING` / `STRING_ARRAY` / `BOOLEAN` / `INT` / `LONG` / `FLOAT` / `CLIP_DATA` / `NONE` / `UNKNOWN`
  - `required`: 起動に不可欠かどうか（`true` / `false`）

---

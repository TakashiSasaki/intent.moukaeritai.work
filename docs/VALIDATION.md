# Intent Surface Report Validation Specification

This document details the layered validation strategy designed to ensure the integrity, safety, and compliance of the exported Android Intent Surface Reports.

---

## 1. Structural vs. Semantic Validation

To ensure generated reports are robust and safe, validation is handled in two distinct layers:

### Layer A: Structural JSON Schema Validation (Ajv)
- **Role**: Validates general files structure, object property nesting, required/optional fields, types, and enumeration values.
- **Scope**:
  - Main schema limits the structure of the overall report (`android-intent-surface-report.schema.v5.json`).
  - Referenced catalog schema defines candidates (`intent-invocation-catalog.schema.v1.json`).
- **Tooling**: This is developer/CI tooling and is **not** an Android runtime dependency of the final application.

### Layer B: Kotlin Semantic Validation (`IntentSurfaceReportSemanticValidator`)
- **Role**: Validates complex, Android-specific cross-field business logic and anti-launch safety invariants.
- **Scope**:
  - Enforces that no fake URI or redacted placeholder string (e.g., `"content://example"`) is used in actionable recipes.
  - Verifies that `start_activity_attempted` is strictly `false` and that the app is never turned into an active launcher.
  - Verifies that `auto_launch_allowed` is strictly `false` and `requires_user_confirmation` is `true`.
  - Ensures a `runtimeProvidedData` declaration is accompanied by at least one matched URI or type requirement.
  - Verifies that the candidate count matches the exact list size.

---

## 2. Dev & CI Verification Flow (Ajv CLI)

To check structural validity using the Ajv CLI under JSON Schema Draft 2020-12, execute:

```bash
npx ajv-cli validate \
  --spec=draft2020 \
  -s docs/schemas/android-intent-surface-report.schema.v5.json \
  -r docs/schemas/intent-invocation-catalog.schema.v1.json \
  -d docs/fixtures/android-intent-surface-report.v5.minimal.valid.json
```

### Date-Time Format Handling
The main schema uses `format: "date-time"` for the `generated_at_utc` field. If strict format validation is enabled in `ajv-cli` (without `--strict=false`), you must install and require `ajv-formats` or run validation with strict mode relaxed to ignore missing formats. Note that this validation is for developer tooling and CI workflows; it introduces no Android runtime dependencies.

---

## 3. Schema Identity & Metadata Contracts

The contracts enforce strict rules around schema identification, naming, and version tracking:

- **Main Schema File**: `docs/schemas/android-intent-surface-report.schema.v5.json`
- **Catalog Schema File**: `docs/schemas/intent-invocation-catalog.schema.v1.json`
- **Main Schema ID**: `urn:uuid:8a69ce28-18d7-4720-b78f-1ab11cc52233`
- **Catalog Schema ID**: `urn:uuid:6df4c0a0-10a1-4575-9952-4d9c4bb72c8f`

### Absent Deprecated Fields (Strictly Removed)
To reduce payload bloat and maintain a clean contract, we guarantee that the following legacy structures are **removed** and **not present** anywhere in the exported report or schemas:
- `report_kind`
- `catalog_kind`
- `schema_semver`
- `schema_family_id` (specifically in generated reports)

### Dynamic Timestamps & History Tracking
- **Schema Lifecycle**: Schema creation, modification, and lifecycle dates are managed and tracked purely through the git history as the definitive source of truth, rather than embedding hardcoded timestamps directly in the JSON Schema files.
- **Instance Metadata**: Generated report timestamps (`generated_at_epoch_millis` and `generated_at_utc`) represent instance-specific run execution metadata and must remain intact to order reports correctly.

---

## 4. Fixture and Security Guidelines

- **Sensitive Data Prohibited**: Real generated reports from real user devices contain sensitive information (package list inventory, personalized apps) and **must never** be checked in or committed to Git.
- **Synthetic Fixtures**: Use fake, sanitized mock fixtures under `docs/fixtures/` for test specifications. Packages should use dummy prefixes like `com.example.viewer`.


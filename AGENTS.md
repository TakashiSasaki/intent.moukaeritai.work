# Developer Instructions

- Refer to `docs/DIAGNOSTICS_MEMO.md` for diagnostic workflows.
- Refer to `docs/APP_SPECIFICATION.md` for functional specifications.
- Refer to `docs/JSON_REPORT_FORMAT.md` for technical details on the generated JSON reports.

## Versioning Rules

- The single source of truth for the app version is `version.properties` at the project root.
- **CRITICAL**: If you make *any* changes to the codebase, you MUST increment `VERSION_CODE` before building the app. You can do this by running `gradle bumpVersionCode`. This applies equally to all builds, even automated ones.
- `VERSION_NAME` should be manually updated according to feature releases (e.g., `0.5.0`).

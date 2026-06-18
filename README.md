# Intent Surface Explorer

A non-destructive Intent surface discovery and export tool for Android.

## Overview
This app discovers which activities in other apps are visible and potentially callable through Intents. It builds a diagnostic report representing this "surface" and exports it as an exchangeable, validator-checkable JSON document.

## Schema Versioning
- **Current JSON Schema**: V5 (`intent-surface-report.schema.v5`)
- **JSON Validation**: The app enforces strict Kotlin semantic validation prior to generating exports, checking Android-specific consistency constraints.
- **Consumer Artifacts**: The generated report features the `intent_invocation_catalog`, which is the preferred consumer-facing section for launcher-like apps.

## Safety & Non-Destructive Behavior
- The app discovers intents using PackageManager queries (`resolveActivity`, `queryIntentActivities`), explicit checks on component logic, and targeted package assessment. 
- The app **does not execute `startActivity()`**.
- Within reports, findings indicate explicit static checks like `EXPLICIT_COMPONENT_STATIC_OK` rather than confirmed successful launches.
- Status values explicitly flag elements using `START_ACTIVITY_NOT_TESTED`.

## Privacy Warning
Generated reports contain comprehensive app inventories specific to the user's device and should be treated as sensitive diagnostic artifacts. Real generated JSON files must **never be committed to source control**. The app disables `allowBackup` to ensure device intent reports are not backed up.

## Development
- Uses Kotlin and Jetpack Compose.
- **DO NOT** commit real device reports as fixtures.
- To test the semantic validator, generate mock reports inside test resources `app/src/test/resources/fixtures/`.

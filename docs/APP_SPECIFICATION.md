# Application Specification: Intent Surface Explorer

## Overview
The Intent Surface Explorer is an Android application designed to discover, audit, and export information about how intents are handled by installed applications on a device. It provides diagnostic insights into how different intent configurations (actions, categories, data and flags) trigger activity resolution.

## Core Features
1. **Intent Probing**: Automatically runs a suite of probes using various `Intent` configurations (Action `VIEW`, `SEND`, `SENDTO`, `GET_CONTENT`, etc.). This is purely non-destructive discovery and does not automatically launch target applications.
2. **Analysis and Categorization**: Captures and categorizes candidates based on their resolution behavior (direct, resolver, etc.).
3. **Data Export**: Generates a standardized JSON report (schema version 5) detailing probe results, candidate information, and the `intent_invocation_catalog`.
   - Reports are semantically validated prior to final output.
   - Reports are automatically saved to internal app storage upon generation before triggering an external export prompt.
   - Users can re-export the last internally saved report without having to re-run the discovery.
4. **Diagnostic Summaries**:
   - Compares result variations between different flag and category combinations.
   - Identifies candidate components across different families.
   - Provides risk assessment notes for candidate components.

## Internal Terminology
The app differentiates between implicit discovery and explicit intent components. The terminology used in diagnostic reports is clearly distinguished:
- **IMPLICIT_RESOLUTION**: The base Intent resolved by PackageManager.
- **PACKAGE_TARGETED_RESOLUTION**: The base Intent evaluated with `setPackage(packageName)`.
- **COMPONENT_EXPLICIT_STATIC_ASSESSMENT**: Triggers a check on target `ComponentName` based on `ActivityInfo` (static checks only; not actively launched). A status of `EXPLICIT_COMPONENT_STATIC_OK` does not guarantee a successful launch.
- **COMPONENT_EXPLICIT_LAUNCH_RESULT**: Reserved for future use (manual/automated launch testing). The app strictly refrains from making API calls that trigger these currently and returns `START_ACTIVITY_NOT_TESTED`.

## App Versioning
- **Version Code**: Managed in `version.properties`. It is a monotonic internal build/release number. It must be manually bumped (`gradle bumpVersionCode`) for changes.
- **Version Name**: A human-readable app feature version (e.g. `0.6.0`). Managed in `version.properties`.
- **JSON Schema Version**: Currently V5. Exported files embed the schema version and app telemetry.

## Data Structures
- **IntentSurfaceReport**: The root data structure for generated diagnostic reports.
- **IntentInvocationCatalog**: An exchange catalog of safe, explicitly reconstructable intent recipes for components natively consumed by other apps.
- **ProbeFamilySummary**: Statistical summary of probe results by family.
- **CandidateResult**: Detailed information about an activity/component resolved for an intent probe.
- **ComponentSurfaceEntry**: Aggregated summary record for a specific component.
- **ResolvedFilterSummary**: Details regarding `IntentFilter` configuration for resolved activities.

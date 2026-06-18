package packagelist.android.moukaeritai.work

enum class IntentTemplateId {
    VIEW_HTTPS_URL,
    SEND_TEXT_PLAIN
}

enum class TargetingMode {
    IMPLICIT,
    PACKAGE_TARGETED,
    COMPONENT_EXPLICIT
}

enum class LaunchStatus {
    NOT_TESTED,
    STARTED,
    ACTIVITY_NOT_FOUND,
    SECURITY_EXCEPTION,
    OTHER_EXCEPTION
}

data class IntentExtraSpec(
    val key: String,
    val valueKind: String,
    val previewValue: String
)

data class IntentExperimentSpec(
    val templateId: IntentTemplateId,
    val action: String,
    val dataUri: String?,
    val mimeType: String?,
    val categories: List<String>,
    val extras: List<IntentExtraSpec>,
    val targetingMode: TargetingMode,
    val targetPackageName: String?,
    val targetActivityName: String?
)

data class ResolvedActivityCandidate(
    val packageName: String,
    val activityName: String,
    val label: String?,
    val exported: Boolean,
    val enabled: Boolean,
    val permission: String?
)

data class LaunchTestRecord(
    val mode: TargetingMode,
    val targetPackageName: String?,
    val targetActivityName: String?,
    val status: LaunchStatus,
    val errorClass: String?,
    val errorMessage: String?
)

data class IntentExperimentSnapshot(
    val generatedAtEpochMillis: Long,
    val generatedAtIso8601: String,
    val appPackageName: String,
    val appVersionName: String?,
    val androidSdkInt: Int,
    val androidRelease: String,
    val targetSdkVersion: Int,
    val manifestVariant: String,
    val spec: IntentExperimentSpec,
    val candidates: List<ResolvedActivityCandidate>,
    val launchTests: List<LaunchTestRecord>,
    val contentHashShort: String
)

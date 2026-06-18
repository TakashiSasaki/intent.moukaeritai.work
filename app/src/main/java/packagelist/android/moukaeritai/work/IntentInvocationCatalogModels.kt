package packagelist.android.moukaeritai.work

import com.squareup.moshi.JsonClass

object CatalogConstants {
    // Targeting modes
    const val TARGETING_MODE_COMPONENT_EXPLICIT = "COMPONENT_EXPLICIT"
    const val TARGETING_MODE_PACKAGE_TARGETED = "PACKAGE_TARGETED"
    const val TARGETING_MODE_IMPLICIT = "IMPLICIT"

    // Construction APIs
    const val CONSTRUCTION_API_SET_COMPONENT = "setComponent"
    const val CONSTRUCTION_API_SET_PACKAGE = "setPackage"
    const val CONSTRUCTION_API_IMPLICIT = "implicit"

    // Data Set APIs
    const val SET_API_SET_DATA = "setData"
    const val SET_API_SET_TYPE = "setType"
    const val SET_API_SET_DATA_AND_TYPE = "setDataAndType"
    const val SET_API_RUNTIME_PROVIDED_DATA = "runtimeProvidedData"
    const val SET_API_RUNTIME_PROVIDED_DATA_AND_TYPE = "runtimeProvidedDataAndType"
    const val SET_API_NONE = "none"

    // URI Kinds
    const val URI_KIND_NONE = "NONE"
    const val URI_KIND_URL = "URL"
    const val URI_KIND_FILE_PROVIDER = "FILE_PROVIDER_CONTENT_URI"
    const val URI_KIND_TEL = "TEL"
    const val URI_KIND_MAILTO = "MAILTO"
    const val URI_KIND_GEO = "GEO"
    const val URI_KIND_MARKET = "MARKET"
    const val URI_KIND_OTHER = "OTHER"

    // Evidence statuses
    const val EVIDENCE_IMPLICIT_OBSERVED = "IMPLICIT_CANDIDATE_OBSERVED"

    // Package targeted statuses
    const val PKG_RESOLVABLE_DIRECT = "PACKAGE_TARGETED_RESOLVABLE_DIRECT"
    const val PKG_RESOLVABLE_VIA_RESOLVER = "PACKAGE_TARGETED_RESOLVABLE_VIA_RESOLVER"
    const val PKG_NOT_RESOLVABLE = "PACKAGE_TARGETED_NOT_RESOLVABLE"
    const val PKG_RESOLVABLE_OTHER = "PACKAGE_TARGETED_RESOLVABLE_OTHER"

    // Component static statuses
    const val COMP_STATIC_OK = "EXPLICIT_COMPONENT_STATIC_OK"
    const val COMP_DISABLED = "EXPLICIT_COMPONENT_DISABLED"
    const val COMP_NOT_EXPORTED = "EXPLICIT_COMPONENT_NOT_EXPORTED"
    const val COMP_REQUIRES_PERMISSION = "EXPLICIT_COMPONENT_REQUIRES_PERMISSION"
    const val COMP_UNKNOWN = "UNKNOWN"

    // Launch result statuses
    const val LAUNCH_NOT_TESTED = "START_ACTIVITY_NOT_TESTED"

    // Side effect levels
    const val SIDE_EFFECT_UNKNOWN = "UNKNOWN"
    const val SIDE_EFFECT_MAY_OPEN_EXTERNAL = "MAY_OPEN_EXTERNAL_APP"
    const val SIDE_EFFECT_MAY_SHOW_CHOOSER = "MAY_SHOW_CHOOSER"
    const val SIDE_EFFECT_MAY_REQUIRE_INPUT = "MAY_REQUIRE_INPUT"
    const val SIDE_EFFECT_MAY_SEND_SHARE = "MAY_SEND_OR_SHARE_USER_CONTENT"
    const val SIDE_EFFECT_MAY_MODIFY_STATE = "MAY_MODIFY_EXTERNAL_STATE"

    // Extra Value Types
    const val EXTRA_TYPE_STRING = "STRING"
    const val EXTRA_TYPE_STRING_ARRAY = "STRING_ARRAY"
    const val EXTRA_TYPE_BOOLEAN = "BOOLEAN"
    const val EXTRA_TYPE_INT = "INT"
    const val EXTRA_TYPE_LONG = "LONG"
    const val EXTRA_TYPE_FLOAT = "FLOAT"
    const val EXTRA_TYPE_URI_STRING = "URI_STRING"
    const val EXTRA_TYPE_URI_STRING_ARRAY = "URI_STRING_ARRAY"
    const val EXTRA_TYPE_UNKNOWN = "UNKNOWN"

    // Requirement types
    const val REQ_CALLER_SUPPLIED_URI = "CALLER_SUPPLIED_URI"
    const val REQ_CALLER_SUPPLIED_TEXT = "CALLER_SUPPLIED_TEXT"
    const val REQ_CALLER_SUPPLIED_EXTRA = "CALLER_SUPPLIED_EXTRA"
    const val REQ_CALLER_SUPPLIED_CLIP_DATA = "CALLER_SUPPLIED_CLIP_DATA"
    const val REQ_GENERATED_TEMP_URI = "GENERATED_TEMP_CONTENT_URI"
    const val REQ_USER_SELECTED_URI = "USER_SELECTED_CONTENT_URI"
    const val REQ_UNKNOWN_RUNTIME_VALUE = "UNKNOWN_RUNTIME_VALUE"

    // Expected value types
    const val VAL_TYPE_URI_STRING = "URI_STRING"
    const val VAL_TYPE_STRING = "STRING"
    const val VAL_TYPE_STRING_ARRAY = "STRING_ARRAY"
    const val VAL_TYPE_BOOLEAN = "BOOLEAN"
    const val VAL_TYPE_INT = "INT"
    const val VAL_TYPE_LONG = "LONG"
    const val VAL_TYPE_FLOAT = "FLOAT"
    const val VAL_TYPE_CLIP_DATA = "CLIP_DATA"
    const val VAL_TYPE_NONE = "NONE"
    const val VAL_TYPE_UNKNOWN = "UNKNOWN"

    // Flags and grant flags
    const val FLAG_ACTIVITY_NEW_TASK = "FLAG_ACTIVITY_NEW_TASK"
    const val FLAG_ACTIVITY_CLEAR_TOP = "FLAG_ACTIVITY_CLEAR_TOP"
    const val FLAG_ACTIVITY_SINGLE_TOP = "FLAG_ACTIVITY_SINGLE_TOP"
    const val FLAG_GRANT_READ_URI_PERMISSION = "FLAG_GRANT_READ_URI_PERMISSION"
    const val FLAG_GRANT_WRITE_URI_PERMISSION = "FLAG_GRANT_WRITE_URI_PERMISSION"
    const val FLAG_GRANT_PERSISTABLE_URI_PERMISSION = "FLAG_GRANT_PERSISTABLE_URI_PERMISSION"
    const val FLAG_GRANT_PREFIX_URI_PERMISSION = "FLAG_GRANT_PREFIX_URI_PERMISSION"
}

@JsonClass(generateAdapter = true)
data class IntentInvocationCatalog(
    val catalog_schema_version: Int = 1,
    val catalog_kind: String = "moukaeritai.intent_invocation_catalog",
    val candidate_count: Int,
    val candidates: List<IntentInvocationCandidate>
)

@JsonClass(generateAdapter = true)
data class IntentInvocationCandidate(
    val candidate_id: String,
    val source_probe_id: String,
    val source_family: String,
    val display_label: String,
    val target: IntentInvocationTarget,
    val intent_recipe: IntentInvocationRecipe,
    val evidence: IntentInvocationEvidence,
    val safety: IntentInvocationSafety
)

@JsonClass(generateAdapter = true)
data class IntentInvocationTarget(
    val package_name: String,
    val activity_name: String,
    val component_name: String
)

@JsonClass(generateAdapter = true)
data class IntentInvocationRecipe(
    val targeting_mode: String,
    val construction_api: String,
    val action: String?,
    val data: IntentInvocationData,
    val categories: List<String>,
    val extras: List<IntentInvocationExtra>,
    val clip_data: String?,
    val flags: List<String>,
    val grant_flags: List<String>,
    val runtime_requirements: List<IntentRuntimeRequirement> = emptyList()
)

@JsonClass(generateAdapter = true)
data class IntentRuntimeRequirement(
    val requirement_type: String,
    val key: String?,
    val required: Boolean,
    val expected_value_type: String,
    val uri_kind: String?,
    val mime_type: String?,
    val grant_flags: List<String>,
    val description: String
)

@JsonClass(generateAdapter = true)
data class IntentInvocationData(
    val set_api: String,
    val uri: String?,
    val uri_kind: String,
    val scheme: String?,
    val display_redacted: String?,
    val mime_type: String?
)

@JsonClass(generateAdapter = true)
data class IntentInvocationExtra(
    val key: String,
    val value_type: String,
    val value_source: String,
    val required: Boolean,
    val example_value_redacted: String?,
    val notes: List<String>
)

@JsonClass(generateAdapter = true)
data class IntentInvocationEvidence(
    val implicit_resolution_observed: Boolean,
    val implicit_evidence_status: String?,
    val implicit_probe_candidate_index: Int?,
    val package_targeted_resolution_status: String?,
    val component_static_assessment: String?,
    val start_activity_attempted: Boolean,
    val launch_result: String
)

@JsonClass(generateAdapter = true)
data class IntentInvocationSafety(
    val auto_launch_allowed: Boolean,
    val requires_user_confirmation: Boolean,
    val side_effect_level: String,
    val notes: List<String>
)

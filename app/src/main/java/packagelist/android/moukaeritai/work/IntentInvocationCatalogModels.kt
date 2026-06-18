package packagelist.android.moukaeritai.work

import com.squareup.moshi.JsonClass

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
    val grant_flags: List<String>
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

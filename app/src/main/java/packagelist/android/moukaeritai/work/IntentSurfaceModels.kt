package packagelist.android.moukaeritai.work

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AppInfo(
    val app_package_name: String,
    val app_version_name: String,
    val app_version_code: Long,
    val target_sdk_version: Int,
    val min_sdk_version: Int,
    val build_type: String?,
    val build_time_utc: String? = null,
    val git_commit_short: String? = null,
    val git_commit_full: String? = null,
    val json_report_schema_version: Int? = null,
    val debug_build: Boolean,
    val installer_package_name: String?,
    val requested_permissions: List<String>,
    val granted_permissions: List<String>,
    val query_all_packages_granted: Boolean
)

@JsonClass(generateAdapter = true)
data class DeviceInfo(
    val android_sdk_int: Int,
    val android_release: String,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val product: String,
    val supported_abis: List<String>,
    val locale: String,
    val time_zone: String,
    val screen_width_px: Int,
    val screen_height_px: Int,
    val density_dpi: Int
)

@JsonClass(generateAdapter = true)
data class PackageVisibilityInfo(
    val visibility_config: String,
    val query_all_packages_granted: Boolean,
    val can_query_all_packages_by_permission_check: Boolean,
    val requested_permissions: List<String>,
    val declared_intent_queries_hardcoded_description: String
)

@JsonClass(generateAdapter = true)
data class ResolvedFilterSummary(
    val filter_actions: List<String>,
    val filter_categories: List<String>,
    val filter_schemes: List<String>,
    val filter_mime_types: List<String>,
    val filter_authorities: List<String>,
    val filter_priority: Int
)

@JsonClass(generateAdapter = true)
data class CandidateResult(
    val index: Int,
    val package_name: String,
    val activity_name: String,
    val component_name: String,
    val label: String,
    val exported: Boolean,
    val enabled: Boolean,
    val permission: String?,
    val process_name: String? = null,
    val target_activity: String? = null,
    val task_affinity: String? = null,
    val launch_mode: Int = 0,
    val document_launch_mode: Int = 0,
    val screen_orientation: Int = 0,
    val resize_mode: Int? = null,
    val flags: Int = 0,
    val priority: Int = 0,
    val preferred_order: Int = 0,
    val match: Int = 0,
    val is_default: Boolean = false,
    val activity_info_available: Boolean = true,
    val application_enabled: Boolean = true,
    val application_label: String? = null,
    val package_version_name: String? = null,
    val package_version_code: Long? = null,
    val package_target_sdk_version: Int? = null,
    val resolved_filter_summary: ResolvedFilterSummary? = null
)

@JsonClass(generateAdapter = true)
data class InvocationModeSummary(
    val implicit_resolution_candidate_count: Int,
    val package_targeted_resolvable_direct_count: Int,
    val package_targeted_resolvable_via_resolver_count: Int,
    val package_targeted_not_resolvable_count: Int,
    val component_explicit_spec_built_count: Int,
    val component_explicit_disabled_count: Int,
    val component_explicit_not_exported_count: Int,
    val component_explicit_requires_permission_count: Int,
    val component_explicit_launch_tested_count: Int = 0
)

@JsonClass(generateAdapter = true)
data class TargetAssessmentResult(
    val mode: String,
    val target_package: String,
    val target_activity: String?,
    val component_name: String? = null,
    val can_construct_component_name: Boolean? = null,
    val start_activity_attempted: Boolean? = null,
    val candidate_count: Int?,
    val resolved_package: String?,
    val resolved_activity: String?,
    val status: String,
    val note: String?
)

@JsonClass(generateAdapter = true)
data class DiagnosticError(
    val stage: String,
    val template_id: String? = null,
    val probe_id: String? = null,
    val family: String? = null,
    val mode: String?,
    val package_name: String?,
    val activity_name: String?,
    val error_class: String,
    val error_message: String,
    val stack_trace_short: String
)

@JsonClass(generateAdapter = true)
data class DiagnosticEvent(
    val event: String,
    val timestamp: String,
    val message: String
)

@JsonClass(generateAdapter = true)
data class ResolveActivityResult(
    val resolved_package: String?,
    val resolved_activity: String?,
    val is_resolver_activity: Boolean,
    val status: String
)

@JsonClass(generateAdapter = true)
data class IntentSurfaceReport(
    val schema: Int = 5, // Legacy field, schema_version is the canonical integer version
    val schema_version: Int = 5,
    val schema_id: String = "urn:uuid:8a69ce28-18d7-4720-b78f-1ab11cc52233",
    val run_id: String,
    val file_name: String,
    val generated_at_epoch_millis: Long,
    val generated_at_utc: String,
    val app: AppInfo,
    val device: DeviceInfo,
    val package_visibility: PackageVisibilityInfo,
    val intent_invocation_catalog: IntentInvocationCatalog? = null,
    val probe_families: List<ProbeFamilySummary>,
    val intent_surface_probes: List<IntentSurfaceProbeResult>,
    val component_surface_summary: List<ComponentSurfaceEntry>,
    val summary: SurfaceDiagnosticSummary,
    val errors: List<DiagnosticError>,
    val events: List<DiagnosticEvent>
)

@JsonClass(generateAdapter = true)
data class ProbeFamilySummary(
    val family: String,
    val probe_count: Int,
    val total_candidate_rows: Int,
    val unique_component_count: Int,
    val duplicate_candidate_row_count: Int,
    val unique_package_count: Int,
    val unique_components_seen_with_flags_0: Int,
    val unique_components_seen_with_match_default_only: Int,
    val components_only_seen_with_flags_0: Int,
    val components_only_seen_with_match_default_only: Int,
    val disabled_candidate_count: Int,
    val resolver_activity_assessment_count: Int,
    val error_count: Int,
    val duration_ms: Long
)

@JsonClass(generateAdapter = true)
data class IntentSpec(
    val action: String?,
    val data_uri_kind: String?,
    val data_scheme: String?,
    val data_display_redacted: String?,
    val mime_type: String?,
    val categories: List<String>,
    val intent_flags_raw: Int,
    val intent_flags_labels: List<String>,
    val extras_schema: Map<String, String>,
    val clip_data_schema: String? = null
)

@JsonClass(generateAdapter = true)
data class IntentSurfaceProbeResult(
    val probe_id: String,
    val family: String,
    val action: String?,
    val data_uri_kind: String?,
    val data_scheme: String?,
    val data_display_redacted: String?,
    val mime_type: String?,
    val categories: List<String>,
    val flags: Int,
    val extras_schema: Map<String, String>,
    val intent_spec: IntentSpec? = null,
    val query_flags: Int,
    val query_flags_raw: Int,
    val query_flags_labels: List<String>,
    val candidate_count: Int,
    val enabled_candidate_count: Int,
    val disabled_candidate_count: Int,
    val unique_package_count: Int,
    val candidates: List<CandidateResult>,
    val resolve_activity_result: ResolveActivityResult?,
    val package_targeted_assessments: List<TargetAssessmentResult>,
    val component_explicit_assessments: List<TargetAssessmentResult>,
    val duration_ms: Long,
    val errors: List<DiagnosticError>
)

@JsonClass(generateAdapter = true)
data class ComponentSurfaceEntry(
    val component_name: String,
    val package_name: String,
    val activity_name: String,
    val label: String,
    val exported: Boolean,
    val enabled: Boolean,
    val permission: String?,
    val application_label: String?,
    val package_version_name: String?,
    val package_version_code: Long?,
    val package_target_sdk_version: Int?,
    val seen_in_probe_ids: List<String>,
    val seen_families: List<String>,
    val seen_actions: List<String>,
    val seen_mime_types: List<String>,
    val seen_schemes: List<String>,
    val seen_category_sets: List<List<String>>,
    val seen_extras_keys: List<String>,
    val package_target_statuses: List<String>,
    val component_explicit_assessment: String,
    val risk_note: String?
)

@JsonClass(generateAdapter = true)
data class ProbeComparisonSummary(
    val same_results_between_no_category_and_default: Boolean,
    val same_results_between_flags_0_and_match_default_only: Boolean,
    val components_added_by_default_category: List<String>,
    val components_removed_by_default_category: List<String>,
    val components_added_by_match_default_only: List<String>,
    val components_removed_by_match_default_only: List<String>
)

@JsonClass(generateAdapter = true)
data class SurfaceDiagnosticSummary(
    val schema: Int, // Legacy field, schema_version is the canonical integer version
    val run_id: String,
    val probe_family_count: Int,
    val probe_count: Int,
    val total_candidate_rows: Int,
    val unique_component_count: Int,
    val unique_package_count: Int,
    val candidates_by_family: Map<String, Int>,
    val candidates_by_probe: Map<String, Int>,
    val disabled_candidates_by_family: Map<String, Int>,
    val unique_components_by_family: Map<String, Int>,
    val package_target_resolvable_direct_count: Int,
    val package_target_resolvable_via_resolver_count: Int,
    val package_target_not_resolvable_count: Int,
    val component_spec_built_count: Int,
    val component_disabled_count: Int,
    val component_not_exported_count: Int,
    val component_requires_permission_count: Int,
    val error_count: Int,
    val report_duration_ms: Long,
    val invocation_mode_summary: InvocationModeSummary? = null,
    val probe_comparisons: Map<String, ProbeComparisonSummary> = emptyMap()
)

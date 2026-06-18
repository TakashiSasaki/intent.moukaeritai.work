package packagelist.android.moukaeritai.work

class IntentInvocationCatalogBuilder {
    fun build(probes: List<IntentSurfaceProbeResult>): IntentInvocationCatalog {
        val candidates = mutableListOf<IntentInvocationCandidate>()
        
        for (probe in probes) {
            val spec = probe.intent_spec ?: continue
            val action = spec.action
            
            for (candidate in probe.candidates) {
                // One candidate per (probe_id, component_name)
                val candId = "cand.${probe.probe_id}.${sanitize(candidate.package_name)}.${sanitize(candidate.activity_name)}"
                
                // Deduplicate check
                if (candidates.any { it.candidate_id == candId }) continue
                
                val target = IntentInvocationTarget(
                    package_name = candidate.package_name,
                    activity_name = candidate.activity_name,
                    component_name = candidate.component_name
                )
                
                val dataApiAndUri = determineDataApi(spec)
                val setApi = dataApiAndUri.first
                val recipe = IntentInvocationRecipe(
                    targeting_mode = "COMPONENT_EXPLICIT",
                    construction_api = "setComponent",
                    action = action,
                    data = IntentInvocationData(
                        set_api = setApi,
                        uri = if (setApi == "setData" || setApi == "setDataAndType") "content://example" else null, // Fallback if necessary or just use redacted
                        uri_kind = spec.data_uri_kind ?: "NONE",
                        scheme = spec.data_scheme,
                        display_redacted = spec.data_display_redacted,
                        mime_type = spec.mime_type
                    ),
                    categories = spec.categories,
                    extras = buildExtras(spec.extras_schema),
                    clip_data = spec.clip_data_schema,
                    flags = spec.intent_flags_labels,
                    grant_flags = extractGrantFlags(spec.intent_flags_labels)
                )

                var packageStatus: String? = null
                var explicitStatus: String? = null
                
                probe.package_targeted_assessments.find { it.target_package == candidate.package_name }?.let {
                    packageStatus = mapPackageStatus(it.status)
                }
                
                probe.component_explicit_assessments.find { it.target_package == candidate.package_name && it.target_activity == candidate.activity_name }?.let {
                    explicitStatus = mapComponentStatus(it.status)
                }

                val evidence = IntentInvocationEvidence(
                    implicit_resolution_observed = true,
                    implicit_evidence_status = "IMPLICIT_CANDIDATE_OBSERVED",
                    implicit_probe_candidate_index = candidate.index,
                    package_targeted_resolution_status = packageStatus,
                    component_static_assessment = explicitStatus,
                    start_activity_attempted = false,
                    launch_result = "START_ACTIVITY_NOT_TESTED"
                )

                val safety = IntentInvocationSafety(
                    auto_launch_allowed = false,
                    requires_user_confirmation = true,
                    side_effect_level = "MAY_OPEN_EXTERNAL_APP",
                    notes = listOf("Static discovery only.")
                )

                candidates.add(
                    IntentInvocationCandidate(
                        candidate_id = candId,
                        source_probe_id = probe.probe_id,
                        source_family = probe.family,
                        display_label = candidate.label,
                        target = target,
                        intent_recipe = recipe,
                        evidence = evidence,
                        safety = safety
                    )
                )
            }
        }
        
        return IntentInvocationCatalog(
            catalog_schema_version = 1,
            catalog_kind = "moukaeritai.intent_invocation_catalog",
            candidate_count = candidates.size,
            candidates = candidates
        )
    }
    
    private fun sanitize(str: String): String {
        return str.replace(Regex("[^a-zA-Z0-9_]"), "_")
    }

    private fun determineDataApi(spec: IntentSpec): Pair<String, Boolean> {
        val hasUri = spec.data_uri_kind != null && spec.data_uri_kind != "NONE"
        val hasMime = spec.mime_type != null
        
        return when {
            hasUri && hasMime -> "setDataAndType" to true
            hasUri -> "setData" to true
            hasMime -> "setType" to false
            else -> "none" to false
        }
    }
    
    private fun buildExtras(schema: Map<String, String>): List<IntentInvocationExtra> {
        return schema.map { (key, type) ->
            IntentInvocationExtra(
                key = key,
                value_type = type.uppercase(), // assuming the previous code had types like "String", we upper-case them. Actually needs proper mapping.
                value_source = "FIXED_DUMMY",
                required = false,
                example_value_redacted = null,
                notes = emptyList()
            )
        }
    }
    
    private fun extractGrantFlags(flags: List<String>): List<String> {
        return flags.filter { it.startsWith("FLAG_GRANT_") }
    }
    
    private fun mapPackageStatus(status: String): String {
        return when (status) {
            "RESOLVABLE_DIRECT" -> "PACKAGE_TARGETED_RESOLVABLE_DIRECT"
            "RESOLVABLE_VIA_RESOLVER" -> "PACKAGE_TARGETED_RESOLVABLE_VIA_RESOLVER"
            "NOT_RESOLVABLE" -> "PACKAGE_TARGETED_NOT_RESOLVABLE"
            else -> "PACKAGE_TARGETED_RESOLVABLE_OTHER"
        }
    }
    
    private fun mapComponentStatus(status: String): String {
        return when (status) {
            "COMPONENT_SPEC_BUILT" -> "EXPLICIT_COMPONENT_STATIC_OK"
            "DISABLED" -> "EXPLICIT_COMPONENT_DISABLED"
            "NOT_EXPORTED" -> "EXPLICIT_COMPONENT_NOT_EXPORTED"
            "REQUIRES_PERMISSION" -> "EXPLICIT_COMPONENT_REQUIRES_PERMISSION"
            else -> "EXPLICIT_COMPONENT_STATIC_OK"
        }
    }
}

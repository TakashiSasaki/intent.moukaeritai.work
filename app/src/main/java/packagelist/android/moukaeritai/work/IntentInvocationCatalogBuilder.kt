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
                
                val displayRedacted = spec.data_display_redacted
                val scheme = spec.data_scheme
                val isSafe = isUriExecutableAndSafe(scheme, displayRedacted)
                
                val hasUriInput = spec.data_uri_kind != null && spec.data_uri_kind != "NONE"
                val hasMimeInput = spec.mime_type != null
                
                val setApi = when {
                    hasUriInput -> {
                        if (isSafe) {
                            if (hasMimeInput) CatalogConstants.SET_API_SET_DATA_AND_TYPE
                            else CatalogConstants.SET_API_SET_DATA
                        } else {
                            if (hasMimeInput) CatalogConstants.SET_API_RUNTIME_PROVIDED_DATA_AND_TYPE
                            else CatalogConstants.SET_API_RUNTIME_PROVIDED_DATA
                        }
                    }
                    hasMimeInput -> CatalogConstants.SET_API_SET_TYPE
                    else -> CatalogConstants.SET_API_NONE
                }
                
                val finalUri = if (isSafe) displayRedacted else null
                
                val runtimeRequirements = mutableListOf<IntentRuntimeRequirement>()
                if (setApi == CatalogConstants.SET_API_RUNTIME_PROVIDED_DATA || setApi == CatalogConstants.SET_API_RUNTIME_PROVIDED_DATA_AND_TYPE) {
                    val reqType = when (spec.data_uri_kind) {
                        "FILE_PROVIDER_CONTENT_URI" -> CatalogConstants.REQ_GENERATED_TEMP_URI
                        "USER_SELECTED" -> CatalogConstants.REQ_USER_SELECTED_URI
                        else -> CatalogConstants.REQ_CALLER_SUPPLIED_URI
                    }
                    runtimeRequirements.add(
                        IntentRuntimeRequirement(
                            requirement_type = reqType,
                            key = null,
                            required = true,
                            expected_value_type = CatalogConstants.VAL_TYPE_URI_STRING,
                            uri_kind = spec.data_uri_kind,
                            mime_type = spec.mime_type,
                            grant_flags = extractGrantFlags(spec.intent_flags_labels),
                            description = "Consumer must provide a suitable content URI of kind ${spec.data_uri_kind ?: "generic"}."
                        )
                    )
                }

                val extrasList = buildExtras(spec.extras_schema)
                // If any extra mapped to UNKNOWN, add a requirement or prevent launch (auto_launch is already false)
                val unknownExtras = extrasList.filter { it.value_type == CatalogConstants.EXTRA_TYPE_UNKNOWN }
                if (unknownExtras.isNotEmpty()) {
                    runtimeRequirements.add(
                        IntentRuntimeRequirement(
                            requirement_type = CatalogConstants.REQ_UNKNOWN_RUNTIME_VALUE,
                            key = unknownExtras.joinToString(",") { it.key },
                            required = false,
                            expected_value_type = CatalogConstants.VAL_TYPE_UNKNOWN,
                            uri_kind = null,
                            mime_type = null,
                            grant_flags = emptyList(),
                            description = "Unknown extra keys detected: ${unknownExtras.map { it.key }}. Manual integration required."
                        )
                    )
                }
                
                val recipe = IntentInvocationRecipe(
                    targeting_mode = CatalogConstants.TARGETING_MODE_COMPONENT_EXPLICIT,
                    construction_api = CatalogConstants.CONSTRUCTION_API_SET_COMPONENT,
                    action = action,
                    data = IntentInvocationData(
                        set_api = setApi,
                        uri = finalUri,
                        uri_kind = spec.data_uri_kind ?: CatalogConstants.URI_KIND_NONE,
                        scheme = scheme,
                        display_redacted = displayRedacted,
                        mime_type = spec.mime_type
                    ),
                    categories = spec.categories,
                    extras = extrasList,
                    clip_data = spec.clip_data_schema,
                    flags = filterRecipeFlags(spec.intent_flags_labels),
                    grant_flags = extractGrantFlags(spec.intent_flags_labels),
                    runtime_requirements = runtimeRequirements
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
                    implicit_evidence_status = CatalogConstants.EVIDENCE_IMPLICIT_OBSERVED,
                    implicit_probe_candidate_index = candidate.index,
                    package_targeted_resolution_status = packageStatus,
                    component_static_assessment = explicitStatus,
                    start_activity_attempted = false,
                    launch_result = CatalogConstants.LAUNCH_NOT_TESTED
                )

                val safetyNotes = mutableListOf("Static discovery only.")
                if (unknownExtras.isNotEmpty()) {
                    safetyNotes.add("Unknown extras detected. Auto-launch is unsafe.")
                }
                val safety = IntentInvocationSafety(
                    auto_launch_allowed = false,
                    requires_user_confirmation = true,
                    side_effect_level = CatalogConstants.SIDE_EFFECT_MAY_OPEN_EXTERNAL,
                    notes = safetyNotes
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
            candidate_count = candidates.size,
            candidates = candidates
        )
    }
    
    private fun sanitize(str: String): String {
        return str.replace(Regex("[^a-zA-Z0-9_]"), "_")
    }

    private fun isUriExecutableAndSafe(scheme: String?, displayRedacted: String?): Boolean {
        if (scheme == null || displayRedacted == null) return false
        val lowerScheme = scheme.lowercase().trim()
        val allowedSchemes = setOf("http", "https", "geo", "mailto", "tel", "market")
        if (lowerScheme !in allowedSchemes) return false
        
        val lowerRedacted = displayRedacted.lowercase()
        if (lowerRedacted.contains("redacted") || 
            lowerRedacted.contains("[") || 
            lowerRedacted.contains("<") || 
            lowerRedacted.contains("*") ||
            lowerRedacted.startsWith("placeholder")) {
            return false
        }
        return true
    }
    
    private fun buildExtras(schema: Map<String, String>): List<IntentInvocationExtra> {
        return schema.map { (key, type) ->
            val mappedType = mapExtraType(type)
            val notes = mutableListOf<String>()
            if (mappedType == CatalogConstants.EXTRA_TYPE_UNKNOWN) {
                notes.add("Note: Consumer must not auto-launch this candidate without additional handling for unknown extra type '$type'")
            }
            IntentInvocationExtra(
                key = key,
                value_type = mappedType,
                value_source = "FIXED_DUMMY",
                required = false,
                example_value_redacted = null,
                notes = notes
            )
        }
    }
    
    private fun mapExtraType(legacyType: String): String {
        val norm = legacyType.trim().uppercase()
        return when (norm) {
            "STRING", "JAVA.LANG.STRING" -> CatalogConstants.EXTRA_TYPE_STRING
            "STRING_ARRAY", "STRING[]", "[LJAVA.LANG.STRING;" -> CatalogConstants.EXTRA_TYPE_STRING_ARRAY
            "BOOLEAN", "BOOL", "JAVA.LANG.BOOLEAN" -> CatalogConstants.EXTRA_TYPE_BOOLEAN
            "INT", "INTEGER", "JAVA.LANG.INTEGER" -> CatalogConstants.EXTRA_TYPE_INT
            "LONG", "JAVA.LANG.LONG" -> CatalogConstants.EXTRA_TYPE_LONG
            "FLOAT", "DOUBLE", "JAVA.LANG.FLOAT", "JAVA.LANG.DOUBLE" -> CatalogConstants.EXTRA_TYPE_FLOAT
            "URI", "URI_STRING", "ANDROID.NET.URI" -> CatalogConstants.EXTRA_TYPE_URI_STRING
            "URI_ARRAY", "URI_STRING_ARRAY", "URI[]", "[LANDROID.NET.URI;" -> CatalogConstants.EXTRA_TYPE_URI_STRING_ARRAY
            else -> CatalogConstants.EXTRA_TYPE_UNKNOWN
        }
    }
    
    private fun filterRecipeFlags(flags: List<String>): List<String> {
        val allowed = setOf(
            CatalogConstants.FLAG_ACTIVITY_NEW_TASK,
            CatalogConstants.FLAG_ACTIVITY_CLEAR_TOP,
            CatalogConstants.FLAG_ACTIVITY_SINGLE_TOP
        )
        return flags.filter { it in allowed }
    }
    
    private fun extractGrantFlags(flags: List<String>): List<String> {
        val allowed = setOf(
            CatalogConstants.FLAG_GRANT_READ_URI_PERMISSION,
            CatalogConstants.FLAG_GRANT_WRITE_URI_PERMISSION,
            CatalogConstants.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            CatalogConstants.FLAG_GRANT_PREFIX_URI_PERMISSION
        )
        return flags.filter { it in allowed }
    }
    
    private fun mapPackageStatus(status: String): String {
        return when (status) {
            "RESOLVABLE_DIRECT" -> CatalogConstants.PKG_RESOLVABLE_DIRECT
            "RESOLVABLE_VIA_RESOLVER" -> CatalogConstants.PKG_RESOLVABLE_VIA_RESOLVER
            "NOT_RESOLVABLE" -> CatalogConstants.PKG_NOT_RESOLVABLE
            else -> CatalogConstants.PKG_RESOLVABLE_OTHER
        }
    }
    
    private fun mapComponentStatus(status: String): String {
        return when (status) {
            "COMPONENT_SPEC_BUILT" -> CatalogConstants.COMP_STATIC_OK
            "DISABLED" -> CatalogConstants.COMP_DISABLED
            "NOT_EXPORTED" -> CatalogConstants.COMP_NOT_EXPORTED
            "REQUIRES_PERMISSION" -> CatalogConstants.COMP_REQUIRES_PERMISSION
            else -> CatalogConstants.COMP_UNKNOWN // Unknown statuses fallback safely to UNKNOWN, not to COMP_STATIC_OK
        }
    }
}

package packagelist.android.moukaeritai.work

class IntentSurfaceReportSemanticValidator {

    data class ValidationResult(val isValid: Boolean, val errors: List<String>)

    fun validate(report: IntentSurfaceReport): ValidationResult {
        val errors = mutableListOf<String>()

        if (report.schema != 5) errors.add("schema must be 5")
        if (report.schema_version != 5) errors.add("schema_version must be 5")
        if (report.schema_id != "work.moukaeritai.intent-surface-report.schema.v5") {
            errors.add("schema_id must be work.moukaeritai.intent-surface-report.schema.v5")
        }

        val catalog = report.intent_invocation_catalog
        if (catalog == null) {
            errors.add("intent_invocation_catalog is missing")
        } else {
            if (catalog.catalog_kind != "moukaeritai.intent_invocation_catalog") {
                errors.add("catalog_kind must be moukaeritai.intent_invocation_catalog")
            }
            if (catalog.catalog_schema_version != 1) {
                errors.add("catalog_schema_version must be 1")
            }
            if (catalog.candidate_count != catalog.candidates.size) {
                errors.add("intent_invocation_catalog.candidate_count does not match candidates.size")
            }

            val seenIds = mutableSetOf<String>()
            val allowedTargetingModes = setOf(
                CatalogConstants.TARGETING_MODE_COMPONENT_EXPLICIT,
                CatalogConstants.TARGETING_MODE_PACKAGE_TARGETED,
                CatalogConstants.TARGETING_MODE_IMPLICIT
            )
            val allowedSetApis = setOf(
                CatalogConstants.SET_API_SET_DATA,
                CatalogConstants.SET_API_SET_TYPE,
                CatalogConstants.SET_API_SET_DATA_AND_TYPE,
                CatalogConstants.SET_API_RUNTIME_PROVIDED_DATA,
                CatalogConstants.SET_API_RUNTIME_PROVIDED_DATA_AND_TYPE,
                CatalogConstants.SET_API_NONE
            )
            val allowedExtraTypes = setOf(
                CatalogConstants.EXTRA_TYPE_STRING,
                CatalogConstants.EXTRA_TYPE_STRING_ARRAY,
                CatalogConstants.EXTRA_TYPE_BOOLEAN,
                CatalogConstants.EXTRA_TYPE_INT,
                CatalogConstants.EXTRA_TYPE_LONG,
                CatalogConstants.EXTRA_TYPE_FLOAT,
                CatalogConstants.EXTRA_TYPE_URI_STRING,
                CatalogConstants.EXTRA_TYPE_URI_STRING_ARRAY,
                CatalogConstants.EXTRA_TYPE_UNKNOWN
            )
            val allowedRecipeFlags = setOf(
                CatalogConstants.FLAG_ACTIVITY_NEW_TASK,
                CatalogConstants.FLAG_ACTIVITY_CLEAR_TOP,
                CatalogConstants.FLAG_ACTIVITY_SINGLE_TOP
            )
            val allowedGrantFlags = setOf(
                CatalogConstants.FLAG_GRANT_READ_URI_PERMISSION,
                CatalogConstants.FLAG_GRANT_WRITE_URI_PERMISSION,
                CatalogConstants.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                CatalogConstants.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
            val allowedSideEffects = setOf(
                CatalogConstants.SIDE_EFFECT_UNKNOWN,
                CatalogConstants.SIDE_EFFECT_MAY_OPEN_EXTERNAL,
                CatalogConstants.SIDE_EFFECT_MAY_SHOW_CHOOSER,
                CatalogConstants.SIDE_EFFECT_MAY_REQUIRE_INPUT,
                CatalogConstants.SIDE_EFFECT_MAY_SEND_SHARE,
                CatalogConstants.SIDE_EFFECT_MAY_MODIFY_STATE
            )

            for (candidate in catalog.candidates) {
                val cid = candidate.candidate_id
                if (cid.isBlank()) {
                    errors.add("candidate_id cannot be blank")
                } else if (!seenIds.add(cid)) {
                    errors.add("Duplicate candidate_id: $cid")
                }
                
                val expectedComponent = "${candidate.target.package_name}/${candidate.target.activity_name}"
                if (candidate.target.component_name != expectedComponent) {
                    errors.add("target.component_name must be package_name/activity_name for $cid")
                }

                val targetingMode = candidate.intent_recipe.targeting_mode
                if (targetingMode !in allowedTargetingModes) {
                    errors.add("targeting_mode must be one of the allowed enum values for $cid")
                }

                when (targetingMode) {
                    CatalogConstants.TARGETING_MODE_COMPONENT_EXPLICIT -> {
                        if (candidate.target.package_name.isEmpty() || candidate.target.activity_name.isEmpty() || candidate.target.component_name.isEmpty()) {
                            errors.add("COMPONENT_EXPLICIT targeting mode requires package, activity, and component for $cid")
                        }
                        if (candidate.intent_recipe.construction_api != CatalogConstants.CONSTRUCTION_API_SET_COMPONENT) {
                            errors.add("COMPONENT_EXPLICIT mode expects setComponent construction_api for $cid")
                        }
                    }
                    CatalogConstants.TARGETING_MODE_PACKAGE_TARGETED -> {
                        if (candidate.intent_recipe.construction_api != CatalogConstants.CONSTRUCTION_API_SET_PACKAGE) {
                            errors.add("PACKAGE_TARGETED mode expects setPackage construction_api for $cid")
                        }
                    }
                    CatalogConstants.TARGETING_MODE_IMPLICIT -> {
                        if (candidate.intent_recipe.construction_api != CatalogConstants.CONSTRUCTION_API_IMPLICIT) {
                            errors.add("IMPLICIT mode expects implicit construction_api for $cid")
                        }
                    }
                }

                if (candidate.evidence.start_activity_attempted) {
                    errors.add("start_activity_attempted must be false for $cid")
                }
                if (candidate.evidence.launch_result != CatalogConstants.LAUNCH_NOT_TESTED) {
                    errors.add("launch_result must be START_ACTIVITY_NOT_TESTED for $cid")
                }
                if (candidate.safety.auto_launch_allowed) {
                    errors.add("auto_launch_allowed must be false for $cid")
                }
                if (!candidate.safety.requires_user_confirmation) {
                    errors.add("requires_user_confirmation must be true for $cid")
                }
                if (candidate.safety.side_effect_level !in allowedSideEffects) {
                    errors.add("side_effect_level must be one of allowed values for $cid")
                }

                val dataApi = candidate.intent_recipe.data.set_api
                if (dataApi !in allowedSetApis) {
                    errors.add("data.set_api is invalid for $cid")
                }

                val uri = candidate.intent_recipe.data.uri
                val mime = candidate.intent_recipe.data.mime_type
                val requirements = candidate.intent_recipe.runtime_requirements
                
                when (dataApi) {
                    CatalogConstants.SET_API_SET_DATA -> {
                        if (uri == null || mime != null) {
                            errors.add("setData implies URI is non-null and MIME is null for $cid")
                        } else if (uri == "content://example" || uri.contains("[REDACTED]") || uri.contains("<REDACTED>") || uri.contains("*")) {
                            errors.add("setData cannot use fake/redacted URI placeholder for $cid")
                        }
                    }
                    CatalogConstants.SET_API_SET_TYPE -> {
                        if (uri != null || mime == null) {
                            errors.add("setType implies URI is null and MIME is non-null for $cid")
                        }
                    }
                    CatalogConstants.SET_API_SET_DATA_AND_TYPE -> {
                        if (uri == null || mime == null) {
                            errors.add("setDataAndType implies both URI and MIME are non-null for $cid")
                        } else if (uri == "content://example" || uri.contains("[REDACTED]") || uri.contains("<REDACTED>") || uri.contains("*")) {
                            errors.add("setDataAndType cannot use fake/redacted URI placeholder for $cid")
                        }
                    }
                    CatalogConstants.SET_API_NONE -> {
                        if (uri != null || mime != null) {
                            errors.add("none implies both URI and MIME are null for $cid")
                        }
                    }
                    CatalogConstants.SET_API_RUNTIME_PROVIDED_DATA -> {
                        if (uri != null || mime != null) {
                            errors.add("runtimeProvidedData implies both URI and MIME are null for $cid")
                        }
                        val hasUriReq = requirements.any {
                            it.requirement_type in setOf(
                                CatalogConstants.REQ_CALLER_SUPPLIED_URI,
                                CatalogConstants.REQ_GENERATED_TEMP_URI,
                                CatalogConstants.REQ_USER_SELECTED_URI
                            )
                        }
                        if (!hasUriReq) {
                            errors.add("runtimeProvidedData requires matching runtime_requirements containing a URI requirement for $cid")
                        }
                    }
                    CatalogConstants.SET_API_RUNTIME_PROVIDED_DATA_AND_TYPE -> {
                        if (uri != null || mime == null) {
                            errors.add("runtimeProvidedDataAndType implies URI is null and MIME is non-null for $cid")
                        }
                        val hasUriReq = requirements.any {
                            it.requirement_type in setOf(
                                CatalogConstants.REQ_CALLER_SUPPLIED_URI,
                                CatalogConstants.REQ_GENERATED_TEMP_URI,
                                CatalogConstants.REQ_USER_SELECTED_URI
                            )
                        }
                        if (!hasUriReq) {
                            errors.add("runtimeProvidedDataAndType requires matching runtime_requirements containing a URI requirement for $cid")
                        }
                    }
                }

                for (extra in candidate.intent_recipe.extras) {
                    if (extra.value_type !in allowedExtraTypes) {
                        errors.add("Unknown extra.value_type: ${extra.value_type} for $cid")
                    }
                }

                for (flag in candidate.intent_recipe.flags) {
                    if (flag !in allowedRecipeFlags) {
                        errors.add("Invalid recipe flag: $flag for $cid")
                    }
                }

                for (gf in candidate.intent_recipe.grant_flags) {
                    if (gf !in allowedGrantFlags) {
                        errors.add("Invalid grant flag: $gf for $cid")
                    }
                }
            }
        }

        return ValidationResult(errors.isEmpty(), errors)
    }
}

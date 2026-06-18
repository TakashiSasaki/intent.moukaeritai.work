package packagelist.android.moukaeritai.work

class IntentSurfaceReportSemanticValidator {

    data class ValidationResult(val isValid: Boolean, val errors: List<String>)

    fun validate(report: IntentSurfaceReport): ValidationResult {
        val errors = mutableListOf<String>()

        if (report.schema != 5) errors.add("schema must be 5")
        if (report.schema_version != 5) errors.add("schema_version must be 5")
        if (report.schema_id != "work.moukaeritai.intent-surface-report.schema.v5") errors.add("schema_id must be work.moukaeritai.intent-surface-report.schema.v5")

        val catalog = report.intent_invocation_catalog
        if (catalog == null) {
            errors.add("intent_invocation_catalog is missing")
        } else {
            if (catalog.candidate_count != catalog.candidates.size) {
                errors.add("intent_invocation_catalog.candidate_count does not match candidates.size")
            }

            val seenIds = mutableSetOf<String>()
            for (candidate in catalog.candidates) {
                if (!seenIds.add(candidate.candidate_id)) {
                    errors.add("Duplicate candidate_id: \${candidate.candidate_id}")
                }
                
                val expectedComponent = "\${candidate.target.package_name}/\${candidate.target.activity_name}"
                if (candidate.target.component_name != expectedComponent) {
                    errors.add("target.component_name must be package_name/activity_name for \${candidate.candidate_id}")
                }

                if (candidate.intent_recipe.targeting_mode == "COMPONENT_EXPLICIT") {
                    if (candidate.target.package_name.isEmpty() || candidate.target.activity_name.isEmpty() || candidate.target.component_name.isEmpty()) {
                        errors.add("COMPONENT_EXPLICIT targeting mode requires package, activity, and component for \${candidate.candidate_id}")
                    }
                    if (candidate.intent_recipe.construction_api != "setComponent") {
                        errors.add("COMPONENT_EXPLICIT mode expects setComponent construction_api for \${candidate.candidate_id}")
                    }
                }

                if (candidate.evidence.start_activity_attempted) {
                    errors.add("start_activity_attempted must be false in this task for \${candidate.candidate_id}")
                }
                if (candidate.evidence.launch_result != "START_ACTIVITY_NOT_TESTED") {
                    errors.add("launch_result must be START_ACTIVITY_NOT_TESTED for \${candidate.candidate_id}")
                }
                if (candidate.safety.auto_launch_allowed) {
                    errors.add("auto_launch_allowed must be false for \${candidate.candidate_id}")
                }

                val dataApi = candidate.intent_recipe.data.set_api
                val uri = candidate.intent_recipe.data.uri
                val mime = candidate.intent_recipe.data.mime_type
                when (dataApi) {
                    "setData" -> {
                        if (uri == null || mime != null) {
                            errors.add("setData implies URI is non-null and MIME is null for \${candidate.candidate_id}")
                        }
                    }
                    "setType" -> {
                        if (uri != null || mime == null) {
                            errors.add("setType implies URI is null and MIME is non-null for \${candidate.candidate_id}")
                        }
                    }
                    "setDataAndType" -> {
                        if (uri == null || mime == null) {
                            errors.add("setDataAndType implies both URI and MIME are non-null for \${candidate.candidate_id}")
                        }
                    }
                    "none" -> {
                        if (uri != null || mime != null) {
                            errors.add("none implies both URI and MIME are null for \${candidate.candidate_id}")
                        }
                    }
                }
            }
        }

        return ValidationResult(errors.isEmpty(), errors)
    }
}

package packagelist.android.moukaeritai.work

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeFormatter

class IntentExperimentRepository {
    
    private val TAG = "IntentExperiment"
    private val SCHEMA = 1
    private var logCallback: ((String) -> Unit)? = null

    fun runDiagnostics(context: Context, runId: String, onLogLine: (String) -> Unit) {
        logCallback = onLogLine
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val timestamp = dateFormat.format(java.util.Date())

        logEvent(JSONObject().apply {
            put("event", "run_begin")
            put("run_id", runId)
            put("timestamp", timestamp)
        })

        try {
            // 1. Environment
            logStage(runId, "after_environment")
            logEvent(JSONObject().apply {
                put("event", "environment")
                put("run_id", runId)
                put("app_package_name", context.packageName)
                put("app_version_name", context.packageManager.getPackageInfo(context.packageName, 0).versionName)
                put("android_sdk_int", Build.VERSION.SDK_INT)
                put("android_release", Build.VERSION.RELEASE)
                put("target_sdk_version", context.applicationInfo.targetSdkVersion)
                put("visibility_config", "baseline_no_permission_no_queries")
                put("query_all_packages_granted", false)
            })

            val templates = listOf(
                createSpec(IntentTemplateId.VIEW_HTTPS_URL),
                createSpec(IntentTemplateId.SEND_TEXT_PLAIN)
            )

            var totalCandidates = 0
            var totalPackageTargetsResolvable = 0
            var totalComponentSpecsBuilt = 0
            var errorsCount = 0

            logStage(runId, "before_view_https")
            // A. VIEW_HTTPS_URL
            processTemplate(context, runId, templates[0]) {
                totalCandidates += it.candidatesCount
                totalPackageTargetsResolvable += it.packageTargetsCount
                totalComponentSpecsBuilt += it.componentSpecsCount
                errorsCount += it.errorsCount
            }
            logStage(runId, "after_view_https")

            logStage(runId, "before_send_text")
            // B. SEND_TEXT_PLAIN
            processTemplate(context, runId, templates[1]) {
                totalCandidates += it.candidatesCount
                totalPackageTargetsResolvable += it.packageTargetsCount
                totalComponentSpecsBuilt += it.componentSpecsCount
                errorsCount += it.errorsCount
            }
            logStage(runId, "after_send_text")

            logEvent(JSONObject().apply {
                put("event", "run_summary")
                put("run_id", runId)
                put("total_templates", templates.size)
                put("total_candidates", totalCandidates)
                put("total_package_targets_resolvable", totalPackageTargetsResolvable)
                put("total_component_specs_built", totalComponentSpecsBuilt)
                put("errors_count", errorsCount)
            })
        } finally {
            logEvent(JSONObject().apply {
                put("event", "run_end")
                put("run_id", runId)
            })
            logCallback = null
        }
    }

    private fun processTemplate(
        context: Context, 
        runId: String, 
        spec: IntentExperimentSpec,
        onProcessed: (TemplateResults) -> Unit
    ) {
        var candidatesCount = 0
        var packageTargetsCount = 0
        var componentSpecsCount = 0
        var errorsCount = 0

        try {
            logEvent(JSONObject().apply {
                put("event", "template_begin")
                put("run_id", runId)
                put("template_id", spec.templateId.name)
            })

            logEvent(createIntentSpecJson(runId, spec))

            val candidates = resolveCandidates(context, spec)
            candidatesCount = candidates.size

            candidates.forEachIndexed { index, cand ->
                logEvent(JSONObject().apply {
                    put("event", "candidate")
                    put("run_id", runId)
                    put("template_id", spec.templateId.name)
                    put("index", index + 1)
                    put("package", cand.packageName)
                    put("activity", cand.activityName)
                    put("component", "${cand.packageName}/${cand.activityName}")
                    put("label", cand.label ?: "")
                    put("exported", cand.exported)
                    put("enabled", cand.enabled)
                    put("permission", cand.permission ?: "null")
                })
            }

            logEvent(JSONObject().apply {
                put("event", "resolve_summary")
                put("run_id", runId)
                put("template_id", spec.templateId.name)
                put("mode", "IMPLICIT")
                put("candidate_count", candidates.size)
                put("status", "ok")
                put("content_hash_short", "abc")
            })

            candidates.forEach { cand ->
                try {
                    val targetIntent = Intent(spec.action)
                    spec.dataUri?.let { targetIntent.data = Uri.parse(it) }
                    spec.mimeType?.let { targetIntent.type = it }
                    targetIntent.setPackage(cand.packageName)
                    
                    val resolvedTarget = context.packageManager.resolveActivity(targetIntent, 0)
                    if (resolvedTarget != null) packageTargetsCount++

                    logEvent(JSONObject().apply {
                        put("event", "target_assessment")
                        put("run_id", runId)
                        put("template_id", spec.templateId.name)
                        put("mode", "PACKAGE_TARGETED")
                        put("target_package", cand.packageName)
                        put("candidate_count", 1)
                        put("resolved_package", resolvedTarget?.activityInfo?.packageName ?: "null")
                        put("status", if (resolvedTarget != null) "RESOLVABLE" else "NOT_RESOLVABLE")
                    })

                    componentSpecsCount++
                    logEvent(JSONObject().apply {
                        put("event", "target_assessment")
                        put("run_id", runId)
                        put("template_id", spec.templateId.name)
                        put("mode", "COMPONENT_EXPLICIT")
                        put("target_package", cand.packageName)
                        put("target_activity", cand.activityName)
                        put("exported", cand.exported)
                        put("enabled", cand.enabled)
                        put("permission", cand.permission ?: "null")
                        put("status", "COMPONENT_SPEC_BUILT")
                        put("note", "not launched")
                    })
                } catch (e: Exception) {
                    errorsCount++
                    logDiagnosticError(runId, "target_assessment", spec.templateId.name, e)
                }
            }

            logEvent(JSONObject().apply {
                put("event", "template_end")
                put("run_id", runId)
                put("template_id", spec.templateId.name)
            })
        } catch (e: Exception) {
            errorsCount++
            logDiagnosticError(runId, "template_processing", spec.templateId.name, e)
        } finally {
            onProcessed(TemplateResults(candidatesCount, packageTargetsCount, componentSpecsCount, errorsCount))
        }
    }

    private data class TemplateResults(
        val candidatesCount: Int,
        val packageTargetsCount: Int,
        val componentSpecsCount: Int,
        val errorsCount: Int
    )

    private fun logStage(runId: String, stage: String) {
        logEvent(JSONObject().apply {
            put("event", "stage")
            put("run_id", runId)
            put("stage", stage)
        })
    }

    private fun logDiagnosticError(runId: String, stage: String, templateId: String, e: Exception) {
        logEvent(JSONObject().apply {
            put("event", "diagnostic_error")
            put("run_id", runId)
            put("stage", stage)
            put("template_id", templateId)
            put("error_class", e.javaClass.simpleName)
            put("error_message", e.message ?: "Unknown error")
        })
    }

    private fun logEvent(obj: JSONObject) {
        obj.put("schema", SCHEMA)
        val line = "IEI_JSON $obj"
        Log.i(TAG, line)
        logCallback?.invoke(line)
    }

    private fun createIntentSpecJson(runId: String, spec: IntentExperimentSpec): JSONObject {
        return JSONObject().apply {
            put("event", "intent_spec")
            put("run_id", runId)
            put("template_id", spec.templateId.name)
            put("action", spec.action)
            put("data", spec.dataUri ?: JSONObject.NULL)
            put("type", spec.mimeType ?: JSONObject.NULL)
            put("categories", JSONArray(spec.categories))
            put("extras", JSONArray())
        }
    }

    private fun createSpec(id: IntentTemplateId): IntentExperimentSpec {
        return when (id) {
            IntentTemplateId.VIEW_HTTPS_URL -> IntentExperimentSpec(id, "android.intent.action.VIEW", "https://example.com/", null, listOf("android.intent.category.DEFAULT"), emptyList(), TargetingMode.IMPLICIT, null, null)
            IntentTemplateId.SEND_TEXT_PLAIN -> IntentExperimentSpec(id, "android.intent.action.SEND", null, "text/plain", listOf("android.intent.category.DEFAULT"), emptyList(), TargetingMode.IMPLICIT, null, null)
        }
    }

    fun buildIntent(spec: IntentExperimentSpec): Intent {
        val intent = Intent(spec.action)
        spec.dataUri?.let { intent.data = Uri.parse(it) }
        spec.mimeType?.let { intent.type = it }
        for (category in spec.categories) {
            intent.addCategory(category)
        }
        for (extra in spec.extras) {
            intent.putExtra(extra.key, extra.previewValue)
        }

        when (spec.targetingMode) {
            TargetingMode.IMPLICIT -> {}
            TargetingMode.PACKAGE_TARGETED -> {
                spec.targetPackageName?.let { intent.setPackage(it) }
            }
            TargetingMode.COMPONENT_EXPLICIT -> {
                if (spec.targetPackageName != null && spec.targetActivityName != null) {
                    intent.component = ComponentName(spec.targetPackageName, spec.targetActivityName)
                }
            }
        }
        return intent
    }

    fun resolveCandidates(context: Context, spec: IntentExperimentSpec): List<ResolvedActivityCandidate> {
        val baseIntent = Intent(spec.action)
        spec.dataUri?.let { baseIntent.data = Uri.parse(it) }
        spec.mimeType?.let { baseIntent.type = it }
        
        val pm = context.packageManager
        val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val flags = PackageManager.ResolveInfoFlags.of((PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA).toLong())
            pm.queryIntentActivities(baseIntent, flags)
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(baseIntent, PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA)
        }

        return resolveInfoList.mapNotNull { resolveInfo ->
            resolveInfo.activityInfo?.let { activityInfo ->
                ResolvedActivityCandidate(
                    packageName = activityInfo.packageName,
                    activityName = activityInfo.name,
                    label = resolveInfo.loadLabel(pm).toString(),
                    exported = activityInfo.exported,
                    enabled = activityInfo.enabled,
                    permission = activityInfo.permission
                )
            }
        }.sortedWith(compareBy({ it.packageName }, { it.activityName }))
    }

    fun createSnapshot(
        context: Context,
        spec: IntentExperimentSpec,
        candidates: List<ResolvedActivityCandidate>,
        launchTests: List<LaunchTestRecord>
    ): IntentExperimentSnapshot {
        val pm = context.packageManager
        val pi = pm.getPackageInfo(context.packageName, 0)
        
        return IntentExperimentSnapshot(
            generatedAtEpochMillis = System.currentTimeMillis(),
            generatedAtIso8601 = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            appPackageName = context.packageName,
            appVersionName = pi.versionName,
            androidSdkInt = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE,
            targetSdkVersion = context.applicationInfo.targetSdkVersion,
            manifestVariant = "debug",
            spec = spec,
            candidates = candidates,
            launchTests = launchTests,
            contentHashShort = "abc"
        )
    }
}

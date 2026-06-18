package packagelist.android.moukaeritai.work

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest

class IntentExperimentViewModel : ViewModel() {
    private val repo = IntentExperimentRepository()

    // 1. Core Experiment Spec State
    private val _spec = MutableStateFlow(createDefaultSpec(IntentTemplateId.VIEW_HTTPS_URL))
    val spec: StateFlow<IntentExperimentSpec> = _spec.asStateFlow()

    // 2. Resolved Candidates State
    private val _candidates = MutableStateFlow<List<ResolvedActivityCandidate>>(emptyList())
    val candidates: StateFlow<List<ResolvedActivityCandidate>> = _candidates.asStateFlow()

    // 3. Launch Test Records State
    private val _launchTests = MutableStateFlow<List<LaunchTestRecord>>(
        listOf(
            LaunchTestRecord(TargetingMode.IMPLICIT, null, null, LaunchStatus.NOT_TESTED, null, null),
            LaunchTestRecord(TargetingMode.PACKAGE_TARGETED, null, null, LaunchStatus.NOT_TESTED, null, null),
            LaunchTestRecord(TargetingMode.COMPONENT_EXPLICIT, null, null, LaunchStatus.NOT_TESTED, null, null)
        )
    )
    val launchTests: StateFlow<List<LaunchTestRecord>> = _launchTests.asStateFlow()

    // 4. Selected Candidate State for Detail Screen
    private val _selectedCandidate = MutableStateFlow<ResolvedActivityCandidate?>(null)
    val selectedCandidate: StateFlow<ResolvedActivityCandidate?> = _selectedCandidate.asStateFlow()

    // 5. System Messages / Exports State
    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    // Status State
    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _logOutput = MutableStateFlow<List<String>>(emptyList())
    val logOutput: StateFlow<List<String>> = _logOutput.asStateFlow()

    private var hasAutoRunStarted = false
    private var isRunning = false
    
    fun scheduleAutoRun(context: Context) {
        if (hasAutoRunStarted) return
        hasAutoRunStarted = true
        _status.value = "Waiting to run diagnostics..."
        viewModelScope.launch {
            delay(2000)
            if (!isRunning) {
                runDiagnostics(context)
            }
        }
    }

    fun runDiagnostics(context: Context) {
        if (isRunning) return
        isRunning = true
        _logOutput.value = emptyList()
        val runId = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US).format(java.util.Date()) + "-" + (100000..999999).random()
        _status.value = "Running: $runId"
        viewModelScope.launch {
            try {
                repo.runDiagnostics(context, runId) { line ->
                    _logOutput.value = _logOutput.value + line
                }
                _status.value = "Completed: $runId"
            } catch (e: Exception) {
                _status.value = "Failed: $runId"
                Log.e("IntentExperiment", "Diagnostic run failed", e)
            } finally {
                isRunning = false
            }
        }
    }

    fun selectTemplate(templateId: IntentTemplateId) {
        _spec.value = createDefaultSpec(templateId)
        _selectedCandidate.value = null
        _candidates.value = emptyList()
        // Reset launch records
        _launchTests.value = listOf(
            LaunchTestRecord(TargetingMode.IMPLICIT, null, null, LaunchStatus.NOT_TESTED, null, null),
            LaunchTestRecord(TargetingMode.PACKAGE_TARGETED, null, null, LaunchStatus.NOT_TESTED, null, null),
            LaunchTestRecord(TargetingMode.COMPONENT_EXPLICIT, null, null, LaunchStatus.NOT_TESTED, null, null)
        )
    }

    fun setTargetingMode(mode: TargetingMode) {
        _spec.value = _spec.value.copy(targetingMode = mode)
    }

    fun selectCandidate(candidate: ResolvedActivityCandidate) {
        _selectedCandidate.value = candidate
        _spec.value = _spec.value.copy(
            targetPackageName = candidate.packageName,
            targetActivityName = candidate.activityName
        )
        
        // Dynamic update to package targeting lists
        updateLaunchTargetSpecs(candidate.packageName, candidate.activityName)
    }

    private fun updateLaunchTargetSpecs(pkg: String, activity: String) {
        val currentList = _launchTests.value
        _launchTests.value = currentList.map { record ->
            when (record.mode) {
                TargetingMode.PACKAGE_TARGETED -> record.copy(targetPackageName = pkg, targetActivityName = null)
                TargetingMode.COMPONENT_EXPLICIT -> record.copy(targetPackageName = pkg, targetActivityName = activity)
                else -> record
            }
        }
    }

    fun resolve(context: Context) {
        viewModelScope.launch {
            try {
                val resolved = repo.resolveCandidates(context, _spec.value)
                _candidates.value = resolved
                if (resolved.isNotEmpty() && _selectedCandidate.value == null) {
                    // Preselect first candidate as convenience
                    selectCandidate(resolved[0])
                }
            } catch (e: Exception) {
                Log.e("IntentExperimentVM", "Failed to resolve candidates", e)
            }
        }
    }

    fun runLaunchTest(context: Context, mode: TargetingMode) {
        val testSpec = _spec.value.copy(
            targetingMode = mode,
            targetPackageName = if (mode == TargetingMode.IMPLICIT) null else _spec.value.targetPackageName,
            targetActivityName = if (mode == TargetingMode.COMPONENT_EXPLICIT) _spec.value.targetActivityName else null
        )

        val intent = repo.buildIntent(testSpec)
        // Add new task flag so we can trigger from Application / Activity context reliably
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        var status = LaunchStatus.STARTED
        var errClass: String? = null
        var errMsg: String? = null

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            status = LaunchStatus.ACTIVITY_NOT_FOUND
            errClass = e.javaClass.simpleName
            errMsg = e.message ?: "No Activity found to handle Intent"
        } catch (e: SecurityException) {
            status = LaunchStatus.SECURITY_EXCEPTION
            errClass = e.javaClass.simpleName
            errMsg = e.message ?: "Permission verification/Exported validation failed"
        } catch (e: Exception) {
            status = LaunchStatus.OTHER_EXCEPTION
            errClass = e.javaClass.simpleName
            errMsg = e.message ?: "An unexpected error occurred"
        }

        // Update the specific record
        _launchTests.value = _launchTests.value.map { record ->
            if (record.mode == mode) {
                LaunchTestRecord(
                    mode = mode,
                    targetPackageName = testSpec.targetPackageName,
                    targetActivityName = testSpec.targetActivityName,
                    status = status,
                    errorClass = errClass,
                    errorMessage = errMsg
                )
            } else {
                record
            }
        }
    }

    fun clearExportMessage() {
        _exportMessage.value = null
    }

    fun getSnapshot(context: Context): IntentExperimentSnapshot {
        val snapshot = repo.createSnapshot(context, _spec.value, _candidates.value, _launchTests.value)
        val hashInput = "${snapshot.spec.templateId}-${snapshot.candidates.size}-${snapshot.launchTests.map { it.status }}"
        val hash = md5(hashInput).take(6)
        return snapshot.copy(contentHashShort = hash)
    }

    fun exportAsCsv(context: Context) {
        val snapshot = getSnapshot(context)
        val csv = generateCsvData(snapshot)
        _exportMessage.value = "CSV exported successfully:\n\n$csv"
    }

    fun exportAsJson(context: Context) {
        val snapshot = getSnapshot(context)
        val json = generateJsonData(snapshot)
        _exportMessage.value = "JSON exported successfully:\n\n$json"
    }

    private fun md5(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(input.toByteArray())
            bytes.joinToString("") { String.format("%02x", it) }
        } catch (e: Exception) {
            "000000"
        }
    }

    private fun generateCsvData(snapshot: IntentExperimentSnapshot): String {
        val sb = java.lang.StringBuilder()
        sb.append("generated_at_epoch_millis,generated_at_iso8601,app_package_name,android_sdk_int,target_sdk_version,template_id,action,data_uri,mime_type,targeting_mode,candidate_count,content_hash\n")
        sb.append("${snapshot.generatedAtEpochMillis},")
        sb.append("${snapshot.generatedAtIso8601},")
        sb.append("${snapshot.appPackageName},")
        sb.append("${snapshot.androidSdkInt},")
        sb.append("${snapshot.targetSdkVersion},")
        sb.append("${snapshot.spec.templateId},")
        sb.append("${snapshot.spec.action},")
        sb.append("${snapshot.spec.dataUri ?: ""},")
        sb.append("${snapshot.spec.mimeType ?: ""},")
        sb.append("${snapshot.spec.targetingMode},")
        sb.append("${snapshot.candidates.size},")
        sb.append("${snapshot.contentHashShort}\n")
        return sb.toString()
    }

    private fun generateJsonData(snapshot: IntentExperimentSnapshot): String {
        // Build raw JSON simply to avoid adding dependencies
        val candidatesJson = snapshot.candidates.joinToString(",", "[", "]") { cand ->
            """{"packageName":"${cand.packageName}","activityName":"${cand.activityName}","label":"${cand.label ?: ""}","exported":${cand.exported},"enabled":${cand.enabled}}"""
        }
        val recordsJson = snapshot.launchTests.joinToString(",", "[", "]") { rec ->
            """{"mode":"${rec.mode}","status":"${rec.status}","errorClass":"${rec.errorClass ?: ""}","errorMessage":"${rec.errorMessage ?: ""}"}"""
        }
        return """{
  "generatedAtIso8601": "${snapshot.generatedAtIso8601}",
  "appPackageName": "${snapshot.appPackageName}",
  "androidSdkInt": ${snapshot.androidSdkInt},
  "targetSdkVersion": ${snapshot.targetSdkVersion},
  "templateId": "${snapshot.spec.templateId}",
  "action": "${snapshot.spec.action}",
  "dataUri": "${snapshot.spec.dataUri ?: ""}",
  "mimeType": "${snapshot.spec.mimeType ?: ""}",
  "targetingMode": "${snapshot.spec.targetingMode}",
  "candidates": $candidatesJson,
  "launchTests": $recordsJson,
  "contentHash": "${snapshot.contentHashShort}"
}"""
    }

    private fun createDefaultSpec(templateId: IntentTemplateId): IntentExperimentSpec {
        return when (templateId) {
            IntentTemplateId.VIEW_HTTPS_URL -> {
                IntentExperimentSpec(
                    templateId = IntentTemplateId.VIEW_HTTPS_URL,
                    action = "android.intent.action.VIEW",
                    dataUri = "https://example.com/",
                    mimeType = null,
                    categories = emptyList(),
                    extras = emptyList(),
                    targetingMode = TargetingMode.IMPLICIT,
                    targetPackageName = null,
                    targetActivityName = null
                )
            }
            IntentTemplateId.SEND_TEXT_PLAIN -> {
                IntentExperimentSpec(
                    templateId = IntentTemplateId.SEND_TEXT_PLAIN,
                    action = "android.intent.action.SEND",
                    dataUri = null,
                    mimeType = "text/plain",
                    categories = emptyList(),
                    extras = listOf(
                        IntentExtraSpec("android.intent.extra.TEXT", "String", "https://example.com/"),
                        IntentExtraSpec("android.intent.extra.SUBJECT", "String", "Intent experiment")
                    ),
                    targetingMode = TargetingMode.IMPLICIT,
                    targetPackageName = null,
                    targetActivityName = null
                )
            }
        }
    }
}


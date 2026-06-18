package packagelist.android.moukaeritai.work

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IntentExperimentScreen(viewModel: IntentExperimentViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.saveReportToFile(context, uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Intent Surface Explorer",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Text(
            text = "App ${BuildConfig.APP_VERSION_NAME} (code ${BuildConfig.APP_VERSION_CODE}) schema ${BuildConfig.JSON_REPORT_SCHEMA_VERSION}",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { 
                    viewModel.runDiagnostics(context, andSave = true) { suggestedFileName ->
                        createDocumentLauncher.launch(suggestedFileName)
                    }
                },
                enabled = state.status != ExportStatus.RUNNING && state.status != ExportStatus.EXPORTING,
                modifier = Modifier.weight(1f).height(48.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                Text("Run & Save", fontSize = 14.sp)
            }
            
            Button(
                onClick = { 
                    state.fileName?.let { createDocumentLauncher.launch(it) }
                },
                enabled = state.status != ExportStatus.RUNNING &&
                        state.status != ExportStatus.EXPORTING &&
                        state.isInternalFileAvailable &&
                        state.internalPath != null,
                modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF009900)),
                contentPadding = PaddingValues(4.dp)
            ) {
                Text("Export Last", fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Status 
        Text("Status: ${state.status.name}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0057D9))
        Text("Stage: ${state.progressStage}", fontSize = 12.sp)
        
        if (state.fileName != null) {
            Text("File: ${state.fileName}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Internal: ${if (state.internalSaveSuccess) "OK" else "Pending/Fail"}", fontSize = 12.sp)
            Text("Export: ${if (state.externalExportSuccess) "OK" else "Pending/Fail"}", fontSize = 12.sp)
            Text("Size: ${state.jsonByteSize} B", fontSize = 12.sp)
        }

        if (state.lastError != null) {
            Text("Error: ${state.lastError}", color = Color.Red, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        state.summaryMetrics?.let { metrics ->
            Text("Summary Metrics", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    MetricRow("Schema", state.schemaVersion)
                    MetricRow("Validation", state.validationStatus)
                    MetricRow("Catalog Cands", state.catalogCandidateCount)
                    MetricRow("Families", metrics.probe_family_count)
                    MetricRow("Probes", metrics.probe_count)
                    MetricRow("Implicit Cands", metrics.total_candidate_rows)
                    MetricRow("Pkg Target OK", metrics.package_target_resolvable_direct_count + metrics.package_target_resolvable_via_resolver_count)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    MetricRow("Val Errors", state.validationErrorCount)
                    MetricRow("Launch Tested", metrics.invocation_mode_summary?.component_explicit_launch_tested_count ?: 0)
                    MetricRow("Comp Spec Built", metrics.component_spec_built_count)
                    MetricRow("Unique Comps", metrics.unique_component_count)
                    MetricRow("Unique Pkgs", metrics.unique_package_count)
                    MetricRow("Disabled", metrics.component_disabled_count)
                    MetricRow("Not Exported", metrics.component_not_exported_count)
                    MetricRow("Needs Perm", metrics.component_requires_permission_count)
                    MetricRow("Errors", metrics.error_count)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Top Families", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            val sortedFamilies = metrics.candidates_by_family.entries.sortedByDescending { it.value }.take(5)
            sortedFamilies.forEach { (family, count) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(family, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(count.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: Any) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp)
        Text(value.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}


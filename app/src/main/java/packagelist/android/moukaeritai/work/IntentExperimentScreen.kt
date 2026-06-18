package packagelist.android.moukaeritai.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IntentExperimentScreen(viewModel: IntentExperimentViewModel) {
    val status by viewModel.status.collectAsState()
    val logOutput by viewModel.logOutput.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.scheduleAutoRun(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFFFF))
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Intent Experiment Inspector", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Diagnostics will run automatically. Copy IEI_JSON lines from Logcat.", fontSize = 14.sp, color = Color.Gray, textAlign = TextAlign.Center)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { viewModel.runDiagnostics(context) },
                enabled = !status.startsWith("Running:"),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0057D9))
            ) {
                Text("Run Intent Diagnostics", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Status: $status", fontSize = 14.sp, color = Color.DarkGray, textAlign = TextAlign.Center)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFFF0F0F0))
                    .padding(8.dp)
            ) {
                items(logOutput) { logLine ->
                    Text(logLine, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        }
    }
}

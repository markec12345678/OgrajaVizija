package si.ograjavizija.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import si.ograjavizija.app.data.AppState
import si.ograjavizija.app.network.ApiClient
import si.ograjavizija.app.segmentation.OnDeviceSegmenter
import si.ograjavizija.app.ui.components.StepHeader
import si.ograjavizija.app.ui.theme.Accent
import si.ograjavizija.app.ui.theme.Bad
import si.ograjavizija.app.ui.theme.Muted

/** Nastavitve: naslov lastnega strežnika, preverjanje, stanje modelov na napravi. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(AppState.serverUrl) }
    var health by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        StepHeader(0, "Nastavitve", onBack)
        Text(
            "🟢 Brez strežnika aplikacija dela vse lokalno (geometrija, barve, senca, shranjevanje).\n" +
                "🟡 Lasten strežnik (backend/ v tem repoju) doda: LaMa odstranitev, BiRefNet izrez, SAM segmentacijo in AI finalizacijo (FLUX.2 klein 4B / Qwen-Image-Edit).\n" +
                "🔴 Zunanji plačljivi API ni potreben in ni privzet.",
            color = Muted, style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text("Naslov strežnika, npr. http://192.168.1.20:8787") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            AppState.updateServerUrl(url)
            scope.launch {
                health = runCatching {
                    val h = ApiClient.health(AppState.serverUrl)
                    "✅ povezano: v${h.version} · ${h.device} · GPU ${h.gpu} · providerji: ${h.providers.joinToString()}"
                }.getOrElse { e -> "⚠️ ${e.message}" }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Preveri povezavo") }
        if (health.isNotEmpty()) Text(health, color = if (health.startsWith("✅")) Accent else Bad,
            style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(16.dp))
        Text("Modeli na napravi", style = MaterialTheme.typography.titleSmall, color = Muted)
        Text(
            if (OnDeviceSegmenter.modelReady) "✅ MediaPipe Interactive Segmenter: naložen"
            else "⏳ MediaPipe model še ni prenesen (prenese se ob prvem tapu v koraku 3, ~350 kB–5 MB)",
            color = Muted, style = MaterialTheme.typography.bodySmall,
        )
    }
}

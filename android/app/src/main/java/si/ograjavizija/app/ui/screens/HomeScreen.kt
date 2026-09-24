package si.ograjavizija.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import si.ograjavizija.app.data.AppState
import si.ograjavizija.app.data.Project
import si.ograjavizija.app.data.ProjectController
import si.ograjavizija.app.data.ProjectStore
import si.ograjavizija.app.ui.theme.Accent
import si.ograjavizija.app.ui.theme.Muted
import si.ograjavizija.app.ui.theme.Surface
import si.ograjavizija.app.ui.theme.SurfaceAlt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(onNew: (String) -> Unit, onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var projects by remember { mutableStateOf(ProjectStore.list()) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏗 OgrajaVizija", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFE8EAF0))
            Spacer(Modifier.fillMaxWidth().weight(1f))
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Nastavitve", tint = Muted) }
        }
        Text(
            "Fotografiraj balkon → fotografiraj svojo ograjo → realistična vizualizacija. " +
                "Brez naročnine, brez računa, brez oblaka (razen če vklopiš lasten strežnik).",
            style = MaterialTheme.typography.bodySmall, color = Muted,
        )
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Ime projekta (npr. Balkon Marko)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                scope.launch {
                    val p = ProjectController.createProject(name)
                    AppState.setProject(p)
                    projects = ProjectStore.list()
                    onNew(p.id)
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Default.Add, null); Text("  Nova vizualizacija", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(18.dp))
        Text("Shranjeni projekti", style = MaterialTheme.typography.titleSmall, color = Muted)
        Spacer(Modifier.height(6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(projects, key = { it.id }) { p ->
                ProjectRow(p, onOpen = { onOpen(p.id) }, onDelete = {
                    ProjectStore.delete(p); projects = ProjectStore.list()
                })
            }
        }
    }
}

@Composable
private fun ProjectRow(p: Project, onOpen: () -> Unit, onDelete: () -> Unit) {
    val fmt = remember { SimpleDateFormat("d. M. yyyy HH:mm", Locale.getDefault()) }
    Row(
        Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, color = Color(0xFFE8EAF0))
            Text(
                "${fmt.format(Date(p.updatedAt))} · ${p.variants.size} variant · " +
                    (if (p.scene != null) "📷 balkon" else "⚠️ brez fotografije"),
                style = MaterialTheme.typography.labelSmall, color = Muted,
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Izbriši", tint = Muted) }
    }
}

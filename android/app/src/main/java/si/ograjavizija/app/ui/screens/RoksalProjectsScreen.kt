package si.ograjavizija.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import si.ograjavizija.app.data.Project
import si.ograjavizija.app.data.ProjectStatus
import si.ograjavizija.app.data.ProjectStore
import si.ograjavizija.app.ui.components.StepHeader
import si.ograjavizija.app.ui.theme.Muted
import si.ograjavizija.app.ui.theme.SurfaceAlt
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun RoksalProjectsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf(ProjectStore.list()) }
    var filter by remember { mutableStateOf<ProjectStatus?>(null) }

    val visible = projects.filter { filter == null || it.status == filter }

    Column(Modifier.fillMaxSize()) {
        StepHeader(0, "Roksal · interni projekti", onBack)
        Text(
            "Lokalni pregled projektov. Podatki so shranjeni na tej napravi; status lahko spremeniš med pregledom.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Vsi") })
            listOf(
                ProjectStatus.DRAFT,
                ProjectStatus.CONFIGURED,
                ProjectStatus.VISUALIZED,
                ProjectStatus.QUOTE_PREPARED,
                ProjectStatus.QUOTE_REQUESTED,
                ProjectStatus.ROKSAL_REVIEW,
                ProjectStatus.SITE_MEASUREMENT,
                ProjectStatus.OFFER_SENT,
                ProjectStatus.ACCEPTED,
                ProjectStatus.INSTALLATION,
                ProjectStatus.COMPLETED
            ).forEach { s ->
                FilterChip(
                    selected = filter == s,
                    onClick = { filter = s },
                    label = { Text(statusLabel(s)) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(visible, key = { it.id }) { project ->
                ProjectAdminRow(project) {
                    projects = ProjectStore.list()
                }
            }
        }
    }
}

@Composable
private fun ProjectAdminRow(project: Project, onChanged: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var expanded by remember(project.id) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceAlt,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(project.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "ID " + project.id + " · " + (project.config?.category?.name ?: "brez kategorije") +
                    " · " + (project.config?.profileId ?: "brez profila"),
                color = Muted,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "Stranka: " + (project.config?.customerName?.ifBlank { "ni vneseno" } ?: "ni vneseno") +
                    " · " + (project.config?.phone?.ifBlank { "brez telefona" } ?: "brez telefona"),
                color = Muted,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(6.dp))
            Row {
                FilterChip(
                    selected = true,
                    onClick = { expanded = true },
                    label = { Text(statusLabel(project.status)) }
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    ProjectStatus.entries.forEach { next ->
                        DropdownMenuItem(
                            text = { Text(statusLabel(next)) },
                            onClick = {
                                expanded = false
                                scope.launch {
                                    ProjectStore.save(project.copy(status = next))
                                    onChanged()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: ProjectStatus): String = when (status) {
    ProjectStatus.DRAFT -> "Osnutek"
    ProjectStatus.CONFIGURED -> "Konfigurirano"
    ProjectStatus.QUOTE_PREPARED -> "Povpraševanje pripravljeno"
    ProjectStatus.VISUALIZED -> "Vizualizirano"
    ProjectStatus.QUOTE_REQUESTED -> "Povpraševanje"
    ProjectStatus.ROKSAL_REVIEW -> "Roksal pregled"
    ProjectStatus.SITE_MEASUREMENT -> "Izmera"
    ProjectStatus.OFFER_SENT -> "Ponudba"
    ProjectStatus.ACCEPTED -> "Sprejeto"
    ProjectStatus.INSTALLATION -> "Montaža"
    ProjectStatus.COMPLETED -> "Zaključeno"
    ProjectStatus.CANCELLED -> "Preklicano"
}

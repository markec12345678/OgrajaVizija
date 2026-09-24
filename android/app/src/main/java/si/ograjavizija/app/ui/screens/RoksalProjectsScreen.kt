package si.ograjavizija.app.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import si.ograjavizija.app.data.AppState
import si.ograjavizija.app.data.Project
import si.ograjavizija.app.data.ProjectStatus
import si.ograjavizija.app.data.ProjectStore
import si.ograjavizija.app.network.ApiClient
import si.ograjavizija.app.ui.components.StepHeader
import si.ograjavizija.app.ui.theme.Bad
import si.ograjavizija.app.ui.theme.Muted
import si.ograjavizija.app.ui.theme.SurfaceAlt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RoksalProjectsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf(ProjectStore.list()) }
    var filter by remember { mutableStateOf<ProjectStatus?>(null) }
    var remoteMode by remember { mutableStateOf(false) }
    var remoteBusy by remember { mutableStateOf(false) }
    var remoteMessage by remember { mutableStateOf("") }
    var remoteInquiries by remember { mutableStateOf<List<ApiClient.InquirySummary>>(emptyList()) }

    fun refreshLocal() {
        projects = ProjectStore.list()
    }

    fun refreshRemote() {
        if (AppState.serverUrl.isBlank() || AppState.inquiryToken.isBlank()) {
            remoteMessage = "Za lastni inbox nastavi URL strežnika in token v Nastavitvah."
            return
        }
        remoteBusy = true
        remoteMessage = ""
        scope.launch {
            runCatching {
                ApiClient.listInquiries(AppState.serverUrl, AppState.inquiryToken)
            }.onSuccess {
                remoteInquiries = it
                remoteMessage = "✅ Osveženo · " + it.size + " povpraševanj"
            }.onFailure {
                remoteMessage = "⚠️ Inbox: " + (it.message ?: "ni mogoče povezati")
            }
            remoteBusy = false
        }
    }

    LaunchedEffect(remoteMode) {
        if (remoteMode) refreshRemote()
    }

    val visible = projects.filter { filter == null || it.status == filter }

    Column(Modifier.fillMaxSize()) {
        StepHeader(0, "Roksal · projekti", onBack)
        Text(
            if (remoteMode)
                "Lastni backend inbox. Povpraševanja so zaščitena z bearer tokenom in shranjena na tvojem strežniku."
            else
                "Lokalni pregled projektov. Podatki so shranjeni na tej napravi.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !remoteMode,
                onClick = { remoteMode = false },
                label = { Text("Na napravi") }
            )
            FilterChip(
                selected = remoteMode,
                onClick = { remoteMode = true },
                label = { Text("Lastni inbox") }
            )
        }
        Spacer(Modifier.height(10.dp))

        if (!remoteMode) {
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
                    ProjectAdminRow(project) { refreshLocal() }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    enabled = !remoteBusy,
                    onClick = { refreshRemote() },
                    modifier = Modifier.weight(1f)
                ) { Text(if (remoteBusy) "Osvežujem…" else "↻ Osveži inbox") }
                OutlinedButton(
                    enabled = AppState.serverUrl.isNotBlank() && AppState.inquiryToken.isNotBlank(),
                    onClick = {
                        AppState.updateServerUrl(AppState.serverUrl)
                        remoteMessage = "Token je shranjen v Nastavitvah."
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Nastavitve") }
            }
            if (remoteMessage.isNotEmpty()) {
                Text(
                    remoteMessage,
                    color = if (remoteMessage.startsWith("✅")) Muted else Bad,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            if (AppState.serverUrl.isBlank() || AppState.inquiryToken.isBlank()) {
                Text(
                    "Lastni inbox je zaščiten in privzeto izklopljen. Nastavi backend URL + OVIZ_INQUIRY_TOKEN v Nastavitvah.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(remoteInquiries, key = { it.id }) { inquiry ->
                        RemoteInquiryRow(inquiry) { refreshRemote() }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectAdminRow(project: Project, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
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

@Composable
private fun RemoteInquiryRow(
    inquiry: ApiClient.InquirySummary,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val fmt = remember { SimpleDateFormat("d. M. yyyy HH:mm", Locale.getDefault()) }
    var expanded by remember(inquiry.id) { mutableStateOf(false) }
    var details by remember(inquiry.id) { mutableStateOf<ApiClient.InquiryDetail?>(null) }
    var statusMenu by remember(inquiry.id) { mutableStateOf(false) }
    var busy by remember(inquiry.id) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceAlt,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                inquiry.projectName.ifBlank { inquiry.id },
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                (inquiry.customerName.ifBlank { "Stranka ni navedena" }) +
                    " · " + inquiry.category.ifBlank { "brez kategorije" },
                color = Muted,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                fmt.format(Date(inquiry.receivedAt)) +
                    " · " + inquiry.attachmentsLabel(),
                color = Muted,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = true,
                    onClick = { statusMenu = true },
                    label = { Text(remoteStatusLabel(inquiry.status)) }
                )
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        if (details == null) {
                            busy = true
                            scope.launch {
                                runCatching {
                                    ApiClient.getInquiry(AppState.serverUrl, AppState.inquiryToken, inquiry.id)
                                }.onSuccess { details = it }
                                    .onFailure { details = ApiClient.InquiryDetail(inquiryText = "⚠️ " + (it.message ?: "Napaka")) }
                                busy = false
                                expanded = true
                            }
                        } else {
                            expanded = !expanded
                        }
                    }
                ) { Text(if (busy) "…" else if (expanded) "Skrij" else "Podrobnosti") }
            }
            DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                listOf(
                    "NEW",
                    "ROKSAL_REVIEW",
                    "SITE_MEASUREMENT",
                    "OFFER_SENT",
                    "ACCEPTED",
                    "INSTALLATION",
                    "COMPLETED",
                    "CANCELLED"
                ).forEach { next ->
                    DropdownMenuItem(
                        text = { Text(remoteStatusLabel(next)) },
                        onClick = {
                            statusMenu = false
                            busy = true
                            scope.launch {
                                runCatching {
                                    ApiClient.updateInquiryStatus(
                                        AppState.serverUrl,
                                        AppState.inquiryToken,
                                        inquiry.id,
                                        next
                                    )
                                }.onSuccess { onChanged() }
                                busy = false
                            }
                        }
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                details?.let { d ->
                    Text("Priponke: " + d.attachments.joinToString().ifBlank { "brez" }, color = Muted, style = MaterialTheme.typography.labelSmall)
                    Text(d.inquiryText, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun ApiClient.InquirySummary.attachmentsLabel(): String =
    if (attachmentBytes > 0L) "📎 " + "%.1f".format(Locale.US, attachmentBytes / 1024f / 1024f) + " MB"
    else "brez priponk"

private fun statusLabel(status: ProjectStatus): String = when (status) {
    ProjectStatus.DRAFT -> "Osnutek"
    ProjectStatus.CONFIGURED -> "Konfigurirano"
    ProjectStatus.VISUALIZED -> "Vizualizirano"
    ProjectStatus.QUOTE_PREPARED -> "Povpraševanje pripravljeno"
    ProjectStatus.QUOTE_REQUESTED -> "Povpraševanje"
    ProjectStatus.ROKSAL_REVIEW -> "Roksal pregled"
    ProjectStatus.SITE_MEASUREMENT -> "Izmera"
    ProjectStatus.OFFER_SENT -> "Ponudba"
    ProjectStatus.ACCEPTED -> "Sprejeto"
    ProjectStatus.INSTALLATION -> "Montaža"
    ProjectStatus.COMPLETED -> "Zaključeno"
    ProjectStatus.CANCELLED -> "Preklicano"
}

private fun remoteStatusLabel(status: String): String = when (status) {
    "NEW" -> "Novo"
    "ROKSAL_REVIEW" -> "Roksal pregled"
    "SITE_MEASUREMENT" -> "Izmera"
    "OFFER_SENT" -> "Ponudba"
    "ACCEPTED" -> "Sprejeto"
    "INSTALLATION" -> "Montaža"
    "COMPLETED" -> "Zaključeno"
    "CANCELLED" -> "Preklicano"
    else -> status.ifBlank { "Brez statusa" }
}

package si.ograjavizija.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import si.ograjavizija.app.data.AppState
import si.ograjavizija.app.data.Project
import si.ograjavizija.app.data.ProjectStore
import si.ograjavizija.app.data.ProjectStatus
import si.ograjavizija.app.roksal.RoksalCatalog
import si.ograjavizija.app.ui.components.StepHeader
import si.ograjavizija.app.ui.theme.Muted
import si.ograjavizija.app.ui.theme.Warn

@Composable
fun RoksalQuoteScreen(
    projectId: String?,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf<Project?>(null) }

    LaunchedEffect(projectId) {
        project = projectId?.let { ProjectStore.load(it) } ?: AppState.currentProject
    }

    val p = project
    val c = p?.config
    val profile = c?.let { RoksalCatalog.profile(it.profileId) }
    val estimate = c?.let { RoksalCatalog.estimate(it) }

    val inquiry = remember(p, c, profile, estimate) {
        buildString {
            appendLine("ROKSAL POVPRAŠEVANJE")
            appendLine("Projekt: " + (p?.name ?: ""))
            appendLine("Projekt ID: " + (p?.id ?: ""))
            appendLine()
            appendLine("Kategorija: " + (c?.category?.name ?: "OGRAJA"))
            appendLine("Smer: " + (c?.orientation?.name ?: "POKONCNA"))
            appendLine("Profil: " + (profile?.name ?: ""))
            appendLine("Dimenzija profila: " + (profile?.dimensions ?: ""))
            appendLine("Barva: " + (c?.let { RoksalCatalog.colour(it.colourId).name } ?: ""))
            if (!c?.surfaceId.isNullOrBlank()) appendLine("Površina: " + c?.surfaceId)
            appendLine("Razmak desk: " + (c?.boardGapMm ?: 0) + " mm")
            appendLine("Dolžina: " + (c?.lengthM ?: 0f) + " m")
            appendLine("Višina: " + (c?.heightM ?: 0f) + " m")
            appendLine("Razmak stebrov: " + (c?.postSpacingCm ?: 0f) + " cm")
            appendLine("Razmak nosilcev: " + (c?.supportSpacingCm ?: 0f) + " cm")
            appendLine("Vrata: " + (c?.gateType ?: "BREZ"))
            if (c?.gateType != "BREZ") {
                appendLine("Mere vrat: " + c?.gateWidthM + " × " + c?.gateHeightM + " m")
            }
            appendLine("Konstrukcija: " + (c?.existingStructure?.name ?: "NEVEM"))
            appendLine("Status meritev: " + (c?.measurementStatus?.name ?: "OCENA"))
            appendLine("Zgornji ročaj: " + if (c?.handleIncluded == true) "DA" else "NE")
            appendLine("Način določitve mer: " + (c?.measurementMethod?.name ?: "ZNANE_MERE"))
            appendLine("Dostava: " + (c?.deliveryPreference?.name ?: "NEVEM"))
            appendLine()
            appendLine("STRANKA")
            appendLine("Ime/podjetje: " + (c?.customerName ?: ""))
            appendLine("Telefon: " + (c?.phone ?: ""))
            appendLine("E-pošta: " + (c?.email ?: ""))
            appendLine("Lokacija: " + (c?.address ?: ""))
            appendLine()
            if (estimate != null) {
                appendLine("INFORMATIVNI MATERIALNI IZRAČUN")
                appendLine("Deske: " + estimate.boards)
                appendLine("Standardna dolžina: " + estimate.stockLengthM + " m")
                appendLine("Stebri: " + estimate.posts)
                appendLine("Nosilci/povezave: " + estimate.supports)
                appendLine("Vijaki: " + estimate.screws)
                appendLine("Ročaji: " + estimate.handles)
                appendLine("Predviden odpad: ~" + estimate.estimatedWastePercent + "%")
                if (estimate.components.isNotEmpty()) {
                    appendLine("Predlagane komponente:")
                    estimate.components.forEach { component -> appendLine("- " + component.name + " (" + component.unit + ")") }
                }
            }
            appendLine()
            appendLine("Opombe: " + (c?.notes ?: ""))
            appendLine()
            appendLine("Meritve in materialni izračun so informativni.")
            appendLine("Končno ponudbo in tehnično izvedbo potrdi Roksal.")
        }
    }

    Column(Modifier.fillMaxSize()) {
        StepHeader(6, "6 · Roksal povpraševanje", onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Text("Projekt je pripravljen", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Preveri podatke. Nato jih lahko pošlješ iz telefona po e-pošti ali v drugi aplikaciji.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(14.dp))
            Text(inquiry, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(14.dp))
            Text(
                "Cena ni prikazana, ker v aplikacijo še ni vnesen dejanski Roksal cenik.",
                color = Warn,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val current = p
                    if (current != null) {
                        scope.launch {
                            project = ProjectStore.save(current.copy(status = ProjectStatus.QUOTE_REQUESTED))
                        }
                    }
                    val imageUris = p?.let { project ->
                        listOf("original.jpg", "result.jpg")
                            .map { ProjectStore.file(project, it) }
                            .filter { it.exists() }
                            .map { file ->
                                FileProvider.getUriForFile(
                                    context,
                                    context.packageName + ".fileprovider",
                                    file
                                )
                            }
                    }.orEmpty()

                    val intent = if (imageUris.isNotEmpty()) {
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "image/*"
                            putExtra(Intent.EXTRA_SUBJECT, "Roksal povpraševanje · " + (p?.name ?: "projekt"))
                            putExtra(Intent.EXTRA_TEXT, inquiry)
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(imageUris))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    } else {
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Roksal povpraševanje · " + (p?.name ?: "projekt"))
                            putExtra(Intent.EXTRA_TEXT, inquiry)
                        }
                    }
                    context.startActivity(Intent.createChooser(intent, "Pošlji povpraševanje"))
                },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("Pošlji Roksalu")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
                Text("Nazaj na domov")
            }
        }
    }
}

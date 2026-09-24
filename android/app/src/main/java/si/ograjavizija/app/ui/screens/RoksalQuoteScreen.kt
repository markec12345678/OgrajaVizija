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
import si.ograjavizija.app.data.RoksalCategory
import si.ograjavizija.app.roksal.RoksalCatalog
import si.ograjavizija.app.ui.components.StepHeader
import si.ograjavizija.app.ui.theme.Muted
import si.ograjavizija.app.ui.theme.Warn

private fun roksalEnquiryUrl(category: RoksalCategory): String = when (category) {
    RoksalCategory.OGRAJA -> "https://roksal.com/wpc-povprasevanje/povprasevanje-wpc-ograje/"
    RoksalCategory.TERASA -> "https://roksal.com/wpc-povprasevanje/povprasevanje-wpc-terasa/"
    RoksalCategory.FASADA -> "https://roksal.com/wpc-povprasevanje/povprasevanje-wpc-fasade/"
    RoksalCategory.PREGRADNA_STENA -> "https://roksal.com/wpc-povprasevanje/povprasevanje-pregradna-stena/"
    RoksalCategory.NAPUSC -> "https://roksal.com/wpc-povprasevanje/"
}

@Composable
private fun candidateAttachmentNote(project: Project): String? {
    val files = listOf("original.jpg", "result.jpg", "product.jpg")
        .map { ProjectStore.file(project, it) }
        .filter { it.exists() }
    return if (files.sumOf { it.length() } > 25L * 1024L * 1024L)
        "Skupna velikost fotografij presega 25 MB, zato jih aplikacija pri deljenju ne bo pripela. Pošlji jih Roksalu ločeno."
    else null
}

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
    val readyForInquiry = c?.let {
        it.customerName.isNotBlank() &&
            it.phone.isNotBlank() &&
            it.email.isNotBlank() &&
            it.invoiceAddress.isNotBlank() &&
            it.invoicePostalCode.isNotBlank() &&
            it.invoiceCity.isNotBlank() &&
            it.address.isNotBlank() &&
            it.projectPostalCode.isNotBlank() &&
            it.projectCity.isNotBlank() &&
            (it.customerType != si.ograjavizija.app.data.CustomerType.PODJETJE ||
                (it.companyName.isNotBlank() && it.taxNumber.isNotBlank())) &&
            it.dataProcessingConsent &&
            it.termsAccepted
    } == true

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
            appendLine("Katalogni vir: " + (profile?.catalogUrl ?: "https://roksal.com/woodcore-wpc-deske/"))
            appendLine("Barva: " + (c?.let { RoksalCatalog.colour(it.colourId).name } ?: ""))
            if (!c?.mountingVariant.isNullOrBlank()) appendLine("Način polaganja: " + c?.mountingVariant)
            if (!c?.surfaceId.isNullOrBlank()) appendLine("Površina: " + c?.surfaceId)
            appendLine("Razmak desk: " + (c?.boardGapMm ?: 0) + " mm")
            appendLine("Dolžina: " + (c?.lengthM ?: 0f) + " m")
            appendLine("Višina: " + (c?.heightM ?: 0f) + " m")
            appendLine("Razmak stebrov: " + (c?.postSpacingCm ?: 0f) + " cm")
            appendLine("Razmak nosilcev: " + (c?.supportSpacingCm ?: 0f) + " cm")
            appendLine("Število nosilcev po višini: " + (c?.supportCountByHeight ?: ""))
            if (c?.category == RoksalCategory.TERASA) {
                appendLine("Širina terase: " + (c?.terraceWidthM ?: 0f) + " m")
                appendLine("Padec: " + (c?.terraceSlopeCmPerM ?: 0f) + " cm/m")
                appendLine("Končna višina: " + (c?.terraceHeightCm ?: 0f) + " cm")
                appendLine("Podlaga: " + (c?.terraceBase?.name ?: "NEVEM"))
                appendLine("Podkonstrukcija: " + (c?.terraceSubstructure?.name ?: "NEVEM"))
                appendLine("Smer desk: " + (c?.terraceDirection?.name ?: "NEVEM"))
                appendLine("Vijačenje v podlago: " + if (c?.terraceScrewToBase == true) "DA" else "NE")
            }
            if (c?.category == RoksalCategory.FASADA) {
                appendLine("Razpored fasade: " + (c?.facadeLayout?.name ?: "NEVEM"))
                appendLine("Okna/vrata: " + (c?.facadeOpeningNotes ?: ""))
                appendLine("KUBO ojačitev: " + (c?.kuboReinforcement?.name ?: "NEVEM"))
            }
            appendLine("Vrata: " + (c?.gateType ?: "BREZ"))
            if (c?.gateType != "BREZ") {
                appendLine("Mere vrat: " + c?.gateWidthM + " × " + c?.gateHeightM + " m")
            }
            appendLine("Konstrukcija: " + (c?.existingStructure?.name ?: "NEVEM"))
            appendLine("Status meritev: " + (c?.measurementStatus?.name ?: "OCENA"))
            appendLine("Zgornji ročaj: " + if (c?.handleIncluded == true) "DA" else "NE")
            appendLine("Način določitve mer: " + (c?.measurementMethod?.name ?: "ZNANE_MERE"))
            appendLine("Dostava: " + (c?.deliveryPreference?.name ?: "NEVEM"))
            appendLine("Zahtevan razrez: " + if (c?.cuttingRequested == true) "DA" else "NE")
            appendLine("Tip ograje: " + (c?.fenceType?.name ?: "NEVEM"))
            appendLine("Pritrditev stebrov: " + (c?.postFixing?.name ?: "NEVEM"))
            appendLine("Izgled stebrov: " + (c?.postAppearance?.name ?: "NEVEM"))
            appendLine()
            appendLine("STRANKA")
            appendLine("Tip stranke: " + (c?.customerType?.name ?: "FIZICNA_OSEBA"))
            appendLine("Ime in priimek: " + (c?.customerName ?: ""))
            appendLine("Podjetje: " + (c?.companyName ?: ""))
            appendLine("Davčna številka: " + (c?.taxNumber ?: ""))
            appendLine("Telefon: " + (c?.phone ?: ""))
            appendLine("E-pošta: " + (c?.email ?: ""))
            appendLine("Naslov za račun: " + (c?.invoiceAddress ?: "") + ", " + (c?.invoicePostalCode ?: "") + " " + (c?.invoiceCity ?: ""))
            appendLine("Lokacija projekta: " + (c?.address ?: "") + ", " + (c?.projectPostalCode ?: "") + " " + (c?.projectCity ?: ""))
            appendLine("Dostava na drug naslov: " + if (c?.deliveryAddressDifferent == true) "DA" else "NE")
            if (c?.deliveryAddressDifferent == true) appendLine("Naslov dostave: " + c?.deliveryAddress + ", " + c?.deliveryPostalCode + " " + c?.deliveryCity)
            appendLine("Soglasje za obdelavo: " + if (c?.dataProcessingConsent == true) "DA" else "NE")
            appendLine("Sprejeti pogoji: " + if (c?.termsAccepted == true) "DA" else "NE")
            appendLine("E-novice: " + if (c?.newsletterOptIn == true) "DA" else "NE")
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
            Text(
                if (readyForInquiry) "✅ Podatki za povpraševanje so izpolnjeni." else "⚠️ Pred oddajo dopolni obvezne podatke in potrdi obdelavo podatkov ter splošne pogoje.",
                color = if (readyForInquiry) Muted else Warn,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Preveri podatke. Nato jih lahko pošlješ iz telefona po e-pošti ali v drugi aplikaciji.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(14.dp))
            Text(inquiry, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(14.dp))
            if (p != null && p.config?.let { it.notes.isNotBlank() } == true && false) Unit
            Text(
                "Cena ni prikazana, ker v aplikacijo še ni vnesen dejanski Roksal cenik.",
                color = Warn,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(10.dp))
            Button(
                enabled = readyForInquiry,
                onClick = {
                    val current = p
                    if (current != null) {
                        scope.launch {
                            project = ProjectStore.save(current.copy(status = ProjectStatus.QUOTE_REQUESTED))
                        }
                    }
                    val candidateFiles = p?.let { project ->
                        listOf("original.jpg", "result.jpg")
                            .map { ProjectStore.file(project, it) }
                            .filter { it.exists() }
                    }.orEmpty()
                    val totalAttachmentBytes = candidateFiles.sumOf { it.length() }
                    val attachImages = totalAttachmentBytes <= 25L * 1024L * 1024L
                    val imageUris = if (attachImages) {
                        candidateFiles.map { file ->
                            FileProvider.getUriForFile(
                                context,
                                context.packageName + ".fileprovider",
                                file
                            )
                        }
                    } else emptyList()

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
            OutlinedButton(
                onClick = {
                    val url = roksalEnquiryUrl(c?.category ?: RoksalCategory.OGRAJA)
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                            "Odpri Roksal obrazec"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🌐 Odpri uradni Roksal obrazec")
            }
            Spacer(Modifier.height(8.dp))
            if (p?.let { it.config != null } == true) {
                Text(
                    if (candidateAttachmentNote(p) == null) "" else candidateAttachmentNote(p)!!,
                    color = Warn,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                "Roksal javni obrazec zahteva tudi potrditev obdelave podatkov in splošnih pogojev; e-novice so ločena, neobvezna izbira.",
                color = Muted,
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) {
                Text("Nazaj na domov")
            }
        }
    }
}

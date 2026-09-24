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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import si.ograjavizija.app.data.AppState
import si.ograjavizija.app.data.MeasurementStatus
import si.ograjavizija.app.data.Project
import si.ograjavizija.app.data.ProjectController
import si.ograjavizija.app.data.ProjectStore
import si.ograjavizija.app.data.RoksalCategory
import si.ograjavizija.app.data.RoksalConfig
import si.ograjavizija.app.data.RoksalOrientation
import si.ograjavizija.app.data.RoksalPrivacy
import si.ograjavizija.app.data.RoksalStructure
import si.ograjavizija.app.roksal.RoksalCatalog
import si.ograjavizija.app.roksal.RoksalProfile
import si.ograjavizija.app.ui.components.StepHeader
import si.ograjavizija.app.ui.theme.Bad
import si.ograjavizija.app.ui.theme.Muted
import si.ograjavizija.app.ui.theme.SurfaceAlt
import si.ograjavizija.app.ui.theme.Warn

@Composable
fun RoksalConfigScreen(
    projectId: String?,
    onContinue: (RoksalCategory) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf<Project?>(null) }
    var category by remember { mutableStateOf(RoksalCategory.OGRAJA) }
    var orientation by remember { mutableStateOf(RoksalOrientation.POKONCNA) }
    var profile by remember { mutableStateOf<RoksalProfile?>(null) }
    var colourId by remember { mutableStateOf("BURMA_TEAK") }
    var gapMm by remember { mutableFloatStateOf(10f) }
    var privacy by remember { mutableStateOf(RoksalPrivacy.SREDNJA) }
    var length by remember { mutableFloatStateOf(10f) }
    var height by remember { mutableFloatStateOf(1.5f) }
    var postSpacing by remember { mutableFloatStateOf(150f) }
    var supportSpacing by remember { mutableFloatStateOf(100f) }
    var existing by remember { mutableStateOf(RoksalStructure.NEVEM) }
    var measureStatus by remember { mutableStateOf(MeasurementStatus.OCENA) }
    var gateType by remember { mutableStateOf("BREZ") }
    var gateWidth by remember { mutableFloatStateOf(1.0f) }
    var gateHeight by remember { mutableFloatStateOf(1.2f) }
    var customerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    val options = RoksalCatalog.profilesFor(category, orientation)
    val p = project

    LaunchedEffect(projectId) {
        val loaded = projectId?.let { ProjectStore.load(it) } ?: AppState.currentProject
        project = loaded
        val old = loaded?.config
        if (old != null) {
            category = old.category
            orientation = old.orientation
            colourId = old.colourId
            gapMm = old.boardGapMm.toFloat()
            privacy = old.privacy
            length = old.lengthM
            height = old.heightM
            postSpacing = old.postSpacingCm
            supportSpacing = old.supportSpacingCm
            existing = old.existingStructure
            measureStatus = old.measurementStatus
            gateType = old.gateType
            gateWidth = old.gateWidthM
            gateHeight = old.gateHeightM
            customerName = old.customerName
            phone = old.phone
            email = old.email
            address = old.address
            notes = old.notes
        }
    }

    LaunchedEffect(category, orientation) {
        val old = p?.config
        val requested = old?.profileId
        profile = options.firstOrNull { it.id == requested } ?: options.firstOrNull()
    }

    LaunchedEffect(profile?.id) {
        val colors = profile?.let { RoksalCatalog.colourOptions(it) }.orEmpty()
        if (colors.isNotEmpty() && colors.none { it.id == colourId }) colourId = colors.first().id
    }

    val current = profile
    val config = current?.let {
        RoksalConfig(
            category = category,
            orientation = orientation,
            profileId = it.id,
            colourId = colourId,
            boardGapMm = gapMm.toInt(),
            privacy = privacy,
            lengthM = length,
            heightM = height,
            postSpacingCm = postSpacing,
            supportSpacingCm = supportSpacing,
            gateType = if (category == RoksalCategory.OGRAJA) gateType else "BREZ",
            gateWidthM = if (gateType == "BREZ") 0f else gateWidth,
            gateHeightM = if (gateType == "BREZ") 0f else gateHeight,
            existingStructure = existing,
            measurementStatus = measureStatus,
            customerName = customerName,
            phone = phone,
            email = email,
            address = address,
            notes = notes,
        )
    }
    val validation = config?.let { RoksalCatalog.validate(it) }
    val estimate = config?.let { RoksalCatalog.estimate(it) }

    Column(Modifier.fillMaxSize()) {
        StepHeader(2, "2 · Roksal katalog in projekt", onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)
        ) {
            Text("Kaj želiš urediti?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RoksalCategory.entries) { item ->
                    FilterChip(
                        selected = category == item,
                        onClick = { category = item },
                        label = { Text(item.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Smer", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = orientation == RoksalOrientation.POKONCNA,
                    onClick = { orientation = RoksalOrientation.POKONCNA },
                    label = { Text("Pokončno") }
                )
                FilterChip(
                    selected = orientation == RoksalOrientation.PRECNA,
                    onClick = { orientation = RoksalOrientation.PRECNA },
                    label = { Text("Prečno") }
                )
            }

            Spacer(Modifier.height(12.dp))
            Text("Roksal profil", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options) { item ->
                    ProfileCard(item, item.id == current?.id) { profile = item }
                }
            }
            current?.let {
                Spacer(Modifier.height(6.dp))
                Text(it.dimensions, color = Muted, style = MaterialTheme.typography.labelSmall)
                Text(it.notes, color = Muted, style = MaterialTheme.typography.bodySmall)
                if (it.surfaceOptions.isNotEmpty()) {
                    Text("Površina: " + it.surfaceOptions.joinToString(" / "), color = Muted, style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Barva", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(current?.let { RoksalCatalog.colourOptions(it) }.orEmpty()) { c ->
                    Surface(
                        onClick = { colourId = c.id },
                        shape = RoundedCornerShape(12.dp),
                        color = if (c.id == colourId) MaterialTheme.colorScheme.primaryContainer else SurfaceAlt,
                    ) {
                        Row(
                            Modifier.padding(9.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Surface(
                                modifier = Modifier.width(28.dp).height(28.dp),
                                shape = RoundedCornerShape(7.dp),
                                color = Color(c.previewArgb)
                            ) {}
                            Text(c.name, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Text(
                "Barvni prikaz je informativen; Rustic Oak/Walnut lahko med deskami in serijami opazneje variirata. Preveri dejanski vzorec.",
                color = Muted,
                style = MaterialTheme.typography.labelSmall
            )

            if (category == RoksalCategory.OGRAJA) {
                Spacer(Modifier.height(10.dp))
                Text("Zasebnost in razmak", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(
                        selected = privacy == RoksalPrivacy.ODPRTA,
                        onClick = { privacy = RoksalPrivacy.ODPRTA; gapMm = 25f },
                        label = { Text("Odprta") }
                    )
                    FilterChip(
                        selected = privacy == RoksalPrivacy.SREDNJA,
                        onClick = { privacy = RoksalPrivacy.SREDNJA; gapMm = 10f },
                        label = { Text("Srednja") }
                    )
                    FilterChip(
                        selected = privacy == RoksalPrivacy.ZASEBNA,
                        onClick = { privacy = RoksalPrivacy.ZASEBNA; gapMm = 3f },
                        label = { Text("Zasebna") }
                    )
                }
                Text("Razmak med deskami: " + gapMm.toInt() + " mm")
                Slider(value = gapMm, onValueChange = { gapMm = it }, valueRange = 0f..30f)

                Text("Vrata", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("BREZ", "ENOKRILNA", "DVOKRILNA", "DRSNA").forEach { value ->
                        FilterChip(
                            selected = gateType == value,
                            onClick = { gateType = value },
                            label = { Text(value.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                if (gateType != "BREZ") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = gateWidth.toString(),
                            onValueChange = { it.toFloatOrNull()?.let { gateWidth = it } },
                            label = { Text("Širina vrat m") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = gateHeight.toString(),
                            onValueChange = { it.toFloatOrNull()?.let { gateHeight = it } },
                            label = { Text("Višina vrat m") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = length.toString(),
                    onValueChange = { it.toFloatOrNull()?.let { length = it } },
                    label = { Text("Dolžina m") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = height.toString(),
                    onValueChange = { it.toFloatOrNull()?.let { height = it } },
                    label = { Text("Višina m") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = postSpacing.toInt().toString(),
                    onValueChange = { it.toFloatOrNull()?.let { postSpacing = it } },
                    label = { Text("Razmak stebrov cm") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = supportSpacing.toInt().toString(),
                    onValueChange = { it.toFloatOrNull()?.let { supportSpacing = it } },
                    label = { Text("Nosilci cm") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            if (category == RoksalCategory.OGRAJA || category == RoksalCategory.FASADA || category == RoksalCategory.PREGRADNA_STENA) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(
                        selected = existing == RoksalStructure.NOVA,
                        onClick = { existing = RoksalStructure.NOVA },
                        label = { Text("Nova konstrukcija") }
                    )
                    FilterChip(
                        selected = existing == RoksalStructure.OBSTOJECA,
                        onClick = { existing = RoksalStructure.OBSTOJECA },
                        label = { Text("Obstoječa") }
                    )
                    FilterChip(
                        selected = existing == RoksalStructure.NEVEM,
                        onClick = { existing = RoksalStructure.NEVEM },
                        label = { Text("Ne vem") }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(
                    selected = measureStatus == MeasurementStatus.OCENA,
                    onClick = { measureStatus = MeasurementStatus.OCENA },
                    label = { Text("Meritev je ocena") }
                )
                FilterChip(
                    selected = measureStatus == MeasurementStatus.POTRJENO,
                    onClick = { measureStatus = MeasurementStatus.POTRJENO },
                    label = { Text("Mere potrjene") }
                )
            }

            Spacer(Modifier.height(10.dp))
            Text("Podatki stranke", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(customerName, { customerName = it }, label = { Text("Ime in priimek / podjetje") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(phone, { phone = it }, label = { Text("Telefon") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(email, { email = it }, label = { Text("E-pošta") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(address, { address = it }, label = { Text("Lokacija projekta") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(notes, { notes = it }, label = { Text("Opombe") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            Spacer(Modifier.height(10.dp))
            estimate?.let {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceAlt
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Informativni materialni izračun", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Deske: " + it.boards + " kos · standard " + it.stockLengthM + " m · stebri: " +
                                it.posts + " · nosilci: " + it.supports + " · vijaki: " + it.screws,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text("Predviden odpad: ~" + it.estimatedWastePercent + "%", color = Muted, style = MaterialTheme.typography.labelSmall)
                        it.notes.forEach { n -> Text("• " + n, color = Muted, style = MaterialTheme.typography.labelSmall) }
                        Text("Cena: ni določena. Aplikacija ne izmišlja Roksal cenika.", color = Warn, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            validation?.warnings?.forEach { warning ->
                Text(
                    "⚠️ " + warning,
                    color = if (warning.startsWith("Napaka:")) Bad else Warn,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                enabled = current != null && validation?.valid == true,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                onClick = {
                    val p0 = project ?: return@Button
                    val c = config ?: return@Button
                    scope.launch {
                        val saved = ProjectController.applyRoksalConfig(p0, c)
                        project = saved
                        AppState.setProject(saved)
                        status = "Konfiguracija shranjena."
                        onContinue(category)
                    }
                }
            ) {
                Text(if (category == RoksalCategory.OGRAJA) "Nadaljuj: označi obstoječo ograjo →" else "Pripravi Roksal povpraševanje →")
            }
            if (status.isNotEmpty()) Text(status, color = Muted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileCard(profile: RoksalProfile, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else SurfaceAlt,
        modifier = Modifier.width(205.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(profile.name, style = MaterialTheme.typography.titleSmall)
            Text(profile.dimensions, color = Muted, style = MaterialTheme.typography.labelSmall)
            Text(
                if (profile.hiddenFixing) "Skrito vijačenje" else "Vidno vijačenje",
                color = Muted,
                style = MaterialTheme.typography.labelSmall
            )
            val maxPost = if (profile.vertical) profile.maxPostVerticalCm else profile.maxPostHorizontalCm
            maxPost?.let { Text("Stebri: ≤ " + it + " cm", color = Muted, style = MaterialTheme.typography.labelSmall) }
            profile.maxSupportCm?.let { Text("Nosilci: ≤ " + it + " cm", color = Muted, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

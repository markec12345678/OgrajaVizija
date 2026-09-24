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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import si.ograjavizija.app.data.AppState
import si.ograjavizija.app.data.MeasurementStatus
import si.ograjavizija.app.data.FenceType
import si.ograjavizija.app.data.PostFixing
import si.ograjavizija.app.data.PostAppearance
import si.ograjavizija.app.data.CustomerType
import si.ograjavizija.app.data.MeasurementMethod
import si.ograjavizija.app.data.DeliveryPreference
import si.ograjavizija.app.data.TerraceBase
import si.ograjavizija.app.data.TerraceSubstructure
import si.ograjavizija.app.data.TerraceDirection
import si.ograjavizija.app.data.FacadeLayout
import si.ograjavizija.app.data.KuboReinforcement
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
import si.ograjavizija.app.roksal.RoksalRecommendations
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var project by remember { mutableStateOf<Project?>(null) }
    var category by remember { mutableStateOf(RoksalCategory.OGRAJA) }
    var orientation by remember { mutableStateOf(RoksalOrientation.POKONCNA) }
    var profile by remember { mutableStateOf<RoksalProfile?>(null) }
    var colourId by remember { mutableStateOf("BURMA_TEAK") }
    var surfaceId by remember { mutableStateOf("") }
    var mountingVariant by remember { mutableStateOf("") }
    var handleIncluded by remember { mutableStateOf(true) }
    var gapMm by remember { mutableFloatStateOf(10f) }
    var privacy by remember { mutableStateOf(RoksalPrivacy.SREDNJA) }
    var length by remember { mutableFloatStateOf(10f) }
    var height by remember { mutableFloatStateOf(1.5f) }
    var postSpacing by remember { mutableFloatStateOf(150f) }
    var supportSpacing by remember { mutableFloatStateOf(100f) }
    var existing by remember { mutableStateOf(RoksalStructure.NEVEM) }
    var measureStatus by remember { mutableStateOf(MeasurementStatus.OCENA) }
    var measurementMethod by remember { mutableStateOf(MeasurementMethod.ZNANE_MERE) }
    var referenceDimension by remember { mutableFloatStateOf(0f) }
    var referenceLabel by remember { mutableStateOf("") }
    var segmentLengths by remember { mutableStateOf("") }
    var delivery by remember { mutableStateOf(DeliveryPreference.NEVEM) }
    var gateType by remember { mutableStateOf("BREZ") }
    var gateWidth by remember { mutableFloatStateOf(1.0f) }
    var gateHeight by remember { mutableFloatStateOf(1.2f) }
    var cuttingRequested by remember { mutableStateOf(true) }
    var fenceType by remember { mutableStateOf(FenceType.NEVEM) }
    var postFixing by remember { mutableStateOf(PostFixing.NEVEM) }
    var postAppearance by remember { mutableStateOf(PostAppearance.NEVEM) }
    var customerType by remember { mutableStateOf(CustomerType.FIZICNA_OSEBA) }
    var customerName by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var taxNumber by remember { mutableStateOf("") }
    var invoiceAddress by remember { mutableStateOf("") }
    var deliveryAddressDifferent by remember { mutableStateOf(false) }
    var deliveryAddress by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var dataConsent by remember { mutableStateOf(false) }
    var termsAccepted by remember { mutableStateOf(false) }
    var newsletterOptIn by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var showRecommendations by remember { mutableStateOf(false) }
    var terraceWidth by remember { mutableFloatStateOf(0f) }
    var terraceSlope by remember { mutableFloatStateOf(1f) }
    var terraceHeight by remember { mutableFloatStateOf(5.5f) }
    var terraceBase by remember { mutableStateOf(TerraceBase.NEVEM) }
    var terraceSubstructure by remember { mutableStateOf(TerraceSubstructure.NEVEM) }
    var terraceDirection by remember { mutableStateOf(TerraceDirection.NEVEM) }
    var terraceScrewToBase by remember { mutableStateOf(false) }
    var facadeLayout by remember { mutableStateOf(FacadeLayout.NEVEM) }
    var facadeOpeningNotes by remember { mutableStateOf("") }
    var kuboReinforcement by remember { mutableStateOf(KuboReinforcement.NEVEM) }

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
            surfaceId = old.surfaceId
            mountingVariant = old.mountingVariant
            gapMm = old.boardGapMm.toFloat()
            privacy = old.privacy
            length = old.lengthM
            height = old.heightM
            postSpacing = old.postSpacingCm
            supportSpacing = old.supportSpacingCm
            existing = old.existingStructure
            measureStatus = old.measurementStatus
            measurementMethod = old.measurementMethod
            referenceDimension = old.referenceDimensionMm
            referenceLabel = old.referenceLabel
            segmentLengths = old.segmentLengthsText
            delivery = old.deliveryPreference
            gateType = old.gateType
            gateWidth = old.gateWidthM
            gateHeight = old.gateHeightM
            cuttingRequested = old.cuttingRequested
            fenceType = old.fenceType
            postFixing = old.postFixing
            postAppearance = old.postAppearance
            customerType = old.customerType
            customerName = old.customerName
            companyName = old.companyName
            taxNumber = old.taxNumber
            invoiceAddress = old.invoiceAddress
            deliveryAddressDifferent = old.deliveryAddressDifferent
            deliveryAddress = old.deliveryAddress
            phone = old.phone
            email = old.email
            address = old.address
            notes = old.notes
            terraceWidth = old.terraceWidthM
            terraceSlope = old.terraceSlopeCmPerM
            terraceHeight = old.terraceHeightCm
            terraceBase = old.terraceBase
            terraceSubstructure = old.terraceSubstructure
            terraceDirection = old.terraceDirection
            terraceScrewToBase = old.terraceScrewToBase
            facadeLayout = old.facadeLayout
            facadeOpeningNotes = old.facadeOpeningNotes
            kuboReinforcement = old.kuboReinforcement
            dataConsent = old.dataProcessingConsent
            termsAccepted = old.termsAccepted
            newsletterOptIn = old.newsletterOptIn
        }
    }

    LaunchedEffect(category) {
        when (category) {
            RoksalCategory.TERASA -> {
                orientation = RoksalOrientation.PRECNA
                gapMm = 5.5f
                supportSpacing = 34f
            }
            RoksalCategory.NAPUSC -> {
                orientation = RoksalOrientation.POKONCNA
                supportSpacing = 70f
            }
            RoksalCategory.FASADA -> {
                if (profile?.id == "P100") supportSpacing = 50f
                if (profile?.id == "ROMB67") supportSpacing = 80f
                if (profile?.id == "KUBO8042") supportSpacing = 100f
            }
            else -> Unit
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
        val surfaces = profile?.surfaceOptions.orEmpty()
        if (surfaces.isNotEmpty() && surfaceId !in surfaces) surfaceId = surfaces.first()
        if (surfaces.isEmpty()) surfaceId = ""
    }

    val current = profile
    val config = current?.let {
        RoksalConfig(
            category = category,
            orientation = orientation,
            profileId = it.id,
            colourId = colourId,
            surfaceId = surfaceId,
            mountingVariant = mountingVariant,
            boardGapMm = gapMm.toInt(),
            privacy = privacy,
            lengthM = length,
            heightM = height,
            postSpacingCm = postSpacing,
            supportSpacingCm = supportSpacing,
            gateType = if (category == RoksalCategory.OGRAJA) gateType else "BREZ",
            gateWidthM = if (gateType == "BREZ") 0f else gateWidth,
            gateHeightM = if (gateType == "BREZ") 0f else gateHeight,
            cuttingRequested = cuttingRequested,
            fenceType = fenceType,
            postFixing = postFixing,
            postAppearance = postAppearance,
            handleIncluded = handleIncluded,
            existingStructure = existing,
            measurementStatus = measureStatus,
            measurementMethod = measurementMethod,
            referenceDimensionMm = referenceDimension,
            referenceLabel = referenceLabel,
            segmentLengthsText = segmentLengths,
            deliveryPreference = delivery,
            customerType = customerType,
            customerName = customerName,
            companyName = companyName,
            taxNumber = taxNumber,
            phone = phone,
            email = email,
            invoiceAddress = invoiceAddress,
            deliveryAddressDifferent = deliveryAddressDifferent,
            deliveryAddress = deliveryAddress,
            address = address,
            dataProcessingConsent = dataConsent,
            termsAccepted = termsAccepted,
            newsletterOptIn = newsletterOptIn,
            consentAtMillis = if (dataConsent || termsAccepted || newsletterOptIn) System.currentTimeMillis() else 0L,
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
            if (category != RoksalCategory.TERASA) {
                Button(
                    onClick = { showRecommendations = !showRecommendations },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showRecommendations) "Skrij predloge" else "✨ Predlagaj mi konfiguracije")
                }
                if (showRecommendations) {
                    RoksalRecommendations.suggest(config ?: RoksalConfig()).filter { it.profileId in options.map { profileOption -> profileOption.id } }.forEach { suggestion ->
                        Surface(
                            onClick = {
                                profile = RoksalCatalog.profile(suggestion.profileId)
                                gapMm = suggestion.gapMm.toFloat()
                                privacy = suggestion.privacy
                                showRecommendations = false
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = SurfaceAlt
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(suggestion.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    RoksalCatalog.profile(suggestion.profileId).name,
                                    color = Muted,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(suggestion.reason, style = MaterialTheme.typography.bodySmall)
                                Text(suggestion.note, color = Warn, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

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
                OutlinedButton(
                    onClick = {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(it.catalogUrl)
                        )
                        context.startActivity(
                            android.content.Intent.createChooser(intent, "Odpri Roksal katalog")
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("🌐 Odpri uradni Roksal katalog") }
                if (it.mountingOptions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Način polaganja profila", style = MaterialTheme.typography.titleSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        it.mountingOptions.forEach { option ->
                            FilterChip(
                                selected = mountingVariant == option,
                                onClick = { mountingVariant = option },
                                label = { Text(option) }
                            )
                        }
                    }
                }
                if (it.surfaceOptions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Površina", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        it.surfaceOptions.forEach { surface ->
                            FilterChip(
                                selected = surfaceId == surface,
                                onClick = { surfaceId = surface },
                                label = { Text(surface) }
                            )
                        }
                    }
                }
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
                Text("Tip ograje", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = fenceType == FenceType.BALKON, onClick = { fenceType = FenceType.BALKON }, label = { Text("Balkon") })
                    FilterChip(selected = fenceType == FenceType.DVORISCE, onClick = { fenceType = FenceType.DVORISCE }, label = { Text("Dvorišče") })
                    FilterChip(selected = fenceType == FenceType.NEVEM, onClick = { fenceType = FenceType.NEVEM }, label = { Text("Ne vem") })
                }
                Spacer(Modifier.height(8.dp))
                Text("Konstrukcija", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = postFixing == PostFixing.NA_PLOSCI, onClick = { postFixing = PostFixing.NA_PLOSCI }, label = { Text("Na plošči") })
                    FilterChip(selected = postFixing == PostFixing.BOCNO, onClick = { postFixing = PostFixing.BOCNO }, label = { Text("Bočno") })
                    FilterChip(selected = postFixing == PostFixing.NEVEM, onClick = { postFixing = PostFixing.NEVEM }, label = { Text("Ne vem") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = postAppearance == PostAppearance.OBOJE, onClick = { postAppearance = PostAppearance.OBOJE }, label = { Text("Vidno z obeh strani") })
                    FilterChip(selected = postAppearance == PostAppearance.SKRITO_ZUNAJ, onClick = { postAppearance = PostAppearance.SKRITO_ZUNAJ }, label = { Text("Skrito od zunaj") })
                    FilterChip(selected = postAppearance == PostAppearance.NEVEM, onClick = { postAppearance = PostAppearance.NEVEM }, label = { Text("Ne vem") })
                }
                Spacer(Modifier.height(8.dp))
                FilterChip(
                    selected = handleIncluded,
                    onClick = { handleIncluded = !handleIncluded },
                    label = { Text(if (handleIncluded) "Vključi zgornji ročaj" else "Brez zgornjega ročaja") }
                )
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
                Slider(value = gapMm, onValueChange = { gapMm = it }, valueRange = 2f..30f)

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

            if (category == RoksalCategory.TERASA) {
                Spacer(Modifier.height(10.dp))
                Text("Terasa · podlaga in montaža", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Roksal navaja 5–6 mm med deskami, podkonstrukcijo približno 33–35 cm in padec najmanj 1 cm/m.",
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall
                )
                Text("Padec: " + "%.1f".format(java.util.Locale.US, terraceSlope) + " cm/m")
                Slider(value = terraceSlope, onValueChange = { terraceSlope = it }, valueRange = 0f..3f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = terraceHeight.toString(),
                        onValueChange = { it.toFloatOrNull()?.let { terraceHeight = it } },
                        label = { Text("Končna višina cm") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Text("Fuga: " + gapMm.toInt() + " mm", modifier = Modifier.weight(1f).padding(top = 16.dp), style = MaterialTheme.typography.bodySmall)
                }
                Text("Podlaga", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    val bases = listOf(
                        TerraceBase.BETON to "Beton",
                        TerraceBase.PLOSCICE to "Ploščice",
                        TerraceBase.HIDROIZOLACIJA to "Hidroizolacija",
                        TerraceBase.PESek to "Pesek",
                        TerraceBase.ZEMLJA_TRAVA to "Zemlja / trava",
                        TerraceBase.NEVEM to "Ne vem"
                    )
                    bases.forEach { (value, label) ->
                        FilterChip(selected = terraceBase == value, onClick = { terraceBase = value }, label = { Text(label) })
                    }
                }
                Text("Podkonstrukcija", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    val subs = listOf(
                        TerraceSubstructure.WPC_LETVE to "WPC letve",
                        TerraceSubstructure.ALU_CEV to "Alu cevi",
                        TerraceSubstructure.ALU_MREZA to "Alu mreža",
                        TerraceSubstructure.KOVINSKA_KONSTRUKCIJA to "Kovinska",
                        TerraceSubstructure.NEVEM to "Ne vem"
                    )
                    subs.forEach { (value, label) ->
                        FilterChip(selected = terraceSubstructure == value, onClick = { terraceSubstructure = value }, label = { Text(label) })
                    }
                }
                Text("Smer desk", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = terraceDirection == TerraceDirection.V_SMER_PADCA, onClick = { terraceDirection = TerraceDirection.V_SMER_PADCA }, label = { Text("V smeri padca") })
                    FilterChip(selected = terraceDirection == TerraceDirection.PRAVOKOTNO_NA_PADEC, onClick = { terraceDirection = TerraceDirection.PRAVOKOTNO_NA_PADEC }, label = { Text("Pravokotno") })
                    FilterChip(selected = terraceDirection == TerraceDirection.NEVEM, onClick = { terraceDirection = TerraceDirection.NEVEM }, label = { Text("Ne vem") })
                }
                FilterChip(
                    selected = terraceScrewToBase,
                    onClick = { terraceScrewToBase = !terraceScrewToBase },
                    label = { Text(if (terraceScrewToBase) "Vijačenje v podlago" else "Brez vijačenja v podlago") }
                )
            }

            if (category == RoksalCategory.FASADA) {
                Spacer(Modifier.height(10.dp))
                Text("Fasada · razpored in odprtine", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = facadeLayout == FacadeLayout.ENOTEN, onClick = { facadeLayout = FacadeLayout.ENOTEN }, label = { Text("Enoten videz") })
                    FilterChip(selected = facadeLayout == FacadeLayout.MESAN, onClick = { facadeLayout = FacadeLayout.MESAN }, label = { Text("Mešan videz") })
                    FilterChip(selected = facadeLayout == FacadeLayout.NEVEM, onClick = { facadeLayout = FacadeLayout.NEVEM }, label = { Text("Ne vem") })
                }
                OutlinedTextField(
                    value = facadeOpeningNotes,
                    onValueChange = { facadeOpeningNotes = it },
                    label = { Text("Okna/vrata · mere ali brez odprtin") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                if (current?.id == "KUBO8042") {
                    Text("KUBO · notranja ojačitev", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        val kubo = listOf(
                            KuboReinforcement.BREZ_DO_120 to "Do 120 cm · brez",
                            KuboReinforcement.ALU_20X60_DO_260 to "120–260 cm · 20×60×2",
                            KuboReinforcement.PROJEKTNA_OJACITEV to "Nad 260 cm · projektna",
                            KuboReinforcement.NEVEM to "Ne vem"
                        )
                        kubo.forEach { (value, label) ->
                            FilterChip(selected = kuboReinforcement == value, onClick = { kuboReinforcement = value }, label = { Text(label) })
                        }
                    }
                }
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

            Spacer(Modifier.height(8.dp))
            Text("Kako si določil mere?", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(selected = measurementMethod == MeasurementMethod.ZNANE_MERE, onClick = { measurementMethod = MeasurementMethod.ZNANE_MERE }, label = { Text("Znane mere") })
                FilterChip(selected = measurementMethod == MeasurementMethod.REFERENCA_NA_SLIKI, onClick = { measurementMethod = MeasurementMethod.REFERENCA_NA_SLIKI }, label = { Text("Referenca na sliki") })
                FilterChip(selected = measurementMethod == MeasurementMethod.SEGMENTI, onClick = { measurementMethod = MeasurementMethod.SEGMENTI }, label = { Text("Segmenti") })
            }
            if (measurementMethod == MeasurementMethod.REFERENCA_NA_SLIKI) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(referenceDimension.toString(), { it.toFloatOrNull()?.let { referenceDimension = it } }, label = { Text("Znana mera mm") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(referenceLabel, { referenceLabel = it }, label = { Text("Kaj meriš?") }, modifier = Modifier.weight(1f), singleLine = true)
                }
            }
            if (measurementMethod == MeasurementMethod.SEGMENTI) {
                OutlinedTextField(segmentLengths, { segmentLengths = it }, label = { Text("Segmenti, npr. 2,4; 2,8; 3,1 m") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            Spacer(Modifier.height(8.dp))
            Text("Dostava", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(selected = delivery == DeliveryPreference.DOSTAVA, onClick = { delivery = DeliveryPreference.DOSTAVA }, label = { Text("Dostava") })
                FilterChip(selected = delivery == DeliveryPreference.DOSTAVA_IN_MONTAZA, onClick = { delivery = DeliveryPreference.DOSTAVA_IN_MONTAZA }, label = { Text("Dostava + montaža") })
                FilterChip(selected = delivery == DeliveryPreference.OSEBNI_PREVZEM, onClick = { delivery = DeliveryPreference.OSEBNI_PREVZEM }, label = { Text("Osebni prevzem") })
                FilterChip(selected = delivery == DeliveryPreference.NEVEM, onClick = { delivery = DeliveryPreference.NEVEM }, label = { Text("Ne vem") })
            }

            Spacer(Modifier.height(10.dp))
            Text("Podatki za ponudbo", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(selected = customerType == CustomerType.FIZICNA_OSEBA, onClick = { customerType = CustomerType.FIZICNA_OSEBA }, label = { Text("Fizična oseba") })
                FilterChip(selected = customerType == CustomerType.PODJETJE, onClick = { customerType = CustomerType.PODJETJE }, label = { Text("Podjetje") })
            }
            OutlinedTextField(customerName, { customerName = it }, label = { Text("Ime in priimek / podjetje") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (customerType == CustomerType.PODJETJE) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(companyName, { companyName = it }, label = { Text("Naziv podjetja") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(taxNumber, { taxNumber = it }, label = { Text("Davčna številka") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(phone, { phone = it }, label = { Text("Telefon") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(email, { email = it }, label = { Text("E-pošta") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(invoiceAddress, { invoiceAddress = it }, label = { Text("Naslov za račun") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(address, { address = it }, label = { Text("Lokacija projekta") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(6.dp))
            FilterChip(selected = deliveryAddressDifferent, onClick = { deliveryAddressDifferent = !deliveryAddressDifferent }, label = { Text("Dostava na drug naslov") })
            if (deliveryAddressDifferent) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(deliveryAddress, { deliveryAddress = it }, label = { Text("Naslov dostave") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(notes, { notes = it }, label = { Text("Opombe") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            Spacer(Modifier.height(8.dp))
            Text("Pred oddajo povpraševanja", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = dataConsent, onCheckedChange = { dataConsent = it })
                Text("Strinjam se z obdelavo osebnih podatkov za odgovor na povpraševanje.", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = termsAccepted, onCheckedChange = { termsAccepted = it })
                Text("Seznanjen/-a sem s splošnimi pogoji Roksala.", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = newsletterOptIn, onCheckedChange = { newsletterOptIn = it })
                Text("Želim prejemati e-novice (neobvezno).", style = MaterialTheme.typography.bodySmall)
            }

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
            if (profile.referenceImageUrl.isNotBlank()) {
                AsyncImage(
                    model = profile.referenceImageUrl,
                    contentDescription = profile.name,
                    modifier = Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
                Spacer(Modifier.height(8.dp))
            }
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

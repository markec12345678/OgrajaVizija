package si.ograjavizija.app.roksal

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import si.ograjavizija.app.data.RoksalCategory
import si.ograjavizija.app.data.RoksalConfig
import si.ograjavizija.app.data.RoksalOrientation
import si.ograjavizija.app.data.RoksalStructure
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Centralna baza Roksal WoodCore konfiguratorja.
 * Tehnične vrednosti so povzete iz javnih Roksal strani, pregledanih 24. 9. 2026.
 * Cene namenoma niso vključene: aplikacija ne sme ugibati cenika.
 */
data class RoksalProfile(
    val id: String,
    val name: String,
    val dimensions: String,
    val faceWidthMm: Int,
    val stockLengthsMm: List<Int>,
    val vertical: Boolean,
    val horizontal: Boolean,
    val maxSupportCm: Int? = null,
    val maxPostVerticalCm: Int? = null,
    val maxPostHorizontalCm: Int? = null,
    val hiddenFixing: Boolean,
    val requiresAluCore: Boolean = false,
    val colourCount: Int,
    val surfaceOptions: List<String> = emptyList(),
    val mountingOptions: List<String> = emptyList(),
    val colourIds: List<String> = emptyList(),
    val referenceImageUrl: String = "",
    val catalogUrl: String = "https://roksal.com/woodcore-wpc-deske/",
    val notes: String,
)

data class RoksalColour(
    val id: String,
    val name: String,
    val previewArgb: Int,
)

data class ValidationResult(
    val valid: Boolean,
    val warnings: List<String>,
)

data class RoksalComponent(
    val id: String,
    val name: String,
    val unit: String,
    val note: String = "",
)

data class MaterialEstimate(
    val boards: Int,
    val stockLengthM: Float,
    val posts: Int,
    val supports: Int,
    val screws: Int,
    val handles: Int,
    val components: List<RoksalComponent>,
    val estimatedWastePercent: Int,
    val notes: List<String>,
)

object RoksalCatalog {
    const val VERIFIED_AT = "2026-09-24"

    val colours = listOf(
        RoksalColour("ASH_WOOD", "Ash Wood", 0xFF9A927F.toInt()),
        RoksalColour("AMAZON_WOOD", "Amazon Wood", 0xFF6D5847.toInt()),
        RoksalColour("BURMA_TEAK", "Burma Teak", 0xFF9A5A3E.toInt()),
        RoksalColour("GOLDEN_TEAK", "Golden Teak", 0xFFB08A55.toInt()),
        RoksalColour("OAK_WOOD", "Oak Wood", 0xFF766046.toInt()),
        RoksalColour("RUSTIC_OAK", "Rustic Oak", 0xFF7D624A.toInt()),
        RoksalColour("RUSTIC_WALNUT", "Rustic Walnut", 0xFF554239.toInt()),
        RoksalColour("WHITE", "White", 0xFFE7E2D8.toInt()),
    )

    val profiles = listOf(
        RoksalProfile(
            id = "P57_32",
            name = "POLNA DESKA 57/32",
            dimensions = "57 × 32 × 5800 mm",
            faceWidthMm = 57,
            stockLengthsMm = listOf(5800),
            vertical = true,
            horizontal = false,
            maxSupportCm = 100,
            maxPostVerticalCm = 150,
            hiddenFixing = true,
            colourCount = 4,
            mountingOptions = listOf("57 mm", "32 mm", "Razgibano"),
            colourIds = listOf("AMAZON_WOOD", "ASH_WOOD", "GOLDEN_TEAK", "BURMA_TEAK"),
            referenceImageUrl = "https://roksal.com/wp-content/uploads/2025/07/polna-deska-57-32-300x253.jpg",
            catalogUrl = "https://roksal.com/woodcore-wpc-deske/balkonske-ograje-in-dvoriscne-ograje/wpc-ograja-pokoncna/",
            notes = "Pokončna izvedba; deska se lahko montira na 57 ali 32 mm stran ali razgibano; skrito vijačenje. Aktualna produktna stran navaja 4 odtenke, splošna barvna stran pa 3; seznam zaloge zato preveri pred oddajo."
        ),
        RoksalProfile(
            id = "P100",
            name = "POLNA DESKA 100",
            dimensions = "100 × 12 × 5800 mm",
            faceWidthMm = 100,
            stockLengthsMm = listOf(5800),
            vertical = true,
            horizontal = true,
            maxSupportCm = 80,
            maxPostVerticalCm = 180,
            hiddenFixing = false,
            colourCount = 8,
            colourIds = listOf("AMAZON_WOOD", "ASH_WOOD", "GOLDEN_TEAK", "BURMA_TEAK", "OAK_WOOD", "WHITE", "RUSTIC_OAK", "RUSTIC_WALNUT"),
            referenceImageUrl = "https://roksal.com/wp-content/uploads/2020/03/ograja-pokoncna.jpg",
            catalogUrl = "https://roksal.com/woodcore-wpc-deske/balkonske-ograje-in-dvoriscne-ograje/wpc-ograja-pokoncna/",
            notes = "Pokončna; vijaki so vidni z lica. Bela je na Roksalovem obrazcu označena kot možnost samo za polno desko 10 cm."
        ),
        RoksalProfile(
            id = "P128",
            name = "POLNA DESKA 128",
            dimensions = "128 × 16,5 × 2200 mm",
            faceWidthMm = 128,
            stockLengthsMm = listOf(2200),
            vertical = true,
            horizontal = true,
            maxSupportCm = 100,
            maxPostVerticalCm = 180,
            maxPostHorizontalCm = 110,
            hiddenFixing = false,
            colourCount = 6,
            colourIds = listOf("AMAZON_WOOD", "ASH_WOOD", "GOLDEN_TEAK", "BURMA_TEAK", "OAK_WOOD", "RUSTIC_OAK"),
            referenceImageUrl = "https://roksal.com/wp-content/uploads/2021/12/polna-deska-nova2021-274x300.jpg",
            catalogUrl = "https://roksal.com/woodcore-wpc-deske/balkonske-ograje-in-dvoriscne-ograje/wpc-ograja-pokoncna/",
            notes = "Pokončna ali prečna izvedba. Aktualna produktna stran navaja 6 odtenkov, splošna barvna stran pa 5; seznam zaloge zato preveri pred oddajo."
        ),
        RoksalProfile(
            id = "ROMB67",
            name = "ROMB DESKA 67",
            dimensions = "67 × 26 × 5800 mm",
            faceWidthMm = 67,
            stockLengthsMm = listOf(5800),
            vertical = true,
            horizontal = true,
            maxSupportCm = 110,
            maxPostVerticalCm = 145,
            maxPostHorizontalCm = 145,
            hiddenFixing = true,
            requiresAluCore = true,
            colourCount = 7,
            colourIds = listOf("AMAZON_WOOD", "ASH_WOOD", "GOLDEN_TEAK", "BURMA_TEAK", "OAK_WOOD", "RUSTIC_OAK", "RUSTIC_WALNUT"),
            referenceImageUrl = "https://roksal.com/wp-content/uploads/2021/12/romb-woodcore-wpc-300x229.jpg",
            catalogUrl = "https://roksal.com/woodcore-wpc-deske/balkonske-ograje-in-dvoriscne-ograje/wpc-ograja-pokoncna/",
            notes = "Pri ograji je aluminijasta cev v sredini obvezna; skrito vijačenje. Brez alu cevi montaža ni mogoča."
        ),
        RoksalProfile(
            id = "DESKA150",
            name = "DESKA 150",
            dimensions = "150 × 25 × 4000 / 2200 mm",
            faceWidthMm = 150,
            stockLengthsMm = listOf(4000, 2200),
            vertical = false,
            horizontal = true,
            maxPostHorizontalCm = 140,
            hiddenFixing = false,
            colourCount = 7,
            surfaceOptions = listOf("GOSTA REBRA", "ŠIROKA REBRA", "GLADKA / RUSTIK"),
            colourIds = listOf("AMAZON_WOOD", "ASH_WOOD", "GOLDEN_TEAK", "BURMA_TEAK", "OAK_WOOD", "RUSTIC_OAK", "RUSTIC_WALNUT"),
            referenceImageUrl = "https://roksal.com/wp-content/uploads/2020/03/deska-za-terase.jpg",
            notes = "Prečna izvedba; profil 150 ima več površinskih izvedb. Za ograjo so vijaki vidni."
        ),
        RoksalProfile(
            id = "KUBO8042",
            name = "KUBO 80/42",
            dimensions = "80 × 42 × 5000 / 5800 mm",
            faceWidthMm = 80,
            stockLengthsMm = listOf(5000, 5800),
            vertical = true,
            horizontal = false,
            maxSupportCm = 100,
            maxPostVerticalCm = 200,
            hiddenFixing = true,
            requiresAluCore = true,
            colourCount = 4,
            mountingOptions = listOf("80 mm", "42 mm", "Razgibano"),
            colourIds = listOf("AMAZON_WOOD", "OAK_WOOD", "RUSTIC_OAK", "RUSTIC_WALNUT"),
            referenceImageUrl = "https://roksal.com/wp-content/uploads/2021/09/kubo-roksal-salon.jpg",
            notes = "Predvsem fasade/pregradne stene; notranja aluminijasta cev vpliva na konstrukcijo in razpon. 5000 mm dolžina je po Roksalu odvisna od razpoložljivosti odtenka."
        ),
    )

    val components = listOf(
        RoksalComponent("HANDLE_92", "Ročaj za ograjo 92 × 45 × 5800 mm", "kos", "Priporočena spojna dolžina največ 4 m."),
        RoksalComponent("RF_SCREW", "RF vijaki", "kos", "Predvrtanje je obvezno; luknja zaradi raztezanja."),
        RoksalComponent("ROMB_ALU", "Aluminijasta cev za ROMB", "kos", "Pri ROMB je alu cev v sredini obvezna."),
        RoksalComponent("ROMB_CAP_LR", "Čepi za ROMB levo/desno", "komplet", "Zaključek koncev ROMB profila."),
        RoksalComponent("L_BRACKET", "L-kotnik", "kos", "Uporablja se pri pritrditvi ROMB na steber."),
        RoksalComponent("TERRACE_SUB", "WPC podložna letev 40 × 30 × 2200 mm", "kos", "Podkonstrukcija terase."),
        RoksalComponent("TERRACE_ALU", "Alu podložna cev", "kos", "Dolžina 6 m; višina je odvisna od izvedbe."),
        RoksalComponent("TERRACE_TRIM", "Zaključna letev 45 × 55 mm", "kos", "Na voljo v več standardnih dolžinah."),
        RoksalComponent("TERRACE_CLIP", "Začetni distančnik / distančnik", "kos", "Za pravilne dilatacije in montažo."),
        RoksalComponent("TERRACE_GRID", "Alu mreža za podkonstrukcijo", "kos", "Za pripravljeno stabilno podlago, kadar je predvidena mrežna rešitev."),
        RoksalComponent("TERRACE_RUBBER", "Gumijasta podloga", "kos", "Za hidroizolacijo brez vijačenja v podlago."),
        RoksalComponent("TERRACE_INOX_SCREW", "Inox vijaki za teraso", "kos", "Količino in dolžino vijakov določi končna podkonstrukcija in izvedba."),
        RoksalComponent("FACADE_CLIP", "Fasadni klip", "kos", "Za pritrditev določenih fasadnih WoodCore profilov."),
        RoksalComponent("KUBO_CORE", "Notranja aluminijasta cev KUBO", "tekoči m", "Profil KUBO zahteva ustrezno notranjo alu/RF rešitev glede na izvedbo in razpon."),
        RoksalComponent("KUBO_20X60", "Aluminijasta cev 20 × 60 × 2 mm", "tekoči m", "Za KUBO razpone nad 120 cm do 260 cm."),
        RoksalComponent("KUBO_25X25", "Aluminijasta cev 25 × 25 × 2 mm", "tekoči m", "Alternativna notranja cev KUBO glede na način montaže."),
        RoksalComponent("KUBO_20X20", "Aluminijasta cev 20 × 20 × 2 mm", "tekoči m", "Alternativna notranja cev KUBO glede na način montaže."),
    )

    private val profileMap = profiles.associateBy { it.id }
    private val colourMap = colours.associateBy { it.id }

    fun profile(id: String): RoksalProfile = profileMap[id] ?: profiles.first()

    fun colour(id: String): RoksalColour = colourMap[id] ?: colours.first()

    fun profilesFor(category: RoksalCategory, orientation: RoksalOrientation): List<RoksalProfile> {
        val supportsOrientation: (RoksalProfile) -> Boolean = {
            if (orientation == RoksalOrientation.POKONCNA) it.vertical else it.horizontal
        }
        return when (category) {
            RoksalCategory.OGRAJA -> if (orientation == RoksalOrientation.POKONCNA)
                profiles.filter { it.id in setOf("P57_32", "P100", "P128", "ROMB67") }
            else
                profiles.filter { it.id in setOf("P128", "ROMB67", "DESKA150") }
            RoksalCategory.PREGRADNA_STENA -> if (orientation == RoksalOrientation.POKONCNA)
                profiles.filter { it.id in setOf("P57_32", "P100", "P128", "ROMB67", "KUBO8042") }
            else
                profiles.filter { it.id in setOf("P128", "ROMB67", "DESKA150") }
            RoksalCategory.FASADA ->
                profiles.filter { it.id in setOf("P100", "ROMB67", "KUBO8042") }.filter(supportsOrientation)
            RoksalCategory.TERASA -> profiles.filter { it.id == "DESKA150" }
            RoksalCategory.NAPUSC ->
                profiles.filter { it.id in setOf("P100", "ROMB67") }.filter(supportsOrientation)
            RoksalCategory.STROP ->
                profiles.filter { it.id in setOf("P100", "ROMB67", "KUBO8042") }.filter(supportsOrientation)
        }
    }

    fun colourOptions(profile: RoksalProfile): List<RoksalColour> =
        if (profile.colourIds.isNotEmpty()) profile.colourIds.mapNotNull { colourMap[it] }
        else colours.take(profile.colourCount)

    fun maxSupportCmFor(c: RoksalConfig, p: RoksalProfile): Int? = when (c.category) {
        RoksalCategory.FASADA -> when (p.id) {
            "P100" -> if (c.orientation == RoksalOrientation.POKONCNA) 50 else 80
            "ROMB67" -> 80
            "KUBO8042" -> 100
            else -> p.maxSupportCm
        }
        RoksalCategory.TERASA -> 35
        RoksalCategory.OGRAJA, RoksalCategory.PREGRADNA_STENA, RoksalCategory.NAPUSC, RoksalCategory.STROP -> p.maxSupportCm
    }

    fun maxPostCmFor(c: RoksalConfig, p: RoksalProfile): Int? = when (c.category) {
        RoksalCategory.OGRAJA, RoksalCategory.PREGRADNA_STENA ->
            if (c.orientation == RoksalOrientation.POKONCNA) p.maxPostVerticalCm else p.maxPostHorizontalCm
        else -> null
    }

    fun validate(c: RoksalConfig): ValidationResult {
        val p = profileMap[c.profileId] ?: return ValidationResult(false, listOf("Napaka: izbran profil ne obstaja."))
        val warnings = mutableListOf<String>()
        if (c.lengthM <= 0f) warnings += "Napaka: dolžina mora biti večja od 0 m."
        if (c.heightM <= 0f) warnings += "Napaka: višina mora biti večja od 0 m."
        if (c.orientation == RoksalOrientation.POKONCNA && !p.vertical)
            warnings += "Napaka: profil ni namenjen pokončni izvedbi."
        if (c.orientation == RoksalOrientation.PRECNA && !p.horizontal)
            warnings += "Napaka: profil ni namenjen prečni izvedbi."

        when (c.category) {
            RoksalCategory.OGRAJA, RoksalCategory.PREGRADNA_STENA -> {
                if (c.boardGapMm < 0) warnings += "Napaka: razmak med deskami ne more biti negativen."
                val maxSupport = maxSupportCmFor(c, p)
                val maxPost = maxPostCmFor(c, p)
                if (maxSupport != null && c.supportSpacingCm > maxSupport)
                    warnings += "Razmak nosilcev " + c.supportSpacingCm.roundToInt() + " cm presega " + maxSupport + " cm."
                val heightLimitedPost = if (c.orientation == RoksalOrientation.POKONCNA && c.heightM > 1.5f && maxPost != null)
                    minOf(maxPost, 150) else maxPost
                if (heightLimitedPost != null && c.postSpacingCm > heightLimitedPost)
                    warnings += "Razmak stebrov " + c.postSpacingCm.roundToInt() + " cm presega " + heightLimitedPost + " cm."
                if (c.boardGapMm > 30)
                    warnings += "Razmak večji od 3 cm presega javno naveden priporočeni razpon; zahtevaj potrditev Roksala."
                if (p.requiresAluCore)
                    warnings += "Za ta profil je aluminijasto jedro obvezno; to mora biti vključeno v izvedbo."
                if (c.category == RoksalCategory.OGRAJA &&
                    c.existingStructure == RoksalStructure.OBSTOJECA &&
                    c.postFixing != si.ograjavizija.app.data.PostFixing.NEVEM &&
                    c.fenceType == si.ograjavizija.app.data.FenceType.NEVEM) {
                    warnings += "Pri obstoječi konstrukciji določi, ali gre za balkon ali dvoriščno ograjo, da je mogoča pravilna presoja pritrditve."
                }
                if (p.id == "ROMB67" && c.orientation == RoksalOrientation.PRECNA &&
                    c.postSpacingCm > 145f && c.postSpacingCm < 180f) {
                    warnings += "Pri ROMB prečni izvedbi nad 145 cm preveri dodatno povezavo po montažnih navodilih."
                }
                if (p.id == "ROMB67" && c.orientation == RoksalOrientation.PRECNA && c.postSpacingCm >= 180f) {
                    warnings += "Pri ROMB prečni izvedbi pri 180 cm ali več je po javnem opisu potreben dodatni steber."
                }
            }
            RoksalCategory.FASADA -> {
                val supportMax = maxSupportCmFor(c, p)
                if (supportMax != null && c.supportSpacingCm > supportMax)
                    warnings += "Razmak fasadne podkonstrukcije " + c.supportSpacingCm.roundToInt() + " cm presega " + supportMax + " cm."
                if (p.id == "KUBO8042" && c.orientation != RoksalOrientation.POKONCNA)
                    warnings += "KUBO je za fasado konfiguriran samo pokončno."
                if (p.id == "P100" && c.orientation == RoksalOrientation.POKONCNA && c.supportSpacingCm > 50f)
                    warnings += "Pri pokončni P100 fasadi je maksimalni razmak prečnih letev 50 cm."
                if (p.id == "ROMB67" && c.supportSpacingCm > 80f)
                    warnings += "Pri fasadnem ROMB je maksimalni razmak macesnovih moral 80 cm."
                if (c.facadeOpeningNotes.isBlank())
                    warnings += "Za končno ponudbo dodaj dimenzije oken/vrat oziroma opombo, če odprtin ni."
                if (p.id == "KUBO8042" && c.kuboReinforcement == si.ograjavizija.app.data.KuboReinforcement.NEVEM)
                    warnings += "Pri KUBO določi notranjo ojačitev glede na razpon; izbira vpliva na konstrukcijo."
            }
            RoksalCategory.TERASA -> {
                if (c.terraceWidthM <= 0f) warnings += "Napaka: vnesi širino terase."
                if (c.boardGapMm !in 5..6) warnings += "Roksal za teraso navaja razmak 5–6 mm."
                if (c.terraceSlopeCmPerM < 1f) warnings += "Padec terase mora biti najmanj 1 cm/m."
                if (c.terraceHeightCm < 4.5f) warnings += "Minimalna navedena višina terase je 4,5 cm."
                if (c.terraceScrewToBase && c.terraceBase == si.ograjavizija.app.data.TerraceBase.HIDROIZOLACIJA)
                    warnings += "Pri hidroizolaciji se podložne letve ne privijačijo v tla."
                if (c.terraceBase == si.ograjavizija.app.data.TerraceBase.ZEMLJA_TRAVA)
                    warnings += "WPC terase ni dovoljeno polagati neposredno na travo ali zemljo."
                if (c.terraceBase == si.ograjavizija.app.data.TerraceBase.PESek)
                    warnings += "Pesek sam ni dovolj stabilna podlaga; Roksal priporoča pripravljeno stabilno osnovo in alu mrežo."
                if (c.terraceSubstructure == si.ograjavizija.app.data.TerraceSubstructure.WPC_LETVE &&
                    c.terraceBase != si.ograjavizija.app.data.TerraceBase.BETON &&
                    c.terraceBase != si.ograjavizija.app.data.TerraceBase.NEVEM)
                    warnings += "Pri neravni podlagi Roksal priporoča aluminijasto podkonstrukcijo namesto točkovno podprte WPC letve."
            }
            RoksalCategory.NAPUSC -> {
                if (p.id != "P100") warnings += "Za napušč je v trenutnem konfiguratorju podprt P100."
                if (c.supportSpacingCm > 80f) warnings += "Razmak podkonstrukcije je treba preveriti glede na izbrano izvedbo; privzeto ga omejujemo na 80 cm."
            }
        }
        return ValidationResult(warnings.none { it.startsWith("Napaka:") }, warnings)
    }

    fun estimate(c: RoksalConfig): MaterialEstimate {
        val p = profileMap[c.profileId] ?: return MaterialEstimate(0, 0f, 0, 0, 0, 0, emptyList(), 0, listOf("Profil ni znan."))
        return when (c.category) {
            RoksalCategory.TERASA -> {
                val lengthMm = (c.lengthM * 1000f).coerceAtLeast(1f)
                val widthMm = (c.terraceWidthM * 1000f).coerceAtLeast(1f)
                val pitch = p.faceWidthMm + c.boardGapMm
                val rows = ceil(widthMm / pitch).toInt()
                val stock = p.stockLengthsMm.maxOrNull() ?: 4000
                val piecesPerRow = ceil(lengthMm / stock).toInt().coerceAtLeast(1)
                val boards = rows * piecesPerRow
                val underlaySpacing = 34f
                val underlayRows = ceil(widthMm / (underlaySpacing * 10f)).toInt().coerceAtLeast(2)
                MaterialEstimate(
                    boards = boards,
                    stockLengthM = stock / 1000f,
                    posts = 0,
                    supports = underlayRows,
                    screws = boards * 6,
                    handles = 0,
                    components = componentsFor(c),
                    estimatedWastePercent = 8,
                    notes = listOf(
                        "Površina približno " + "%.2f".format(java.util.Locale.US, c.lengthM * c.terraceWidthM) + " m².",
                        "Podložne letve/alu elementi so ocenjeni na razmak približno 33–35 cm.",
                        "Končni smer polaganja in razrez mora potrditi Roksal."
                    )
                )
            }
            else -> {
                val gap = max(0, c.boardGapMm)
                val pitchMm = max(1, p.faceWidthMm + gap)
                if (c.orientation == RoksalOrientation.POKONCNA) {
                    val verticalBoards = ceil(c.lengthM * 1000f / pitchMm).toInt()
                    val stock = p.stockLengthsMm.maxOrNull() ?: 5800
                    val heightMm = max(1, (c.heightM * 1000f).roundToInt())
                    val boardsPerStock = max(1, stock / heightMm)
                    val stockPieces = ceil(verticalBoards.toFloat() / boardsPerStock).toInt()
                    val maxSupport = maxSupportCmFor(c, p)?.toFloat() ?: c.supportSpacingCm
                    val supports = max(2, ceil(c.lengthM * 100f / maxSupport).toInt() + 1)
                    MaterialEstimate(
                        boards = stockPieces,
                        stockLengthM = stock / 1000f,
                        posts = if (c.category == RoksalCategory.OGRAJA || c.category == RoksalCategory.PREGRADNA_STENA)
                            max(2, ceil(c.lengthM * 100f / max(1f, c.postSpacingCm)).toInt() + 1) else 0,
                        supports = supports,
                        screws = verticalBoards * 2,
                        handles = if (c.category == RoksalCategory.OGRAJA && c.handleIncluded) 1 else 0,
                        components = componentsFor(c),
                        estimatedWastePercent = 8,
                        notes = listOf(
                            "Izračun je informativen in ne nadomešča Roksal razreza.",
                            "Standardna zaloga profila: " + (stock / 1000f) + " m."
                        )
                    )
                } else {
                    val rows = ceil(c.heightM * 1000f / pitchMm).toInt()
                    val stock = p.stockLengthsMm.maxOrNull() ?: 4000
                    val piecesPerRow = max(1, ceil(c.lengthM * 1000f / stock).toInt())
                    MaterialEstimate(
                        boards = rows * piecesPerRow,
                        stockLengthM = stock / 1000f,
                        posts = if (c.category == RoksalCategory.OGRAJA || c.category == RoksalCategory.PREGRADNA_STENA)
                            max(2, ceil(c.lengthM * 100f / max(1f, c.postSpacingCm)).toInt() + 1) else 0,
                        supports = max(2, ceil(c.lengthM * 100f / max(1f, c.supportSpacingCm)).toInt() + 1),
                        screws = rows * piecesPerRow * 2,
                        handles = if (c.category == RoksalCategory.OGRAJA && c.handleIncluded) 1 else 0,
                        components = componentsFor(c),
                        estimatedWastePercent = 10,
                        notes = listOf(
                            "Izračun je informativen; optimalen razrez je odvisen od dejanskih segmentov.",
                            "Spoji desk naj program vedno načrtuje nad stebrom oziroma ustrezno nosilno točko."
                        )
                    )
                }
            }
        }
    }

    fun componentsFor(c: RoksalConfig): List<RoksalComponent> {
        val out = mutableListOf<RoksalComponent>()

        if (c.category == RoksalCategory.OGRAJA && c.handleIncluded) {
            out += components.first { it.id == "HANDLE_92" }
        }

        if (c.category != RoksalCategory.TERASA && c.category != RoksalCategory.FASADA) {
            out += components.first { it.id == "RF_SCREW" }
        }

        if (c.profileId == "ROMB67") {
            out += components.first { it.id == "ROMB_ALU" }
            out += components.first { it.id == "ROMB_CAP_LR" }
            if (c.orientation == RoksalOrientation.PRECNA) {
                out += components.first { it.id == "L_BRACKET" }
            }
        }

        if (c.profileId == "KUBO8042") {
            out += components.first { it.id == "KUBO_CORE" }
            when (c.kuboReinforcement) {
                si.ograjavizija.app.data.KuboReinforcement.ALU_20X60_DO_260 ->
                    out += components.first { it.id == "KUBO_20X60" }
                si.ograjavizija.app.data.KuboReinforcement.PROJEKTNA_OJACITEV ->
                    out += components.first { it.id == "KUBO_20X60" }
                else -> Unit
            }
        }

        when (c.category) {
            RoksalCategory.TERASA -> {
                out += components.first { it.id == "TERRACE_INOX_SCREW" }
                when (c.terraceSubstructure) {
                    si.ograjavizija.app.data.TerraceSubstructure.WPC_LETVE ->
                        out += components.first { it.id == "TERRACE_SUB" }
                    si.ograjavizija.app.data.TerraceSubstructure.ALU_CEV ->
                        out += components.first { it.id == "TERRACE_ALU" }
                    si.ograjavizija.app.data.TerraceSubstructure.ALU_MREZA ->
                        out += components.first { it.id == "TERRACE_GRID" }
                    else -> Unit
                }
                out += components.first { it.id == "TERRACE_TRIM" }
                out += components.first { it.id == "TERRACE_CLIP" }
                if (c.terraceBase == si.ograjavizija.app.data.TerraceBase.HIDROIZOLACIJA &&
                    !c.terraceScrewToBase) {
                    out += components.first { it.id == "TERRACE_RUBBER" }
                }
            }
            RoksalCategory.FASADA -> {
                out += components.first { it.id == "FACADE_CLIP" }
            }
            else -> Unit
        }

        return out.distinctBy { it.id }
    }

    fun renderProfilePreview(profileId: String, colourId: String, width: Int = 720, height: Int = 420, surfaceId: String = "", mountingVariant: String = ""): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val baseColour = colour(colourId).previewArgb
        val profile = profile(profileId)

        val productPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = baseColour
        }
        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shade(baseColour, 0.72f)
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
        }
        val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(150, 25, 28, 32)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }

        val left = 28f
        val right = width - 28f
        val top = 42f
        val bottom = height - 38f
        val spacing = 9f

        when (profileId) {
            "ROMB67" -> {
                var x = left - 20f
                while (x < right + 30f) {
                    val path = android.graphics.Path().apply {
                        moveTo(x + 34f, top)
                        lineTo(x + 67f, (top + bottom) / 2f)
                        lineTo(x + 34f, bottom)
                        lineTo(x, (top + bottom) / 2f)
                        close()
                    }
                    canvas.drawPath(path, productPaint)
                    canvas.drawPath(path, darkPaint)
                    x += 76f + spacing
                }
                val aluPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(170, 185, 190, 198)
                    strokeWidth = 4f
                }
                var coreX = left + 8f
                while (coreX < right) {
                    canvas.drawLine(coreX + 33f, top + 20f, coreX + 33f, bottom - 20f, aluPaint)
                    coreX += 86f
                }
            }
            "DESKA150" -> drawHorizontalBoards(canvas, left, right, top, bottom, 56f, spacing, productPaint, detailPaint, surfaceId)
            "KUBO8042" -> {
                var x = left
                val w = 42f
                while (x < right) {
                    canvas.drawRoundRect(RectF(x, top, minOf(right, x + w), bottom), 5f, 5f, productPaint)
                    canvas.drawRoundRect(RectF(x, top, minOf(right, x + w), bottom), 5f, 5f, darkPaint)
                    x += w + spacing
                }
                val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(140, 195, 200, 208)
                    strokeWidth = 3f
                }
                var ix = left + 21f
                while (ix < right) {
                    canvas.drawLine(ix, top + 12f, ix, bottom - 12f, inner)
                    ix += w + spacing
                }
            }
            "P57_32" -> {
                val face = when (mountingVariant) {
                    "32 mm" -> 30f
                    else -> 55f
                }
                var x = left
                while (x < right) {
                    canvas.drawRect(RectF(x, top, minOf(right, x + face), bottom), productPaint)
                    canvas.drawRect(RectF(x, top, minOf(right, x + face), bottom), darkPaint)
                    x += face + spacing
                }
            }
            else -> {
                val face = if (profileId == "P100") 62f else 70f
                var x = left
                while (x < right) {
                    canvas.drawRect(RectF(x, top, minOf(right, x + face), bottom), productPaint)
                    canvas.drawRect(RectF(x, top, minOf(right, x + face), bottom), darkPaint)
                    x += face + spacing
                }
            }
        }
        return bmp
    }

    fun renderTechnicalPreview(config: RoksalConfig, width: Int = 1200, height: Int = 700): Bitmap {
        val preview = renderProfilePreview(
            profileId = config.profileId,
            colourId = config.colourId,
            width = width,
            height = height,
            surfaceId = config.surfaceId,
            mountingVariant = config.mountingVariant,
        )
        return preview
    }

    private fun drawHorizontalBoards(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        boardHeight: Float,
        gap: Float,
        productPaint: Paint,
        detailPaint: Paint,
        surfaceId: String,
    ) {
        var y = top
        while (y < bottom) {
            val boardBottom = minOf(bottom, y + boardHeight)
            canvas.drawRect(RectF(left, y, right, boardBottom), productPaint)
            if (surfaceId.contains("GOSTA", ignoreCase = true)) {
                var ribX = left + 7f
                while (ribX < right) {
                    canvas.drawLine(ribX, y + 7f, ribX, boardBottom - 7f, detailPaint)
                    ribX += 10f
                }
            } else if (surfaceId.contains("ŠIROKA", ignoreCase = true) || surfaceId.contains("Siroka", ignoreCase = true)) {
                var ribX = left + 8f
                while (ribX < right) {
                    canvas.drawLine(ribX, y + 7f, ribX, boardBottom - 7f, detailPaint)
                    ribX += 22f
                }
            }
            y += boardHeight + gap
        }
    }

    private fun shade(argb: Int, factor: Float): Int {
        val a = (argb ushr 24) and 0xFF
        val r = ((argb ushr 16) and 0xFF) * factor
        val g = ((argb ushr 8) and 0xFF) * factor
        val b = (argb and 0xFF) * factor
        return android.graphics.Color.argb(a, r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255))
    }
}

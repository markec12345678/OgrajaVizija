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
            notes = "Pokončna izvedba; deska se lahko montira na 57 ali 32 mm stran ali razgibano; skrito vijačenje. Roksalove javne strani imajo glede števila barv neskladje, zato je zaloga vedno za preverjanje."
        ),
        RoksalProfile(
            id = "P100",
            name = "POLNA DESKA 100",
            dimensions = "100 × 12 × 5800 mm",
            faceWidthMm = 100,
            stockLengthsMm = listOf(5800),
            vertical = true,
            horizontal = false,
            maxSupportCm = 80,
            maxPostVerticalCm = 180,
            hiddenFixing = false,
            colourCount = 8,
            colourIds = listOf("AMAZON_WOOD", "ASH_WOOD", "GOLDEN_TEAK", "BURMA_TEAK", "OAK_WOOD", "WHITE", "RUSTIC_OAK", "RUSTIC_WALNUT"),
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
            colourIds = listOf("AMAZON_WOOD", "ASH_WOOD", "GOLDEN_TEAK", "BURMA_TEAK", "OAK_WOOD"),
            notes = "Pokončna ali prečna izvedba; Roksal na trenutni barvni strani navaja 5 odtenkov, medtem ko druga stran navaja 6; aplikacija zato ne dodaja šestega nepreverjenega odtenka."
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
            maxPostHorizontalCm = 130,
            hiddenFixing = false,
            colourCount = 7,
            surfaceOptions = listOf("KLASIK", "RUSTIK"),
            colourIds = listOf("AMAZON_WOOD", "ASH_WOOD", "GOLDEN_TEAK", "BURMA_TEAK", "OAK_WOOD", "RUSTIC_OAK", "RUSTIC_WALNUT"),
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
        RoksalComponent("FACADE_CLIP", "Fasadni klip", "kos", "Za pritrditev določenih fasadnih WoodCore profilov."),
    )

    private val profileMap = profiles.associateBy { it.id }
    private val colourMap = colours.associateBy { it.id }

    fun profile(id: String): RoksalProfile = profileMap[id] ?: profiles.first()

    fun colour(id: String): RoksalColour = colourMap[id] ?: colours.first()

    fun profilesFor(category: RoksalCategory, orientation: RoksalOrientation): List<RoksalProfile> {
        return when (category) {
            RoksalCategory.OGRAJA -> profiles.filter {
                if (orientation == RoksalOrientation.POKONCNA) it.vertical else it.horizontal
            }.filterNot { it.id == "KUBO8042" }
            RoksalCategory.PREGRADNA_STENA -> profiles.filter {
                if (orientation == RoksalOrientation.POKONCNA) it.vertical else it.horizontal
            }
            RoksalCategory.FASADA -> profiles.filter { it.id in setOf("P100", "ROMB67", "KUBO8042") }
            RoksalCategory.TERASA -> profiles.filter { it.id == "DESKA150" }
            RoksalCategory.NAPUSC -> profiles.filter { it.id == "P100" }
        }
    }

    fun colourOptions(profile: RoksalProfile): List<RoksalColour> =
        if (profile.colourIds.isNotEmpty()) profile.colourIds.mapNotNull { colourMap[it] }
        else colours.take(profile.colourCount)

    fun validate(c: RoksalConfig): ValidationResult {
        val p = profileMap[c.profileId] ?: return ValidationResult(false, listOf("Napaka: izbran profil ne obstaja."))
        val warnings = mutableListOf<String>()

        if (c.lengthM <= 0f) warnings += "Napaka: dolžina mora biti večja od 0 m."
        if (c.heightM <= 0f) warnings += "Napaka: višina mora biti večja od 0 m."
        if (c.boardGapMm < 0) warnings += "Napaka: razmak med deskami ne more biti negativen."

        if (c.orientation == RoksalOrientation.POKONCNA && !p.vertical)
            warnings += "Napaka: profil ni namenjen pokončni izvedbi."
        if (c.orientation == RoksalOrientation.PRECNA && !p.horizontal)
            warnings += "Napaka: profil ni namenjen prečni izvedbi."

        if (p.maxSupportCm != null && c.supportSpacingCm > p.maxSupportCm)
            warnings += "Razmak nosilcev " + c.supportSpacingCm.roundToInt() + " cm presega " + p.maxSupportCm + " cm."

        val maxPost = if (c.orientation == RoksalOrientation.POKONCNA) p.maxPostVerticalCm else p.maxPostHorizontalCm
        val heightLimitedMax = if (c.orientation == RoksalOrientation.POKONCNA && c.heightM > 1.5f && maxPost != null) minOf(maxPost, 150) else maxPost
        if (heightLimitedMax != null && c.postSpacingCm > heightLimitedMax)
            warnings += "Razmak stebrov " + c.postSpacingCm.roundToInt() + " cm presega " + heightLimitedMax + " cm za izbrano izvedbo."

        if (p.requiresAluCore)
            warnings += "Za ta profil je aluminijasto jedro obvezno; to mora biti vključeno v izvedbo."

        if (c.orientation == RoksalOrientation.POKONCNA && c.heightM > 1.5f && p.id in setOf("P100", "P128"))
            warnings += "Za ograjo nad 150 cm je pri teh profilih potreben manjši razmak stebrov; preveri aktualno montažno pravilo."

        if (c.boardGapMm > 30)
            warnings += "Razmak večji od 3 cm presega javno navedeni priporočeni razpon; zahtevaj potrditev Roksala."

        if (c.existingStructure == RoksalStructure.OBSTOJECA && heightLimitedMax != null &&
            c.postSpacingCm > heightLimitedMax)
            warnings += "Obstoječi stebri ne ustrezajo objavljenemu maksimalnemu razmaku tega profila."

        return ValidationResult(warnings.none { it.startsWith("Napaka:") }, warnings)
    }

    fun estimate(c: RoksalConfig): MaterialEstimate {
        val p = profileMap[c.profileId] ?: return MaterialEstimate(0, 0f, 0, 0, 0, 0, emptyList(), 0, listOf("Profil ni znan."))
        val gap = max(0, c.boardGapMm)
        val pitchMm = max(1, p.faceWidthMm + gap)
        val posts = max(2, ceil(c.lengthM * 100f / max(1f, c.postSpacingCm)).toInt() + 1)

        return if (c.orientation == RoksalOrientation.POKONCNA) {
            val verticalBoards = ceil(c.lengthM * 1000f / pitchMm).toInt()
            val stock = p.stockLengthsMm.maxOrNull() ?: 5800
            val heightMm = max(1, (c.heightM * 1000f).roundToInt())
            val boardsPerStock = max(1, stock / heightMm)
            val stockPieces = ceil(verticalBoards.toFloat() / boardsPerStock).toInt()
            val supports = max(2, ceil(c.lengthM * 100f / max(1f, c.supportSpacingCm)).toInt() + 1)
            MaterialEstimate(
                boards = stockPieces,
                stockLengthM = stock / 1000f,
                posts = posts,
                supports = supports,
                screws = verticalBoards * 2,
                handles = if (c.category == RoksalCategory.OGRAJA && c.handleIncluded) 1 else 0,
                components = componentsFor(c),
                estimatedWastePercent = 8,
                notes = listOf(
                    "Izračun je informativen in ne nadomešča Roksal razreza.",
                    "Standardna zaloga profila: " + (stock / 1000f) + " m.",
                    "Pri vratih in kotih so potrebne dodatne komponente."
                )
            )
        } else {
            val rows = ceil(c.heightM * 1000f / pitchMm).toInt()
            val stock = p.stockLengthsMm.maxOrNull() ?: 4000
            val piecesPerRow = max(1, ceil(c.lengthM * 1000f / stock).toInt())
            MaterialEstimate(
                boards = rows * piecesPerRow,
                stockLengthM = stock / 1000f,
                posts = posts,
                supports = posts,
                screws = rows * piecesPerRow * 2,
                handles = if (c.category == RoksalCategory.OGRAJA && c.handleIncluded) 1 else 0,
                components = componentsFor(c),
                estimatedWastePercent = 10,
                notes = listOf(
                    "Izračun je informativen in predvideva polne vrste po podani dolžini.",
                    "Optimalni razrez in dodatne komponente potrdi Roksal."
                )
            )
        }
    }

    fun componentsFor(c: RoksalConfig): List<RoksalComponent> {
        val out = mutableListOf<RoksalComponent>()
        if (c.category == RoksalCategory.OGRAJA && c.handleIncluded) out += components.first { it.id == "HANDLE_92" }
        out += components.first { it.id == "RF_SCREW" }
        if (c.profileId == "ROMB67") {
            out += components.first { it.id == "ROMB_ALU" }
            out += components.first { it.id == "ROMB_CAP_LR" }
            if (c.orientation == RoksalOrientation.PRECNA) out += components.first { it.id == "L_BRACKET" }
        }
        if (c.category == RoksalCategory.TERASA) {
            out += components.first { it.id == "TERRACE_SUB" }
            out += components.first { it.id == "TERRACE_ALU" }
            out += components.first { it.id == "TERRACE_TRIM" }
            out += components.first { it.id == "TERRACE_CLIP" }
        }
        if (c.category == RoksalCategory.FASADA) out += components.first { it.id == "FACADE_CLIP" }
        return out.distinctBy { it.id }
    }

    fun renderTechnicalPreview(config: RoksalConfig, width: Int = 1200, height: Int = 700): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val colour = colour(config.colourId).previewArgb
        val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour }
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 18f
            color = android.graphics.Color.rgb(55, 60, 68)
        }

        val left = 70f
        val right = width - 70f
        val top = 80f
        val bottom = height - 70f
        val gap = max(4f, config.boardGapMm * 1.2f)
        val boardPx = max(10f, minOf(70f, profile(config.profileId).faceWidthMm / 2f))

        if (config.orientation == RoksalOrientation.POKONCNA) {
            var x = left
            while (x < right) {
                canvas.drawRect(RectF(x, top, minOf(right, x + boardPx), bottom), boardPaint)
                x += boardPx + gap
            }
        } else {
            var y = top
            while (y < bottom) {
                canvas.drawRect(RectF(left, y, right, minOf(bottom, y + boardPx)), boardPaint)
                y += boardPx + gap
            }
        }
        canvas.drawRect(left, top, right, bottom, framePaint)
        return bmp
    }
}

package si.ograjavizija.app.data

import kotlinx.serialization.Serializable

/**
 * Domeni model projekta. Vse je shranjeno na napravi (brez računa, brez oblaka).
 * Struktura na disku:
 *   projects/<id>/project.json     -> ta datoteka
 *   projects/<id>/original.jpg     -> fotografija prostora (nikoli spremenjena)
 *   projects/<id>/product.jpg      -> fotografija izdelka (referenca)
 *   projects/<id>/cutout.png       -> izdelek z prosojnim ozadjem (RGBA)
 *   projects/<id>/mask.png         -> maska stare ograje (8-bit, bela = zamenjaj)
 *   projects/<id>/result.jpg       -> končni rezultat
 *   projects/<id>/variants/<n>.jpg -> rezultati variant (ograja A, B, C, ...)
 */
@Serializable
data class Project(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val scene: ImageRef? = null,
    val product: ImageRef? = null,
    val placement: Placement? = null,
    val maskMeta: MaskMeta? = null,
    val config: RoksalConfig? = null,
    val status: ProjectStatus = ProjectStatus.DRAFT,
    val variants: List<Variant> = emptyList(),
    val activeVariantId: String? = null,
    val settings: RenderSettings = RenderSettings(),
)

@Serializable
data class ImageRef(
    val fileName: String,
    val width: Int,
    val height: Int,
    /** EXIF orientacija originalne fotografije (0..7), da se ohrani pravilen prikaz. */
    val exifOrientation: Int = 1,
)

/**
 * Geometrija vstavljenega izdelka.
 * [corners] so štiri točke v koordinatah PRIZORA (original.jpg), v vrstnem redu
 * zgornji-levo, zgornji-desno, spodnji-desno, spodnji-levo. Iz njih se izračuna
 * homografija, ki preslika pravokotnik izdelka v ta štirikotnik.
 *
 * To je ključni del zahtevka "realistična perspektiva": uporabnik vleče vogale,
 * mi izračunamo projekcijsko preslikavo. Nobene "izmišljene" geometrije.
 */
@Serializable
data class Placement(
    val corners: List<Pt> = emptyList(),
    /** Dodatne ročne korekcije, uporabljene pred homografijo na sami referenci. */
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val skewX: Float = 0f,
    val skewY: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    /** Kateri del izdelka uporabimo (crop v normaliziranih koordinatah 0..1). */
    val cropRect: List<Float> = listOf(0f, 0f, 1f, 1f),
    val flipHorizontal: Boolean = false,
) {
    val isValid: Boolean get() = corners.size == 4
}

@Serializable
data class Pt(val x: Float, val y: Float)

@Serializable
data class MaskMeta(
    /** Kako je bila maska ustvarjena. */
    val source: MaskSource = MaskSource.MANUAL,
    val width: Int = 0,
    val height: Int = 0,
    val featherPx: Int = 6,
    val expandPx: Int = 4,
)

@Serializable
enum class MaskSource { MANUAL, BRUSH, RECT, TAP_SEGMENT, AUTO_SEGMENT, SAM_SERVER, IMPORT }

/**
 * Nastavitve izračuna. [mode] določa, kje teče AI:
 *  LOCAL_GEOMETRY -> brez strežnika, brez plačila: samo geometrija + barvno ujemanje + senca
 *  SERVER_REMOVAL -> strežnik odstrani staro ograjo (LaMa), mi sestavimo
 *  SERVER_FULL    -> strežnik naredi odstranitev + AI finalizacijo (FLUX.2 Klein / Qwen-Image-Edit)
 */
@Serializable
data class RenderSettings(
    val mode: RenderMode = RenderMode.LOCAL_GEOMETRY,
    val serverUrl: String = "",
    val provider: AiProvider = AiProvider.FLUX2_KLEIN_4B,
    val strength: Float = 0.35f,
    val steps: Int = 8,
    val colorMatch: Boolean = true,
    val colorMatchStrength: Float = 0.35f,
    val shadows: Boolean = true,
    val shadowOpacity: Float = 0.35f,
    val edgeFeatherPx: Int = 3,
    val poissonBlend: Boolean = false,
    val prompt: String = DEFAULT_PROMPT,
    val maxLongEdge: Int = 1600,
    val seed: Long = 1234,
) {
    companion object {
        /**
         * Prompt je napisan tako, da modelu izrecno prepove prerisovanje izdelka
         * in spreminjanje okolice (zahteva 6, 8 in 16 iz specifikacije).
         */
        const val DEFAULT_PROMPT =
            "Replace only the area inside the supplied mask with the supplied reference WoodCore product. " +
            "Preserve the exact selected profile, material, colour, pattern, orientation and proportions. " +
            "Do not redesign the product and do not invent additional elements. " +
            "Match perspective, scale, lighting, shadows, reflections and colour temperature to the original. " +
            "Keep every pixel outside the mask unchanged: building, windows, walls, floor, roof, vegetation, " +
            "sky, furniture and all other surroundings. Photorealistic."
    }
}

@Serializable
enum class RenderMode { LOCAL_GEOMETRY, SERVER_REMOVAL, SERVER_FULL }

@Serializable
enum class AiProvider {
    FLUX2_KLEIN_4B,        // Apache-2.0, ~8-13 GB VRAM, multi-reference editing
    QWEN_IMAGE_EDIT_2511,  // Apache-2.0, 8-16 GB VRAM (GGUF/FP8), multi-image
    SDXL_INPAINT_IPA,      // starejša pot (kot v osnovnem repoju) - samo za primerjavo
    LAMA_ONLY,             // brez difuzije: samo odstranitev + naša geometrijska sestava
}

@Serializable
enum class RoksalCategory { OGRAJA, PREGRADNA_STENA, TERASA, FASADA, NAPUSC }

@Serializable
enum class RoksalOrientation { POKONCNA, PRECNA }

@Serializable
enum class RoksalPrivacy { ODPRTA, SREDNJA, ZASEBNA }

@Serializable
enum class RoksalStructure { NOVA, OBSTOJECA, NEVEM }

@Serializable
enum class MeasurementStatus { OCENA, POTRJENO }

@Serializable
enum class MeasurementMethod { ZNANE_MERE, REFERENCA_NA_SLIKI, SEGMENTI }

@Serializable
enum class DeliveryPreference { DOSTAVA, DOSTAVA_IN_MONTAZA, OSEBNI_PREVZEM, NEVEM }

@Serializable
enum class CustomerType { FIZICNA_OSEBA, PODJETJE }

@Serializable
enum class FenceType { BALKON, DVORISCE, NEVEM }

@Serializable
enum class PostFixing { NA_PLOSCI, BOCNO, NEVEM }

@Serializable
enum class PostAppearance { OBOJE, SKRITO_ZUNAJ, NEVEM }

@Serializable
enum class TerraceBase {
    BETON,
    PLOSCICE,
    HIDROIZOLACIJA,
    PESek,
    ZEMLJA_TRAVA,
    NEVEM,
}

@Serializable
enum class TerraceSubstructure {
    WPC_LETVE,
    ALU_CEV,
    ALU_MREZA,
    KOVINSKA_KONSTRUKCIJA,
    NEVEM,
}

@Serializable
enum class TerraceDirection {
    V_SMER_PADCA,
    PRAVOKOTNO_NA_PADEC,
    NEVEM,
}

@Serializable
enum class FacadeLayout {
    ENOTEN,
    MESAN,
    NEVEM,
}

@Serializable
enum class KuboReinforcement {
    BREZ_DO_120,
    ALU_20X60_DO_260,
    PROJEKTNA_OJACITEV,
    NEVEM,
}

@Serializable
enum class ProjectStatus {
    DRAFT,
    CONFIGURED,
    VISUALIZED,
    QUOTE_PREPARED,
    QUOTE_REQUESTED,
    ROKSAL_REVIEW,
    SITE_MEASUREMENT,
    OFFER_SENT,
    ACCEPTED,
    INSTALLATION,
    COMPLETED,
    CANCELLED,
}

@Serializable
data class RoksalConfig(
    val category: RoksalCategory = RoksalCategory.OGRAJA,
    val orientation: RoksalOrientation = RoksalOrientation.POKONCNA,
    val profileId: String = "P128",
    val colourId: String = "BURMA_TEAK",
    val surfaceId: String = "",
    val mountingVariant: String = "",
    val boardGapMm: Int = 10,
    val privacy: RoksalPrivacy = RoksalPrivacy.SREDNJA,
    val lengthM: Float = 10f,
    val heightM: Float = 1.5f,
    val postSpacingCm: Float = 150f,
    val supportSpacingCm: Float = 100f,
    val supportCountByHeight: String = "",
    val segmentCount: Int = 1,
    val terraceWidthM: Float = 0f,
    val terraceSlopeCmPerM: Float = 1f,
    val terraceHeightCm: Float = 5.5f,
    val terraceBase: TerraceBase = TerraceBase.NEVEM,
    val terraceSubstructure: TerraceSubstructure = TerraceSubstructure.NEVEM,
    val terraceDirection: TerraceDirection = TerraceDirection.NEVEM,
    val terraceScrewToBase: Boolean = false,
    val facadeLayout: FacadeLayout = FacadeLayout.NEVEM,
    val facadeOpeningNotes: String = "",
    val kuboReinforcement: KuboReinforcement = KuboReinforcement.NEVEM,
    val gateType: String = "BREZ",
    val gateWidthM: Float = 0f,
    val gateHeightM: Float = 0f,
    val cuttingRequested: Boolean = true,
    val fenceType: FenceType = FenceType.NEVEM,
    val postFixing: PostFixing = PostFixing.NEVEM,
    val postAppearance: PostAppearance = PostAppearance.NEVEM,
    val handleIncluded: Boolean = true,
    val existingStructure: RoksalStructure = RoksalStructure.NEVEM,
    val measurementStatus: MeasurementStatus = MeasurementStatus.OCENA,
    val measurementMethod: MeasurementMethod = MeasurementMethod.ZNANE_MERE,
    val referenceDimensionMm: Float = 0f,
    val referenceLabel: String = "",
    val segmentLengthsText: String = "",
    val deliveryPreference: DeliveryPreference = DeliveryPreference.NEVEM,
    val customerType: CustomerType = CustomerType.FIZICNA_OSEBA,
    val customerName: String = "",
    val companyName: String = "",
    val taxNumber: String = "",
    val phone: String = "",
    val email: String = "",
    val invoiceAddress: String = "",
    val invoicePostalCode: String = "",
    val invoiceCity: String = "",
    val address: String = "",
    val projectPostalCode: String = "",
    val projectCity: String = "",
    val deliveryAddressDifferent: Boolean = false,
    val deliveryAddress: String = "",
    val deliveryPostalCode: String = "",
    val deliveryCity: String = "",
    val dataProcessingConsent: Boolean = false,
    val termsAccepted: Boolean = false,
    val newsletterOptIn: Boolean = false,
    val consentAtMillis: Long = 0L,
    val notes: String = "",
)

@Serializable
data class Variant(
    val id: String,
    val label: String,
    val productFileName: String,
    val cutoutFileName: String? = null,
    val resultFileName: String? = null,
    val placement: Placement? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val mode: RenderMode = RenderMode.LOCAL_GEOMETRY,
    val provider: AiProvider? = null,
    val elapsedMs: Long = 0,
)

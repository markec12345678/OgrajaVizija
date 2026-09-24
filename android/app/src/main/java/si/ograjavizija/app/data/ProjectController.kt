package si.ograjavizija.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import si.ograjavizija.app.imaging.BackgroundRemover
import si.ograjavizija.app.roksal.RoksalCatalog
import si.ograjavizija.app.imaging.BitmapIo
import si.ograjavizija.app.imaging.MaskEditor
import si.ograjavizija.app.imaging.PureCore
import si.ograjavizija.app.network.ApiClient
import java.io.File

/**
 * Vsa poslovna logika korakov 1-6. Zasloni kličejo samo sem, da ostanejo tanki.
 */
object ProjectController {

    data class RenderOutcome(
        val result: Bitmap,
        val changedOutsideMask: Int,
        val mode: RenderMode,
        val elapsedMs: Long,
        val note: String,
    )

    suspend fun createProject(name: String): Project {
        val p = Project(id = ProjectStore.newId(), name = name.ifBlank { "Projekt ${ProjectStore.list().size + 1}" })
        return ProjectStore.save(p)
    }

    suspend fun setScene(ctx: Context, p: Project, uri: String): Project? {
        val ref = ProjectStore.importImage(ctx, p, uri, "original.jpg", 2400) ?: return null
        return ProjectStore.save(p.copy(scene = ref))
    }

    suspend fun setProduct(ctx: Context, p: Project, uri: String, mode: RenderMode, serverUrl: String): Pair<Project?, String> {
        val ref = ProjectStore.importImage(ctx, p, uri, "product.jpg", 2000) ?: return null to "Slike ni bilo mogoče prebrati"
        val product = ProjectStore.readBitmap(p, ref.fileName, 2000) ?: return null to "Slike ni bilo mogoče dekodirati"

        // 🟡 poskusi strežnik (BiRefNet), sicer 🟢 lokalno (Otsu)
        var cut: BackgroundRemover.CutoutResult? = null
        var how = "🟢 lokalno (Otsu)"
        if (mode != RenderMode.LOCAL_GEOMETRY && serverUrl.isNotBlank()) {
            cut = runCatching {
                val bmp = ApiClient.removeBackground(serverUrl, product)
                val px = IntArray(bmp.width * bmp.height); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                val alpha = ByteArray(px.size) { i -> ((px[i] ushr 24) and 0xFF).toByte() }
                BackgroundRemover.CutoutResult(bmp, alpha, bmp.width, bmp.height,
                    BackgroundRemover.bboxOf(alpha, bmp.width, bmp.height), "SERVER_BIREFNET", 0.9f)
            }.getOrNull()
            if (cut != null) how = "🟡 strežnik (BiRefNet)"
        }
        if (cut == null) {
            cut = withContext(Dispatchers.Default) { BackgroundRemover.removeLocal(product) }
        }
        val cropped = BackgroundRemover.cropToMask(cut.bitmap, cut.bbox)
        ProjectStore.writeBitmap(p, "cutout.png", cropped)
        product.recycle()
        val p2 = ProjectStore.save(p.copy(product = ref.copy(fileName = "product.jpg")))
        return p2 to how
    }

    suspend fun applyRoksalConfig(p: Project, config: RoksalConfig): Project = withContext(Dispatchers.IO) {
        val preview = RoksalCatalog.renderTechnicalPreview(config)
        ProjectStore.writeBitmap(p, "product.jpg", preview, 95)
        ProjectStore.writeBitmap(p, "cutout.png", preview, 100)
        val ref = ImageRef("product.jpg", preview.width, preview.height)
        ProjectStore.save(p.copy(product = ref, config = config, status = ProjectStatus.CONFIGURED))
    }
    suspend fun loadMask(p: Project): MaskEditor? = withContext(Dispatchers.IO) {
        val scene = ProjectStore.readBitmap(p, "original.jpg", 2000) ?: return@withContext null
        val f = ProjectStore.file(p, "mask.png")
        if (f.exists()) {
            val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return@withContext null
            MaskEditor.fromGrayBitmap(Bitmap.createScaledBitmap(bmp, scene.width, scene.height, true)).also {
                bmp.recycle()
            }
        } else MaskEditor(scene.width, scene.height)
    }

    suspend fun saveMask(p: Project, editor: MaskEditor, source: MaskSource = MaskSource.MANUAL): Project {
        ProjectStore.writeBytes(p, "mask.png", editor.toPngBytes())
        return ProjectStore.save(
            p.copy(maskMeta = MaskMeta(source = source, width = editor.maskW, height = editor.maskH))
        )
    }

    fun defaultPlacement(sceneW: Int, sceneH: Int): Placement {
        // začetni štirikotnik: sredinski pas, rahlo perspektivno, da uporabnik vidi idejo
        return Placement(corners = listOf(
            Pt(sceneW * 0.12f, sceneH * 0.42f),
            Pt(sceneW * 0.88f, sceneH * 0.46f),
            Pt(sceneW * 0.88f, sceneH * 0.80f),
            Pt(sceneW * 0.12f, sceneH * 0.78f),
        ))
    }

    /**
     * 🟢 LOKALNI IZRIS: clean plate (lokalno inpaintanje maske) + homografija + LAB + senca.
     * Nikoli ne spremeni pikslov izven maske (preverjeno z changedOutsideMask == 0).
     */
    suspend fun renderLocal(p: Project, placement: Placement, settings: RenderSettings): RenderOutcome =
        withContext(Dispatchers.Default) {
            val t0 = System.currentTimeMillis()
            val scene = ProjectStore.readBitmap(p, "original.jpg", settings.maxLongEdge)
                ?: error("Ni originalne fotografije")
            val cutout = ProjectStore.readBitmap(p, "cutout.png", 1600)
                ?: error("Ni izreza izdelka. Ponovi korak 2.")
            val editor = loadMask(p) ?: error("Ni maske. Ponovi korak 3.")

            val sw = scene.width; val sh = scene.height
            val scenePx = IntArray(sw * sh); scene.getPixels(scenePx, 0, sw, 0, 0, sw, sh)
            val pw = cutout.width; val ph = cutout.height
            val prodPx = IntArray(pw * ph); cutout.getPixels(prodPx, 0, pw, 0, 0, pw, ph)

            val mask = editor.finalizeMask()
            val clean = PureCore.inpaintPyramid(scenePx, sw, sh, mask, editor.maskW, editor.maskH, 6)

            val r = PureCore.composite(
                clean, sw, sh, prodPx, pw, ph,
                placement.corners.map { floatArrayOf(it.x, it.y) },
                maskPx = mask, maskW = editor.maskW, maskH = editor.maskH,
                featherPx = settings.edgeFeatherPx,
                colorMatch = if (settings.colorMatch) PureCore.ColorMatchMode.LUMA_ONLY else PureCore.ColorMatchMode.NONE,
                colorMatchStrength = settings.colorMatchStrength,
                shadow = if (settings.shadows) PureCore.ShadowOptions(6, 14, 22, settings.shadowOpacity) else null,
            )
            val bmp = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            bmp.setPixels(r.pixels, 0, sw, 0, 0, sw, sh)
            RenderOutcome(bmp, r.changedOutsideMask, RenderMode.LOCAL_GEOMETRY,
                System.currentTimeMillis() - t0, "🟢 lokalno, brez AI")
        }

    /** 🟡 Strežniški izris: odstranitev (LaMa) + opcionalno AI finalizacija + hard-restore. */
    suspend fun renderServer(p: Project, placement: Placement, settings: RenderSettings, full: Boolean): RenderOutcome =
        withContext(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            val url = settings.serverUrl.ifBlank { AppState.serverUrl }
            require(url.isNotBlank()) { "Nastavi naslov strežnika (Nastavitve)." }
            val scene = ProjectStore.readBitmap(p, "original.jpg", settings.maxLongEdge) ?: error("Ni originala")
            val cutout = ProjectStore.readBitmap(p, "cutout.png", 1600) ?: error("Ni izreza")
            val editor = loadMask(p) ?: error("Ni maske")
            val mask = editor.finalizeMask()
            val maskPng = editor.toPngBytes()

            // 1) clean plate na strežniku (LaMa) — če pade, vzamemo lokalno
            val clean = runCatching { ApiClient.removeObject(url, scene, maskPng) }.getOrElse {
                val px = IntArray(scene.width * scene.height)
                scene.getPixels(px, 0, scene.width, 0, 0, scene.width, scene.height)
                val inp = PureCore.inpaintPyramid(px, scene.width, scene.height, mask, editor.maskW, editor.maskH, 6)
                val b = Bitmap.createBitmap(scene.width, scene.height, Bitmap.Config.ARGB_8888)
                b.setPixels(inp, 0, scene.width, 0, 0, scene.width, scene.height); b
            }

            // 2) geometrijska sestava na telefonu (deterministična)
            val local = withContext(Dispatchers.Default) {
                val sw = clean.width; val sh = clean.height
                val px = IntArray(sw * sh); clean.getPixels(px, 0, sw, 0, 0, sw, sh)
                val pw = cutout.width; val ph = cutout.height
                val ppx = IntArray(pw * ph); cutout.getPixels(ppx, 0, pw, 0, 0, pw, ph)
                val r = PureCore.composite(px, sw, sh, ppx, pw, ph,
                    placement.corners.map { floatArrayOf(it.x, it.y) },
                    maskPx = mask, maskW = editor.maskW, maskH = editor.maskH,
                    featherPx = settings.edgeFeatherPx,
                    colorMatch = if (settings.colorMatch) PureCore.ColorMatchMode.LUMA_ONLY else PureCore.ColorMatchMode.NONE,
                    colorMatchStrength = settings.colorMatchStrength,
                    shadow = if (settings.shadows) PureCore.ShadowOptions(6, 14, 22, settings.shadowOpacity) else null)
                val b = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
                b.setPixels(r.pixels, 0, sw, 0, 0, sw, sh); b to r.changedOutsideMask
            }

            if (!full) {
                return@withContext RenderOutcome(local.first, local.second, RenderMode.SERVER_REMOVAL,
                    System.currentTimeMillis() - t0, "🟡 strežnik: LaMa odstranitev + lokalna geometrija")
            }

            // 3) AI finalizacija (samo znotraj maske) + hard-restore izven maske
            val req = ApiClient.JobRequest(
                provider = settings.provider.name, prompt = settings.prompt,
                strength = settings.strength, steps = settings.steps, seed = settings.seed,
                maxLongEdge = settings.maxLongEdge, returnMaskedOnly = true,
            )
            val (ai, meta) = ApiClient.finalize(url, scene, local.first, maskPng, cutout, req)
            RenderOutcome(ai, meta.changedOutsideMask, RenderMode.SERVER_FULL,
                System.currentTimeMillis() - t0, "🟡 strežnik: LaMa + ${settings.provider.name}")
        }

    suspend fun saveResult(p: Project, bmp: Bitmap, variantLabel: String?): Project {
        ProjectStore.writeBitmap(p, "result.jpg", bmp, 95)
        val updated = if (variantLabel != null) {
            val v = Variant(id = "v" + System.currentTimeMillis().toString(36), label = variantLabel,
                productFileName = "product.jpg", cutoutFileName = "cutout.png", resultFileName = "result.jpg",
                placement = p.placement)
            p.copy(variants = p.variants + v, activeVariantId = v.id)
        } else p
        return ProjectStore.save(updated)
    }
}

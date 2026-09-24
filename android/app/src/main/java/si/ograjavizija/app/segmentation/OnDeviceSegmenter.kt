package si.ograjavizija.app.segmentation

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenterOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.Stroke
import java.io.File
import java.net.URL

/**
 * 🟢 Segmentacija NA NAPRAVI (brez strežnika, brez plačila).
 *
 * MediaPipe Interactive Segmenter (Google AI Edge, Apache-2.0; uradni vodič
 * developers.google.com/edge/mediapipe/solutions/vision/interactive_segmenter/android).
 * Model (~2–6 MB, int8) se prenese enkrat v mapo aplikacije, zato APK ostane majhen.
 *
 * Če model ni na voljo (ni interneta ob prvem zagonu), se "tap" način skrije in
 * ostaneta čopič ter pravokotnik — aplikacija nikoli ne neha delovati.
 *
 * Opomba: SAM 3 (facebookresearch/sam3) je pretežak za telefon in je pod "SAM License"
 * (ni OSI), zato teče samo na strežniku (🟡), kot opcija.
 */
object OnDeviceSegmenter {

    private const val MODEL_DIR = "models"
    private const val MODEL_FILE = "interactive_segmentation.task"
    private const val MODEL_URL =
        "https://storage.googleapis.com/mediapipe-models/interactive_segmenter_v2/" +
            "magic_touch/int8/latest/interactive_segmentation.task"

    private var segmenter: InteractiveSegmenter? = null
    var lastError: String? = null
        private set
    var modelReady: Boolean = false
        private set

    fun modelFile(ctx: Context): File =
        File(File(ctx.filesDir, MODEL_DIR).apply { mkdirs() }, MODEL_FILE)

    suspend fun ensureModel(ctx: Context): File? {
        val f = modelFile(ctx)
        if (f.exists() && f.length() > 100_000) return f
        return try {
            val tmp = File(f.parentFile, f.name + ".tmp")
            URL(MODEL_URL).openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (tmp.length() < 100_000) { tmp.delete(); null } else { tmp.renameTo(f); f }
        } catch (e: Exception) {
            lastError = "Model ni na voljo (${e.javaClass.simpleName}) — uporabi čopič/pravokotnik"
            null
        }
    }

    fun init(ctx: Context): Boolean {
        val f = modelFile(ctx)
        if (!f.exists()) { lastError = "Model ni prenesen"; modelReady = false; return false }
        return try {
            segmenter?.close()
            val base = BaseOptions.builder().setModelAssetPath(f.absolutePath).build()
            segmenter = InteractiveSegmenter.createFromOptions(
                ctx, InteractiveSegmenterOptions.builder().setBaseOptions(base).build())
            modelReady = true; lastError = null; true
        } catch (e: Throwable) {
            lastError = "Inicializacija segmentatorja ni uspela: ${e.message}"
            modelReady = false; false
        }
    }

    /**
     * Segmentira objekt na mestu dotika.
     * @param normX, normY normalizirani koordinati (0..1) glede na sliko
     * @return binarna maska (255 = objekt) v velikosti vhodne slike, ali null
     */
    fun segmentAt(bmp: Bitmap, normX: Float, normY: Float): ByteArray? {
        val s = segmenter ?: return null
        return try {
            val mp: MPImage = BitmapImageBuilder(bmp.copy(Bitmap.Config.ARGB_8888, false)).build()
            s.setImage(mp)
            val stroke = Stroke.builder()
                .setBrushMode(Stroke.BrushMode.POSITIVE)
                .setPoints(listOf(NormalizedKeypoint.create(normX.coerceIn(0f, 1f), normY.coerceIn(0f, 1f))))
                .setCompleted(true)
                .build()
            val out: MPImage = s.segment(listOf(stroke)) ?: return null
            val w = out.width; val h = out.height
            val buf = ByteBufferExtractor.extract(out).asFloatBuffer()
            val floats = FloatArray(w * h)
            buf.rewind()
            buf.get(floats)
            val mask = ByteArray(w * h)
            for (i in mask.indices) mask[i] = if (floats[i] > 0.5f) 255.toByte() else 0
            if (w == bmp.width && h == bmp.height) mask
            else scaleNearest(mask, w, h, bmp.width, bmp.height)
        } catch (e: Throwable) {
            lastError = e.message; null
        }
    }

    fun release() {
        runCatching { segmenter?.close() }
        segmenter = null; modelReady = false
    }

    private fun scaleNearest(src: ByteArray, sw: Int, sh: Int, dw: Int, dh: Int): ByteArray {
        val out = ByteArray(dw * dh)
        for (y in 0 until dh) for (x in 0 until dw) {
            val sx = minOf(sw - 1, maxOf(0, x * sw / dw))
            val sy = minOf(sh - 1, maxOf(0, y * sh / dh))
            out[y * dw + x] = src[sy * sw + sx]
        }
        return out
    }
}

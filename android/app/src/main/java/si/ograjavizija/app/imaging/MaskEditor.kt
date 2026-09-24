package si.ograjavizija.app.imaging

import android.graphics.Bitmap
import android.graphics.Color
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Urejevalnik maske (zahteva 4 in 5): čopič +, čopič -, pravokotnik, flood fill,
 * prevzem rezultata segmentacije, undo/redo.
 *
 * Deluje v koordinatah MASKE (maskW x maskH). Vmesnik preslika poteze uporabnika
 * iz koordinat zaslona v koordinate maske, zato je urejanje neodvisno od zooma.
 */
class MaskEditor(val maskW: Int, val maskH: Int, initial: ByteArray? = null) {

    enum class Tool { BRUSH_ADD, BRUSH_ERASE, RECT_ADD, RECT_ERASE, FILL_ADD, FILL_ERASE, SET_SEGMENT }

    var mask: ByteArray = initial?.copyOf() ?: ByteArray(maskW * maskH)
        private set

    private val undoStack = ArrayList<ByteArray>()
    private val redoStack = ArrayList<ByteArray>()
    private val maxHistory = 14

    var featherPx: Int = 3
    var expandPx: Int = 2

    val coverage: Float get() = mask.count { (it.toInt() and 0xFF) > 127 }.toFloat() / (maskW.toFloat() * maskH)
    val isEmpty: Boolean get() = mask.none { (it.toInt() and 0xFF) > 127 }
    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    /** Pokliči PRED spremembo (na začetku poteze / pred operacijo). */
    fun beginStroke() { pushHistory() }

    private fun pushHistory() {
        undoStack.add(mask.copyOf())
        if (undoStack.size > maxHistory) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(mask.copyOf())
        mask = undoStack.removeAt(undoStack.lastIndex)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(mask.copyOf())
        mask = redoStack.removeAt(redoStack.lastIndex)
    }

    fun clear() { pushHistory(); mask = ByteArray(maskW * maskH) }

    fun invert() {
        pushHistory()
        for (i in mask.indices) mask[i] = (255 - (mask[i].toInt() and 0xFF)).toByte()
    }

    /** Krogla s peresom na robu; add=true -> 255, add=false -> 0. */
    fun stamp(cx: Float, cy: Float, radiusPx: Float, add: Boolean) {
        val r = max(1f, radiusPx)
        val x0 = max(0, (cx - r).toInt()); val x1 = min(maskW - 1, (cx + r).toInt())
        val y0 = max(0, (cy - r).toInt()); val y1 = min(maskH - 1, (cy + r).toInt())
        val feather = max(1f, r * 0.35f)
        for (y in y0..y1) for (x in x0..x1) {
            val d = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))
            if (d > r) continue
            val soft = ((r - d) / feather).coerceIn(0f, 1f)
            val i = y * maskW + x
            val cur = mask[i].toInt() and 0xFF
            val target = if (add) 255 else 0
            val v = if (add) max(cur, (target * soft).roundToInt()) else min(cur, (255 - (255 * soft)).roundToInt() + (cur * (1 - soft)).roundToInt())
            mask[i] = v.coerceIn(0, 255).toByte()
        }
    }

    /** Vmesna točka med dvema položajema poteze (da ni lukenj pri hitrem risanju). */
    fun strokeLine(x0: Float, y0: Float, x1: Float, y1: Float, radiusPx: Float, add: Boolean) {
        val dist = sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
        val steps = max(1, (dist / max(1f, radiusPx * 0.4f)).toInt())
        for (s in 0..steps) {
            val t = s.toFloat() / steps
            stamp(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, radiusPx, add)
        }
    }

    fun fillRect(rx0: Float, ry0: Float, rx1: Float, ry1: Float, add: Boolean) {
        val x0 = min(rx0, rx1).toInt().coerceIn(0, maskW - 1)
        val x1 = max(rx0, rx1).toInt().coerceIn(0, maskW - 1)
        val y0 = min(ry0, ry1).toInt().coerceIn(0, maskH - 1)
        val y1 = max(ry0, ry1).toInt().coerceIn(0, maskH - 1)
        for (y in y0..y1) for (x in x0..x1) {
            val edge = min(min(x - x0, x1 - x), min(y - y0, y1 - y)).toFloat()
            val soft = (edge / max(1f, featherPx.toFloat())).coerceIn(0f, 1f)
            val i = y * maskW + x
            val v = if (add) max(mask[i].toInt() and 0xFF, (255 * soft).roundToInt())
                    else min(mask[i].toInt() and 0xFF, (255 * (1 - soft)).roundToInt())
            mask[i] = v.coerceIn(0, 255).toByte()
        }
    }

    /** Flood fill po barvni podobnosti (magic wand) — za "tap" način. */
    fun floodFill(src: Bitmap, sx: Float, sy: Float, tolerance: Int = 42, add: Boolean = true) {
        val w = min(src.width, maskW); val h = min(src.height, maskH)
        val px = IntArray(src.width * src.height)
        src.getPixels(px, 0, src.width, 0, 0, src.width, src.height)
        val startX = sx.toInt().coerceIn(0, src.width - 1); val startY = sy.toInt().coerceIn(0, src.height - 1)
        val seed = px[startY * src.width + startX]
        val sr = Color.red(seed); val sg = Color.green(seed); val sb = Color.blue(seed)
        val visited = BooleanArray(src.width * src.height)
        val q = ArrayDeque<Int>()
        q.add(startY * src.width + startX); visited[q.peek()] = true
        val tol = tolerance.toFloat() * tolerance
        while (q.isNotEmpty()) {
            val p = q.poll(); val x = p % src.width; val y = p / src.width
            val c = px[p]
            val dr = Color.red(c) - sr; val dg = Color.green(c) - sg; val db = Color.blue(c) - sb
            if (dr * dr + dg * dg + db * db > tol) continue
            val mx = min(maskW - 1, x * maskW / max(1, src.width))
            val my = min(maskH - 1, y * maskH / max(1, src.height))
            val mi = my * maskW + mx
            mask[mi] = if (add) 255.toByte() else 0
            if (x > 0) { val n = p - 1; if (!visited[n]) { visited[n] = true; q.add(n) } }
            if (x < src.width - 1) { val n = p + 1; if (!visited[n]) { visited[n] = true; q.add(n) } }
            if (y > 0) { val n = p - src.width; if (!visited[n]) { visited[n] = true; q.add(n) } }
            if (y < src.height - 1) { val n = p + src.width; if (!visited[n]) { visited[n] = true; q.add(n) } }
        }
    }

    /** Prevzem maske iz segmentacije (MediaPipe/SAM) — z združitvijo (OR) ali zamenjavo. */
    fun setFromSegmentation(other: ByteArray, ow: Int, oh: Int, replace: Boolean = true) {
        if (replace) pushHistory()
        val scaled = BitmapIo.scaleMask(other, ow, oh, maskW, maskH)
        for (i in mask.indices) {
            val v = scaled[i].toInt() and 0xFF
            mask[i] = (if (replace) v else max(mask[i].toInt() and 0xFF, v)).toByte()
        }
    }

    /** Razširi masko (dilate), da zajamemo rob stare ograje + perje. */
    fun finalizeMask(): ByteArray {
        var out = mask.copyOf()
        if (expandPx > 0) {
            val bmpMask = BackgroundRemover.morphology(out, maskW, maskH, closeRadius = expandPx, openRadius = 0)
            out = bmpMask
        }
        if (featherPx > 0) out = BackgroundRemover.featherMask(out, maskW, maskH, featherPx)
        return out
    }

    fun toBitmap(tint: Int = 0x77FF3B30): Bitmap = BitmapIo.maskToBitmap(mask, maskW, maskH, tint)

    /** PNG (siva -> alfa) za shranjevanje v projekt. */
    fun toPngBytes(): ByteArray {
        val bmp = Bitmap.createBitmap(maskW, maskH, Bitmap.Config.ARGB_8888)
        val px = IntArray(maskW * maskH)
        for (i in px.indices) {
            val v = mask[i].toInt() and 0xFF
            px[i] = Color.argb(255, v, v, v)
        }
        bmp.setPixels(px, 0, maskW, 0, 0, maskW, maskH)
        val b = BitmapIo.toPngBytes(bmp); bmp.recycle(); return b
    }

    companion object {
        fun fromGrayBitmap(bmp: Bitmap): MaskEditor {
            val w = bmp.width; val h = bmp.height
            val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
            val m = ByteArray(w * h)
            for (i in m.indices) {
                val v = max(max(Color.red(px[i]), Color.green(px[i])), Color.blue(px[i]))
                m[i] = (if (Color.alpha(px[i]) > 127) v else 0).toByte()
            }
            return MaskEditor(w, h, m)
        }

        fun fromAlphaBitmap(bmp: Bitmap): MaskEditor {
            val w = bmp.width; val h = bmp.height
            val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
            val m = ByteArray(w * h)
            for (i in m.indices) m[i] = ((px[i] ushr 24) and 0xFF).toByte()
            return MaskEditor(w, h, m)
        }
    }
}

package si.ograjavizija.app.imaging

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Odstranjevanje ozadja s fotografije IZDELKA (zahteva 4).
 *
 * Trije načini (označeni v vmesniku):
 *  🟢 LOCAL      — brez strežnika, brez modela: Otsu + morfologija + največja komponenta.
 *                  Deluje takoj in povsod, a je kakovost odvisna od kontrasta med
 *                  ograjo in ozadjem skladišča. Zato obstaja ročni čopič (+/-).
 *  🟢 ONNX       — BiRefNet-lite ONNX na napravi (onnxruntime-android), če uporabnik
 *                  naloži model (~100 MB) v mapo aplikacije. Brez strežnika, MIT licenca.
 *  🟡 SERVER     — BiRefNet na lastnem strežniku (najboljša kakovost).
 *
 * Opomba: namerno NE uporabljamo briaai/RMBG-2.0 — ta model je pod licenco
 * "bria-rmbg-2.0" (nekomercialno / zahteva licenco). BiRefNet je MIT.
 */
object BackgroundRemover {

    data class CutoutResult(
        val bitmap: Bitmap,
        val mask: ByteArray,
        val maskW: Int,
        val maskH: Int,
        val bbox: IntArray,
        val method: String,
        val confidence: Float,
    )

    /** 🟢 Povsem lokalno, brez modelov. */
    fun removeLocal(src: Bitmap, preferForegroundDark: Boolean? = null): CutoutResult {
        val w = src.width; val h = src.height
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val gray = ByteArray(w * h)
        val hist = IntArray(256)
        for (i in px.indices) {
            val g = (0.299 * Color.red(px[i]) + 0.587 * Color.green(px[i]) + 0.114 * Color.blue(px[i])).roundToInt()
            gray[i] = g.toByte(); hist[g.coerceIn(0, 255)]++
        }
        val t = otsu(hist, w * h)

        // Robovi slike so skoraj gotovo ozadje -> odločimo polariteto iz robov, ne ugibamo.
        var edgeSum = 0L; var edgeN = 0
        for (x in 0 until w) { edgeSum += (gray[x].toInt() and 0xFF); edgeSum += (gray[(h - 1) * w + x].toInt() and 0xFF); edgeN += 2 }
        for (y in 0 until h) { edgeSum += (gray[y * w].toInt() and 0xFF); edgeSum += (gray[y * w + w - 1].toInt() and 0xFF); edgeN += 2 }
        val edgeMean = if (edgeN > 0) edgeSum.toDouble() / edgeN else 128.0
        var sDark = 0.0; var nDark = 0; var sLight = 0.0; var nLight = 0
        for (i in gray.indices) {
            val g = gray[i].toInt() and 0xFF
            if (g <= t) { sDark += g; nDark++ } else { sLight += g; nLight++ }
        }
        val meanDark = if (nDark > 0) sDark / nDark else 0.0
        val meanLight = if (nLight > 0) sLight / nLight else 255.0
        // Rob slike = ozadje. Če je rob blizu razredu "temno", je predmet SVETEL in obratno.
        val backgroundIsDark = abs(edgeMean - meanDark) < abs(edgeMean - meanLight)
        val dark = preferForegroundDark ?: (!backgroundIsDark)
        var mask = ByteArray(w * h)
        for (i in mask.indices) {
            val g = gray[i].toInt() and 0xFF
            // Otsu vrne prag, ki JE se v temnem razredu -> temni razred je g <= t
            mask[i] = if ((dark && g <= t) || (!dark && g > t)) 255.toByte() else 0
        }

        mask = largestComponent(mask, w, h)
        mask = morphology(mask, w, h, closeRadius = max(2, min(w, h) / 220), openRadius = max(1, min(w, h) / 400))
        mask = morphology(mask, w, h, closeRadius = max(2, min(w, h) / 160), openRadius = 0)

        val bbox = bboxOf(mask, w, h)
        val feather = max(2, min(w, h) / 320)
        val soft = featherMask(mask, w, h, feather)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val outPx = IntArray(w * h)
        for (i in outPx.indices) {
            val a = soft[i].toInt() and 0xFF
            outPx[i] = (a shl 24) or (px[i] and 0x00FFFFFF)
        }
        out.setPixels(outPx, 0, w, 0, 0, w, h)

        val covered = mask.count { it > 8 }.toFloat() / (w * h)
        val conf = (1f - abs(covered - 0.35f)).coerceIn(0.05f, 0.99f)
        return CutoutResult(out, soft, w, h, bbox, "LOCAL_OTSU", conf)
    }

    // ------------------------------------------------------------ Otsu

    fun otsu(hist: IntArray, total: Int): Int {
        var sum = 0.0
        for (i in 0..255) sum += i.toDouble() * hist[i]
        var sumB = 0.0; var wB = 0; var maxVar = 0.0; var threshold = 127
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t.toDouble() * hist[t]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > maxVar) { maxVar = between; threshold = t }
        }
        return threshold
    }

    // ------------------------------------------------------------ morphology

    fun morphology(mask: ByteArray, w: Int, h: Int, closeRadius: Int, openRadius: Int): ByteArray {
        var m = mask
        if (closeRadius > 0) m = dilate(erode(m, w, h, closeRadius), w, h, closeRadius)
        if (openRadius > 0) m = erode(dilate(m, w, h, openRadius), w, h, openRadius)
        return m
    }

    private fun dilate(m: ByteArray, w: Int, h: Int, r: Int): ByteArray {
        val out = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            if ((m[y * w + x].toInt() and 0xFF) > 8) { out[y * w + x] = 255.toByte(); continue }
            var hit = false
            for (dy in -r..r) {
                val yy = y + dy; if (yy < 0 || yy >= h) continue
                for (dx in -r..r) {
                    val xx = x + dx; if (xx < 0 || xx >= w) continue
                    if (dx * dx + dy * dy > r * r) continue
                    if ((m[yy * w + xx].toInt() and 0xFF) > 8) { hit = true; break }
                }
                if (hit) break
            }
            if (hit) out[y * w + x] = 255.toByte()
        }
        return out
    }

    private fun erode(m: ByteArray, w: Int, h: Int, r: Int): ByteArray {
        val out = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            if ((m[y * w + x].toInt() and 0xFF) <= 8) continue
            var keep = true
            for (dy in -r..r) {
                val yy = y + dy; if (yy < 0 || yy >= h) { keep = false; break }
                for (dx in -r..r) {
                    val xx = x + dx; if (xx < 0 || xx >= w) { keep = false; break }
                    if (dx * dx + dy * dy > r * r) continue
                    if ((m[yy * w + xx].toInt() and 0xFF) <= 8) { keep = false; break }
                }
                if (!keep) break
            }
            if (keep) out[y * w + x] = 255.toByte()
        }
        return out
    }

    // ------------------------------------------------------------ components

    /** Obdrži samo največjo povezano komponento (4-sosednost, BFS). */
    fun largestComponent(mask: ByteArray, w: Int, h: Int): ByteArray {
        val labels = IntArray(w * h)
        var best = 0; var bestSize = 0; var label = 0
        val q = IntArray(w * h)
        for (start in mask.indices) {
            if ((mask[start].toInt() and 0xFF) <= 8 || labels[start] != 0) continue
            label++
            var head = 0; var tail = 0; q[tail++] = start; labels[start] = label
            while (head < tail) {
                val p = q[head++]; val x = p % w; val y = p / w
                if (x > 0) { val n = p - 1; if (labels[n] == 0 && (mask[n].toInt() and 0xFF) > 8) { labels[n] = label; q[tail++] = n } }
                if (x < w - 1) { val n = p + 1; if (labels[n] == 0 && (mask[n].toInt() and 0xFF) > 8) { labels[n] = label; q[tail++] = n } }
                if (y > 0) { val n = p - w; if (labels[n] == 0 && (mask[n].toInt() and 0xFF) > 8) { labels[n] = label; q[tail++] = n } }
                if (y < h - 1) { val n = p + w; if (labels[n] == 0 && (mask[n].toInt() and 0xFF) > 8) { labels[n] = label; q[tail++] = n } }
            }
            if (tail > bestSize) { bestSize = tail; best = label }
        }
        val out = ByteArray(w * h)
        for (i in out.indices) if (labels[i] == best) out[i] = 255.toByte()
        return out
    }

    fun bboxOf(mask: ByteArray, w: Int, h: Int): IntArray {
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((mask[y * w + x].toInt() and 0xFF) > 8) {
                if (x < x0) x0 = x; if (x > x1) x1 = x
                if (y < y0) y0 = y; if (y > y1) y1 = y
            }
        }
        return if (x1 < 0) intArrayOf(0, 0, w - 1, h - 1) else intArrayOf(x0, y0, x1, y1)
    }

    /** Mehčanje roba maske: linearni padec alfe na [radius] pikslih od roba. */
    fun featherMask(mask: ByteArray, w: Int, h: Int, radius: Int): ByteArray {
        if (radius <= 0) return mask
        // aproksimacija razdalje do roba z večkratnim box blur-jem
        val f = FloatArray(w * h) { if ((mask[it].toInt() and 0xFF) > 8) 1f else 0f }
        val blurred = PureCore.boxBlur(f, w, h, radius)
        val out = ByteArray(w * h)
        for (i in out.indices) out[i] = ((blurred[i].coerceIn(0f, 1f)) * 255).roundToInt().toByte()
        return out
    }

    /**
     * Odstrani "bel rob" (color decontamination): piksli na robu izreza, ki so po barvi
     * bližje ozadju kot predmetu, se razglasijo za prosojne. Zelo opazna izboljšava
     pri fotografijah iz skladišča z belim/svetlim ozadjem.
     */
    fun defringe(rgba: Bitmap, tolerance: Int = 34, passes: Int = 2): Bitmap {
        val w = rgba.width; val h = rgba.height
        val px = IntArray(w * h); rgba.getPixels(px, 0, w, 0, 0, w, h)
        // povprečna barva notranjosti = barva predmeta
        var sr = 0L; var sg = 0L; var sb = 0L; var n = 0L
        for (i in px.indices) {
            if (((px[i] ushr 24) and 0xFF) < 250) continue
            sr += Color.red(px[i]); sg += Color.green(px[i]); sb += Color.blue(px[i]); n++
        }
        if (n < 200) return rgba
        val mr = sr / n; val mg = sg / n; val mb = sb / n
        repeat(passes) {
            for (y in 0 until h) for (x in 0 until w) {
                val i = y * w + x
                val a = (px[i] ushr 24) and 0xFF
                if (a <= 0 || a >= 250) continue
                val edge = (x == 0 || y == 0 || x == w - 1 || y == h - 1) ||
                    (((px[i - 1] ushr 24) and 0xFF) < 128) || (((px[i + 1] ushr 24) and 0xFF) < 128) ||
                    (y > 0 && ((px[i - w] ushr 24) and 0xFF) < 128) || (y < h - 1 && ((px[i + w] ushr 24) and 0xFF) < 128)
                if (!edge) continue
                val d = sqrt(
                    (Color.red(px[i]) - mr).toDouble().let { it * it } +
                    (Color.green(px[i]) - mg).toDouble().let { it * it } +
                    (Color.blue(px[i]) - mb).toDouble().let { it * it }
                )
                if (d > tolerance * 2.2) px[i] = px[i] and 0x00FFFFFF
            }
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** Izreže RGBA bitmap po bbox-u maske (da je izdelek čim večji v okvirju). */
    fun cropToMask(rgba: Bitmap, bbox: IntArray, paddingPx: Int = 4): Bitmap {
        val x0 = max(0, bbox[0] - paddingPx); val y0 = max(0, bbox[1] - paddingPx)
        val x1 = min(rgba.width - 1, bbox[2] + paddingPx); val y1 = min(rgba.height - 1, bbox[3] + paddingPx)
        val w = x1 - x0 + 1; val h = y1 - y0 + 1
        if (w <= 2 || h <= 2) return rgba
        return Bitmap.createBitmap(rgba, x0, y0, w, h)
    }
}

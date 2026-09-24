package si.ograjavizija.app.imaging

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Jedro za obdelavo slik — ČISTI KOTLIN (brez android.*), da je testabilno na JVM
 * in enako na telefonu kot v testih. Vse slike so predstavljenje kot IntArray v
 * ARGB_8888 (enako kot android.graphics.Bitmap.getPixels).
 *
 * Ta razred izvaja zahteve 6, 7, 8 in 9 iz specifikacije:
 *  - referenca se PRESLIKA (homografija), ne generira,
 *  - perspektiva iz štirih vogalov,
 *  - barvno/svetlobno ujemanje in senca stika,
 *  - zaščita originala: piksli zunaj maske ostanejo bitno enaki.
 */
object PureCore {

    enum class ColorMatchMode { NONE, LAB_STATS, LUMA_ONLY }

    data class ShadowOptions(
        val offsetX: Int = 6,
        val offsetY: Int = 12,
        val blurPx: Int = 18,
        val opacity: Float = 0.35f,
    )

    data class CompositeResult(
        val pixels: IntArray,
        val width: Int,
        val height: Int,
        val changedTotal: Int,
        val changedOutsideMask: Int,
        val productPixels: Int,
        val bbox: IntArray,
    ) {
        /** Delež pikslov, ki so se spremenili IZVEN maske. Mora biti 0 (zahteva 9). */
        val leakageRatio: Float get() = if (width * height == 0) 0f else changedOutsideMask.toFloat() / (width * height)
    }

    // ============================================================ COMPOSITE

    /**
     * Sestavi izdelek v prizor.
     *
     * @param scenePx   piksli prizora (original ali 'clean plate' po odstranitvi stare ograje)
     * @param productPx piksli izdelka ARGB (alfa = izrez, prosojno ozadje)
     * @param corners   4 točke v koordinatah prizora: ZL, ZD, SD, SL
     * @param maskPx    maska območja (null = brez omejitve); vse izven maske ostane original
     */
    fun composite(
        scenePx: IntArray, sceneW: Int, sceneH: Int,
        productPx: IntArray, prodW: Int, prodH: Int,
        corners: List<FloatArray>,
        maskPx: ByteArray? = null, maskW: Int = 0, maskH: Int = 0,
        featherPx: Int = 3,
        colorMatch: ColorMatchMode = ColorMatchMode.LUMA_ONLY,
        colorMatchStrength: Float = 0.35f,
        shadow: ShadowOptions? = null,
    ): CompositeResult {
        require(corners.size == 4) { "composite() potrebuje natanko 4 vogale" }
        val out = scenePx.copyOf()

        val dst = Array(4) { doubleArrayOf(corners[it][0].toDouble(), corners[it][1].toDouble()) }
        val h = Homography.fromRectToQuad(prodW.toDouble(), prodH.toDouble(), dst)
        val hi = h.inverse()

        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (c in corners) {
            minX = min(minX, c[0]); minY = min(minY, c[1])
            maxX = max(maxX, c[0]); maxY = max(maxY, c[1])
        }
        val shadowBlur = shadow?.blurPx ?: 0
        val pad = max(featherPx, shadow?.offsetY ?: 0) + shadowBlur + 8
        val x0 = max(0, floor(minX - pad).toInt())
        val y0 = max(0, floor(minY - pad).toInt())
        val x1 = min(sceneW - 1, ceil(maxX + pad).toInt())
        val y1 = min(sceneH - 1, ceil(maxY + pad).toInt())
        if (x1 <= x0 || y1 <= y0) {
            return CompositeResult(out, sceneW, sceneH, 0, 0, 0, intArrayOf(x0, y0, x1, y1))
        }

        val cm = if (colorMatch != ColorMatchMode.NONE) {
            computeColorMatchModel(productPx, prodW, prodH, scenePx, sceneW, sceneH, corners, colorMatch)
        } else null

        val shadowBuf: FloatArray? = if (shadow != null) {
            buildShadow(
                x1 - x0 + 1, y1 - y0 + 1, x0, y0,
                productPx, prodW, prodH, hi, shadow,
            )
        } else null

        val bw = x1 - x0 + 1
        val feather = max(0, featherPx).toDouble()
        var changed = 0; var outside = 0; var productHits = 0

        for (j in 0 until (y1 - y0 + 1)) {
            val sy = y0 + j
            for (i in 0 until bw) {
                val sx = x0 + i
                val inMask = if (maskPx != null && maskW > 0) {
                    val mx = min(maskW - 1, max(0, sx * maskW / sceneW))
                    val my = min(maskH - 1, max(0, sy * maskH / sceneH))
                    (maskPx[my * maskW + mx].toInt() and 0xFF) > 8
                } else true
                val idx = sy * sceneW + sx
                val orig = out[idx]
                var cur = orig

                val p = hi.apply(sx.toDouble(), sy.toDouble())
                val a = sampleAlpha(productPx, prodW, prodH, p[0], p[1], feather)
                if (a > 0.0035f && inMask) {
                    val col = sampleArgb(productPx, prodW, prodH, p[0], p[1])
                    val matched = if (cm != null) applyColorMatch(col, cm, colorMatchStrength) else col
                    cur = alphaBlend(cur, matched, a)
                    productHits++
                    changed++
                }

                if (shadowBuf != null && inMask) {
                    val sa = shadowBuf[j * bw + i]
                    if (sa > 0.004f) {
                        val before = cur
                        cur = darken(cur, sa)
                        if (cur != before && a <= 0.0035f) changed++
                    }
                }

                if (!inMask) cur = orig   // ← ZAŠČITA ORIGINALA (zahteva 9)

                if (cur != orig) {
                    out[idx] = cur
                    if (!inMask) outside++
                }
            }
        }
        return CompositeResult(out, sceneW, sceneH, changed, outside, productHits, intArrayOf(x0, y0, x1, y1))
    }

    // ============================================================ SAMPLING

    fun sampleAlpha(px: IntArray, w: Int, h: Int, x: Double, y: Double, feather: Double): Float {
        if (x < 0 || y < 0 || x > w - 1.0 || y > h - 1.0) return 0f
        var a = ((bilinear(px, w, h, x, y) ushr 24) and 0xFF) / 255f
        if (feather > 0.5) {
            val dEdge = min(min(x, w - 1.0 - x), min(y, h - 1.0 - y))
            if (dEdge < feather) a *= (dEdge / feather).toFloat().coerceIn(0f, 1f)
        }
        return a.coerceIn(0f, 1f)
    }

    fun sampleArgb(px: IntArray, w: Int, h: Int, x: Double, y: Double): Int =
        bilinear(px, w, h, x, y)

    /** Biliniearno vzorčenje vseh 4 kanalov; izven slike vrne 0. */
    fun bilinear(px: IntArray, w: Int, h: Int, x: Double, y: Double): Int {
        if (x < 0 || y < 0 || x > w - 1.0 || y > h - 1.0) return 0
        val xi = x.toInt(); val yi = y.toInt()
        val fx = (x - xi).toFloat(); val fy = (y - yi).toFloat()
        val x2 = min(xi + 1, w - 1); val y2 = min(yi + 1, h - 1)
        val p00 = px[yi * w + xi]; val p10 = px[yi * w + x2]
        val p01 = px[y2 * w + xi]; val p11 = px[y2 * w + x2]
        val shift = intArrayOf(16, 8, 0, 24)
        var out = 0
        for (k in shift.indices) {
            val sh = shift[k]
            val v00 = (p00 ushr sh) and 0xFF; val v10 = (p10 ushr sh) and 0xFF
            val v01 = (p01 ushr sh) and 0xFF; val v11 = (p11 ushr sh) and 0xFF
            val top = v00 + (v10 - v00) * fx
            val bot = v01 + (v11 - v01) * fx
            val v = (top + (bot - top) * fy).roundToInt().coerceIn(0, 255)
            out = out or (v shl sh)
        }
        return out
    }

    fun alphaBlend(base: Int, fg: Int, a: Float): Int {
        val ia = 1f - a
        val r = ((fg ushr 16 and 0xFF) * a + (base ushr 16 and 0xFF) * ia).roundToInt()
        val g = ((fg ushr 8 and 0xFF) * a + (base ushr 8 and 0xFF) * ia).roundToInt()
        val b = ((fg and 0xFF) * a + (base and 0xFF) * ia).roundToInt()
        return -0x1000000 or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }

    fun darken(c: Int, amount: Float): Int {
        val f = (1f - amount.coerceIn(0f, 1f))
        val r = ((c ushr 16 and 0xFF) * f).roundToInt()
        val g = ((c ushr 8 and 0xFF) * f).roundToInt()
        val b = ((c and 0xFF) * f).roundToInt()
        return -0x1000000 or (r shl 16) or (g shl 8) or b
    }

    // ============================================================ COLOR MATCH

    data class ColorMatchModel(
        val mode: ColorMatchMode,
        val gainL: Float, val biasL: Float,
        val gainA: Float, val biasA: Float,
        val gainB: Float, val biasB: Float,
        val srcCount: Int, val ctxCount: Int,
    )

    /**
     * Model barvnega ujemanja: LAB statistika izdelka (samo vidni piksli) proti
     * LAB statistiki ozadja v prstanu okoli ciljnega štirikotnika.
     * Rezultat: ograja dobi enako svetilnost/barvno temperaturo kot fasada,
     * ne da bi ji spremenili odtenek (LUMA_ONLY ohrani a/b).
     */
    fun computeColorMatchModel(
        productPx: IntArray, pw: Int, ph: Int,
        scenePx: IntArray, sw: Int, sh: Int,
        corners: List<FloatArray>, mode: ColorMatchMode,
    ): ColorMatchModel? {
        var n = 0; var sL = 0.0; var sA = 0.0; var sB = 0.0
        var qL = 0.0; var qA = 0.0; var qB = 0.0
        val step = max(1, (pw * ph) / 160_000)
        var i = 0
        while (i < productPx.size) {
            val p = productPx[i]
            if (((p ushr 24) and 0xFF) >= 128) {
                val lab = rgbToLab((p ushr 16) and 0xFF, (p ushr 8) and 0xFF, p and 0xFF)
                sL += lab[0]; sA += lab[1]; sB += lab[2]
                qL += lab[0] * lab[0]; qA += lab[1] * lab[1]; qB += lab[2] * lab[2]
                n++
            }
            i += step
        }
        if (n < 40) return null
        val mL = sL / n; val mA = sA / n; val mB = sB / n
        val sdL = sqrt(max(1e-6, qL / n - mL * mL))
        val sdA = sqrt(max(1e-6, qA / n - mA * mA))
        val sdB = sqrt(max(1e-6, qB / n - mB * mB))

        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (c in corners) {
            minX = min(minX, c[0]); minY = min(minY, c[1])
            maxX = max(maxX, c[0]); maxY = max(maxY, c[1])
        }
        val diag = sqrt((maxX - minX) * (maxX - minX) + (maxY - minY) * (maxY - minY))
        val band = max(6f, diag * 0.12f)
        val bx0 = max(0, (minX - band).toInt()); val by0 = max(0, (minY - band).toInt())
        val bx1 = min(sw - 1, (maxX + band).toInt()); val by1 = min(sh - 1, (maxY + band).toInt())
        if (bx1 <= bx0 || by1 <= by0) return null

        var cn = 0; var cL = 0.0; var cA = 0.0; var cB = 0.0
        var cqL = 0.0; var cqA = 0.0; var cqB = 0.0
        val area = (bx1 - bx0 + 1) * (by1 - by0 + 1)
        val s2 = max(1, area / 90_000)
        var k = 0
        while (k < area) {
            val x = bx0 + k % (bx1 - bx0 + 1); val y = by0 + k / (bx1 - bx0 + 1)
            val inside = x >= minX && x <= maxX && y >= minY && y <= maxY
            if (!inside) {
                val p = scenePx[y * sw + x]
                val lab = rgbToLab((p ushr 16) and 0xFF, (p ushr 8) and 0xFF, p and 0xFF)
                cL += lab[0]; cA += lab[1]; cB += lab[2]
                cqL += lab[0] * lab[0]; cqA += lab[1] * lab[1]; cqB += lab[2] * lab[2]
                cn++
            }
            k += s2
        }
        if (cn < 40) return null
        val xL = cL / cn; val xA = cA / cn; val xB = cB / cn
        val xsL = sqrt(max(1e-6, cqL / cn - xL * xL))
        val xsA = sqrt(max(1e-6, cqA / cn - xA * xA))
        val xsB = sqrt(max(1e-6, cqB / cn - xB * xB))

        val cl = { v: Double -> v.coerceIn(0.55, 1.8).toFloat() }
        val gL = cl(xsL / sdL); val gA = cl(xsA / sdA); val gB = cl(xsB / sdB)
        return ColorMatchModel(
            mode, gL, (xL - mL * gL).toFloat(), gA, (xA - mA * gA).toFloat(), gB, (xB - mB * gB).toFloat(), n, cn,
        )
    }

    fun applyColorMatch(color: Int, cm: ColorMatchModel, strength: Float): Int {
        val s = strength.coerceIn(0f, 1f)
        if (s <= 0.001f) return color
        val lab = rgbToLab((color ushr 16) and 0xFF, (color ushr 8) and 0xFF, color and 0xFF)
        var l = lab[0] * (1f + (cm.gainL - 1f) * s) + cm.biasL * s
        var a = lab[1]; var b = lab[2]
        if (cm.mode == ColorMatchMode.LAB_STATS) {
            a = lab[1] * (1f + (cm.gainA - 1f) * s) + cm.biasA * s
            b = lab[2] * (1f + (cm.gainB - 1f) * s) + cm.biasB * s
        }
        val rgb = labToRgb(l.coerceIn(0.0, 100.0), a, b)
        return -0x1000000 or (rgb[0].coerceIn(0, 255) shl 16) or (rgb[1].coerceIn(0, 255) shl 8) or rgb[2].coerceIn(0, 255)
    }

    // ============================================================ SHADOW

    /**
     * Senca stika iz ALFA maske izdelka: zamaknjena v smeri svetlobe in zabrisana.
     * Ni generativna — deterministična, zato ne more "izmisliti" ničesar.
     */
    fun buildShadow(
        bw: Int, bh: Int, x0: Int, y0: Int,
        productPx: IntArray, pw: Int, ph: Int,
        invH: Homography, opt: ShadowOptions,
    ): FloatArray {
        val mask = FloatArray(bw * bh)
        val dx = -opt.offsetX.toDouble(); val dy = -opt.offsetY.toDouble()
        for (j in 0 until bh) {
            val sy0 = (y0 + j) + dy
            for (i in 0 until bw) {
                val p = invH.apply((x0 + i) + dx, sy0)
                val px = p[0]; val py = p[1]
                if (px < 0 || py < 0 || px > pw - 1.0 || py > ph - 1.0) continue
                val a = ((productPx[py.toInt() * pw + px.toInt()] ushr 24) and 0xFF) / 255f
                if (a > 0.05f) mask[j * bw + i] = a * opt.opacity
            }
        }
        return boxBlur(mask, bw, bh, opt.blurPx)
    }

    fun boxBlur(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        if (radius <= 0 || w <= 0 || h <= 0) return src
        val tmp = FloatArray(w * h); val out = FloatArray(w * h)
        val r = min(radius, min(w, h) / 2).coerceAtLeast(1)
        val div = (2 * r + 1).toFloat()
        for (y in 0 until h) {
            var sum = 0f
            for (x in -r..r) sum += src[y * w + x.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[y * w + x] = sum / div
                sum += src[y * w + min(x + r + 1, w - 1)] - src[y * w + max(x - r, 0)]
            }
        }
        for (x in 0 until w) {
            var sum = 0f
            for (y in -r..r) sum += tmp[y.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = sum / div
                sum += tmp[min(y + r + 1, h - 1) * w + x] - tmp[max(y - r, 0) * w + x]
            }
        }
        return out
    }

    // ============================================================ INPAINT (lokalno)

    /**
     * Lokalno zapolnjevanje luknje (brez AI, brez strežnika) — piramidna propagacija.
     * Dovolj dobro za predogled; za končni rezultat uporabi strežnik z LaMa.
     */
    fun inpaintPyramid(px: IntArray, w: Int, h: Int, mask: ByteArray, maskW: Int, maskH: Int, levels: Int = 5): IntArray {
        var cur = px.copyOf(); var cw = w; var ch = h
        var cm = ByteArray(cw * ch)
        for (i in cm.indices) {
            val sx = min(maskW - 1, (i % cw) * maskW / cw)
            val sy = min(maskH - 1, (i / cw) * maskH / ch)
            cm[i] = mask[sy * maskW + sx]
        }
        data class Level(val px: IntArray, val mask: ByteArray, val w: Int, val h: Int)
        val stack = ArrayList<Level>()
        repeat(max(0, levels)) {
            val nw = max(8, cw / 2); val nh = max(8, ch / 2)
            val np = IntArray(nw * nh); val nm = ByteArray(nw * nh)
            for (y in 0 until nh) for (x in 0 until nw) {
                val sx = min(cw - 1, x * 2); val sy = min(ch - 1, y * 2)
                var r = 0; var g = 0; var b = 0; var cnt = 0; var hole = 0
                for (dy in 0..1) for (dx in 0..1) {
                    val xx = min(cw - 1, sx + dx); val yy = min(ch - 1, sy + dy)
                    val p = cur[yy * cw + xx]
                    if ((cm[yy * cw + xx].toInt() and 0xFF) > 8) hole++
                    else { r += (p ushr 16) and 0xFF; g += (p ushr 8) and 0xFF; b += p and 0xFF; cnt++ }
                }
                np[y * nw + x] = if (cnt > 0) -0x1000000 or (r / cnt shl 16) or (g / cnt shl 8) or (b / cnt)
                                 else cur[sy * cw + sx]
                nm[y * nw + x] = if (hole == 4) 255.toByte() else 0
            }
            stack.add(Level(cur, cm, cw, ch))
            cur = np; cm = nm; cw = nw; ch = nh
        }
        fillHolesLocal(cur, cm, cw, ch, 6)
        for (lvl in stack.indices.reversed()) {
            val L = stack[lvl]
            val res = IntArray(L.w * L.h)
            for (y in 0 until L.h) for (x in 0 until L.w) {
                val i = y * L.w + x
                res[i] = if ((L.mask[i].toInt() and 0xFF) <= 8) L.px[i]
                         else bilinear(cur, cw, ch, (x + 0.5) * cw / L.w, (y + 0.5) * ch / L.h)
            }
            cur = res; cm = L.mask; cw = L.w; ch = L.h
        }
        return cur
    }

    private fun fillHolesLocal(px: IntArray, m: ByteArray, w: Int, h: Int, r: Int) {
        for (y in 0 until h) for (x in 0 until w) {
            if ((m[y * w + x].toInt() and 0xFF) <= 8) continue
            var sr = 0; var sg = 0; var sb = 0; var c = 0
            for (yy in max(0, y - r)..min(h - 1, y + r)) for (xx in max(0, x - r)..min(w - 1, x + r)) {
                if ((m[yy * w + xx].toInt() and 0xFF) > 8) continue
                val p = px[yy * w + xx]; sr += (p ushr 16) and 0xFF; sg += (p ushr 8) and 0xFF; sb += p and 0xFF; c++
            }
            if (c > 0) px[y * w + x] = -0x1000000 or (sr / c shl 16) or (sg / c shl 8) or (sb / c)
        }
    }

    // ============================================================ BACKGROUND REMOVAL

    data class Cutout(val rgba: IntArray, val w: Int, val h: Int, val alpha: ByteArray,
                      val bbox: IntArray, val method: String, val confidence: Float)

    /**
     * 🟢 Lokalno odstranjevanje ozadja s fotografije izdelka: Otsu + največja komponenta
     * + morfologija + mehčanje roba + odstranjevanje barvnega obrisa (defringe).
     */
    fun removeBackgroundLocal(px: IntArray, w: Int, h: Int, preferDarkForeground: Boolean? = null): Cutout {
        val gray = IntArray(w * h)
        val hist = IntArray(256)
        for (i in px.indices) {
            val g = (0.299 * (px[i] ushr 16 and 0xFF) + 0.587 * (px[i] ushr 8 and 0xFF) + 0.114 * (px[i] and 0xFF)).roundToInt()
                .coerceIn(0, 255)
            gray[i] = g; hist[g]++
        }
        val t = otsu(hist, w * h)

        // Polariteta: robovi fotografije so skoraj vedno OZADJE. Primerjamo
        // povprecje robov s povprecjem obeh razredov, ki ju Otsu loci.
        var es = 0.0; var en = 0
        for (x in 0 until w) { es += gray[x]; es += gray[(h - 1) * w + x]; en += 2 }
        for (y in 0 until h) { es += gray[y * w]; es += gray[y * w + w - 1]; en += 2 }
        val edgeMean = if (en > 0) es / en else 128.0
        var sDark = 0.0; var nDark = 0; var sLight = 0.0; var nLight = 0
        for (i in gray.indices) {
            if (gray[i] <= t) { sDark += gray[i]; nDark++ } else { sLight += gray[i]; nLight++ }
        }
        val meanDark = if (nDark > 0) sDark / nDark else 0.0
        val meanLight = if (nLight > 0) sLight / nLight else 255.0
        // Rob slike = ozadje. Če je rob blizu razredu "temno", je predmet SVETEL in obratno.
        val backgroundIsDark = abs(edgeMean - meanDark) < abs(edgeMean - meanLight)
        val dark = preferDarkForeground ?: (!backgroundIsDark)

        var mask = ByteArray(w * h)
        for (i in mask.indices) {
            val g = gray[i]
            // Otsu vrne prag, ki JE se v temnem razredu -> temni razred je g <= t
            mask[i] = if ((dark && g <= t) || (!dark && g > t)) 255.toByte() else 0
        }
        mask = largestComponent(mask, w, h)
        mask = morphology(mask, w, h, max(2, min(w, h) / 220), max(1, min(w, h) / 400))
        mask = morphology(mask, w, h, max(2, min(w, h) / 160), 0)
        val bbox = bboxOf(mask, w, h)
        val alpha = featherMask(mask, w, h, max(2, min(w, h) / 320))

        val out = IntArray(w * h)
        for (i in out.indices) out[i] = ((alpha[i].toInt() and 0xFF) shl 24) or (px[i] and 0x00FFFFFF)

        val covered = alpha.count { (it.toInt() and 0xFF) > 127 }.toFloat() / (w * h)
        val conf = (1f - abs(covered - 0.32f) * 1.6f).coerceIn(0.05f, 0.99f)
        return Cutout(out, w, h, alpha, bbox, "LOCAL_OTSU", conf)
    }

    fun otsu(hist: IntArray, total: Int): Int {
        var sum = 0.0; for (i in 0..255) sum += i.toDouble() * hist[i]
        var sumB = 0.0; var wB = 0; var maxVar = 0.0; var th = 127
        for (t in 0..255) {
            wB += hist[t]; if (wB == 0) continue
            val wF = total - wB; if (wF == 0) break
            sumB += t.toDouble() * hist[t]
            val mB = sumB / wB; val mF = (sum - sumB) / wF
            val v = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (v > maxVar) { maxVar = v; th = t }
        }
        return th
    }

    fun largestComponent(mask: ByteArray, w: Int, h: Int): ByteArray {
        val labels = IntArray(w * h); val q = IntArray(w * h)
        var best = 0; var bestSize = 0; var label = 0
        for (start in mask.indices) {
            if ((mask[start].toInt() and 0xFF) <= 8 || labels[start] != 0) continue
            label++; var head = 0; var tail = 0
            q[tail++] = start; labels[start] = label
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

    fun morphology(mask: ByteArray, w: Int, h: Int, closeR: Int, openR: Int): ByteArray {
        var m = mask
        if (closeR > 0) m = dilate(erode(m, w, h, closeR), w, h, closeR)
        if (openR > 0) m = erode(dilate(m, w, h, openR), w, h, openR)
        return m
    }

    private fun dilate(m: ByteArray, w: Int, h: Int, r: Int): ByteArray {
        val out = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            if ((m[y * w + x].toInt() and 0xFF) > 8) { out[y * w + x] = 255.toByte(); continue }
            var hit = false
            outer@ for (dy in -r..r) {
                val yy = y + dy; if (yy < 0 || yy >= h) continue
                for (dx in -r..r) {
                    val xx = x + dx; if (xx < 0 || xx >= w) continue
                    if (dx * dx + dy * dy > r * r) continue
                    if ((m[yy * w + xx].toInt() and 0xFF) > 8) { hit = true; break@outer }
                }
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
            outer@ for (dy in -r..r) {
                val yy = y + dy; if (yy < 0 || yy >= h) { keep = false; break }
                for (dx in -r..r) {
                    val xx = x + dx; if (xx < 0 || xx >= w) { keep = false; break }
                    if (dx * dx + dy * dy > r * r) continue
                    if ((m[yy * w + xx].toInt() and 0xFF) <= 8) { keep = false; break@outer }
                }
            }
            if (keep) out[y * w + x] = 255.toByte()
        }
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

    fun featherMask(mask: ByteArray, w: Int, h: Int, radius: Int): ByteArray {
        if (radius <= 0) return mask
        val f = FloatArray(w * h) { if ((mask[it].toInt() and 0xFF) > 8) 1f else 0f }
        val bl = boxBlur(f, w, h, radius)
        val out = ByteArray(w * h)
        for (i in out.indices) out[i] = (bl[i].coerceIn(0f, 1f) * 255).roundToInt().toByte()
        return out
    }

    // ============================================================ COLOR SPACE

    /** sRGB (0..255) -> CIE LAB (D65). L je vedno v 0..100. */
    fun rgbToLab(r: Int, g: Int, b: Int): DoubleArray {
        val xyz = rgbToXyz(r, g, b)
        val f = { t: Double -> if (t > 0.008856451679) cbrt(t) else (t * 903.2962962 + 16.0) / 116.0 }
        val fx = f(xyz[0] / 0.95047); val fy = f(xyz[1]); val fz = f(xyz[2] / 1.08883)
        val l = (116.0 * fy - 16.0).coerceIn(0.0, 100.0)
        return doubleArrayOf(l, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    fun labToRgb(l: Double, a: Double, b: Double): IntArray {
        val fy = (l + 16.0) / 116.0; val fx = fy + a / 500.0; val fz = fy - b / 200.0
        val fi = { t: Double ->
            val t3 = t * t * t
            if (t3 > 0.008856451679) t3 else (116.0 * t - 16.0) / 903.2962962
        }
        val x = 0.95047 * fi(fx); val y = fi(fy); val z = 1.08883 * fi(fz)
        val r = x * 3.2406 + y * -1.5372 + z * -0.4986
        val g = x * -0.9689 + y * 1.8758 + z * 0.0415
        val bb = x * 0.0557 + y * -0.2040 + z * 1.0570
        val gam = { c: Double ->
            val v = if (c > 0.0031308) 1.055 * c.pow(1.0 / 2.4) - 0.055 else 12.92 * c
            (v * 255.0).roundToInt().coerceIn(0, 255)
        }
        return intArrayOf(gam(r), gam(g), gam(bb))
    }

    private fun rgbToXyz(r: Int, g: Int, b: Int): DoubleArray {
        val lin = { v: Int ->
            val c = v / 255.0
            if (c > 0.04045) Math.pow((c + 0.055) / 1.055, 2.4) else c / 12.92
        }
        val rr = lin(r); val gg = lin(g); val bb = lin(b)
        return doubleArrayOf(
            rr * 0.4124 + gg * 0.3576 + bb * 0.1805,
            rr * 0.2126 + gg * 0.7152 + bb * 0.0722,
            rr * 0.0193 + gg * 0.1192 + bb * 0.9505,
        )
    }

    /** Povprecno pomanjsanje (area average) — za predoglede in shranjevanje. */
    fun downscale(px: IntArray, w: Int, h: Int, maxEdge: Int): Triple<IntArray, Int, Int> {
        val long = maxOf(w, h)
        if (long <= maxEdge) return Triple(px, w, h)
        val nw = maxOf(1, w * maxEdge / long); val nh = maxOf(1, h * maxEdge / long)
        val out = IntArray(nw * nh)
        for (y in 0 until nh) for (x in 0 until nw) {
            val sx0 = x * w / nw; val sx1 = maxOf(sx0 + 1, (x + 1) * w / nw)
            val sy0 = y * h / nh; val sy1 = maxOf(sy0 + 1, (y + 1) * h / nh)
            var r = 0; var g = 0; var b = 0; var a = 0; var n = 0
            for (yy in sy0 until minOf(sy1, h)) for (xx in sx0 until minOf(sx1, w)) {
                val p = px[yy * w + xx]
                r += (p ushr 16) and 0xFF; g += (p ushr 8) and 0xFF; b += p and 0xFF; a += (p ushr 24) and 0xFF; n++
            }
            if (n == 0) continue
            out[y * nw + x] = ((a / n) shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
        return Triple(out, nw, nh)
    }

    /** Sestavi dve sliki v montazo (levo|desno) za primerjavo PREJ|POTEM. */
    fun sideBySide(a: IntArray, aw: Int, ah: Int, b: IntArray, bw: Int, bh: Int, gap: Int = 6): Triple<IntArray, Int, Int> {
        val hgt = maxOf(ah, bh)
        val wdt = aw + bw + gap
        val out = IntArray(wdt * hgt) { -0x1000000 or (18 shl 16) or (20 shl 8) or 26 }
        for (y in 0 until ah) for (x in 0 until aw) out[y * wdt + x] = a[y * aw + x]
        for (y in 0 until bh) for (x in 0 until bw) out[y * wdt + aw + gap + x] = b[y * bw + x]
        return Triple(out, wdt, hgt)
    }

    // ============================================================ METRICS

    /** Koliko pikslov se razlikuje za več kot [tol] (za preverjanje zahteve 9). */
    fun diffCount(a: IntArray, b: IntArray, tol: Int = 2): Int {
        var d = 0
        val n = min(a.size, b.size)
        for (i in 0 until n) {
            val dr = abs((a[i] ushr 16 and 0xFF) - (b[i] ushr 16 and 0xFF))
            val dg = abs((a[i] ushr 8 and 0xFF) - (b[i] ushr 8 and 0xFF))
            val db = abs((a[i] and 0xFF) - (b[i] and 0xFF))
            if (maxOf(dr, dg, db) > tol) d++
        }
        return d
    }

    /** Povprečna barvna razlika (ΔE-like) med dvema slikama na maskiranem območju. */
    fun meanAbsDelta(a: IntArray, b: IntArray, mask: ByteArray?, maskW: Int, maskH: Int, w: Int, h: Int): Double {
        var s = 0.0; var n = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (mask != null) {
                val mx = min(maskW - 1, x * maskW / w); val my = min(maskH - 1, y * maskH / h)
                if ((mask[my * maskW + mx].toInt() and 0xFF) <= 8) continue
            }
            val i = y * w + x
            s += abs((a[i] ushr 16 and 0xFF) - (b[i] ushr 16 and 0xFF))
            s += abs((a[i] ushr 8 and 0xFF) - (b[i] ushr 8 and 0xFF))
            s += abs((a[i] and 0xFF) - (b[i] and 0xFF))
            n += 3
        }
        return if (n == 0) 0.0 else s / n
    }
}

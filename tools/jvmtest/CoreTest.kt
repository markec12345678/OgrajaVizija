package test

import si.ograjavizija.app.imaging.Homography
import si.ograjavizija.app.imaging.PureCore
import java.io.File
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * JVM test jedra (brez Androida). Dokazuje, da geometrija in zaščita originala delujeta.
 * Zazene: ./run_core_tests.sh  -> izpise rezultate in zapise slike v tools/jvmtest/out/
 */
object CoreTest {
    var passed = 0; var failed = 0
    fun check(name: String, cond: Boolean, detail: String = "") {
        if (cond) { passed++; println("  ✅ $name ${if (detail.isNotEmpty()) "($detail)" else ""}") }
        else { failed++; println("  ❌ $name ${if (detail.isNotEmpty()) "-> $detail" else ""}") }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File("out"); out.mkdirs()
        println("=== TEST A: homografija (4 vogali -> projekcijska preslikava) ===")
        testHomography()
        println("=== TEST A2: barvni prostor sRGB <-> LAB ===")
        testLab()
        println("=== TEST B: zaščita originala (zahteva 9) ===")
        testNoLeakage()
        println("=== TEST C: barvno ujemanje (LAB) ===")
        testColorMatch()
        println("=== TEST D: senca stika ===")
        testShadow()
        println("=== TEST E: lokalno odstranjevanje ozadja (Otsu) ===")
        testRemoveBg()
        println("=== TEST F: lokalno inpaintanje ===")
        testInpaint()
        println("=== TEST G: realna slika (balkon + ograja) ===")
        testReal(args)

        println("\n---- $passed passed, $failed failed ----")
        if (failed > 0) kotlin.system.exitProcess(1)
    }

    fun testHomography() {
        val w = 200.0; val h = 100.0
        val dst = arrayOf(
            doubleArrayOf(100.0, 200.0),   // ZL
            doubleArrayOf(500.0, 180.0),   // ZD
            doubleArrayOf(540.0, 400.0),   // SD
            doubleArrayOf(80.0, 420.0),    // SL
        )
        val H = Homography.fromRectToQuad(w, h, dst)
        val corners = listOf(0.0 to 0.0, w to 0.0, w to h, 0.0 to h)
        var maxErr = 0.0
        corners.forEachIndexed { i, (sx, sy) ->
            val p = H.apply(sx, sy)
            val e = maxOf(abs(p[0] - dst[i][0]), abs(p[1] - dst[i][1]))
            maxErr = maxOf(maxErr, e)
        }
        check("vogali se preslikajo natanko", maxErr < 1e-6, "max napaka ${"%.2e".format(maxErr)}")

        // inverz mora vrniti izvor
        val Hi = H.inverse()
        val back = Hi.apply(dst[2][0], dst[2][1])
        check("inverz vrne (w,h)", abs(back[0] - w) < 1e-6 && abs(back[1] - h) < 1e-6,
            "${"%.6f".format(back[0])},${"%.6f".format(back[1])}")

        // sredina pravokotnika mora pristati znotraj štirikotnika
        val mid = H.apply(w / 2, h / 2)
        check("sredina pade v štirikotnik", mid[0] in 80.0..545.0 && mid[1] in 175.0..425.0,
            "${"%.1f".format(mid[0])},${"%.1f".format(mid[1])}")

        // singularnost: podvojen vogal mora sprožiti izjemo
        val bad = try {
            Homography.fromRectToQuad(w, h, arrayOf(dst[0], dst[0], dst[2], dst[3])); false
        } catch (e: Exception) { true }
        check("degeneriran štirikotnik vrže izjemo", bad)
    }

    fun testLab() {
        val black = PureCore.rgbToLab(0, 0, 0)
        val white = PureCore.rgbToLab(255, 255, 255)
        val dark = PureCore.rgbToLab(30, 30, 40)
        check("L(črna)=0", abs(black[0]) < 0.01, "L=${"%.3f".format(black[0])}")
        check("L(bela)=100", abs(white[0] - 100.0) < 0.01, "L=${"%.3f".format(white[0])}")
        check("L je v 0..100 za temno modrikasto", dark[0] in 0.0..100.0, "L=${"%.2f".format(dark[0])}")
        var maxErr = 0.0
        for (r in intArrayOf(0, 30, 118, 200, 255)) for (g in intArrayOf(0, 64, 118, 190, 255)) for (b in intArrayOf(0, 90, 118, 210, 255)) {
            val lab = PureCore.rgbToLab(r, g, b)
            val rgb = PureCore.labToRgb(lab[0], lab[1], lab[2])
            val e = maxOf(abs(rgb[0] - r), abs(rgb[1] - g), abs(rgb[2] - b)).toDouble()
            if (e > maxErr) maxErr = e
        }
        check("sRGB->LAB->sRGB round-trip", maxErr <= 1.0, "max napaka ${"%.2f".format(maxErr)}/255")
        val gray = PureCore.rgbToLab(118, 118, 118)
        check("siva 118 ≈ L50", abs(gray[0] - 49.7) < 3.0, "L=${"%.1f".format(gray[0])}")
    }

    private fun syntheticScene(w: Int, h: Int): IntArray {
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            // "fasada" z gradientom + šum, da je test realen
            val base = 150 + (y * 60 / h)
            val n = ((x * 31 + y * 17) % 7) - 3
            val v = (base + n).coerceIn(0, 255)
            px[y * w + x] = (0xFF shl 24) or (v shl 16) or ((v - 8).coerceIn(0, 255) shl 8) or (v - 20).coerceIn(0, 255)
        }
        return px
    }

    private fun syntheticProduct(w: Int, h: Int): IntArray {
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val slat = (x / 12) % 2 == 0
            val a = if (y < 8 || y > h - 9 || slat) 255 else 0
            val c = if (slat) 60 else 40
            px[y * w + x] = (a shl 24) or (c shl 16) or (c shl 8) or c
        }
        return px
    }

    fun testNoLeakage() {
        val sw = 400; val sh = 300
        val scene = syntheticScene(sw, sh)
        val prod = syntheticProduct(160, 90)
        val mask = ByteArray(sw * sh)
        for (y in 100 until 200) for (x in 60 until 340) mask[y * sw + x] = 255.toByte()
        val corners = listOf(floatArrayOf(60f, 100f), floatArrayOf(340f, 108f), floatArrayOf(336f, 198f), floatArrayOf(64f, 190f))

        val r = PureCore.composite(scene, sw, sh, prod, 160, 90, corners,
            maskPx = mask, maskW = sw, maskH = sh, featherPx = 2,
            colorMatch = PureCore.ColorMatchMode.NONE,
            shadow = PureCore.ShadowOptions(4, 8, 10, 0.4f))

        check("nič sprememb izven maske", r.changedOutsideMask == 0, "changedOutsideMask=${r.changedOutsideMask}")
        check("izdelek je bil vložen", r.productPixels > 1000, "productPixels=${r.productPixels}")
        // piksli zunaj maske morajo biti bitno enaki
        var diff = 0
        for (i in scene.indices) {
            val x = i % sw; val y = i / sw
            if (mask[i] == 0.toByte() && scene[i] != r.pixels[i]) diff++
        }
        check("original zunaj maske bitno enak", diff == 0, "razlik=$diff")
        writePng(r.pixels, sw, sh, File("out/noLeakage.png"))
        writePng(scene, sw, sh, File("out/sceneSynthetic.png"))
    }

    fun testColorMatch() {
        val sw = 200; val sh = 200
        val scene = IntArray(sw * sh) { i -> (0xFF shl 24) or (200 shl 16) or (170 shl 8) or 140 }   // topla fasada
        val prod = IntArray(50 * 30)
        for (i in prod.indices) prod[i] = (255 shl 24) or (30 shl 16) or (30 shl 8) or (40)          // hladna temna ograja
        val corners = listOf(floatArrayOf(50f, 60f), floatArrayOf(150f, 60f), floatArrayOf(150f, 120f), floatArrayOf(50f, 120f))
        val m = PureCore.computeColorMatchModel(prod, 50, 30, scene, sw, sh, corners, PureCore.ColorMatchMode.LAB_STATS)
        check("model barvnega ujemanja izračunan", m != null, m?.let { "gL=${"%.2f".format(it.gainL)} biasL=${"%.1f".format(it.biasL)}" } ?: "")
        if (m != null) {
            val before = prod[0]
            val after = PureCore.applyColorMatch(before, m, 1f)
            val lb = PureCore.rgbToLab(before ushr 16 and 0xFF, before ushr 8 and 0xFF, before and 0xFF)[0]
            val la = PureCore.rgbToLab(after ushr 16 and 0xFF, after ushr 8 and 0xFF, after and 0xFF)[0]
            val target = PureCore.rgbToLab(200, 170, 140)[0]
            check("svetilnost se približa okolici", abs(la - target) < abs(lb - target),
                "L: ${"%.1f".format(lb)} -> ${"%.1f".format(la)} (cilj ${"%.1f".format(target)})")
            val none = PureCore.applyColorMatch(before, m, 0f)
            check("strength=0 ne spremeni nič", none == before)
        }
    }

    fun testShadow() {
        val sw = 200; val sh = 200
        val scene = syntheticScene(sw, sh)
        val prod = syntheticProduct(80, 40)
        val corners = listOf(floatArrayOf(60f, 60f), floatArrayOf(140f, 60f), floatArrayOf(140f, 100f), floatArrayOf(60f, 100f))
        val noShadow = PureCore.composite(scene, sw, sh, prod, 80, 40, corners, featherPx = 1,
            colorMatch = PureCore.ColorMatchMode.NONE, shadow = null)
        val withShadow = PureCore.composite(scene, sw, sh, prod, 80, 40, corners, featherPx = 1,
            colorMatch = PureCore.ColorMatchMode.NONE, shadow = PureCore.ShadowOptions(0, 10, 12, 0.5f))
        check("senca pove število spremenjenih pikslov", withShadow.changedTotal > noShadow.changedTotal,
            "${noShadow.changedTotal} -> ${withShadow.changedTotal}")
        // pod izdelkom mora biti temneje
        val idx = (110 * sw) + 100
        val lumNo = noShadow.pixels[idx] ushr 16 and 0xFF
        val lumYes = withShadow.pixels[idx] ushr 16 and 0xFF
        check("senca potemni območje pod izdelkom", lumYes < lumNo, "R: $lumNo -> $lumYes")
    }

    fun testRemoveBg() {
        val w = 200; val h = 200
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val inObj = (x in 40..160 && y in 60..140)
            px[y * w + x] = if (inObj) (0xFF shl 24) or (30 shl 16) or (30 shl 8) or 30
                            else (0xFF shl 24) or (240 shl 16) or (240 shl 8) or 240
        }
        val c = PureCore.removeBackgroundLocal(px, w, h)
        val center = c.rgba[100 * w + 100]
        val corner = c.rgba[5 * w + 5]
        check("sredica objekta ostane neprosojna", ((center ushr 24) and 0xFF) > 200, "alpha=${(center ushr 24) and 0xFF}")
        check("ozadje postane prosojno", ((corner ushr 24) and 0xFF) < 40, "alpha=${(corner ushr 24) and 0xFF}")
        check("bbox je smiseln", c.bbox[0] in 30..50 && c.bbox[2] in 150..175, c.bbox.joinToString())
        writePng(c.rgba, w, h, File("out/cutoutSynthetic.png"))
    }

    fun testInpaint() {
        val w = 160; val h = 120
        val scene = syntheticScene(w, h)
        val mask = ByteArray(w * h)
        for (y in 40 until 70) for (x in 40 until 120) mask[y * w + x] = 255.toByte()
        // v luknjo damo "staro ograjo" (zelo temno)
        val before = scene.copyOf()
        for (y in 40 until 70) for (x in 40 until 120) before[y * w + x] = (0xFF shl 24) or (10 shl 16) or (10 shl 8) or 10
        val after = PureCore.inpaintPyramid(before, w, h, mask, w, h, 4)
        val dBefore = PureCore.meanAbsDelta(before, scene, mask, w, h, w, h)
        val dAfter = PureCore.meanAbsDelta(after, scene, mask, w, h, w, h)
        check("inpaint zmanjša razliko do originala", dAfter < dBefore,
            "Δ pred=${"%.1f".format(dBefore)} po=${"%.1f".format(dAfter)}")
        check("izven maske nedotaknjeno", run {
            var bad = 0
            for (i in before.indices) if (mask[i] == 0.toByte() && before[i] != after[i]) bad++
            bad == 0
        })
    }

    /**
     * TEST G: realna slika. Če sta podani poti (arg0=balkon, arg1=ograja), naredi
     * celoten geometrijski pipeline in izpiše PNG-je + metriko zaščite originala.
     */
    fun testReal(args: Array<String>) {
        if (args.size < 2) { println("  (preskočeno: podaj poti do slik kot argumenta)"); return }
        val sceneImg = ImageIO.read(File(args[0]))
        if (sceneImg == null) { println("  ❌ ne morem prebrati ${args[0]}"); failed++; return }
        val prodImg = ImageIO.read(File(args[1]))
        if (prodImg == null) { println("  ❌ ne morem prebrati ${args[1]}"); failed++; return }
        val sw = sceneImg.width; val sh = sceneImg.height
        val scene = IntArray(sw * sh)
        sceneImg.getRGB(0, 0, sw, sh, scene, 0, sw)
        for (i in scene.indices) scene[i] = scene[i] or (0xFF shl 24)

        // izdelek: lokalno odstranjevanje ozadja + izrez po bbox
        val pw0 = prodImg.width; val ph0 = prodImg.height
        val prodFull = IntArray(pw0 * ph0)
        prodImg.getRGB(0, 0, pw0, ph0, prodFull, 0, pw0)
        for (i in prodFull.indices) prodFull[i] = prodFull[i] or (0xFF shl 24)
        val cut = PureCore.removeBackgroundLocal(prodFull, pw0, ph0)
        val bx0 = cut.bbox[0]; val by0 = cut.bbox[1]; val bx1 = cut.bbox[2]; val by1 = cut.bbox[3]
        val bw = bx1 - bx0 + 1; val bh = by1 - by0 + 1
        val prod = IntArray(bw * bh)
        for (y in 0 until bh) for (x in 0 until bw) prod[y * bw + x] = cut.rgba[(by0 + y) * pw0 + (bx0 + x)]
        check("izrez izdelka uspel", bw > 20 && bh > 20, "${bw}x${bh}, zaupanje=${"%.2f".format(cut.confidence)}")
        writePng(prod, bw, bh, File("out/real_cutout.png"))

        // postavitev: privzeto pas, ali eksplicitno iz JSON datoteke (args[2])
        var corners: List<FloatArray> = listOf(
            floatArrayOf(sw * 0.08f, sh * 0.58f),
            floatArrayOf(sw * 0.92f, sh * 0.63f),
            floatArrayOf(sw * 0.92f, sh * 0.86f),
            floatArrayOf(sw * 0.08f, sh * 0.86f),
        )
        var poly: List<FloatArray> = corners
        if (args.size >= 3) {
            // Format datoteke s postavitvijo (preprost, brez JSON escapiranja):
            //   corners=0.55,0.15 0.88,0.35 0.88,0.61 0.55,0.43
            //   poly=0.54,0.14 0.89,0.34 0.89,0.62 0.54,0.44
            // Koordinate so normalizirane (0..1) glede na sirino/visino scene.
            fun parseLine(tag: String, txt: String): List<FloatArray> {
                val line = txt.lineSequence().firstOrNull { it.trimStart().startsWith("$tag=") } ?: return emptyList()
                return line.substringAfter("=").trim().split(Regex("\\s+")).filter { it.isNotBlank() }.map { tok ->
                    val (a, b) = tok.split(",").let { it[0].toFloat() to it[1].toFloat() }
                    floatArrayOf(a * sw, b * sh)
                }
            }
            val txt = File(args[2]).readText()
            val c = parseLine("corners", txt)
            val pl = parseLine("poly", txt)
            if (c.size == 4) corners = c
            poly = if (pl.size >= 3) pl else corners
            println("     -> postavitev iz ${args[2]}: ${corners.size} vogalov, poligon ${poly.size} tock")
        }
        val mask = fillPolygon(poly, sw, sh)
        val r = PureCore.composite(scene, sw, sh, prod, bw, bh, corners,
            maskPx = mask, maskW = sw, maskH = sh, featherPx = 3,
            colorMatch = PureCore.ColorMatchMode.LUMA_ONLY, colorMatchStrength = 0.35f,
            shadow = PureCore.ShadowOptions(6, 14, 22, 0.35f))

        check("nič sprememb izven maske (realna slika)", r.changedOutsideMask == 0, "changedOutsideMask=${r.changedOutsideMask}")
        val opaque = prod.count { ((it ushr 24) and 0xFF) > 128 }
        // Biliniearno vzorčenje razširi pol-prosojne robove, zato je vloženih pikslov
        // lahko nekoliko VEČ kot neprosojnih v referenci (a nikoli bistveno).
        check("izdelek vstavljen", r.productPixels in (opaque / 4)..(opaque * 3 / 2 + 100),
            "vlozenih=${r.productPixels}, neprosojnih v referenci=$opaque")
        // shrani manjse razlicice (pomnilnik!) in montazo PREJ|POTEM
        val (op, ow, oh) = PureCore.downscale(scene, sw, sh, 780)
        val (rp, rw, rh) = PureCore.downscale(r.pixels, sw, sh, 780)
        val (cp, cw2, ch2) = PureCore.downscale(prod, bw, bh, 420)
        writePng(op, ow, oh, File("out/real_original.png"))
        writePng(rp, rw, rh, File("out/real_result_geometry.png"))
        writePng(cp, cw2, ch2, File("out/real_cutout.png"))
        val maskVis = IntArray(sw * sh) { i -> if (mask[i] != 0.toByte()) (-0x1000000 or (0xFF shl 16) or 0x2222) else scene[i] }
        val (mv, mvw, mvh) = PureCore.downscale(maskVis, sw, sh, 780)
        val (mm, mmw, mmh) = PureCore.sideBySide(op, ow, oh, mv, mvw, mvh)
        writePng(mm, mmw, mmh, File("out/real_mask_compare.png"))
        val (sb, sbw, sbh) = PureCore.sideBySide(op, ow, oh, rp, rw, rh)
        writePng(sb, sbw, sbh, File("out/real_before_after.png"))

        // ---- CELTEN LOKALNI PIPELINE: odstranitev stare ograje -> clean plate -> vstavljanje
        val clean = PureCore.inpaintPyramid(scene, sw, sh, mask, sw, sh, 6)
        val r2 = PureCore.composite(clean, sw, sh, prod, bw, bh, corners,
            maskPx = mask, maskW = sw, maskH = sh, featherPx = 3,
            colorMatch = PureCore.ColorMatchMode.LUMA_ONLY, colorMatchStrength = 0.35f,
            shadow = PureCore.ShadowOptions(6, 14, 22, 0.35f))
        check("lokalni pipeline: nic izven maske", r2.changedOutsideMask == 0, "changedOutsideMask=${r2.changedOutsideMask}")
        val (cp2, cw3, ch3) = PureCore.downscale(clean, sw, sh, 780)
        val (rp2, rw2, rh2) = PureCore.downscale(r2.pixels, sw, sh, 780)
        writePng(cp2, cw3, ch3, File("out/real_clean_plate.png"))
        val (sb2, sbw2, sbh2) = PureCore.sideBySide(cp2, cw3, ch3, rp2, rw2, rh2)
        writePng(sb2, sbw2, sbh2, File("out/real_local_pipeline.png"))
        println("     -> out/real_local_pipeline.png (clean plate | končni rezultat)")
        println("     -> lokalni pipeline vlozil ${r2.productPixels} pikslov izdelka")
        println("     -> out/real_before_after.png (${sbw}x${sbh}), out/real_cutout.png (${cw2}x${ch2})")
        println("     -> vlozenih pikslov izdelka: ${r.productPixels} / ${opaque} neprosojnih v referenci")
        println("     -> leakageRatio = ${r.leakageRatio}")
    }

    /** Scanline izpolnjevanje poligona -> maska (255 znotraj). */
    fun fillPolygon(poly: List<FloatArray>, w: Int, h: Int): ByteArray {
        val mask = ByteArray(w * h)
        if (poly.size < 3) return mask
        var minY = Int.MAX_VALUE; var maxY = Int.MIN_VALUE
        for (p in poly) { minY = min(minY, p[1].toInt()); maxY = max(maxY, p[1].toInt()) }
        minY = max(0, minY); maxY = min(h - 1, maxY)
        for (y in minY..maxY) {
            val xs = ArrayList<Float>()
            for (i in poly.indices) {
                val a = poly[i]; val b = poly[(i + 1) % poly.size]
                if ((a[1] <= y && b[1] > y) || (b[1] <= y && a[1] > y)) {
                    val t = (y - a[1]) / (b[1] - a[1])
                    xs.add(a[0] + t * (b[0] - a[0]))
                }
            }
            xs.sort()
            var k = 0
            while (k + 1 < xs.size) {
                val x0 = max(0, kotlin.math.ceil(xs[k]).toInt())
                val x1 = min(w - 1, xs[k + 1].toInt())
                for (x in x0..x1) mask[y * w + x] = 255.toByte()
                k += 2
            }
        }
        return mask
    }

    fun writePng(px: IntArray, w: Int, h: Int, f: File) {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, w, h, px, 0, w)
        ImageIO.write(img, "png", f)
    }
}

package si.ograjavizija.app.imaging

import kotlin.math.abs

/**
 * Projekcijska preslikava (3x3) iz štirih pari točk — DLT (Direct Linear Transform).
 *
 * Uporabnik na zaslonu premakne štiri vogale referenčne ograje; ta razred izračuna
 * matriko H, ki preslika pravokotnik izdelka (0,0)-(w,h) v poljuben štirikotnik
 * na fotografiji balkona. To je matematično natanko tisto, kar naredi perspektivo
 * pravilno — brez kakršne koli generativne "ugibanja".
 *
 * Razred je namenoma brez odvisnosti od android.* , da ga lahko testiramo na JVM.
 */
class Homography(val m: DoubleArray) {

    init { require(m.size == 9) { "Homography needs 9 coefficients" } }

    fun apply(x: Double, y: Double): DoubleArray {
        val w = m[6] * x + m[7] * y + m[8]
        val ww = if (abs(w) < 1e-12) 1e-12 else w
        return doubleArrayOf(
            (m[0] * x + m[1] * y + m[2]) / ww,
            (m[3] * x + m[4] * y + m[5]) / ww,
        )
    }

    fun inverse(): Homography {
        val a = m
        val det = a[0] * (a[4] * a[8] - a[5] * a[7]) -
                  a[1] * (a[3] * a[8] - a[5] * a[6]) +
                  a[2] * (a[3] * a[7] - a[4] * a[6])
        require(abs(det) > 1e-14) { "Singular homography (det=$det)" }
        val inv = DoubleArray(9)
        // adjugata / determinanta (pozor na predznake lihih kofaktorjev)
        inv[0] =  (a[4] * a[8] - a[5] * a[7]) / det
        inv[1] = -(a[1] * a[8] - a[2] * a[7]) / det
        inv[2] =  (a[1] * a[5] - a[2] * a[4]) / det
        inv[3] = -(a[3] * a[8] - a[5] * a[6]) / det
        inv[4] =  (a[0] * a[8] - a[2] * a[6]) / det
        inv[5] = -(a[0] * a[5] - a[2] * a[3]) / det
        inv[6] =  (a[3] * a[7] - a[4] * a[6]) / det
        inv[7] = -(a[0] * a[7] - a[1] * a[6]) / det
        inv[8] =  (a[0] * a[4] - a[1] * a[3]) / det
        return Homography(inv)
    }

    companion object {
        /**
         * Izračuna H tako, da (0,0)->p0, (w,0)->p1, (w,h)->p2, (0,h)->p3.
         *
         * @param w širina izvornega pravokotnika (izdelek)
         * @param h višina izvornega pravokotnika (izdelek)
         * @param dst štiri ciljne točke, vrstni red: ZL, ZD, SD, SL
         */
        fun fromRectToQuad(
            w: Double,
            h: Double,
            dst: Array<DoubleArray>,
        ): Homography {
            require(dst.size == 4) { "Need exactly 4 destination points" }
            require(w > 0 && h > 0) { "Source rect must be positive" }

            val src = arrayOf(
                doubleArrayOf(0.0, 0.0),
                doubleArrayOf(w, 0.0),
                doubleArrayOf(w, h),
                doubleArrayOf(0.0, h),
            )

            // 8 neznank: h11 h12 h13 h21 h22 h23 h31 h32  (h33 = 1)
            val a = Array(8) { DoubleArray(9) }
            for (i in 0 until 4) {
                val (sx, sy) = src[i][0] to src[i][1]
                val (dx, dy) = dst[i][0] to dst[i][1]
                a[2 * i] = doubleArrayOf(sx, sy, 1.0, 0.0, 0.0, 0.0, -sx * dx, -sy * dx, dx)
                a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, sx, sy, 1.0, -sx * dy, -sy * dy, dy)
            }
            val x = solve(a, 8)
            val h = Homography(doubleArrayOf(x[0], x[1], x[2], x[3], x[4], x[5], x[6], x[7], 1.0))
            // Preverimo, da H res preslika vse 4 vogale (ulovi degenerirane vnose,
            // npr. podvojen vogal, kjer Gaussovo pivotiranje samo po sebi ne odpove).
            val diag = dst.let { d ->
                var mx = 0.0
                for (i in d.indices) for (j in d.indices) {
                    val dd = kotlin.math.hypot(d[i][0] - d[j][0], d[i][1] - d[j][1])
                    if (dd > mx) mx = dd
                }
                mx
            }
            val tol = maxOf(1e-3, diag * 1e-6)
            for (i in 0 until 4) {
                val p = h.apply(src[i][0], src[i][1])
                val err = maxOf(abs(p[0] - dst[i][0]), abs(p[1] - dst[i][1]))
                require(err < tol) {
                    "Degeneriran štirikotnik: vogal $i se ne preslika pravilno (napaka ${"%.4g".format(err)})"
                }
            }
            return h
        }

        /** Gaussova eliminacija s parcialnim pivotiranjem za Ax = b (A je n x (n+1)). */
        fun solve(a: Array<DoubleArray>, n: Int): DoubleArray {
            val m = Array(n) { a[it].clone() }
            for (col in 0 until n) {
                var piv = col
                for (r in col + 1 until n) if (abs(m[r][col]) > abs(m[piv][col])) piv = r
                if (abs(m[piv][col]) < 1e-14) throw IllegalStateException("Singular system at column $col")
                val tmp = m[col]; m[col] = m[piv]; m[piv] = tmp
                val d = m[col][col]
                for (c in col..n) m[col][c] /= d
                for (r in 0 until n) {
                    if (r == col) continue
                    val f = m[r][col]
                    if (f == 0.0) continue
                    for (c in col..n) m[r][c] -= f * m[col][c]
                }
            }
            return DoubleArray(n) { m[it][n] }
        }

        fun identity() = Homography(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0))
    }
}

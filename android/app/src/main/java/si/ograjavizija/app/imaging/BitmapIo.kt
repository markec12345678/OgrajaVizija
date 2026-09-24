package si.ograjavizija.app.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pomožne funkcije za nalaganje / shranjevanje / obračanje slik. */
object BitmapIo {

    fun decodeSampled(f: File, maxEdge: Int): Bitmap? {
        if (!f.exists()) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, opts)
        val sample = calcSample(opts.outWidth, opts.outHeight, maxEdge)
        val opts2 = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeFile(f.absolutePath, opts2) ?: return null
        return fit(bmp, maxEdge)
    }

    fun decodeFromUri(ctx: Context, uri: String, maxEdge: Int): Bitmap? = runCatching {
        val u = Uri.parse(uri)
        ctx.contentResolver.openInputStream(u)?.use { input ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, opts)
            val sample = calcSample(opts.outWidth, opts.outHeight, maxEdge)
            ctx.contentResolver.openInputStream(u)?.use { input2 ->
                val opts2 = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bmp = BitmapFactory.decodeStream(input2, null, opts2) ?: return@use null
                val rotated = applyExif(bmp, exifOrientation(ctx, uri))
                fit(rotated, maxEdge)
            }
        }
    }.getOrNull()

    fun exifOrientation(ctx: Context, uri: String): Int = runCatching {
        ctx.contentResolver.openInputStream(Uri.parse(uri))?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    fun exifOrientation(f: File): Int = runCatching {
        ExifInterface(f.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    fun applyExif(bmp: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return bmp
        }
        val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (out != bmp) bmp.recycle()
        return out
    }

    fun fit(bmp: Bitmap, maxEdge: Int): Bitmap {
        if (maxEdge <= 0) return bmp
        val long = max(bmp.width, bmp.height)
        if (long <= maxEdge) return bmp
        val s = maxEdge.toFloat() / long
        val w = max(1, (bmp.width * s).roundToInt())
        val h = max(1, (bmp.height * s).roundToInt())
        val out = Bitmap.createScaledBitmap(bmp, w, h, true)
        if (out != bmp) bmp.recycle()
        return out
    }

    private fun calcSample(w: Int, h: Int, maxEdge: Int): Int {
        var s = 1
        if (w <= 0 || h <= 0 || maxEdge <= 0) return 1
        while (max(w, h) / (s * 2) >= maxEdge) s *= 2
        return s
    }

    fun toJpegBytes(bmp: Bitmap, quality: Int = 92): ByteArray {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        return bos.toByteArray()
    }

    fun toPngBytes(bmp: Bitmap): ByteArray {
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
        return bos.toByteArray()
    }

    fun saveJpeg(bmp: Bitmap, f: File, quality: Int = 94) {
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    /** Siva maska (8 bit) -> Bitmap za prikaz vmesnika. */
    fun maskToBitmap(mask: ByteArray, w: Int, h: Int, tint: Int = 0x66FF3B30.toInt()): Bitmap {
        val px = IntArray(w * h)
        for (i in px.indices) px[i] = if (mask[i] > 8) tint else 0
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    fun bitmapToMaskGray(bmp: Bitmap): ByteArray {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        val out = ByteArray(w * h)
        for (i in px.indices) out[i] = (((px[i] ushr 24) and 0xFF) > 127).let { if (it) 255.toByte() else 0 }
        return out
    }

    fun scaleMask(mask: ByteArray, w: Int, h: Int, nw: Int, nh: Int): ByteArray {
        if (w == nw && h == nh) return mask
        val out = ByteArray(nw * nh)
        for (y in 0 until nh) for (x in 0 until nw) {
            val sx = min(w - 1, x * w / nw); val sy = min(h - 1, y * h / nh)
            out[y * nw + x] = mask[sy * w + sx]
        }
        return out
    }
}

package si.ograjavizija.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import si.ograjavizija.app.imaging.BitmapIo
import java.io.File
import java.io.FileOutputStream

/**
 * Shramba projektov — izključno lokalna (zahteva 11 in 12: brez računa, brez oblaka).
 *
 * projects/<id>/
 *   project.json   original.jpg   product.jpg   cutout.png   mask.png   result.jpg
 *   variants/<variantId>.jpg   variants/<variantId>.png (cutout)
 */
object ProjectStore {
    private lateinit var root: File
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun init(ctx: Context) {
        root = File(ctx.filesDir, "projects").apply { mkdirs() }
    }

    fun dir(): File = root
    fun projectDir(id: String) = File(root, id).apply { mkdirs() }

    fun list(): List<Project> = root.listFiles()?.filter { it.isDirectory }
        ?.mapNotNull { runCatching { json.decodeFromString<Project>(File(it, "project.json").readText()) }.getOrNull() }
        ?.sortedByDescending { it.updatedAt } ?: emptyList()

    fun load(id: String): Project? = runCatching {
        json.decodeFromString<Project>(File(projectDir(id), "project.json").readText())
    }.getOrNull()

    suspend fun save(p: Project): Project = withContext(Dispatchers.IO) {
        val updated = p.copy(updatedAt = System.currentTimeMillis())
        File(projectDir(updated.id), "project.json").writeText(json.encodeToString(updated))
        updated
    }

    fun file(p: Project, name: String) = File(projectDir(p.id), name)

    suspend fun writeBitmap(p: Project, name: String, bmp: Bitmap, quality: Int = 94): File =
        withContext(Dispatchers.IO) {
            val f = File(projectDir(p.id), name)
            FileOutputStream(f).use { out ->
                if (name.endsWith(".png")) bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                else bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            f
        }

    suspend fun writeBytes(p: Project, name: String, bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val f = File(projectDir(p.id), name)
        f.writeBytes(bytes); f
    }

    fun readBitmap(p: Project, name: String, maxEdge: Int = 0): Bitmap? {
        val f = File(projectDir(p.id), name)
        if (!f.exists()) return null
        return if (maxEdge > 0) BitmapIo.decodeSampled(f, maxEdge) else BitmapFactory.decodeFile(f.absolutePath)
    }

    fun delete(p: Project) { projectDir(p.id).deleteRecursively() }

    fun newId(): String = "p" + System.currentTimeMillis().toString(36) +
        (0..999).random().toString(36).padStart(2, '0')

    /** Kopira sliko iz poljubne URI/ poti v projekt in vrne ImageRef. */
    suspend fun importImage(ctx: Context, p: Project, uri: String, targetName: String, maxEdge: Int = 2400): ImageRef? =
        withContext(Dispatchers.IO) {
            val bmp = BitmapIo.decodeFromUri(ctx, uri, maxEdge) ?: return@withContext null
            val out = writeBitmap(p, targetName, bmp, 95)
            val ref = ImageRef(out.name, bmp.width, bmp.height, BitmapIo.exifOrientation(ctx, uri))
            bmp.recycle()
            ref
        }
}

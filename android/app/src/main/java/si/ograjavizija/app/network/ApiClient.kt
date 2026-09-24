package si.ograjavizija.app.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import si.ograjavizija.app.data.AiProvider
import si.ograjavizija.app.imaging.BitmapIo
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Odjemalec za LASTNI AI backend (zahteva 12).
 *
 * 🟢 Brez strežnika  -> aplikacija dela vse lokalno (geometrijski način).
 * 🟡 Lasten strežnik -> /health, /remove, /segment, /finalize na tvojem GPU računalniku.
 * 🔴 Zunanji plačljivi API -> NI privzeto in NI obvezno; backend ga lahko vklopi
 *    (provider=REPLICATE/FAL), a aplikacija nikoli ne pošilja ključev.
 *
 * Namerno brez Retrofit/OkHttp: HttpURLConnection je del platforme, manj odvisnosti,
 * manjši APK, in backend je tako ali tako tvoj.
 */
object ApiClient {

    class ServerException(msg: String, val code: Int = -1) : Exception(msg)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable data class Health(val ok: Boolean = false, val version: String = "", val device: String = "",
                                   val providers: List<String> = emptyList(), val gpu: String = "", val vramGb: Double = 0.0)

    @Serializable data class JobRequest(
        val provider: String = AiProvider.FLUX2_KLEIN_4B.name,
        val prompt: String = "",
        val strength: Float = 0.35f,
        val steps: Int = 8,
        val seed: Long = 1234,
        val maxLongEdge: Int = 1600,
        /** true = server vrne samo maskirano območje, sestavo naredi telefon (zaščita originala). */
        val returnMaskedOnly: Boolean = true,
    )

    @Serializable data class JobResponse(
        val ok: Boolean = false,
        val elapsedMs: Long = 0,
        val provider: String = "",
        val changedOutsideMask: Int = -1,
        val message: String = "",
    )

    suspend fun health(baseUrl: String): Health = withContext(Dispatchers.IO) {
        val body = get(baseUrl, "/health")
        json.decodeFromString<Health>(body)
    }

    /** 🟡 Odstrani staro ograjo (LaMa / MI-GAN). Vrne 'clean plate'. */
    suspend fun removeObject(baseUrl: String, scene: Bitmap, maskPng: ByteArray): Bitmap =
        withContext(Dispatchers.IO) {
            val out = ByteArrayOutputStream()
            writeMultipart(out, "boundaryOgraja", listOf(
                Part.File("scene", "scene.jpg", "image/jpeg", BitmapIo.toJpegBytes(scene, 95)),
                Part.File("mask", "mask.png", "image/png", maskPng),
            ))
            val bytes = postRaw(baseUrl, "/remove", out.toByteArray(), "multipart/form-data; boundary=boundaryOgraja")
            decodeOrFail(bytes)
        }

    /** 🟡 Segmentacija na strežniku (SAM3/MobileSAM), če je lokalna prešibka. */
    suspend fun segment(baseUrl: String, image: Bitmap, points: List<FloatArray>, boxes: List<FloatArray> = emptyList()): ByteArray =
        withContext(Dispatchers.IO) {
            val pts = points.joinToString(";") { "${it[0]},${it[1]}" }
            val out = ByteArrayOutputStream()
            writeMultipart(out, "boundaryOgraja", listOf(
                Part.File("image", "image.jpg", "image/jpeg", BitmapIo.toJpegBytes(image, 92)),
                Part.Text("points", pts),
            ))
            val bytes = postRaw(baseUrl, "/segment", out.toByteArray(), "multipart/form-data; boundary=boundaryOgraja")
            bytes
        }

    /** 🟡 Odstrani ozadje s fotografije izdelka (BiRefNet). */
    suspend fun removeBackground(baseUrl: String, product: Bitmap): Bitmap = withContext(Dispatchers.IO) {
        val out = ByteArrayOutputStream()
        writeMultipart(out, "boundaryOgraja", listOf(
            Part.File("image", "product.jpg", "image/jpeg", BitmapIo.toJpegBytes(product, 95)),
        ))
        val bytes = postRaw(baseUrl, "/remove-background", out.toByteArray(), "multipart/form-data; boundary=boundaryOgraja")
        decodeOrFail(bytes)
    }

    /**
     * 🟡 AI finalizacija (zahteva 8): strežnik dobi original + masko + že geometrijsko
     * postavljen izdelek + prompt, in vrne rezultat.
     *
     * Pomembno: pošljemo 'composite' (naša deterministična sestava), ne samega originala.
     * Model tako samo 'zlije' robove, doda sence in uskladi svetlobo — ne more pa si
     * izmisliti nove ograje, ker so piksli izdelka že na pravem mestu.
     */
    suspend fun finalize(
        baseUrl: String,
        scene: Bitmap,
        composite: Bitmap,
        maskPng: ByteArray,
        product: Bitmap,
        req: JobRequest,
    ): Pair<Bitmap, JobResponse> = withContext(Dispatchers.IO) {
        val out = ByteArrayOutputStream()
        writeMultipart(out, "boundaryOgraja", listOf(
            Part.File("scene", "scene.jpg", "image/jpeg", BitmapIo.toJpegBytes(scene, 95)),
            Part.File("composite", "composite.jpg", "image/jpeg", BitmapIo.toJpegBytes(composite, 95)),
            Part.File("mask", "mask.png", "image/png", maskPng),
            Part.File("product", "product.jpg", "image/jpeg", BitmapIo.toJpegBytes(product, 92)),
            Part.Text("request", json.encodeToString(JobRequest.serializer(), req)),
        ))
        val bytes = postRaw(baseUrl, "/finalize", out.toByteArray(), "multipart/form-data; boundary=boundaryOgraja")
        // odgovor = multipart z 'meta' (json) in 'image'
        val parts = parseMultipart(bytes)
        val meta = parts["meta"]?.let { runCatching { json.decodeFromString<JobResponse>(String(it)) }.getOrNull() } ?: JobResponse(ok = true)
        val img = parts["image"]?.let { decodeOrFail(it) } ?: throw ServerException("Strežnik ni vrnil slike")
        img to meta
    }

    // ------------------------------------------------------------ http

    private fun get(base: String, path: String): String {
        val c = open(base, path, "GET")
        return try { c.inputStream.readBytes().decodeToString() } finally { c.disconnect() }
    }

    private fun postRaw(base: String, path: String, body: ByteArray, contentType: String): ByteArray {
        val c = open(base, path, "POST")
        c.doOutput = true
        c.setRequestProperty("Content-Type", contentType)
        c.outputStream.use { it.write(body) }
        val code = c.responseCode
        return try {
            if (code in 200..299) c.inputStream.readBytes()
            else throw ServerException((c.errorStream?.readBytes()?.decodeToString() ?: "HTTP $code").take(400), code)
        } finally { c.disconnect() }
    }

    private fun open(base: String, path: String, method: String): HttpURLConnection {
        require(base.isNotBlank()) { "Nastavi naslov strežnika v Nastavitvah" }
        val url = URL(base.trimEnd('/') + path)
        val c = url.openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15_000
        c.readTimeout = 600_000   // difuzija lahko traja
        c.setRequestProperty("Accept", "*/*")
        return c
    }

    private fun decodeOrFail(bytes: ByteArray): Bitmap =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw ServerException("Odgovor ni veljavna slika (${bytes.size} B)")

    // ------------------------------------------------------------ multipart

    sealed class Part {
        data class File(val name: String, val filename: String, val mime: String, val data: ByteArray) : Part()
        data class Text(val name: String, val value: String) : Part()
    }

    private fun writeMultipart(out: ByteArrayOutputStream, boundary: String, parts: List<Part>) {
        val dash = ("--" + boundary).toByteArray()
        for (p in parts) {
            out.write(dash); out.write("\r\n".toByteArray())
            when (p) {
                is Part.Text -> {
                    out.write("Content-Disposition: form-data; name=\"${p.name}\"\r\n\r\n".toByteArray())
                    out.write(p.value.toByteArray()); out.write("\r\n".toByteArray())
                }
                is Part.File -> {
                    out.write("Content-Disposition: form-data; name=\"${p.name}\"; filename=\"${p.filename}\"\r\n".toByteArray())
                    out.write("Content-Type: ${p.mime}\r\n\r\n".toByteArray())
                    out.write(p.data); out.write("\r\n".toByteArray())
                }
            }
        }
        out.write(dash); out.write("--\r\n".toByteArray())
    }

    /** Zelo preprost bralnik multipart odgovora (strežnik vrne 'meta' + 'image'). */
    private fun parseMultipart(bytes: ByteArray): Map<String, ByteArray> {
        val text = String(bytes, Charsets.ISO_8859_1)
        val bIdx = text.indexOf("--boundaryOgraja")
        if (bIdx < 0) return mapOf("image" to bytes)
        val boundary = text.substring(bIdx, bIdx + "--boundaryOgraja".length)
        val out = HashMap<String, ByteArray>()
        var pos = 0
        while (true) {
            val start = text.indexOf(boundary, pos)
            if (start < 0) break
            val next = text.indexOf(boundary, start + boundary.length)
            if (next < 0) break
            val headerEnd = text.indexOf("\r\n\r\n", start)
            if (headerEnd in 0..next) {
                val header = text.substring(start, headerEnd)
                val nameMatch = Regex("name=\"([^\"]+)\"").find(header)
                val name = nameMatch?.groupValues?.get(1)
                val bodyStart = headerEnd + 4
                val bodyEnd = (next - 2).coerceAtLeast(bodyStart)
                if (name != null) out[name] = bytes.copyOfRange(bodyStart, bodyEnd)
            }
            pos = next
        }
        return out
    }
}

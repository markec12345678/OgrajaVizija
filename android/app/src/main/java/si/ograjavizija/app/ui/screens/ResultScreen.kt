package si.ograjavizija.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import si.ograjavizija.app.data.AppState
import si.ograjavizija.app.data.Project
import si.ograjavizija.app.data.ProjectController
import si.ograjavizija.app.data.ProjectStore
import si.ograjavizija.app.data.RenderMode
import si.ograjavizija.app.data.Variant
import si.ograjavizija.app.ui.components.StepHeader
import si.ograjavizija.app.ui.theme.Accent
import si.ograjavizija.app.ui.theme.Bad
import si.ograjavizija.app.ui.theme.Muted
import si.ograjavizija.app.ui.theme.SurfaceAlt
import si.ograjavizija.app.ui.theme.Warn
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Korak 5+6: ustvari vizualizacijo, primerjaj PREJ|POTEM in A|B|C|D, shrani. */
@Composable
fun ResultScreen(projectId: String?, onNewRailing: () -> Unit, onBack: () -> Unit, onHome: () -> Unit) {
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf<Project?>(null) }
    var before by remember { mutableStateOf<Bitmap?>(null) }
    var after by remember { mutableStateOf<Bitmap?>(null) }
    var mix by remember { mutableFloatStateOf(1f) }   // 0 = prej, 1 = potem
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var variantThumb by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }

    LaunchedEffect(projectId) {
        val p = projectId?.let { ProjectStore.load(it) } ?: AppState.currentProject ?: return@LaunchedEffect
        project = p
        before = ProjectStore.readBitmap(p, "original.jpg", 1600)
        after = ProjectStore.readBitmap(p, "result.jpg", 1600)
        val thumbs = HashMap<String, Bitmap>()
        p.variants.forEach { v ->
            v.resultFileName?.let { f ->
                ProjectStore.readBitmap(p, "variants/${v.id}.jpg", 400)?.let { thumbs[v.id] = it }
            }
        }
        variantThumb = thumbs
    }

    fun render(mode: RenderMode) {
        val p = project ?: return
        val pl = p.placement ?: run { status = "⚠️ Najprej prilagodi položaj (korak 4)."; return }
        busy = true; status = "Računam…"
        scope.launch {
            val out = runCatching {
                if (mode == RenderMode.LOCAL_GEOMETRY) ProjectController.renderLocal(p, pl, p.settings.copy(serverUrl = AppState.serverUrl))
                else ProjectController.renderServer(p, pl, p.settings.copy(serverUrl = AppState.serverUrl), mode == RenderMode.SERVER_FULL)
            }
            out.fold(
                onSuccess = { r ->
                    after = r.result
                    status = "${r.note} · ${r.elapsedMs} ms · sprememb izven maske: ${r.changedOutsideMask}"
                    if (r.changedOutsideMask > 0) status += " ⚠️ (pričakovano 0!)"
                },
                onFailure = { e -> status = "⚠️ ${e.message}" },
            )
            busy = false
        }
    }

    fun save(label: String?) {
        val p = project ?: return
        val r = after ?: return
        scope.launch {
            val f = ProjectStore.file(p, if (label != null) "variants/${label}.jpg" else "result.jpg")
            f.parentFile?.mkdirs()
            ProjectStore.writeBitmap(p, if (label != null) "variants/$label.jpg" else "result.jpg", r, 95)
            var p2 = ProjectStore.save(p.copy())
            if (label != null) {
                val v = Variant(id = label, label = label, productFileName = "product.jpg",
                    cutoutFileName = "cutout.png", resultFileName = "variants/$label.jpg", placement = p2.placement)
                p2 = ProjectStore.save(p2.copy(variants = p2.variants + v, activeVariantId = v.id))
            } else {
                p2 = ProjectStore.save(p2.copy())
            }
            project = p2; AppState.setProject(p2)
            status = "💾 Shranjeno."
        }
    }

    Column(Modifier.fillMaxSize()) {
        StepHeader(5, "5 · Vizualizacija in 6 · shranjevanje", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { render(RenderMode.LOCAL_GEOMETRY) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("🟢 Lokalno")
            }
            OutlinedButton(onClick = { render(RenderMode.SERVER_REMOVAL) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("🟡 Strežnik")
            }
            OutlinedButton(onClick = { render(RenderMode.SERVER_FULL) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text("🟡 + AI")
            }
        }
        Box(Modifier.weight(1f).padding(12.dp)) {
            val b = before; val a = after
            if (b != null && a != null) {
                // PREJ | POTEM z drsnikom
                val w = minOf(b.width, a.width); val h = minOf(b.height, a.height)
                val merged = remember(mix, a, b) {
                    val m = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val cut = (w * mix).toInt().coerceIn(0, w)
                    val pb = IntArray(w * h); b.getPixels(pb, 0, w, 0, 0, w, h)
                    val pa = IntArray(w * h); a.getPixels(pa, 0, w, 0, 0, w, h)
                    for (y in 0 until h) for (x in 0 until w) {
                        m.setPixel(x, y, if (x < cut) pb[y * w + x] else pa[y * w + x])
                    }
                    m
                }
                androidx.compose.foundation.Image(
                    bitmap = merged.asImageBitmap(), contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
            } else {
                Text("Klikni 🟢 Lokalno za prvi izris.", color = Muted, modifier = Modifier.padding(8.dp))
            }
        }
        Slider(value = mix, onValueChange = { mix = it }, modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("PREJ", color = Muted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text("POTEM", color = Accent, style = MaterialTheme.typography.labelSmall)
        }
        if (status.isNotEmpty()) Text(status, color = if (status.startsWith("⚠")) Bad else Muted,
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onNewRailing, modifier = Modifier.weight(1f)) { Text("🆕 Nova ograja") }
            Button(onClick = { save(null) }, modifier = Modifier.weight(1f)) { Text("💾 Shrani") }
            OutlinedButton(onClick = { save("ograja_" + ('A' + (project?.variants?.size ?: 0))) }, modifier = Modifier.weight(1f)) {
                Text("💾 Kot varianto")
            }
        }
        // A | B | C | D primerjava
        val vars = project?.variants.orEmpty()
        if (vars.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                before?.let { b ->
                    ThumbBox("PREJ", b) { mix = 0f }
                }
                vars.forEach { v ->
                    variantThumb[v.id]?.let { t -> ThumbBox(v.label, t) { after = ProjectStore.readBitmap(project!!, "variants/${v.id}.jpg", 1600) } }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("🏠 Domov") }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ThumbBox(label: String, bmp: Bitmap, onClick: () -> Unit) {
    Surface(color = SurfaceAlt, shape = RoundedCornerShape(10.dp), onClick = onClick) {
        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(), contentDescription = label,
                modifier = Modifier.width(96.dp).height(72.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

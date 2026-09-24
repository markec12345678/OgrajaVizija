package si.ograjavizija.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.ograjavizija.app.data.AppState
import si.ograjavizija.app.data.Placement
import si.ograjavizija.app.data.Project
import si.ograjavizija.app.data.ProjectController
import si.ograjavizija.app.data.ProjectStore
import si.ograjavizija.app.data.Pt
import si.ograjavizija.app.imaging.PureCore
import si.ograjavizija.app.ui.components.StepHeader
import si.ograjavizija.app.ui.components.ZoomPanBox
import si.ograjavizija.app.ui.theme.Accent
import si.ograjavizija.app.ui.theme.Muted

/**
 * Korak 4: prilagodi položaj — 4 vogali, ki jih uporabnik vleče (zahteva 7),
 * + drsniki za fino nastavljanje. Predogled se izrisuje sproti (🟢 lokalno).
 */
@Composable
fun PlaceScreen(projectId: String?, onNext: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf<Project?>(null) }
    var scene by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var cutout by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var placement by remember { mutableStateOf<Placement?>(null) }
    var preview by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var dragIdx by remember { mutableIntStateOf(-1) }
    var busy by remember { mutableStateOf(false) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    var previewRevision by remember { mutableIntStateOf(0) }

    fun recompute() {
        val s = scene ?: return
        val c = cutout ?: return
        val pl = placement ?: return
        if (!pl.isValid) return
        previewJob?.cancel()
        val revision = previewRevision + 1
        previewRevision = revision
        previewJob = scope.launch {
            kotlinx.coroutines.delay(80)
            val bmp = withContext(Dispatchers.Default) {
                val sw = s.width; val sh = s.height
                val px = IntArray(sw * sh); s.getPixels(px, 0, sw, 0, 0, sw, sh)
                val pw = c.width; val ph = c.height
                val ppx = IntArray(pw * ph); c.getPixels(ppx, 0, pw, 0, 0, pw, ph)
                val r = PureCore.composite(
                    px, sw, sh, ppx, pw, ph,
                    pl.corners.map { floatArrayOf(it.x, it.y) },
                    featherPx = 2,
                    colorMatch = PureCore.ColorMatchMode.LUMA_ONLY, colorMatchStrength = 0.35f,
                    shadow = PureCore.ShadowOptions(5, 10, 16, 0.3f),
                )
                android.graphics.Bitmap.createBitmap(sw, sh, android.graphics.Bitmap.Config.ARGB_8888)
                    .also { it.setPixels(r.pixels, 0, sw, 0, 0, sw, sh) }
            }
            if (revision == previewRevision) preview = bmp
        }
    }

    LaunchedEffect(projectId) {
        val p = projectId?.let { ProjectStore.load(it) } ?: AppState.currentProject ?: return@LaunchedEffect
        project = p
        val s = ProjectStore.readBitmap(p, "original.jpg", 1600) ?: return@LaunchedEffect
        scene = s
        cutout = ProjectStore.readBitmap(p, "cutout.png", 1200)
        placement = p.placement ?: ProjectController.defaultPlacement(s.width, s.height)
        recompute()
    }

    Column(Modifier.fillMaxSize()) {
        StepHeader(4, "4 · Prilagodi položaj (vleci vogale)", onBack)
        val s = scene
        val pl = placement
        if (s != null && pl != null) {
            ZoomPanBox(
                image = (preview ?: s).copy(android.graphics.Bitmap.Config.ARGB_8888, false).asImageBitmap(),
                modifier = Modifier.weight(1f),
                onDown = { x, y ->
                    // ali je dotik blizu katerega vogala?
                    var best = -1; var bestD = 60f * 60f
                    pl.corners.forEachIndexed { i, c ->
                        val d = (c.x - x) * (c.x - x) + (c.y - y) * (c.y - y)
                        if (d < bestD) { bestD = d; best = i }
                    }
                    dragIdx = best
                },
                onMove = { x, y ->
                    if (dragIdx in 0..3) {
                        placement = pl.copy(corners = pl.corners.toMutableList().also { it[dragIdx] = Pt(x, y) })
                        recompute()
                    }
                },
                onUp = { dragIdx = -1 },
                overlay = {
                    if (pl.corners.size == 4) {
                        val path = Path().apply {
                            moveTo(pl.corners[0].x, pl.corners[0].y)
                            for (i in 1..3) lineTo(pl.corners[i].x, pl.corners[i].y)
                            close()
                        }
                        drawPath(path, color = Accent, style = Stroke(width = 3f / 1f))
                        pl.corners.forEachIndexed { i, c ->
                            drawCircle(color = if (i == dragIdx) Color.White else Accent, radius = 14f, center = Offset(c.x, c.y))
                            drawCircle(color = Color(0xAA000000), radius = 6f, center = Offset(c.x, c.y))
                        }
                    }
                },
            )
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Text("Vleci štiri vogale na robova dejanske ograje. Predogled je 🟢 lokalen in takojšen.", color = Muted, style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        val p = project ?: return@Button
                        scope.launch {
                            val p2 = ProjectStore.save(p.copy(placement = placement))
                            project = p2; AppState.setProject(p2)
                            onNext()
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Naprej: ✨ ustvari vizualizacijo") }
                }
            }
        } else {
            Text("Manjka fotografija ali izrez izdelka.", modifier = Modifier.padding(24.dp), color = Muted)
        }
    }
}

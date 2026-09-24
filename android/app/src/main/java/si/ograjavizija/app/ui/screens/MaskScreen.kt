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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.ograjavizija.app.data.AppState
import si.ograjavizija.app.data.Project
import si.ograjavizija.app.data.ProjectController
import si.ograjavizija.app.data.ProjectStore
import si.ograjavizija.app.data.RoksalCategory
import si.ograjavizija.app.data.MaskSource
import si.ograjavizija.app.imaging.MaskEditor
import si.ograjavizija.app.segmentation.OnDeviceSegmenter
import si.ograjavizija.app.ui.components.StepHeader
import si.ograjavizija.app.ui.components.ZoomPanBox
import si.ograjavizija.app.ui.theme.Muted
import si.ograjavizija.app.ui.theme.Warn

/** Korak 3: označi staro ograjo (tap / čopič / pravokotnik / SAM-na-napravi). */
@Composable
fun MaskScreen(projectId: String?, onNext: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf<Project?>(null) }
    var scene by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var editor by remember { mutableStateOf<MaskEditor?>(null) }
    var maskBmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var tool by remember { mutableStateOf(MaskEditor.Tool.BRUSH_ADD) }
    var brush by remember { mutableFloatStateOf(30f) }
    var status by remember { mutableStateOf("") }
    var rectStart by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var segReady by remember { mutableStateOf(false) }
    var autoBusy by remember { mutableStateOf(false) }
    var maskSource by remember { mutableStateOf(MaskSource.MANUAL) }
    val sceneImage = remember(scene) { scene?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)?.asImageBitmap() }
    val maskImage = remember(maskBmp) { maskBmp?.asImageBitmap() }

    LaunchedEffect(projectId) {
        val p = projectId?.let { ProjectStore.load(it) } ?: AppState.currentProject ?: return@LaunchedEffect
        project = p
        val s = ProjectStore.readBitmap(p, "original.jpg", 2000) ?: return@LaunchedEffect
        scene = s
        editor = ProjectController.loadMask(p)
        maskBmp = editor?.toBitmap()
        val autoSupported = p.config?.category == RoksalCategory.OGRAJA
        segReady = if (autoSupported) {
            withContext(Dispatchers.IO) {
                if (OnDeviceSegmenter.modelReady) true
                else OnDeviceSegmenter.ensureModel(ctx) != null && OnDeviceSegmenter.init(ctx)
            }
        } else false
        status = if (autoSupported && segReady)
            "🟢 samodejna + ročna segmentacija pripravljena"
        else
            "🟢 ročno označi območje, nato ga po potrebi popravi s čopičem."
    }

    fun refresh() {
        maskBmp = editor?.toBitmap()
    }

    Column(Modifier.fillMaxSize()) {
        StepHeader(3, "3 · Označi območje za zamenjavo", onBack)
        val s = scene
        if (s != null) {
            ZoomPanBox(
                image = sceneImage!!,
                modifier = Modifier.weight(1f),
                onTap = { x, y ->
                    val e = editor ?: return@ZoomPanBox
                    when (tool) {
                        MaskEditor.Tool.SET_SEGMENT -> {
                            scope.launch {
                                status = "Segmentiram…"
                                val m = withContext(Dispatchers.Default) { OnDeviceSegmenter.segmentAt(s, x / s.width, y / s.height) }
                                if (m != null) { e.beginStroke(); e.setFromSegmentation(m, s.width, s.height); refresh(); status = "🟢 segmentacija dodana" }
                                else status = "⚠️ ${OnDeviceSegmenter.lastError ?: "segmentacija ni uspela"}"
                            }
                        }
                        MaskEditor.Tool.FILL_ADD, MaskEditor.Tool.FILL_ERASE -> {
                            e.beginStroke(); e.floodFill(s, x, y, 40, tool == MaskEditor.Tool.FILL_ADD); refresh()
                        }
                        MaskEditor.Tool.RECT_ADD, MaskEditor.Tool.RECT_ERASE -> {
                            val rs = rectStart
                            if (rs == null) { rectStart = x to y; status = "Pravokotnik: tapni še nasprotni vogal." }
                            else {
                                e.beginStroke()
                                e.fillRect(rs.first, rs.second, x, y, tool == MaskEditor.Tool.RECT_ADD)
                                rectStart = null; status = ""; refresh()
                            }
                        }
                        else -> { e.beginStroke(); e.stamp(x, y, brush, tool == MaskEditor.Tool.BRUSH_ADD); refresh() }
                    }
                },
                onDown = { x, y ->
                    val e = editor ?: return@ZoomPanBox
                    if (tool == MaskEditor.Tool.BRUSH_ADD || tool == MaskEditor.Tool.BRUSH_ERASE) {
                        e.beginStroke(); e.stamp(x, y, brush, tool == MaskEditor.Tool.BRUSH_ADD); refresh()
                    }
                },
                onMove = { x, y ->
                    val e = editor ?: return@ZoomPanBox
                    when (tool) {
                        MaskEditor.Tool.BRUSH_ADD, MaskEditor.Tool.BRUSH_ERASE -> { e.stamp(x, y, brush, tool == MaskEditor.Tool.BRUSH_ADD); refresh() }
                        else -> {}
                    }
                },
                onUp = { },
                overlay = {
                    maskBmp?.let { m ->
                        drawImage(maskImage!!, alpha = 1f)
                    }
                },
            )
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ToolChip("👆 tap", tool == MaskEditor.Tool.SET_SEGMENT && segReady, enabled = segReady) { tool = MaskEditor.Tool.SET_SEGMENT }
                    ToolChip("➕", tool == MaskEditor.Tool.BRUSH_ADD) { tool = MaskEditor.Tool.BRUSH_ADD }
                    ToolChip("➖", tool == MaskEditor.Tool.BRUSH_ERASE) { tool = MaskEditor.Tool.BRUSH_ERASE }
                    ToolChip("▭", tool == MaskEditor.Tool.RECT_ADD) { tool = MaskEditor.Tool.RECT_ADD }
                    ToolChip("🪄", tool == MaskEditor.Tool.FILL_ADD) { tool = MaskEditor.Tool.FILL_ADD }
                    ToolChip("↩️", editor?.canUndo == true) { editor?.undo(); refresh() }
                    ToolChip("↪️", editor?.canRedo == true) { editor?.redo(); refresh() }
                    ToolChip("🗑", true) { editor?.clear(); refresh() }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Čopič ${brush.toInt()} px", color = Muted, style = MaterialTheme.typography.labelSmall)
                    Slider(value = brush, onValueChange = { brush = it }, valueRange = 6f..160f, modifier = Modifier.weight(1f))
                }
                if (rectStart != null) Text("Pravokotnik: tapni še nasprotni vogal.", color = Warn, style = MaterialTheme.typography.labelSmall)
                if (project?.config?.category == RoksalCategory.OGRAJA) {
                Button(
                    onClick = {
                        val e = editor ?: return@Button
                        val s2 = s
                        if (!segReady || autoBusy) return@Button
                        autoBusy = true
                        status = "Samodejno označujem ograjo…"
                        scope.launch {
                            val m = withContext(Dispatchers.Default) {
                                OnDeviceSegmenter.segmentAutomatic(s2)
                            }
                            if (m != null) {
                                e.beginStroke()
                                e.setFromSegmentation(m, s2.width, s2.height)
                                maskSource = MaskSource.AUTO_SEGMENT
                                refresh()
                                status = "🟢 Samodejna maska pripravljena — po potrebi popravi ročno."
                            } else {
                                status = "⚠️ ${OnDeviceSegmenter.lastError ?: "Samodejna segmentacija ni uspela"}"
                            }
                            autoBusy = false
                        }
                    },
                    enabled = segReady && !autoBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (autoBusy) "⏳ Samodejno…" else "✨ Samodejno označi ograjo")
                }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = editor?.mask?.any { (it.toInt() and 0xFF) > 0 } == true,
                        onClick = {
                        val e = editor ?: return@Button
                        val p = project ?: return@Button
                        scope.launch {
                            val p2 = ProjectController.saveMask(p, e, maskSource)
                            project = p2; AppState.setProject(p2)
                            onNext()
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("✅ Potrdi območje") }
                }
                if (status.isNotEmpty()) Text(status, color = if (status.startsWith("⚠")) Warn else Muted, style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Text("Najprej fotografiraj balkon (korak 1).", modifier = Modifier.padding(24.dp), color = Muted)
        }
    }
}

package si.ograjavizija.app.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import si.ograjavizija.app.data.AppState
import si.ograjavizija.app.data.Project
import si.ograjavizija.app.data.ProjectController
import si.ograjavizija.app.data.ProjectStore
import si.ograjavizija.app.data.RenderMode
import si.ograjavizija.app.imaging.MaskEditor
import si.ograjavizija.app.ui.components.StepHeader
import si.ograjavizija.app.ui.components.ZoomPanBox
import si.ograjavizija.app.ui.theme.Muted
import si.ograjavizija.app.ui.theme.Warn

/**
 * Korak 2: fotografija izdelka + odstranjevanje ozadja + ročni čopič (zahteva 4).
 */
@Composable
fun ProductScreen(projectId: String?, onNext: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf<Project?>(null) }
    var cutout by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var editor by remember { mutableStateOf<MaskEditor?>(null) }
    var overlayBmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var brush by remember { mutableFloatStateOf(24f) }
    var tool by remember { mutableStateOf(MaskEditor.Tool.BRUSH_ADD) }
    var status by remember { mutableStateOf("") }
    var pendingUri by remember { mutableStateOf<String?>(null) }

    fun refreshCutout() {
        val p = project ?: return
        val b = ProjectStore.readBitmap(p, "cutout.png", 1600) ?: return
        cutout = b
        val e = MaskEditor.fromAlphaBitmap(b)
        editor = e
        overlayBmp = e.toBitmap()
    }

    suspend fun loadProduct(uri: String) {
        val p = project ?: return
        val (np, how) = ProjectController.setProduct(ctx, p, uri, AppState.serverUrl.let {
            if (it.isBlank()) RenderMode.LOCAL_GEOMETRY else RenderMode.SERVER_REMOVAL
        }, AppState.serverUrl)
        np?.let { project = it; AppState.setProject(it) }
        status = "Izrez: $how"
        refreshCutout()
    }

    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val takePic = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingUri?.let { u -> scope.launch { loadProduct(u) } }
    }
    val pickPic = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { scope.launch { loadProduct(it.toString()) } }
    }

    LaunchedEffect(projectId) {
        val p = projectId?.let { ProjectStore.load(it) } ?: AppState.currentProject
        project = p
        if (p?.product != null) refreshCutout()
    }

    fun commitEdit() {
        val e = editor ?: return
        val p = project ?: return
        overlayBmp = e.toBitmap()
        // izrez = cutout piksli * nova alfa
        val c = cutout ?: return
        val w = c.width; val h = c.height
        val px = IntArray(w * h); c.getPixels(px, 0, w, 0, 0, w, h)
        val m = e.mask
        val mw = e.maskW; val mh = e.maskH
        for (y in 0 until h) for (x in 0 until w) {
            val mv = m[(y.coerceAtMost(mh - 1)) * mw + x.coerceAtMost(mw - 1)].toInt() and 0xFF
            val i = y * w + x
            px[i] = (mv shl 24) or (px[i] and 0x00FFFFFF)
        }
        val nb = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        nb.setPixels(px, 0, w, 0, 0, w, h)
        cutout = nb
        scope.launch { ProjectStore.writeBitmap(p, "cutout.png", nb) }
    }

    Column(Modifier.fillMaxSize()) {
        StepHeader(2, "2 · Dodaj svojo ograjo", onBack)
        if (cutout == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("Posnemi fotografijo svoje ograje (npr. v skladišču), na čim bolj enostavnem ozadju.", color = Muted)
                Spacer(Modifier.height(14.dp))
                Button(onClick = {
                    camPerm.launch(Manifest.permission.CAMERA)
                    val f = java.io.File(ctx.cacheDir, "camera").apply { mkdirs() }
                    val tmp = java.io.File(f, "product_${System.currentTimeMillis()}.jpg")
                    pendingUri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", tmp).toString()
                    takePic.launch(Uri.parse(pendingUri))
                }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("📷 Fotografiraj ograjo") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { pickPic.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text("📁 Izberi iz galerije")
                }
                if (status.isNotEmpty()) Text(status, color = Warn, style = MaterialTheme.typography.labelSmall)
            }
        } else {
            ZoomPanBox(
                image = cutout!!.asImageBitmap(),
                modifier = Modifier.weight(1f),
                onTap = { x, y ->
                    val e = editor ?: return@ZoomPanBox
                    e.beginStroke()
                    when (tool) {
                        MaskEditor.Tool.FILL_ADD, MaskEditor.Tool.FILL_ERASE ->
                            e.floodFill(cutout!!, x, y, 42, tool == MaskEditor.Tool.FILL_ADD)
                        else -> e.stamp(x, y, brush, tool == MaskEditor.Tool.BRUSH_ADD)
                    }
                    commitEdit()
                },
                onDown = { _, _ -> editor?.beginStroke() },
                onMove = { x, y ->
                    val e = editor ?: return@ZoomPanBox
                    if (tool == MaskEditor.Tool.BRUSH_ADD || tool == MaskEditor.Tool.BRUSH_ERASE) {
                        e.stamp(x, y, brush, tool == MaskEditor.Tool.BRUSH_ADD)
                        commitEdit()
                    }
                },
                overlay = {
                    // priročna mreža za presojo prosojnosti
                    drawRect(color = Color(0x33FFFFFF), style = androidx.compose.ui.graphics.drawscope.Fill)
                },
            )
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToolChip("➕ čopič", tool == MaskEditor.Tool.BRUSH_ADD) { tool = MaskEditor.Tool.BRUSH_ADD }
                    ToolChip("➖ čopič", tool == MaskEditor.Tool.BRUSH_ERASE) { tool = MaskEditor.Tool.BRUSH_ERASE }
                    ToolChip("🪄 fill", tool == MaskEditor.Tool.FILL_ADD) { tool = MaskEditor.Tool.FILL_ADD }
                    ToolChip("↩️", editor?.canUndo == true) { editor?.undo(); commitEdit() }
                    ToolChip("↪️", editor?.canRedo == true) { editor?.redo(); commitEdit() }
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Čopič ${brush.toInt()} px", color = Muted, style = MaterialTheme.typography.labelSmall)
                    Slider(value = brush, onValueChange = { brush = it }, valueRange = 4f..120f, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickPic.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("📁 Druga") }
                    Button(onClick = onNext, modifier = Modifier.weight(2f)) { Text("Naprej: označi staro →") }
                }
                if (status.isNotEmpty()) Text(status, color = Warn, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun ToolChip(label: String, active: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        color = if (active) si.ograjavizija.app.ui.theme.Accent else si.ograjavizija.app.ui.theme.SurfaceAlt,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        onClick = onClick,
        enabled = enabled,
    ) {
        Text(
            label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = if (!enabled) Color(0x66E8EAF0) else if (active) Color(0xFF06231A) else Color(0xFFE8EAF0),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

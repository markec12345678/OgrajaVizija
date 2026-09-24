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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import si.ograjavizija.app.ui.components.StepHeader
import si.ograjavizija.app.ui.components.ZoomPanBox
import si.ograjavizija.app.ui.theme.Muted

/** Korak 1: fotografiraj balkon (ali uvozi), zoom/premik, ponovno fotografiraj. */
@Composable
fun SceneScreen(projectId: String?, onNext: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf<Project?>(AppState.currentProject) }
    var bmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var pendingUri by remember { mutableStateOf<String?>(null) }
    var hasCamPerm by remember { mutableStateOf(false) }
    val sceneImage = remember(bmp) { bmp?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)?.asImageBitmap() }

    suspend fun import(c: android.content.Context, uri: String, done: (Project?, android.graphics.Bitmap?) -> Unit) {
        var p = project ?: projectId?.let { ProjectStore.load(it) } ?: ProjectController.createProject("")
        p = ProjectController.setScene(c, p, uri) ?: p
        AppState.setProject(p)
        val b = ProjectStore.readBitmap(p, "original.jpg", 2000)
        done(p, b)
    }

    val takePic = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) pendingUri?.let { u -> scope.launch { import(ctx, u) { p, b -> project = p; bmp = b } } }
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasCamPerm = ok
        if (ok) {
            val f = java.io.File(ctx.cacheDir, "camera").apply { mkdirs() }
            val tmp = java.io.File(f, "scene_${System.currentTimeMillis()}.jpg")
            pendingUri = androidx.core.content.FileProvider.getUriForFile(
                ctx, ctx.packageName + ".fileprovider", tmp).toString()
            takePic.launch(Uri.parse(pendingUri))
        }
    }
    val pickPic = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { u -> scope.launch { import(ctx, u.toString()) { p, b -> project = p; bmp = b } } }
    }

    LaunchedEffect(projectId) {
        val p = projectId?.let { ProjectStore.load(it) } ?: AppState.currentProject
        project = p
        bmp = p?.let { ProjectStore.readBitmap(it, "original.jpg", 2000) }
    }

    Column(Modifier.fillMaxSize()) {
        StepHeader(1, "1 · Fotografiraj balkon", onBack)
        if (bmp != null) {
            ZoomPanBox(image = sceneImage!!,
                modifier = Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickPic.launch("image/*") }, modifier = Modifier.weight(1f)) {
                    Text("📁 Ponovno")
                }
                Button(onClick = onNext, modifier = Modifier.weight(2f)) { Text("Naprej: dodaj ograjo →") }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Posnemi fotografijo prostora, kamor boš postavil ograjo.", color = Muted)
                Spacer(Modifier.height(14.dp))
                Button(onClick = {
                    if (hasCamPerm) {
                        val f = java.io.File(ctx.cacheDir, "camera").apply { mkdirs() }
                        val tmp = java.io.File(f, "scene_${System.currentTimeMillis()}.jpg")
                        pendingUri = androidx.core.content.FileProvider.getUriForFile(
                            ctx, ctx.packageName + ".fileprovider", tmp).toString()
                        takePic.launch(Uri.parse(pendingUri))
                    } else {
                        camPerm.launch(Manifest.permission.CAMERA)
                    }
                }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("📷 Fotografiraj balkon") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { pickPic.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text("📁 Izberi iz galerije")
                }
            }
        }
    }
}

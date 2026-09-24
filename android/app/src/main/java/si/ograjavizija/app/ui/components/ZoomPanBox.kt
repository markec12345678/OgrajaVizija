package si.ograjavizija.app.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * Okvir z zoomom/premikanjem (zahteva 3) in risanjem po sliki (zahteva 4 in 5).
 *
 * Način dela se preklopi z [drawMode]:
 *  - **risanje** (privzeto): 1 prst = poteza/čopič/vlečenje vogala; tap = [onTap]
 *  - **premik**: 1–2 prsta = pan + zoom (pregledovanje slike)
 *
 * Vse koordinate v callbackih in [overlay] so KOORDINATE SLIKE (0..w, 0..h),
 * zato čopič, maska in vogali delujejo pravilno pri poljubnem zoomu.
 */
@Composable
fun ZoomPanBox(
    image: ImageBitmap,
    modifier: Modifier = Modifier,
    drawMode: Boolean = true,
    onTap: ((Float, Float) -> Unit)? = null,
    onDown: ((Float, Float) -> Unit)? = null,
    onMove: ((Float, Float) -> Unit)? = null,
    onUp: (() -> Unit)? = null,
    overlay: (DrawScope.() -> Unit)? = null,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(image) { mutableStateOf(1f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }
    var fitted by remember(image, size) { mutableStateOf(false) }

    // pointerInput lives longer than a recomposition. Keep the gesture handler
    // connected to the latest transform and callbacks instead of stale values.
    val currentScale by rememberUpdatedState(scale)
    val currentOffset by rememberUpdatedState(offset)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDown by rememberUpdatedState(onDown)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnUp by rememberUpdatedState(onUp)

    fun toImg(p: Offset): Offset = Offset(
        ((p.x - currentOffset.x) / currentScale).coerceIn(0f, image.width.toFloat() - 0.001f),
        ((p.y - currentOffset.y) / currentScale).coerceIn(0f, image.height.toFloat() - 0.001f),
    )

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(drawMode, image) {
                if (drawMode) {
                    var start = Offset.Zero
                    var moved = false
                    detectDragGestures(
                        onDragStart = { p ->
                            start = p
                            moved = false
                            val i = toImg(p)
                            currentOnDown?.invoke(i.x, i.y)
                        },
                        onDragEnd = {
                            // ce se prst ni premaknil, gre za tap (npr. segmentacija/flood fill)
                            if (!moved) {
                                val i = toImg(start)
                                currentOnTap?.invoke(i.x, i.y)
                            }
                            currentOnUp?.invoke()
                        },
                        onDragCancel = { onUp?.invoke() },
                        onDrag = { change, drag ->
                            if (drag.getDistance() > 4f) moved = true
                            if (moved) {
                                val i = toImg(change.position)
                                currentOnMove?.invoke(i.x, i.y)
                            }
                            change.consume()
                        },
                    )
                } else {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val old = scale
                        scale = (scale * zoom).coerceIn(0.4f, 10f)
                        val imgCentroid = centroid - offset
                        offset += pan + imgCentroid - imgCentroid * (scale / old)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (!fitted && size.width > 0 && image.width > 0) {
                scale = minOf(size.width.toFloat() / image.width, size.height.toFloat() / image.height) * 0.98f
                offset = Offset(
                    (size.width - image.width * scale) / 2f,
                    (size.height - image.height * scale) / 2f,
                )
                fitted = true
            }
            withTransform({
                translate(left = offset.x, top = offset.y)
                scale(scaleX = scale, scaleY = scale)
            }) {
                drawImage(image)
                overlay?.invoke(this)
            }
        }
    }
}

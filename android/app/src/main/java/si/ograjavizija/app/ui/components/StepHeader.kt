package si.ograjavizija.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import si.ograjavizija.app.ui.STEP_LABELS
import si.ograjavizija.app.ui.theme.Line
import si.ograjavizija.app.ui.theme.Muted
import si.ograjavizija.app.ui.theme.Accent

/** Glava z oznako koraka in vrstico napredka 1..6 (zahteva 14). */
@Composable
fun StepHeader(step: Int, title: String, onBack: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Nazaj", tint = Muted)
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color(0xFFE8EAF0))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            STEP_LABELS.forEachIndexed { i, label ->
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (i + 1 == step) Accent else Muted,
                        maxLines = 1,
                    )
                    Box(
                        Modifier.fillMaxWidth().height(3.dp)
                            .background(if (i + 1 <= step) Accent else Line, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

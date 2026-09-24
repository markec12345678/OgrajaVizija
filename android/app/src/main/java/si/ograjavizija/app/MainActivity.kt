package si.ograjavizija.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import si.ograjavizija.app.ui.AppNav
import si.ograjavizija.app.ui.theme.Bg
import si.ograjavizija.app.ui.theme.VizijaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VizijaTheme {
                Surface(modifier = Modifier.fillMaxSize().background(Bg), color = Bg) {
                    Box(Modifier.fillMaxSize().systemBarsPadding()) { AppNav() }
                }
            }
        }
    }
}

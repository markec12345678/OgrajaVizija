package si.ograjavizija.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import si.ograjavizija.app.ui.screens.HomeScreen
import si.ograjavizija.app.ui.screens.MaskScreen
import si.ograjavizija.app.ui.screens.PlaceScreen
import si.ograjavizija.app.ui.screens.RoksalConfigScreen
import si.ograjavizija.app.ui.screens.RoksalProjectsScreen
import si.ograjavizija.app.ui.screens.RoksalQuoteScreen
import si.ograjavizija.app.ui.screens.ResultScreen
import si.ograjavizija.app.ui.screens.SceneScreen
import si.ograjavizija.app.ui.screens.SettingsScreen

/**
 * Glavni tok Roksal WoodCore konfiguratorja:
 * 1) prostor, 2) Roksal konfiguracija, 3) označitev območja,
 * 4) perspektiva, 5) vizualizacija, 6) strukturirano povpraševanje.
 */
enum class Route {
    HOME, PROJECTS, SCENE, CONFIG, MASK, PLACE, RESULT, QUOTE, SETTINGS
}

val STEP_LABELS = listOf(
    "1 📷 Prostor",
    "2 🧰 Roksal",
    "3 ✏️ Označi",
    "4 📐 Položaj",
    "5 ✨ Rezultat",
    "6 📩 Povpraš",
)

@Composable
fun AppNav() {
    var route by remember { mutableStateOf(Route.HOME) }
    var openProjectId by remember { mutableStateOf<String?>(null) }

    when (route) {
        Route.HOME -> HomeScreen(
            onNew = { id ->
                openProjectId = id
                route = Route.SCENE
            },
            onOpen = { id ->
                openProjectId = id
                route = Route.SCENE
            },
            onSettings = { route = Route.SETTINGS },
            onProjects = { route = Route.PROJECTS },
        )

        Route.PROJECTS -> RoksalProjectsScreen(
            onBack = { route = Route.HOME },
            onSettings = { route = Route.SETTINGS },
        )

        Route.SCENE -> SceneScreen(
            projectId = openProjectId,
            onNext = { route = Route.CONFIG },
            onBack = { route = Route.HOME },
        )

        Route.CONFIG -> RoksalConfigScreen(
            projectId = openProjectId,
            onContinue = { route = Route.MASK },
            onBack = { route = Route.SCENE },
        )

        Route.MASK -> MaskScreen(
            projectId = openProjectId,
            onNext = { route = Route.PLACE },
            onBack = { route = Route.CONFIG },
        )

        Route.PLACE -> PlaceScreen(
            projectId = openProjectId,
            onNext = { route = Route.RESULT },
            onBack = { route = Route.MASK },
        )

        Route.RESULT -> ResultScreen(
            projectId = openProjectId,
            onNewRailing = { route = Route.CONFIG },
            onBack = { route = Route.PLACE },
            onHome = { route = Route.HOME },
            onQuote = { route = Route.QUOTE },
        )

        Route.QUOTE -> RoksalQuoteScreen(
            projectId = openProjectId,
            onBack = { route = Route.RESULT },
            onHome = { route = Route.HOME },
        )

        Route.SETTINGS -> SettingsScreen(
            onBack = { route = Route.HOME }
        )
    }
}

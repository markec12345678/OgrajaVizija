package si.ograjavizija.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import si.ograjavizija.app.ui.screens.HomeScreen
import si.ograjavizija.app.ui.screens.MaskScreen
import si.ograjavizija.app.ui.screens.PlaceScreen
import si.ograjavizija.app.ui.screens.RoksalConfigScreen
import si.ograjavizija.app.ui.screens.RoksalQuoteScreen
import si.ograjavizija.app.ui.screens.ResultScreen
import si.ograjavizija.app.ui.screens.SceneScreen
import si.ograjavizija.app.ui.screens.SettingsScreen

/**
 * Preprosta navigacija brez knjižnice: 6 korakov iz zahteve 14 + domov + nastavitve.
 *
 *  1 📷 Fotografiraj balkon   -> SceneScreen
 *  2 📷 Dodaj svojo ograjo    -> ProductScreen
 *  3 ✏️ Označi staro ograjo   -> MaskScreen
 *  4 📐 Prilagodi položaj     -> PlaceScreen
 *  5 ✨ Ustvari vizualizacijo -> ResultScreen
 *  6 💾 Shrani                -> v ResultScreen
 */
enum class Route { HOME, SCENE, PRODUCT, MASK, PLACE, RESULT, QUOTE, DIRECT_QUOTE, SETTINGS }

val STEP_LABELS = listOf(
    "1 📷 Prostor", "2 🧰 Roksal", "3 ✏️ Označi", "4 📐 Položaj", "5 ✨ Rezultat", "6 📩 Povpraš",
)

@Composable
fun AppNav() {
    var route by remember { mutableStateOf(Route.HOME) }
    var openProjectId by remember { mutableStateOf<String?>(null) }

    when (route) {
        Route.HOME -> HomeScreen(
            onNew = { id -> openProjectId = id; route = Route.SCENE },
            onOpen = { id -> openProjectId = id; route = Route.SCENE },
            onSettings = { route = Route.SETTINGS },
        )
        Route.SCENE -> SceneScreen(projectId = openProjectId, onNext = { route = Route.PRODUCT }, onBack = { route = Route.HOME })
        Route.PRODUCT -> RoksalConfigScreen(
            projectId = openProjectId,
            onContinue = { category ->
                route = if (category == si.ograjavizija.app.data.RoksalCategory.OGRAJA) Route.MASK else Route.DIRECT_QUOTE
            },
            onBack = { route = Route.SCENE },
        )
        Route.MASK -> MaskScreen(projectId = openProjectId, onNext = { route = Route.PLACE }, onBack = { route = Route.PRODUCT })
        Route.PLACE -> PlaceScreen(projectId = openProjectId, onNext = { route = Route.RESULT }, onBack = { route = Route.MASK })
        Route.RESULT -> ResultScreen(
            projectId = openProjectId,
            onNewRailing = { route = Route.PRODUCT },
            onBack = { route = Route.PLACE },
            onHome = { route = Route.HOME },
            onQuote = { route = Route.QUOTE },
        )
        Route.QUOTE -> RoksalQuoteScreen(
            projectId = openProjectId,
            onBack = { route = Route.RESULT },
            onHome = { route = Route.HOME },
        )
        Route.DIRECT_QUOTE -> RoksalQuoteScreen(
            projectId = openProjectId,
            onBack = { route = Route.PRODUCT },
            onHome = { route = Route.HOME },
        )
        Route.SETTINGS -> SettingsScreen(onBack = { route = Route.HOME })
    }
}

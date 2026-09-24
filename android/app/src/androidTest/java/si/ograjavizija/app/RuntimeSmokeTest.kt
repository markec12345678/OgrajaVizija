package si.ograjavizija.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchAndCreateProject_reachesSceneStep() {
        composeRule.onNodeWithText("OgrajaVizija").assertIsDisplayed()
        composeRule.onNodeWithText("Nova vizualizacija").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("1 · Fotografiraj balkon").assertIsDisplayed()
        composeRule.onNodeWithText("Fotografiraj balkon").assertIsDisplayed()
        composeRule.onNodeWithText("Izberi iz galerije").assertIsDisplayed()
    }
}

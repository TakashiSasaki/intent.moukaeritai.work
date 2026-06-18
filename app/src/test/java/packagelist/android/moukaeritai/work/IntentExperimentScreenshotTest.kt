package packagelist.android.moukaeritai.work

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import packagelist.android.moukaeritai.work.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class IntentExperimentScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun test_intent_experiment_screen_snapshot() {
    // We need to inject a ViewModel or just create an instance.
    // For screenshot test, we don't strictly need a functional VM, as long as it's not null.
    // Since we are not actually resolving intents, we can use a dummy/empty VM if possible,
    // or just pass a real one (as it's a unit test environment).
    val viewModel = IntentExperimentViewModel()
    
    composeTestRule.setContent { MyApplicationTheme { IntentExperimentScreen(viewModel = viewModel) } }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/intent_experiment_snapshot.png")
  }
}

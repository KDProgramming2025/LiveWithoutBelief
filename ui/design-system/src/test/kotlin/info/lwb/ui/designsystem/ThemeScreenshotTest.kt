package info.lwb.ui.designsystem

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.junit.Rule
import org.junit.Test

class ThemeScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6)

    @Test
    fun captureLight() {
        paparazzi.snapshot { SampleSurface() }
    }

    @Test
    fun captureDark() {
        paparazzi.snapshot { SampleSurface(dark = true) }
    }
}

@Composable
private fun SampleSurface(dark: Boolean = false) {
    MaterialTheme {
        androidx.compose.material3.Surface(color = if (dark) Color(0xFF111111) else Color(0xFFFFFFFF)) {
            androidx.compose.material3.Text("Design System Preview")
        }
    }
}

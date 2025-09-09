package info.lwb.feature.reader

import app.cash.paparazzi.Paparazzi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.junit.Rule
import org.junit.Test

class ReaderSnapshotTest {
    @get:Rule val paparazzi = Paparazzi()

    @Test fun header_snapshot() {
        paparazzi.snapshot { HeaderSample("Live Without Belief") }
    }
}

@Composable
private fun HeaderSample(title: String) {
    MaterialTheme { Text(text = title) }
}

package info.lwb.feature.reader

import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test
import org.junit.Ignore

@Ignore("Paparazzi snapshot failing with IllegalAccessError under current AGP/Kotlin versions; pending version alignment")
class ReaderScreenSnapshotTest {
    @get:Rule
    val paparazzi = Paparazzi()

    @Test
    fun snapshot_readerPlaceholder() {
        paparazzi.snapshot {
            ReaderScreen()
        }
    }
}

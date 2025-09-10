/*
 * SPDX-License-Identifier: Apache-2.0
 */
package info.lwb.telemetry

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TelemetrySmokeTest {
    @Test
    fun init_and_log_doesNotCrash() {
        val ctx = ApplicationProvider.getApplicationContext<Context>() as android.app.Application
        Telemetry.init(ctx)
        Telemetry.logEvent("test_event", mapOf("k" to "v"))
        Telemetry.recordCaught(RuntimeException("test"))
    }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2025 Live Without Belief
 */
package info.lwb.e2e

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/**
 * Single instrumentation test that runs all E2E steps sequentially in-process.
 * The step implementations remain in their own files; we just invoke them here.
 * This avoids instrumentation moving between separate test classes, which can
 * trigger Activity teardown by the framework/OEM.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class E2e_AllStepsSingleTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1)
    val logRule = LogcatCaptureRule()

    @Test
    fun run_all_steps_in_order_as_single_test() {
        // Inject Hilt for this wrapper test
        hiltRule.inject()
        // Reset abort state
        TestAbortState.clear()

        // Step 1
        TestLog.mark("[WRAP] Step01 start")
        val step01 = Step01_HomeMenuTest()
        step01.homeMenu_showsAtLeastOneItem_withIconAndTitle()
        assertFalse("Step01 marked scenario aborted; failing suite", TestAbortState.isAborted())
        TestLog.mark("[WRAP] Step01 done")

        // Step 2
        TestLog.mark("[WRAP] Step02 start")
        val step02 = Step02_ArticlesListTest()
        step02.openFirstMenuAndAssertArticles()
        assertFalse("Step02 marked scenario aborted; failing suite", TestAbortState.isAborted())
        TestLog.mark("[WRAP] Step02 done")

        // Step 3
        TestLog.mark("[WRAP] Step03 start")
        val step03 = Step03_OpenArticleAndValidateReader()
        step03.openFirstArticle_andVerifyReaderLoads()
        if (TestAbortState.isAborted()) {
            fail("Step03 marked scenario aborted; failing suite")
        }
        TestLog.mark("[WRAP] Step03 done")

        // Step 4
        TestLog.mark("[WRAP] Step04 start")
        val step04 = Step04_AdjustReaderAppearance()
        step04.openAppearance_andVerifyReaderSettings()
        assertFalse("Step04 marked scenario aborted; failing suite", TestAbortState.isAborted())
        TestLog.mark("[WRAP] Step04 done")

        // Step 5
        TestLog.mark("[WRAP] Step05 start")
        val step05 = Step05_VerifyStateRestoration()
        step05.animateScroll_exitAndReopen_assertRestored()
        assertFalse("Step05 marked scenario aborted; failing suite", TestAbortState.isAborted())
        TestLog.mark("[WRAP] Step05 done")

        // Step 6
        TestLog.mark("[WRAP] Step06 start")
        val step06 = Step06_ValidateMediaQuestionButtons()
        step06.validate_media_question_buttons_styles()
        assertFalse("Step06 marked scenario aborted; failing suite", TestAbortState.isAborted())
        TestLog.mark("[WRAP] Step06 done")
    }
}

package com.openinglab.app

import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openinglab.app.ui.AppViewModel
import com.openinglab.app.ui.MainTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Use a disposable test device. Never capture a learner's installation or unrelated screens. */
@RunWith(AndroidJUnit4::class)
class PublicAlphaTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val vm get() = ViewModelProvider(rule.activity)[AppViewModel::class.java]

    @Test fun brandingAndSampleStatisticsAreExplicit() {
        rule.onNodeWithText("ASHVA · ALPHA").assertIsDisplayed()
        rule.onNodeWithText("Profile").performClick()
        rule.onNodeWithText("Demo profile · not a signed-in account", ignoreCase = true).assertIsDisplayed()
        rule.onNodeWithText("Learner").assertIsDisplayed()
        rule.onNodeWithText("Sample statistics and goals below are design previews, not your rating, recall or study history.").assertIsDisplayed()
        rule.onNodeWithText("Review").performClick()
        rule.onNodeWithText("Review preview · sample data", ignoreCase = true).assertIsDisplayed()
    }

    @Test fun labelAndBackupPolicyMatchThePublicPrivacyNotice() {
        val context = rule.activity.applicationContext
        assertEquals("Ashva", context.applicationInfo.loadLabel(context.packageManager).toString())
        assertFalse(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        rule.onNodeWithText("Profile").performClick()
        rule.onNodeWithText("Ashva ${BuildConfig.VERSION_NAME} · Android alpha").performScrollTo().assertIsDisplayed()
    }

    @Test fun publicScreensCanBeCapturedFromSyntheticLessonState() {
        rule.runOnIdle { vm.selectTab(MainTab.LEARN) }
        shot("home")
        rule.onNodeWithText("Explore").performClick()
        rule.onNodeWithText("Ruy López").performScrollTo().performClick()
        rule.onNodeWithText("Play White").performScrollTo().performClick()
        rule.onNodeWithTag("study-mode").performClick()
        rule.runOnIdle { vm.jumpTrainer(5) }
        assertEquals(5, vm.uiState.value.trainer?.ply)
        shot("trainer")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithText("Don't know the name?").performScrollTo().performClick()
        rule.runOnIdle { vm.loadIdentifierExample("ruy-lopez") }
        shot("identifier")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithText("Learn").performClick()
        rule.onNodeWithTag("offline-library").performScrollTo().performClick()
        shot("library")
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        // Compose idleness does not cover Android's splash/window fade animation.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(750)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val directory = File(rule.activity.cacheDir, "public-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        bitmap.recycle()
    }
}

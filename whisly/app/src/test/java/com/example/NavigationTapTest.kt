package com.example

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.core.navigation.WhislyAppRoot
import com.example.core.navigation.WhislyRoutes
import com.example.core.theme.WhislyTheme
import com.example.presentation.WhislyViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationTapTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testNavigationSequence() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = WhislyViewModel(app)

        composeTestRule.setContent {
            WhislyTheme(darkTheme = false) {
                WhislyAppRoot(viewModel = viewModel, startInTranslateDirectly = true)
            }
        }
        composeTestRule.waitForIdle()

        // Initially on Translate
        composeTestRule.onNodeWithTag("translate_home_screen").assertExists()

        // 1. Tap Home
        composeTestRule.onNodeWithTag("nav_tab_home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen").assertExists()

        // 2. Tap Profile
        composeTestRule.onNodeWithTag("nav_tab_profile").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_screen").assertExists()

        // 3. Now tap Translate tab from Profile screen!
        composeTestRule.onNodeWithTag("nav_tab_translate").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("translate_home_screen").assertExists()

        // 4. Test exit arrow back to Home from Translate screen!
        composeTestRule.onNodeWithTag("top_bar_home_exit_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen").assertExists()
    }
}


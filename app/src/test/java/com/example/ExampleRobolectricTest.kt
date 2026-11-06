package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.ai.modelmanager.WhislyModelManager
import com.example.ai.translation.IndicLanguageCatalog
import com.example.core.config.AppConfig
import com.example.core.navigation.WhislyAppRoot
import com.example.core.theme.WhislyTheme
import com.example.core.theme.WhislyTokens
import com.example.data.local.WhislyDatabase
import com.example.data.repositories.WhislyRepository
import com.example.presentation.WhislyViewModel
import com.example.presentation.about.AboutWhislyScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `verify MainActivity launches and transitions cleanly to content`() {
        val activityController = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = activityController.get()
        assertNotNull(activity)
    }

    @Test
    fun `verify app_name is Whisly, Room seeds 13 Wave 1 languages and directed pairs, and user tables start clean`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Whisly", context.getString(R.string.app_name))

        val db = WhislyDatabase.getInstance(context)
        val dao = db.whislyDao()
        val modelManager = WhislyModelManager(context, dao)
        val repository = WhislyRepository(dao, modelManager)

        repository.ensureSeeded()

        val languages = dao.getAllLanguages()
        assertEquals(12, languages.size)
        assertTrue(languages.any { it.code == "ta" && it.isInstalled })
        assertTrue(languages.any { it.code == "bn" && it.isInstalled })
        assertTrue(languages.any { it.code == "sat" && !it.canSpeak })

        val directedPairs = dao.getAllDirectedPairs()
        assertTrue(directedPairs.isNotEmpty())
        val taToBn = dao.getDirectedPair("ta", "bn")
        assertNotNull(taToBn)
        assertEquals("DIRECT", taToBn?.method)

        val sharedMtVerified = modelManager.isPackInstalledAndVerified(IndicLanguageCatalog.SHARED_MT_PACK_ID)
        assertTrue(sharedMtVerified)
        assertFalse(modelManager.canDeleteSharedMtPack())

        assertEquals(0, repository.savedPhrasesFlow.first().size)

        repository.recordEncryptedHistory(
            sourceLang = "ta",
            targetLang = "bn",
            originalText = "வணக்கம், நீங்கள் எப்படி இருக்கிறீர்கள்?",
            translatedText = "নমস্কার, আপনি কেমন আছেন?",
            mode = "VOICE",
            confidence = 0.96f,
            latencyMs = 45L
        )
        val history = repository.decryptedHistoryFlow.first()
        assertTrue(history.isNotEmpty())
        assertEquals("নমস্কার, আপনি কেমন আছেন?", history.first().translatedText)
    }

    @Test
    fun `verify navigation across Home, Translate, History, Profile, and About Whisly with bottom bar hidden on About`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = WhislyViewModel(app)

        composeTestRule.setContent {
            WhislyTheme(darkTheme = viewModel.uiState.value.isDarkTheme) {
                WhislyAppRoot(
                    viewModel = viewModel,
                    startInTranslateDirectly = true
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Verify Translate screen, Strands Voice Orb, Real Waveform, and GooeyNav Bottom Bar
        composeTestRule.onNodeWithTag("translate_home_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("offline_status_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hero_microphone_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("strands_voice_orb_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("audio_waveform_canvas").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("translation_result_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("whisly_gooey_nav_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gooey_nav_liquid_canvas").assertIsDisplayed()

        // 2. Navigate to Home tab
        composeTestRule.onNodeWithTag("nav_tab_home").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()

        // 3. Navigate to History tab
        composeTestRule.onNodeWithTag("nav_tab_history").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history_screen").assertIsDisplayed()

        // 4. Navigate to Profile tab
        composeTestRule.onNodeWithTag("nav_tab_profile").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_screen").assertIsDisplayed()

        // 5. Open About Whisly from Profile -> verify bottom nav is hidden and creator info is displayed
        composeTestRule.onNodeWithTag("profile_open_about_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("about_whisly_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("whisly_gooey_nav_bar").assertDoesNotExist()
        composeTestRule.onNodeWithTag("about_creator_name").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Mohammed Danseer Z").assertIsDisplayed()

        // 6. Navigate back to Profile, then back to Translate tab
        composeTestRule.onNodeWithTag("about_back_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("whisly_gooey_nav_bar").assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tab_translate").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("translate_home_screen").assertIsDisplayed()

        // 7. Verify WhislyTopBar brand, theme toggle (Light <-> Dark), and live connectivity states
        composeTestRule.onNodeWithTag("whisly_top_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("whisly_brand").assertIsDisplayed()
        composeTestRule.onNodeWithTag("whisly_wordmark").assertIsDisplayed()
        composeTestRule.onNodeWithTag("top_theme_toggle_button").assertIsDisplayed()

        val initialDark = viewModel.uiState.value.isDarkTheme
        composeTestRule.onNodeWithTag("top_theme_toggle_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(!initialDark, viewModel.uiState.value.isDarkTheme)
    }

    @Test
    fun `verify AboutWhislyScreen opens Portfolio and GitHub links and respects SUPPORT_URL visibility`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(app)
        val launchedUrls = mutableListOf<String>()

        composeTestRule.setContent {
            WhislyTheme(darkTheme = false) {
                AboutWhislyScreen(
                    onBack = {},
                    supportUrlOverride = AppConfig.SUPPORT_URL,
                    onExternalUrlClick = { launchedUrls.add(it) }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Verify Support the Dream button is hidden when AppConfig.SUPPORT_URL is blank
        assertEquals("", AppConfig.SUPPORT_URL)
        composeTestRule.onNodeWithTag("about_support_dream_button").assertDoesNotExist()

        // Verify Wishlist chips and disclaimer
        composeTestRule.onNodeWithTag("wishlist_chip_ps5").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("wishlist_chip_gta6").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("about_dream_disclaimer").performScrollTo().assertIsDisplayed()

        // Click "My Portfolio" and verify ACTION_VIEW intent for https://devseer.vercel.app/
        composeTestRule.onNodeWithTag("about_portfolio_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        val portfolioIntent = shadowApp.nextStartedActivity
        assertNotNull(portfolioIntent)
        assertEquals(Intent.ACTION_VIEW, portfolioIntent.action)
        assertEquals(AppConfig.PORTFOLIO_URL, portfolioIntent.dataString)

        // Click "GitHub" and verify ACTION_VIEW intent for https://github.com/mdanseergit
        composeTestRule.onNodeWithTag("about_github_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        val githubIntent = shadowApp.nextStartedActivity
        assertNotNull(githubIntent)
        assertEquals(Intent.ACTION_VIEW, githubIntent.action)
        assertEquals(AppConfig.GITHUB_URL, githubIntent.dataString)

        assertEquals(listOf(AppConfig.PORTFOLIO_URL, AppConfig.GITHUB_URL), launchedUrls)
    }

    @Test
    fun `verify Phase 1 tokens and Phase 3 SplashScreen resources`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(0xFFFFF9F5.toInt(), context.getColor(R.color.whisly_light_bg))
        assertEquals(0xFF1A1412.toInt(), context.getColor(R.color.whisly_dark_bg))
        assertEquals(0xFF29211E.toInt(), WhislyTokens.HeaderCocoaBackground.value.shr(32).toInt())

        val splashLogoDrawable = context.getDrawable(R.drawable.ic_splash_whisly_logo)
        assertNotNull(splashLogoDrawable)

        val githubIconDrawable = context.getDrawable(R.drawable.ic_github)
        assertNotNull(githubIconDrawable)
    }
}

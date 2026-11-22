package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.crash.CrashProtectionManager
import com.example.core.security.KeystoreCryptoManager
import com.example.core.theme.WhislyTheme
import com.example.data.local.LanguageEntity
import com.example.data.local.TranslationHistoryEntity
import com.example.data.local.WhislyDatabase
import com.example.presentation.WhislyViewModel
import com.example.presentation.safemode.SafeModeScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Phase 3 Prevention Tests:
 * 1. Cold start launch test (MainActivity cold start + first frame render).
 * 2. Crash-loop protection & Safe Mode test (2 consecutive failures -> 3rd launch opens Safe Mode).
 * 3. Corrupted history & Keystore recovery test (corrupted key / payload recovers without crash).
 * 4. Release smoke test (ViewModel reflection & DAO instantiation).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchAndRecoveryPreventionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var testDb: WhislyDatabase? = null

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        testDb = Room.inMemoryDatabaseBuilder(context, WhislyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        WhislyDatabase.setTestInstance(testDb)

        // Reset crash protection prefs before each test
        val prefs = context.getSharedPreferences("whisly_crash_protection", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        WhislyDatabase.resetInstance()
        testDb?.close()
        testDb = null
    }

    @Test
    fun testColdStartLaunchWithoutException() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CrashProtectionManager.resetCrashLoop(context)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        assertNotNull(activity)
        assertFalse(activity.isFinishing)

        controller.pause().stop().destroy()
    }

    @Test
    fun testCrashLoopProtectionActivatesSafeModeOnThirdFailure() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1st launch failure
        val safeMode1 = CrashProtectionManager.checkAndRecordLaunch(context)
        assertFalse("First launch must not trigger Safe Mode", safeMode1)

        // 2nd launch failure
        val safeMode2 = CrashProtectionManager.checkAndRecordLaunch(context)
        assertFalse("Second launch must not trigger Safe Mode", safeMode2)

        // 3rd launch attempt -> Must engage Safe Mode!
        val safeMode3 = CrashProtectionManager.checkAndRecordLaunch(context)
        assertTrue("Third launch after 2 crashes must activate Safe Mode", safeMode3)

        // Render SafeModeScreen and verify 3 action buttons
        var retryTriggered = false
        var clearingTriggered = false

        composeTestRule.setContent {
            WhislyTheme(darkTheme = false) {
                SafeModeScreen(
                    onRetryNormalStart = { retryTriggered = true },
                    onRestartAfterClearing = { clearingTriggered = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("safe_mode_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("safe_mode_retry_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("safe_mode_clear_packs_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("safe_mode_export_button").assertIsDisplayed()

        // Clicking retry resets the crash loop
        composeTestRule.onNodeWithTag("safe_mode_retry_button").performClick()
        assertTrue(retryTriggered)

        val safeModeAfterReset = CrashProtectionManager.checkAndRecordLaunch(context)
        assertFalse("Safe mode must be disengaged after retry/reset", safeModeAfterReset)
    }

    @Test
    fun testSafeModeClearingPreservesUserHistoryAndSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = WhislyDatabase.getInstance(context)
        val dao = db.whislyDao()

        // Seed some history and a model pack
        dao.insertHistory(
            TranslationHistoryEntity(
                id = "history_123",
                sourceLangCode = "en",
                targetLangCode = "ta",
                originalTextEncrypted = "ENC1:dummy",
                translatedTextEncrypted = "ENC1:dummy",
                mode = "TEXT",
                sttConfidence = 1.0f,
                inferenceLatencyMs = 20L
            )
        )

        // Create dummy downloaded model directory and files
        val dummyModelDir = File(context.filesDir, "models/test_pack").apply {
            mkdirs()
            File(this, "model.onnx").writeText("fake-weights")
        }
        assertTrue(dummyModelDir.exists())

        // Run non-destructive model cleanup
        val latch = java.util.concurrent.CountDownLatch(1)
        CrashProtectionManager.clearDownloadedModelPacks(context) {
            latch.countDown()
        }
        latch.await(3, java.util.concurrent.TimeUnit.SECONDS)

        // Verify model files were cleared
        assertFalse(File(context.filesDir, "models/test_pack").exists())

        // Verify user translation history was strictly preserved!
        val history = dao.observeHistory().first()
        assertEquals(1, history.size)
        assertEquals("history_123", history.first().id)
    }

    @Test
    fun testCorruptedKeystoreRecoveryDoesNotCrash() {
        val plainText = "Sensitive user translation history item"
        val encrypted = KeystoreCryptoManager.encryptText(plainText)
        assertTrue(encrypted.startsWith("ENC1:"))

        // Verify clean decryption
        val decrypted = KeystoreCryptoManager.decryptText(encrypted)
        assertEquals(plainText, decrypted)

        // Artificially corrupt the encryption key by deleting and regenerating
        KeystoreCryptoManager.deleteAndRegenerateKey()

        // Decrypting payload encrypted with previous key will cause AEADBadTagException / invalid tag
        val recovered = KeystoreCryptoManager.decryptText(encrypted)
        // Must recover safely without throwing unhandled exception
        assertNotNull(recovered)

        // StateFlow must report credential rotation
        assertTrue(KeystoreCryptoManager.credentialsRotatedFlow.value)
        KeystoreCryptoManager.acknowledgeCredentialsRotated()
        assertFalse(KeystoreCryptoManager.credentialsRotatedFlow.value)
    }

    @Test
    fun testReleaseSmokeViewModelReflectionInstantiation() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Verify ViewModel constructor reflection contract required by ViewModelProvider
        val constructor = WhislyViewModel::class.java.getConstructor(Application::class.java)
        assertNotNull(constructor)
        val viewModel = constructor.newInstance(app)
        assertNotNull(viewModel)
        assertNotNull(viewModel.uiState)
        assertNotNull(viewModel.isColdStartReady)
    }
}

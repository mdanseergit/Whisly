package com.example

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.core.crash.CrashProtectionManager
import com.example.core.navigation.WhislyAppRoot
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTheme
import com.example.presentation.WhislyViewModel
import com.example.presentation.safemode.SafeModeScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val whislyViewModel: WhislyViewModel by viewModels()
    private var isSafeModeActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Install UncaughtExceptionHandler and evaluate Crash-Loop Protection
        CrashProtectionManager.installCrashHandler(this)
        isSafeModeActive = CrashProtectionManager.checkAndRecordLaunch(this)
        // Capture historical exit reasons (API 30+) for the in-app Exit Report (no user content).
        com.example.core.crash.ExitReportManager.captureHistoricalExitReasons(this)

        // 2. Install AndroidX SplashScreen BEFORE super.onCreate() for zero-flash cold start
        val splashScreen = installSplashScreen()
        val coldStartTimestamp = SystemClock.uptimeMillis()

        super.onCreate(savedInstanceState)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val initialWindowColor = if (isNight) "#1A1412" else "#F5ECE4"
        window.setBackgroundDrawable(ColorDrawable(Color.parseColor(initialWindowColor)))

        configureSplashScreen(splashScreen, coldStartTimestamp)
        enableEdgeToEdge()

        if (isSafeModeActive) {
            setContent {
                SafeModeScreen(
                    onRetryNormalStart = {
                        recreate()
                    },
                    onRestartAfterClearing = {
                        recreate()
                    }
                )
            }
        } else {
            // Schedule crash-loop reset once app remains stable past 5 seconds
            lifecycleScope.launch {
                delay(CrashProtectionManager.LAUNCH_STABILITY_WINDOW_MS)
                CrashProtectionManager.markLaunchSuccessful(this@MainActivity)
            }

            setContent {
                val uiState by whislyViewModel.uiState.collectAsStateWithLifecycle()
                WhislyTheme(darkTheme = uiState.isDarkTheme) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(WhislyDesign.colors.background)
                    ) {
                        WhislyAppRoot(viewModel = whislyViewModel)
                    }
                }
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!isSafeModeActive) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
                level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
            ) {
                whislyViewModel.onTrimMemory(level)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isSafeModeActive) {
            whislyViewModel.onAppBackgrounded()
        }
    }

    private fun configureSplashScreen(
        splashScreen: SplashScreen,
        coldStartTimestamp: Long
    ) {
        splashScreen.setKeepOnScreenCondition {
            if (isSafeModeActive) return@setKeepOnScreenCondition false
            val elapsed = SystemClock.uptimeMillis() - coldStartTimestamp
            !whislyViewModel.isColdStartReady.value && elapsed < 300L
        }

        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val reducedMotion = runCatching {
                Settings.Global.getFloat(
                    contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE,
                    1f
                ) == 0f
            }.getOrDefault(false)

            if (reducedMotion) {
                splashScreenViewProvider.remove()
                return@setOnExitAnimationListener
            }

            val splashView = splashScreenViewProvider.view
            val iconView = splashScreenViewProvider.iconView
            val fastOutSlowIn = PathInterpolator(0.4f, 0f, 0.2f, 1f)

            val animators = mutableListOf<Animator>()
            animators.add(ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f))
            // iconView is non-null per the SplashScreenViewProvider contract; the old null/identity
            // check was always true and has been removed rather than left to hide a real change.
            animators.add(ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 1.05f))
            animators.add(ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 1.05f))
            animators.add(ObjectAnimator.ofFloat(iconView, View.ALPHA, 1f, 0f))

            val animSet = AnimatorSet().apply {
                duration = 250L
                interpolator = fastOutSlowIn
                playTogether(animators)
                doOnEnd {
                    splashScreenViewProvider.remove()
                }
            }

            // Fallback timeout in case animation callback is skipped
            splashView.postDelayed({
                splashScreenViewProvider.remove()
            }, 350L)

            runCatching {
                animSet.start()
            }.onFailure {
                splashScreenViewProvider.remove()
            }
        }
    }
}

package com.example

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
import com.example.core.navigation.WhislyAppRoot
import com.example.core.theme.WhislyDesign
import com.example.core.theme.WhislyTheme
import com.example.presentation.WhislyViewModel

class MainActivity : ComponentActivity() {

    private val whislyViewModel: WhislyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // PHASE 3 — Install AndroidX SplashScreen BEFORE super.onCreate() for zero-flash cold start
        val splashScreen = installSplashScreen()
        val coldStartTimestamp = SystemClock.uptimeMillis()

        super.onCreate(savedInstanceState)

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val initialWindowColor = if (isNight) "#1A1412" else "#FFF9F5"
        window.setBackgroundDrawable(ColorDrawable(Color.parseColor(initialWindowColor)))

        configureSplashScreen(splashScreen, coldStartTimestamp)
        enableEdgeToEdge()

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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
        ) {
            whislyViewModel.onTrimMemory(level)
        }
    }

    override fun onStop() {
        super.onStop()
        whislyViewModel.onAppBackgrounded()
    }

    private fun configureSplashScreen(
        splashScreen: SplashScreen,
        coldStartTimestamp: Long
    ) {
        splashScreen.setKeepOnScreenCondition {
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

            val containerFadeOut = ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f)
            val iconScaleX = ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 1.05f)
            val iconScaleY = ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 1.05f)
            val iconFadeOut = ObjectAnimator.ofFloat(iconView, View.ALPHA, 1f, 0f)

            splashView.postDelayed({
                splashScreenViewProvider.remove()
            }, 300L)

            AnimatorSet().apply {
                duration = 250L
                interpolator = fastOutSlowIn
                playTogether(containerFadeOut, iconScaleX, iconScaleY, iconFadeOut)
                doOnEnd {
                    splashScreenViewProvider.remove()
                }
                start()
            }
        }
    }
}

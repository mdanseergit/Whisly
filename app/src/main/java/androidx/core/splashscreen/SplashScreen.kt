package androidx.core.splashscreen

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.R

/**
 * AndroidX SplashScreen API (`androidx.core.splashscreen.SplashScreen`) implementation
 * supporting API 24..36 with:
 * - `Activity.installSplashScreen()` called prior to `super.onCreate()`
 * - Theme-matched background (`#FFF9F5` light / `#1A1412` dark) with zero flash
 * - Centered Whisly logo + bold `"WHISLY"` wordmark
 * - `KeepOnScreenCondition` via `ViewTreeObserver.OnPreDrawListener`
 * - `OnExitAnimationListener` with `SplashScreenViewProvider` (`view`, `iconView`, `remove()`)
 */
class SplashScreen private constructor(private val activity: Activity) {

    fun interface KeepOnScreenCondition {
        fun shouldKeepOnScreen(): Boolean
    }

    fun interface OnExitAnimationListener {
        fun onSplashScreenExit(splashScreenViewProvider: SplashScreenViewProvider)
    }

    class SplashScreenViewProvider internal constructor(
        val view: View,
        val iconView: View,
        private val onRemoveCallback: () -> Unit
    ) {
        fun remove() {
            onRemoveCallback()
        }
    }

    private var keepOnScreenCondition: KeepOnScreenCondition = KeepOnScreenCondition { false }
    private var exitAnimationListener: OnExitAnimationListener? = null
    private var preDrawListenerAttached = false
    private var exitDispatched = false

    internal fun install() {
        runCatching {
            activity.setTheme(R.style.Theme_MyApplication)
        }
        attachPreDrawGate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            attachApi31ExitListener()
        }
    }

    fun setKeepOnScreenCondition(condition: KeepOnScreenCondition) {
        keepOnScreenCondition = condition
        attachPreDrawGate()
    }

    fun setOnExitAnimationListener(listener: OnExitAnimationListener) {
        exitAnimationListener = listener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            attachApi31ExitListener()
        }
    }

    private fun attachPreDrawGate() {
        if (preDrawListenerAttached) return
        val contentView = activity.findViewById<View>(android.R.id.content) ?: return
        preDrawListenerAttached = true
        contentView.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    return if (keepOnScreenCondition.shouldKeepOnScreen()) {
                        false
                    } else {
                        contentView.viewTreeObserver.removeOnPreDrawListener(this)
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                            dispatchLegacyExitAnimationIfNeeded()
                        }
                        true
                    }
                }
            }
        )
    }

    private fun attachApi31ExitListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                activity.splashScreen.setOnExitAnimationListener { platformSplashView ->
                    val listener = exitAnimationListener
                    if (listener == null) {
                        platformSplashView.remove()
                    } else {
                        val icon = platformSplashView.iconView ?: platformSplashView
                        val provider = SplashScreenViewProvider(
                            view = platformSplashView,
                            iconView = icon,
                            onRemoveCallback = {
                                runCatching { platformSplashView.remove() }
                            }
                        )
                        listener.onSplashScreenExit(provider)
                    }
                }
            }
        }
    }

    private fun dispatchLegacyExitAnimationIfNeeded() {
        if (exitDispatched) return
        exitDispatched = true
        val listener = exitAnimationListener ?: return
        val decorView = activity.window?.decorView as? ViewGroup ?: return

        val isNight = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val bgColor = if (isNight) Color.parseColor("#1A1412") else Color.parseColor("#FFF9F5")
        val textColor = if (isNight) Color.parseColor("#FFF9F5") else Color.parseColor("#29211E")

        val density = activity.resources.displayMetrics.density
        val splashContainer = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(bgColor)
        }

        val centerStack = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        val badgeSizePx = (96f * density).toInt()
        val iconPaddingPx = (16f * density).toInt()
        val logoImageView = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(badgeSizePx, badgeSizePx)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f * density
                setColor(Color.parseColor("#FFF9F5"))
            }
            setPadding(iconPaddingPx, iconPaddingPx, iconPaddingPx, iconPaddingPx)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.whisly_launcher_fg)
        }

        val titleView = TextView(activity).apply {
            text = "WHISLY"
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.12f
            gravity = Gravity.CENTER
            setPadding(0, (14f * density).toInt(), 0, 0)
        }

        centerStack.addView(logoImageView)
        centerStack.addView(titleView)
        splashContainer.addView(centerStack)
        decorView.addView(splashContainer)

        val provider = SplashScreenViewProvider(
            view = splashContainer,
            iconView = centerStack,
            onRemoveCallback = {
                runCatching { decorView.removeView(splashContainer) }
            }
        )
        splashContainer.post {
            listener.onSplashScreenExit(provider)
        }
    }

    companion object {
        @JvmStatic
        fun Activity.installSplashScreen(): SplashScreen {
            return SplashScreen(this).also { it.install() }
        }
    }
}

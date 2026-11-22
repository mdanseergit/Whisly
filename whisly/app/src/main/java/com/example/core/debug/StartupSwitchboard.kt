package com.example.core.debug

import android.content.Context
import android.content.Intent

/**
 * DEBUG-only Startup Switchboard (Phase 1 Bisect).
 *
 * Allows independently disabling post-first-frame tasks via Intent extras or SharedPreferences:
 * - disable_agsl: forces zero-allocation Canvas fallback instead of AGSL RuntimeShader
 * - disable_history: bypasses Room translation history loading and AES-GCM decryption
 * - disable_seed: bypasses initial database catalog seeding
 * - disable_network: bypasses ConnectivityManager callback flow
 * - disable_clock: stops the continuous animation frame clock
 */
object StartupSwitchboard {
    private const val PREFS_NAME = "whisly_debug_switchboard"

    @Volatile var disableAgsl: Boolean = false
    @Volatile var disableHistory: Boolean = false
    @Volatile var disableDbSeed: Boolean = false
    @Volatile var disableNetworkObserver: Boolean = false
    @Volatile var disableAnimationClock: Boolean = false

    fun initFromIntent(intent: Intent?, context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (intent != null) {
            if (intent.hasExtra("disable_agsl")) {
                disableAgsl = intent.getBooleanExtra("disable_agsl", false)
                prefs.edit().putBoolean("disable_agsl", disableAgsl).apply()
            }
            if (intent.hasExtra("disable_history")) {
                disableHistory = intent.getBooleanExtra("disable_history", false)
                prefs.edit().putBoolean("disable_history", disableHistory).apply()
            }
            if (intent.hasExtra("disable_seed")) {
                disableDbSeed = intent.getBooleanExtra("disable_seed", false)
                prefs.edit().putBoolean("disable_seed", disableDbSeed).apply()
            }
            if (intent.hasExtra("disable_network")) {
                disableNetworkObserver = intent.getBooleanExtra("disable_network", false)
                prefs.edit().putBoolean("disable_network", disableNetworkObserver).apply()
            }
            if (intent.hasExtra("disable_clock")) {
                disableAnimationClock = intent.getBooleanExtra("disable_clock", false)
                prefs.edit().putBoolean("disable_clock", disableAnimationClock).apply()
            }
        } else {
            disableAgsl = prefs.getBoolean("disable_agsl", false)
            disableHistory = prefs.getBoolean("disable_history", false)
            disableDbSeed = prefs.getBoolean("disable_seed", false)
            disableNetworkObserver = prefs.getBoolean("disable_network", false)
            disableAnimationClock = prefs.getBoolean("disable_clock", false)
        }
    }

    fun isAgslDisabled(context: Context): Boolean {
        return disableAgsl || context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("disable_agsl", false)
    }

    fun isHistoryDisabled(context: Context): Boolean {
        return disableHistory || context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("disable_history", false)
    }

    fun isDbSeedDisabled(context: Context): Boolean {
        return disableDbSeed || context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("disable_seed", false)
    }

    fun isDbSeedingDisabled(context: Context): Boolean = isDbSeedDisabled(context)

    fun isNetworkDisabled(context: Context): Boolean {
        return disableNetworkObserver || context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("disable_network", false)
    }

    fun isClockDisabled(context: Context): Boolean {
        return disableAnimationClock || context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean("disable_clock", false)
    }

    fun resetAll(context: Context) {
        disableAgsl = false
        disableHistory = false
        disableDbSeed = false
        disableNetworkObserver = false
        disableAnimationClock = false
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

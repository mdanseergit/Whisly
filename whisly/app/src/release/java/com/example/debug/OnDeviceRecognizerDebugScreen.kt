package com.example.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Release stub: the DEBUG-only on-device recognizer probe is not shipped in release builds.
 * Navigation to this route is disabled in release by a BuildConfig.DEBUG guard.
 */
@Composable
fun OnDeviceRecognizerDebugRoute(onBack: () -> Unit) {
    LaunchedEffect(Unit) { onBack() }
}

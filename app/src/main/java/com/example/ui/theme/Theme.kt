package com.example.ui.theme

import androidx.compose.runtime.Composable
import com.example.core.theme.WhislyTheme

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    WhislyTheme(darkTheme = darkTheme, content = content)
}

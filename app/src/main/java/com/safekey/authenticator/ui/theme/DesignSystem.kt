package com.safekey.authenticator.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalExpressiveDesign = staticCompositionLocalOf { false }

@Composable
fun ProvideDesignSystem(expressive: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExpressiveDesign provides expressive, content = content)
}

package com.velos.net.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 深色模式管理
 * 0 = 跟随系统, 1 = 浅色, 2 = 深色
 */
object DarkModeManager {
    private const val PREFS = "velos_theme"
    private const val KEY_MODE = "dark_mode"

    fun getMode(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_MODE, 0)
    }

    fun setMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_MODE, mode).apply()
    }
}

// 全局可观察的深色模式状态
val LocalDarkMode = compositionLocalOf { mutableStateOf(0) }

object MiuiColors {
    val Brand = Color(0xFF3482FF)
    val TextDark = Color(0xFF1A1A2E)
    val TextGrey = Color(0xFF8E9AAF)
    val Error = Color(0xFFFF5252)
    val Success = Color(0xFF4CAF50)
    val Warning = Color(0xFFFFB300)
}

@Composable
fun VelosTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val darkModeState = remember { mutableStateOf(DarkModeManager.getMode(context)) }

    val isDark = when (darkModeState.value) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    val colors = if (isDark) darkColorScheme() else lightColorScheme()

    CompositionLocalProvider(LocalDarkMode provides darkModeState) {
        MiuixTheme(colors = colors) {
            content()
        }
    }
}

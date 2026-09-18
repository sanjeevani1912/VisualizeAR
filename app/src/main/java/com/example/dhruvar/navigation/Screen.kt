package com.example.dhruvar.navigation

/**
 * Sealed destination routes for app navigation.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Planning : Screen("planning")
    data object Visualization : Screen("visualization")
}

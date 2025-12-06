package com.pitchslap.app.ui.navigation

/**
 * Navigation routes for the app
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Practice : Screen("practice")
    object Feedback : Screen("feedback")
    object History : Screen("history")
    object Settings : Screen("settings")
}

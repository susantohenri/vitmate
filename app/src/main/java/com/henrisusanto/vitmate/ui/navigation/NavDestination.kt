package com.henrisusanto.vitmate.ui.navigation

sealed class NavDestination(val route: String) {
    object Home : NavDestination("home")
    object Downloads : NavDestination("downloads")
    object Settings : NavDestination("settings")
    object Terms : NavDestination("terms")
}

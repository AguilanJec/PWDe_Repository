package com.pwde.app.ui.navigation

import androidx.navigation.NavGraphBuilder

/**
 * Release builds: the Testing Station and the gaze-SDK diagnostics rig are development tools and
 * don't ship. Eye control itself is a real feature and lives in the main graph.
 */
const val TESTING_STATION_AVAILABLE = false

@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
fun NavGraphBuilder.debugDestinations(onBack: () -> Unit) = Unit

package com.pwde.app.ui.navigation

import androidx.navigation.NavGraphBuilder

/** Release builds: the Testing Station is a development tool and doesn't ship. */
const val TESTING_STATION_AVAILABLE = false

@Suppress("UnusedReceiverParameter", "UNUSED_PARAMETER")
fun NavGraphBuilder.debugDestinations(onBack: () -> Unit) = Unit

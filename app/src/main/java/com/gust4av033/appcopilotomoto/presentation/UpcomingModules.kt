package com.gust4av033.appcopilotomoto.presentation

import com.gust4av033.appcopilotomoto.R

/** Catálogo visual de módulos previstos. No representa capacidades implementadas. */
data class UpcomingModuleUi(val titleRes: Int, val descriptionRes: Int, val iconRes: Int)

val upcomingModules = listOf(
    UpcomingModuleUi(R.string.module_motorcycle, R.string.module_motorcycle_detail, R.drawable.ic_motorcycle),
    UpcomingModuleUi(R.string.module_prepare_trip, R.string.module_prepare_trip_detail, R.drawable.ic_trip),
    UpcomingModuleUi(R.string.module_history, R.string.module_history_detail, R.drawable.ic_history)
)

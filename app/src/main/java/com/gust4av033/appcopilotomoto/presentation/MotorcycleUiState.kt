package com.gust4av033.appcopilotomoto.presentation

import androidx.compose.ui.graphics.ImageBitmap
import com.gust4av033.appcopilotomoto.MotorcycleData

data class MotorcycleUiState(
    val data: MotorcycleData = MotorcycleData(),
    val loaded: Boolean = false,
    val working: Boolean = false,
    val error: Boolean = false,
    val messageRes: Int? = null,
    val photo: ImageBitmap? = null,
    val gpsDistanceMeters: Double? = null,
    val gpsTripCount: Int = 0,
    val gpsReadFailed: Boolean = false,
    val revision: Int = 0
)

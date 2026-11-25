package com.example.wearhealthdemo.domain.models

import androidx.compose.runtime.Immutable
import com.example.wearhealthdemo.data.models.HealthKitState

@Immutable
data class UiState(
    val healthKitState: HealthKitState = HealthKitState(),
    val logMessages: List<String> = emptyList()
)

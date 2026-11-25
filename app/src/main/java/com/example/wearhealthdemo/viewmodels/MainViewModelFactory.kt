package com.example.wearhealthdemo.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.wearhealthdemo.healthkit.HealthKitClient
import com.example.wearhealthdemo.push.WorkoutSubscriptions

class MainViewModelFactory(
    private val healthKitClient: HealthKitClient,
    private val workoutSubscriptions: WorkoutSubscriptions,
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(
            healthKitClient = healthKitClient,
            workoutSubscriptions = workoutSubscriptions,
        ) as T
    }
}

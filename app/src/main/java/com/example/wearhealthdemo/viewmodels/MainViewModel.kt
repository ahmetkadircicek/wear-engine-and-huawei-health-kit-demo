package com.example.wearhealthdemo.viewmodels

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wearhealthdemo.data.models.HealthKitState
import com.example.wearhealthdemo.domain.models.UiState
import com.example.wearhealthdemo.healthkit.AuthorizationOutcome
import com.example.wearhealthdemo.healthkit.HealthKitAuthMethod
import com.example.wearhealthdemo.healthkit.HealthKitClient
import com.example.wearhealthdemo.healthkit.HealthKitConfig
import com.example.wearhealthdemo.healthkit.auth.LoginFreeStatus
import com.example.wearhealthdemo.push.WorkoutSubscriptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat

class MainViewModel(
    private val healthKitClient: HealthKitClient,
    private val workoutSubscriptions: WorkoutSubscriptions,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        updateHealthKit {
            it.copy(
                isAuthorized = healthKitClient.isAuthorized,
                authMethod = healthKitClient.authMethod,
                tokenItems = healthKitClient.tokenItems(),
            )
        }
    }

    private fun updateHealthKit(transform: (HealthKitState) -> HealthKitState) {
        _uiState.update { it.copy(healthKitState = transform(it.healthKitState)) }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logMessages = emptyList()) }
    }

    @SuppressLint("SimpleDateFormat")
    private fun addLogMessage(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())
        _uiState.update { it.copy(logMessages = it.logMessages + "[$timestamp] $message") }
    }

    fun selectAuthMethod(method: HealthKitAuthMethod) {
        healthKitClient.useMethod(method)
        updateHealthKit { it.copy(authMethod = method, errorMessage = null) }
        addLogMessage("Health Kit method: ${method.label}")
    }

    fun connectHealthKit(activity: Activity) {
        updateHealthKit { it.copy(isAuthorizing = true, errorMessage = null) }

        healthKitClient.startAuthorization(activity)
            .onSuccess { addLogMessage("Waiting for Huawei Health authorization") }
            .onFailure { error ->
                setHealthKitError("Could not start authorization: ${error.message}")
            }
    }

    fun openHuaweiHealth(activity: Activity) {
        val launch = activity.packageManager
            .getLaunchIntentForPackage(LoginFreeStatus.HUAWEI_HEALTH_PACKAGE)

        if (launch == null) {
            setHealthKitError(
                "The Huawei Health app is not installed, and the privacy authorization " +
                    "can only be granted there."
            )
            return
        }

        runCatching { activity.startActivity(launch) }
        updateHealthKit { it.copy(isAuthorizing = false) }
        addLogMessage("Opened Huawei Health to check the account and the grant")
    }

    fun handleHealthKitRedirect(uri: Uri) {
        if (!healthKitClient.ownsRedirect(uri)) return

        viewModelScope.launch {
            healthKitClient.completeAuthorization(uri)
                .onSuccess {
                    updateHealthKit {
                        it.copy(isAuthorizing = false, isAuthorized = true, errorMessage = null)
                    }
                    updateHealthKit {
                        it.copy(
                            accountOpenId = healthKitClient.accountOpenId,
                            tokenItems = healthKitClient.tokenItems(),
                        )
                    }
                    addLogMessage("Huawei Health account connected")
                    enableWorkoutPush()
                    fetchRecentWorkouts()
                }
                .onFailure { error ->
                    setHealthKitError("Authorization failed: ${error.message}")
                }
        }
    }

    fun handleAuthorizationActivityResult(
        activity: Activity,
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        val outcome = healthKitClient.readAuthorizationOutcome(requestCode, resultCode, data)
        if (outcome == null) {
            updateHealthKit { it.copy(isAuthorizing = false) }
            return
        }

        when (outcome) {
            is AuthorizationOutcome.Redirect -> handleHealthKitRedirect(outcome.uri)

            is AuthorizationOutcome.Failed -> setHealthKitError(outcome.message)
        }
    }

    fun selectLookbackDays(days: Long) {
        val safe = days.coerceIn(1L, HealthKitConfig.MAX_QUERY_RANGE_DAYS)
        updateHealthKit { it.copy(lookbackDays = safe) }
        fetchRecentWorkouts()
    }

    fun fetchRecentWorkouts() {
        updateHealthKit { it.copy(isLoadingWorkouts = true, errorMessage = null) }
        val range = _uiState.value.healthKitState

        viewModelScope.launch {
            healthKitClient.fetchWorkouts(days = range.lookbackDays)
                .onSuccess { fetch ->
                    updateHealthKit {
                        it.copy(
                            isLoadingWorkouts = false,
                            workouts = fetch.records,
                            lastSyncedAtMillis = System.currentTimeMillis(),
                            errorMessage = fetch.notice,
                            needsPrivacy = fetch.needsPrivacy,
                            accountOpenId = healthKitClient.accountOpenId,
                            tokenItems = healthKitClient.tokenItems(),
                        )
                    }
                    addLogMessage(
                        fetch.notice ?: "Fetched ${fetch.records.size} workout(s)"
                    )
                }
                .onFailure { error ->
                    setHealthKitError("Fetch failed: ${error.message}")
                }
        }
    }

    fun refreshToken() {
        updateHealthKit { it.copy(isRefreshingToken = true, errorMessage = null) }

        viewModelScope.launch {
            healthKitClient.refreshAccessToken()
                .onSuccess {
                    updateHealthKit {
                        it.copy(isRefreshingToken = false, tokenItems = healthKitClient.tokenItems())
                    }
                    addLogMessage("Access token refreshed")
                }
                .onFailure { error ->
                    updateHealthKit {
                        it.copy(
                            isRefreshingToken = false,
                            isAuthorized = healthKitClient.isAuthorized,
                            tokenItems = healthKitClient.tokenItems(),
                        )
                    }
                    setHealthKitError("Token refresh failed: ${error.message}")
                }
        }
    }

    fun enableWorkoutPush() {
        val openId = healthKitClient.accountOpenId ?: return

        viewModelScope.launch {
            val accessToken = healthKitClient.accessToken().getOrElse {
                addLogMessage("Workout notifications need a valid session: ${it.message}")
                return@launch
            }

            workoutSubscriptions.enable(openId, accessToken)
                .onSuccess { addLogMessage("Workout notifications on, over ${it.wireName}") }
                .onFailure { addLogMessage("Workout notifications failed: ${it.message}") }
        }
    }

    fun deleteHealthKitData() {
        updateHealthKit { it.copy(isLoadingWorkouts = true, errorMessage = null) }
        addLogMessage("Deleting every activity record this app wrote")

        viewModelScope.launch {
            healthKitClient.deleteEverythingThisAppWrote()
                .onSuccess {
                    updateHealthKit { HealthKitState(authMethod = healthKitClient.authMethod) }
                    addLogMessage(
                        "Deleted this app records and cancelled the authorization, " +
                            "so the account is disconnected"
                    )
                }
                .onFailure { error ->
                    setHealthKitError("Delete failed: ${error.message}")
                }
        }
    }

    fun disconnectHealthKit() {
        healthKitClient.signOut()
        updateHealthKit {
            HealthKitState(
                authMethod = healthKitClient.authMethod,
            )
        }
        addLogMessage("Disconnected Huawei Health account")
    }

    private fun setHealthKitError(message: String) {
        updateHealthKit {
            it.copy(
                isAuthorizing = false,
                isLoadingWorkouts = false,
                isRefreshingToken = false,
                errorMessage = message,
            )
        }
        addLogMessage(message)
    }
}

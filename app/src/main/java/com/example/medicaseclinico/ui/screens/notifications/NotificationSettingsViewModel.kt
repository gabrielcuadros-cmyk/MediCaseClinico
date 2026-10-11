package com.example.medicaseclinico.ui.screens.notifications

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.medicaseclinico.MediCaseApplication
import com.example.medicaseclinico.domain.model.AlertType
import com.example.medicaseclinico.domain.model.NotificationPreferences
import com.example.medicaseclinico.domain.repository.NotificationPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

@Immutable
sealed interface NotificationSettingsUiState {
    data object Loading : NotificationSettingsUiState
    data class Success(val preferences: NotificationPreferences) : NotificationSettingsUiState
    data object Error : NotificationSettingsUiState
}

class NotificationSettingsViewModel(
    private val repository: NotificationPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<NotificationSettingsUiState> =
        repository.preferences
            .map<NotificationPreferences, NotificationSettingsUiState> {
                NotificationSettingsUiState.Success(it)
            }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(NotificationSettingsUiState.Error)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = NotificationSettingsUiState.Loading
            )

    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    fun setAlertEnabled(type: AlertType, enabled: Boolean) = save { prefs ->
        when (type) {
            AlertType.TEMPERATURE -> prefs.copy(temperatureAlertsEnabled = enabled)
            AlertType.BATTERY -> prefs.copy(batteryAlertsEnabled = enabled)
            AlertType.CONNECTION -> prefs.copy(connectionAlertsEnabled = enabled)
            AlertType.GEOFENCE -> prefs.copy(geofenceAlertsEnabled = enabled)
            AlertType.SECURITY -> prefs.copy(securityAlertsEnabled = enabled)
        }
    }

    fun setTemporarilySilenced(silenced: Boolean) = save { it.copy(temporarilySilenced = silenced) }
    fun setQuietHoursEnabled(enabled: Boolean) = save { it.copy(quietHoursEnabled = enabled) }
    fun setQuietHoursStart(minuteOfDay: Int) = save {
        it.copy(quietHoursStartMinute = minuteOfDay.coerceIn(0, NotificationPreferences.MINUTES_PER_DAY - 1))
    }
    fun setQuietHoursEnd(minuteOfDay: Int) = save {
        it.copy(quietHoursEndMinute = minuteOfDay.coerceIn(0, NotificationPreferences.MINUTES_PER_DAY - 1))
    }

    fun onSaveErrorShown() {
        _saveFailed.value = false
    }

    private fun save(transform: (NotificationPreferences) -> NotificationPreferences) {
        viewModelScope.launch {
            repository.update(transform).onFailure { _saveFailed.value = true }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MediCaseApplication
                NotificationSettingsViewModel(
                    repository = application.container.notificationPreferencesRepository
                )
            }
        }
    }
}
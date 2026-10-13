package com.example.medicaseclinico.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.medicaseclinico.MediCaseApplication
import com.example.medicaseclinico.domain.model.AppearanceMode
import com.example.medicaseclinico.domain.repository.AppearanceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlin.coroutines.cancellation.CancellationException

/**
 * Apariencia elegida por el usuario, para la raíz de la app.
 *
 * Mientras se lee (y si la lectura falla) vale la de fábrica: la app nunca se
 * queda sin tema por no poder leer una preferencia.
 */
class AppearanceViewModel(
    appearanceRepository: AppearanceRepository
) : ViewModel() {

    val mode: StateFlow<AppearanceMode> =
        appearanceRepository.mode
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(AppearanceMode.DEFAULT)
            }
            .stateIn(
                scope = viewModelScope,
                // Sin pausa: el tema se aplica a toda la app, no a una pantalla.
                started = SharingStarted.Eagerly,
                initialValue = AppearanceMode.DEFAULT
            )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY]) as MediCaseApplication
                AppearanceViewModel(
                    appearanceRepository = application.container.appearanceRepository
                )
            }
        }
    }
}

package com.example.medicaseclinico.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Sustituye el hilo principal de Android por uno de pruebas: `viewModelScope`
 * lo necesita y en un test JVM no existe.
 *
 * Es "sin cola" (como `Main.immediate`): el estado de un ViewModel queda listo
 * en cuanto ocurre algo, sin tener que avanzar el reloj en cada test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/**
 * Deja a alguien observando el estado, como haría la pantalla. Sin un
 * observador, un `stateIn(WhileSubscribed)` no arranca y se queda en su valor
 * inicial.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> TestScope.keepObserved(state: StateFlow<T>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { state.collect { } }
}

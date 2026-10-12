package com.example.medicaseclinico.domain.repository

import com.example.medicaseclinico.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de las cuentas de usuario.
 *
 * Mismas reglas que el resto de repositorios: las operaciones `suspend`
 * devuelven `Result` y nunca lanzan. Un fallo llega como
 * [com.example.medicaseclinico.domain.model.AuthException], con el motivo ya
 * clasificado.
 */
interface AuthRepository {

    /**
     * false si esta instalación no tiene servicio de cuentas (se compiló sin
     * `google-services.json`). La app no exige entonces iniciar sesión: sin
     * servicio nadie podría entrar y el estuche quedaría sin monitoreo.
     */
    val isAvailable: Boolean get() = true

    /**
     * Usuario con sesión iniciada, o null si no hay sesión. Emite el valor
     * actual al suscribirse y de nuevo cada vez que se inicia o cierra sesión.
     */
    val currentUser: Flow<AuthUser?>

    suspend fun signIn(email: String, password: String): Result<AuthUser>

    /** Crea la cuenta, le pone [name] como nombre visible y deja la sesión iniciada. */
    suspend fun register(name: String, email: String, password: String): Result<AuthUser>

    /** Envía al correo un enlace para elegir una contraseña nueva. */
    suspend fun sendPasswordReset(email: String): Result<Unit>

    suspend fun signOut(): Result<Unit>
}

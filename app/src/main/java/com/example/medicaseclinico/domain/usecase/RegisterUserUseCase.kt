package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.auth.AuthValidation
import com.example.medicaseclinico.domain.model.AuthUser
import com.example.medicaseclinico.domain.model.UserProfile
import com.example.medicaseclinico.domain.repository.AuthRepository
import com.example.medicaseclinico.domain.repository.UserProfileRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Registra a un usuario: crea su cuenta y guarda su perfil con el teléfono,
 * que el servicio de cuentas no almacena.
 *
 * Son dos pasos y solo el primero decide el resultado. Si la cuenta se crea
 * pero el perfil no se puede guardar, el registro cuenta como hecho: repetirlo
 * fallaría con "correo ya en uso" y el usuario no podría hacer nada. El
 * perfil se puede completar después.
 */
class RegisterUserUseCase(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository
) {
    suspend operator fun invoke(
        name: String,
        email: String,
        phone: String,
        password: String
    ): Result<AuthUser> {
        val cleanName = name.trim()
        val cleanEmail = email.trim()

        return authRepository.register(name = cleanName, email = cleanEmail, password = password)
            .onSuccess { user ->
                // NonCancellable: en cuanto existe la cuenta, la app sale de la
                // pantalla de registro y cancela su trabajo pendiente; el
                // perfil debe guardarse igualmente o el teléfono se perdería.
                // El Result se descarta a propósito (ver el KDoc de la clase).
                withContext(NonCancellable) {
                    userProfileRepository.saveProfile(
                        UserProfile(
                            uid = user.uid,
                            name = cleanName,
                            email = cleanEmail,
                            phone = AuthValidation.normalizePhone(phone)
                        )
                    )
                }
            }
    }
}

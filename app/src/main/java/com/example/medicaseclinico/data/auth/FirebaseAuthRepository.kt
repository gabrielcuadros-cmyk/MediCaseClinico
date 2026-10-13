package com.example.medicaseclinico.data.auth

import android.content.Context
import com.example.medicaseclinico.domain.model.AuthError
import com.example.medicaseclinico.domain.model.AuthException
import com.example.medicaseclinico.domain.model.AuthUser
import com.example.medicaseclinico.domain.repository.AuthRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

/**
 * Cuentas de usuario con Firebase Auth (correo y contraseña).
 *
 * Firebase solo existe si la app se compiló con `google-services.json`. Sin
 * él no hay nada que inicializar: en vez de fallar al arrancar, cada operación
 * devuelve [AuthError.NOT_CONFIGURED] y la app sigue funcionando.
 */
class FirebaseAuthRepository(context: Context) : AuthRepository {

    private val appContext = context.applicationContext

    /** null si Firebase no está configurado en esta compilación. */
    private val auth: FirebaseAuth? by lazy {
        try {
            if (FirebaseApp.getApps(appContext).isEmpty()) null else FirebaseAuth.getInstance()
        } catch (e: IllegalStateException) {
            null
        }
    }

    override val isAvailable: Boolean
        get() = auth != null

    /**
     * Firebase solo avisa cuando se inicia o cierra sesión, no cuando cambia
     * el nombre del usuario. Este contador se incrementa al guardarlo para
     * que [currentUser] vuelva a emitir y el saludo aparezca sin reiniciar.
     */
    private val profileChanges = MutableStateFlow(0)

    override val currentUser: Flow<AuthUser?>
        get() {
            val auth = auth ?: return flowOf(null)
            val sessionChanges = callbackFlow {
                // El listener se dispara al registrarse con el usuario actual,
                // así que quien se suscribe recibe enseguida si hay sesión.
                val listener = FirebaseAuth.AuthStateListener { trySend(Unit) }
                auth.addAuthStateListener(listener)
                awaitClose { auth.removeAuthStateListener(listener) }
            }
            return combine(sessionChanges, profileChanges) { _, _ ->
                auth.currentUser?.toAuthUser()
            }.distinctUntilChanged()
        }

    override suspend fun signIn(email: String, password: String): Result<AuthUser> =
        authCall { auth ->
            val user = auth.signInWithEmailAndPassword(email.trim(), password).await().user
            checkNotNull(user).toAuthUser()
        }

    override suspend fun register(name: String, email: String, password: String): Result<AuthUser> =
        authCall { auth ->
            val user = checkNotNull(
                auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            )
            val displayName = name.trim()
            // La cuenta ya existe: si falla poner el nombre no se le dice al
            // usuario que el registro falló, porque repetirlo daría "correo ya
            // en uso". Se queda sin nombre visible hasta que se pueda guardar.
            try {
                user.updateProfile(
                    UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
                ).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@authCall user.toAuthUser()
            }
            profileChanges.update { it + 1 }
            user.toAuthUser().copy(displayName = displayName)
        }

    /**
     * Un correo sin cuenta cuenta como éxito: según cómo esté configurado el
     * proyecto, Firebase lo acepta en silencio o responde "usuario no
     * encontrado", y decírselo al usuario revelaría qué correos tienen cuenta.
     */
    override suspend fun sendPasswordReset(email: String): Result<Unit> =
        authCall { auth ->
            auth.sendPasswordResetEmail(email.trim()).await()
            Unit
        }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { failure ->
                if ((failure as? AuthException)?.error == AuthError.INVALID_CREDENTIALS) {
                    Result.success(Unit)
                } else {
                    Result.failure(failure)
                }
            }
        )

    override suspend fun signOut(): Result<Unit> =
        authCall { auth -> auth.signOut() }

    private suspend inline fun <T> authCall(block: (FirebaseAuth) -> T): Result<T> {
        val auth = auth ?: return Result.failure(AuthException(AuthError.NOT_CONFIGURED))
        return try {
            Result.success(block(auth))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.toAuthException())
        }
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        displayName = displayName?.takeIf { it.isNotBlank() },
        email = email,
        // Firebase devuelve 0 cuando no conoce la fecha: eso no es un dato.
        lastSignInAt = metadata?.lastSignInTimestamp?.takeIf { it > 0L }
    )
}

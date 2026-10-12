package com.example.medicaseclinico.data.user

import android.content.Context
import com.example.medicaseclinico.domain.model.UserProfile
import com.example.medicaseclinico.domain.repository.UserProfileRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Perfil de usuario en Firestore: un documento por cuenta en `users/{uid}`.
 *
 * Igual que con las cuentas, sin `google-services.json` no hay Firestore que
 * usar: la operación falla de forma controlada en vez de tumbar la app.
 *
 * @param writeTimeoutMs cuánto se espera la confirmación del servidor.
 */
class FirestoreUserProfileRepository(
    context: Context,
    private val writeTimeoutMs: Long = DEFAULT_WRITE_TIMEOUT_MS
) : UserProfileRepository {

    private val appContext = context.applicationContext

    /** null si Firebase no está configurado en esta compilación. */
    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(appContext).isEmpty()) null else FirebaseFirestore.getInstance()
        } catch (e: IllegalStateException) {
            null
        }
    }

    override suspend fun saveProfile(profile: UserProfile): Result<Unit> {
        val firestore = firestore
            ?: return Result.failure(IllegalStateException("Firestore no está configurado"))
        return try {
            val write = firestore.collection(USERS_COLLECTION)
                .document(profile.uid)
                .set(
                    mapOf(
                        FIELD_NAME to profile.name,
                        FIELD_EMAIL to profile.email,
                        FIELD_PHONE to profile.phone
                    ),
                    // merge: no borra campos que otras pantallas añadan al documento.
                    SetOptions.merge()
                )
            // Sin red, Firestore guarda la escritura en el teléfono y la envía
            // al volver la conexión, pero su tarea no termina hasta entonces.
            // Pasado el límite se da por buena: ya está en cola y no se pierde.
            withTimeoutOrNull(writeTimeoutMs) { write.await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val USERS_COLLECTION = "users"
        const val FIELD_NAME = "name"
        const val FIELD_EMAIL = "email"
        const val FIELD_PHONE = "phone"
        const val DEFAULT_WRITE_TIMEOUT_MS = 8_000L
    }
}

package com.example.medicaseclinico.data.user

import android.content.Context
import com.example.medicaseclinico.domain.model.AuthorizedUser
import com.example.medicaseclinico.domain.repository.AuthorizedUsersRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Usuarios autorizados en Firestore: `users/{ownerId}/authorizedUsers/{id}`.
 *
 * Cuelgan del perfil del dueño para que la misma regla de seguridad ("cada
 * usuario solo toca lo suyo") los proteja sin reglas adicionales por documento.
 *
 * Sin `google-services.json` no hay Firestore: la lista sale vacía y las
 * escrituras fallan de forma controlada.
 *
 * @param writeTimeoutMs cuánto se espera la confirmación del servidor.
 */
class FirestoreAuthorizedUsersRepository(
    context: Context,
    private val writeTimeoutMs: Long = DEFAULT_WRITE_TIMEOUT_MS
) : AuthorizedUsersRepository {

    private val appContext = context.applicationContext

    /** null si Firebase no está configurado en esta compilación. */
    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(appContext).isEmpty()) null else FirebaseFirestore.getInstance()
        } catch (e: IllegalStateException) {
            null
        }
    }

    override fun observeUsers(ownerId: String): Flow<List<AuthorizedUser>> {
        val collection = usersOf(ownerId) ?: return flowOf(emptyList())
        return callbackFlow {
            val registration = collection.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Sin permiso o sin servicio: lo decide el ViewModel, no se calla.
                    close(error)
                    return@addSnapshotListener
                }
                val users = snapshot?.documents.orEmpty()
                    .mapNotNull { document ->
                        AuthorizedUserMapper.fromMap(id = document.id, data = document.data.orEmpty())
                    }
                    .sortedBy { it.name.lowercase() }
                trySend(users)
            }
            awaitClose { registration.remove() }
        }
    }

    override suspend fun saveUser(ownerId: String, user: AuthorizedUser): Result<String> =
        firestoreCall(ownerId) { collection ->
            val document = if (user.id.isEmpty()) collection.document() else collection.document(user.id)
            awaitQueued { document.set(AuthorizedUserMapper.toMap(user)).await() }
            document.id
        }

    override suspend fun deleteUser(ownerId: String, userId: String): Result<Unit> =
        firestoreCall(ownerId) { collection ->
            awaitQueued { collection.document(userId).delete().await() }
        }

    private fun usersOf(ownerId: String): CollectionReference? =
        firestore?.takeIf { ownerId.isNotBlank() }
            ?.collection(USERS_COLLECTION)
            ?.document(ownerId)
            ?.collection(AUTHORIZED_USERS_COLLECTION)

    /**
     * Sin red, Firestore guarda la escritura en el teléfono y la envía al
     * volver la conexión, pero su tarea no termina hasta entonces. Pasado el
     * límite se da por buena: ya está en cola y la lista local ya la refleja.
     */
    private suspend fun awaitQueued(write: suspend () -> Unit) {
        withTimeoutOrNull(writeTimeoutMs) { write() }
    }

    private suspend inline fun <T> firestoreCall(
        ownerId: String,
        block: (CollectionReference) -> T
    ): Result<T> {
        val collection = usersOf(ownerId)
            ?: return Result.failure(IllegalStateException("Firestore no está disponible o falta el dueño"))
        return try {
            Result.success(block(collection))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val USERS_COLLECTION = "users"
        const val AUTHORIZED_USERS_COLLECTION = "authorizedUsers"
        const val DEFAULT_WRITE_TIMEOUT_MS = 8_000L
    }
}

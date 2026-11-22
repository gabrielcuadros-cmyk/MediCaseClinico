package com.example.medicaseclinico.data.user

import android.content.Context
import com.example.medicaseclinico.domain.model.AccessStatus
import com.example.medicaseclinico.domain.model.AuthUser
import com.example.medicaseclinico.domain.model.AuthorizedUser
import com.example.medicaseclinico.domain.model.CaseAccess
import com.example.medicaseclinico.domain.model.Invitation
import com.example.medicaseclinico.domain.repository.CaseAccessRepository
import com.example.medicaseclinico.domain.usecase.ResolveCaseAccessUseCase
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Acceso de la cuenta al estuche según Firestore.
 *
 * Escucha en vivo tres documentos: el perfil propio (`users/{uid}`, campo
 * `guestOf`), la invitación de su correo (`invitations/{correo}`) y, si la
 * hay, su registro en la lista del dueño (`users/{dueño}/authorizedUsers/{id}`).
 * La primera vez que una invitada entra en cada sesión se marca su registro
 * como activo con la fecha del acceso, y se anota en su perfil de quién es
 * invitada para reconocer después una revocación.
 *
 * Sin `google-services.json` no hay invitaciones: la cuenta es dueña.
 */
class FirestoreCaseAccessRepository(
    context: Context,
    private val resolve: ResolveCaseAccessUseCase = ResolveCaseAccessUseCase(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val writeTimeoutMs: Long = DEFAULT_WRITE_TIMEOUT_MS
) : CaseAccessRepository {

    private val appContext = context.applicationContext

    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(appContext).isEmpty()) null else FirebaseFirestore.getInstance()
        } catch (e: IllegalStateException) {
            null
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAccess(user: AuthUser): Flow<CaseAccess> {
        val db = firestore ?: return flowOf(CaseAccess.Owner(user.uid))
        val profile = db.collection(USERS_COLLECTION).document(user.uid)
        val email = InvitationMapper.documentIdFor(user.email)

        val guestOf: Flow<String?> = documentFlow(profile)
            .map { it?.getString(InvitationMapper.FIELD_GUEST_OF) }

        val invitationWithUser: Flow<Pair<Invitation?, AuthorizedUser?>> =
            if (email.isEmpty()) {
                flowOf(null to null)
            } else {
                documentFlow(db.collection(InvitationMapper.COLLECTION).document(email))
                    .map { snapshot -> snapshot?.data?.let(InvitationMapper::fromMap) }
                    .distinctUntilChanged()
                    .flatMapLatest { invitation ->
                        if (invitation == null) {
                            flowOf(null to null)
                        } else {
                            // Al revocar, el registro desaparece y las reglas ya no dejan
                            // leerlo: "sin permiso" aquí significa "ya no existe".
                            documentFlow(authorizedUserOf(db, invitation), deniedMeansMissing = true)
                                .map { snapshot ->
                                    invitation to snapshot?.let {
                                        AuthorizedUserMapper.fromMap(id = it.id, data = it.data.orEmpty())
                                    }
                                }
                        }
                    }
            }

        var acceptedFor: String? = null
        return combine(guestOf, invitationWithUser) { guestOfOwner, (invitation, authorizedUser) ->
            val access = resolve(
                uid = user.uid,
                guestOf = guestOfOwner,
                invitation = invitation,
                authorizedUser = authorizedUser
            )
            Triple(access, guestOfOwner, authorizedUser)
        }
            .onEach { (access, guestOfOwner, authorizedUser) ->
                if (access is CaseAccess.Guest && acceptedFor != access.userId) {
                    acceptedFor = access.userId
                    recordAccess(db, profile, access, guestOfOwner, authorizedUser)
                }
            }
            .map { (access, _, _) -> access }
            .distinctUntilChanged()
    }

    /**
     * Marca el registro como activo con la hora del acceso y anota en el perfil
     * de quién es invitada. Si falla (sin red, reglas sin publicar) el acceso
     * sigue funcionando: solo se pierde el dato de "último acceso".
     */
    private suspend fun recordAccess(
        db: FirebaseFirestore,
        profile: DocumentReference,
        access: CaseAccess.Guest,
        guestOfOwner: String?,
        authorizedUser: AuthorizedUser?
    ) {
        try {
            withTimeoutOrNull(writeTimeoutMs) {
                if (guestOfOwner != access.ownerId) {
                    profile.set(
                        mapOf(InvitationMapper.FIELD_GUEST_OF to access.ownerId),
                        SetOptions.merge()
                    ).await()
                }
                if (authorizedUser != null) {
                    authorizedUserOf(db, Invitation(access.ownerId, access.userId)).update(
                        mapOf(
                            AuthorizedUserMapper.FIELD_STATUS to AccessStatus.ACTIVE.name,
                            AuthorizedUserMapper.FIELD_LAST_ACCESS_AT to clock()
                        )
                    ).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Ver KDoc: el acceso no depende de esta escritura.
        }
    }

    private fun authorizedUserOf(db: FirebaseFirestore, invitation: Invitation): DocumentReference =
        db.collection(USERS_COLLECTION)
            .document(invitation.ownerId)
            .collection(AUTHORIZED_USERS_COLLECTION)
            .document(invitation.userId)

    /** El documento en vivo; null si no existe. Los errores cierran el flujo (no se callan). */
    private fun documentFlow(
        document: DocumentReference,
        deniedMeansMissing: Boolean = false
    ): Flow<DocumentSnapshot?> = callbackFlow {
        val registration = document.addSnapshotListener { snapshot, error ->
            when {
                error != null && deniedMeansMissing &&
                    error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> trySend(null)
                error != null -> close(error)
                else -> trySend(snapshot?.takeIf { it.exists() })
            }
        }
        awaitClose { registration.remove() }
    }

    private companion object {
        const val USERS_COLLECTION = "users"
        const val AUTHORIZED_USERS_COLLECTION = "authorizedUsers"
        const val DEFAULT_WRITE_TIMEOUT_MS = 8_000L
    }
}

package com.example.medicaseclinico.domain.repository

import com.example.medicaseclinico.domain.model.AuthorizedUser
import kotlinx.coroutines.flow.Flow

/**
 * Contrato de las personas a las que un dueño ha dado acceso a su estuche.
 *
 * Todo cuelga del dueño ([ownerId] es el uid de su cuenta): cada dueño solo
 * ve y cambia su propia lista.
 *
 * Mismas reglas que el resto de repositorios: el `Flow` emite una lista vacía
 * cuando no hay nadie y no silencia errores; las operaciones `suspend`
 * devuelven `Result`.
 */
interface AuthorizedUsersRepository {

    /** Usuarios autorizados por [ownerId], por orden alfabético de nombre. */
    fun observeUsers(ownerId: String): Flow<List<AuthorizedUser>>

    /**
     * Crea el usuario si su id está vacío, o lo actualiza si ya existe.
     * Success contiene el id del registro guardado.
     */
    suspend fun saveUser(ownerId: String, user: AuthorizedUser): Result<String>

    /** Quita el acceso a un usuario. No falla si ya no existía. */
    suspend fun deleteUser(ownerId: String, userId: String): Result<Unit>
}

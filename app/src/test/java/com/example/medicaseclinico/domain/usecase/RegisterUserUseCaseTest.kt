package com.example.medicaseclinico.domain.usecase

import com.example.medicaseclinico.domain.model.AuthError
import com.example.medicaseclinico.domain.model.AuthException
import com.example.medicaseclinico.domain.model.AuthUser
import com.example.medicaseclinico.domain.model.UserProfile
import com.example.medicaseclinico.domain.repository.AuthRepository
import com.example.medicaseclinico.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RegisterUserUseCaseTest {

    private class FakeAuthRepository(private val failWith: AuthError? = null) : AuthRepository {
        val registered = mutableListOf<Triple<String, String, String>>()

        override val currentUser: Flow<AuthUser?> = flowOf(null)
        override suspend fun signIn(email: String, password: String): Result<AuthUser> =
            Result.failure(AuthException(AuthError.UNKNOWN))
        override suspend fun sendPasswordReset(email: String): Result<Unit> = Result.success(Unit)
        override suspend fun signOut(): Result<Unit> = Result.success(Unit)

        override suspend fun register(name: String, email: String, password: String): Result<AuthUser> {
            if (failWith != null) return Result.failure(AuthException(failWith))
            registered += Triple(name, email, password)
            return Result.success(AuthUser(uid = "uid-1", displayName = name, email = email))
        }
    }

    private class FakeUserProfileRepository(private val failing: Boolean = false) : UserProfileRepository {
        val saved = mutableListOf<UserProfile>()

        override suspend fun saveProfile(profile: UserProfile): Result<Unit> {
            if (failing) return Result.failure(IOException("permiso denegado"))
            saved += profile
            return Result.success(Unit)
        }
    }

    @Test
    fun `crea la cuenta y guarda el perfil con el telefono normalizado`() = runTest {
        val auth = FakeAuthRepository()
        val profiles = FakeUserProfileRepository()

        val result = RegisterUserUseCase(auth, profiles)(
            name = "  Ana Pérez ",
            email = " ana@ejemplo.com ",
            phone = "+51 987 654 321",
            password = "secreto123"
        )

        assertEquals("uid-1", result.getOrThrow().uid)
        assertEquals(listOf(Triple("Ana Pérez", "ana@ejemplo.com", "secreto123")), auth.registered)
        assertEquals(
            listOf(UserProfile(uid = "uid-1", name = "Ana Pérez", email = "ana@ejemplo.com", phone = "+51987654321")),
            profiles.saved
        )
    }

    @Test
    fun `si la cuenta no se crea no se guarda ningun perfil`() = runTest {
        val profiles = FakeUserProfileRepository()

        val result = RegisterUserUseCase(FakeAuthRepository(failWith = AuthError.EMAIL_ALREADY_IN_USE), profiles)(
            name = "Ana Pérez",
            email = "ana@ejemplo.com",
            phone = "987654321",
            password = "secreto123"
        )

        assertEquals(AuthError.EMAIL_ALREADY_IN_USE, (result.exceptionOrNull() as AuthException).error)
        assertTrue(profiles.saved.isEmpty())
    }

    @Test
    fun `si el perfil no se puede guardar el registro sigue siendo valido`() = runTest {
        val result = RegisterUserUseCase(FakeAuthRepository(), FakeUserProfileRepository(failing = true))(
            name = "Ana Pérez",
            email = "ana@ejemplo.com",
            phone = "987654321",
            password = "secreto123"
        )

        assertEquals("uid-1", result.getOrThrow().uid)
    }
}

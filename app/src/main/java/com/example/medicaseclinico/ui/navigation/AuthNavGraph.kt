package com.example.medicaseclinico.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.medicaseclinico.ui.screens.auth.login.LoginRoute
import com.example.medicaseclinico.ui.screens.auth.recovery.PasswordRecoveryRoute
import com.example.medicaseclinico.ui.screens.auth.register.RegisterRoute

/** Rutas de las pantallas de cuenta, las únicas visibles sin sesión. */
object AuthRoutes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val PASSWORD_RECOVERY = "password_recovery"
}

/**
 * Grafo de navegación de las pantallas de cuenta: Iniciar sesión, Crear
 * cuenta y Recuperar contraseña.
 *
 * Ninguna navega "hacia la app" al terminar: cuando la sesión queda iniciada,
 * la compuerta de `MediCaseApp` cambia de grafo por sí sola. Así hay un único
 * sitio que decide si se puede entrar.
 */
@Composable
fun AuthNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = AuthRoutes.LOGIN,
        modifier = modifier
    ) {
        composable(AuthRoutes.LOGIN) {
            LoginRoute(
                onSignedIn = { },
                onForgotPassword = { navController.navigate(AuthRoutes.PASSWORD_RECOVERY) },
                onCreateAccount = { navController.navigate(AuthRoutes.REGISTER) }
            )
        }
        composable(AuthRoutes.REGISTER) {
            RegisterRoute(
                onRegistered = { },
                onSignIn = { navController.popBackStack() }
            )
        }
        composable(AuthRoutes.PASSWORD_RECOVERY) {
            PasswordRecoveryRoute(onBack = { navController.popBackStack() })
        }
    }
}

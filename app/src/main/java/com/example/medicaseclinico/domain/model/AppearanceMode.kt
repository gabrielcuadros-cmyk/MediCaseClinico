package com.example.medicaseclinico.domain.model

/**
 * Apariencia que el usuario eligió para la app.
 */
enum class AppearanceMode {
    /** La del teléfono: clara u oscura según sus ajustes. */
    SYSTEM,
    LIGHT,
    DARK;

    /** true si la app debe pintarse oscura, dado cómo está el teléfono. */
    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /** Configuración de fábrica: seguir al teléfono, como hacía la app antes de poder elegir. */
        val DEFAULT = SYSTEM

        /**
         * Lee el valor guardado con tolerancia: si falta o no se reconoce (una
         * versión futura pudo guardar otro), se vuelve a la de fábrica.
         */
        fun fromStored(name: String?): AppearanceMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

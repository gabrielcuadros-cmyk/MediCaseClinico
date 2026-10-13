package com.example.medicaseclinico.ui.components

import androidx.annotation.StringRes
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.model.UserRole

/**
 * Nombre visible de un rol. En un único lugar para que Seguridad y las
 * pantallas de usuarios lo digan igual.
 */
@StringRes
fun UserRole.labelRes(): Int = when (this) {
    UserRole.PRINCIPAL -> R.string.user_role_principal
    UserRole.CAREGIVER -> R.string.user_role_caregiver
    UserRole.SECONDARY_ADMIN -> R.string.user_role_secondary_admin
}

package com.example.medicaseclinico.domain.model

/**
 * Datos del usuario que el servicio de cuentas no guarda (el teléfono) junto
 * con los que sí, para tenerlos en un solo documento.
 *
 * @param uid identificador de la cuenta; es también el id del documento, y lo
 * que permite que cada usuario solo pueda leer y escribir el suyo.
 */
data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val phone: String
)

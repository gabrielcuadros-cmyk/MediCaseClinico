package com.example.medicaseclinico.ui.screens.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import java.util.Locale

private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

// Locale.US: en español el separador decimal sería una coma y rompería la URI.
private fun uriCoordinates(latitude: Double, longitude: Double): String =
    String.format(Locale.US, "%.6f,%.6f", latitude, longitude)

/**
 * Enlace web a una posición, que abre cualquier teléfono o computadora aunque
 * no tenga la app: sirve para enviarla a otra persona.
 */
fun mapsWebUrl(latitude: Double, longitude: Double): String =
    "https://www.google.com/maps/search/?api=1&query=${uriCoordinates(latitude, longitude)}"

/**
 * Abre la posición del estuche en Google Maps con un `Intent`, sin API key.
 *
 * Si Google Maps no está instalado, se intenta con cualquier otra app de
 * mapas y, por último, con el navegador: lo que el usuario quiere es llegar
 * al estuche, no una app en concreto.
 *
 * @return false si ninguna aplicación pudo abrirla; nunca lanza excepción.
 */
fun openInGoogleMaps(context: Context, latitude: Double, longitude: Double): Boolean {
    val coordinates = uriCoordinates(latitude, longitude)
    val geoUri = "geo:$coordinates?q=$coordinates".toUri()
    val webUri = mapsWebUrl(latitude, longitude).toUri()

    val attempts = listOf(
        Intent(Intent.ACTION_VIEW, geoUri).setPackage(GOOGLE_MAPS_PACKAGE),
        Intent(Intent.ACTION_VIEW, geoUri),
        Intent(Intent.ACTION_VIEW, webUri)
    )
    return attempts.any { intent ->
        try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}

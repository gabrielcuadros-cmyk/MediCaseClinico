package com.example.medicaseclinico.ui.screens.auth.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.medicaseclinico.R
import com.example.medicaseclinico.ui.theme.MediCaseTheme

/**
 * Logotipo de MediCase (maletín con la gota de insulina, la marca y el lema)
 * sobre la tarjeta clara del diseño.
 *
 * La imagen ya incluye la marca y el lema como dibujo, así que se describe
 * entera para los lectores de pantalla.
 *
 * La tarjeta es siempre clara: el logotipo tiene letras azul oscuro que no se
 * leerían sobre el fondo del tema oscuro.
 *
 * @param logoWidth ancho de la imagen; el alto sale de su proporción.
 */
@Composable
fun MediCaseLogo(
    modifier: Modifier = Modifier,
    logoWidth: Dp = 200.dp
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = colorScheme.surface.luminance() < 0.5f

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (isDarkTheme) colorScheme.inverseSurface else colorScheme.surface
    ) {
        Image(
            painter = painterResource(R.drawable.logo_medicase),
            contentDescription = stringResource(R.string.auth_logo_description),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .padding(20.dp)
                .width(logoWidth)
        )
    }
}

@Preview(showBackground = true, name = "Logotipo")
@Composable
private fun MediCaseLogoPreview() {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            MediCaseLogo(modifier = Modifier.padding(24.dp))
        }
    }
}

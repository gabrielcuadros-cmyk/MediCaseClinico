package com.example.medicaseclinico.ui.screens.auth.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.medicaseclinico.R
import com.example.medicaseclinico.ui.theme.MediCaseTheme
import com.example.medicaseclinico.ui.theme.PureWhite

/**
 * Logotipo de MediCase (maletín con la gota de insulina, la marca y el lema)
 * sobre la tarjeta clara del diseño.
 *
 * La imagen ya incluye la marca y el lema como dibujo, así que se describe
 * entera para los lectores de pantalla.
 *
 * La tarjeta es blanca pura en los dos temas: el logotipo tiene letras azul
 * oscuro que no se leerían sobre el fondo del tema oscuro, y sobre cualquier
 * otro tono claro sus colores de marca se ven teñidos.
 *
 * @param logoWidth ancho de la imagen; el alto sale de su proporción.
 */
@Composable
fun MediCaseLogo(
    modifier: Modifier = Modifier,
    logoWidth: Dp = 200.dp
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = PureWhite
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

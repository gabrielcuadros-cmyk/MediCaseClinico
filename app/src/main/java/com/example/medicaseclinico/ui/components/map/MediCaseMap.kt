package com.example.medicaseclinico.ui.components.map

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.geo.GeoPoint
import com.example.medicaseclinico.ui.theme.MediCaseTheme
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * Estilos de OpenFreeMap: mapa vectorial con datos de OpenStreetMap, público,
 * sin API key ni cuenta. Liberty para el tema claro y Dark para el oscuro: un
 * mapa claro en una pantalla oscura deslumbra y rompe el resto del diseño.
 */
private const val MAP_STYLE_LIGHT_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val MAP_STYLE_DARK_URL = "https://tiles.openfreemap.org/styles/dark"

/** Aire alrededor del círculo del punto elegido cuando el mapa ajusta el zoom para que quepa. */
private val PickerFitPadding = 32.dp

/**
 * Cuánto se espera al estilo del mapa antes de darlo por no disponible. Sin
 * internet el SDK no informa de ningún error: se queda reintentando en
 * silencio, y sin este límite el usuario vería un recuadro vacío para siempre.
 */
private const val STYLE_LOAD_TIMEOUT_MS = 8_000L

/**
 * Mapa base de la app: el único sitio donde se crea un `MapView`.
 *
 * MapLibre no tiene API de Compose compatible con el Kotlin del proyecto, así
 * que su `MapView` se envuelve aquí y se le conecta el ciclo de vida a mano.
 * Las pantallas solo ven un Composable.
 *
 * La atribución va siempre visible sobre el mapa porque la licencia de
 * OpenStreetMap lo exige.
 *
 * Si el mapa no se puede mostrar (sin internet la primera vez, o un teléfono
 * donde la librería nativa no carga) lo dice él mismo, con un botón para
 * reintentar: así todas las pantallas lo explican igual y ninguna se queda
 * con un hueco gris. Lo que cada pantalla muestra fuera del mapa (coordenadas,
 * zonas) sigue funcionando.
 *
 * @param marker posición del estuche, o null si no hay ninguna que mostrar.
 * La cámara la sigue hasta que el usuario mueve el mapa.
 * @param circles zonas de seguridad, con su radio en metros reales.
 * @param recenterRequests contador de veces que el usuario pidió centrar el
 * mapa en el estuche; cada incremento vuelve a centrarlo y a seguirlo.
 * @param centerPicker si no es null, el mapa pasa al modo "elegir un punto":
 * deja de seguir al estuche, dibuja un círculo destacado alrededor de su
 * centro y atenúa [circles] para que no se confundan con él.
 * @param onCenterChanged punto elegido en ese modo, cada vez que el mapa se detiene.
 * @param overlay lo que la pantalla dibuja encima del mapa y solo tiene
 * sentido con el mapa a la vista (p. ej. el pin del centro): queda tapado
 * cuando el mapa no está disponible.
 */
@Composable
fun MediCaseMap(
    modifier: Modifier = Modifier,
    marker: GeoPoint? = null,
    circles: List<MapCircle> = emptyList(),
    recenterRequests: Int = 0,
    centerPicker: MapCenterPicker? = null,
    onCenterChanged: (GeoPoint) -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    // Estado solo visual: si el mapa cargó o falló, y cuántas veces se reintentó.
    var loaded by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    Box(modifier = modifier) {
        if (LocalInspectionMode.current) {
            // El IDE no puede cargar la librería nativa del mapa.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        } else {
            // Reintentar crea un MapView nuevo: el anterior pudo quedar a medias.
            key(attempt) {
                MapLibreMapView(
                    marker = marker,
                    circles = circles,
                    recenterRequests = recenterRequests,
                    centerPicker = centerPicker,
                    onCenterChanged = onCenterChanged,
                    onLoadFailed = { loadFailed = true },
                    // El SDK reintenta solo cuando vuelve la red: si lo
                    // consigue, el aviso se quita sin que el usuario haga nada.
                    onLoaded = {
                        loaded = true
                        loadFailed = false
                    }
                )
            }
            if (!loaded && !loadFailed) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp),
                    strokeWidth = 3.dp
                )
            }
        }
        MapAttribution(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
        )
        overlay()
        if (loadFailed) {
            MapUnavailablePanel(
                onRetry = {
                    loaded = false
                    loadFailed = false
                    attempt++
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Sustituye al mapa cuando no se puede mostrar. Tapa el mapa entero (y
 * bloquea sus gestos) para no dejar a la vista una cuadrícula vacía que
 * parezca un mapa roto.
 */
@Composable
private fun MapUnavailablePanel(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.map_unavailable_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.map_unavailable_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = stringResource(R.string.action_retry), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MapLibreMapView(
    marker: GeoPoint?,
    circles: List<MapCircle>,
    recenterRequests: Int,
    centerPicker: MapCenterPicker?,
    onCenterChanged: (GeoPoint) -> Unit,
    onLoadFailed: () -> Unit,
    onLoaded: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnLoadFailed by rememberUpdatedState(onLoadFailed)
    val currentOnLoaded by rememberUpdatedState(onLoaded)
    val currentOnCenterChanged by rememberUpdatedState(onCenterChanged)
    val fitPaddingPx = with(LocalDensity.current) { PickerFitPadding.roundToPx() }
    val mapDescription = stringResource(R.string.map_content_description)

    // Se pregunta al tema de la app y no al del sistema: el usuario puede
    // haber elegido una apariencia distinta de la del teléfono.
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val styleUrl = if (isDarkTheme) MAP_STYLE_DARK_URL else MAP_STYLE_LIGHT_URL

    // Como en el diseño: pin rojo de alerta con insignia azul, y zonas en azul primario.
    val pinColors = MapPinColors(
        pin = MediCaseTheme.statusColors.critical.toArgb(),
        badge = MaterialTheme.colorScheme.primary.toArgb(),
        badgeBorder = MaterialTheme.colorScheme.surface.toArgb(),
        glyph = MaterialTheme.colorScheme.onPrimary.toArgb()
    )
    val circleColor = MaterialTheme.colorScheme.primary.toArgb()
    val mutedCircleColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    // Un MapView destruido no se puede reutilizar: si cambia el dueño del
    // ciclo de vida, se crea un mapa nuevo en vez de reconectar el anterior.
    // También al cambiar de tema: el estilo y los colores del pin son otros.
    val handle = remember(context, lifecycleOwner, isDarkTheme) {
        createMapOrNull(context, pinColors, circleColor, mutedCircleColor, fitPaddingPx)
    }
    if (handle == null) {
        LaunchedEffect(Unit) { currentOnLoadFailed() }
        return
    }
    val mapView = handle.mapView
    val content = handle.content

    SideEffect { content.show(marker, circles, centerPicker) }
    LaunchedEffect(handle) {
        delay(STYLE_LOAD_TIMEOUT_MS)
        if (!content.isAttached) currentOnLoadFailed()
    }
    LaunchedEffect(content, recenterRequests) {
        if (recenterRequests > 0) content.recenter()
    }

    DisposableEffect(handle) {
        // Solo cuenta como fallo si el estilo aún no cargó. Con el mapa ya en
        // pantalla, un error posterior (una tesela que no baja) no lo inutiliza.
        val failListener = MapView.OnDidFailLoadingMapListener {
            if (!content.isAttached) currentOnLoadFailed()
        }
        mapView.addOnDidFailLoadingMapListener(failListener)
        content.onCenterChanged = { currentOnCenterChanged(it) }
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                content.attach(map, style)
                currentOnLoaded()
            }
        }

        val mapLifecycle = MapViewLifecycle(mapView)
        // Al registrarse, el observador recibe los eventos que faltan hasta el
        // estado actual, así que el mapa arranca aunque la pantalla ya esté visible.
        lifecycleOwner.lifecycle.addObserver(mapLifecycle)
        context.registerComponentCallbacks(mapLifecycle)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(mapLifecycle)
            context.unregisterComponentCallbacks(mapLifecycle)
            mapView.removeOnDidFailLoadingMapListener(failListener)
            content.detach()
            // Salir de la pantalla no emite ON_STOP ni ON_DESTROY: hay que
            // cerrar el mapa aquí o se queda vivo con su hilo de render.
            mapLifecycle.destroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = mapDescription }
    )
}

/** El `MapView` y quien dibuja sobre él: nacen y mueren juntos. */
private class MapHandle(val mapView: MapView, val content: MapContentRenderer)

/**
 * Crea el `MapView` ya inicializado, o null si la librería nativa no carga en
 * este dispositivo: un mapa que no abre no debe tumbar la app.
 */
private fun createMapOrNull(
    context: Context,
    pinColors: MapPinColors,
    @ColorInt circleColor: Int,
    @ColorInt mutedCircleColor: Int,
    fitPaddingPx: Int
): MapHandle? =
    try {
        MapLibre.getInstance(context.applicationContext)
        MapHandle(
            mapView = MapView(context).apply { onCreate(null) },
            content = MapContentRenderer(
                pinBitmap = MapContentRenderer.createPinBitmap(context, pinColors),
                circleColor = circleColor,
                mutedCircleColor = mutedCircleColor,
                fitPaddingPx = fitPaddingPx
            )
        )
    } catch (e: Exception) {
        null
    } catch (e: LinkageError) {
        null
    }

/**
 * Traduce el ciclo de vida de la pantalla a las llamadas que el `MapView`
 * exige, siempre en orden y sin repetirlas.
 *
 * Lleva la cuenta del estado porque hay dos caminos para cerrar el mapa (la
 * pantalla se destruye, o el Composable sale de la composición estando
 * visible) y en ambos deben llegar onPause → onStop → onDestroy una sola vez:
 * saltarse uno o repetirlo provoca fugas o crashes en la librería nativa.
 */
private class MapViewLifecycle(private val mapView: MapView) :
    LifecycleEventObserver,
    ComponentCallbacks2 {

    private var started = false
    private var resumed = false
    private var destroyed = false

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> start()
            Lifecycle.Event.ON_RESUME -> resume()
            Lifecycle.Event.ON_PAUSE -> pause()
            Lifecycle.Event.ON_STOP -> stop()
            Lifecycle.Event.ON_DESTROY -> destroy()
            // onCreate ya se llamó al crear el MapView.
            else -> Unit
        }
    }

    private fun start() {
        if (destroyed || started) return
        mapView.onStart()
        started = true
    }

    private fun resume() {
        if (destroyed || resumed) return
        start()
        mapView.onResume()
        resumed = true
    }

    private fun pause() {
        if (!resumed) return
        mapView.onPause()
        resumed = false
    }

    private fun stop() {
        if (!started) return
        pause()
        mapView.onStop()
        started = false
    }

    fun destroy() {
        if (destroyed) return
        stop()
        mapView.onDestroy()
        destroyed = true
    }

    // Android lo marca obsoleto, pero sigue llamándolo y la interfaz obliga a implementarlo.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onLowMemory() {
        if (!destroyed) mapView.onLowMemory()
    }

    override fun onTrimMemory(level: Int) = Unit

    override fun onConfigurationChanged(newConfig: Configuration) = Unit
}

/** Crédito de los datos del mapa, sobre su propio fondo para que se lea encima de cualquier zona. */
@Composable
private fun MapAttribution(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    ) {
        Text(
            text = stringResource(R.string.map_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Preview(showBackground = true, name = "Mapa base")
@Composable
private fun MediCaseMapPreview() {
    MediCaseTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            MediCaseMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )
        }
    }
}

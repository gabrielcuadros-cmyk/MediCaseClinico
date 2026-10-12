package com.example.medicaseclinico.ui.components.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.ColorInt
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.example.medicaseclinico.R
import com.example.medicaseclinico.domain.geo.GeoPoint
import com.example.medicaseclinico.domain.geo.circleOutline
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

/** Círculo en metros reales sobre el mapa (una zona de seguridad). */
@Immutable
data class MapCircle(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double
)

/**
 * Modo "elegir un punto": el centro del mapa es el punto elegido y a su
 * alrededor se dibuja un círculo de [radiusMeters]. El usuario mueve el mapa
 * por debajo; el círculo se queda en el centro.
 *
 * @param start dónde abrir el mapa, o null si no hay ningún lugar conocido
 * desde el que empezar.
 */
@Immutable
data class MapCenterPicker(
    val radiusMeters: Double,
    val start: GeoPoint?
)

/** Colores del marcador del estuche, ya resueltos desde el tema. */
internal class MapPinColors(
    @param:ColorInt val pin: Int,
    @param:ColorInt val badge: Int,
    @param:ColorInt val badgeBorder: Int,
    @param:ColorInt val glyph: Int
)

/**
 * Dibuja el marcador del estuche y los círculos sobre un mapa ya cargado, y
 * mueve la cámara.
 *
 * Guarda lo último que se le pidió mostrar porque el estilo del mapa se
 * descarga de internet y puede estar listo después de que lleguen los datos:
 * en cuanto se engancha al mapa, pinta lo pendiente.
 *
 * La cámara sigue al estuche hasta que el usuario mueve el mapa con el dedo;
 * desde ahí manda el usuario, o cada posición nueva (una por segundo) le
 * arrancaría el mapa de las manos.
 */
internal class MapContentRenderer(
    private val pinBitmap: Bitmap,
    @param:ColorInt private val circleColor: Int,
    @param:ColorInt private val mutedCircleColor: Int,
    private val fitPaddingPx: Int
) {
    private var map: MapLibreMap? = null
    private var markerSource: GeoJsonSource? = null
    private var circlesSource: GeoJsonSource? = null
    private var pickerSource: GeoJsonSource? = null
    private var circlesFillLayer: FillLayer? = null
    private var circlesLineLayer: LineLayer? = null

    private var marker: GeoPoint? = null
    private var circles: List<MapCircle> = emptyList()
    private var picker: MapCenterPicker? = null

    /** A quién avisar del punto elegido en el modo "elegir un punto". */
    var onCenterChanged: (GeoPoint) -> Unit = {}

    private var detached = false
    private var followMarker = true
    private var cameraPlaced = false

    // Modo "elegir un punto": el centro solo cuenta cuando el mapa se abrió en
    // un lugar conocido o el usuario lo movió; la vista inicial del mundo no
    // es una elección.
    private var centerChosen = false
    private var fittedRadiusMeters: Double? = null

    /** El estilo terminó de cargar: se añaden las capas y se pinta lo pendiente. */
    fun attach(map: MapLibreMap, style: Style) {
        if (detached) return

        // La atribución la muestra MediCaseMap como texto siempre visible, así
        // que sobra el botón "i" del SDK. Sin giro ni inclinación el norte
        // queda arriba y no hace falta brújula.
        map.uiSettings.apply {
            isLogoEnabled = false
            isAttributionEnabled = false
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
        }

        val circlesSource = GeoJsonSource(CIRCLES_SOURCE_ID)
        style.addSource(circlesSource)
        // El color y el trazo de estas dos capas los pone styleCircles().
        val circlesFillLayer = FillLayer(CIRCLES_FILL_LAYER_ID, CIRCLES_SOURCE_ID)
        style.addLayer(circlesFillLayer)
        val circlesLineLayer = LineLayer(CIRCLES_LINE_LAYER_ID, CIRCLES_SOURCE_ID)
            .withProperties(PropertyFactory.lineWidth(CIRCLE_LINE_WIDTH))
        style.addLayer(circlesLineLayer)

        val pickerSource = GeoJsonSource(PICKER_SOURCE_ID)
        style.addSource(pickerSource)
        style.addLayer(
            FillLayer(PICKER_FILL_LAYER_ID, PICKER_SOURCE_ID).withProperties(
                PropertyFactory.fillColor(circleColor),
                PropertyFactory.fillOpacity(PICKER_FILL_OPACITY)
            )
        )
        style.addLayer(
            LineLayer(PICKER_LINE_LAYER_ID, PICKER_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(circleColor),
                PropertyFactory.lineWidth(CIRCLE_LINE_WIDTH)
            )
        )

        // El marcador se añade al final para que quede encima de los círculos.
        style.addImage(PIN_IMAGE_ID, pinBitmap)
        val markerSource = GeoJsonSource(MARKER_SOURCE_ID)
        style.addSource(markerSource)
        style.addLayer(
            SymbolLayer(MARKER_LAYER_ID, MARKER_SOURCE_ID).withProperties(
                PropertyFactory.iconImage(PIN_IMAGE_ID),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
            )
        )

        map.addOnCameraMoveStartedListener { reason ->
            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                followMarker = false
                centerChosen = true
            }
        }
        // El círculo del punto elegido acompaña al centro mientras el mapa se
        // mueve; el aviso se da al soltar, no en cada fotograma.
        map.addOnCameraMoveListener { renderPickerCircle() }
        map.addOnCameraIdleListener { reportChosenCenter() }

        this.map = map
        this.circlesSource = circlesSource
        this.circlesFillLayer = circlesFillLayer
        this.circlesLineLayer = circlesLineLayer
        this.pickerSource = pickerSource
        this.markerSource = markerSource
        render()
    }

    /** Lo que debe verse ahora. Se puede llamar antes de que el mapa esté listo. */
    fun show(marker: GeoPoint?, circles: List<MapCircle>, picker: MapCenterPicker?) {
        if (marker == this.marker && circles == this.circles && picker == this.picker) return
        this.marker = marker
        this.circles = circles
        this.picker = picker
        render()
    }

    /** El usuario pidió volver al estuche: se centra en él y se le vuelve a seguir. */
    fun recenter() {
        followMarker = true
        val map = map ?: return
        val marker = marker ?: return
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(marker.latitude, marker.longitude), INITIAL_ZOOM)
        )
    }

    /** El mapa se va a destruir: tocar sus fuentes después provocaría un crash nativo. */
    fun detach() {
        detached = true
        map = null
        markerSource = null
        circlesSource = null
        circlesFillLayer = null
        circlesLineLayer = null
        pickerSource = null
    }

    private fun render() {
        val map = map ?: return

        styleCircles()
        circlesSource?.setGeoJson(
            FeatureCollection.fromFeatures(circles.map { it.toFeature() })
        )

        val marker = marker
        markerSource?.setGeoJson(
            FeatureCollection.fromFeatures(
                listOfNotNull(
                    marker?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) }
                )
            )
        )

        val picker = picker
        if (picker != null) {
            placePickerCamera(map, picker)
            renderPickerCircle()
            return
        }
        pickerSource?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))

        if (marker == null) return
        val target = LatLng(marker.latitude, marker.longitude)
        when {
            // La primera vez se salta directo: animar desde la vista del mundo marea.
            !cameraPlaced -> {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(target, INITIAL_ZOOM))
                cameraPlaced = true
            }
            followMarker -> map.animateCamera(CameraUpdateFactory.newLatLng(target))
        }
    }

    /**
     * Mientras se elige un punto, las zonas ya guardadas pasan a segundo
     * plano (gris tenue y borde punteado): lo que debe destacar es el círculo
     * de la zona nueva, y dos círculos azules superpuestos no se distinguen.
     */
    private fun styleCircles() {
        val muted = picker != null
        val color = if (muted) mutedCircleColor else circleColor
        circlesFillLayer?.setProperties(
            PropertyFactory.fillColor(color),
            PropertyFactory.fillOpacity(if (muted) MUTED_CIRCLE_FILL_OPACITY else CIRCLE_FILL_OPACITY)
        )
        circlesLineLayer?.setProperties(
            PropertyFactory.lineColor(color),
            PropertyFactory.lineDasharray(if (muted) DASHED_LINE else SOLID_LINE)
        )
    }

    /**
     * En el modo "elegir un punto" la cámara no sigue a nadie: se abre una vez
     * en el punto de partida y después solo cambia el zoom, para que el
     * círculo quepa entero cuando el usuario cambia el radio.
     */
    private fun placePickerCamera(map: MapLibreMap, picker: MapCenterPicker) {
        val start = picker.start
        if (!cameraPlaced && !centerChosen && start != null) {
            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(start.latitude, start.longitude), INITIAL_ZOOM)
            )
            cameraPlaced = true
            centerChosen = true
            fitPickerCircle(map, picker, animate = false)
        } else if (centerChosen && picker.radiusMeters != fittedRadiusMeters) {
            fitPickerCircle(map, picker, animate = true)
        }
    }

    private fun fitPickerCircle(map: MapLibreMap, picker: MapCenterPicker, animate: Boolean) {
        val center = map.cameraPosition.target ?: return
        fittedRadiusMeters = picker.radiusMeters
        val outline = circleOutline(center.latitude, center.longitude, picker.radiusMeters)
            .map { LatLng(it.latitude, it.longitude) }
        try {
            val update = CameraUpdateFactory.newLatLngBounds(
                LatLngBounds.Builder().includes(outline).build(),
                fitPaddingPx
            )
            if (animate) map.animateCamera(update) else map.moveCamera(update)
        } catch (e: RuntimeException) {
            // Mapa aún sin tamaño o límites no válidos: se queda el zoom que había.
        }
    }

    private fun renderPickerCircle() {
        val picker = picker ?: return
        val center = map?.cameraPosition?.target ?: return
        pickerSource?.setGeoJson(
            MapCircle(center.latitude, center.longitude, picker.radiusMeters).toFeature()
        )
    }

    private fun reportChosenCenter() {
        if (picker == null || !centerChosen) return
        // wrap(): tras dar la vuelta al mundo la longitud se sale de ±180.
        val center = map?.cameraPosition?.target?.wrap() ?: return
        onCenterChanged(GeoPoint(latitude = center.latitude, longitude = center.longitude))
    }

    private fun MapCircle.toFeature(): Feature {
        val ring = circleOutline(
            centerLatitude = latitude,
            centerLongitude = longitude,
            radiusMeters = radiusMeters
        ).map { Point.fromLngLat(it.longitude, it.latitude) }
        return Feature.fromGeometry(Polygon.fromLngLats(listOf(ring)))
    }

    companion object {
        private const val CIRCLES_SOURCE_ID = "medicase-circles"
        private const val CIRCLES_FILL_LAYER_ID = "medicase-circles-fill"
        private const val CIRCLES_LINE_LAYER_ID = "medicase-circles-line"
        private const val PICKER_SOURCE_ID = "medicase-picker"
        private const val PICKER_FILL_LAYER_ID = "medicase-picker-fill"
        private const val PICKER_LINE_LAYER_ID = "medicase-picker-line"
        private const val MARKER_SOURCE_ID = "medicase-marker"
        private const val MARKER_LAYER_ID = "medicase-marker-layer"
        private const val PIN_IMAGE_ID = "medicase-pin"

        private const val CIRCLE_FILL_OPACITY = 0.15f
        private const val MUTED_CIRCLE_FILL_OPACITY = 0.08f
        private const val PICKER_FILL_OPACITY = 0.3f

        // Patrón del trazo en múltiplos del grosor de la línea: raya, hueco.
        private val DASHED_LINE = arrayOf(2f, 2f)
        private val SOLID_LINE = arrayOf(1f, 0f)
        private const val CIRCLE_LINE_WIDTH = 2f

        /** Nivel de calle: se distinguen las manzanas alrededor del estuche. */
        private const val INITIAL_ZOOM = 15.0

        /**
         * Compone la imagen del marcador como en el diseño: pin del color de
         * alerta con una insignia redonda y un termómetro, que es lo que dice
         * que el pin es el estuche de insulina y no un lugar cualquiera.
         */
        fun createPinBitmap(context: Context, colors: MapPinColors): Bitmap {
            val pin = checkNotNull(ContextCompat.getDrawable(context, R.drawable.ic_map_case_pin)).mutate()
            val glyph = checkNotNull(ContextCompat.getDrawable(context, R.drawable.ic_map_thermometer)).mutate()
            val width = pin.intrinsicWidth
            val height = pin.intrinsicHeight
            val bitmap = createBitmap(width, height)
            val canvas = Canvas(bitmap)

            pin.setTint(colors.pin)
            pin.setBounds(0, 0, width, height)
            pin.draw(canvas)

            // Proporciones de la silueta: 14 × 20, con la cabeza centrada en (7, 7).
            val unit = width / 14f
            val centerX = width / 2f
            val centerY = height * 7f / 20f
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = colors.badgeBorder
            canvas.drawCircle(centerX, centerY, unit * 4.4f, paint)
            paint.color = colors.badge
            canvas.drawCircle(centerX, centerY, unit * 3.7f, paint)

            val glyphHalf = (unit * 2.7f).toInt()
            glyph.setTint(colors.glyph)
            glyph.setBounds(
                centerX.toInt() - glyphHalf,
                centerY.toInt() - glyphHalf,
                centerX.toInt() + glyphHalf,
                centerY.toInt() + glyphHalf
            )
            glyph.draw(canvas)
            return bitmap
        }
    }
}

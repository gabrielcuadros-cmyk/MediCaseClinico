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
    @param:ColorInt private val circleColor: Int
) {
    private var map: MapLibreMap? = null
    private var markerSource: GeoJsonSource? = null
    private var circlesSource: GeoJsonSource? = null

    private var marker: GeoPoint? = null
    private var circles: List<MapCircle> = emptyList()

    private var detached = false
    private var followMarker = true
    private var cameraPlaced = false

    /** El estilo terminó de cargar: se añaden las capas y se pinta lo pendiente. */
    fun attach(map: MapLibreMap, style: Style) {
        if (detached) return

        val circlesSource = GeoJsonSource(CIRCLES_SOURCE_ID)
        style.addSource(circlesSource)
        style.addLayer(
            FillLayer(CIRCLES_FILL_LAYER_ID, CIRCLES_SOURCE_ID).withProperties(
                PropertyFactory.fillColor(circleColor),
                PropertyFactory.fillOpacity(CIRCLE_FILL_OPACITY)
            )
        )
        style.addLayer(
            LineLayer(CIRCLES_LINE_LAYER_ID, CIRCLES_SOURCE_ID).withProperties(
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
            }
        }

        this.map = map
        this.circlesSource = circlesSource
        this.markerSource = markerSource
        render()
    }

    /** Lo que debe verse ahora. Se puede llamar antes de que el mapa esté listo. */
    fun show(marker: GeoPoint?, circles: List<MapCircle>) {
        if (marker == this.marker && circles == this.circles) return
        this.marker = marker
        this.circles = circles
        render()
    }

    /** El mapa se va a destruir: tocar sus fuentes después provocaría un crash nativo. */
    fun detach() {
        detached = true
        map = null
        markerSource = null
        circlesSource = null
    }

    private fun render() {
        val map = map ?: return

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
        private const val MARKER_SOURCE_ID = "medicase-marker"
        private const val MARKER_LAYER_ID = "medicase-marker-layer"
        private const val PIN_IMAGE_ID = "medicase-pin"

        private const val CIRCLE_FILL_OPACITY = 0.15f
        private const val CIRCLE_LINE_WIDTH = 2f

        /** Nivel de calle: se distinguen las manzanas alrededor del estuche. */
        private const val INITIAL_ZOOM = 15.0

        /**
         * Convierte el ícono del marcador en la imagen que usa el mapa, con el
         * color del tema y un centro claro para que el hueco del pin no deje
         * ver las calles de debajo.
         */
        fun createPinBitmap(
            context: Context,
            @ColorInt pinColor: Int,
            @ColorInt centerColor: Int
        ): Bitmap {
            val drawable = checkNotNull(ContextCompat.getDrawable(context, R.drawable.ic_map_case_pin)).mutate()
            val width = drawable.intrinsicWidth
            val height = drawable.intrinsicHeight
            val bitmap = createBitmap(width, height)
            val canvas = Canvas(bitmap)

            // Proporciones del ícono: 14 × 20, con el hueco centrado en (7, 7).
            val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = centerColor }
            canvas.drawCircle(width / 2f, height * 7f / 20f, width * 3.2f / 14f, centerPaint)

            drawable.setTint(pinColor)
            drawable.setBounds(0, 0, width, height)
            drawable.draw(canvas)
            return bitmap
        }
    }
}

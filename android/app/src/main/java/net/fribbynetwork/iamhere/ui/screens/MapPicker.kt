package net.fribbynetwork.iamhere.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * Mappa per scegliere un punto. Il punto selezionato e sempre il centro
 * dello schermo: si trascina la mappa sotto il mirino invece di toccare
 * un pixel preciso col dito. Il cerchio del raggio e disegnato sopra la
 * mappa e si ridimensiona a ogni zoom, quindi mostra sempre l'area vera.
 */
@Composable
fun MapPicker(
    styleUrl: String,
    initialLat: Double,
    initialLon: Double,
    initialZoom: Double = 14.0,
    radiusMeters: Int,
    onCenterChanged: (Double, Double) -> Unit,
    /** Quando cambia, la camera si sposta qui. Null = non spostare. */
    recenterTo: Pair<Double, Double>? = null,
    onUserTouch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var radiusPx by remember { mutableStateOf(0f) }

    val mapView = remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mv = mapView.value ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> mv.onStart()
                Lifecycle.Event.ON_RESUME -> mv.onResume()
                Lifecycle.Event.ON_PAUSE -> mv.onPause()
                Lifecycle.Event.ON_STOP -> mv.onStop()
                Lifecycle.Event.ON_DESTROY -> mv.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.value?.onStop()
            mapView.value?.onDestroy()
        }
    }

    fun recompute(map: MapLibreMap) {
        val c = map.cameraPosition.target ?: return
        val degPerMeterLon = 1.0 / (111320.0 * cos(c.latitude * PI / 180.0).coerceAtLeast(0.01))
        val east = LatLng(c.latitude, c.longitude + radiusMeters * degPerMeterLon)
        val p1 = map.projection.toScreenLocation(c)
        val p2 = map.projection.toScreenLocation(east)
        radiusPx = abs(p2.x - p1.x)
        onCenterChanged(c.latitude, c.longitude)
    }

    LaunchedEffect(radiusMeters, mapRef) {
        mapRef?.let { recompute(it) }
    }

    LaunchedEffect(recenterTo, mapRef) {
        val m = mapRef ?: return@LaunchedEffect
        val r = recenterTo ?: return@LaunchedEffect
        m.moveCamera(CameraUpdateFactory.newLatLng(LatLng(r.first, r.second)))
        recompute(m)
    }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).also { mv ->
                    mapView.value = mv
                    mv.onCreate(null)
                    mv.onStart()
                    mv.onResume()

                    // La mappa sta dentro una colonna che scorre in verticale.
                    // Senza questo, il contenitore si prende il trascinamento
                    // a un dito e la mappa si muove solo con due dita, perche
                    // solo il gesto di zoom chiede di non essere intercettato.
                    // Appena il dito tocca la mappa chiediamo alla colonna di
                    // stare ferma, e glielo ridiamo quando lo si solleva.
                    mv.setOnTouchListener { v, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                                onUserTouch()
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        false // non consumiamo: il gesto prosegue verso la mappa
                    }
                    mv.getMapAsync { map ->
                        mapRef = map
                        map.uiSettings.isRotateGesturesEnabled = false
                        map.uiSettings.isTiltGesturesEnabled = false
                        // L'attribuzione a OpenStreetMap e al fornitore dei tile
                        // e obbligatoria: non va disattivata.
                        map.uiSettings.isAttributionEnabled = true
                        map.uiSettings.isLogoEnabled = true
                        map.setStyle(Style.Builder().fromUri(styleUrl)) {
                            map.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(initialLat, initialLon), initialZoom
                                )
                            )
                            recompute(map)
                        }
                        map.addOnCameraMoveListener { recompute(map) }
                        map.addOnCameraIdleListener { recompute(map) }
                    }
                }
            }
        )

        // Mirino e cerchio del raggio. Canvas disegna e basta: non
        // registra gestori di tocco, quindi il dito lo attraversa e il
        // gesto arriva alla mappa sotto. Qualunque cosa si aggiunga qui
        // sopra deve mantenere questa proprieta.
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            if (radiusPx > 1f) {
                drawCircle(Color(0x33E4572E), radius = radiusPx, center = c)
                drawCircle(Color(0xFFE4572E), radius = radiusPx, center = c, style = Stroke(width = 3f))
            }
            drawLine(Color(0xFFE4572E), Offset(c.x - 22f, c.y), Offset(c.x + 22f, c.y), strokeWidth = 4f)
            drawLine(Color(0xFFE4572E), Offset(c.x, c.y - 22f), Offset(c.x, c.y + 22f), strokeWidth = 4f)
            drawCircle(Color.White, radius = 5f, center = c)
            drawCircle(Color(0xFFE4572E), radius = 5f, center = c, style = Stroke(width = 2.5f))
        }
    }
}

/**
 * Stile di default: OpenFreeMap, tile vettoriali da dati OpenStreetMap
 * senza chiave, senza registrazione e senza limiti dichiarati.
 * Il tile server di openstreetmap.org NON e utilizzabile: la sua policy
 * vieta esplicitamente la distribuzione di app che lo usano.
 */
const val OPENFREEMAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

/** Lo stile personalizzato se c'e, altrimenti quello predefinito. */
fun styleUrlOf(customUrl: String): String =
    customUrl.ifBlank { OPENFREEMAP_STYLE }

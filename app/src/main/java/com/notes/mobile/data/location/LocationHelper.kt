package com.notes.mobile.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Ubicacion bajo demanda: un solo fix por peticion y cancelacion explicita.
 * No hay tracking continuo: minimo consumo de bateria. Todo update pendiente
 * se cancela con [cancelAll] (onStop de la pantalla y cierre de sesion).
 */
object LocationHelper {

    private const val TAG = "LocationHelper"
    private const val TIMEOUT_MS = 30000L
    private const val MAX_LAST_KNOWN_AGE_MS = 120000L

    private val handler = Handler(Looper.getMainLooper())
    private var locationManager: LocationManager? = null
    private var listener: LocationListener? = null
    private var timeoutRunnable: Runnable? = null

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun areProvidersEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @Suppress("DEPRECATION")
    fun requestSingleFix(context: Context, onResult: (String?) -> Unit) {
        requestFix(context) { location ->
            onResult(location?.let { format(it) })
        }
    }

    fun requestSingleCoords(context: Context, onResult: (latitude: Double?, longitude: Double?) -> Unit) {
        requestFix(context) { location ->
            if (location != null) onResult(location.latitude, location.longitude)
            else onResult(null, null)
        }
    }

    @Suppress("DEPRECATION")
    private fun requestFix(context: Context, onLocation: (Location?) -> Unit) {
        cancelAll()
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager = lm

            // 1. Ubicación reciente en caché: instantánea, sin esperar al GPS.
            freshestLastKnown(lm)?.let {
                Log.d(TAG, "Using last known location")
                locationManager = null
                onLocation(it)
                return
            }

            // 2. Sin caché útil: escuchar en ambos proveedores, gana el primero.
            // requestSingleUpdate con solo GPS en interiores casi nunca responde.
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            ).filter { lm.isProviderEnabled(it) }
            if (providers.isEmpty()) {
                Log.w(TAG, "No location provider enabled")
                finishLocation(null, onLocation)
                return
            }
            var delivered = false
            val locListener = LocationListener { location ->
                if (!delivered) {
                    delivered = true
                    finishLocation(location, onLocation)
                }
            }
            listener = locListener
            providers.forEach { provider ->
                lm.requestLocationUpdates(provider, 0L, 0f, locListener, Looper.getMainLooper())
            }
            timeoutRunnable = Runnable {
                Log.w(TAG, "Location timeout")
                if (!delivered) {
                    delivered = true
                    // Último intento: aunque esté vieja, mejor que nada.
                    finishLocation(freshestLastKnown(lm, Long.MAX_VALUE), onLocation)
                }
            }
            handler.postDelayed(timeoutRunnable!!, TIMEOUT_MS)
            Log.d(TAG, "Location updates requested ($providers)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing location permission: ${e.message}")
            finishLocation(null, onLocation)
        } catch (e: Exception) {
            Log.e(TAG, "Location error: ${e.message}")
            finishLocation(null, onLocation)
        }
    }

    private fun freshestLastKnown(
        lm: LocationManager,
        maxAgeMs: Long = MAX_LAST_KNOWN_AGE_MS
    ): Location? {
        return try {
            listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            ).filter { lm.isProviderEnabled(it) }
                .mapNotNull { provider ->
                    try {
                        lm.getLastKnownLocation(provider)
                    } catch (_: SecurityException) {
                        null
                    }
                }
                .filter {
                    (android.os.SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000 <= maxAgeMs
                }
                .maxByOrNull { it.elapsedRealtimeNanos }
        } catch (_: Exception) {
            null
        }
    }

    private fun finishLocation(result: Location?, onLocation: (Location?) -> Unit) {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
        try {
            listener?.let { locationManager?.removeUpdates(it) }
        } catch (_: Exception) {
        }
        listener = null
        locationManager = null
        onLocation(result)
    }

    fun cancelAll() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
        try {
            listener?.let { locationManager?.removeUpdates(it) }
        } catch (_: Exception) {
        }
        listener = null
        locationManager = null
        Log.d(TAG, "Location requests cancelled")
    }

    private fun format(location: Location): String {
        return "%.5f, %.5f".format(location.latitude, location.longitude)
    }
}

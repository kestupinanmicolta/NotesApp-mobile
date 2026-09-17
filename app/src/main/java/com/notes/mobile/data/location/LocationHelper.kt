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
    private const val TIMEOUT_MS = 15000L

    private val handler = Handler(Looper.getMainLooper())
    private var locationManager: LocationManager? = null
    private var listener: LocationListener? = null
    private var timeoutRunnable: Runnable? = null
    private var callback: ((String?) -> Unit)? = null

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    fun requestSingleFix(context: Context, onResult: (String?) -> Unit) {
        cancelAll()
        callback = onResult
        try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager = lm
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                Log.w(TAG, "No location provider enabled")
                finish(null)
                return
            }
            val locListener = LocationListener { location -> finish(format(location)) }
            listener = locListener
            lm.requestSingleUpdate(provider, locListener, Looper.getMainLooper())
            timeoutRunnable = Runnable {
                Log.w(TAG, "Location timeout")
                finish(null)
            }
            handler.postDelayed(timeoutRunnable!!, TIMEOUT_MS)
            Log.d(TAG, "Single fix requested ($provider)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing location permission: ${e.message}")
            finish(null)
        } catch (e: Exception) {
            Log.e(TAG, "Location error: ${e.message}")
            finish(null)
        }
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
        callback = null
        Log.d(TAG, "Location requests cancelled")
    }

    private fun finish(result: String?) {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
        try {
            listener?.let { locationManager?.removeUpdates(it) }
        } catch (_: Exception) {
        }
        val cb = callback
        listener = null
        locationManager = null
        callback = null
        cb?.invoke(result)
    }

    private fun format(location: Location): String {
        return "%.5f, %.5f".format(location.latitude, location.longitude)
    }
}

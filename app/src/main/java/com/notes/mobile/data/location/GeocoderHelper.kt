package com.notes.mobile.data.location

import android.content.Context
import android.location.Geocoder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Convierte coordenadas en nombre legible (barrio, municipio/ciudad).
 * Usa Geocoder del sistema (requiere red). Si falla, devuelve null y la
 * UI muestra las coordenadas como respaldo.
 */
object GeocoderHelper {

    private const val TAG = "GeocoderHelper"

    suspend fun resolveName(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) {
                Log.w(TAG, "Geocoder not present on device")
                return@withContext null
            }
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.getDefault())
                .getFromLocation(latitude, longitude, 1)
            val address = addresses?.firstOrNull() ?: return@withContext null
            // Barrio/municipio primero, luego ciudad/departamento.
            val parts = listOfNotNull(
                address.subLocality?.takeIf { it.isNotBlank() },
                address.locality?.takeIf { it.isNotBlank() },
                address.subAdminArea?.takeIf { it.isNotBlank() },
                address.adminArea?.takeIf { it.isNotBlank() }
            ).distinct()
            val name = parts.take(2).joinToString(", ").ifBlank { null }
            Log.d(TAG, "Resolved ($latitude,$longitude) -> $name")
            name
        } catch (e: Exception) {
            Log.e(TAG, "Reverse geocode failed: ${e.message}")
            null
        }
    }

    fun coordsText(latitude: Double, longitude: Double): String =
        "%.5f, %.5f".format(latitude, longitude)
}

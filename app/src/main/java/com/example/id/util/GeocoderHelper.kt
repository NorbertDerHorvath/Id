package com.example.id.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class GeocoderHelper(private val context: Context) {

    private val geocoder = Geocoder(context)

    suspend fun getAddressFromCoordinates(latitude: Double, longitude: Double): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return suspendCoroutine { continuation ->
                try {
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                        continuation.resume(formatAddress(addresses.firstOrNull()))
                    }
                } catch (e: IOException) {
                    continuation.resume(null)
                }
            }
        } else {
            return try {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                formatAddress(addresses?.firstOrNull())
            } catch (e: IOException) {
                null
            }
        }
    }

    private fun formatAddress(address: Address?): String? {
        if (address == null) return null
        // Város, utca, házszám formátum
        val city = address.locality ?: ""
        val street = address.thoroughfare ?: ""
        val number = address.subThoroughfare ?: ""
        
        return "$city, $street $number".trim(' ', ',')
    }
}

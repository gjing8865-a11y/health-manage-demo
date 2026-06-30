package com.example.healthmanager.platform

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.example.healthmanager.domain.WeatherLocationCandidate
import com.example.healthmanager.domain.WeatherLocationResolver
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class AndroidWeatherLocationResolver(context: Context) {
    private val appContext = context.applicationContext

    suspend fun resolve(latitude: Double, longitude: Double): WeatherLocationCandidate? {
        val geocoder = Geocoder(appContext, Locale.CHINA)
        val address = geocoder.awaitFromLocation(latitude, longitude, 1)
            ?.firstOrNull()

        return WeatherLocationResolver.buildCandidate(
            locality = address?.locality,
            subAdminArea = address?.subAdminArea,
            adminArea = address?.adminArea,
            subLocality = address?.subLocality
        )
    }

    private suspend fun Geocoder.awaitFromLocation(
        latitude: Double,
        longitude: Double,
        maxResults: Int
    ): List<Address>? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                getFromLocation(latitude, longitude, maxResults, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) {
                            continuation.resume(addresses)
                        }
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) {
                            continuation.resume(emptyList())
                        }
                    }
                })
            }
        } else {
            @Suppress("DEPRECATION")
            getFromLocation(latitude, longitude, maxResults)
        }
    }
}

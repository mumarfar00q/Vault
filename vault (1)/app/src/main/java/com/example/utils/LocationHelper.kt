package com.example.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.network.LocationMetadata
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.Locale

object LocationHelper {
    private const val TAG = "LocationHelper"

    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(context: Context): LocationMetadata? {
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCoarse && !hasFine) {
            Log.d(TAG, "Location permissions not available.")
            return null
        }

        return try {
            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val location: Location? = suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    continuation.resume(loc)
                }.addOnFailureListener {
                    continuation.resume(null)
                }
            }
            if (location != null) {
                val addressStr = getAddressFromCoords(context, location.latitude, location.longitude)
                LocationMetadata(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    address = addressStr
                )
            } else {
                Log.d(TAG, "Last known location is null.")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching location", e)
            null
        }
    }

    private fun getAddressFromCoords(context: Context, latitude: Double, longitude: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val street = address.getAddressLine(0) ?: ""
                val locality = address.locality ?: ""
                val country = address.countryName ?: ""
                listOfNotNull(street, locality, country).joinToString(", ")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Geocoding error", e)
            null
        }
    }
}

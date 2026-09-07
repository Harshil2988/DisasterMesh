package com.disastermesh.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** What a single location attempt produced. */
sealed interface SosLocation {
    data class Fix(
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long,
        /** True when the fix came from the cache rather than a fresh read. */
        val fromCache: Boolean
    ) : SosLocation

    data class Unavailable(val reason: String) : SosLocation
}

/**
 * Reads ONE location, on demand, when the user sends an SOS.
 *
 * Deliberately has no start/stop: there is no continuous tracking, no location
 * history, and nothing is stored or uploaded. The coordinates go straight into
 * one mesh message and are then forgotten by this class.
 *
 * Uses the fused provider from Play Services, which the project already depends
 * on for Nearby Connections. No Maps SDK, no geocoding, no internet.
 */
class SosLocationProvider(context: Context) {

    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    /**
     * Tries for a fresh fix, falling back to the last known one.
     * Never throws — every failure comes back as [SosLocation.Unavailable].
     */
    suspend fun currentLocation(): SosLocation {
        if (!hasAnyLocationPermission()) {
            return SosLocation.Unavailable("Location permission has not been granted.")
        }
        if (!locationServicesEnabled()) {
            return SosLocation.Unavailable("Location is switched off on this phone.")
        }

        // Precise when allowed, approximate when only coarse was granted.
        val priority = if (hasFinePermission()) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val cancellation = CancellationTokenSource()
        return try {
            val fresh = withTimeout(FRESH_FIX_TIMEOUT_MS) {
                client.getCurrentLocation(priority, cancellation.token).await()
            }
            if (fresh != null) {
                SosLocation.Fix(fresh.latitude, fresh.longitude, System.currentTimeMillis(), false)
            } else {
                lastKnownLocation() ?: SosLocation.Unavailable("No GPS fix available yet.")
            }
        } catch (e: TimeoutCancellationException) {
            // Give up waiting, but a cached fix is far better than nothing.
            cancellation.cancel()
            Log.w(TAG, "Fresh location timed out; falling back to last known")
            lastKnownLocation() ?: SosLocation.Unavailable("Timed out waiting for GPS.")
        } catch (e: SecurityException) {
            SosLocation.Unavailable("Location permission was refused.")
        } catch (e: Exception) {
            Log.w(TAG, "Location failed", e)
            lastKnownLocation() ?: SosLocation.Unavailable("Location unavailable on this device.")
        }
    }

    private suspend fun lastKnownLocation(): SosLocation.Fix? = try {
        val cached = client.lastLocation.await()
        cached?.let {
            SosLocation.Fix(it.latitude, it.longitude, it.time, fromCache = true)
        }
    } catch (e: Exception) {
        Log.w(TAG, "Last known location failed", e)
        null
    }

    private fun hasFinePermission(): Boolean = ContextCompat.checkSelfPermission(
        appContext, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun hasAnyLocationPermission(): Boolean = hasFinePermission() ||
        ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun locationServicesEnabled(): Boolean = try {
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    } catch (e: Exception) {
        false
    }

    private companion object {
        const val TAG = "DisasterMesh"

        /** Long enough for a real fix, short enough not to strand someone. */
        const val FRESH_FIX_TIMEOUT_MS = 12_000L
    }
}

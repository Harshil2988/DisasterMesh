package com.disastermesh.app.nearby

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Works out which runtime permissions Nearby Connections needs on THIS phone.
 *
 * The list is different per Android version, which is the single most common
 * reason Nearby "silently does nothing" — so we compute it in one place.
 */
object MeshPermissions {

    /** Permissions we must ask the user for at runtime on this device. */
    fun required(): Array<String> {
        val permissions = mutableListOf<String>()

        // Android 12 (API 31) split Bluetooth into three separate runtime permissions.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions += Manifest.permission.BLUETOOTH_SCAN
            permissions += Manifest.permission.BLUETOOTH_ADVERTISE
            permissions += Manifest.permission.BLUETOOTH_CONNECT
        }

        // Location is needed on EVERY Android version we support, and Nearby Connections
        // wants PRECISE location - verified on a physical Android 13 phone, where
        // startDiscovery() failed with 8036 (MISSING_PERMISSION_ACCESS_FINE_LOCATION)
        // while coarse location was already granted. NEARBY_WIFI_DEVICES does not
        // replace it. Both are requested together because Android 12+ refuses a
        // FINE request that does not also ask for COARSE.
        permissions += Manifest.permission.ACCESS_COARSE_LOCATION
        permissions += Manifest.permission.ACCESS_FINE_LOCATION

        // Android 13 (API 33) introduced a dedicated permission for nearby Wi-Fi instead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.NEARBY_WIFI_DEVICES
        }

        return permissions.toTypedArray()
    }

    /** True when every permission in [required] has already been granted. */
    fun allGranted(context: Context): Boolean = required().all { isGranted(context, it) }

    /** The permissions the user has not granted yet. */
    fun missing(context: Context): List<String> = required().filterNot { isGranted(context, it) }

    /** Turns "android.permission.BLUETOOTH_SCAN" into "BLUETOOTH_SCAN" for display. */
    fun shortName(permission: String): String = permission.substringAfterLast('.')

    private fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

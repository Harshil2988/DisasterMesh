package com.disastermesh.app.uplink

import android.content.Context
import android.util.Log
import com.disastermesh.app.command.EmergencyReport
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** The result of one delivery attempt. */
sealed interface UplinkResult {
    data object Delivered : UplinkResult
    data class Failed(val reason: String, val retryable: Boolean) : UplinkResult
    data object NotConfigured : UplinkResult
}

/**
 * A way of getting one emergency report out of the mesh.
 *
 * Kept behind an interface so the transport can change without the queue or the
 * UI knowing. The mesh never depends on any implementation existing.
 */
interface ExternalUplink {
    val name: String
    suspend fun send(report: EmergencyReport): UplinkResult
}

/**
 * Delivers reports over ordinary IP using whatever transport the OS has bound —
 * cellular, Wi-Fi, or satellite.
 *
 * This is the honest place to be clear about satellite: when the platform routes
 * the active network over TRANSPORT_SATELLITE, this HTTPS request travels over
 * satellite because the OS put it there. The app does not, and cannot, command a
 * satellite transmission itself (see [SatelliteUplink]).
 *
 * The endpoint is OPTIONAL and unset by default. With none configured nothing is
 * ever transmitted, which keeps emergency data on the device unless an operator
 * has deliberately pointed the app somewhere.
 */
class InternetUplink(context: Context) : ExternalUplink {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override val name: String = "Internet"

    /**
     * Operator-configured HTTPS endpoint, e.g. https://example.org/api/emergency
     * No default, no bundled credentials, nothing hardcoded.
     */
    var endpoint: String?
        get() = prefs.getString(KEY_ENDPOINT, null)?.takeIf { it.isNotBlank() }
        set(value) {
            val clean = value?.trim().orEmpty()
            // Plaintext HTTP is rejected outright: this payload can contain a
            // person's location.
            val accepted = clean.takeIf { it.startsWith("https://") }
            prefs.edit().putString(KEY_ENDPOINT, accepted).apply()
        }

    val isConfigured: Boolean get() = endpoint != null

    override suspend fun send(report: EmergencyReport): UplinkResult {
        val target = endpoint ?: return UplinkResult.NotConfigured

        // Only the structured emergency fields travel. No chat, no media, no
        // device identifiers beyond the mesh node id the report already carries.
        val body = JSONObject().apply {
            put("reportId", report.reportId)
            put("senderNodeId", report.senderId)
            put("category", report.category.name)
            put("priority", report.category.priorityLabel)
            put("description", report.description)
            put("timestamp", report.receivedAt)
            report.latitude?.let { put("latitude", it) }
            report.longitude?.let { put("longitude", it) }
            report.peopleAffected?.let { put("peopleAffected", it) }
            report.hops?.let { put("hops", it) }
        }.toString()

        return try {
            val url = URL(target)
            val connection = url.openConnection() as? HttpsURLConnection
                ?: return UplinkResult.Failed("Endpoint must be HTTPS", retryable = false)

            connection.apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                // TLS verification is left at platform defaults on purpose.
            }

            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            connection.disconnect()

            when {
                code in 200..299 -> UplinkResult.Delivered
                // 4xx means the server rejected the content; retrying will not help.
                code in 400..499 -> UplinkResult.Failed("Rejected ($code)", retryable = false)
                else -> UplinkResult.Failed("Server error ($code)", retryable = true)
            }
        } catch (e: Exception) {
            // Connectivity can vanish mid-upload. That is expected, not fatal.
            Log.w(TAG, "Uplink attempt failed", e)
            UplinkResult.Failed(e.message ?: "Network error", retryable = true)
        }
    }

    private companion object {
        const val TAG = "DisasterMesh"
        const val PREFS = "disaster_mesh"
        const val KEY_ENDPOINT = "uplink_endpoint"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
    }
}

/**
 * Satellite: detection only, and deliberately so.
 *
 * WHAT THE PLATFORM ACTUALLY OFFERS A THIRD-PARTY APP
 * ---------------------------------------------------
 * Verified against android.jar for this project's compile SDK, the entire
 * public satellite surface is:
 *
 *   SatelliteManager.registerStateChangeListener(Executor, listener)
 *   SatelliteManager.unregisterStateChangeListener(listener)
 *   SatelliteStateChangeListener.onEnabledStateChanged(boolean)
 *   SatelliteManager.PROPERTY_SATELLITE_DATA_OPTIMIZED
 *
 * plus NetworkCapabilities.TRANSPORT_SATELLITE for spotting a satellite-backed
 * network.
 *
 * There is NO public API for sending an arbitrary payload over satellite. The
 * methods that would do so (requestSatelliteEnabled, sendDatagram and friends)
 * are @SystemApi and need privileged, signature-level permissions no ordinary
 * app can hold. Using hidden APIs or working around carrier entitlement is not
 * something this app does.
 *
 * So this class NEVER transmits. What genuinely works is the combination: when
 * the OS binds the active network over TRANSPORT_SATELLITE, [InternetUplink]'s
 * ordinary HTTPS request is carried over satellite because the system routed it
 * there. The app observes; the platform transmits.
 *
 * Satellite availability in practice depends on compatible hardware, Android 16
 * or newer, the carrier and its entitlement, the region, an active subscription
 * and current satellite visibility. It is unavailable on most devices, and that
 * is the expected outcome rather than a fault.
 */
class SatelliteUplink : ExternalUplink {

    override val name: String = "Satellite"

    override suspend fun send(report: EmergencyReport): UplinkResult =
        UplinkResult.Failed(
            reason = "No public Android API allows an app to transmit over satellite. " +
                "Reports travel over satellite only when the system routes the " +
                "network that way.",
            retryable = false
        )
}

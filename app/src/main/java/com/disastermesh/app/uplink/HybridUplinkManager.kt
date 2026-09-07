package com.disastermesh.app.uplink

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.telephony.satellite.SatelliteManager
import android.telephony.satellite.SatelliteStateChangeListener
import android.util.Log
import com.disastermesh.app.command.EmergencyReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MESH -> OUTSIDE WORLD.
 *
 * Strictly separate from NearbyConnectionManager, which owns PHONE <-> PHONE and
 * is not touched by any of this. If every method here failed, the mesh would be
 * completely unaffected: nothing in the relay path calls into this class.
 *
 * Connectivity is watched with ConnectivityManager callbacks and satellite state
 * with the platform listener — both event-driven, so nothing polls and nothing
 * wakes the CPU on a timer.
 */
class HybridUplinkManager(context: Context) {

    private val appContext = context.applicationContext
    private val connectivity =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val internetUplink = InternetUplink(appContext)
    private val satelliteUplink = SatelliteUplink()

    val queue = UplinkQueue(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var uploadJob: Job? = null

    private val _status = MutableStateFlow(UplinkStatus())
    val status: StateFlow<UplinkStatus> = _status.asStateFlow()

    private val _events = MutableStateFlow<List<UplinkEvent>>(emptyList())
    val events: StateFlow<List<UplinkEvent>> = _events.asStateFlow()

    /** Reports the queue can draw on, supplied by the app layer. */
    @Volatile
    private var reportSource: () -> List<EmergencyReport> = { emptyList() }

    private var monitoring = false

    // -----------------------------------------------------------------
    // Lifecycle — driven by the existing mesh service
    // -----------------------------------------------------------------

    @Synchronized
    fun start(reports: () -> List<EmergencyReport>) {
        reportSource = reports
        if (monitoring) return
        monitoring = true

        registerNetworkCallback()
        registerSatelliteListener()
        refreshStatus()
        log("Uplink monitoring started", UplinkEvent.Kind.INFO)
    }

    @Synchronized
    fun stop() {
        if (!monitoring) return
        monitoring = false
        uploadJob?.cancel()
        unregisterNetworkCallback()
        unregisterSatelliteListener()
        _status.update { it.copy(transport = UplinkTransport.NONE, internetValidated = false) }
        log("Uplink monitoring stopped", UplinkEvent.Kind.INFO)
    }

    // -----------------------------------------------------------------
    // Connectivity detection
    // -----------------------------------------------------------------

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshStatus()
        override fun onLost(network: Network) = refreshStatus()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            refreshStatus(caps)
    }

    private fun registerNetworkCallback() {
        val manager = connectivity ?: return
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            manager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            connectivity?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Already unregistered; harmless.
        }
    }

    /**
     * Reads the real capabilities of the active network.
     *
     * Deliberately does NOT treat "Wi-Fi is on" as internet: the mesh itself uses
     * Wi-Fi and Bluetooth with no internet at all. Only NET_CAPABILITY_VALIDATED
     * — the platform having actually verified reachability — counts as a path out.
     */
    private fun refreshStatus(known: NetworkCapabilities? = null) {
        val manager = connectivity
        val caps = known ?: manager?.let { it.getNetworkCapabilities(it.activeNetwork) }

        val transport = when {
            caps == null -> UplinkTransport.NONE
            hasSatelliteTransport(caps) -> UplinkTransport.SATELLITE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> UplinkTransport.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> UplinkTransport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> UplinkTransport.ETHERNET
            else -> UplinkTransport.OTHER
        }

        val validated = caps != null &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val previous = _status.value
        _status.update {
            it.copy(
                transport = transport,
                internetValidated = validated,
                endpointConfigured = internetUplink.isConfigured,
                pending = queue.pendingCount,
                delivered = queue.deliveredCount
            )
        }

        val now = _status.value
        if (now.hasExternalPath && !previous.hasExternalPath) {
            log("Gateway path available via ${transport.label}", UplinkEvent.Kind.GATEWAY)
        } else if (!now.hasExternalPath && previous.hasExternalPath) {
            log("External path lost — holding reports locally", UplinkEvent.Kind.INFO)
        }

        syncQueueState()
        if (now.isGateway) triggerUpload()
    }

    /**
     * TRANSPORT_SATELLITE exists from Android 15 (API 35). Below that the
     * constant is unavailable, so satellite simply cannot be reported — which is
     * the correct answer, not a fallback guess.
     */
    private fun hasSatelliteTransport(caps: NetworkCapabilities): Boolean =
        Build.VERSION.SDK_INT >= 35 &&
            runCatching { caps.hasTransport(NetworkCapabilities.TRANSPORT_SATELLITE) }
                .getOrDefault(false)

    // -----------------------------------------------------------------
    // Satellite state
    // -----------------------------------------------------------------

    private var satelliteManager: SatelliteManager? = null
    private var satelliteListener: SatelliteStateChangeListener? = null

    /**
     * Observes platform satellite state where it exists.
     *
     * This is the ONLY public satellite capability available to a third-party
     * app: a boolean saying whether satellite is enabled. It confers no ability
     * to transmit — see [SatelliteUplink] for the full explanation.
     */
    private fun registerSatelliteListener() {
        if (Build.VERSION.SDK_INT < SATELLITE_MIN_SDK) {
            setSatellite(SatelliteSupport.UNSUPPORTED)
            return
        }
        try {
            val manager = appContext.getSystemService(SatelliteManager::class.java)
            if (manager == null) {
                setSatellite(SatelliteSupport.UNSUPPORTED)
                return
            }
            val listener = SatelliteStateChangeListener { enabled ->
                setSatellite(
                    if (enabled) SatelliteSupport.ACTIVE else SatelliteSupport.SUPPORTED_INACTIVE
                )
                log(
                    if (enabled) "Platform reports satellite enabled"
                    else "Platform reports satellite disabled",
                    UplinkEvent.Kind.GATEWAY
                )
            }
            manager.registerStateChangeListener({ it.run() }, listener)
            satelliteManager = manager
            satelliteListener = listener
            // Registered successfully, but nothing has been reported yet.
            setSatellite(SatelliteSupport.SUPPORTED_INACTIVE)
        } catch (e: Throwable) {
            // Missing hardware, missing entitlement, or a SecurityException on a
            // device that exposes the class but not the capability.
            Log.w(TAG, "Satellite state unavailable", e)
            setSatellite(SatelliteSupport.UNSUPPORTED)
        }
    }

    private fun unregisterSatelliteListener() {
        try {
            val manager = satelliteManager
            val listener = satelliteListener
            if (manager != null && listener != null) {
                manager.unregisterStateChangeListener(listener)
            }
        } catch (e: Throwable) {
            // Nothing to do.
        }
        satelliteManager = null
        satelliteListener = null
    }

    private fun setSatellite(support: SatelliteSupport) {
        _status.update { it.copy(satellite = support) }
    }

    // -----------------------------------------------------------------
    // Store and forward
    // -----------------------------------------------------------------

    /** Registers reports so they can be delivered when a gateway appears. */
    fun trackReports(reports: List<EmergencyReport>) {
        if (reports.isEmpty()) return
        val before = queue.pendingCount
        queue.enqueueMissing(reports.map { it.reportId }, waitingState())
        val added = queue.pendingCount - before
        if (added > 0) {
            log("$added emergency report(s) stored for uplink", UplinkEvent.Kind.QUEUED)
        }
        refreshCounts()
        if (_status.value.isGateway) triggerUpload()
    }

    private fun waitingState(): DeliveryState = when {
        !internetUplink.isConfigured -> DeliveryState.NO_ENDPOINT
        _status.value.hasExternalPath -> DeliveryState.GATEWAY_AVAILABLE
        else -> DeliveryState.WAITING_FOR_GATEWAY
    }

    private fun syncQueueState() {
        queue.refreshWaitingState(waitingState())
        refreshCounts()
    }

    private fun refreshCounts() {
        _status.update {
            it.copy(
                pending = queue.pendingCount,
                delivered = queue.deliveredCount,
                endpointConfigured = internetUplink.isConfigured
            )
        }
    }

    /**
     * Sends queued reports in emergency priority order: P0 critical first, then
     * medical, warning, supply, safe. On a constrained link the most urgent
     * report should be the one that gets through.
     */
    private fun triggerUpload() {
        if (uploadJob?.isActive == true) return
        uploadJob = scope.launch {
            _status.update { it.copy(uploading = true, lastError = null) }
            try {
                val pending = reportSource()
                    .filterNot { queue.isDelivered(it.reportId) }
                    .sortedWith(
                        compareBy<EmergencyReport> { it.category.priority }
                            .thenBy { it.receivedAt }
                    )

                for (report in pending) {
                    if (!monitoring || !_status.value.hasExternalPath) break

                    queue.update(report.reportId) { it.copy(state = DeliveryState.UPLOADING) }
                    refreshCounts()

                    when (val result = internetUplink.send(report)) {
                        is UplinkResult.Delivered -> {
                            queue.update(report.reportId) {
                                it.copy(
                                    state = DeliveryState.DELIVERED,
                                    lastAttempt = System.currentTimeMillis(),
                                    lastError = null
                                )
                            }
                            log(
                                "Delivered ${report.category.label} report from ${report.senderId}",
                                UplinkEvent.Kind.SUCCESS
                            )
                        }

                        is UplinkResult.NotConfigured -> {
                            queue.update(report.reportId) {
                                it.copy(state = DeliveryState.NO_ENDPOINT)
                            }
                            break
                        }

                        is UplinkResult.Failed -> {
                            queue.update(report.reportId) {
                                it.copy(
                                    state = if (result.retryable) {
                                        DeliveryState.RETRYING
                                    } else {
                                        DeliveryState.WAITING_FOR_GATEWAY
                                    },
                                    attempts = it.attempts + 1,
                                    lastAttempt = System.currentTimeMillis(),
                                    lastError = result.reason
                                )
                            }
                            _status.update { it.copy(lastError = result.reason) }
                            log("Upload failed: ${result.reason}", UplinkEvent.Kind.FAILURE)
                            if (result.retryable) {
                                // Back off, then let the next event retry.
                                delay(RETRY_BACKOFF_MS)
                                break
                            }
                        }
                    }
                    refreshCounts()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Upload pass aborted", e)
            } finally {
                _status.update { it.copy(uploading = false) }
                refreshCounts()
            }
        }
    }

    // -----------------------------------------------------------------
    // Configuration + events
    // -----------------------------------------------------------------

    var endpoint: String?
        get() = internetUplink.endpoint
        set(value) {
            internetUplink.endpoint = value
            log(
                if (internetUplink.isConfigured) "Uplink endpoint configured"
                else "Uplink endpoint cleared",
                UplinkEvent.Kind.INFO
            )
            refreshStatus()
        }

    /** Records a real event. Nothing here is ever synthesised for a demo. */
    private fun log(text: String, kind: UplinkEvent.Kind) {
        Log.d(TAG, "uplink: $text")
        _events.update { current ->
            (current + UplinkEvent(System.currentTimeMillis(), text, kind))
                .takeLast(MAX_EVENTS)
        }
    }

    private companion object {
        const val TAG = "DisasterMesh"
        const val MAX_EVENTS = 60
        const val RETRY_BACKOFF_MS = 8_000L

        /** SatelliteManager became public API in Android 16. */
        const val SATELLITE_MIN_SDK = 36
    }
}

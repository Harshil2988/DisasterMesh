package com.disastermesh.app.uplink

/**
 * How this node can currently reach the outside world.
 *
 * Deliberately separate from mesh state: the mesh works with every one of these
 * set to none. This describes only MESH -> OUTSIDE WORLD.
 */
enum class UplinkTransport(val label: String) {
    /** No usable external path. The mesh still works. */
    NONE("None"),
    CELLULAR("Cellular"),
    WIFI("Wi-Fi"),
    ETHERNET("Ethernet"),

    /**
     * The active network reports TRANSPORT_SATELLITE. Set ONLY from the
     * platform's own capability report, never inferred from "no internet".
     */
    SATELLITE("Satellite"),
    OTHER("Other")
}

/**
 * What this device can tell us about satellite support.
 *
 * Three genuinely different things, kept apart so the UI never overstates:
 * the API may not exist, may exist but report disabled, or may report enabled.
 */
enum class SatelliteSupport(val label: String, val detail: String) {
    /**
     * The platform exposes no public satellite state API. This is the case on
     * every Android below 16 (API 36), and on API 36+ devices whose hardware or
     * carrier does not offer it.
     */
    UNSUPPORTED(
        "Unavailable",
        "This device or Android version does not expose satellite status."
    ),

    /** The API exists and reports satellite currently disabled/not in use. */
    SUPPORTED_INACTIVE(
        "Available",
        "This device can report satellite status. Not currently active."
    ),

    /** The platform reports satellite is enabled/active right now. */
    ACTIVE(
        "Active",
        "The system reports satellite connectivity is currently enabled."
    )
}

/** Where an emergency report stands on its journey out of the mesh. */
enum class DeliveryState(val label: String) {
    /** Held locally; no gateway has been available. */
    WAITING_FOR_GATEWAY("Waiting for gateway"),

    /** A gateway exists and this item is queued behind higher priorities. */
    GATEWAY_AVAILABLE("Gateway available"),

    UPLOADING("Uploading"),
    DELIVERED("Delivered"),

    /** The attempt failed; it stays queued and will be retried. */
    RETRYING("Retrying"),

    /** No external endpoint is configured, so nothing will be sent. */
    NO_ENDPOINT("No endpoint configured");

    companion object {
        fun fromName(name: String?): DeliveryState =
            entries.firstOrNull { it.name == name } ?: WAITING_FOR_GATEWAY
    }
}

/**
 * The complete uplink picture for the UI.
 *
 * [isGateway] is true only when there is a genuinely usable external path. A
 * gateway is still an ordinary mesh node — this adds a capability, not a rank.
 */
data class UplinkStatus(
    val transport: UplinkTransport = UplinkTransport.NONE,
    /** The platform validated actual internet reachability on this network. */
    val internetValidated: Boolean = false,
    val satellite: SatelliteSupport = SatelliteSupport.UNSUPPORTED,
    /** True when an external endpoint has been configured by the operator. */
    val endpointConfigured: Boolean = false,
    val pending: Int = 0,
    val delivered: Int = 0,
    val uploading: Boolean = false,
    val lastError: String? = null
) {
    /** Can this node actually carry data out of the mesh right now? */
    val hasExternalPath: Boolean
        get() = transport != UplinkTransport.NONE && internetValidated

    /** A gateway needs both a path out AND somewhere to send to. */
    val isGateway: Boolean
        get() = hasExternalPath && endpointConfigured

    /** True only when the platform itself says satellite is enabled. */
    val isSatelliteGateway: Boolean
        get() = isGateway &&
            (transport == UplinkTransport.SATELLITE || satellite == SatelliteSupport.ACTIVE)

    val summary: String
        get() = when {
            isSatelliteGateway -> "Satellite gateway active"
            isGateway -> "Gateway active via ${transport.label}"
            hasExternalPath && !endpointConfigured ->
                "External path available — no endpoint configured"
            transport != UplinkTransport.NONE && !internetValidated ->
                "${transport.label} connected, but no verified internet"
            else -> "No external uplink — mesh only"
        }
}

/** One real thing that happened. Never synthesised for presentation. */
data class UplinkEvent(
    val timestamp: Long,
    val text: String,
    val kind: Kind
) {
    enum class Kind { INFO, QUEUED, GATEWAY, UPLOAD, SUCCESS, FAILURE }
}

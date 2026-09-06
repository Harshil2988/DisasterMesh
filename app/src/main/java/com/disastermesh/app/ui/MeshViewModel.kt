package com.disastermesh.app.ui

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.disastermesh.app.nearby.MeshLogEntry
import com.disastermesh.app.nearby.NearbyConnectionManager
import com.disastermesh.app.service.MeshForegroundService
import com.disastermesh.app.service.MeshNodeHolder
import com.disastermesh.app.location.SosLocation
import com.disastermesh.app.location.SosLocationProvider
import com.disastermesh.app.notify.NotificationPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The emergency categories offered on the dashboard. Presentation only. */
enum class MessageCategory(val label: String) {
    SAFE("SAFE"),
    MEDICAL("MEDICAL"),
    WARNING("WARNING"),
    SUPPLY("SUPPLY")
}

/**
 * Holds the [NearbyConnectionManager] so the mesh survives screen rotation,
 * and exposes exactly the actions the screens need.
 *
 * The networking is untouched by the redesign — the UI is a presentation layer
 * over the same manager and the same [com.disastermesh.app.nearby.MeshState].
 */
class MeshViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * The SHARED mesh node, owned by the process rather than by this ViewModel.
     * That is what lets the mesh keep running when the Activity goes away.
     */
    private val manager: NearbyConnectionManager = MeshNodeHolder.get(application)

    val state = manager.state

    /**
     * Which emergency category is selected. This is UI state: it is attached to
     * the SOS payload text and nothing else. It does not change routing, TTL or
     * any other networking behaviour.
     */
    private val _selectedCategory = mutableStateOf(MessageCategory.SAFE)
    val selectedCategory: State<MessageCategory> = _selectedCategory

    fun selectCategory(category: MessageCategory) {
        _selectedCategory.value = category
    }

    // --- Notification switches -------------------------------------------
    // Presentation only. Turning these off stops notifications being posted;
    // messages are still received and still relayed exactly as before.

    private val notificationPrefs = NotificationPreferences(application)

    private val _messageNotifications = mutableStateOf(notificationPrefs.messagesEnabled)
    val messageNotifications: State<Boolean> = _messageNotifications

    private val _sosNotifications = mutableStateOf(notificationPrefs.sosEnabled)
    val sosNotifications: State<Boolean> = _sosNotifications

    fun setMessageNotifications(enabled: Boolean) {
        notificationPrefs.messagesEnabled = enabled
        _messageNotifications.value = enabled
    }

    fun setSosNotifications(enabled: Boolean) {
        notificationPrefs.sosEnabled = enabled
        _sosNotifications.value = enabled
    }

    /**
     * Advertise, discover, and auto-connect to whatever is out there.
     *
     * Goes through the foreground service so reception survives the UI being
     * closed. The service starts the same shared manager this ViewModel reads.
     */
    fun startMesh() = MeshForegroundService.start(getApplication())

    fun stopMesh() = MeshForegroundService.stop(getApplication())

    /** Unchanged from the working build: sends the plain payload "HELLO". */
    fun sendHello() = manager.sendText("HELLO")

    /**
     * Sends a typed text message.
     *
     * Goes through exactly the same [NearbyConnectionManager.sendText] path as
     * HELLO and SOS — the text simply becomes the envelope's payload, so it gets
     * the existing messageId, TTL, duplicate protection and relay for free.
     * No second networking system.
     */
    fun sendMessage(text: String) {
        val trimmed = text.trim().take(MAX_MESSAGE_LENGTH)
        if (trimmed.isEmpty()) return
        manager.sendText(trimmed)
    }

    /**
     * Emergency broadcast. Deliberately goes through the SAME sendText path as
     * HELLO — no new networking, no new message type. The only difference is the
     * payload text, which is marked so the UI can style it as an emergency.
     */
    // --- SOS with location ------------------------------------------------

    private val locationProvider = SosLocationProvider(application)

    private val _sosLocation = mutableStateOf<SosLocationState>(SosLocationState.Idle)
    val sosLocation: State<SosLocationState> = _sosLocation

    private var locationJob: Job? = null

    /**
     * Called when the SOS confirmation dialog opens. Starts looking for a fix so
     * the dialog can show real progress, but sends NOTHING until the user
     * confirms. Cancelling the dialog cancels this.
     */
    fun beginSosFlow() {
        locationJob?.cancel()
        _sosLocation.value = SosLocationState.Acquiring
        locationJob = viewModelScope.launch {
            when (val result = locationProvider.currentLocation()) {
                is SosLocation.Fix -> _sosLocation.value = SosLocationState.Acquired(
                    latitude = result.latitude,
                    longitude = result.longitude,
                    timestamp = result.timestamp,
                    fromCache = result.fromCache
                )

                is SosLocation.Unavailable ->
                    _sosLocation.value = SosLocationState.Failed(result.reason)
            }
        }
    }

    /** User cancelled the dialog: stop looking and send nothing. */
    fun cancelSosFlow() {
        locationJob?.cancel()
        locationJob = null
        _sosLocation.value = SosLocationState.Idle
    }

    /**
     * User confirmed. Sends the SOS with coordinates when a fix arrived, through
     * the SAME sendText path as every other message.
     */
    fun confirmSos() {
        val fix = _sosLocation.value as? SosLocationState.Acquired
        val category = _selectedCategory.value.label
        manager.sendText(
            text = "${MeshLogEntry.SOS_PREFIX} $category",
            latitude = fix?.latitude,
            longitude = fix?.longitude,
            locationTime = fix?.timestamp
        )
        cancelSosFlow()
    }

    /**
     * Explicit opt-out after a location failure. Never reached automatically —
     * an SOS is only sent without coordinates because the user chose that.
     */
    fun sendSosWithoutLocation() {
        val category = _selectedCategory.value.label
        manager.sendText("${MeshLogEntry.SOS_PREFIX} $category")
        cancelSosFlow()
    }

    companion object {
        /** Keeps a single payload comfortably inside one Nearby byte payload. */
        const val MAX_MESSAGE_LENGTH = 200
    }

    override fun onCleared() {
        super.onCleared()
        // Deliberately does NOT stop the mesh. The node is owned by the process and
        // kept alive by the foreground service, so closing the screen no longer
        // tears the network down. Stopping is an explicit user action:
        // STOP MESH NODE, or the STOP action on the persistent notification.
        locationJob?.cancel()
    }
}

/** Progress of the one-shot location read behind the SOS dialog. */
sealed interface SosLocationState {
    /** Dialog closed, nothing running. */
    data object Idle : SosLocationState

    /** Waiting for a fix. */
    data object Acquiring : SosLocationState

    data class Acquired(
        val latitude: Double,
        val longitude: Double,
        val timestamp: Long,
        val fromCache: Boolean
    ) : SosLocationState

    data class Failed(val reason: String) : SosLocationState
}

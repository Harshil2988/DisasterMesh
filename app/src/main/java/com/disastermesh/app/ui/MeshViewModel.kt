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
import com.disastermesh.app.audio.AudioMessage
import com.disastermesh.app.audio.AudioMessageConfig
import com.disastermesh.app.audio.AudioPlayer
import com.disastermesh.app.audio.AudioRecorder
import com.disastermesh.app.audio.AudioState
import com.disastermesh.app.audio.RecordingResult
import com.disastermesh.app.command.EmergencyReport
import com.disastermesh.app.command.ReportCategory
import com.disastermesh.app.command.ReportCounts
import com.disastermesh.app.command.ReportStatus
import com.disastermesh.app.location.SosLocation
import com.disastermesh.app.map.GeoPoint
import com.disastermesh.app.map.MapData
import com.disastermesh.app.map.buildMapData
import com.disastermesh.app.location.SosLocationProvider
import com.disastermesh.app.notify.NotificationPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The emergency categories offered on the dashboard. Presentation only. */
enum class MessageCategory(val label: String) {
    SAFE("SAFE"),
    MEDICAL("MEDICAL"),
    WARNING("WARNING"),
    SUPPLY("SUPPLY")
}

/** Bridges the dashboard's quick picker to the command layer's taxonomy. */
fun MessageCategory.toReportCategory(): ReportCategory = when (this) {
    MessageCategory.SAFE -> ReportCategory.SAFE
    MessageCategory.MEDICAL -> ReportCategory.MEDICAL
    MessageCategory.WARNING -> ReportCategory.WARNING
    MessageCategory.SUPPLY -> ReportCategory.SUPPLY
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

    // --- Disaster command layer -------------------------------------------
    // Reports are DERIVED from the mesh's own message log, which is only
    // appended for messages SeenMessages already accepted as new. That keeps the
    // networking layer the single authority on duplicates.

    private val reports = MeshNodeHolder.reports(application)

    val emergencyReports = reports.reports

    val reportCounts = reports.reports
        .map { ReportCounts.from(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportCounts())

    // --- Map ---------------------------------------------------------------
    // Derived from the SAME reports the Command Center uses, so there is one
    // source of truth. Node positions come from reports those nodes actually
    // sent; nothing is invented.

    private val _myLocation = mutableStateOf<GeoPoint?>(null)
    private val _locationUnavailable = mutableStateOf(false)
    val locationUnavailable: State<Boolean> = _locationUnavailable

    /** Set when Command Center asks the map to centre on a report. */
    private val _mapFocusReportId = mutableStateOf<String?>(null)
    val mapFocusReportId: State<String?> = _mapFocusReportId

    val mapData: StateFlow<MapData> = combine(
        reports.reports,
        manager.state
    ) { reportList, meshState ->
        buildMapData(
            reports = reportList,
            connectedNodeIds = meshState.connectedPeers.map { it.nodeId }.toSet(),
            localNodeId = meshState.nodeId,
            myLocation = _myLocation.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapData())

    fun focusReportOnMap(reportId: String) { _mapFocusReportId.value = reportId }

    fun clearMapFocus() { _mapFocusReportId.value = null }

    /**
     * Fetches ONE location fix for the map, on demand. Reuses the existing SOS
     * provider rather than adding a second GPS implementation, and does not
     * track continuously.
     */
    fun refreshMyLocation() {
        viewModelScope.launch {
            when (val result = locationProvider.currentLocation()) {
                is SosLocation.Fix -> {
                    _myLocation.value = GeoPoint.of(result.latitude, result.longitude)
                    _locationUnavailable.value = _myLocation.value == null
                }
                is SosLocation.Unavailable -> _locationUnavailable.value = true
            }
        }
    }

    // --- Store-carry-forward -----------------------------------------------

    private val sync = MeshNodeHolder.sync(application)

    val syncStatus = sync.status

    // --- Voice messages ----------------------------------------------------
    // Audio is a payload TYPE, not a second network: metadata rides the normal
    // envelope and the binary moves as a Nearby FILE payload.

    private val recorder = AudioRecorder(application)
    private val player = AudioPlayer()

    val playback = player.state
    val audioTransfers = sync.audioTransfers.transfers

    private val _recording = mutableStateOf(false)
    val recording: State<Boolean> = _recording

    private val _recordElapsedMs = mutableStateOf(0L)
    val recordElapsedMs: State<Long> = _recordElapsedMs

    private val _pendingAudio = mutableStateOf<RecordingResult.Success?>(null)
    val pendingAudio: State<RecordingResult.Success?> = _pendingAudio

    private val _audioError = mutableStateOf<String?>(null)
    val audioError: State<String?> = _audioError

    private var recordTicker: Job? = null

    fun dismissAudioError() { _audioError.value = null }

    /** Begins capture. The caller must already hold RECORD_AUDIO. */
    fun startRecording() {
        _audioError.value = null
        val failure = recorder.start()
        if (failure != null) {
            _audioError.value = failure
            return
        }
        _recording.value = true
        recordTicker = viewModelScope.launch {
            while (_recording.value) {
                _recordElapsedMs.value = recorder.elapsedMs()
                // The platform also enforces this, but stopping here keeps the
                // UI honest about the limit.
                if (_recordElapsedMs.value >= AudioMessageConfig.MAX_DURATION_SECONDS * 1000L) {
                    _audioError.value = "Maximum recording length reached."
                    stopRecording()
                    break
                }
                delay(200)
            }
        }
    }

    fun stopRecording() {
        if (!_recording.value) return
        _recording.value = false
        recordTicker?.cancel()
        when (val result = recorder.stop()) {
            is RecordingResult.Success -> _pendingAudio.value = result
            is RecordingResult.Failed -> _audioError.value = result.reason
        }
        _recordElapsedMs.value = 0L
    }

    fun cancelRecording() {
        _recording.value = false
        recordTicker?.cancel()
        recorder.cancel()
        _recordElapsedMs.value = 0L
        discardPendingAudio()
    }

    fun discardPendingAudio() {
        player.stop()
        _pendingAudio.value?.let { runCatching { it.file.delete() } }
        _pendingAudio.value = null
    }

    /** Plays the not-yet-sent recording. */
    fun togglePreviewPlayback() {
        val pending = _pendingAudio.value ?: return
        player.toggle(pending.audioId, pending.file)
    }

    fun togglePlayback(audioId: String) {
        val file = sync.audioStore.fileFor(audioId)
        player.toggle(audioId, file)
    }

    fun refreshPlaybackPosition() = player.refreshPosition()

    fun audioStateOf(audioId: String): AudioState = sync.audioTransfers.stateOf(audioId)

    fun retryAudio(audioId: String) = sync.audioTransfers.allowRetry(audioId)

    /**
     * Commits the recording to the mesh.
     *
     * Sends ONLY the small metadata envelope, which relays, expires and
     * synchronises like any other message. Peers that lack the audio pull the
     * binary themselves, so it never crosses a link that does not need it.
     */
    fun sendPendingAudio() {
        val pending = _pendingAudio.value ?: return
        val stored = sync.audioStore.adopt(pending.audioId, pending.file)
        if (!stored) {
            _audioError.value = "Could not save the voice message."
            return
        }
        sync.audioTransfers.markLocal(pending.audioId)

        val metadata = AudioMessage(
            audioId = pending.audioId,
            durationMs = pending.durationMs,
            sizeBytes = sync.audioStore.sizeOf(pending.audioId).toInt()
        )
        manager.sendText(metadata.encode())

        player.stop()
        _pendingAudio.value = null
    }

    // --- Hybrid uplink -----------------------------------------------------
    // Observes only. Nothing here can affect the mesh; if every uplink call
    // failed, local messaging and relay would be untouched.

    private val uplink = MeshNodeHolder.uplink(application)

    val uplinkStatus = uplink.status
    val uplinkEvents = uplink.events

    var uplinkEndpoint: String?
        get() = uplink.endpoint
        set(value) { uplink.endpoint = value }

    /** Delivery state per report id, for the Command Center. */
    val uplinkQueue = uplink.queue.entries

    /** Changes a report's status on this device. Not propagated — see report notes. */
    fun setReportStatus(reportId: String, status: ReportStatus) =
        reports.setStatus(reportId, status)

    fun clearReports() = reports.clearAll()

    /**
     * Files a structured emergency report into the mesh.
     *
     * Uses the SAME [NearbyConnectionManager.sendText] path as every other
     * message — the structure lives inside the payload string, which the network
     * treats as opaque. No protocol, relay or TTL change.
     */
    fun sendReport(
        category: ReportCategory,
        description: String,
        peopleAffected: Int?,
        attachLocation: Boolean
    ) {
        val fix = (_sosLocation.value as? SosLocationState.Acquired).takeIf { attachLocation }
        manager.sendText(
            text = EmergencyReport.buildPayload(category, description, peopleAffected),
            latitude = fix?.latitude,
            longitude = fix?.longitude,
            locationTime = fix?.timestamp
        )
    }

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
        manager.sendText(
            // Still begins with "SOS:", so the existing notifier routes it to the
            // emergency channel exactly as before. The structured block is appended.
            text = EmergencyReport.buildPayload(
                category = _selectedCategory.value.toReportCategory(),
                description = "",
                peopleAffected = null
            ),
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
        manager.sendText(
            EmergencyReport.buildPayload(
                category = _selectedCategory.value.toReportCategory(),
                description = "",
                peopleAffected = null
            )
        )
        cancelSosFlow()
    }

    companion object {
        /** Keeps a single payload comfortably inside one Nearby byte payload. */
        const val MAX_MESSAGE_LENGTH = 200
    }

    /**
     * Started last, on purpose.
     *
     * Kotlin runs property initialisers and init blocks in DECLARATION order,
     * and viewModelScope dispatches with Dispatchers.Main.immediate — so a
     * collector launched here runs synchronously and observes a StateFlow's
     * current value straight away. Placed higher up the class it reached
     * `uplink` before that property had been assigned, which crashed the app on
     * startup with a NullPointerException. Keeping this block below every
     * property guarantees each collaborator exists before collection begins.
     */
    init {
        viewModelScope.launch {
            manager.state.collect { meshState ->
                reports.ingest(meshState.messages, meshState.nodeId)
            }
        }
        viewModelScope.launch {
            // Every stored report becomes a store-and-forward candidate. The
            // queue ignores ids it already knows, so this cannot double-queue.
            reports.reports.collect { uplink.trackReports(it) }
        }
    }

    override fun onCleared() {
        recorder.cancel()
        player.stop()
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

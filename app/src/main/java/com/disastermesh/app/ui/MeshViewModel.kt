package com.disastermesh.app.ui

import android.app.Application
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.disastermesh.app.nearby.MeshLogEntry
import com.disastermesh.app.nearby.NearbyConnectionManager

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

    private val manager = NearbyConnectionManager(application)

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

    /** Advertise, discover, and auto-connect to whatever is out there. */
    fun startMesh() = manager.startMesh()

    fun stopMesh() = manager.stopMesh()

    /** Unchanged from the working build: sends the plain payload "HELLO". */
    fun sendHello() = manager.sendText("HELLO")

    /**
     * Emergency broadcast. Deliberately goes through the SAME sendText path as
     * HELLO — no new networking, no new message type. The only difference is the
     * payload text, which is marked so the UI can style it as an emergency.
     */
    fun sendSos() {
        val category = _selectedCategory.value.label
        manager.sendText("${MeshLogEntry.SOS_PREFIX} $category")
    }

    override fun onCleared() {
        super.onCleared()
        manager.stopMesh()
    }
}

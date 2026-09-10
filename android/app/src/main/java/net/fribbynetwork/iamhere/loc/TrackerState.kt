package net.fribbynetwork.iamhere.loc

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LiveState(
    val running: Boolean = false,
    val tripId: Long = 0,
    val startedAt: Long = 0,
    val destName: String? = null,
    val destRadius: Int = 0,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracy: Float? = null,
    val speed: Float? = null,
    val altitude: Double? = null,
    val satTotal: Int? = null,
    val satUsed: Int? = null,
    val distToDest: Double? = null,
    val fixAt: Long? = null,
    val sentCount: Int = 0,
    val lastResult: String? = null,
    val lastResultOk: Boolean? = null,
    val lastSmsAt: Long? = null,
    val nextSendAt: Long? = null,
    val endReason: String? = null
)

/** Stato condiviso tra service e interfaccia. */
object TrackerState {
    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state

    fun update(block: (LiveState) -> LiveState) {
        _state.value = block(_state.value)
    }

    fun reset(endReason: String?) {
        _state.value = LiveState(endReason = endReason)
    }
}

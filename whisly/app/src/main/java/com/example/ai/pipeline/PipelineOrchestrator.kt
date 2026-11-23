package com.example.ai.pipeline

import com.example.domain.models.PipelineErrorType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single, immutable orchestrator state machine for the whole voice pipeline.
 *
 * Every state is an immutable object and only this class owns the mutable holder. Screens and the
 * compatibility [PipelineStage] view observe [state]; they never set it. Illegal transitions are
 * rejected instead of silently corrupting the machine, so a later stage can never run while an
 * earlier one still owns the microphone or an unconfirmed transcript.
 */
sealed interface OrchestratorState {
    data object Idle : OrchestratorState
    data object Listening : OrchestratorState
    data object Transcribing : OrchestratorState
    data object ConfirmTranscript : OrchestratorState
    data object Translating : OrchestratorState
    data object Speaking : OrchestratorState
    data class Error(val errorType: PipelineErrorType) : OrchestratorState
}

class PipelineOrchestrator {

    private val _state = MutableStateFlow<OrchestratorState>(OrchestratorState.Idle)
    val state: StateFlow<OrchestratorState> = _state.asStateFlow()

    fun current(): OrchestratorState = _state.value

    /** Returns false when the transition was rejected; the state is unchanged in that case. */
    fun transition(to: OrchestratorState): Boolean {
        val from = _state.value
        if (from == to) return true
        if (!isAllowed(from, to)) return false
        _state.value = to
        return true
    }

    /** Cancellation is the one transition that is always legal. */
    fun cancel() {
        _state.value = OrchestratorState.Idle
    }

    private fun isAllowed(from: OrchestratorState, to: OrchestratorState): Boolean = when (from) {
        OrchestratorState.Idle -> to == OrchestratorState.Listening ||
            to == OrchestratorState.Translating ||
            to is OrchestratorState.Error
        OrchestratorState.Listening -> to == OrchestratorState.Transcribing ||
            to == OrchestratorState.Idle ||
            to is OrchestratorState.Error
        OrchestratorState.Transcribing -> to == OrchestratorState.ConfirmTranscript ||
            to == OrchestratorState.Idle ||
            to is OrchestratorState.Error
        OrchestratorState.ConfirmTranscript -> to == OrchestratorState.Translating ||
            to == OrchestratorState.Idle ||
            to is OrchestratorState.Error
        OrchestratorState.Translating -> to == OrchestratorState.Speaking ||
            to == OrchestratorState.Idle ||
            to is OrchestratorState.Error
        OrchestratorState.Speaking -> to == OrchestratorState.Idle ||
            to is OrchestratorState.Error
        is OrchestratorState.Error -> to == OrchestratorState.Idle ||
            to == OrchestratorState.Listening ||
            to == OrchestratorState.Translating ||
            to is OrchestratorState.Error
    }
}

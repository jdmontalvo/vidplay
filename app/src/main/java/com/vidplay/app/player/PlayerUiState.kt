package com.vidplay.app.player

import androidx.compose.runtime.Immutable

enum class AspectMode(val label: String) {
    FIT("Ajustar"),
    FILL("Rellenar"),
    ZOOM("Recortar"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
    ORIGINAL("Original"),
}

@Immutable
data class PlayerTrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean,
)

@Immutable
data class PlayerUiState(
    val title: String = "VidCofre",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val speed: Float = 1f,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val controlsVisible: Boolean = true,
    val locked: Boolean = false,
    val zoom: Float = 1f,
    val isVault: Boolean = false,
    val audioTracks: List<PlayerTrackOption> = emptyList(),
    val subtitleTracks: List<PlayerTrackOption> = emptyList(),
    val subtitlesEnabled: Boolean = false,
    val aspect: AspectMode = AspectMode.FIT,
    val orientationLocked: Boolean = false,
    val repeatMode: Int = 0,
    val shuffle: Boolean = false,
    val overlayMessage: String? = null,
    val seekPreview: String? = null,
    val boosting: Boolean = false,
    val boostSpeed: Float = 2f,
    val error: String? = null,
    val canOpenExternal: Boolean = true,
)

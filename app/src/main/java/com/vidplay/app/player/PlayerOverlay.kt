package com.vidplay.app.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class PlayerCallbacks(
    val onBack: () -> Unit,
    val onTogglePlay: () -> Unit,
    val onHideControls: () -> Unit,
    val onShowControls: () -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onSeekPreview: (Long) -> Unit,
    val onSeekBy: (Long) -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onToggleRepeat: () -> Unit,
    val onToggleShuffle: () -> Unit,
    val onSetSpeed: (Float) -> Unit,
    val onSetAspect: (AspectMode) -> Unit,
    val onRotate: () -> Unit,
    val onPip: () -> Unit,
    val onResetZoom: () -> Unit,
    val onSetZoom: (Float) -> Unit,
    val onToggleLock: () -> Unit,
    val onSelectAudio: (Int, Int) -> Unit,
    val onSelectSubtitle: (Int, Int) -> Unit,
    val onDisableSubtitles: () -> Unit,
    val onLoadSubtitle: () -> Unit,
    val onSleepTimer: (Int) -> Unit,
    val onRetry: () -> Unit,
    val onOpenExternal: () -> Unit,
)

private enum class PlayerDialog { SPEED, ASPECT, SLEEP, ZOOM, AUDIO, SUBTITLES }

@Composable
fun PlayerOverlay(
    state: PlayerUiState,
    callbacks: PlayerCallbacks,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<PlayerDialog?>(null) }
    var moreExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.controlsVisible, state.isPlaying, state.locked) {
        if (state.controlsVisible && state.isPlaying && !state.locked && dialog == null) {
            delay(5000)
            callbacks.onHideControls()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val navigationBarHeight = WindowInsets.navigationBars
            .asPaddingValues()
            .calculateBottomPadding()
        if (navigationBarHeight > 0.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(navigationBarHeight)
                    .background(Color.Black.copy(alpha = 0.45f)),
            )
        }

        if (state.isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                color = Color.White,
            )
        }

        if (state.locked) {
            Surface(
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
            ) {
                IconButton(onClick = callbacks.onToggleLock) {
                    Icon(Icons.Rounded.LockOpen, contentDescription = "Desbloquear controles", tint = Color.White)
                }
            }
            return@Box
        }

        AnimatedVisibility(
            visible = state.controlsVisible,
            enter = fadeIn() + slideInVertically { -it / 3 },
            exit = fadeOut() + slideOutVertically { -it / 3 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = callbacks.onBack) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.isVault) {
                        Text(
                            text = "Baúl cifrado",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                        )
                    }
                }
                if (state.zoom > 1.01f) {
                    ZoomPill(state.zoom)
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = callbacks.onResetZoom,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Rounded.ZoomOutMap, contentDescription = "Restablecer zoom", tint = Color.White)
                    }
                }
                IconButton(onClick = callbacks.onToggleLock) {
                    Icon(Icons.Rounded.Lock, contentDescription = "Bloquear controles", tint = Color.White)
                }
                Box {
                    IconButton(onClick = { moreExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Más opciones", tint = Color.White)
                    }
                    DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Imagen en imagen") },
                            leadingIcon = { Icon(Icons.Rounded.PictureInPictureAlt, null) },
                            onClick = {
                                moreExpanded = false
                                callbacks.onPip()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Ajustar zoom") },
                            leadingIcon = { Icon(Icons.Rounded.ZoomOutMap, null) },
                            onClick = {
                                moreExpanded = false
                                dialog = PlayerDialog.ZOOM
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Temporizador") },
                            leadingIcon = { Icon(Icons.Rounded.Timer, null) },
                            onClick = {
                                moreExpanded = false
                                dialog = PlayerDialog.SLEEP
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    when (state.repeatMode) {
                                        1 -> "Repetir: todo"
                                        2 -> "Repetir: uno"
                                        else -> "Repetir: desactivado"
                                    },
                                )
                            },
                            leadingIcon = { Icon(Icons.Rounded.Repeat, null) },
                            onClick = {
                                moreExpanded = false
                                callbacks.onToggleRepeat()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (state.shuffle) "Aleatorio: activado" else "Aleatorio: desactivado") },
                            leadingIcon = { Icon(Icons.Rounded.Shuffle, null) },
                            onClick = {
                                moreExpanded = false
                                callbacks.onToggleShuffle()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Cargar subtítulos") },
                            leadingIcon = { Icon(Icons.Rounded.Subtitles, null) },
                            onClick = {
                                moreExpanded = false
                                callbacks.onLoadSubtitle()
                            },
                        )
                        if (state.canOpenExternal) {
                            DropdownMenuItem(
                                text = { Text("Abrir con otra app") },
                                leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) },
                                onClick = {
                                    moreExpanded = false
                                    callbacks.onOpenExternal()
                                },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = state.controlsVisible && !state.locked,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.align(Alignment.Center),
        ) {
            val bg = Color.Black.copy(alpha = 0.45f)
            IconButton(
                onClick = callbacks.onTogglePlay,
                modifier = Modifier
                    .size(72.dp)
                    .background(bg, CircleShape),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = state.controlsVisible && !state.locked,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val scrubDuration = state.durationMs.coerceAtLeast(0L)
            var scrubbing by remember { mutableStateOf(false) }
            var scrubPosition by remember { mutableLongStateOf(0L) }
            val shownPosition = if (scrubbing) scrubPosition else state.positionMs
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                        ),
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Slider(
                    value = if (scrubDuration > 0) (shownPosition.toFloat() / scrubDuration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { value ->
                        scrubbing = true
                        val target = (value * scrubDuration).toLong().coerceAtLeast(0L)
                        scrubPosition = target
                        callbacks.onSeekPreview(target)
                    },
                    onValueChangeFinished = {
                        if (scrubDuration > 0) callbacks.onSeekTo(scrubPosition)
                        scrubbing = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(formatTime(shownPosition), color = Color.White, fontSize = 12.sp)
                    Text(
                        text = if (scrubDuration > 0) formatTime(scrubDuration) else "--:--",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlIcon(Icons.Rounded.SkipPrevious, "Anterior") { callbacks.onPrevious() }
                    ControlIcon(Icons.Rounded.Replay10, "Retroceder 10 s") { callbacks.onSeekBy(-10_000L) }
                    ControlIcon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (state.isPlaying) "Pausar" else "Reproducir",
                    ) { callbacks.onTogglePlay() }
                    ControlIcon(Icons.Rounded.Forward10, "Avanzar 10 s") { callbacks.onSeekBy(10_000L) }
                    ControlIcon(Icons.Rounded.SkipNext, "Siguiente") { callbacks.onNext() }
                    ControlIcon(Icons.Rounded.Speed, "Velocidad") { dialog = PlayerDialog.SPEED }
                    ControlIcon(Icons.Rounded.Subtitles, "Subtítulos") { dialog = PlayerDialog.SUBTITLES }
                    ControlIcon(Icons.Rounded.Audiotrack, "Audio") { dialog = PlayerDialog.AUDIO }
                    ControlIcon(Icons.Rounded.AspectRatio, "Relación de aspecto") { dialog = PlayerDialog.ASPECT }
                    ControlIcon(Icons.Rounded.ScreenRotation, "Rotar") { callbacks.onRotate() }
                    ControlIcon(Icons.Rounded.PictureInPictureAlt, "Imagen en imagen") { callbacks.onPip() }
                }
            }
        }

        if (state.boosting) {
            OverlayPill(
                text = "${formatSpeed(state.boostSpeed)} ▶▶",
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp),
            )
        }
        state.seekPreview?.let { preview ->
            OverlayPill(
                text = preview,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp),
            )
        }
        state.overlayMessage?.let { message ->
            OverlayPill(
                text = message,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    when (dialog) {
        PlayerDialog.SPEED -> SpeedDialog(state.speed, onDismiss = { dialog = null }) {
            callbacks.onSetSpeed(it)
            dialog = null
        }
        PlayerDialog.ASPECT -> AspectDialog(state.aspect, onDismiss = { dialog = null }) {
            callbacks.onSetAspect(it)
            dialog = null
        }
        PlayerDialog.SLEEP -> SleepDialog(onDismiss = { dialog = null }) {
            callbacks.onSleepTimer(it)
            dialog = null
        }
        PlayerDialog.ZOOM -> ZoomDialog(
            state.zoom,
            onDismiss = { dialog = null },
            onZoom = callbacks.onSetZoom,
            onReset = callbacks.onResetZoom,
        )
        PlayerDialog.AUDIO -> TrackDialog(
            title = "Pista de audio",
            tracks = state.audioTracks,
            emptyText = "El vídeo no tiene pistas de audio",
            onDismiss = { dialog = null },
            onSelect = { group, track ->
                callbacks.onSelectAudio(group, track)
                dialog = null
            },
        )
        PlayerDialog.SUBTITLES -> SubtitleDialog(
            tracks = state.subtitleTracks,
            enabled = state.subtitlesEnabled,
            onDismiss = { dialog = null },
            onDisable = {
                callbacks.onDisableSubtitles()
                dialog = null
            },
            onSelect = { group, track ->
                callbacks.onSelectSubtitle(group, track)
                dialog = null
            },
            onLoad = {
                dialog = null
                callbacks.onLoadSubtitle()
            },
        )
        null -> Unit
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = { callbacks.onRetry() },
            title = { Text("No se pudo reproducir") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = callbacks.onRetry) { Text("Reintentar") }
            },
            dismissButton = {
                Row {
                    if (state.canOpenExternal) {
                        TextButton(onClick = callbacks.onOpenExternal) { Text("Abrir con otra app") }
                    }
                    TextButton(onClick = callbacks.onBack) { Text("Cerrar") }
                }
            },
        )
    }
}

@Composable
private fun ControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun ZoomPill(zoom: Float) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.55f),
    ) {
        Text(
            text = String.format("%.1fx", zoom),
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun OverlayPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.72f),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun SpeedDialog(current: Float, onDismiss: () -> Unit, onSelect: (Float) -> Unit) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Velocidad") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                speeds.forEach { speed ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = kotlin.math.abs(current - speed) < 0.01f, onClick = { onSelect(speed) })
                        Text(formatSpeed(speed), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun AspectDialog(current: AspectMode, onDismiss: () -> Unit, onSelect: (AspectMode) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Relación de aspecto") },
        text = {
            Column {
                AspectMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = current == mode, onClick = { onSelect(mode) })
                        Text(mode.label, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun SleepDialog(onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Temporizador") },
        text = {
            Column {
                listOf(0, 15, 30, 60, 90).forEach { minutes ->
                    TextButton(
                        onClick = { onSelect(minutes) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (minutes == 0) "Desactivado" else "$minutes minutos",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun ZoomDialog(
    current: Float,
    onDismiss: () -> Unit,
    onZoom: (Float) -> Unit,
    onReset: () -> Unit,
) {
    var value by remember(current) { mutableFloatStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zoom") },
        text = {
            Column {
                Text("Pellizca sobre el vídeo o usa el control para acercar y arrastrar para moverte.")
                Spacer(Modifier.height(12.dp))
                Text("${String.format("%.1f", value)}x", fontWeight = FontWeight.SemiBold)
                Slider(
                    value = value,
                    onValueChange = {
                        value = it
                        onZoom(it)
                    },
                    valueRange = 1f..5f,
                )
                TextButton(onClick = onReset) { Text("Restablecer zoom") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun TrackDialog(
    title: String,
    tracks: List<PlayerTrackOption>,
    emptyText: String,
    onDismiss: () -> Unit,
    onSelect: (Int, Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (tracks.isEmpty()) {
                    Text(emptyText)
                } else {
                    tracks.forEach { track ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = track.selected,
                                onClick = { onSelect(track.groupIndex, track.trackIndex) },
                            )
                            Text(track.label, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun SubtitleDialog(
    tracks: List<PlayerTrackOption>,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onDisable: () -> Unit,
    onSelect: (Int, Int) -> Unit,
    onLoad: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtítulos") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = !enabled, onClick = onDisable)
                    Text("Desactivados", modifier = Modifier.padding(start = 6.dp))
                }
                tracks.forEach { track ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = track.selected,
                            onClick = { onSelect(track.groupIndex, track.trackIndex) },
                        )
                        Text(track.label, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                TextButton(onClick = onLoad, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Subtitles, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cargar archivo de subtítulos")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun formatSpeed(speed: Float): String {
    return if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"
}

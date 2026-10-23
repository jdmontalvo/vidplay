package com.vidplay.app.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import com.google.common.util.concurrent.ListenableFuture
import com.vidplay.app.R
import com.vidplay.app.VidPlayApp
import com.vidplay.app.data.settings.AppSettings
import com.vidplay.app.databinding.ActivityPlayerBinding
import com.vidplay.app.ui.theme.VidPlayTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException

class PlayerActivity : FragmentActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private val app by lazy { application as VidPlayApp }
    private val uiState = MutableStateFlow(PlayerUiState())
    private var settings = AppSettings()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var playerListener: Player.Listener? = null
    private var pendingLaunch: LaunchSpec? = null
    private var titleMap: MutableMap<String, String> = emptyMap<String, String>().toMutableMap()
    private var overlayJob: Job? = null
    private var sleepJob: Job? = null
    private var settingsJob: Job? = null
    private var tickerJob: Job? = null
    private var currentAspect = AspectMode.FIT
    private var orientationLocked = false
    private var savedPosition = false

    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { attachSubtitle(it) }
    }

    private val callbacks = PlayerCallbacks(
        onBack = { finish() },
        onTogglePlay = { togglePlay() },
        onHideControls = { uiState.update { it.copy(controlsVisible = false) } },
        onShowControls = { uiState.update { it.copy(controlsVisible = true) } },
        onSeekTo = { position -> controller?.seekTo(position) },
        onSeekPreview = { position -> previewSeek(position) },
        onSeekBy = { delta -> seekBy(delta) },
        onPrevious = {
            controller?.seekToPreviousMediaItem()
            flashOverlay("Anterior")
        },
        onNext = {
            controller?.seekToNextMediaItem()
            flashOverlay("Siguiente")
        },
        onToggleRepeat = { toggleRepeat() },
        onToggleShuffle = { toggleShuffle() },
        onSetSpeed = { speed -> setSpeed(speed) },
        onSetAspect = { mode -> setAspect(mode) },
        onRotate = { toggleOrientation() },
        onPip = { enterPip() },
        onResetZoom = {
            binding.playerContainer.resetZoom()
            uiState.update { it.copy(zoom = 1f) }
        },
        onSetZoom = { zoom ->
            binding.playerContainer.setZoom(zoom)
            uiState.update { it.copy(zoom = zoom) }
        },
        onToggleLock = { toggleLock() },
        onSelectAudio = { group, track -> selectTrack(C.TRACK_TYPE_AUDIO, group, track) },
        onSelectSubtitle = { group, track -> selectTrack(C.TRACK_TYPE_TEXT, group, track) },
        onDisableSubtitles = { disableSubtitles() },
        onLoadSubtitle = { subtitlePicker.launch(SUBTITLE_MIME_TYPES) },
        onSleepTimer = { minutes -> setSleepTimer(minutes) },
        onRetry = { retry() },
        onOpenExternal = { openExternal() },
    )

    private val gestureListener: ZoomablePlayerLayout.Listener by lazy {
        object : ZoomablePlayerLayout.Listener {
            override fun onToggleControls() {
                uiState.update { it.copy(controlsVisible = !it.controlsVisible) }
            }

            override fun onTogglePlay() {
                togglePlay()
            }

            override fun onSeekBack() {
                seekBy(-settings.seekBackMs)
            }

            override fun onSeekForward() {
                seekBy(settings.seekForwardMs)
            }

            override fun onSeekPreview(deltaMs: Long) {
                if (!settings.gestureSeek) return
                val c = controller ?: return
                val duration = c.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
                val target = (c.currentPosition + deltaMs).coerceIn(0L, duration.coerceAtLeast(0L))
                uiState.update {
                    it.copy(seekPreview = "${formatTime(target)} / ${formatTime(duration)}")
                }
            }

            override fun onSeekCommit(deltaMs: Long) {
                if (!settings.gestureSeek) return
                val c = controller ?: return
                val duration = c.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
                val target = (c.currentPosition + deltaMs).coerceIn(0L, duration.coerceAtLeast(0L))
                c.seekTo(target)
                uiState.update { it.copy(seekPreview = null) }
                flashOverlay(if (deltaMs >= 0) "+${formatTime(target)}" else "-${formatTime(target)}")
            }

            override fun onBoostStart() {
                val c = controller ?: return
                if (!c.isPlaying) return
                uiState.update { it.copy(boosting = true, boostSpeed = settings.boostSpeed) }
                c.playbackParameters = PlaybackParameters(settings.boostSpeed, 1f)
            }

            override fun onBoostEnd() {
                val c = controller ?: return
                uiState.update { it.copy(boosting = false) }
                c.playbackParameters = PlaybackParameters(uiState.value.speed.coerceAtLeast(0.25f), 1f)
            }

            override fun onBrightnessDelta(delta: Float) {
                if (!settings.gestureBrightness) return
                val attributes = window.attributes
                val current = if (attributes.screenBrightness < 0f) 0.5f else attributes.screenBrightness
                val next = (current + delta).coerceIn(0.05f, 1f)
                attributes.screenBrightness = next
                window.attributes = attributes
                flashOverlay("Brillo ${(next * 100).toInt()}%")
            }

            override fun onVolumeDelta(delta: Float) {
                if (!settings.gestureVolume) return
                val audio = getSystemService(AudioManager::class.java)
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                val next = (current + delta * max).toInt().coerceIn(0, max)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                flashOverlay("Volumen ${(next * 100 / max.coerceAtLeast(1))}%")
            }

            override fun onZoomChanged(scale: Float) {
                uiState.update { it.copy(zoom = scale) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.playerContainer.listener = gestureListener
        binding.playerContainer.maxZoom = settings.maxZoom

        binding.controlsOverlay.setContent {
            VidPlayTheme {
                val state by uiState.collectAsStateWithLifecycle()
                PlayerOverlay(state = state, callbacks = callbacks)
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            if (uiState.value.locked) {
                uiState.update { it.copy(locked = false, controlsVisible = true) }
                binding.playerContainer.listener = gestureListener
            } else {
                finish()
            }
        }

        settingsJob = lifecycleScope.launch {
            app.container.settingsRepository.settings.collectLatest { value ->
                settings = value
                binding.playerContainer.maxZoom = value.maxZoom
                uiState.update {
                    it.copy(
                        boostSpeed = value.boostSpeed,
                        canOpenExternal = !it.isVault,
                    )
                }
            }
        }

        tickerJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    tickPosition()
                    delay(250)
                }
            }
        }

        parseIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        connectController()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        savePosition()
        if (!isInPictureInPictureMode && !settings.backgroundPlay) {
            controller?.pause()
        }
    }

    override fun onDestroy() {
        savePosition()
        playerListener?.let { listener -> controller?.removeListener(listener) }
        playerListener = null
        controller?.release()
        controller = null
        controllerFuture?.let {
            if (!it.isDone) MediaController.releaseFuture(it)
        }
        controllerFuture = null
        overlayJob?.cancel()
        sleepJob?.cancel()
        settingsJob?.cancel()
        tickerJob?.cancel()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.playerContainer.resetZoom(animated = false)
        uiState.update { it.copy(zoom = 1f, controlsVisible = true) }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        uiState.update { it.copy(controlsVisible = !isInPictureInPictureMode) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val state = uiState.value
        if (settings.backgroundPlay && state.isPlaying &&
            (!state.isVault || settings.allowVaultPip) &&
            PipActionReceiver.hasPip()
        ) {
            enterPip()
        }
    }

    private fun connectController() {
        if (controller != null) {
            applyLaunchSpec()
            return
        }
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            if (isDestroyed) {
                MediaController.releaseFuture(future)
                return@addListener
            }
            try {
                val c = future.get()
                controller = c
                binding.playerView.player = c
                attachPlayerListener(c)
                updateState(c)
                applyLaunchSpec()
            } catch (_: ExecutionException) {
                toast("No se pudo abrir el reproductor")
                finish()
            } catch (_: InterruptedException) {
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun attachPlayerListener(c: MediaController) {
        playerListener?.let { c.removeListener(it) }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                updateState(player)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else if (!settings.keepScreenOn) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                updateState(c)
                updatePipParams(c)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                binding.playerContainer.resetZoom(animated = false)
                uiState.update { it.copy(zoom = 1f) }
                updateState(c)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                uiState.update {
                    it.copy(videoWidth = videoSize.width, videoHeight = videoSize.height)
                }
                updatePipParams(c)
            }

            override fun onPlayerError(error: PlaybackException) {
                uiState.update { it.copy(error = friendlyError(error)) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateState(c)
            }
        }
        playerListener = listener
        c.addListener(listener)
    }

    private fun parseIntent(intent: Intent?) {
        if (intent == null) return
        val expectedToken = app.container.playerLaunchToken
        val spec = when {
            intent.action == Intent.ACTION_VIEW && intent.data != null -> {
                val uri = intent.data!!
                // External ACTION_VIEW may only open ordinary content/file videos.
                // Vault playback is an internal capability and is never accepted here.
                if (uri.scheme != "content" && uri.scheme != "file") return
                val name = app.container.mediaStoreRepository.queryOpenableMetadata(uri)?.first
                    ?: uri.lastPathSegment
                    ?: "Vídeo"
                LaunchSpec(
                    uris = listOf(uri),
                    titles = listOf(name),
                    mimeTypes = listOf(intent.type ?: "video/*"),
                    startIndex = 0,
                    startPosition = intent.getLongExtra(EXTRA_START_POSITION, 0L),
                )
            }
            else -> {
                // Custom playlists (including vault:// URIs) must present the
                // per-process token. Other apps cannot forge a vault playback.
                if (intent.getStringExtra(EXTRA_LAUNCH_TOKEN) != expectedToken) return
                val rawUris = intent.getStringArrayListExtra(EXTRA_URIS) ?: return
                val uris = rawUris.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
                if (uris.isEmpty()) return
                val titles = intent.getStringArrayListExtra(EXTRA_TITLES) ?: emptyList()
                LaunchSpec(
                    uris = uris,
                    titles = titles.ifEmpty { uris.map { "Vídeo" } },
                    mimeTypes = intent.getStringArrayListExtra(EXTRA_MIME_TYPES) ?: emptyList(),
                    startIndex = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, uris.lastIndex),
                    startPosition = intent.getLongExtra(EXTRA_START_POSITION, 0L),
                )
            }
        }
        pendingLaunch = spec
        titleMap = spec.uris.mapIndexed { index, uri ->
            uri.toString() to spec.titles.getOrElse(index) { "Vídeo" }
        }.toMap().toMutableMap()
        if (controller != null) applyLaunchSpec()
    }

    private fun applyLaunchSpec() {
        val spec = pendingLaunch ?: return
        val c = controller ?: return
        pendingLaunch = null
        if (spec.uris.any { it.scheme == "vault" }) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        lifecycleScope.launch {
            val items = spec.uris.mapIndexed { index, uri ->
                val title = spec.titles.getOrElse(index) { "Vídeo" }
                val vault = uri.scheme == "vault"
                val notificationTitle = if (vault && settings.hideVaultNotification) {
                    "Vídeo protegido"
                } else {
                    title
                }
                MediaItem.Builder()
                    .setUri(uri)
                    .apply {
                        if (vault) {
                            setMimeType(
                                spec.mimeTypes.getOrElse(index) { "video/mp4" }
                                    .takeIf { it.startsWith("video/") } ?: "video/mp4",
                            )
                        }
                    }
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(notificationTitle)
                            .build(),
                    )
                    .build()
            }
            var start = spec.startPosition
            if (start <= 0L && settings.autoResume) {
                start = loadResumePosition(spec.uris[spec.startIndex])
            }
            c.setMediaItems(items, spec.startIndex, start.coerceAtLeast(0L))
            c.prepare()
            c.play()
            uiState.update {
                it.copy(
                    title = spec.titles.getOrElse(spec.startIndex) { "VidPlay" },
                    isVault = spec.uris[spec.startIndex].scheme == "vault",
                    controlsVisible = true,
                )
            }
        }
    }

    private suspend fun loadResumePosition(uri: Uri): Long {
        return try {
            if (uri.scheme == "vault") {
                val id = app.container.vaultRepository.idFromUri(uri) ?: return 0L
                app.container.vaultRepository.item(id)?.lastPositionMs ?: 0L
            } else {
                app.container.database.playbackPositionDao().get(uri.toString())?.positionMs ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun updateState(player: Player) {
        val current = player.currentMediaItem
        val uri = current?.localConfiguration?.uri
        val realTitle = uri?.let { titleMap[it.toString()] }
            ?: current?.mediaMetadata?.title?.toString()
            ?: "VidPlay"
        val duration = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
        val audio = buildTrackOptions(player.currentTracks, C.TRACK_TYPE_AUDIO)
        val subtitles = buildTrackOptions(player.currentTracks, C.TRACK_TYPE_TEXT)
        val isVault = uri?.scheme == "vault"
        if (isVault) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        uiState.update {
            it.copy(
                title = realTitle,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
                speed = player.playbackParameters.speed,
                audioTracks = audio,
                subtitleTracks = subtitles,
                subtitlesEnabled = subtitles.any { track -> track.selected },
                isVault = isVault,
                canOpenExternal = !isVault,
                repeatMode = player.repeatMode,
                shuffle = player.shuffleModeEnabled,
                boostSpeed = settings.boostSpeed,
            )
        }
    }

    private fun buildTrackOptions(
        tracks: androidx.media3.common.Tracks,
        type: Int,
    ): List<PlayerTrackOption> {
        val result = mutableListOf<PlayerTrackOption>()
        var groupIndex = 0
        tracks.groups.forEach { group ->
            if (group.type != type || !group.isSupported) return@forEach
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                val parts = buildList {
                    format.label?.takeIf { it.isNotBlank() }?.let { add(it) }
                    format.language?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
                    format.codecs?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
                result += PlayerTrackOption(
                    groupIndex = groupIndex,
                    trackIndex = i,
                    label = parts.joinToString(" · ").ifBlank {
                        if (type == C.TRACK_TYPE_AUDIO) "Audio ${i + 1}" else "Subtítulo ${i + 1}"
                    },
                    selected = group.isTrackSelected(i),
                )
            }
            groupIndex++
        }
        return result
    }

    private fun selectTrack(type: Int, groupIndex: Int, trackIndex: Int) {
        val c = controller ?: return
        val groups = c.currentTracks.groups.filter { it.type == type && it.isSupported }
        val group = groups.getOrNull(groupIndex) ?: return
        val params = c.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
        c.trackSelectionParameters = params
    }

    private fun disableSubtitles() {
        val c = controller ?: return
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
    }

    private fun attachSubtitle(uri: Uri) {
        val c = controller ?: return
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Non-persistable provider; current process grant is enough for playback.
        }
        val mime = contentResolver.getType(uri) ?: guessSubtitleMime(uri)
        val current = c.currentMediaItem ?: return
        val config = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mime)
            .setLanguage("und")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val existing = current.localConfiguration?.subtitleConfigurations.orEmpty()
        val updated = current.buildUpon()
            .setSubtitleConfigurations(existing + config)
            .build()
        val index = c.currentMediaItemIndex
        val position = c.currentPosition
        c.replaceMediaItem(index, updated)
        c.prepare()
        c.seekTo(index, position)
        c.play()
        toast("Subtítulos añadidos")
    }

    private fun guessSubtitleMime(uri: Uri): String {
        return when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
            "vtt" -> "text/vtt"
            "ass", "ssa" -> "text/x-ssa"
            "ttml", "xml" -> "application/ttml+xml"
            else -> "application/x-subrip"
        }
    }

    private fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
        uiState.update { it.copy(controlsVisible = true) }
    }

    private fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val duration = c.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
        val target = (c.currentPosition + deltaMs).coerceIn(0L, duration)
        c.seekTo(target)
        flashOverlay(if (deltaMs >= 0) "+${deltaMs / 1000} s" else "-${-deltaMs / 1000} s")
    }

    private fun previewSeek(positionMs: Long) {
        val c = controller ?: return
        val duration = c.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
        uiState.update {
            it.copy(seekPreview = "${formatTime(positionMs)} / ${formatTime(duration)}")
        }
    }

    private fun setSpeed(speed: Float) {
        controller?.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 4f), 1f)
        uiState.update { it.copy(speed = speed) }
        flashOverlay(formatSpeed(speed))
    }

    private fun setAspect(mode: AspectMode) {
        currentAspect = mode
        when (mode) {
            AspectMode.FIT -> binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectMode.FILL -> binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectMode.ZOOM -> binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectMode.RATIO_16_9 -> {
                binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                setPlayerAspectRatio(16f / 9f)
            }
            AspectMode.RATIO_4_3 -> {
                binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                setPlayerAspectRatio(4f / 3f)
            }
            AspectMode.ORIGINAL -> binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        uiState.update { it.copy(aspect = mode) }
        flashOverlay(mode.label)
    }

    private fun setPlayerAspectRatio(ratio: Float) {
        findAspectRatioFrame(binding.playerView)?.setAspectRatio(ratio)
    }

    private fun findAspectRatioFrame(view: android.view.View): AspectRatioFrameLayout? {
        if (view is AspectRatioFrameLayout) return view
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) {
                findAspectRatioFrame(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    private fun toggleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        uiState.update { it.copy(repeatMode = c.repeatMode) }
        flashOverlay(
            when (c.repeatMode) {
                Player.REPEAT_MODE_ALL -> "Repetir todo"
                Player.REPEAT_MODE_ONE -> "Repetir uno"
                else -> "Repetir desactivado"
            },
        )
    }

    private fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        uiState.update { it.copy(shuffle = c.shuffleModeEnabled) }
        flashOverlay(if (c.shuffleModeEnabled) "Aleatorio activado" else "Aleatorio desactivado")
    }

    private fun toggleOrientation() {
        orientationLocked = !orientationLocked
        requestedOrientation = if (orientationLocked) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        uiState.update { it.copy(orientationLocked = orientationLocked) }
        flashOverlay(if (orientationLocked) "Horizontal" else "Rotación automática")
    }

    private fun toggleLock() {
        val locked = !uiState.value.locked
        uiState.update { it.copy(locked = locked, controlsVisible = !locked) }
        binding.playerContainer.listener = if (locked) null else gestureListener
        if (locked) {
            flashOverlay("Controles bloqueados")
        }
    }

    private fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            flashOverlay("Temporizador desactivado")
            return
        }
        flashOverlay("Pausa en $minutes min")
        sleepJob = lifecycleScope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            flashOverlay("Reproducción pausada")
        }
    }

    private fun retry() {
        val c = controller ?: return
        uiState.update { it.copy(error = null) }
        c.prepare()
    }

    private fun openExternal() {
        val c = controller ?: return
        val uri = c.currentMediaItem?.localConfiguration?.uri ?: return
        if (uri.scheme == "vault") {
            toast("Exporta el vídeo desde el baúl para abrirlo con otra app")
            return
        }
        val mime = app.container.mediaStoreRepository.mimeType(uri)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(Intent.createChooser(intent, "Abrir con"))
        } catch (_: Exception) {
            toast("No hay otra app para reproducir este vídeo")
        }
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            toast("Imagen en imagen requiere Android 8 o superior")
            return
        }
        val state = uiState.value
        if (state.isVault && !settings.allowVaultPip) {
            toast("Imagen en imagen está desactivada para el baúl")
            return
        }
        try {
            enterPictureInPictureMode(buildPipParams(controller))
        } catch (_: Exception) {
            toast("No se pudo activar imagen en imagen")
        }
    }

    private fun updatePipParams(c: MediaController) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (isFinishing || isDestroyed) return
        try {
            setPictureInPictureParams(buildPipParams(c))
        } catch (_: Exception) {
            // Ignore while the activity is not ready.
        }
    }

    private fun buildPipParams(c: MediaController?): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        val width = uiState.value.videoWidth
        val height = uiState.value.videoHeight
        if (width > 0 && height > 0) {
            builder.setAspectRatio(Rational(width, height))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && c != null) {
            val playPauseIcon = if (c.isPlaying) R.drawable.ic_pip_pause else R.drawable.ic_pip_play
            val playPauseLabel = if (c.isPlaying) "Pausar" else "Reproducir"
            builder.setActions(
                listOf(
                    RemoteAction(
                        Icon.createWithResource(this, R.drawable.ic_pip_rewind),
                        "Retroceder 10 s",
                        "Retroceder 10 s",
                        PipActionReceiver.pendingIntent(this, PipActionReceiver.ACTION_REWIND, 1),
                    ),
                    RemoteAction(
                        Icon.createWithResource(this, playPauseIcon),
                        playPauseLabel,
                        playPauseLabel,
                        PipActionReceiver.pendingIntent(this, PipActionReceiver.ACTION_PLAY_PAUSE, 2),
                    ),
                    RemoteAction(
                        Icon.createWithResource(this, R.drawable.ic_pip_forward),
                        "Avanzar 10 s",
                        "Avanzar 10 s",
                        PipActionReceiver.pendingIntent(this, PipActionReceiver.ACTION_FORWARD, 3),
                    ),
                ),
            )
        }
        return builder.build()
    }

    private fun savePosition() {
        val c = controller ?: return
        val uri = c.currentMediaItem?.localConfiguration?.uri ?: return
        val position = c.currentPosition.coerceAtLeast(0L)
        val duration = c.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
        if (position <= 0L || (duration > 0 && position > duration - 3000L)) return
        lifecycleScope.launch {
            try {
                if (uri.scheme == "vault") {
                    val id = app.container.vaultRepository.idFromUri(uri) ?: return@launch
                    app.container.vaultRepository.updatePosition(id, position)
                } else {
                    app.container.database.playbackPositionDao().upsert(
                        com.vidplay.app.data.db.PlaybackPositionEntity(
                            mediaKey = uri.toString(),
                            positionMs = position,
                            durationMs = duration,
                        ),
                    )
                }
            } catch (_: Exception) {
                // Best effort.
            }
        }
        savedPosition = true
    }

    private fun tickPosition() {
        val c = controller ?: return
        if (c.playbackState == Player.STATE_IDLE) return
        val duration = c.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
        uiState.update {
            it.copy(
                positionMs = c.currentPosition.coerceAtLeast(0L),
                bufferedMs = c.bufferedPosition.coerceAtLeast(0L),
                durationMs = duration,
            )
        }
    }

    private fun flashOverlay(message: String) {
        overlayJob?.cancel()
        uiState.update { it.copy(overlayMessage = message) }
        overlayJob = lifecycleScope.launch {
            delay(900)
            uiState.update { it.copy(overlayMessage = null) }
        }
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun friendlyError(error: PlaybackException): String {
        val base = getString(R.string.player_error_generic)
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            -> "$base\n\nCódigo: ${error.errorCodeName}"
            else -> "$base\n\n${error.errorCodeName}: ${error.message ?: ""}".trim()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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

    data class LaunchSpec(
        val uris: List<Uri>,
        val titles: List<String>,
        val mimeTypes: List<String>,
        val startIndex: Int,
        val startPosition: Long,
    )

    companion object {
        const val EXTRA_URIS = "com.vidplay.app.extra.URIS"
        const val EXTRA_TITLES = "com.vidplay.app.extra.TITLES"
        const val EXTRA_MIME_TYPES = "com.vidplay.app.extra.MIME_TYPES"
        const val EXTRA_INDEX = "com.vidplay.app.extra.INDEX"
        const val EXTRA_START_POSITION = "com.vidplay.app.extra.START_POSITION"
        const val EXTRA_LAUNCH_TOKEN = "com.vidplay.app.extra.LAUNCH_TOKEN"

        private val SUBTITLE_MIME_TYPES = arrayOf(
            "text/*",
            "application/x-subrip",
            "application/x-ssa",
            "application/x-ass",
            "application/ttml+xml",
        )

        fun intent(
            context: android.content.Context,
            uris: List<Uri>,
            titles: List<String>,
            index: Int = 0,
            startPosition: Long = 0L,
            mimeTypes: List<String> = emptyList(),
            launchToken: String = "",
        ): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_URIS, ArrayList(uris.map { it.toString() }))
                putStringArrayListExtra(EXTRA_TITLES, ArrayList(titles))
                putStringArrayListExtra(EXTRA_MIME_TYPES, ArrayList(mimeTypes))
                putExtra(EXTRA_INDEX, index)
                putExtra(EXTRA_START_POSITION, startPosition)
                putExtra(EXTRA_LAUNCH_TOKEN, launchToken)
            }
        }
    }
}

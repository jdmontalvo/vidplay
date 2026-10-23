package com.vidplay.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.vidplay.app.R
import com.vidplay.app.VidPlayApp
import com.vidplay.app.data.vault.VaultDataSource
import com.vidplay.app.util.Notifications
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var app: VidPlayApp

    override fun onCreate() {
        super.onCreate()
        app = applicationContext as VidPlayApp
        val vaultDataSourceFactory = VaultDataSource.Factory(app.container.vaultRepository)
        val defaultDataSourceFactory = DefaultDataSource.Factory(this, vaultDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(defaultDataSourceFactory)

        val renderersFactory = NextRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateVaultPlaybackFlag()
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                updateVaultPlaybackFlag()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateVaultPlaybackFlag()
            }
        })

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    300,
                    Intent(this, PlayerActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(Notifications.CHANNEL_PLAYBACK)
                .build(),
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (player.playbackState == Player.STATE_IDLE || !player.isPlaying) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        app.container.vaultRepository.session.playbackActive = false
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun updateVaultPlaybackFlag() {
        val current = player.currentMediaItem
        val isVault = current?.localConfiguration?.uri?.scheme == "vault"
        // Keep the key alive while a vault item is loaded, including a pause, so
        // resuming from the notification does not fail after auto-lock.
        app.container.vaultRepository.session.playbackActive =
            isVault &&
            player.playbackState != Player.STATE_IDLE &&
            player.playbackState != Player.STATE_ENDED
    }
}

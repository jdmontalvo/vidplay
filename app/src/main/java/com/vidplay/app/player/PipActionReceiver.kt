package com.vidplay.app.player

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

class PipActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val controller = future.get()
                when (action) {
                    ACTION_PLAY_PAUSE -> if (controller.isPlaying) controller.pause() else controller.play()
                    ACTION_REWIND -> controller.seekTo(
                        (controller.currentPosition - 10_000L).coerceAtLeast(0L),
                    )
                    ACTION_FORWARD -> controller.seekTo(
                        (controller.currentPosition + 10_000L).coerceAtMost(
                            controller.duration.coerceAtLeast(0L),
                        ),
                    )
                }
                controller.release()
            } catch (_: Exception) {
                // The service may be gone.
            } finally {
                pending.finish()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.vidplay.app.PIP_PLAY_PAUSE"
        const val ACTION_REWIND = "com.vidplay.app.PIP_REWIND"
        const val ACTION_FORWARD = "com.vidplay.app.PIP_FORWARD"

        fun pendingIntent(context: Context, action: String, requestCode: Int) =
            android.app.PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, PipActionReceiver::class.java).setAction(action),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )

        fun hasPip(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }
}

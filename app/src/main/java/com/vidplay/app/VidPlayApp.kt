package com.vidplay.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.Room
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.vidplay.app.data.db.AppDatabase
import com.vidplay.app.data.media.MediaStoreRepository
import com.vidplay.app.data.settings.SettingsRepository
import com.vidplay.app.data.vault.VaultRepository
import com.vidplay.app.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val database: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "vidplay.db",
    ).fallbackToDestructiveMigration(dropAllTables = true).build()

    val settingsRepository = SettingsRepository(context)
    val mediaStoreRepository = MediaStoreRepository(context)
    val vaultRepository = VaultRepository(context, database, settingsRepository)
}

class VidPlayApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var autoLockSeconds = 60
    private var autoLockJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)

        appScope.launch {
            container.settingsRepository.settings.collect { settings ->
                autoLockSeconds = settings.vaultAutoLockSeconds
            }
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                autoLockJob?.cancel()
            }

            override fun onStop(owner: LifecycleOwner) {
                if (!container.vaultRepository.isUnlocked()) return
                autoLockJob?.cancel()
                autoLockJob = appScope.launch {
                    delay(autoLockSeconds.coerceAtLeast(30) * 1000L)
                    if (!container.vaultRepository.session.playbackActive) {
                        container.vaultRepository.lock()
                    }
                }
            }
        })
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
}

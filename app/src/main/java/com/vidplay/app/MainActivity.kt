package com.vidplay.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vidplay.app.ui.folders.FoldersScreen
import com.vidplay.app.ui.library.LibraryScreen
import com.vidplay.app.ui.settings.SettingsScreen
import com.vidplay.app.ui.theme.VidPlayTheme
import com.vidplay.app.data.vault.VaultWorkScheduler
import com.vidplay.app.player.PlayerActivity
import com.vidplay.app.ui.vault.VaultScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent {
            VidPlayTheme {
                MainScreen()
            }
        }
        if (savedInstanceState == null) {
            handleDebugIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDebugIntent(intent)
    }

    /**
     * Debug-only automation hook. It is compiled out of release behavior by the
     * BuildConfig.DEBUG check and is useful for hardware validation where ADB
     * input injection is blocked by the OEM.
     */
    private fun handleDebugIntent(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        val pin = intent.getStringExtra("debug_vault_pin") ?: return
        val app = application as VidPlayApp
        val vault = app.container.vaultRepository
        lifecycleScope.launch {
            if (!vault.hasVault()) {
                vault.setup(pin)
            } else {
                vault.unlockWithPin(pin)
            }
            val importUri = intent.data
            if (importUri != null) {
                val metadata = app.container.mediaStoreRepository.queryOpenableMetadata(importUri)
                app.container.mediaStoreRepository.takePersistableRead(importUri)
                VaultWorkScheduler.enqueueImport(
                    context = this@MainActivity,
                    uri = importUri,
                    displayName = metadata?.first ?: "Prueba",
                    mimeType = app.container.mediaStoreRepository.mimeType(importUri),
                    size = metadata?.second ?: -1L,
                    deleteOriginal = intent.getBooleanExtra("debug_delete_original", false),
                )
            }
            if (intent.getBooleanExtra("debug_play_latest_vault", false)) {
                val latest = vault.observeItems().first().firstOrNull()
                if (latest != null) {
                    startActivity(
                        PlayerActivity.intent(
                            context = this@MainActivity,
                            uris = listOf(vault.vaultUri(latest.id)),
                            titles = listOf(latest.displayName),
                            index = 0,
                            startPosition = latest.lastPositionMs,
                            mimeTypes = listOf(latest.mimeType),
                            launchToken = app.container.playerLaunchToken,
                        ),
                    )
                }
            }
        }
    }
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val destinations = listOf(
        BottomDestination("videos", "Vídeos", Icons.Rounded.VideoLibrary),
        BottomDestination("folders", "Carpetas", Icons.Rounded.Folder),
        BottomDestination("vault", "Baúl", Icons.Rounded.Lock),
        BottomDestination("settings", "Ajustes", Icons.Rounded.Settings),
    )

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun navigate(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                destinations.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any {
                        it.route == destination.route
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigate(destination.route) },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "videos",
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            composable("videos") {
                LibraryScreen(onOpenVault = { navigate("vault") })
            }
            composable("folders") {
                FoldersScreen(onOpenVault = { navigate("vault") })
            }
            composable("vault") {
                VaultScreen()
            }
            composable("settings") {
                SettingsScreen()
            }
        }
    }
}

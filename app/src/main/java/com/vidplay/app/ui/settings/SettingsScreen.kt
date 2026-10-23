package com.vidplay.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vidplay.app.BuildConfig
import com.vidplay.app.VidPlayApp
import com.vidplay.app.data.settings.AppSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as VidPlayApp
    val repository = app.container.settingsRepository
    val vault = app.container.vaultRepository
    val scope = rememberCoroutineScope()
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var privacyDialog by remember { mutableStateOf(false) }
    var licenseDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("Ajustes") },
            )
        }
        item { SettingsSection("Reproducción") }
        item {
            ListItem(
                headlineContent = { Text("Saltar hacia atrás") },
                supportingContent = { Text("${settings.seekBackMs / 1000} segundos") },
                trailingContent = {
                    Row {
                        listOf(5L, 10L, 20L, 30L).forEach { seconds ->
                            AssistChip(
                                onClick = { scope.launch { repository.setSeekBack(seconds * 1000) } },
                                label = { Text("$seconds") },
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                    }
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Saltar hacia delante") },
                supportingContent = { Text("${settings.seekForwardMs / 1000} segundos") },
                trailingContent = {
                    Row {
                        listOf(5L, 10L, 20L, 30L).forEach { seconds ->
                            AssistChip(
                                onClick = { scope.launch { repository.setSeekForward(seconds * 1000) } },
                                label = { Text("$seconds") },
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                    }
                },
            )
        }
        item {
            var boost by remember(settings.boostSpeed) { mutableStateOf(settings.boostSpeed) }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("Velocidad al mantener pulsado: ${formatSpeed(boost)}")
                Slider(
                    value = boost,
                    onValueChange = { boost = it },
                    onValueChangeFinished = { scope.launch { repository.setBoostSpeed(boost) } },
                    valueRange = 1.5f..4f,
                    steps = 4,
                )
            }
        }
        item {
            var zoom by remember(settings.maxZoom) { mutableStateOf(settings.maxZoom) }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("Zoom máximo: ${String.format("%.1f", zoom)}x")
                Slider(
                    value = zoom,
                    onValueChange = { zoom = it },
                    onValueChangeFinished = { scope.launch { repository.setMaxZoom(zoom) } },
                    valueRange = 2f..8f,
                    steps = 5,
                )
            }
        }
        item {
            SwitchRow("Reanudar donde lo dejaste", settings.autoResume) {
                scope.launch { repository.setAutoResume(it) }
            }
        }
        item {
            SwitchRow("Seguir reproduciendo en segundo plano", settings.backgroundPlay) {
                scope.launch { repository.setBackgroundPlay(it) }
            }
        }
        item {
            SwitchRow("Mantener la pantalla encendida", settings.keepScreenOn) {
                scope.launch { repository.setKeepScreenOn(it) }
            }
        }

        item { SettingsSection("Gestos") }
        item {
            SwitchRow("Deslizar para buscar", settings.gestureSeek) {
                scope.launch { repository.setGestureSeek(it) }
            }
        }
        item {
            SwitchRow("Deslizar para cambiar el brillo", settings.gestureBrightness) {
                scope.launch { repository.setGestureBrightness(it) }
            }
        }
        item {
            SwitchRow("Deslizar para cambiar el volumen", settings.gestureVolume) {
                scope.launch { repository.setGestureVolume(it) }
            }
        }

        item { SettingsSection("Baúl cifrado") }
        item {
            ListItem(
                leadingContent = { Icon(Icons.Rounded.Shield, null) },
                headlineContent = { Text("Cifrado AES-256-GCM") },
                supportingContent = { Text("Clave protegida por Android Keystore. Los archivos viven en el almacenamiento privado de la app.") },
            )
        }
        item {
            ListItem(
                leadingContent = { Icon(Icons.Rounded.Lock, null) },
                headlineContent = { Text("Bloquear baúl ahora") },
                supportingContent = { Text("Se pedirá biometría o PIN para volver a abrirlo.") },
                trailingContent = {
                    TextButton(onClick = { vault.lock() }) { Text("Bloquear") }
                },
            )
        }
        item {
            ListItem(
                headlineContent = { Text("Auto-bloqueo al salir") },
                supportingContent = { Text("${settings.vaultAutoLockSeconds} segundos después de dejar la app, si no hay reproducción del baúl.") },
                trailingContent = {
                    Row {
                        listOf(30, 60, 300).forEach { seconds ->
                            AssistChip(
                                onClick = { scope.launch { repository.setVaultAutoLock(seconds) } },
                                label = { Text(if (seconds < 60) "${seconds}s" else "${seconds / 60}min") },
                                modifier = Modifier.padding(start = 2.dp),
                            )
                        }
                    }
                },
            )
        }
        item {
            SwitchRow("Ocultar títulos del baúl en notificaciones", settings.hideVaultNotification) {
                scope.launch { repository.setHideVaultNotification(it) }
            }
        }
        item {
            SwitchRow("Permitir imagen en imagen del baúl", settings.allowVaultPip) {
                scope.launch { repository.setAllowVaultPip(it) }
            }
        }
        item {
            ListItem(
                leadingContent = { Icon(Icons.Rounded.PrivacyTip, null) },
                headlineContent = { Text("Aviso importante") },
                supportingContent = {
                    Text(
                        "Desinstalar la app o borrar sus datos elimina el baúl y su clave. " +
                            "Si el vídeo ya estaba respaldado en Google Fotos u otra nube, ocultarlo aquí no borra esa copia.",
                    )
                },
            )
        }

        item { SettingsSection("Privacidad y licencia") }
        item {
            ListItem(
                leadingContent = { Icon(Icons.Rounded.PrivacyTip, null) },
                headlineContent = { Text("Privacidad") },
                supportingContent = { Text("Todo se procesa en el dispositivo. Sin internet, cuentas, analíticas ni anuncios.") },
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        item {
            ListItem(
                leadingContent = { Icon(Icons.Rounded.Code, null) },
                headlineContent = { Text("Código fuente (GPL-3.0)") },
                supportingContent = { Text("github.com/jdmontalvo/vidplay") },
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { privacyDialog = true }) { Text("Privacidad") }
                TextButton(onClick = { licenseDialog = true }) { Text("Licencia") }
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/jdmontalvo/vidplay"))
                    runCatching { context.startActivity(intent) }
                }) { Text("Código fuente") }
            }
        }
        item {
            Text(
                text = "VidPlay ${BuildConfig.VERSION_NAME} · GPL-3.0 · Media3 + NextLib",
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (privacyDialog) {
        AlertDialog(
            onDismissRequest = { privacyDialog = false },
            title = { Text("Privacidad") },
            text = {
                Text(
                    "VidPlay no tiene permiso de Internet. La biblioteca, el reproductor y el baúl funcionan " +
                        "solo en el dispositivo. No hay cuentas, analíticas, anuncios ni subida de archivos. " +
                        "El baúl cifra los vídeos con AES-256-GCM y la clave se guarda en Android Keystore. " +
                        "Desinstalar la app o borrar sus datos hace que el baúl sea irrecuperable.",
                )
            },
            confirmButton = { TextButton(onClick = { privacyDialog = false }) { Text("Cerrar") } },
        )
    }

    if (licenseDialog) {
        AlertDialog(
            onDismissRequest = { licenseDialog = false },
            title = { Text("GPL-3.0") },
            text = {
                Column {
                    Text(
                        "VidPlay se distribuye bajo GNU General Public License v3.0. " +
                            "Incluye Media3 (Apache-2.0) y NextLib (GPL-3.0), que a su vez incorpora FFmpeg. " +
                            "El código fuente completo está disponible en el repositorio público.",
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("github.com/jdmontalvo/vidplay", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { licenseDialog = false }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onChange)
        },
    )
}

private fun formatSpeed(speed: Float): String {
    return if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"
}

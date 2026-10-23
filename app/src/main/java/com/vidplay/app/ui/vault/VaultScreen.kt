package com.vidplay.app.ui.vault

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vidplay.app.data.db.PendingDeleteEntity
import com.vidplay.app.data.db.VaultItemEntity
import com.vidplay.app.data.vault.TransferState
import com.vidplay.app.data.vault.TransferKind
import com.vidplay.app.data.vault.VaultTransferMonitor
import com.vidplay.app.ui.components.EmptyState
import com.vidplay.app.ui.components.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val pendingDeletes by viewModel.pendingDeletes.collectAsStateWithLifecycle()
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
    val hasVault by viewModel.hasVault.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var showSetup by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var setupMode by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var restoreItem by remember { mutableStateOf<VaultItemEntity?>(null) }
    var deleteItem by remember { mutableStateOf<VaultItemEntity?>(null) }
    var pendingRestore by remember { mutableStateOf<VaultItemEntity?>(null) }
    var showPendingDialog by remember { mutableStateOf(true) }

    val activity = context.findFragmentActivity()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshState()
    }

    LaunchedEffect(pendingDeletes.size) {
        if (pendingDeletes.isNotEmpty()) showPendingDialog = true
    }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(transfer) {
        val t = transfer
        if (t != null && !t.running && t.error == null) {
            kotlinx.coroutines.delay(3_000)
            viewModel.clearTransfer()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.importUris(uris) }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("video/*"),
    ) { uri ->
        pendingRestore?.let { item ->
            if (uri != null) viewModel.restore(item, uri)
            pendingRestore = null
        }
    }

    val deleteOriginalLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.refreshState()
        }
    }

    fun authenticate() {
        val host = activity
        if (host == null) {
            setupMode = false
            showPin = true
            return
        }
        val allowed = if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
        val can = BiometricManager.from(context).canAuthenticate(allowed)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            setupMode = false
            showPin = true
            return
        }
        val prompt = BiometricPrompt(
            host,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.unlockWithKeystore { unlockResult ->
                        if (unlockResult.isFailure) {
                            viewModel.setMessage("No se pudo abrir el baúl. Usa el PIN de recuperación.")
                            setupMode = false
                            showPin = true
                        }
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    setupMode = false
                    showPin = true
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Desbloquear el baúl")
            .setSubtitle("Usa tu huella, rostro o el PIN del dispositivo")
            .apply {
                if (Build.VERSION.SDK_INT >= 30) {
                    setAllowedAuthenticators(allowed)
                } else {
                    setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    setNegativeButtonText("Usar PIN")
                }
            }
            .build()
        try {
            prompt.authenticate(info)
        } catch (_: Exception) {
            setupMode = false
            showPin = true
        }
    }

    fun handlePendingDelete(pending: PendingDeleteEntity) {
        scope.launch {
            when (val result = viewModel.deleteOriginal(pending)) {
                is DeleteOriginalResult.IntentSenderReady -> {
                    deleteOriginalLauncher.launch(IntentSenderRequest.Builder(result.sender).build())
                }
                DeleteOriginalResult.Deleted -> viewModel.refreshState()
                is DeleteOriginalResult.Failed -> viewModel.setMessage(result.message)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
            title = { Text("Baúl") },
            actions = {
                if (unlocked) {
                    IconButton(onClick = { importLauncher.launch(arrayOf("video/*")) }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Importar vídeos")
                    }
                    IconButton(onClick = { viewModel.lock() }) {
                        Icon(Icons.Rounded.Lock, contentDescription = "Bloquear baúl")
                    }
                }
            },
        )

        when {
            !hasVault -> VaultSetup(onConfigure = { setupMode = true; showPin = true })
            !unlocked -> VaultLocked(
                onBiometric = ::authenticate,
                onPin = { setupMode = false; showPin = true },
            )
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    TransferBanner(transfer, onDismiss = viewModel::clearTransfer)
                    val pending = pendingDeletes.firstOrNull()
                    if (pending != null && showPendingDialog) {
                        PendingDeleteCard(
                            pending = pending,
                            onDelete = { handlePendingDelete(pending) },
                            onLater = { showPendingDialog = false },
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (items.isEmpty()) {
                            EmptyState(
                                icon = Icons.Rounded.Shield,
                                title = "Baúl vacío",
                                subtitle = "Importa vídeos para cifrarlos y ocultarlos de la galería y del explorador.",
                                actionLabel = "Importar vídeos",
                                onAction = { importLauncher.launch(arrayOf("video/*")) },
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(158.dp),
                                contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 92.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(items, key = { it.id }) { item ->
                                    VaultCard(
                                        item = item,
                                        thumbnail = { VaultThumbnail(viewModel, item) },
                                        onPlay = { viewModel.play(context, item) },
                                        onRestore = { restoreItem = item },
                                        onDelete = { deleteItem = item },
                                    )
                                }
                            }
                        }
                        ExtendedFloatingActionButton(
                            onClick = { importLauncher.launch(arrayOf("video/*")) },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
                            icon = { Icon(Icons.Rounded.Add, null) },
                            text = { Text("Importar") },
                        )
                    }
                }
            }
        }
    }

    if (showPin) {
        PinDialog(
            setup = setupMode,
            pin = pin,
            error = pinError,
            onPinChange = {
                pin = it
                pinError = null
            },
            onDismiss = {
                showPin = false
                pin = ""
                pinError = null
            },
            onConfirm = {
                if (it.length < 8) {
                    pinError = "Usa al menos 8 caracteres"
                } else if (setupMode) {
                    viewModel.setup(it) { result ->
                        if (result.isSuccess) {
                            showPin = false
                            pin = ""
                            showSetup = false
                        } else {
                            pinError = result.exceptionOrNull()?.message ?: "No se pudo crear el baúl"
                        }
                    }
                } else {
                    viewModel.unlockWithPin(it) { result ->
                        if (result.isSuccess) {
                            showPin = false
                            pin = ""
                        } else {
                            pinError = result.exceptionOrNull()?.message ?: "PIN incorrecto"
                        }
                    }
                }
            },
        )
    }

    restoreItem?.let { item ->
        AlertDialog(
            onDismissRequest = { restoreItem = null },
            title = { Text("Hacer visible de nuevo") },
            text = {
                Text("Se descifrará el vídeo y se guardará como un archivo normal. El original del baúl se eliminará al terminar.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restore(item, null)
                    restoreItem = null
                }) { Text("Guardar en Movies/VidPlay") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        pendingRestore = item
                        restoreItem = null
                        restoreLauncher.launch(item.displayName)
                    }) { Text("Elegir ubicación") }
                    TextButton(onClick = { restoreItem = null }) { Text("Cancelar") }
                }
            },
        )
    }

    deleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text("Eliminar del baúl") },
            text = { Text("Esta acción borra la copia cifrada de forma permanente. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(item)
                    deleteItem = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { deleteItem = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun VaultSetup(onConfigure: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("Protege tus vídeos", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        Text(
            "VidPlay cifra los vídeos con AES-256-GCM y los guarda en el almacenamiento privado de la app. " +
                "Desaparecen de la galería y del explorador de archivos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        SetupPoint("Solo se abren con biometría o tu PIN de recuperación.")
        SetupPoint("El PIN es obligatorio como respaldo si cambias la biometría.")
        SetupPoint("Desinstalar la app o borrar sus datos elimina el baúl y la clave.")
        SetupPoint("Las copias que ya estén en Google Fotos u otra nube no se borran.")
        Spacer(Modifier.height(20.dp))
        Button(onClick = onConfigure) { Text("Configurar baúl") }
    }
}

@Composable
private fun SetupPoint(text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun VaultLocked(onBiometric: () -> Unit, onPin: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("Baúl bloqueado", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Desbloquea para ver, reproducir o importar vídeos.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onBiometric) {
            Icon(Icons.Rounded.Fingerprint, null)
            Spacer(Modifier.width(8.dp))
            Text("Desbloquear con biometría")
        }
        TextButton(onClick = onPin) { Text("Usar PIN de recuperación") }
    }
}

@Composable
private fun PinDialog(
    setup: Boolean,
    pin: String,
    error: String?,
    onPinChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (setup) "Crear PIN del baúl" else "PIN de recuperación") },
        text = {
            Column {
                Text(
                    if (setup) {
                        "Elige un PIN de al menos 8 caracteres. Lo necesitarás si la biometría deja de funcionar."
                    } else {
                        "Introduce el PIN que elegiste al crear el baúl."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = onPinChange,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("PIN") },
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pin) }) {
                Text(if (setup) "Crear" else "Desbloquear")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun VaultThumbnail(viewModel: VaultViewModel, item: VaultItemEntity) {
    val bitmap by produceState<Bitmap?>(null, item.id) {
        value = withContext(Dispatchers.IO) { viewModel.thumbnail(item.id) }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF23232F)),
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                modifier = Modifier.align(Alignment.Center).size(34.dp),
            )
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            shape = RoundedCornerShape(4.dp),
            color = Color.Black.copy(alpha = 0.72f),
        ) {
            Text(
                text = formatDuration(item.durationMs),
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun VaultCard(
    item: VaultItemEntity,
    thumbnail: @Composable () -> Unit,
    onPlay: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onPlay,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box {
            thumbnail()
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "Más opciones",
                        tint = Color.White,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                            .padding(2.dp),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Reproducir") },
                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                        onClick = {
                            menu = false
                            onPlay()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Hacer visible") },
                        leadingIcon = { Icon(Icons.Rounded.Restore, null) },
                        onClick = {
                            menu = false
                            onRestore()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = {
                            menu = false
                            onDelete()
                        },
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = item.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${formatSize(item.sizeBytes)} · cifrado",
                maxLines = 1,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransferBanner(transfer: TransferState?, onDismiss: () -> Unit) {
    if (transfer == null) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            transfer.error != null -> "No se pudo completar"
                            !transfer.running -> if (transfer.kind == TransferKind.IMPORT) "Vídeo ocultado" else "Vídeo restaurado"
                            transfer.kind == TransferKind.IMPORT -> "Ocultando en el baúl"
                            else -> "Restaurando vídeo"
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = transfer.error ?: transfer.displayName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!transfer.running) {
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                }
            }
            if (transfer.running) {
                Spacer(Modifier.height(8.dp))
                if (transfer.progress != null) {
                    LinearProgressIndicator(
                        progress = { transfer.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun PendingDeleteCard(
    pending: PendingDeleteEntity,
    onDelete: () -> Unit,
    onLater: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Falta borrar el original visible",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                "El vídeo ya está en el baúl, pero el archivo original sigue en el dispositivo. Confirma el borrado del sistema para ocultarlo del explorador.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onLater) { Text("Más tarde") }
                TextButton(onClick = onDelete) { Text("Eliminar original") }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) "${bytes} B" else String.format("%.1f %s", value, units[index])
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return current as? FragmentActivity
}

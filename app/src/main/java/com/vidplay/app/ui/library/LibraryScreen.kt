package com.vidplay.app.ui.library

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vidplay.app.R
import com.vidplay.app.VidPlayApp
import com.vidplay.app.data.media.VideoItem
import com.vidplay.app.data.settings.SortOrder
import com.vidplay.app.ui.components.EmptyState
import com.vidplay.app.ui.components.PermissionCard
import com.vidplay.app.ui.components.VideoGrid
import com.vidplay.app.ui.components.VideoList
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenVault: () -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = context.applicationContext as VidPlayApp
    val ads = app.container.ads
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val permissionGranted by viewModel.permissionGranted.collectAsStateWithLifecycle()
    val partialAccess by viewModel.partialAccess.collectAsStateWithLifecycle()
    val adReady by ads.adReady.collectAsStateWithLifecycle()

    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var listMode by rememberSaveable { mutableStateOf(false) }
    var vaultPrompt by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refresh() }

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.openFile(context, it) } }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.refresh()
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // Ask for a library ad only when the user is actually browsing videos.
    LaunchedEffect(permissionGranted, videos.isEmpty()) {
        if (permissionGranted && videos.isNotEmpty()) ads.onLibraryVisible()
    }

    val adContent: (@Composable () -> Unit)? =
        if (adReady && permissionGranted && !searchVisible) {
            {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                ) {
                    ads.NativeAdCard(Modifier.fillMaxWidth())
                }
            }
        } else {
            null
        }

    fun requestPermission() {
        permissionLauncher.launch(com.vidplay.app.data.media.MediaPermissions.requiredPermissions())
    }

    fun deleteVideo(item: VideoItem) {
        val sender = viewModel.deleteRequest(item)
        if (sender != null) {
            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
        } else {
            scope.launch {
                val ok = viewModel.deleteDirect(item)
                if (ok) viewModel.refresh() else Toast.makeText(
                    context,
                    "No se pudo eliminar el vídeo. Usa el explorador de archivos.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun hideVideo(item: VideoItem) {
        if (viewModel.hide(context, item)) {
            Toast.makeText(context, "Ocultando en el baúl…", Toast.LENGTH_SHORT).show()
            onOpenVault()
        } else {
            vaultPrompt = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
            title = {
                if (searchVisible) {
                    TextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        singleLine = true,
                        placeholder = { Text("Buscar vídeos…") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(stringResource(R.string.app_name))
                }
            },
            actions = {
                if (searchVisible) {
                    IconButton(onClick = {
                        searchVisible = false
                        viewModel.setQuery("")
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar búsqueda")
                    }
                } else {
                    IconButton(onClick = { searchVisible = true }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Buscar")
                    }
                }
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.Rounded.Sort, contentDescription = "Ordenar")
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(sortLabel(order)) },
                                onClick = {
                                    viewModel.setSortOrder(order)
                                    sortMenu = false
                                },
                            )
                        }
                    }
                }
                IconButton(onClick = { listMode = !listMode }) {
                    Icon(
                        if (listMode) Icons.Rounded.GridView else Icons.Rounded.ViewList,
                        contentDescription = if (listMode) "Vista de cuadrícula" else "Vista de lista",
                    )
                }
                Box {
                    IconButton(onClick = { moreMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Más")
                    }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Abrir un archivo") },
                            onClick = {
                                moreMenu = false
                                openFileLauncher.launch(arrayOf("video/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Actualizar biblioteca") },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                            onClick = {
                                moreMenu = false
                                viewModel.refresh()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Ir al baúl") },
                            onClick = {
                                moreMenu = false
                                onOpenVault()
                            },
                        )
                    }
                }
            },
        )

        if (partialAccess && !permissionGranted) {
            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        "Acceso parcial: solo vemos los vídeos que seleccionaste.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = ::requestPermission) { Text("Añadir más") }
                }
            }
        }

        when {
            !permissionGranted && !partialAccess -> {
                PermissionCard(
                    partial = false,
                    onRequest = ::requestPermission,
                    onOpenFile = { openFileLauncher.launch(arrayOf("video/*")) },
                )
            }
            videos.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.VideoLibrary,
                    title = if (query.isBlank()) "No hay vídeos" else "Sin resultados",
                    subtitle = if (query.isBlank()) {
                        "Añade vídeos al teléfono o abre un archivo concreto."
                    } else {
                        "Prueba con otro nombre o carpeta."
                    },
                    actionLabel = if (query.isBlank()) "Abrir un archivo" else null,
                    onAction = if (query.isBlank()) {
                        { openFileLauncher.launch(arrayOf("video/*")) }
                    } else {
                        null
                    },
                )
            }
            listMode -> {
                VideoList(
                    videos = videos,
                    onClick = { viewModel.play(context, it) },
                    onHide = ::hideVideo,
                    onDelete = ::deleteVideo,
                    adContent = adContent,
                )
            }
            else -> {
                VideoGrid(
                    videos = videos,
                    onClick = { viewModel.play(context, it) },
                    onHide = ::hideVideo,
                    onDelete = ::deleteVideo,
                    adContent = adContent,
                )
            }
        }
    }

    if (vaultPrompt) {
        AlertDialog(
            onDismissRequest = { vaultPrompt = false },
            title = { Text("Baúl bloqueado") },
            text = { Text("Para ocultar un vídeo, primero configura y desbloquea el baúl cifrado.") },
            confirmButton = {
                TextButton(onClick = {
                    vaultPrompt = false
                    onOpenVault()
                }) { Text("Ir al baúl") }
            },
            dismissButton = {
                TextButton(onClick = { vaultPrompt = false }) { Text("Cancelar") }
            },
        )
    }
}

private fun sortLabel(order: SortOrder): String = when (order) {
    SortOrder.DATE_DESC -> "Más recientes"
    SortOrder.DATE_ASC -> "Más antiguos"
    SortOrder.NAME_ASC -> "Nombre A-Z"
    SortOrder.NAME_DESC -> "Nombre Z-A"
    SortOrder.SIZE_DESC -> "Tamaño mayor"
    SortOrder.DURATION_DESC -> "Duración mayor"
}

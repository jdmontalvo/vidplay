package com.vidplay.app.ui.folders

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vidplay.app.data.media.FolderItem
import com.vidplay.app.data.media.VideoItem
import com.vidplay.app.ui.components.EmptyState
import com.vidplay.app.ui.components.PermissionCard
import com.vidplay.app.ui.components.VideoGrid
import com.vidplay.app.ui.components.VideoThumbnail
import com.vidplay.app.ui.components.formatDuration
import com.vidplay.app.ui.library.LibraryViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    onOpenVault: () -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val permissionGranted by viewModel.permissionGranted.collectAsStateWithLifecycle()
    val partialAccess by viewModel.partialAccess.collectAsStateWithLifecycle()
    var selectedBucket by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedFolder = folders.firstOrNull { it.bucketId == selectedBucket }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refresh() }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.refresh()
    }

    LaunchedEffect(Unit) { viewModel.refresh() }

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
        if (!viewModel.hide(context, item)) {
            Toast.makeText(context, "Desbloquea el baúl para ocultar vídeos", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Ocultando en el baúl…", Toast.LENGTH_SHORT).show()
            onOpenVault()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedFolder != null) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = { selectedBucket = null }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Atrás")
                    }
                },
                title = { Text(selectedFolder.name, maxLines = 1) },
            )
            val items = selectedFolder.videos
            VideoGrid(
                videos = items,
                onClick = { item ->
                    val index = items.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)
                    viewModel.playFolder(context, items, index)
                },
                onHide = ::hideVideo,
                onDelete = ::deleteVideo,
            )
            return@Column
        }

        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
            title = { Text("Carpetas") },
        )

        when {
            !permissionGranted && !partialAccess -> {
                PermissionCard(
                    partial = false,
                    onRequest = {
                        permissionLauncher.launch(
                            com.vidplay.app.data.media.MediaPermissions.requiredPermissions(),
                        )
                    },
                    onOpenFile = {
                        Toast.makeText(context, "Abre un vídeo desde la pestaña Vídeos", Toast.LENGTH_SHORT).show()
                    },
                )
            }
            folders.isEmpty() -> {
                EmptyState(
                    icon = Icons.Rounded.Folder,
                    title = "Sin carpetas",
                    subtitle = "Cuando tengas vídeos en el teléfono aparecerán aquí agrupados por carpeta.",
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(folders, key = { it.bucketId }) { folder ->
                        FolderCard(folder) { selectedBucket = folder.bucketId }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderCard(folder: FolderItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            folder.cover?.let { cover ->
                VideoThumbnail(
                    video = cover,
                    showDuration = false,
                    modifier = Modifier.width(132.dp).aspectRatio(16f / 9f),
                )
            } ?: Column(
                modifier = Modifier
                    .width(132.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .padding(8.dp),
            ) {}
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${folder.count} vídeo${if (folder.count == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                folder.cover?.let {
                    Text(
                        text = "Duración total ${formatDuration(folder.videos.sumOf { video -> video.durationMs })}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

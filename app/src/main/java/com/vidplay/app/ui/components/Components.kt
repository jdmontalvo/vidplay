package com.vidplay.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.video.videoFrameMillis
import com.vidplay.app.data.media.VideoItem
import androidx.compose.ui.platform.LocalContext

/** Ads appear after the first six videos, never as the first card. */
private const val AD_ANCHOR_INDEX = 6

@Composable
fun VideoThumbnail(
    video: VideoItem,
    modifier: Modifier = Modifier,
    showDuration: Boolean = true,
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF23232F)),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(video.uri)
                .videoFrameMillis(1_000L)
                .crossfade(true)
                .build(),
            contentDescription = video.displayName,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(Color(0xFF23232F)),
            error = ColorPainter(Color(0xFF23232F)),
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(42.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        if (showDuration && video.durationMs > 0) {
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color.Black.copy(alpha = 0.72f),
            ) {
                Text(
                    text = formatDuration(video.durationMs),
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
fun VideoGrid(
    videos: List<VideoItem>,
    onClick: (VideoItem) -> Unit,
    onHide: (VideoItem) -> Unit,
    onDelete: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    adContent: (@Composable () -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 158.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(videos.take(AD_ANCHOR_INDEX), key = { it.uri.toString() }) { video ->
            VideoCard(video, onClick, onHide, onDelete)
        }
        if (adContent != null) {
            item(
                key = "native_ad",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "native_ad",
            ) {
                adContent()
            }
        }
        items(videos.drop(AD_ANCHOR_INDEX), key = { it.uri.toString() }) { video ->
            VideoCard(video, onClick, onHide, onDelete)
        }
    }
}

@Composable
fun VideoCard(
    video: VideoItem,
    onClick: (VideoItem) -> Unit,
    onHide: (VideoItem) -> Unit,
    onDelete: (VideoItem) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = { onClick(video) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box {
            VideoThumbnail(
                video = video,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
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
                        text = { Text("Ocultar en el baúl") },
                        leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) },
                        onClick = {
                            menu = false
                            onHide(video)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar del dispositivo") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = {
                            menu = false
                            onDelete(video)
                        },
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = video.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = video.bucketName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun VideoList(
    videos: List<VideoItem>,
    onClick: (VideoItem) -> Unit,
    onHide: (VideoItem) -> Unit,
    onDelete: (VideoItem) -> Unit,
    modifier: Modifier = Modifier,
    adContent: (@Composable () -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 6.dp),
    ) {
        items(videos.take(AD_ANCHOR_INDEX), key = { it.uri.toString() }) { video ->
            VideoListRow(video, onClick, onHide, onDelete)
        }
        if (adContent != null) {
            item(key = "native_ad", contentType = "native_ad") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    adContent()
                }
            }
        }
        items(videos.drop(AD_ANCHOR_INDEX), key = { it.uri.toString() }) { video ->
            VideoListRow(video, onClick, onHide, onDelete)
        }
    }
}

/** One video as a full-width row. Kept as its own composable so the ad item can
 *  be inserted between two slices of the list without duplicating the row. */
@Composable
private fun VideoListRow(
    video: VideoItem,
    onClick: (VideoItem) -> Unit,
    onHide: (VideoItem) -> Unit,
    onDelete: (VideoItem) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(video) }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoThumbnail(
            video = video,
            modifier = Modifier.width(126.dp).aspectRatio(16f / 9f),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.displayName,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${video.bucketName} · ${formatDuration(video.durationMs)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Más opciones")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Ocultar en el baúl") },
                    leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) },
                    onClick = {
                        menu = false
                        onHide(video)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Eliminar del dispositivo") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = {
                        menu = false
                        onDelete(video)
                    },
                )
            }
        }
    }
}

@Composable
fun PermissionCard(
    partial: Boolean,
    onRequest: () -> Unit,
    onOpenFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (partial) "Acceso parcial a tus vídeos" else "Acceso a tus vídeos",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (partial) {
                    "Solo vemos los vídeos que seleccionaste. Puedes añadir más o abrir un archivo concreto."
                } else {
                    "VidCofre necesita permiso para leer los vídeos de este dispositivo. Tus vídeos no se suben a internet."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRequest) {
                Text(if (partial) "Seleccionar más vídeos" else "Permitir acceso")
            }
            TextButton(onClick = onOpenFile) {
                Text("Abrir un archivo")
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(58.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

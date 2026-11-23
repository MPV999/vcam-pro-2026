package com.vcampro.ultimate.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vcampro.ultimate.core.di.AppGraph
import com.vcampro.ultimate.core.i18n.LocalStrings
import com.vcampro.ultimate.core.ui.components.EmptyState
import com.vcampro.ultimate.core.ui.components.InfoRow
import com.vcampro.ultimate.core.ui.components.SectionTitle
import com.vcampro.ultimate.core.ui.components.VcamCard
import com.vcampro.ultimate.core.ui.components.VcamPrimaryButton
import com.vcampro.ultimate.core.ui.components.VcamScreen
import com.vcampro.ultimate.core.ui.util.shareUri
import com.vcampro.ultimate.data.db.AlbumEntity
import com.vcampro.ultimate.domain.model.MediaFilter
import com.vcampro.ultimate.domain.model.MediaItem
import com.vcampro.ultimate.domain.model.MediaSort
import com.vcampro.ultimate.domain.model.StorageUsage
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaLibraryScreen(onBack: () -> Unit) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { AppGraph.media }

    val mediaPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    var hasPermission by remember {
        mutableStateOf(
            mediaPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    var filter by remember { mutableStateOf(MediaFilter.ALL) }
    var sort by remember { mutableStateOf(MediaSort.NEWEST) }
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var usage by remember { mutableStateOf(StorageUsage()) }
    var selection by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<MediaItem?>(null) }
    var showAlbumDialog by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    val albums by repository.albums()
        .collectAsStateWithLifecycle(initialValue = emptyList<AlbumEntity>())

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.values.all { it }
        refreshKey += 1
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) {
        selection = emptySet()
        selectionMode = false
        refreshKey += 1
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = repository.importDocument(uri)
                status = result.fold(
                    onSuccess = { strings.savedTo },
                    onFailure = { "${strings.failed}: ${it.message}" }
                )
                refreshKey += 1
            }
        }
    }

    LaunchedEffect(filter, sort, query, refreshKey, hasPermission) {
        if (!hasPermission) {
            items = emptyList()
            return@LaunchedEffect
        }
        items = repository.loadLibrary(filter, query, sort)
        usage = repository.storageUsage()
    }

    VcamScreen(
        title = strings.mediaLibrary,
        subtitle = "${usage.photoCount} ${strings.photos} · ${usage.videoCount} ${strings.videos}",
        onBack = onBack,
        actions = {
            AssistChip(
                onClick = { importLauncher.launch(arrayOf("image/*", "video/*")) },
                label = { Text(strings.import, style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(14.dp)) }
            )
        }
    ) {
        if (!hasPermission) {
            VcamCard {
                Text(text = strings.permissionRequired, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = strings.mediaNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                VcamPrimaryButton(
                    text = strings.grantPermission,
                    onClick = { permissionLauncher.launch(mediaPermissions) }
                )
            }
            return@VcamScreen
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(strings.search) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                MediaFilter.ALL to strings.all,
                MediaFilter.PHOTOS to strings.photos,
                MediaFilter.VIDEOS to strings.videos,
                MediaFilter.FAVORITES to strings.favorites
            ).forEach { (value, label) ->
                FilterChip(
                    selected = filter == value,
                    onClick = { filter = value },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                MediaSort.NEWEST to strings.sortNewest,
                MediaSort.OLDEST to strings.sortOldest,
                MediaSort.LARGEST to strings.sortLargest,
                MediaSort.NAME to strings.sortName
            ).forEach { (value, label) ->
                FilterChip(
                    selected = sort == value,
                    onClick = { sort = value },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                )
            }
        }

        if (albums.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                albums.forEach { album ->
                    AssistChip(
                        onClick = { showAlbumDialog = false },
                        label = { Text(album.name, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        VcamCard {
            InfoRow(
                label = strings.storageUsage,
                value = "${usage.usedLabel} / ${usage.totalLabel}"
            )
            InfoRow(label = "VCAM PRO", value = usage.appMediaLabel)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        selectionMode = !selectionMode
                        selection = emptySet()
                    },
                    label = { Text(strings.batchSelect) }
                )
                AssistChip(
                    onClick = { showAlbumDialog = true },
                    label = { Text(strings.createAlbum) }
                )
            }
        }

        if (selectionMode) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        scope.launch {
                            val selectedItems = items.filter { selection.contains(it.uri) }
                            selectedItems.forEach { repository.toggleFavorite(it) }
                            refreshKey += 1
                        }
                    },
                    label = { Text("${strings.selectedCount}: ${selection.size}") },
                    leadingIcon = { Icon(Icons.Filled.Star, null, Modifier.size(14.dp)) }
                )
                AssistChip(
                    onClick = { renameTarget = items.firstOrNull { selection.contains(it.uri) } },
                    label = { Text(strings.rename) },
                    leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(14.dp)) }
                )
                AssistChip(
                    onClick = {
                        val selectedItems = items.filter { selection.contains(it.uri) }
                        val sender = repository.deleteRequest(selectedItems)
                        if (sender != null) {
                            deleteLauncher.launch(
                                androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                            )
                        } else {
                            scope.launch {
                                repository.deleteDirect(selectedItems)
                                selection = emptySet()
                                refreshKey += 1
                            }
                        }
                    },
                    label = { Text(strings.delete) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, Modifier.size(14.dp)) }
                )
                AssistChip(
                    onClick = {
                        val item = items.firstOrNull { selection.contains(it.uri) }
                        if (item != null) context.shareUri(item.uri, item.mimeType)
                    },
                    label = { Text(strings.share) },
                    leadingIcon = { Icon(Icons.Filled.Share, null, Modifier.size(14.dp)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.PhotoLibrary,
                title = strings.emptyLibrary,
                message = strings.mediaNote
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items, key = { it.uri.toString() }) { item ->
                    val selected = selection.contains(item.uri)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(118.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) {
                                        selection = if (selected) {
                                            selection - item.uri
                                        } else {
                                            selection + item.uri
                                        }
                                    } else {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(item.uri, item.mimeType)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            )
                                        }
                                    }
                                },
                                onLongClick = {
                                    selectionMode = true
                                    selection = selection + item.uri
                                }
                            )
                    ) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (item.isVideo) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = item.durationLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(20.dp)
                                )
                            }
                        }
                        Icon(
                            imageVector = if (item.isFavorite) {
                                Icons.Filled.Star
                            } else {
                                Icons.Filled.StarBorder
                            },
                            contentDescription = null,
                            tint = if (item.isFavorite) Color(0xFFFFC94D) else Color.White.copy(alpha = 0.8f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .size(18.dp)
                        )
                    }
                }
            }
        }

        if (status != null) {
            Text(
                text = status ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }

    val target = renameTarget
    if (target != null) {
        var newName by remember(target) { mutableStateOf(target.displayName) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(strings.renameTitle) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(strings.newName) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val result = repository.rename(target, newName)
                        status = result.fold(
                            onSuccess = { strings.success },
                            onFailure = { "${strings.failed}: ${it.message}" }
                        )
                        renameTarget = null
                        refreshKey += 1
                    }
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(strings.cancel) }
            }
        )
    }

    if (showAlbumDialog) {
        var albumName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAlbumDialog = false },
            title = { Text(strings.createAlbum) },
            text = {
                OutlinedTextField(
                    value = albumName,
                    onValueChange = { albumName = it },
                    label = { Text(strings.albumName) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (albumName.isNotBlank()) {
                            val albumId = repository.createAlbum(albumName)
                            items.filter { selection.contains(it.uri) }.forEach { item ->
                                repository.addToAlbum(albumId, item)
                            }
                            status = strings.success
                        }
                        showAlbumDialog = false
                    }
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showAlbumDialog = false }) { Text(strings.cancel) }
            }
        )
    }
}

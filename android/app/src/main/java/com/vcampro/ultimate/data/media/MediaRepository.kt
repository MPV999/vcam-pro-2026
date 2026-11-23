package com.vcampro.ultimate.data.media

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Size
import android.graphics.Bitmap
import com.vcampro.ultimate.data.db.AlbumEntity
import com.vcampro.ultimate.data.db.AlbumItemEntity
import com.vcampro.ultimate.data.db.FavoriteEntity
import com.vcampro.ultimate.data.db.MediaDao
import com.vcampro.ultimate.domain.model.MediaFilter
import com.vcampro.ultimate.domain.model.MediaItem
import com.vcampro.ultimate.domain.model.MediaKind
import com.vcampro.ultimate.domain.model.MediaSort
import com.vcampro.ultimate.domain.model.StorageUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Media library backed by MediaStore (read) and Room (favourites / albums).
 *
 * Nothing is copied into the app's own storage: the library is a curated view
 * over the user's media, so the device never holds two copies of the same file.
 */
class MediaRepository(
    private val context: Context,
    private val dao: MediaDao
) {

    suspend fun loadLibrary(
        filter: MediaFilter = MediaFilter.ALL,
        query: String = "",
        sort: MediaSort = MediaSort.NEWEST
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val favorites = dao.favoriteUris().toHashSet()
        val items = mutableListOf<MediaItem>()
        if (filter != MediaFilter.VIDEOS) {
            items.addAll(queryPhotos(query).map { it.copy(isFavorite = favorites.contains(it.uri.toString())) })
        }
        if (filter != MediaFilter.PHOTOS) {
            items.addAll(queryVideos(query).map { it.copy(isFavorite = favorites.contains(it.uri.toString())) })
        }
        val filtered = when (filter) {
            MediaFilter.FAVORITES -> items.filter { it.isFavorite }
            else -> items
        }
        sortItems(filtered, sort)
    }

    private fun sortItems(items: List<MediaItem>, sort: MediaSort): List<MediaItem> = when (sort) {
        MediaSort.NEWEST -> items.sortedByDescending { it.dateAddedSeconds }
        MediaSort.OLDEST -> items.sortedBy { it.dateAddedSeconds }
        MediaSort.LARGEST -> items.sortedByDescending { it.sizeBytes }
        MediaSort.NAME -> items.sortedBy { it.displayName.lowercase() }
    }

    private fun queryPhotos(query: String): List<MediaItem> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT
        )
        val selection = if (query.isBlank()) null else "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        val args = if (query.isBlank()) null else arrayOf("%$query%")
        val result = mutableListOf<MediaItem>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                args,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    result.add(
                        MediaItem(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            displayName = cursor.getString(nameColumn) ?: "IMG_$id",
                            kind = MediaKind.PHOTO,
                            mimeType = cursor.getString(mimeColumn) ?: "image/jpeg",
                            sizeBytes = cursor.getLong(sizeColumn),
                            dateAddedSeconds = cursor.getLong(dateColumn),
                            width = cursor.getInt(widthColumn),
                            height = cursor.getInt(heightColumn)
                        )
                    )
                }
            }
        }
        return result
    }

    private fun queryVideos(query: String): List<MediaItem> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        val selection = if (query.isBlank()) null else "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
        val args = if (query.isBlank()) null else arrayOf("%$query%")
        val result = mutableListOf<MediaItem>()
        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                args,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    result.add(
                        MediaItem(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id),
                            displayName = cursor.getString(nameColumn) ?: "VID_$id",
                            kind = MediaKind.VIDEO,
                            mimeType = cursor.getString(mimeColumn) ?: "video/mp4",
                            sizeBytes = cursor.getLong(sizeColumn),
                            dateAddedSeconds = cursor.getLong(dateColumn),
                            durationMs = cursor.getLong(durationColumn),
                            width = cursor.getInt(widthColumn),
                            height = cursor.getInt(heightColumn)
                        )
                    )
                }
            }
        }
        return result
    }

    suspend fun toggleFavorite(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        val uriString = item.uri.toString()
        if (item.isFavorite) {
            dao.removeFavorite(uriString)
            false
        } else {
            dao.addFavorite(FavoriteEntity(uri = uriString, addedAt = System.currentTimeMillis()))
            true
        }
    }

    fun favoriteUris(): Flow<List<String>> = dao.favoriteUrisFlow()

    fun albums(): Flow<List<AlbumEntity>> = dao.albums()

    suspend fun createAlbum(name: String): Long = withContext(Dispatchers.IO) {
        dao.insertAlbum(AlbumEntity(name = name, createdAt = System.currentTimeMillis()))
    }

    suspend fun deleteAlbum(albumId: Long) = withContext(Dispatchers.IO) {
        dao.deleteAlbumItems(albumId)
        dao.deleteAlbum(albumId)
    }

    suspend fun addToAlbum(albumId: Long, item: MediaItem) = withContext(Dispatchers.IO) {
        dao.addToAlbum(AlbumItemEntity(albumId = albumId, uri = item.uri.toString()))
    }

    suspend fun removeFromAlbum(albumId: Long, item: MediaItem) = withContext(Dispatchers.IO) {
        dao.removeFromAlbum(albumId, item.uri.toString())
    }

    suspend fun albumItems(albumId: Long): List<MediaItem> = withContext(Dispatchers.IO) {
        val uris = dao.albumItems(albumId).map { it.uri }.toSet()
        if (uris.isEmpty()) return@withContext emptyList()
        loadLibrary(MediaFilter.ALL).filter { uris.contains(it.uri.toString()) }
    }

    /**
     * Renames a media item. Returns a failure message when the platform refuses
     * the update (the caller can then show it instead of pretending it worked).
     */
    suspend fun rename(item: MediaItem, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, GalleryWriter.ensureExtension(
                    newName,
                    if (item.isVideo) "mp4" else "jpg"
                ))
            }
            val updated = context.contentResolver.update(item.uri, values, null, null)
            if (updated <= 0) throw IllegalStateException("MediaStore từ chối đổi tên tệp này")
        }
    }

    /**
     * Builds the system delete request (Android 11+) so the user confirms the
     * deletion. Returns null when the platform needs no confirmation.
     */
    fun deleteRequest(items: List<MediaItem>): IntentSender? {
        if (items.isEmpty()) return null
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return null
        return runCatching {
            MediaStore.createDeleteRequest(
                context.contentResolver,
                items.map { it.uri }
            ).intentSender
        }.getOrNull()
    }

    suspend fun deleteDirect(items: List<MediaItem>): Int = withContext(Dispatchers.IO) {
        var deleted = 0
        items.forEach { item ->
            runCatching {
                val rows = context.contentResolver.delete(item.uri, null, null)
                if (rows > 0) deleted += 1
            }
        }
        deleted
    }

    suspend fun thumbnail(item: MediaItem, sizePx: Int = 256): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.loadThumbnail(item.uri, Size(sizePx, sizePx), null)
        }.getOrNull()
    }

    suspend fun storageUsage(): StorageUsage = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        var appBytes = 0L
        var photos = 0
        var videos = 0
        runCatching {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.MEDIA_TYPE),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("%VCAM PRO%"),
                null
            )?.use { cursor ->
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MEDIA_TYPE)
                while (cursor.moveToNext()) {
                    appBytes += cursor.getLong(sizeColumn)
                    when (cursor.getInt(typeColumn)) {
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> photos += 1
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> videos += 1
                    }
                }
            }
        }
        StorageUsage(
            totalBytes = stat.totalBytes,
            freeBytes = stat.availableBytes,
            appMediaBytes = appBytes,
            photoCount = photos,
            videoCount = videos
        )
    }

    suspend fun importDocument(uri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val extension = when {
                mimeType.contains("video") -> "mp4"
                mimeType.contains("png") -> "png"
                else -> "jpg"
            }
            val cached = GalleryWriter.copyToCache(context, uri, extension)
                ?: throw IllegalStateException("Không đọc được tệp đã chọn")
            val name = "VCAM_IMPORT_${System.currentTimeMillis()}"
            val result = if (mimeType.startsWith("video/")) {
                GalleryWriter.saveVideo(context, cached, name)
            } else {
                GalleryWriter.saveBytes(context, cached.readBytes(), "$name.$extension", mimeType)
            }
            cached.delete()
            result ?: throw IllegalStateException("Không ghi được vào thư viện")
        }
    }

    suspend fun clearAppRecords() = withContext(Dispatchers.IO) {
        dao.clearFavorites()
        dao.clearAlbumItems()
        dao.clearAlbums()
    }

    fun albumName(): String = GalleryWriter.IMAGE_ALBUM
}

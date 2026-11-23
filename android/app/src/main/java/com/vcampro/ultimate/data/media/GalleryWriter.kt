package com.vcampro.ultimate.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Writes app output into the public MediaStore collections (Scoped Storage
 * compliant: the app only ever writes files it owns).
 */
object GalleryWriter {

    const val IMAGE_ALBUM = "Pictures/VCAM PRO"
    const val VIDEO_ALBUM = "Movies/VCAM PRO"

    fun saveImage(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        quality: Int = 95
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, ensureExtension(displayName, "jpg"))
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, IMAGE_ALBUM)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = runCatching {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return null
        return runCatching {
            resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), stream)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrElse {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    fun saveVideo(context: Context, file: File, displayName: String): Uri? {
        if (!file.exists() || file.length() == 0L) return null
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, ensureExtension(displayName, "mp4"))
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, VIDEO_ALBUM)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = runCatching {
            resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return null
        return runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrElse {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    fun saveBytes(
        context: Context,
        bytes: ByteArray,
        displayName: String,
        mimeType: String
    ): Uri? {
        val collection = if (mimeType.startsWith("video/")) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val relativePath = if (mimeType.startsWith("video/")) VIDEO_ALBUM else IMAGE_ALBUM
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        return runCatching {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrElse {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    /** Copies an arbitrary document Uri into the app's private cache. */
    fun copyToCache(context: Context, uri: Uri, extension: String): File? {
        val target = File(context.cacheDir, "import_${System.currentTimeMillis()}.$extension")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            if (target.length() > 0) target else null
        }.getOrNull()
    }

    fun ensureExtension(name: String, extension: String): String =
        if (name.lowercase().endsWith(".${extension.lowercase()}")) name else "$name.$extension"

    /** True when the app can write to the shared collections without a permission prompt. */
    fun canWriteToGallery(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @Suppress("unused")
    fun externalRoot(): File = Environment.getExternalStorageDirectory()
}

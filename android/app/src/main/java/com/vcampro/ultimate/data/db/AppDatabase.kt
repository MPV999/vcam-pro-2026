package com.vcampro.ultimate.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import kotlinx.coroutines.flow.Flow

/** A media item the user marked as favourite. Only the content Uri is stored. */
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val uri: String,
    val addedAt: Long
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long
)

@Entity(tableName = "album_items")
data class AlbumItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val albumId: Long,
    val uri: String
)

@Dao
interface MediaDao {

    @Query("SELECT uri FROM favorites")
    suspend fun favoriteUris(): List<String>

    @Query("SELECT uri FROM favorites ORDER BY addedAt DESC")
    fun favoriteUrisFlow(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE uri = :uri")
    suspend fun removeFavorite(uri: String)

    @Query("SELECT * FROM albums ORDER BY createdAt DESC")
    fun albums(): Flow<List<AlbumEntity>>

    @Insert
    suspend fun insertAlbum(album: AlbumEntity): Long

    @Query("DELETE FROM albums WHERE id = :albumId")
    suspend fun deleteAlbum(albumId: Long)

    @Query("DELETE FROM album_items WHERE albumId = :albumId")
    suspend fun deleteAlbumItems(albumId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToAlbum(entity: AlbumItemEntity)

    @Query("DELETE FROM album_items WHERE albumId = :albumId AND uri = :uri")
    suspend fun removeFromAlbum(albumId: Long, uri: String)

    @Query("SELECT * FROM album_items WHERE albumId = :albumId")
    suspend fun albumItems(albumId: Long): List<AlbumItemEntity>

    @Query("DELETE FROM favorites")
    suspend fun clearFavorites()

    @Query("DELETE FROM album_items")
    suspend fun clearAlbumItems()

    @Query("DELETE FROM albums")
    suspend fun clearAlbums()
}

@Database(
    entities = [FavoriteEntity::class, AlbumEntity::class, AlbumItemEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vcam_pro.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}

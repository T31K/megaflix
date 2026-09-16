package com.megaflix.tv.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    // ignore-on-conflict so a rescan never wipes positionMs of known files;
    // new files insert, known files keep their existing rows.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(items: List<VideoEntity>)

    @Query("SELECT * FROM videos WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): VideoEntity?

    @Query("SELECT * FROM videos ORDER BY addedEpochSec DESC")
    fun observeAll(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos ORDER BY addedEpochSec DESC")
    suspend fun getAll(): List<VideoEntity>

    @Query("UPDATE videos SET positionMs = :positionMs, durationMs = :durationMs WHERE uri = :uri")
    suspend fun updateProgress(uri: String, positionMs: Long, durationMs: Long)

    @Query("SELECT uri FROM videos")
    suspend fun allUris(): List<String>
}

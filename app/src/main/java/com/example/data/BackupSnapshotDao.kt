package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BackupSnapshotDao {
    @Query("SELECT * FROM backup_snapshots ORDER BY createdAt DESC LIMIT 30")
    fun getAllSnapshots(): Flow<List<BackupSnapshotEntity>>

    @Query("SELECT * FROM backup_snapshots WHERE provider = :provider ORDER BY createdAt DESC")
    suspend fun getSnapshotsByProvider(provider: String): List<BackupSnapshotEntity>

    @Query("SELECT * FROM backup_snapshots ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestSnapshot(): BackupSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: BackupSnapshotEntity): Long

    @Query("DELETE FROM backup_snapshots WHERE id = :id")
    suspend fun deleteSnapshotById(id: Long)
}

package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "backup_snapshots")
data class BackupSnapshotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val provider: String, // "LOCAL_VAULT", "LOCAL_AUTO", "GOOGLE_DRIVE", "ONEDRIVE", "SAF_EXPORT"
    val fileName: String,
    val localPath: String = "",
    val remoteFileId: String = "",
    val checksumSha256: String = "",
    val papersCount: Int = 0,
    val notesCount: Int = 0,
    val foldersCount: Int = 0,
    val attemptsCount: Int = 0,
    val sizeBytes: Long = 0L,
    val status: String = "VERIFIED", // "VERIFIED", "UPLOADED", "FAILED"
    val details: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

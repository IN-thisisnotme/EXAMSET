package com.example.model

import java.io.File

enum class CloudProviderType(val code: String, val displayName: String) {
    GOOGLE_DRIVE("GOOGLE_DRIVE", "Google Drive"),
    ONEDRIVE("ONEDRIVE", "OneDrive")
}

enum class AutoBackupSchedule(val code: String, val label: String, val intervalHours: Long) {
    OFF("OFF", "Manual Only", 0L),
    ON_SIGNIFICANT_CHANGE("ON_CHANGE", "Every 6h (When Online)", 6L),
    DAILY_WIFI("DAILY_WIFI", "Daily Auto Sync (Online)", 24L);

    companion object {
        fun fromCode(code: String?): AutoBackupSchedule {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: OFF
        }
    }
}

enum class RestoreMode(val label: String, val subtitle: String) {
    SMART_MERGE(
        label = "Smart Merge (Recommended)",
        subtitle = "Keeps existing items, adds missing records, and updates older versions without losing local work."
    ),
    FULL_REPLACE(
        label = "Clean Replace",
        subtitle = "Creates an automatic safety snapshot first, then replaces your local library with this vault."
    )
}

data class BackupCounts(
    val papersCount: Int = 0,
    val notesCount: Int = 0,
    val foldersCount: Int = 0,
    val attemptsCount: Int = 0,
    val analyticsDaysCount: Int = 0
)

data class BackupManifest(
    val schemaVersion: Int = 3,
    val appVersion: String = "1.0",
    val createdAt: Long = System.currentTimeMillis(),
    val deviceName: String = "",
    val counts: BackupCounts = BackupCounts(),
    val entryChecksumsSha256: Map<String, String> = emptyMap()
)

data class RestorePreview(
    val sourceLabel: String,
    val isLegacyJson: Boolean,
    val checksumVerified: Boolean,
    val checksumDetails: String,
    val manifest: BackupManifest,
    val tempFile: File?,
    val legacyJsonContent: String? = null
)

data class LinkedCloudFileStatus(
    val uriString: String = "",
    val displayName: String = "",
    val lastSyncedAt: Long = 0L,
    val lastError: String? = null
) {
    val isLinked: Boolean get() = uriString.isNotBlank()
}

data class CloudAccountStatus(
    val provider: CloudProviderType,
    val isConnected: Boolean = false,
    val hasClientIdConfigured: Boolean = false,
    val accountEmail: String = "",
    val accountName: String = "",
    val lastSyncedAt: Long = 0L,
    val lastError: String? = null
)

data class RemoteCloudBackupFile(
    val provider: CloudProviderType,
    val remoteId: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedAt: Long
)

sealed interface BackupOperationState {
    data object Idle : BackupOperationState
    data class Running(val phaseLabel: String, val progress: Float? = null) : BackupOperationState
    data class Success(val message: String) : BackupOperationState
    data class Error(val message: String) : BackupOperationState
}

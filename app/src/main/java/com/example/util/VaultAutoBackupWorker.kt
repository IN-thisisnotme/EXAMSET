package com.example.util

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.BackupSnapshotEntity
import com.example.data.CloudAuthStore
import com.example.data.ExamSetPreferences
import com.example.data.ThemePreferences
import com.example.model.AutoBackupSchedule
import com.example.model.CloudProviderType
import java.io.FileInputStream
import java.util.concurrent.TimeUnit

class VaultAutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val context = applicationContext
            val db = AppDatabase.getDatabase(context)
            val appPrefs = ExamSetPreferences(context)
            val themePrefs = ThemePreferences(context)
            val authStore = CloudAuthStore(context)

            val (snapshot, file) = VaultArchiveEngine.createLocalRollingSnapshot(
                context = context,
                db = db,
                appPrefs = appPrefs,
                themePrefs = themePrefs,
                providerTag = "LOCAL_AUTO",
                prefix = "auto"
            )
            authStore.setLastLocalSnapshotAt(snapshot.createdAt)

            // 1. Auto-sync to zero-config Linked Cloud File (Drive / OneDrive / Files SAF URI)
            val linkedFile = authStore.getLinkedCloudFileStatus()
            if (linkedFile.isLinked) {
                runCatching {
                    val uri = Uri.parse(linkedFile.uriString)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        FileInputStream(file).use { input ->
                            input.copyTo(out, bufferSize = 16 * 1024)
                        }
                    } ?: throw IllegalStateException("Linked cloud file is not writable.")
                    val now = TrustedTimeManager.getTrustedTimeMillis()
                    authStore.saveLinkedCloudFile(linkedFile.uriString, linkedFile.displayName, now)
                    db.backupSnapshotDao().insertSnapshot(
                        BackupSnapshotEntity(
                            provider = "CLOUD_FILE",
                            fileName = linkedFile.displayName.ifBlank { file.name },
                            localPath = file.absolutePath,
                            checksumSha256 = snapshot.checksumSha256,
                            papersCount = snapshot.papersCount,
                            notesCount = snapshot.notesCount,
                            foldersCount = snapshot.foldersCount,
                            attemptsCount = snapshot.attemptsCount,
                            sizeBytes = file.length(),
                            status = "UPLOADED",
                            details = snapshot.details
                        )
                    )
                }.onFailure { err ->
                    authStore.setLinkedCloudFileError(err.localizedMessage ?: "Auto-sync to linked file failed")
                }
            }

            // 2. Auto-sync to connected OAuth2 cloud providers (Google Drive / OneDrive) when online
            if (CloudBackupClients.isDeviceOnline(context)) {
                CloudProviderType.entries.forEach { provider ->
                    if (authStore.buildAccountStatus(provider).isConnected) {
                        val uploadRes = CloudBackupClients.uploadBackupFile(provider, authStore, file, context)
                        uploadRes.onSuccess { remote ->
                            db.backupSnapshotDao().insertSnapshot(
                                BackupSnapshotEntity(
                                    provider = provider.code,
                                    fileName = remote.name,
                                    localPath = file.absolutePath,
                                    remoteFileId = remote.remoteId,
                                    checksumSha256 = snapshot.checksumSha256,
                                    papersCount = snapshot.papersCount,
                                    notesCount = snapshot.notesCount,
                                    foldersCount = snapshot.foldersCount,
                                    attemptsCount = snapshot.attemptsCount,
                                    sizeBytes = remote.sizeBytes,
                                    status = "UPLOADED",
                                    details = snapshot.details
                                )
                            )
                        }
                    }
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "examset_vault_auto_backup"

        fun scheduleAutoBackup(context: Context, schedule: AutoBackupSchedule) {
            val wm = WorkManager.getInstance(context)
            if (schedule == AutoBackupSchedule.OFF || schedule.intervalHours <= 0L) {
                wm.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }
            // [DEPRECATED 2026-11-27] Reason: NetworkType.CONNECTED blocked offline local snapshots & SAF sync. Restore if: Only online auto-backups are desired.
            // .setRequiredNetworkType(NetworkType.CONNECTED)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<VaultAutoBackupWorker>(
                schedule.intervalHours,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            wm.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}

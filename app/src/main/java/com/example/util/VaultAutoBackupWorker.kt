package com.example.util

import android.content.Context
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

            CloudProviderType.entries.forEach { provider ->
                if (authStore.buildAccountStatus(provider).isConnected) {
                    val uploadRes = CloudBackupClients.uploadBackupFile(provider, authStore, file)
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
                                details = "Auto-synced to ${provider.displayName}"
                            )
                        )
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
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
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

package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.BackupSnapshotEntity
import com.example.model.AutoBackupSchedule
import com.example.model.BackupOperationState
import com.example.model.CloudAccountStatus
import com.example.model.CloudProviderType
import com.example.model.LinkedCloudFileStatus
import com.example.model.RemoteCloudBackupFile
import com.example.model.RestoreMode
import com.example.model.RestorePreview
import com.example.ui.MainViewModel
import com.example.ui.theme.ExamTheme
import com.example.util.CloudBackupClients
import com.example.util.VaultArchiveEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupVaultCenterSection(
    viewModel: MainViewModel
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current

    val savedPapers by viewModel.savedPapers.collectAsState()
    val studyNotes by viewModel.studyNotes.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val allAttempts by viewModel.allAttempts.collectAsState()
    val backupSnapshots by viewModel.backupSnapshots.collectAsState()
    val backupOpState by viewModel.backupOpState.collectAsState()
    val restorePreview by viewModel.restorePreview.collectAsState()
    val driveStatus by viewModel.driveStatus.collectAsState()
    val oneDriveStatus by viewModel.oneDriveStatus.collectAsState()
    val linkedCloudFileStatus by viewModel.linkedCloudFileStatus.collectAsState()
    val isNetworkTimeSynced by viewModel.isNetworkTimeSynced.collectAsState()
    val remoteCloudBackups by viewModel.remoteCloudBackups.collectAsState()
    val autoBackupSchedule by viewModel.autoBackupSchedule.collectAsState()

    var clientIdConfigProvider by remember { mutableStateOf<CloudProviderType?>(null) }

    val exportVaultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.exportVaultToUri(uri)
        }
    }

    val linkCloudFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            viewModel.linkAndSyncCloudFile(uri)
        }
    }

    val importVaultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.inspectBackupUri(uri)
        }
    }

    restorePreview?.let { preview ->
        RestorePreviewDialog(
            preview = preview,
            onDismiss = { viewModel.dismissRestorePreview() },
            onConfirmRestore = { mode -> viewModel.confirmRestore(mode) }
        )
    }

    clientIdConfigProvider?.let { provider ->
        CloudClientIdConfigDialog(
            provider = provider,
            initialClientId = viewModel.getCloudClientId(provider),
            onSave = { cid ->
                viewModel.configureCloudClientId(provider, cid)
                clientIdConfigProvider = null
            },
            onDismiss = { clientIdConfigProvider = null }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Streaming Backup Vault Hero Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.navyBanner)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = Color(0xFF5EEAD4), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ExamSet Backup Vault (.examset.zip)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.navyBannerText
                            )
                            Text(
                                text = "Streaming archive • SHA-256 verified • Zero crash limit",
                                fontSize = 11.sp,
                                color = colors.navyBannerText.copy(alpha = 0.8f)
                            )
                        }
                    }
                    Icon(Icons.Default.Shield, contentDescription = "Verified Archive", tint = Color(0xFF5EEAD4), modifier = Modifier.size(18.dp))
                }

                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 4-Pillar Library Coverage Summary
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        VaultStatPill(label = "${savedPapers.size} Question Sets", color = Color(0xFF2563EB))
                        VaultStatPill(label = "${studyNotes.size} Knowledge Sets", color = colors.teal)
                        VaultStatPill(label = "${folders.size} Folders", color = Color(0xFF7C3AED))
                        VaultStatPill(label = "${allAttempts.size} Attempts", color = colors.green)
                    }

                    // Live Operation Banner
                    when (val op = backupOpState) {
                        BackupOperationState.Idle -> Unit
                        is BackupOperationState.Running -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colors.blueLight,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.teal)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(op.phaseLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.inkBold)
                                    }
                                    if (op.progress != null) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { op.progress },
                                            color = colors.teal,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                        is BackupOperationState.Success -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colors.greenLight,
                                border = BorderStroke(0.8.dp, colors.green.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = colors.green, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(op.message, fontSize = 12.sp, color = colors.inkBold, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { viewModel.clearBackupOpState() }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                        is BackupOperationState.Error -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colors.redLight,
                                border = BorderStroke(0.8.dp, colors.red.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = colors.red, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(op.message, fontSize = 12.sp, color = colors.inkBold, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { viewModel.clearBackupOpState() }, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Primary Vault Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.createLocalVaultSnapshot() },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("create_vault_snapshot_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Local Vault", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { exportVaultLauncher.launch(VaultArchiveEngine.defaultVaultFileName()) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("export_vault_saf_button")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save to Drive / Files", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.shareVaultArchive(context) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("share_vault_zip_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = colors.teal, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share .zip File", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                importVaultLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*"))
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("restore_vault_picker_button")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore Vault / JSON", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // 2. Online Cloud Sync (Zero-Setup Cloud File Sync + Direct OAuth2 Google Drive & OneDrive)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(0.8.dp, colors.border)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ONLINE CLOUD SYNC",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkMuted,
                        letterSpacing = 0.5.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isNetworkTimeSynced) colors.greenLight else colors.amberLight,
                        border = BorderStroke(
                            0.6.dp,
                            if (isNetworkTimeSynced) colors.green.copy(alpha = 0.45f) else colors.amber.copy(alpha = 0.45f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 8.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = if (isNetworkTimeSynced) colors.green else colors.amber,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isNetworkTimeSynced) "Online Time Synced" else "Time Pending",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            IconButton(
                                onClick = { viewModel.refreshNetworkTimeSync() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("refresh_network_time_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh network time sync",
                                    tint = colors.inkMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                // Zero-Config Quick Cloud File Sync (Works immediately with Google Drive, OneDrive, Dropbox, Files)
                LinkedCloudFileSyncCard(
                    status = linkedCloudFileStatus,
                    onLinkFile = { linkCloudFileLauncher.launch("ExamSet_Live_Sync.examset.zip") },
                    onSyncNow = { viewModel.syncLinkedCloudFileNow() },
                    onRestore = { viewModel.restoreFromLinkedCloudFile() },
                    onUnlink = { viewModel.unlinkCloudFile() }
                )

                HorizontalDivider(color = colors.border.copy(alpha = 0.45f))

                Text(
                    text = "DIRECT OAUTH2 CLOUD SYNC (ADVANCED)",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 0.4.sp
                )
                Text(
                    text = "Optional: Connect your own Google Cloud or Microsoft Entra OAuth Client ID for direct API sync to your cloud app folder.",
                    fontSize = 11.5.sp,
                    color = colors.inkMuted
                )

                CloudProviderSyncCard(
                    status = driveStatus,
                    onConfigureClientId = { clientIdConfigProvider = CloudProviderType.GOOGLE_DRIVE },
                    onConnect = { viewModel.startCloudOAuth(context, CloudProviderType.GOOGLE_DRIVE) },
                    onSyncNow = { viewModel.syncVaultToCloud(CloudProviderType.GOOGLE_DRIVE) },
                    onBrowseBackups = { viewModel.loadRemoteBackups(CloudProviderType.GOOGLE_DRIVE) },
                    onDisconnect = { viewModel.disconnectCloud(CloudProviderType.GOOGLE_DRIVE) }
                )

                CloudProviderSyncCard(
                    status = oneDriveStatus,
                    onConfigureClientId = { clientIdConfigProvider = CloudProviderType.ONEDRIVE },
                    onConnect = { viewModel.startCloudOAuth(context, CloudProviderType.ONEDRIVE) },
                    onSyncNow = { viewModel.syncVaultToCloud(CloudProviderType.ONEDRIVE) },
                    onBrowseBackups = { viewModel.loadRemoteBackups(CloudProviderType.ONEDRIVE) },
                    onDisconnect = { viewModel.disconnectCloud(CloudProviderType.ONEDRIVE) }
                )

                if (remoteCloudBackups.isNotEmpty()) {
                    HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
                    Text(
                        text = "Cloud Backups Available (${remoteCloudBackups.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    remoteCloudBackups.take(6).forEach { remote ->
                        RemoteBackupRow(
                            file = remote,
                            onRestore = { viewModel.restoreFromRemoteBackup(remote) }
                        )
                    }
                }
            }
        }

        // 3. Auto-Backup Cadence & Local Vault Snapshots
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            border = BorderStroke(0.8.dp, colors.border)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "AUTO-BACKUP SCHEDULE & LOCAL ROLLING SNAPSHOTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 0.5.sp
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AutoBackupSchedule.entries.forEach { schedule ->
                        FilterChip(
                            selected = autoBackupSchedule == schedule,
                            onClick = { viewModel.setAutoBackupSchedule(schedule) },
                            label = { Text(schedule.label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.tealLight,
                                selectedLabelColor = colors.tealDark
                            )
                        )
                    }
                }

                if (backupSnapshots.isEmpty()) {
                    Text(
                        text = "No snapshots recorded yet. Tap \"Create Local Vault\" above to verify and store your first snapshot.",
                        fontSize = 11.5.sp,
                        color = colors.inkMuted
                    )
                } else {
                    HorizontalDivider(color = colors.border.copy(alpha = 0.5f))
                    backupSnapshots.take(8).forEach { snap ->
                        SnapshotHistoryRow(
                            snapshot = snap,
                            onRestore = { viewModel.inspectLocalBackupSnapshot(snap) },
                            onDelete = { viewModel.deleteLocalBackupSnapshot(snap) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultStatPill(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(0.6.dp, color.copy(alpha = 0.3f))
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkedCloudFileSyncCard(
    status: LinkedCloudFileStatus,
    onLinkFile: () -> Unit,
    onSyncNow: () -> Unit,
    onRestore: () -> Unit,
    onUnlink: () -> Unit
) {
    val colors = ExamTheme.colors
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (status.isLinked) colors.tealLight.copy(alpha = 0.35f) else colors.blueLight.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, if (status.isLinked) colors.teal.copy(alpha = 0.6f) else colors.teal.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (status.isLinked) Icons.Default.CloudDone else Icons.Default.Link,
                    contentDescription = null,
                    tint = colors.teal,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Quick Cloud File Sync (Zero Setup)",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    Text(
                        text = if (status.isLinked) {
                            status.displayName.ifBlank { "ExamSet_Live_Sync.examset.zip" }
                        } else {
                            "Pick a file in Google Drive, OneDrive, or Files once for 1-tap & automatic background sync — no Client ID required."
                        },
                        fontSize = 11.sp,
                        color = colors.inkMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (status.isLinked && status.lastSyncedAt > 0L) {
                val formatted = remember(status.lastSyncedAt) {
                    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(status.lastSyncedAt))
                }
                Text(
                    text = "Last synced: $formatted",
                    fontSize = 11.sp,
                    color = colors.tealDark,
                    fontWeight = FontWeight.Medium
                )
            }
            if (!status.lastError.isNullOrBlank()) {
                Text(
                    text = status.lastError,
                    fontSize = 11.sp,
                    color = colors.red
                )
            }

            if (!status.isLinked) {
                Button(
                    onClick = onLinkFile,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("link_cloud_file_button")
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Link Cloud File (Drive / OneDrive / Files)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onSyncNow,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("sync_linked_cloud_file_button")
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Sync Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onRestore,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("restore_linked_cloud_file_button")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Restore", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onLinkFile,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Change File", fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = onUnlink,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Unlink", fontSize = 12.sp, color = colors.red)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudProviderSyncCard(
    status: CloudAccountStatus,
    onConfigureClientId: () -> Unit,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit,
    onBrowseBackups: () -> Unit,
    onDisconnect: () -> Unit
) {
    val colors = ExamTheme.colors
    val accent = if (status.provider == CloudProviderType.GOOGLE_DRIVE) Color(0xFF0F9D58) else Color(0xFF0078D4)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(0.8.dp, if (status.isConnected) accent.copy(alpha = 0.5f) else colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = if (status.isConnected) Icons.Default.CloudDone else Icons.Default.Cloud,
                        contentDescription = null,
                        tint = if (status.isConnected) accent else colors.inkMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = status.provider.displayName,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                        val sub = when {
                            status.isConnected && status.accountEmail.isNotBlank() -> status.accountEmail
                            status.isConnected -> "Connected"
                            status.hasClientIdConfigured -> "Client ID set — ready to connect"
                            else -> "Tap Client ID to configure OAuth"
                        }
                        Text(text = sub, fontSize = 11.sp, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                IconButton(onClick = onConfigureClientId, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Settings, contentDescription = "Configure ${status.provider.displayName} Client ID", tint = colors.inkMuted, modifier = Modifier.size(18.dp))
                }
            }

            if (status.lastSyncedAt > 0L) {
                val formatted = remember(status.lastSyncedAt) {
                    SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(status.lastSyncedAt))
                }
                Text("Last synced: $formatted", fontSize = 10.5.sp, color = colors.tealDark, fontWeight = FontWeight.Medium)
            }
            if (!status.lastError.isNullOrBlank()) {
                Text(status.lastError, fontSize = 10.5.sp, color = colors.red)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!status.isConnected) {
                    OutlinedButton(
                        onClick = onConfigureClientId,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Client ID", fontSize = 12.sp)
                    }
                    Button(
                        onClick = if (status.hasClientIdConfigured) onConnect else onConfigureClientId,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(if (status.hasClientIdConfigured) "Connect Account" else "Setup & Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onSyncNow,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Sync Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onBrowseBackups,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("List Backups", fontSize = 12.sp)
                    }
                    TextButton(onClick = onDisconnect, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Disconnect", fontSize = 12.sp, color = colors.red)
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteBackupRow(
    file: RemoteCloudBackupFile,
    onRestore: () -> Unit
) {
    val colors = ExamTheme.colors
    val dateStr = remember(file.modifiedAt) {
        if (file.modifiedAt > 0L) SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(file.modifiedAt)) else ""
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.blueLight,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.inkBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${file.provider.displayName} • ${formatBytes(file.sizeBytes)}${if (dateStr.isNotBlank()) " • $dateStr" else ""}",
                    fontSize = 10.5.sp,
                    color = colors.inkMuted
                )
            }
            OutlinedButton(onClick = onRestore, modifier = Modifier.heightIn(min = 38.dp)) {
                Text("Restore", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SnapshotHistoryRow(
    snapshot: BackupSnapshotEntity,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = ExamTheme.colors
    val dateStr = remember(snapshot.createdAt) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(snapshot.createdAt))
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(0.6.dp, colors.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(4.dp), color = colors.tealLight) {
                        Text(
                            text = snapshot.provider.replace("_", " "),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.tealDark,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                    Text(dateStr, fontSize = 10.5.sp, color = colors.inkMuted)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${snapshot.papersCount} sets • ${snapshot.notesCount} knowledge • ${formatBytes(snapshot.sizeBytes)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.inkBold
                )
                if (snapshot.checksumSha256.isNotBlank()) {
                    Text(
                        text = "SHA-256: ${snapshot.checksumSha256.take(12)}…",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = colors.inkMuted
                    )
                }
            }
            if (snapshot.localPath.isNotBlank()) {
                OutlinedButton(
                    onClick = onRestore,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 38.dp)
                ) {
                    Text("Restore", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete snapshot", tint = colors.red, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun RestorePreviewDialog(
    preview: RestorePreview,
    onDismiss: () -> Unit,
    onConfirmRestore: (RestoreMode) -> Unit
) {
    val colors = ExamTheme.colors
    var selectedMode by remember { mutableStateOf(RestoreMode.SMART_MERGE) }
    val counts = preview.manifest.counts

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Inspect & Restore Vault", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(preview.sourceLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (preview.checksumVerified) colors.greenLight else colors.amberLight,
                    border = BorderStroke(0.8.dp, if (preview.checksumVerified) colors.green else colors.amber),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = preview.checksumDetails,
                        fontSize = 11.5.sp,
                        color = colors.inkBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Contents Verified:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.inkMuted)
                        Text("• ${counts.papersCount} Question Sets", fontSize = 12.sp, color = colors.inkBold)
                        Text("• ${counts.notesCount} Knowledge Sets", fontSize = 12.sp, color = colors.inkBold)
                        Text("• ${counts.foldersCount} Folders", fontSize = 12.sp, color = colors.inkBold)
                        Text("• ${counts.attemptsCount} Test Attempts • ${counts.analyticsDaysCount} Study Days", fontSize = 12.sp, color = colors.inkBold)
                    }
                }

                Text("Choose Restore Mode:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                RestoreMode.entries.forEach { mode ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedMode == mode) colors.tealLight.copy(alpha = 0.45f) else colors.surface,
                        border = BorderStroke(1.dp, if (selectedMode == mode) colors.teal else colors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = mode }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            RadioButton(
                                selected = selectedMode == mode,
                                onClick = { selectedMode = mode },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.teal)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(mode.label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
                                Text(mode.subtitle, fontSize = 11.sp, color = colors.inkMuted, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmRestore(selectedMode) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.teal)
            ) {
                Text("Restore Now", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CloudClientIdConfigDialog(
    provider: CloudProviderType,
    initialClientId: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    var clientId by remember { mutableStateOf(initialClientId) }

    fun copyValue(label: String, value: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${provider.displayName} OAuth Setup", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (provider == CloudProviderType.GOOGLE_DRIVE) {
                        "Create an Android OAuth Client ID in Google Cloud Console with the Package Name below, enable Custom URI Scheme, and enable Google Drive API (scope: drive.file)."
                    } else {
                        "Register a Mobile / Public Client app in Microsoft Entra ID with the Redirect URI below (scope: Files.ReadWrite.AppFolder)."
                    },
                    fontSize = 12.sp,
                    color = colors.inkMuted
                )

                // Package Name copy row
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Android Package Name:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.inkMuted)
                            Text(
                                text = BuildConfig.APPLICATION_ID,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.inkBold
                            )
                        }
                        IconButton(
                            onClick = { copyValue("Package Name", BuildConfig.APPLICATION_ID) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Package Name", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Redirect URI copy row
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Redirect URI:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.inkMuted)
                            Text(
                                text = CloudBackupClients.REDIRECT_URI,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.inkBold
                            )
                        }
                        IconButton(
                            onClick = { copyValue("Redirect URI", CloudBackupClients.REDIRECT_URI) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Redirect URI", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("OAuth Client ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(clientId.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        }
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    return if (kb < 1024.0) String.format(Locale.US, "%.1f KB", kb)
    else String.format(Locale.US, "%.2f MB", kb / 1024.0)
}

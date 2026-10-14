package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.theme.ExamTheme
import kotlinx.coroutines.launch

@Composable
fun ShareSystemTab(
    viewModel: MainViewModel,
    onNavigateToMaster: () -> Unit,
    onOpenPsychometrics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentPaper by viewModel.currentPaper.collectAsState()
    val savedPapers by viewModel.savedPapers.collectAsState()

    var pastedJson by remember { mutableStateOf("") }
    var copiedJson by remember { mutableStateOf(false) }
    var copiedManifest by remember { mutableStateOf(false) }

    var pendingExportData by remember { mutableStateOf<String?>(null) }
    var pendingExportMimeType by remember { mutableStateOf("application/json") }

    // Storage Access Framework launcher to save file directly to device File Manager
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(pendingExportMimeType)
    ) { uri: Uri? ->
        if (uri != null && pendingExportData != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.bufferedWriter(Charsets.UTF_8).write(pendingExportData!!)
                }
                Toast.makeText(context, "Saved file successfully to device!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to write file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Concurrent multi-file import launcher
    val multipleFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.batchValidateUris(uris) { batchItems ->
                coroutineScope.launch {
                    val res = viewModel.importValidatedBatch(batchItems)
                    Toast.makeText(context, "Imported ${res.importedCount} question set(s) successfully!", Toast.LENGTH_SHORT).show()
                    if (res.importedCount > 0) {
                        onNavigateToMaster()
                    }
                }
            }
        }
    }

    fun triggerExportToFile(data: String, defaultName: String, mime: String) {
        pendingExportData = data
        pendingExportMimeType = mime
        createDocLauncher.launch(defaultName)
    }

    fun triggerShare(data: String, title: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, data)
            putExtra(Intent.EXTRA_SUBJECT, title)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Question Sets"))
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Top Action Header
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF7C3AED).copy(alpha = 0.12f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = Color(0xFF7C3AED),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Share & Interop System",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "Export and import question sets to File Manager or external apps",
                                fontSize = 11.5.sp,
                                color = colors.inkMuted
                            )
                        }
                    }
                }
            }
        }

        // SECTION: EXPORT ALL SAVED QUESTION SETS (BULK BACKUP / SHARE)
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = colors.teal, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "EXPORT ALL QUESTION SETS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkMuted,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = "${savedPapers.size} Saved Sets",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.teal
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Backup all stored exam papers to your phone storage or share them with teachers and students.",
                        fontSize = 12.sp,
                        color = colors.inkMuted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (savedPapers.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Export All as JSON File
                                Button(
                                    onClick = {
                                        val json = viewModel.exportAllPapersAsJson()
                                        if (!json.isNullOrBlank()) {
                                            triggerExportToFile(json, "examset_all_${savedPapers.size}_papers.json", "application/json")
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save as JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                // Export All as Text File
                                Button(
                                    onClick = {
                                        val textData = viewModel.exportPapersAsFormattedText()
                                        if (!textData.isNullOrBlank()) {
                                            triggerExportToFile(textData, "examset_all_${savedPapers.size}_papers.txt", "text/plain")
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.navyBanner),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save as TXT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Share All via Android Apps
                            OutlinedButton(
                                onClick = {
                                    val json = viewModel.exportAllPapersAsJson()
                                    if (!json.isNullOrBlank()) {
                                        triggerShare(json, "ExamSet Backup (${savedPapers.size} Question Sets)")
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.teal)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share All via Apps (WhatsApp / Drive / Gmail)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        Text(
                            text = "No saved papers to export. Create or paste question sets first.",
                            fontSize = 11.5.sp,
                            color = colors.inkMuted
                        )
                    }
                }
            }
        }

        // SECTION: ACTIVE QUESTION PAPER SHARING
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ACTIVE QUESTION PAPER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkMuted,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (currentPaper != null) {
                        val paper = currentPaper!!
                        Text(
                            text = paper.metadata.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                        Text(
                            text = "${paper.metadata.subject} • ${paper.metadata.setNumber} • ${paper.questions.size} Questions",
                            fontSize = 12.sp,
                            color = colors.inkMuted
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Core Identifier Badges
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.tealLight,
                                border = BorderStroke(0.6.dp, colors.teal.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "Set: ${paper.metadata.setNumber}",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.teal,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (!paper.metadata.folderId.isNullOrBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = colors.surfaceVariant,
                                    border = BorderStroke(0.6.dp, colors.border)
                                ) {
                                    Text(
                                        text = "Folder: ${paper.metadata.folderId}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colors.inkBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFEFF6FF),
                                border = BorderStroke(0.6.dp, Color(0xFF3B82F6).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "Roll: ${paper.metadata.rollNumber}",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D4ED8),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFDF2F8),
                                border = BorderStroke(0.6.dp, Color(0xFFEC4899).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "Booklet: ${paper.metadata.bookletNumber}",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFBE185D),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Copy JSON
                            Button(
                                onClick = {
                                    val json = viewModel.exportCurrentPaperAsJson()
                                    if (!json.isNullOrBlank()) {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("ExamSet JSON", json)
                                        clipboard.setPrimaryClip(clip)
                                        copiedJson = true
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (copiedJson) Color(0xFF059669) else Color(0xFF2563EB)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    if (copiedJson) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (copiedJson) "Copied!" else "Copy JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // Save Active to File
                            Button(
                                onClick = {
                                    val json = viewModel.exportCurrentPaperAsJson()
                                    if (!json.isNullOrBlank()) {
                                        val name = "exam_${paper.metadata.title.take(20).replace(" ", "_")}.json"
                                        triggerExportToFile(json, name, "application/json")
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save File", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // Share via Apps
                            OutlinedButton(
                                onClick = {
                                    val json = viewModel.exportCurrentPaperAsJson()
                                    if (!json.isNullOrBlank()) {
                                        triggerShare(json, "${paper.metadata.title} - ExamSet JSON")
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        Text(
                            text = "No active paper loaded. Open a saved set or load one in Question Creator.",
                            fontSize = 12.sp,
                            color = colors.inkMuted
                        )
                    }
                }
            }
        }

        // SECTION: CONCURRENT IMPORT QUESTION SETS
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CONCURRENT BATCH IMPORT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkMuted,
                            letterSpacing = 0.5.sp
                        )

                        OutlinedButton(
                            onClick = {
                                multipleFilePicker.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pick Files", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Import multiple files simultaneously or paste single/bundled JSON arrays.",
                        fontSize = 11.5.sp,
                        color = colors.inkMuted
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = pastedJson,
                        onValueChange = { pastedJson = it },
                        placeholder = {
                            Text(
                                text = "Paste ExamSet JSON payload or JSON array of papers here...",
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = colors.inkMuted.copy(alpha = 0.6f)
                            )
                        },
                        textStyle = TextStyle(
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = colors.inkBold
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (pastedJson.isNotBlank()) {
                                coroutineScope.launch {
                                    if (pastedJson.trim().startsWith("[")) {
                                        viewModel.importMultiplePapersFromJson(pastedJson)
                                    } else {
                                        val success = viewModel.importPaperFromJson(pastedJson)
                                        if (success) {
                                            pastedJson = ""
                                            onNavigateToMaster()
                                        }
                                    }
                                }
                            }
                        },
                        enabled = pastedJson.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Validate & Import Papers", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // SECTION: OMR EVALUATION & CANDIDATE MANIFEST (CSV)
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "OMR EVALUATION MANIFEST (CSV)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0284C7),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Exports standardized matrix of Roll Numbers (10-digit), Booklet Numbers (7-digit), Set Codes, and Answer Keys for mass student scanning and psychometric analysis.",
                        fontSize = 11.5.sp,
                        color = colors.inkMuted
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val csv = viewModel.exportOmrCohortManifestCsv()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("OMR Manifest CSV", csv)
                                clipboard.setPrimaryClip(clip)
                                copiedManifest = true
                                Toast.makeText(context, "Copied OMR Manifest CSV to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (copiedManifest) Color(0xFF059669) else Color(0xFF0284C7)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (copiedManifest) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (copiedManifest) "Copied CSV!" else "Copy CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val csv = viewModel.exportOmrCohortManifestCsv()
                                triggerExportToFile(csv, "omr_candidate_manifest.csv", "text/csv")
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val csv = viewModel.exportOmrCohortManifestCsv()
                                triggerShare(csv, "OMR Candidate Manifest CSV")
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // SECTION: PSYCHOMETRIC & CLASSICAL TEST THEORY (CTT) ANALYSIS
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PSYCHOMETRIC & ITEM ANALYSIS (CTT)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0284C7),
                            letterSpacing = 0.5.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFF0FDF4)
                        ) {
                            Text(
                                text = "CALIBRATION READY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Analyzes item facility / difficulty (p-value), point-biserial discrimination (r_pb), distractor efficacy, Cronbach's alpha test reliability, and candidate scorecards with individual report card export.",
                        fontSize = 11.5.sp,
                        color = colors.inkMuted
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onOpenPsychometrics,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Psychometric Analysis Suite", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val csv = viewModel.exportPsychometricReportCsv(allowSimulationFallback = true)
                                triggerExportToFile(csv, "item_psychometrics_report.csv", "text/csv")
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Item CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val csv = viewModel.exportCandidateScorecardsCsv(allowSimulationFallback = true)
                                triggerExportToFile(csv, "student_scorecards_roster.csv", "text/csv")
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Roster CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val cardsText = viewModel.exportAllStudentReportCardsText(allowSimulationFallback = true)
                                triggerExportToFile(cardsText, "candidate_report_cards.txt", "text/plain")
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Report Cards", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}


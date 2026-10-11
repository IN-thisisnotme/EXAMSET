package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.BatchImportItem
import com.example.model.QuestionOptions
import com.example.model.McqQuestion
import com.example.ui.MainViewModel
import com.example.ui.components.StructuredQuestionContent
import com.example.ui.components.BatchImportDialog
import com.example.ui.theme.ExamTheme
import com.example.util.CameraCaptureHelper
import com.example.util.FeatureToggles
import com.example.util.HandwrittenNotesExtractor
import com.example.util.NotesExtractionResult
import kotlinx.coroutines.launch

/**
 * Exam Tools & Diagnostics Tab (formerly Creator Tab).
 *
 * Fully redesigned to protect active sets against accidental overwrites:
 * - Removed raw text paste and question form fields that caused active question set loss.
 * - Added comprehensive Reading Comfort & Font Sizing controls with live preview.
 * - Active Paper Quality & Diagnostics Scorecard.
 * - Safe Backup & Batch JSON Export / Import Hub.
 * - Safe Read-Only Question Inspector.
 */
@Composable
fun CreatorTab(
    viewModel: MainViewModel,
    onNavigateToMaster: () -> Unit,
    onNavigateToTests: () -> Unit = {},
    onNavigateToPrint: () -> Unit = {},
    onEditMetadata: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current

    val currentPaper by viewModel.currentPaper.collectAsState()
    // val fontSizeScale by viewModel.fontSizeScale.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val savedPapers by viewModel.savedPapers.collectAsState()

    var previewMode by remember { mutableStateOf("live") } // "live" or "list"
    var selectedPreviewIndex by remember { mutableIntStateOf(0) }
    var batchImportItems by remember { mutableStateOf<List<BatchImportItem>>(emptyList()) }
    var showBatchImportDialog by remember { mutableStateOf(false) }

    val batchFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            viewModel.batchValidateUris(uris) { validatedItems ->
                batchImportItems = batchImportItems + validatedItems
                showBatchImportDialog = true
            }
        }
    }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isOcrScanning by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<NotesExtractionResult?>(null) }
    var showScanResultDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { isSaved ->
        if (isSaved && pendingCameraUri != null) {
            val uriToProcess = pendingCameraUri!!
            isOcrScanning = true
            coroutineScope.launch {
                val result = HandwrittenNotesExtractor.extractFromImageUri(context, uriToProcess)
                isOcrScanning = false
                scanResult = result
                showScanResultDialog = true
            }
        }
    }

    val paper = currentPaper
    val detectedQuestions = paper?.questions ?: emptyList()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // 1. Top Active Set Overview & Quick Action Card
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colors.teal.copy(alpha = 0.12f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = colors.teal,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Exam Tools & Diagnostics",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkBold
                                )
                                Text(
                                    text = "Active Set Inspector & Readability Settings",
                                    fontSize = 12.sp,
                                    color = colors.inkMuted
                                )
                            }
                        }

                        // Protection Badge
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.greenLight,
                            border = BorderStroke(0.6.dp, colors.green.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = colors.green,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Active Set Protected",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.green
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Active Paper Metadata Banner
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(0.6.dp, colors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = paper?.metadata?.title?.ifBlank { "No Question Set Loaded" } ?: "No Question Set Loaded",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (paper != null) {
                                        Text(
                                            text = "${paper.metadata.subject.ifBlank { "General" }} • ${paper.metadata.topic.ifBlank { "All Topics" }}",
                                            fontSize = 12.sp,
                                            color = colors.inkMuted
                                        )
                                    }
                                }

                                if (paper != null) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.navyBanner
                                    ) {
                                        Text(
                                            text = paper.metadata.setNumber.ifBlank { "SET A" }.uppercase(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.navyBannerText,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick Action Navigation Buttons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (FeatureToggles.ENABLE_CAMERA_NOTES_SCANNER) {
                                    Button(
                                        onClick = {
                                            val tempUri = CameraCaptureHelper.createTempPictureUri(context)
                                            if (tempUri != null) {
                                                pendingCameraUri = tempUri
                                                takePhotoLauncher.launch(tempUri)
                                            } else {
                                                Toast.makeText(context, "Unable to access camera storage.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                        modifier = Modifier
                                            .heightIn(min = 38.dp)
                                            .testTag("scan_notes_quick_button")
                                    ) {
                                        if (isOcrScanning) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Scanning...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        } else {
                                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Scan Notes", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Button(
                                    onClick = onNavigateToMaster,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (FeatureToggles.ENABLE_CAMERA_NOTES_SCANNER) colors.navyBanner else colors.teal
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                    modifier = Modifier.heightIn(min = 38.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Master View", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }

                                OutlinedButton(
                                    onClick = onNavigateToTests,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                                    border = BorderStroke(1.dp, colors.border),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                    modifier = Modifier.heightIn(min = 38.dp)
                                ) {
                                    Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.teal)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Practice Test", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = onNavigateToPrint,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                                    border = BorderStroke(1.dp, colors.border),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                    modifier = Modifier.heightIn(min = 38.dp)
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.ink)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Print & OMR", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }

                                OutlinedButton(
                                    onClick = onEditMetadata,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                                    border = BorderStroke(1.dp, colors.border),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                                    modifier = Modifier.heightIn(min = 38.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.inkMuted)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Edit Info", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dedicated Camera Notes & Question Scanner Card
        if (FeatureToggles.ENABLE_CAMERA_NOTES_SCANNER) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                    border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.teal.copy(alpha = 0.12f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = null,
                                            tint = colors.teal,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Scan Handwritten Notes",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold
                                    )
                                    Text(
                                        text = "On-device AI / OCR document recognition",
                                        fontSize = 11.5.sp,
                                        color = colors.inkMuted
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.greenLight,
                                border = BorderStroke(0.5.dp, colors.green.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "Offline OCR",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.green,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Capture a photo of handwritten question notes or printed test sheets. The offline OCR engine reconstructs MCQ statements, options (A)-(D), answer keys, and pedagogical explanations with zero API keys.",
                            fontSize = 12.sp,
                            color = colors.ink,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val tempUri = CameraCaptureHelper.createTempPictureUri(context)
                                if (tempUri != null) {
                                    pendingCameraUri = tempUri
                                    takePhotoLauncher.launch(tempUri)
                                } else {
                                    Toast.makeText(context, "Unable to access camera storage.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .testTag("capture_notes_scan_button")
                        ) {
                            if (isOcrScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scanning Notes...", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            } else {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Take Photo & Scan", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // 2. Reading Comfort & Font Sizing Controller (Direct user request)
        // item {
        //     Card(
        //         shape = RoundedCornerShape(14.dp),
        //         colors = CardDefaults.cardColors(containerColor = colors.surface),
        //         border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.4f))
        //     ) {
        //         Column(modifier = Modifier.padding(16.dp)) {
        //             Row(
        //                 modifier = Modifier.fillMaxWidth(),
        //                 horizontalArrangement = Arrangement.SpaceBetween,
        //                 verticalAlignment = Alignment.CenterVertically
        //             ) {
        //                 Row(verticalAlignment = Alignment.CenterVertically) {
        //                     Surface(
        //                         shape = RoundedCornerShape(8.dp),
        //                         color = Color(0xFF6366F1).copy(alpha = 0.12f),
        //                         modifier = Modifier.size(36.dp)
        //                     ) {
        //                         Box(contentAlignment = Alignment.Center) {
        //                             Icon(
        //                                 imageVector = Icons.Default.FormatSize,
        //                                 contentDescription = null,
        //                                 tint = Color(0xFF6366F1),
        //                                 modifier = Modifier.size(20.dp)
        //                             )
        //                         }
        //                     }
        //                     Spacer(modifier = Modifier.width(10.dp))
        //                     Column {
        //                         Text(
        //                             text = "Reading Comfort & Font Sizing",
        //                             fontSize = 15.sp,
        //                             fontWeight = FontWeight.Bold,
        //                             color = colors.inkBold
        //                         )
        //                         Text(
        //                             text = "Eye-saver modes for long study and test sessions",
        //                             fontSize = 11.5.sp,
        //                             color = colors.inkMuted
        //                         )
        //                     }
        //                 }
        //             }
        //
        //             Spacer(modifier = Modifier.height(14.dp))
        //
        //             // 3 Font Size Presets (Standard, Large, Extra Large)
        //             Row(
        //                 modifier = Modifier.fillMaxWidth(),
        //                 horizontalArrangement = Arrangement.spacedBy(8.dp)
        //             ) {
        //                 val sizeOptions = listOf(
        //                     Triple(1.0f, "Standard", "16sp Crisp"),
        //                     Triple(1.2f, "Large", "+20% Easy Read"),
        //                     Triple(1.4f, "Extra Large", "+40% Eye Saver")
        //                 )
        //
        //                 sizeOptions.forEach { (scale, label, sub) ->
        //                     val isSelected = (fontSizeScale - scale) in -0.05f..0.05f
        //                     Surface(
        //                         modifier = Modifier
        //                             .weight(1f)
        //                             .clickable {
        //                                 viewModel.setFontSizeScale(scale)
        //                                 Toast.makeText(context, "Font size set to $label ($sub)", Toast.LENGTH_SHORT).show()
        //                             },
        //                         shape = RoundedCornerShape(10.dp),
        //                         color = if (isSelected) colors.tealLight else colors.surfaceVariant.copy(alpha = 0.4f),
        //                         border = BorderStroke(
        //                             if (isSelected) 1.5.dp else 0.8.dp,
        //                             if (isSelected) colors.teal else colors.border
        //                         )
        //                     ) {
        //                         Column(
        //                             modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        //                             horizontalAlignment = Alignment.CenterHorizontally
        //                         ) {
        //                             Row(verticalAlignment = Alignment.CenterVertically) {
        //                                 if (isSelected) {
        //                                     Icon(
        //                                         imageVector = Icons.Default.Check,
        //                                         contentDescription = null,
        //                                         tint = colors.teal,
        //                                         modifier = Modifier.size(13.dp)
        //                                     )
        //                                     Spacer(modifier = Modifier.width(3.dp))
        //                                 }
        //                                 Text(
        //                                     text = label,
        //                                     fontSize = 12.5.sp,
        //                                     fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
        //                                     color = if (isSelected) colors.tealDark else colors.inkBold
        //                                 )
        //                             }
        //                             Spacer(modifier = Modifier.height(2.dp))
        //                             Text(
        //                                 text = sub,
        //                                 fontSize = 10.sp,
        //                                 color = if (isSelected) colors.tealDark else colors.inkMuted,
        //                                 maxLines = 1,
        //                                 overflow = TextOverflow.Ellipsis
        //                             )
        //                         }
        //                     }
        //                 }
        //             }
        //
        //             Spacer(modifier = Modifier.height(12.dp))
        //
        //             // Live Interactive Typography Preview
        //             Surface(
        //                 shape = RoundedCornerShape(10.dp),
        //                 color = colors.surfaceVariant.copy(alpha = 0.35f),
        //                 border = BorderStroke(0.6.dp, colors.border),
        //                 modifier = Modifier.fillMaxWidth()
        //             ) {
        //                 Column(modifier = Modifier.padding(12.dp)) {
        //                     Row(
        //                         modifier = Modifier.fillMaxWidth(),
        //                         horizontalArrangement = Arrangement.SpaceBetween,
        //                         verticalAlignment = Alignment.CenterVertically
        //                     ) {
        //                         Text(
        //                             text = "LIVE TYPOGRAPHY PREVIEW",
        //                             fontSize = 11.sp,
        //                             fontWeight = FontWeight.Bold,
        //                             color = colors.inkMuted,
        //                             letterSpacing = 0.5.sp
        //                         )
        //                         Text(
        //                             text = "Comfortable for hours",
        //                             fontSize = 10.5.sp,
        //                             color = colors.teal,
        //                             fontWeight = FontWeight.SemiBold
        //                         )
        //                     }
        //                     Spacer(modifier = Modifier.height(8.dp))
        //                     val sampleQuestion = remember {
        //                         McqQuestion(
        //                             num = 1,
        //                             question = "Which mechanism contributes primarily to genetic variation during meiosis in eukaryotic cells?",
        //                             options = QuestionOptions(
        //                                 a = "Crossing over (homologous recombination) during prophase I",
        //                                 b = "DNA replication proofreading errors in S phase",
        //                                 c = "Independent assortment during cytokinesis II",
        //                                 d = "Transcriptional gene silencing"
        //                             ),
        //                             correctAnswer = "A",
        //                             explanation = "Crossing over during prophase I exchanges maternal and paternal chromatid segments, generating novel allele combinations."
        //                         )
        //                     }
        //                     StructuredQuestionContent(
        //                         question = sampleQuestion,
        //                         showAnswerIndicator = true
        //                     )
        //                 }
        //             }
        //         }
        //     }
        // }

        // 3. Exam Quality Scorecard & Diagnostics
        // item {
        //     Card(
        //         shape = RoundedCornerShape(14.dp),
        //         colors = CardDefaults.cardColors(containerColor = colors.surface),
        //         border = BorderStroke(0.8.dp, colors.border)
        //     ) {
        //         Column(modifier = Modifier.padding(16.dp)) {
        //             Row(
        //                 modifier = Modifier.fillMaxWidth(),
        //                 horizontalArrangement = Arrangement.SpaceBetween,
        //                 verticalAlignment = Alignment.CenterVertically
        //             ) {
        //                 Text(
        //                     text = "SET QUALITY & DIAGNOSTICS",
        //                     fontSize = 11.5.sp,
        //                     fontWeight = FontWeight.Bold,
        //                     color = colors.inkMuted,
        //                     letterSpacing = 0.5.sp
        //                 )

        //                 Surface(
        //                     shape = RoundedCornerShape(4.dp),
        //                     color = Color(0xFFD1FAE5),
        //                     border = BorderStroke(0.5.dp, Color(0xFF10B981))
        //                 ) {
        //                     Text(
        //                         text = "Database Synced",
        //                         fontSize = 10.5.sp,
        //                         fontWeight = FontWeight.Bold,
        //                         color = Color(0xFF047857),
        //                         modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        //                     )
        //                 }
        //             }

        //             Spacer(modifier = Modifier.height(12.dp))

        //             val rawCount = detectedQuestions.size
        //             val keysCount = detectedQuestions.count { it.correctAnswer != null }
        //             val expsCount = detectedQuestions.count { !it.explanation.isNullOrBlank() }

        //             Row(
        //                 modifier = Modifier.fillMaxWidth(),
        //                 horizontalArrangement = Arrangement.spacedBy(8.dp)
        //             ) {
        //                 TelemetryCard(
        //                     title = "Questions",
        //                     value = "$rawCount",
        //                     valueColor = colors.inkBold,
        //                     modifier = Modifier.weight(1f)
        //                 )
        //                 TelemetryCard(
        //                     title = "Answer Keys",
        //                     value = "$keysCount",
        //                     valueColor = if (keysCount == rawCount && rawCount > 0) Color(0xFF059669) else Color(0xFFD97706),
        //                     modifier = Modifier.weight(1f)
        //                 )
        //                 TelemetryCard(
        //                     title = "Explanations",
        //                     value = "$expsCount",
        //                     valueColor = if (expsCount == rawCount && rawCount > 0) Color(0xFF059669) else Color(0xFF2563EB),
        //                     modifier = Modifier.weight(1f)
        //                 )
        //             }

        //             Spacer(modifier = Modifier.height(10.dp))

        //             if (keysCount < rawCount && rawCount > 0) {
        //                 Surface(
        //                     shape = RoundedCornerShape(8.dp),
        //                     color = Color(0xFFFFFBEB),
        //                     border = BorderStroke(0.6.dp, Color(0xFFFDE68A)),
        //                     modifier = Modifier.fillMaxWidth()
        //                 ) {
        //                     Row(
        //                         modifier = Modifier.padding(10.dp),
        //                         verticalAlignment = Alignment.CenterVertically,
        //                         horizontalArrangement = Arrangement.SpaceBetween
        //                     ) {
        //                         Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        //                             Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
        //                             Spacer(modifier = Modifier.width(8.dp))
        //                             Text(
        //                                 text = "${rawCount - keysCount} question(s) missing official answer key.",
        //                                 fontSize = 11.5.sp,
        //                                 fontWeight = FontWeight.SemiBold,
        //                                 color = Color(0xFF92400E)
        //                             )
        //                         }
        //                         TextButton(onClick = onNavigateToMaster) {
        //                             Text("Set in Master", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
        //                         }
        //                     }
        //                 }
        //             } else if (rawCount > 0) {
        //                 Surface(
        //                     shape = RoundedCornerShape(8.dp),
        //                     color = Color(0xFFECFDF5),
        //                     border = BorderStroke(0.6.dp, Color(0xFFA7F3D0)),
        //                     modifier = Modifier.fillMaxWidth()
        //                 ) {
        //                     Row(
        //                         modifier = Modifier.padding(10.dp),
        //                         verticalAlignment = Alignment.CenterVertically
        //                     ) {
        //                         Icon(
        //                             imageVector = Icons.Default.CheckCircle,
        //                             contentDescription = null,
        //                             tint = Color(0xFF059669),
        //                             modifier = Modifier.size(16.dp)
        //                         )
        //                         Spacer(modifier = Modifier.width(8.dp))
        //                         Text(
        //                             text = "All $rawCount questions, options, and keys formatted cleanly.",
        //                             fontSize = 11.5.sp,
        //                             fontWeight = FontWeight.SemiBold,
        //                             color = Color(0xFF047857)
        //                         )
        //                     }
        //                 }
        //             }
        //         }
        //     }
        // }

        // 4. Safe Backup & Batch Hub (Import & Export with Zero Overwrite Risk)
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(0.8.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.teal.copy(alpha = 0.12f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Safe Backup & Batch JSON Hub",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "Import new sets to library without overwriting active paper",
                                fontSize = 11.5.sp,
                                color = colors.inkMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Batch Import Files
                        Button(
                            onClick = {
                                batchFilePicker.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 36.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Files", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }

                        // 2. Export Current Paper
                        OutlinedButton(
                            onClick = {
                                val json = viewModel.exportCurrentPaperAsJson()
                                if (!json.isNullOrBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("ExamSet JSON", json)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Current paper JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No active paper to export.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 36.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.ink)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Export Paper", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.ink, maxLines = 1)
                        }

                        // 3. Batch Export All
                        OutlinedButton(
                            onClick = {
                                val json = viewModel.exportAllPapersAsJson()
                                if (!json.isNullOrBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("All ExamSets JSON", json)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Exported ${savedPapers.size} papers to clipboard!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "No saved papers to export.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 36.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp), tint = colors.green)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Export All (${savedPapers.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.green, maxLines = 1)
                        }
                    }
                }
            }
        }

        // 5. AI Prompt Builder & Share System Shortcuts
        // item {
        //     Card(
        //         shape = RoundedCornerShape(14.dp),
        //         colors = CardDefaults.cardColors(containerColor = colors.surface),
        //         border = BorderStroke(0.8.dp, colors.border)
        //     ) {
        //         Column(modifier = Modifier.padding(14.dp)) {
        //             Row(
        //                 modifier = Modifier.fillMaxWidth(),
        //                 horizontalArrangement = Arrangement.spacedBy(10.dp)
        //             ) {
        //                 // AI Prompt Builder Shortcut
        //                 Surface(
        //                     onClick = { viewModel.selectTab(5) },
        //                     shape = RoundedCornerShape(10.dp),
        //                     color = Color(0xFFF59E0B).copy(alpha = 0.08f),
        //                     border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f)),
        //                     modifier = Modifier.weight(1f)
        //                 ) {
        //                     Column(modifier = Modifier.padding(12.dp)) {
        //                         Row(verticalAlignment = Alignment.CenterVertically) {
        //                             Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
        //                             Spacer(modifier = Modifier.width(6.dp))
        //                             Text("AI Prompt Builder", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
        //                         }
        //                         Spacer(modifier = Modifier.height(4.dp))
        //                         Text("Generate zero-drift exams with Gemini & Claude", fontSize = 11.sp, color = colors.inkMuted)
        //                     }
        //                 }

        //                 // Share & Export Shortcut
        //                 Surface(
        //                     onClick = { viewModel.selectTab(6) },
        //                     shape = RoundedCornerShape(10.dp),
        //                     color = Color(0xFF2563EB).copy(alpha = 0.08f),
        //                     border = BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.3f)),
        //                     modifier = Modifier.weight(1f)
        //                 ) {
        //                     Column(modifier = Modifier.padding(12.dp)) {
        //                         Row(verticalAlignment = Alignment.CenterVertically) {
        //                             Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
        //                             Spacer(modifier = Modifier.width(6.dp))
        //                             Text("Share & Export", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.inkBold)
        //                         }
        //                         Spacer(modifier = Modifier.height(4.dp))
        //                         Text("QR codes, print sheets, & student links", fontSize = 11.sp, color = colors.inkMuted)
        //                     }
        //                 }
        //             }
        //         }
        //     }
        // }

        // 6. Safe Read-Only Question Inspector
        // if (detectedQuestions.isNotEmpty()) {
        //     item {
        //         Card(
        //             shape = RoundedCornerShape(14.dp),
        //             colors = CardDefaults.cardColors(containerColor = colors.surface),
        //             border = BorderStroke(0.8.dp, colors.border)
        //         ) {
        //             Column(modifier = Modifier.padding(16.dp)) {
        //                 Row(
        //                     modifier = Modifier.fillMaxWidth(),
        //                     horizontalArrangement = Arrangement.SpaceBetween,
        //                     verticalAlignment = Alignment.CenterVertically
        //                 ) {
        //                     Row(verticalAlignment = Alignment.CenterVertically) {
        //                         Text(
        //                             text = "Safe Question Browser",
        //                             fontSize = 14.sp,
        //                             fontWeight = FontWeight.Bold,
        //                             color = colors.inkBold
        //                         )
        //                         Spacer(modifier = Modifier.width(8.dp))
        //                         Surface(
        //                             shape = RoundedCornerShape(12.dp),
        //                             color = Color(0xFFEFF6FF),
        //                             border = BorderStroke(0.5.dp, Color(0xFFBFDBFE))
        //                         ) {
        //                             Text(
        //                                 text = "${detectedQuestions.size} Items",
        //                                 fontSize = 11.sp,
        //                                 fontWeight = FontWeight.Bold,
        //                                 color = Color(0xFF2563EB),
        //                                 modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        //                             )
        //                         }
        //                     }

        //                     // View Mode Toggle (Live vs List)
        //                     Surface(
        //                         shape = RoundedCornerShape(6.dp),
        //                         color = colors.surfaceVariant,
        //                         border = BorderStroke(0.6.dp, colors.border)
        //                     ) {
        //                         Row(modifier = Modifier.padding(2.dp)) {
        //                             ModeToggleButton(
        //                                 label = "Carousel",
        //                                 isSelected = previewMode == "live"
        //                             ) { previewMode = "live" }
        //                             ModeToggleButton(
        //                                 label = "List",
        //                                 isSelected = previewMode == "list"
        //                             ) { previewMode = "list" }
        //                         }
        //                     }
        //                 }

        //                 Spacer(modifier = Modifier.height(12.dp))

        //                 if (previewMode == "live") {
        //                     val activeIndex = selectedPreviewIndex.coerceIn(0, (detectedQuestions.size - 1).coerceAtLeast(0))
        //                     val question = detectedQuestions.getOrNull(activeIndex)

        //                     // Question Carousel Switcher Row
        //                     Row(
        //                         modifier = Modifier
        //                             .fillMaxWidth()
        //                             .background(colors.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        //                             .padding(4.dp),
        //                         verticalAlignment = Alignment.CenterVertically,
        //                         horizontalArrangement = Arrangement.SpaceBetween
        //                     ) {
        //                         IconButton(
        //                             onClick = { if (selectedPreviewIndex > 0) selectedPreviewIndex-- },
        //                             enabled = selectedPreviewIndex > 0,
        //                             modifier = Modifier.size(30.dp)
        //                         ) {
        //                             Icon(
        //                                 Icons.AutoMirrored.Filled.ArrowBack,
        //                                 contentDescription = "Previous Question",
        //                                 modifier = Modifier.size(16.dp)
        //                             )
        //                         }

        //                         Row(
        //                             modifier = Modifier
        //                                 .weight(1f)
        //                                 .horizontalScroll(rememberScrollState())
        //                                 .padding(horizontal = 4.dp),
        //                             horizontalArrangement = Arrangement.spacedBy(4.dp)
        //                         ) {
        //                             detectedQuestions.forEachIndexed { idx, q ->
        //                                 Surface(
        //                                     modifier = Modifier.clickable { selectedPreviewIndex = idx },
        //                                     shape = RoundedCornerShape(6.dp),
        //                                     color = if (idx == activeIndex) Color(0xFF2563EB) else colors.surface,
        //                                     border = BorderStroke(
        //                                         0.6.dp,
        //                                         if (idx == activeIndex) Color(0xFF1D4ED8) else colors.border
        //                                     )
        //                                 ) {
        //                                     Text(
        //                                         text = "Q${q.num}",
        //                                         fontSize = 11.5.sp,
        //                                         fontWeight = FontWeight.Bold,
        //                                         color = if (idx == activeIndex) Color.White else colors.ink,
        //                                         modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        //                                     )
        //                                 }
        //                             }
        //                         }

        //                         IconButton(
        //                             onClick = { if (selectedPreviewIndex < detectedQuestions.size - 1) selectedPreviewIndex++ },
        //                             enabled = selectedPreviewIndex < detectedQuestions.size - 1,
        //                             modifier = Modifier.size(30.dp)
        //                         ) {
        //                             Icon(
        //                                 Icons.AutoMirrored.Filled.ArrowForward,
        //                                 contentDescription = "Next Question",
        //                                 modifier = Modifier.size(16.dp)
        //                             )
        //                         }
        //                     }

        //                     Spacer(modifier = Modifier.height(10.dp))

        //                     // Structured Question Layout
        //                     if (question != null) {
        //                         Surface(
        //                             shape = RoundedCornerShape(10.dp),
        //                             color = colors.surfaceVariant.copy(alpha = 0.35f),
        //                             border = BorderStroke(0.6.dp, colors.border)
        //                         ) {
        //                             StructuredQuestionContent(
        //                                 question = question,
        //                                 showAnswerIndicator = true,
        //                                 modifier = Modifier.padding(14.dp)
        //                             )
        //                         }
        //                     }
        //                 } else {
        //                     // Compact List Overview
        //                     Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        //                         detectedQuestions.forEachIndexed { idx, q ->
        //                             Surface(
        //                                 modifier = Modifier
        //                                     .fillMaxWidth()
        //                                     .clickable {
        //                                         selectedPreviewIndex = idx
        //                                         previewMode = "live"
        //                                     },
        //                                 shape = RoundedCornerShape(8.dp),
        //                                 color = if (selectedPreviewIndex == idx) Color(0xFFEFF6FF) else colors.surfaceVariant.copy(alpha = 0.3f),
        //                                 border = BorderStroke(
        //                                     0.6.dp,
        //                                     if (selectedPreviewIndex == idx) Color(0xFFBFDBFE) else colors.border
        //                                 )
        //                             ) {
        //                                 Row(
        //                                     modifier = Modifier.padding(10.dp),
        //                                     verticalAlignment = Alignment.CenterVertically,
        //                                     horizontalArrangement = Arrangement.SpaceBetween
        //                                 ) {
        //                                     Column(modifier = Modifier.weight(1f)) {
        //                                         Row(verticalAlignment = Alignment.CenterVertically) {
        //                                             Text(
        //                                                 text = "Q${q.num}.",
        //                                                 fontSize = 13.sp,
        //                                                 fontWeight = FontWeight.Bold,
        //                                                 color = colors.inkBold
        //                                             )
        //                                             Spacer(modifier = Modifier.width(6.dp))
        //                                             Surface(
        //                                                 shape = RoundedCornerShape(4.dp),
        //                                                 color = Color(0xFFF3E8FF),
        //                                                 border = BorderStroke(0.5.dp, Color(0xFFD8B4FE))
        //                                             ) {
        //                                                 Text(
        //                                                     text = q.questionType ?: "Direct Fact",
        //                                                     fontSize = 10.sp,
        //                                                     fontWeight = FontWeight.Bold,
        //                                                     color = Color(0xFF7E22CE),
        //                                                     modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        //                                                 )
        //                                             }
        //                                         }
        //                                         Spacer(modifier = Modifier.height(2.dp))
        //                                         Text(
        //                                             text = q.question.replace("\n", " ").trim(),
        //                                             fontSize = 12.5.sp,
        //                                             color = colors.inkMuted,
        //                                             maxLines = 1,
        //                                             overflow = TextOverflow.Ellipsis
        //                                         )
        //                                     }

        //                                     Spacer(modifier = Modifier.width(8.dp))

        //                                     Surface(
        //                                         shape = CircleShape,
        //                                         color = if (q.correctAnswer != null) Color(0xFFD1FAE5) else colors.border,
        //                                         modifier = Modifier.size(26.dp)
        //                                     ) {
        //                                         Box(contentAlignment = Alignment.Center) {
        //                                             Text(
        //                                                 text = q.correctAnswer ?: "-",
        //                                                 fontSize = 12.sp,
        //                                                 fontWeight = FontWeight.Bold,
        //                                                 color = if (q.correctAnswer != null) Color(0xFF065F46) else colors.inkMuted
        //                                             )
        //                                         }
        //                                     }
        //                                 }
        //                             }
        //                         }
        //                     }
        //                 }
        //             }
        //         }
        //     }
        // }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (showBatchImportDialog) {
        BatchImportDialog(
            initialItems = batchImportItems,
            folders = folders,
            defaultFolderId = "all",
            onDismiss = {
                showBatchImportDialog = false
                batchImportItems = emptyList()
            },
            onAddMoreFiles = {
                batchFilePicker.launch(arrayOf("application/json", "text/plain", "*/*"))
            },
            onImportSuccess = { result ->
                Toast.makeText(
                    context,
                    "Successfully imported ${result.importedCount} papers (${result.totalQuestions} questions)!",
                    Toast.LENGTH_SHORT
                ).show()
            },
            viewModel = viewModel
        )
    }

    if (FeatureToggles.ENABLE_CAMERA_NOTES_SCANNER && showScanResultDialog && scanResult != null) {
        val result = scanResult!!
        val parsedQuestions = result.parseResult?.paper?.questions ?: emptyList()
        AlertDialog(
            onDismissRequest = { showScanResultDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (result.isSuccess && parsedQuestions.isNotEmpty()) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = if (result.isSuccess && parsedQuestions.isNotEmpty()) colors.green else colors.amber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (result.isSuccess && parsedQuestions.isNotEmpty()) "Notes Extracted (${parsedQuestions.size} Questions)" else "Scan Results",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (result.isSuccess && parsedQuestions.isNotEmpty()) {
                        Text(
                            text = "Successfully recognized ${parsedQuestions.size} questions from handwritten notes.",
                            fontSize = 13.sp,
                            color = colors.ink,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        parsedQuestions.take(5).forEach { q ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(0.6.dp, colors.border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "Q${q.num}. ${q.question}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.inkBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "(A) ${q.options.a.take(25)}  (B) ${q.options.b.take(25)}",
                                        fontSize = 11.sp,
                                        color = colors.inkMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!q.correctAnswer.isNullOrBlank()) {
                                        Text(
                                            text = "Answer: (${q.correctAnswer})",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.green
                                        )
                                    }
                                }
                            }
                        }

                        if (parsedQuestions.size > 5) {
                            Text(
                                text = "...and ${parsedQuestions.size - 5} more questions",
                                fontSize = 11.5.sp,
                                color = colors.inkMuted,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.amberLight,
                            border = BorderStroke(0.6.dp, colors.amber.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = result.errorMessage
                                    ?: "No structured MCQ questions could be identified in the captured photo. Please ensure clear lighting and legible (A)-(D) options.",
                                fontSize = 12.sp,
                                color = colors.amber,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        if (result.normalizedText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Recognized Text:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = result.normalizedText,
                                    fontSize = 11.sp,
                                    color = colors.ink,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (result.isSuccess && parsedQuestions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (paper != null && paper.questions.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    val currentQCount = paper.questions.size
                                    val renumbered = parsedQuestions.mapIndexed { idx, q ->
                                        q.copy(num = currentQCount + idx + 1)
                                    }
                                    val updatedQuestions = paper.questions + renumbered
                                    viewModel.saveAllPaperQuestions(updatedQuestions)
                                    showScanResultDialog = false
                                    Toast.makeText(
                                        context,
                                        "Appended ${parsedQuestions.size} questions to active paper!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("append_scanned_questions_button")
                            ) {
                                Text("Append", fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.parseAndSetPaper(
                                    text = result.normalizedText,
                                    customTitle = paper?.metadata?.title ?: "Handwritten Notes Exam Set",
                                    customSubject = paper?.metadata?.subject ?: "General Studies"
                                )
                                showScanResultDialog = false
                                Toast.makeText(
                                    context,
                                    "Paper populated with ${parsedQuestions.size} questions!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.teal),
                            modifier = Modifier.testTag("populate_scanned_paper_button")
                        ) {
                            Text(
                                if (paper != null && paper.questions.isNotEmpty()) "Replace Paper" else "Populate Paper",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    TextButton(onClick = { showScanResultDialog = false }) {
                        Text("OK", color = colors.teal)
                    }
                }
            },
            dismissButton = {
                if (result.isSuccess && parsedQuestions.isNotEmpty()) {
                    TextButton(onClick = { showScanResultDialog = false }) {
                        Text("Cancel", color = colors.inkMuted)
                    }
                }
            }
        )
    }
}

@Composable
private fun TelemetryCard(
    title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(0.6.dp, colors.border),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                color = valueColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.inkMuted
            )
        }
    }
}

@Composable
private fun ModeToggleButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = ExamTheme.colors
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(4.dp),
        color = if (isSelected) colors.surface else Color.Transparent,
        shadowElevation = if (isSelected) 1.dp else 0.dp
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF2563EB) else colors.inkMuted,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

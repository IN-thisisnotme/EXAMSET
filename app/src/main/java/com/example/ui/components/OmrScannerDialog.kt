package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.QuestionPaper
import com.example.ui.theme.ExamTheme
import com.example.util.OmrVisionProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * High-accuracy OMR scanner dialog that accepts photo capture or gallery images of NEET OMR sheets.
 * Runs optical drop-out ink attenuation, projective homography unwarping, bubble sampling,
 * and automatic grading against official answer keys.
 */
@Composable
fun OmrScannerDialog(
    paper: QuestionPaper,
    onDismiss: () -> Unit,
    onSubmitResult: (OmrVisionProcessor.OmrScanOutput) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val colors = ExamTheme.colors

    var isProcessing by remember { mutableStateOf(false) }
    var processingStage by remember { mutableStateOf("") }
    var scanResult by remember { mutableStateOf<OmrVisionProcessor.OmrScanOutput?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    fun processBitmap(bitmap: Bitmap) {
        isProcessing = true
        errorMessage = null
        processingStage = "Analyzing image and optical registration anchors..."

        coroutineScope.launch(Dispatchers.Default) {
            try {
                val argbBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    bitmap
                }

                withContext(Dispatchers.Main) {
                    processingStage = "Projecting homography unwarping & drop-out filter..."
                }

                val result = OmrVisionProcessor.processOmr(
                    bitmap = argbBitmap,
                    paper = paper
                )

                withContext(Dispatchers.Main) {
                    isProcessing = false
                    scanResult = result
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    errorMessage = "Scan evaluation failed: ${e.localizedMessage ?: "Unknown optical error"}. Please ensure all four corner black anchors are visible with adequate lighting."
                }
            }
        }
    }

    fun loadBitmapFromUri(uri: Uri) {
        selectedImageUri = uri
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                withContext(Dispatchers.Main) {
                    processBitmap(bitmap)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Could not load image: ${e.localizedMessage}"
                }
            }
        }
    }

    // Photo picker launcher (Android Photo Picker / Gallery)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            loadBitmapFromUri(uri)
        }
    }

    // Camera photo capture launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { previewBitmap: Bitmap? ->
        if (previewBitmap != null) {
            processBitmap(previewBitmap)
        }
    }

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isProcessing,
            dismissOnClickOutside = !isProcessing
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.2.dp, colors.border)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header with Title and Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFBE185D).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFFBE185D).copy(alpha = 0.3f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = Color(0xFFBE185D),
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Optical OMR Sheet Scanner",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold
                            )
                            Text(
                                text = "Auto-evaluates NEET 200 MCQ magenta answer sheets",
                                fontSize = 12.sp,
                                color = colors.muted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isProcessing,
                        modifier = Modifier.testTag("omr_scanner_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Scanner",
                            tint = colors.inkBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = colors.border, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Body content based on state
                val currentResult = scanResult
                when {
                    isProcessing -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(54.dp),
                                    color = Color(0xFFBE185D),
                                    strokeWidth = 4.dp
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = processingStage,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.inkBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Filtering out magenta drop-out ink & detecting pencil/pen marks...",
                                    fontSize = 12.sp,
                                    color = colors.muted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    currentResult != null -> {
                        ScannedResultReviewView(
                            result = currentResult,
                            paper = paper,
                            onRescan = {
                                scanResult = null
                                errorMessage = null
                            },
                            onSubmit = {
                                onSubmitResult(currentResult)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    else -> {
                        // Image capture / upload picker options
                        ScannerUploadPromptsView(
                            paper = paper,
                            errorMessage = errorMessage,
                            onPickCamera = { cameraLauncher.launch(null) },
                            onPickGallery = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Upload prompt section with camera and gallery selection cards and optical guidance tips.
 */
@Composable
private fun ScannerUploadPromptsView(
    paper: QuestionPaper,
    errorMessage: String?,
    onPickCamera: () -> Unit,
    onPickGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (errorMessage != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.amberLight,
                    border = BorderStroke(1.dp, colors.amber.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = colors.amber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMessage,
                            fontSize = 12.5.sp,
                            color = colors.inkBold,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Step 1: Capture or Upload Filled OMR Sheet",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Paper: ${paper.metadata.title.ifBlank { "NEET Mock Practice Set" }} (${paper.questions.size} Questions)",
                        fontSize = 12.sp,
                        color = colors.muted
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Camera capture button
                        Button(
                            onClick = onPickCamera,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("omr_scan_camera_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFBE185D),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Take Photo",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Gallery / File upload button
                        OutlinedButton(
                            onClick = onPickGallery,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("omr_scan_gallery_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.2.dp, colors.border),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = colors.inkBold
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = colors.teal
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "From Gallery",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFBE185D),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Best Practices for 100% Accuracy",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OmrTipItem(
                        number = "1",
                        title = "Four Corner Anchors",
                        detail = "Ensure all 4 solid black corner marker squares (top-left, top-right, bottom-left, bottom-right) are clearly in frame. They allow perspective correction."
                    )
                    OmrTipItem(
                        number = "2",
                        title = "Adequate Lighting & Flat Surface",
                        detail = "Place the sheet on a flat table without folds. Avoid harsh direct shadows or extreme glare on the response bubble columns."
                    )
                    OmrTipItem(
                        number = "3",
                        title = "Drop-Out Ink Advantage",
                        detail = "The magenta/pink template markings naturally drop out under our optical filter, leaving only your dark ballpoint or 2B pencil marks for grading."
                    )
                }
            }
        }
    }
}

@Composable
private fun OmrTipItem(
    number: String,
    title: String,
    detail: String
) {
    val colors = ExamTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFFBE185D).copy(alpha = 0.12f),
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFBE185D)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = colors.inkBold
            )
            Text(
                text = detail,
                fontSize = 11.sp,
                color = colors.muted,
                lineHeight = 15.sp
            )
        }
    }
}

/**
 * Result display screen after OMR evaluation showing score, accuracy, questions table, and save action.
 */
@Composable
private fun ScannedResultReviewView(
    result: OmrVisionProcessor.OmrScanOutput,
    paper: QuestionPaper,
    onRescan: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val totalQuestions = result.totalQuestions
    val score = result.totalScore
    val correct = result.correctCount
    val incorrect = result.incorrectCount
    val unanswered = result.unattemptedCount

    val accuracyPercent = if (result.attemptedCount > 0) {
        ((correct.toFloat() / result.attemptedCount) * 100).toInt().coerceIn(0, 100)
    } else 0

    Column(modifier = modifier.fillMaxWidth()) {
        // Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Scanned OMR Evaluation",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.muted
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = score.toString(),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Black,
                                color = if (score > 0) colors.teal else colors.inkBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "/ ${result.maxPossibleScore} marks",
                                fontSize = 14.sp,
                                color = colors.muted,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.tealLight,
                        border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$accuracyPercent%",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.teal
                            )
                            Text(
                                text = "Accuracy",
                                fontSize = 10.sp,
                                color = colors.muted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scanned Candidate Header Identification
                if (result.headerResult != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("CANDIDATE ROLL NO.", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.muted)
                                Text(
                                    text = result.headerResult.rollNumber,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF0284C7)
                                )
                            }
                            Column {
                                Text("BOOKLET NO.", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.muted)
                                Text(
                                    text = result.headerResult.bookletNumber,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFBE185D)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("SET CODE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.muted)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFFDF2F8),
                                    border = BorderStroke(0.6.dp, Color(0xFFBE185D))
                                ) {
                                    Text(
                                        text = "Set ${result.headerResult.bookletCode}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFBE185D),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Breakdown pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BreakdownStatChip(
                        label = "Correct",
                        count = correct,
                        color = colors.green,
                        bgColor = colors.greenLight,
                        modifier = Modifier.weight(1f)
                    )
                    BreakdownStatChip(
                        label = "Incorrect",
                        count = incorrect,
                        color = colors.red,
                        bgColor = colors.redLight,
                        modifier = Modifier.weight(1f)
                    )
                    BreakdownStatChip(
                        label = "Unanswered",
                        count = unanswered,
                        color = colors.muted,
                        bgColor = colors.surface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Questions list header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Question-by-Question Audit (${result.questionResults.size} Questions)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.inkBold
            )
            Text(
                text = "Marked vs Key",
                fontSize = 11.sp,
                color = colors.muted
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Question detail list
        val questionsList = result.questionResults.values.toList().sortedBy { it.questionNum }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(questionsList) { item ->
                val officialQuestion = paper.questions.getOrNull(item.questionNum - 1)
                val officialAnswer = officialQuestion?.correctAnswer?.uppercase()?.firstOrNull()?.toString() ?: ""
                val markedChar = item.selectedOption?.toString()

                val isCorrect = !item.isMultipleMarked && markedChar != null && markedChar.equals(officialAnswer, ignoreCase = true)
                val isIncorrect = !item.isMultipleMarked && markedChar != null && !markedChar.equals(officialAnswer, ignoreCase = true)

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.surface,
                    border = BorderStroke(0.6.dp, colors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = String.format(java.util.Locale.US, "Q%03d", item.questionNum),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkBold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(44.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Marked: ${if (item.isMultipleMarked) "Multiple" else markedChar ?: "—"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    isCorrect -> colors.green
                                    isIncorrect -> colors.red
                                    item.isMultipleMarked -> colors.amber
                                    else -> colors.muted
                                }
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Key: ${officialAnswer.ifBlank { "—" }}",
                                fontSize = 11.5.sp,
                                color = colors.muted
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val (badgeBg, badgeFg, label) = when {
                                isCorrect -> Triple(colors.greenLight, colors.green, "+4")
                                isIncorrect -> Triple(colors.redLight, colors.red, "-1")
                                item.isMultipleMarked -> Triple(colors.amberLight, colors.amber, "-1")
                                else -> Triple(colors.surfaceVariant, colors.muted, "0")
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = badgeBg
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeFg,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action Buttons: Re-scan vs Submit Result
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onRescan,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colors.inkBold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Scan Another", fontSize = 12.5.sp, color = colors.inkBold)
            }

            Button(
                onClick = onSubmit,
                modifier = Modifier
                    .weight(1.4f)
                    .height(48.dp)
                    .testTag("omr_save_score_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.navyBanner,
                    contentColor = colors.navyBannerText
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save & Record Result", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BreakdownStatChip(
    label: String,
    count: Int,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                fontSize = 10.5.sp,
                color = color.copy(alpha = 0.9f)
            )
        }
    }
}

package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
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

@Composable
fun ShareSystemTab(
    viewModel: MainViewModel,
    onNavigateToMaster: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    val currentPaper by viewModel.currentPaper.collectAsState()

    var pastedJson by remember { mutableStateOf("") }
    var copiedJson by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.loadFromUri(uri)
            onNavigateToMaster()
        }
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
                                text = "JSON exchange with Web, Mobile, and LMS portals",
                                fontSize = 11.5.sp,
                                color = colors.inkMuted
                            )
                        }
                    }
                }
            }
        }

        // Active Paper Sharing
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
                                Text(if (copiedJson) "JSON Copied!" else "Copy JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            // Share via Android Apps
                            OutlinedButton(
                                onClick = {
                                    val json = viewModel.exportCurrentPaperAsJson()
                                    if (!json.isNullOrBlank()) {
                                        val sendIntent: Intent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, json)
                                            putExtra(Intent.EXTRA_SUBJECT, "${paper.metadata.title} - ExamSet JSON")
                                            type = "text/plain"
                                        }
                                        val shareIntent = Intent.createChooser(sendIntent, "Share ExamSet Paper")
                                        context.startActivity(shareIntent)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share via Apps", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else {
                        Text(
                            text = "No active paper loaded. Select one in Folders or load one in Exam Tools.",
                            fontSize = 12.sp,
                            color = colors.inkMuted
                        )
                    }
                }
            }
        }

        // Import ExamSet JSON
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
                            text = "IMPORT EXAMSET JSON",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkMuted,
                            letterSpacing = 0.5.sp
                        )

                        OutlinedButton(
                            onClick = {
                                filePicker.launch(arrayOf("application/json", "text/plain"))
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import File", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = pastedJson,
                        onValueChange = { pastedJson = it },
                        placeholder = {
                            Text(
                                text = "Paste ExamSet JSON payload here (must include metadata and questions array)...",
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
                            .height(140.dp),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (pastedJson.isNotBlank()) {
                                val success = viewModel.importPaperFromJson(pastedJson)
                                if (success) {
                                    pastedJson = ""
                                    onNavigateToMaster()
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
                        Text("Validate & Import Assessment", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

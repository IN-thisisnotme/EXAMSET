package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AttemptEntity
import com.example.ui.theme.ExamTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AttemptHistoryDialog(
    attempts: List<AttemptEntity>,
    onDismiss: () -> Unit,
    onLoadAttempt: (AttemptEntity) -> Unit
) {
    val colors = ExamTheme.colors
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Attempt History",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkBold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                ) {
                    items(attempts) { attempt ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                            border = BorderStroke(1.dp, colors.border),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(attempt.createdAt))
                                    Text(dateStr, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.inkBold)
                                    if (attempt.isSubmitted) {
                                        Text("Score: ${attempt.getFormattedScore()} / ${attempt.totalQuestions}", fontSize = 12.sp, color = colors.green)
                                        val roll = attempt.getCandidateRollNumber()
                                        val booklet = attempt.getCandidateBookletNumber()
                                        val code = attempt.getCandidateBookletCode()
                                        if (roll.isNotBlank() || booklet.isNotBlank()) {
                                            Text(
                                                text = "Roll: ${roll.ifBlank { "—" }}  •  Booklet: ${booklet.ifBlank { "—" }}${if (code.isNotBlank()) " (Set $code)" else ""}",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = androidx.compose.ui.graphics.Color(0xFF0284C7)
                                            )
                                        }
                                    } else {
                                        Text("In Progress", fontSize = 12.sp, color = colors.muted)
                                    }
                                }
                                
                                Button(
                                    onClick = { onLoadAttempt(attempt) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(if (attempt.isSubmitted) "Review" else "Resume")
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

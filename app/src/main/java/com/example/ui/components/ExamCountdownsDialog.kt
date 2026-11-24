package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ExamCountdownTarget
import com.example.model.StudyDateUtils
import com.example.ui.theme.ExamTheme

private fun parseCountdownAccent(hex: String, fallback: Color): Color {
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
}

@Composable
fun PinnedExamCountdownsStrip(
    countdowns: List<ExamCountdownTarget>,
    nowMillis: Long,
    onManageCountdowns: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    val pinned = remember(countdowns, nowMillis) {
        countdowns
            .filter { it.isPinned }
            .sortedBy { it.daysRemaining(nowMillis) ?: Long.MAX_VALUE }
            .take(2)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("pinned_exam_countdowns_strip"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pinned.forEach { target ->
            val days = target.daysRemaining(nowMillis) ?: 0L
            val accent = when {
                days in 0L..7L -> colors.red
                days in 8L..21L -> colors.amber
                else -> parseCountdownAccent(target.colorHex, colors.teal)
            }
            Surface(
                onClick = onManageCountdowns,
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.12f),
                border = BorderStroke(0.9.dp, accent.copy(alpha = 0.45f)),
                modifier = Modifier.testTag("pinned_countdown_${target.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = target.formattedBadgeLabel(nowMillis),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Surface(
            onClick = onManageCountdowns,
            shape = RoundedCornerShape(10.dp),
            color = colors.surfaceVariant,
            border = BorderStroke(0.8.dp, colors.border),
            modifier = Modifier.testTag("manage_exam_countdowns_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (pinned.isEmpty()) Icons.Default.Add else Icons.Default.Event,
                    contentDescription = null,
                    tint = colors.teal,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (countdowns.isEmpty()) "Add Exam Date" else "Countdowns (${countdowns.size})",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.tealDark
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamCountdownsDialog(
    countdowns: List<ExamCountdownTarget>,
    nowMillis: Long,
    onSaveCountdown: (
        id: String?,
        title: String,
        targetDateStr: String,
        subject: String,
        colorHex: String,
        isPinned: Boolean
    ) -> Boolean,
    onTogglePin: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    var editingId by remember { mutableStateOf<String?>(null) }
    var titleInput by remember { mutableStateOf("") }
    var subjectInput by remember { mutableStateOf("") }
    var dateInput by remember { mutableStateOf(StudyDateUtils.addDaysToDateString(nowMillis, 30)) }
    var isPinnedInput by remember { mutableStateOf(true) }
    var showDatePicker by remember { mutableStateOf(false) }
    var pendingDeleteTarget by remember { mutableStateOf<ExamCountdownTarget?>(null) }

    val sortedCountdowns = remember(countdowns, nowMillis) {
        countdowns.sortedBy { it.daysRemaining(nowMillis) ?: Long.MAX_VALUE }
    }

    pendingDeleteTarget?.let { targetToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTarget = null },
            containerColor = colors.surface,
            titleContentColor = colors.inkBold,
            textContentColor = colors.ink,
            title = { Text("Delete Exam Countdown?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Remove \"${targetToDelete.title}\" (${StudyDateUtils.formatHumanDate(targetToDelete.targetDateStr)}) from your countdown targets?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(targetToDelete.id)
                        if (editingId == targetToDelete.id) {
                            editingId = null
                            titleInput = ""
                            subjectInput = ""
                        }
                        pendingDeleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.red, contentColor = Color.White),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { pendingDeleteTarget = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", color = colors.inkBold)
                }
            }
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = StudyDateUtils.parseDateToStartOfDayMillis(dateInput) ?: nowMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { picked ->
                            dateInput = StudyDateUtils.formatDateString(picked)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Select", fontWeight = FontWeight.Bold, color = colors.teal)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = colors.muted)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Exam-Day Countdowns", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = colors.inkBold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close Countdowns", tint = colors.muted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Add / Edit Form
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surfaceVariant,
                    border = BorderStroke(0.8.dp, colors.border)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (editingId == null) "Add Exam Target" else "Edit Exam Target",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold
                        )
                        OutlinedTextField(
                            value = titleInput,
                            onValueChange = { titleInput = it },
                            label = { Text("Exam Name") },
                            placeholder = { Text("e.g., UPSC Prelims 2026") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("countdown_title_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = dateInput,
                                onValueChange = { dateInput = it },
                                label = { Text("YYYY-MM-DD") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("countdown_date_input")
                            )
                            OutlinedButton(
                                onClick = { showDatePicker = true },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.Default.Event, contentDescription = "Pick Date", tint = colors.teal)
                            }
                        }
                        OutlinedTextField(
                            value = subjectInput,
                            onValueChange = { subjectInput = it },
                            label = { Text("Subject / Focus (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = isPinnedInput,
                                    onCheckedChange = { isPinnedInput = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = colors.teal)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pin to Library Strip", fontSize = 12.sp, color = colors.inkBold)
                            }
                            Button(
                                onClick = {
                                    val saved = onSaveCountdown(
                                        editingId,
                                        titleInput,
                                        dateInput,
                                        subjectInput,
                                        "#0D9488",
                                        isPinnedInput
                                    )
                                    if (saved) {
                                        editingId = null
                                        titleInput = ""
                                        subjectInput = ""
                                        isPinnedInput = true
                                    }
                                },
                                enabled = titleInput.trim().isNotBlank() &&
                                    StudyDateUtils.parseDateToStartOfDayMillis(dateInput) != null,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.teal, contentColor = Color.White),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("save_countdown_button")
                            ) {
                                Text(if (editingId == null) "Add" else "Update", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.borderLight)

                if (sortedCountdowns.isEmpty()) {
                    Text(
                        text = "No exam countdowns added yet.",
                        fontSize = 12.sp,
                        color = colors.muted
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(sortedCountdowns, key = { it.id }) { target ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surface),
                                border = BorderStroke(1.dp, if (target.isPinned) colors.teal else colors.border)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = target.title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.inkBold
                                        )
                                        Text(
                                            text = "${target.formattedDaysShort(nowMillis)} • ${StudyDateUtils.formatHumanDate(target.targetDateStr)}",
                                            fontSize = 11.sp,
                                            color = colors.teal
                                        )
                                    }
                                    IconButton(
                                        onClick = { onTogglePin(target.id) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = "Pin Countdown",
                                            tint = if (target.isPinned) colors.teal else colors.muted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            editingId = target.id
                                            titleInput = target.title
                                            dateInput = target.targetDateStr
                                            subjectInput = target.subject
                                            isPinnedInput = target.isPinned
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Countdown",
                                            tint = colors.inkBold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { pendingDeleteTarget = target },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Countdown",
                                            tint = colors.red,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Done", fontWeight = FontWeight.Bold, color = colors.teal)
            }
        },
        modifier = Modifier.testTag("exam_countdowns_dialog")
    )
}

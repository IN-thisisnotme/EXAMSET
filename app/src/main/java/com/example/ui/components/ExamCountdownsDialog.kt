package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.ExamCountdownTarget
import com.example.model.StudyDateUtils
import com.example.ui.theme.ExamTheme
import com.example.ui.theme.parseHexColorSafe
import com.example.util.CountdownAlertScheduler
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun PinnedExamCountdownsStrip(
    countdowns: List<ExamCountdownTarget>,
    nowMillis: Long,
    onManageCountdowns: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors
    var liveNowMillis by remember(nowMillis) { mutableLongStateOf(nowMillis) }
    val hasTimedTarget = remember(countdowns) {
        countdowns.any { it.hasExplicitTime() || (it.targetUtcMillis ?: 0L) > 0L }
    }
    LaunchedEffect(hasTimedTarget, nowMillis) {
        if (!hasTimedTarget) return@LaunchedEffect
        val startElapsed = System.currentTimeMillis()
        while (true) {
            delay(1000L)
            liveNowMillis = nowMillis + (System.currentTimeMillis() - startElapsed)
        }
    }

    val pinned = remember(countdowns, liveNowMillis) {
        countdowns
            .filter { it.isPinned }
            .sortedBy { it.remainingMillis(liveNowMillis) ?: Long.MAX_VALUE }
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
            val days = target.daysRemaining(liveNowMillis) ?: 0L
            val accent = when {
                days in 0L..7L -> colors.red
                days in 8L..21L -> colors.amber
                else -> parseHexColorSafe(target.colorHex, colors.teal)
            }
            Surface(
                onClick = onManageCountdowns,
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = 0.12f),
                border = BorderStroke(0.9.dp, accent.copy(alpha = 0.45f)),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("pinned_countdown_${target.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                        text = target.formattedBadgeLabel(liveNowMillis),
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
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("manage_exam_countdowns_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
    defaultAlertLeadMinutes: Int = ExamCountdownTarget.DEFAULT_ALERT_LEAD_MINUTES,
    onSaveDefaultAlertLeadMinutes: (Int) -> Unit = {},
    onTestAlert: (title: String, message: String, leadMinutes: Int, vibrate: Boolean, sound: Boolean) -> String = { _, _, _, _, _ -> "" },
    onSaveCountdown: (
        id: String?,
        title: String,
        targetDateStr: String,
        targetTimeStr: String,
        timezoneMode: String,
        subject: String,
        colorHex: String,
        isPinned: Boolean,
        alertEnabled: Boolean,
        alertLeadMinutes: Int,
        alertMessage: String,
        vibrateOnAlert: Boolean,
        alertSoundEnabled: Boolean,
        alertAtZero: Boolean
    ) -> Boolean,
    onTogglePin: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ExamTheme.colors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var liveNowMillis by remember(nowMillis) { mutableLongStateOf(nowMillis) }
    val hasTimedTarget = remember(countdowns) {
        countdowns.any { it.hasExplicitTime() || (it.targetUtcMillis ?: 0L) > 0L }
    }
    LaunchedEffect(hasTimedTarget, nowMillis) {
        if (!hasTimedTarget) return@LaunchedEffect
        val startElapsed = System.currentTimeMillis()
        while (true) {
            delay(1000L)
            liveNowMillis = nowMillis + (System.currentTimeMillis() - startElapsed)
        }
    }

    var readiness by remember { mutableStateOf(CountdownAlertScheduler.inspectReadiness(context)) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                readiness = CountdownAlertScheduler.inspectReadiness(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        readiness = CountdownAlertScheduler.inspectReadiness(context)
    }

    val safeDefaultLead = defaultAlertLeadMinutes.coerceIn(
        ExamCountdownTarget.MIN_ALERT_LEAD_MINUTES,
        ExamCountdownTarget.MAX_ALERT_LEAD_MINUTES
    )

    var editingId by remember { mutableStateOf<String?>(null) }
    var titleInput by remember { mutableStateOf("") }
    var subjectInput by remember { mutableStateOf("") }
    var dateInput by remember { mutableStateOf(StudyDateUtils.addDaysToDateString(nowMillis, 30)) }
    var timeInput by remember { mutableStateOf("00:00") }
    var tzModeInput by remember { mutableStateOf("LOCAL") }
    var isPinnedInput by remember { mutableStateOf(true) }

    var alertEnabledInput by remember { mutableStateOf(true) }
    var leadHoursInput by remember { mutableStateOf((safeDefaultLead / 60).toString()) }
    var leadMinsInput by remember { mutableStateOf((safeDefaultLead % 60).toString()) }
    var alertMessageInput by remember { mutableStateOf("") }
    var vibrateInput by remember { mutableStateOf(true) }
    var soundInput by remember { mutableStateOf(true) }
    var alertAtZeroInput by remember { mutableStateOf(true) }
    var testFeedback by remember { mutableStateOf<String?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDeleteTarget by remember { mutableStateOf<ExamCountdownTarget?>(null) }

    val resetForm = {
        editingId = null
        titleInput = ""
        subjectInput = ""
        dateInput = StudyDateUtils.addDaysToDateString(nowMillis, 30)
        timeInput = "00:00"
        tzModeInput = "LOCAL"
        isPinnedInput = true
        alertEnabledInput = true
        leadHoursInput = (safeDefaultLead / 60).toString()
        leadMinsInput = (safeDefaultLead % 60).toString()
        alertMessageInput = ""
        vibrateInput = true
        soundInput = true
        alertAtZeroInput = true
        testFeedback = null
    }

    val parsedLeadHours = leadHoursInput.trim().toIntOrNull()?.coerceIn(0, 720) ?: 0
    val parsedLeadMins = leadMinsInput.trim().toIntOrNull()?.coerceIn(0, 59) ?: 0
    val computedLeadMinutes = (parsedLeadHours * 60 + parsedLeadMins).coerceIn(
        ExamCountdownTarget.MIN_ALERT_LEAD_MINUTES,
        ExamCountdownTarget.MAX_ALERT_LEAD_MINUTES
    )
    val isLeadZeroOrEmpty = (parsedLeadHours * 60 + parsedLeadMins) < ExamCountdownTarget.MIN_ALERT_LEAD_MINUTES

    val localTzLabel = remember(liveNowMillis) {
        StudyDateUtils.localTimezoneShortLabel(liveNowMillis)
    }

    val sortedCountdowns = remember(countdowns, liveNowMillis) {
        countdowns.sortedBy { it.remainingMillis(liveNowMillis) ?: Long.MAX_VALUE }
    }

    val effectiveTimeForPreview = timeInput.trim().ifBlank { "00:00" }
    val previewTargetMillis = remember(dateInput, effectiveTimeForPreview, tzModeInput) {
        StudyDateUtils.parseDateTimeMillis(
            dateStr = dateInput,
            timeStr = effectiveTimeForPreview,
            useGmt = tzModeInput == "GMT"
        )
    }

    val previewEarlyTriggerMillis = remember(previewTargetMillis, computedLeadMinutes, alertEnabledInput) {
        if (!alertEnabledInput || previewTargetMillis == null) null
        else previewTargetMillis - computedLeadMinutes * 60_000L
    }

    val isTimeFieldValid = timeInput.trim().isBlank() || StudyDateUtils.isValidTimeHm(timeInput)
    val canSave = titleInput.trim().isNotBlank() &&
        StudyDateUtils.parseDateToStartOfDayMillis(dateInput) != null &&
        isTimeFieldValid &&
        (!alertEnabledInput || !isLeadZeroOrEmpty)

    pendingDeleteTarget?.let { targetToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTarget = null },
            containerColor = colors.surface,
            titleContentColor = colors.inkBold,
            textContentColor = colors.ink,
            title = { Text("Delete Exam Countdown?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Remove \"${targetToDelete.title}\" (${targetToDelete.formattedTargetSchedule()}) from your countdown targets?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(targetToDelete.id)
                        if (editingId == targetToDelete.id) {
                            resetForm()
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
            initialSelectedDateMillis = StudyDateUtils.parseDateTimeMillis(dateInput, "00:00", useGmt = true)
                ?: nowMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { picked ->
                            dateInput = StudyDateUtils.formatDateStringGmt(picked)
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Select", fontWeight = FontWeight.Bold, color = colors.teal)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", color = colors.muted)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val parsedHour = timeInput.trim().split(":").getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
        val parsedMinute = timeInput.trim().split(":").getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val timePickerState = rememberTimePickerState(
            initialHour = parsedHour,
            initialMinute = parsedMinute,
            is24Hour = true
        )
        var useKeyboardTimeInput by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = colors.surface,
            titleContentColor = colors.inkBold,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Target Time (24h)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { useKeyboardTimeInput = !useKeyboardTimeInput },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (useKeyboardTimeInput) Icons.Default.AccessTime else Icons.Default.Keyboard,
                            contentDescription = if (useKeyboardTimeInput) "Switch to Clock Dial" else "Switch to Keyboard Time Entry",
                            tint = colors.teal
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (useKeyboardTimeInput) {
                        TimeInput(state = timePickerState)
                    } else {
                        TimePicker(state = timePickerState)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        timeInput = String.format(
                            Locale.US,
                            "%02d:%02d",
                            timePickerState.hour,
                            timePickerState.minute
                        )
                        showTimePicker = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Select", fontWeight = FontWeight.Bold, color = colors.teal)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTimePicker = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", color = colors.muted)
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val currentFontScale = LocalDensity.current.fontScale
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 520.dp)
                .heightIn(max = 760.dp)
                .imePadding()
                .testTag("exam_countdowns_dialog")
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isLargeScaleOrNarrow = currentFontScale >= 1.3f || maxWidth < 340.dp
                val isExtraWideFont = currentFontScale >= 1.45f || maxWidth < 310.dp

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Exam-Day Countdowns",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.inkBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close Countdowns", tint = colors.muted)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Unified scrollable content (prevents clipped lists & nested-scroll crashes)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "countdown_form") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = colors.surfaceVariant,
                                border = BorderStroke(0.8.dp, colors.border)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (editingId == null) "Add Exam Target" else "Edit Exam Target",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.inkBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (editingId != null) {
                                            TextButton(
                                                onClick = resetForm,
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("cancel_countdown_edit_button")
                                            ) {
                                                Text("Cancel", fontWeight = FontWeight.SemiBold, color = colors.muted)
                                            }
                                        }
                                    }

                                    OutlinedTextField(
                                        value = titleInput,
                                        onValueChange = { titleInput = it },
                                        label = { Text("Target / Milestone Name") },
                                        placeholder = { Text("e.g., Term-End Exam / Finals") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("countdown_title_input")
                                    )

                                    // Date Input + Calendar Button
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = dateInput,
                                            onValueChange = { dateInput = it },
                                            label = { Text("Date (YYYY-MM-DD)") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("countdown_date_input")
                                        )
                                        OutlinedButton(
                                            onClick = { showDatePicker = true },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Icon(Icons.Default.Event, contentDescription = "Pick Date", tint = colors.teal)
                                        }
                                    }

                                    // [DEPRECATED 2026-11-25] Reason: Quick date offset chips (+7d/+15d/+30d/+60d/+90d) removed per user request ("no quick thing in it"). Restore if: Quick preset date shortcuts are requested again.

                                    // Time Input (HH:mm, default 00:00) + Clock Button
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = timeInput,
                                            onValueChange = { timeInput = it },
                                            label = { Text("Time (HH:mm)") },
                                            placeholder = { Text("00:00") },
                                            isError = !isTimeFieldValid,
                                            singleLine = true,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("countdown_time_input")
                                        )
                                        OutlinedButton(
                                            onClick = { showTimePicker = true },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Icon(Icons.Default.AccessTime, contentDescription = "Pick Time", tint = colors.teal)
                                        }
                                    }

                                    // Timezone Toggle: Adaptive layout so Local (<TZ>) and GMT (UTC) never truncate under high font scales
                                    val renderZonePills: @Composable (Modifier) -> Unit = { containerMod ->
                                        if (isExtraWideFont) {
                                            Column(
                                                modifier = containerMod,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    onClick = { tzModeInput = "LOCAL" },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (tzModeInput == "LOCAL") colors.tealLight else colors.surface,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (tzModeInput == "LOCAL") colors.teal else colors.border
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 48.dp)
                                                        .testTag("countdown_tz_local")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Local ($localTzLabel)",
                                                            fontSize = 12.sp,
                                                            fontWeight = if (tzModeInput == "LOCAL") FontWeight.Bold else FontWeight.Medium,
                                                            color = if (tzModeInput == "LOCAL") colors.tealDark else colors.ink,
                                                            maxLines = 2
                                                        )
                                                    }
                                                }
                                                Surface(
                                                    onClick = { tzModeInput = "GMT" },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (tzModeInput == "GMT") colors.tealLight else colors.surface,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (tzModeInput == "GMT") colors.teal else colors.border
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 48.dp)
                                                        .testTag("countdown_tz_gmt")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "GMT (UTC)",
                                                            fontSize = 12.sp,
                                                            fontWeight = if (tzModeInput == "GMT") FontWeight.Bold else FontWeight.Medium,
                                                            color = if (tzModeInput == "GMT") colors.tealDark else colors.ink,
                                                            maxLines = 2
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            Row(
                                                modifier = containerMod,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    onClick = { tzModeInput = "LOCAL" },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (tzModeInput == "LOCAL") colors.tealLight else colors.surface,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (tzModeInput == "LOCAL") colors.teal else colors.border
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .heightIn(min = 48.dp)
                                                        .testTag("countdown_tz_local")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Local ($localTzLabel)",
                                                            fontSize = 12.sp,
                                                            fontWeight = if (tzModeInput == "LOCAL") FontWeight.Bold else FontWeight.Medium,
                                                            color = if (tzModeInput == "LOCAL") colors.tealDark else colors.ink,
                                                            maxLines = 2
                                                        )
                                                    }
                                                }
                                                Surface(
                                                    onClick = { tzModeInput = "GMT" },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (tzModeInput == "GMT") colors.tealLight else colors.surface,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (tzModeInput == "GMT") colors.teal else colors.border
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .heightIn(min = 48.dp)
                                                        .testTag("countdown_tz_gmt")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "GMT (UTC)",
                                                            fontSize = 12.sp,
                                                            fontWeight = if (tzModeInput == "GMT") FontWeight.Bold else FontWeight.Medium,
                                                            color = if (tzModeInput == "GMT") colors.tealDark else colors.ink,
                                                            maxLines = 2
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (isLargeScaleOrNarrow) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Zone:",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.muted
                                            )
                                            renderZonePills(Modifier.fillMaxWidth())
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Zone:",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.muted
                                            )
                                            renderZonePills(Modifier.weight(1f))
                                        }
                                    }

                                    if (previewTargetMillis != null) {
                                        val localPreview = StudyDateUtils.formatHumanDateTimeLocal(previewTargetMillis)
                                        val previewText = if (
                                            tzModeInput == "GMT" &&
                                            !localTzLabel.equals("GMT", ignoreCase = true) &&
                                            !localTzLabel.equals("UTC", ignoreCase = true)
                                        ) {
                                            "Targets $localPreview ($effectiveTimeForPreview GMT)"
                                        } else {
                                            "Targets $localPreview"
                                        }
                                        Text(
                                            text = previewText,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colors.tealDark,
                                            modifier = Modifier.testTag("countdown_preview_label")
                                        )
                                    }

                                    OutlinedTextField(
                                        value = subjectInput,
                                        onValueChange = { subjectInput = it },
                                        label = { Text("Course / Subject Focus (Optional)") },
                                        placeholder = { Text("e.g., Course-101, Paper II, Organic Chemistry") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    HorizontalDivider(color = colors.borderLight)

                                    // Early Alert & Vibration Section
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (alertEnabledInput) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                                contentDescription = null,
                                                tint = if (alertEnabledInput) colors.teal else colors.muted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "Early Alert & Vibration",
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.inkBold
                                                )
                                                Text(
                                                    text = "Default: ${ExamCountdownTarget.formatLeadDuration(safeDefaultLead)} before target",
                                                    fontSize = 11.sp,
                                                    color = colors.muted
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = alertEnabledInput,
                                            onCheckedChange = { alertEnabledInput = it },
                                            modifier = Modifier.testTag("countdown_alert_switch"),
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = colors.teal
                                            )
                                        )
                                    }

                                    if (alertEnabledInput) {
                                        // Custom Hours & Minutes + Save as Default
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = leadHoursInput,
                                                onValueChange = { raw ->
                                                    leadHoursInput = raw.filter { it.isDigit() }.take(3)
                                                },
                                                label = { Text("Lead Hours") },
                                                placeholder = { Text("2") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("countdown_lead_hours_input")
                                            )
                                            OutlinedTextField(
                                                value = leadMinsInput,
                                                onValueChange = { raw ->
                                                    leadMinsInput = raw.filter { it.isDigit() }.take(2)
                                                },
                                                label = { Text("Lead Mins") },
                                                placeholder = { Text("0") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("countdown_lead_mins_input")
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    if (!isLeadZeroOrEmpty) {
                                                        onSaveDefaultAlertLeadMinutes(computedLeadMinutes)
                                                    }
                                                },
                                                enabled = !isLeadZeroOrEmpty && computedLeadMinutes != safeDefaultLead,
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("save_default_lead_button")
                                            ) {
                                                Text(
                                                    text = if (computedLeadMinutes == safeDefaultLead) "Default ✓" else "Set Default",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (computedLeadMinutes == safeDefaultLead) colors.teal else colors.inkBold,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        if (isLeadZeroOrEmpty) {
                                            Text(
                                                text = "Enter at least 1 minute of advance lead time.",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.red
                                            )
                                        } else if (previewEarlyTriggerMillis != null) {
                                            val isEarlyInPast = previewEarlyTriggerMillis <= liveNowMillis
                                            val isTargetInFuture = (previewTargetMillis ?: 0L) > liveNowMillis
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isEarlyInPast) colors.amber.copy(alpha = 0.14f) else colors.surface,
                                                border = BorderStroke(
                                                    0.8.dp,
                                                    if (isEarlyInPast) colors.amber else colors.border
                                                ),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = if (isEarlyInPast) Icons.Default.Warning else Icons.Default.AccessTime,
                                                        contentDescription = null,
                                                        tint = if (isEarlyInPast) colors.amber else colors.teal,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = when {
                                                            !isTargetInFuture ->
                                                                "Target date/time is already in the past — future alerts will not fire unless moved forward."
                                                            isEarlyInPast ->
                                                                "Early alert time (${StudyDateUtils.formatHumanDateTimeLocal(previewEarlyTriggerMillis)}) already passed; ${if (alertAtZeroInput) "only 0m on-time alert will fire" else "reduce lead time so it can fire"}."
                                                            else ->
                                                                "Early alert fires ${StudyDateUtils.formatHumanDateTimeLocal(previewEarlyTriggerMillis)} (${ExamCountdownTarget.formatLeadDuration(computedLeadMinutes)} before)"
                                                        },
                                                        fontSize = 11.sp,
                                                        color = if (isEarlyInPast) colors.inkBold else colors.tealDark
                                                    )
                                                }
                                            }
                                        }

                                        OutlinedTextField(
                                            value = alertMessageInput,
                                            onValueChange = { alertMessageInput = it },
                                            label = { Text("What is going to happen (Alert Note)") },
                                            placeholder = { Text("e.g., Hall gate opens — carry admit card & ID") },
                                            maxLines = 2,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("countdown_alert_message_input")
                                        )

                                        // Vibrate, Sound, and Zero-time toggles
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                onClick = { vibrateInput = !vibrateInput },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (vibrateInput) colors.tealLight else colors.surface,
                                                border = BorderStroke(1.dp, if (vibrateInput) colors.teal else colors.border),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 48.dp)
                                                    .testTag("countdown_vibrate_toggle")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Vibration,
                                                        contentDescription = null,
                                                        tint = if (vibrateInput) colors.tealDark else colors.muted,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text(
                                                        text = if (vibrateInput) "Vibrate: On" else "Vibrate: Off",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (vibrateInput) colors.tealDark else colors.muted
                                                    )
                                                }
                                            }
                                            Surface(
                                                onClick = { soundInput = !soundInput },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (soundInput) colors.tealLight else colors.surface,
                                                border = BorderStroke(1.dp, if (soundInput) colors.teal else colors.border),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .heightIn(min = 48.dp)
                                                    .testTag("countdown_sound_toggle")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = if (soundInput) "Sound: On" else "Sound: Off",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (soundInput) colors.tealDark else colors.muted
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Switch(
                                                checked = alertAtZeroInput,
                                                onCheckedChange = { alertAtZeroInput = it },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = colors.teal
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Also alert at exact countdown time (0m)",
                                                fontSize = 12.sp,
                                                color = colors.ink,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        // Transparent Readiness Disclosure Banner (never fails silently)
                                        if (readiness.blockingIssues.isNotEmpty() || readiness.advisoryWarnings.isNotEmpty()) {
                                            val hasBlocking = readiness.blockingIssues.isNotEmpty()
                                            val bannerAccent = if (hasBlocking) colors.red else colors.amber
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = bannerAccent.copy(alpha = 0.10f),
                                                border = BorderStroke(0.9.dp, bannerAccent.copy(alpha = 0.55f)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("countdown_alert_readiness_banner")
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.Warning,
                                                            contentDescription = null,
                                                            tint = bannerAccent,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = if (hasBlocking) "Alerts Cannot Fire Until Fixed" else "Delivery Notice",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = bannerAccent
                                                        )
                                                    }
                                                    readiness.blockingIssues.forEach { issue ->
                                                        Text(
                                                            text = "• $issue",
                                                            fontSize = 11.sp,
                                                            color = colors.inkBold
                                                        )
                                                    }
                                                    readiness.advisoryWarnings.forEach { warn ->
                                                        Text(
                                                            text = "• $warn",
                                                            fontSize = 11.sp,
                                                            color = colors.ink
                                                        )
                                                    }
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        if (!readiness.postNotificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                                },
                                                                shape = RoundedCornerShape(8.dp),
                                                                modifier = Modifier.heightIn(min = 48.dp)
                                                            ) {
                                                                Text("Allow Notifications", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.red)
                                                            }
                                                        } else if (!readiness.systemNotificationsEnabled || !readiness.channelEnabled) {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    CountdownAlertScheduler.openAppNotificationSettings(context)
                                                                },
                                                                shape = RoundedCornerShape(8.dp),
                                                                modifier = Modifier.heightIn(min = 48.dp)
                                                            ) {
                                                                Text("Notification Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.red)
                                                            }
                                                        }
                                                        if (!readiness.exactAlarmsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                            OutlinedButton(
                                                                onClick = {
                                                                    CountdownAlertScheduler.openExactAlarmSettings(context)
                                                                },
                                                                shape = RoundedCornerShape(8.dp),
                                                                modifier = Modifier.heightIn(min = 48.dp)
                                                            ) {
                                                                Text("Allow Exact Alarms", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.amber)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val result = onTestAlert(
                                                    titleInput,
                                                    alertMessageInput,
                                                    computedLeadMinutes,
                                                    vibrateInput,
                                                    soundInput
                                                )
                                                testFeedback = result
                                                readiness = CountdownAlertScheduler.inspectReadiness(context)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("test_countdown_alert_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Vibration,
                                                contentDescription = null,
                                                tint = colors.teal,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Test Vibration & Alert Now",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.tealDark
                                            )
                                        }

                                        testFeedback?.let { fb ->
                                            Text(
                                                text = fb,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (readiness.isReadyForAlerts) colors.tealDark else colors.red
                                            )
                                        }
                                    }

                                    val onSaveClick = {
                                        val normalizedTime = timeInput.trim().ifBlank { "00:00" }
                                        val saved = onSaveCountdown(
                                            editingId,
                                            titleInput,
                                            dateInput,
                                            normalizedTime,
                                            tzModeInput,
                                            subjectInput,
                                            "#0D9488",
                                            isPinnedInput,
                                            alertEnabledInput,
                                            computedLeadMinutes,
                                            alertMessageInput,
                                            vibrateInput,
                                            soundInput,
                                            alertAtZeroInput
                                        )
                                        if (saved) {
                                            resetForm()
                                            readiness = CountdownAlertScheduler.inspectReadiness(context)
                                        }
                                    }

                                    if (isLargeScaleOrNarrow) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Switch(
                                                    checked = isPinnedInput,
                                                    onCheckedChange = { isPinnedInput = it },
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = Color.White,
                                                        checkedTrackColor = colors.teal
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Pin to Library Strip",
                                                    fontSize = 12.5.sp,
                                                    color = colors.inkBold,
                                                    maxLines = 2,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Button(
                                                onClick = onSaveClick,
                                                enabled = canSave,
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = colors.teal,
                                                    contentColor = Color.White
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 48.dp)
                                                    .testTag("save_countdown_button")
                                            ) {
                                                Text(
                                                    text = if (editingId == null) "Add" else "Update",
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Switch(
                                                    checked = isPinnedInput,
                                                    onCheckedChange = { isPinnedInput = it },
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = Color.White,
                                                        checkedTrackColor = colors.teal
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Pin to Library Strip",
                                                    fontSize = 12.5.sp,
                                                    color = colors.inkBold,
                                                    maxLines = 2,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Button(
                                                onClick = onSaveClick,
                                                enabled = canSave,
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = colors.teal,
                                                    contentColor = Color.White
                                                ),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("save_countdown_button")
                                            ) {
                                                Text(
                                                    text = if (editingId == null) "Add" else "Update",
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "countdown_divider") {
                            HorizontalDivider(color = colors.borderLight)
                        }

                        if (sortedCountdowns.isEmpty()) {
                            item(key = "countdown_empty") {
                                Text(
                                    text = "No exam countdowns added yet.",
                                    fontSize = 12.5.sp,
                                    color = colors.muted,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        } else {
                            items(sortedCountdowns, key = { it.id }) { target ->
                                val daysLeft = target.daysRemaining(liveNowMillis)
                                val remMs = target.remainingMillis(liveNowMillis)
                                val pillColor = when {
                                    remMs != null && remMs < 0L -> colors.muted
                                    daysLeft != null && daysLeft in 0L..7L -> colors.red
                                    daysLeft != null && daysLeft in 8L..21L -> colors.amber
                                    else -> parseHexColorSafe(target.colorHex, colors.teal)
                                }
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = colors.surface),
                                    border = BorderStroke(1.dp, if (target.isPinned) colors.teal else colors.border)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        // Row 1: Title + Live Remaining Pill (stacked at high font scale so neither clips)
                                        if (isLargeScaleOrNarrow) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = target.title,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.inkBold,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = pillColor.copy(alpha = 0.14f),
                                                    border = BorderStroke(0.8.dp, pillColor.copy(alpha = 0.45f))
                                                ) {
                                                    Text(
                                                        text = target.formattedCountdownDetailed(liveNowMillis),
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = pillColor,
                                                        maxLines = 2,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = target.title,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.inkBold,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = pillColor.copy(alpha = 0.14f),
                                                    border = BorderStroke(0.8.dp, pillColor.copy(alpha = 0.45f))
                                                ) {
                                                    Text(
                                                        text = target.formattedCountdownDetailed(liveNowMillis),
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = pillColor,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }

                                        val renderActionIcons: @Composable () -> Unit = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                                        timeInput = target.targetTimeStr.ifBlank { "00:00" }
                                                        tzModeInput = if (target.isGmtAnchored()) "GMT" else "LOCAL"
                                                        subjectInput = target.subject
                                                        isPinnedInput = target.isPinned
                                                        alertEnabledInput = target.alertEnabled
                                                        leadHoursInput = (target.alertLeadMinutes / 60).toString()
                                                        leadMinsInput = (target.alertLeadMinutes % 60).toString()
                                                        alertMessageInput = target.alertMessage
                                                        vibrateInput = target.vibrateOnAlert
                                                        soundInput = target.alertSoundEnabled
                                                        alertAtZeroInput = target.alertAtZero
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

                                        val earlyMillis = target.earlyAlertTriggerMillis()
                                        val isPastTarget = (remMs ?: 0L) <= 0L
                                        val earlyMissed = target.alertEnabled && earlyMillis != null && earlyMillis <= liveNowMillis && !isPastTarget
                                        val alertSummaryLine = when {
                                            !target.alertEnabled -> "Alerts off"
                                            isPastTarget -> "Target passed — alert inactive"
                                            earlyMissed -> "Early ${target.formattedLeadSummary()} window passed" +
                                                if (target.alertAtZero) " • 0m alert active" else ""
                                            else -> buildString {
                                                append("Alerts ${target.formattedLeadSummary()} before")
                                                if (target.vibrateOnAlert) append(" • Vibrate")
                                                if (!target.alertSoundEnabled) append(" • Silent")
                                                if (target.alertAtZero) append(" • +0m")
                                            }
                                        }

                                        // Row 2: Schedule, Alert summary & Subject + Action Icons
                                        if (isLargeScaleOrNarrow) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = target.formattedTargetSchedule(),
                                                    fontSize = 11.5.sp,
                                                    color = colors.ink
                                                )
                                                Text(
                                                    text = alertSummaryLine,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = when {
                                                        !target.alertEnabled || isPastTarget -> colors.muted
                                                        earlyMissed -> colors.amber
                                                        else -> colors.tealDark
                                                    }
                                                )
                                                if (target.alertEnabled && target.alertMessage.isNotBlank()) {
                                                    Text(
                                                        text = "Note: ${target.alertMessage}",
                                                        fontSize = 11.sp,
                                                        color = colors.inkBold,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (target.subject.isNotBlank()) {
                                                    Text(
                                                        text = target.subject,
                                                        fontSize = 11.sp,
                                                        color = colors.muted,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End
                                                ) {
                                                    renderActionIcons()
                                                }
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Text(
                                                        text = target.formattedTargetSchedule(),
                                                        fontSize = 11.5.sp,
                                                        color = colors.ink
                                                    )
                                                    Text(
                                                        text = alertSummaryLine,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = when {
                                                            !target.alertEnabled || isPastTarget -> colors.muted
                                                            earlyMissed -> colors.amber
                                                            else -> colors.tealDark
                                                        }
                                                    )
                                                    if (target.alertEnabled && target.alertMessage.isNotBlank()) {
                                                        Text(
                                                            text = "Note: ${target.alertMessage}",
                                                            fontSize = 11.sp,
                                                            color = colors.inkBold,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    if (target.subject.isNotBlank()) {
                                                        Text(
                                                            text = target.subject,
                                                            fontSize = 11.sp,
                                                            color = colors.muted,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                                renderActionIcons()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Footer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Done", fontWeight = FontWeight.Bold, color = colors.teal)
                        }
                    }
                }
            }
        }
    }
}


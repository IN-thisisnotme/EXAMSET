package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ExamConfig
import com.example.model.OmrBubblingMode
import com.example.model.QuestionPaper
import com.example.util.ExamNumberGenerator
import com.example.ui.theme.RobotoMonoFontFamily

private val OMR_MAGENTA = Color(0xFFBE185D)
private val OMR_DARK_MAGENTA = Color(0xFF831843)
private val OMR_SHADED_ROW = Color(0xFFFDF2F8)
private val OMR_BUBBLE_DARK = Color(0xFF000000)

/**
 * Authentic, millimeter-precise A4 OMR Sheet Composable for Preview and Inspection.
 * Faithfully mirrors official UPSC and NEET competitive examination response sheets.
 */
@Composable
fun AuthenticOmrSheetView(
    paper: QuestionPaper,
    config: ExamConfig,
    modifier: Modifier = Modifier
) {
    val totalQuestions = paper.questions.size.coerceAtLeast(1)
    val rawSet = paper.metadata.setNumber.ifBlank { config.seriesCode.ifBlank { "A" } }
    val setClean = ExamNumberGenerator.extractBookletCode(rawSet)
    val bookletSets = ExamNumberGenerator.resolveBookletSets(setClean)

    val rollStr = if (config.rollNumber.isNotBlank()) {
        ExamNumberGenerator.normalizeRollNumber(config.rollNumber, 10)
    } else if (paper.metadata.rollNumber.isNotBlank()) {
        ExamNumberGenerator.normalizeRollNumber(paper.metadata.rollNumber, 10)
    } else ""

    val bookletStr = if (config.bookletNumber.isNotBlank()) {
        ExamNumberGenerator.normalizeBookletNumber(config.bookletNumber, 7)
    } else if (paper.metadata.bookletNumber.isNotBlank()) {
        ExamNumberGenerator.normalizeBookletNumber(paper.metadata.bookletNumber, 7)
    } else ""

    val showRollDigits = (config.omrBubblingMode == OmrBubblingMode.PRE_PRINTED_DIGITS ||
            config.omrBubblingMode == OmrBubblingMode.PRE_DARKENED_BUBBLES) && rollStr.length == 10
    val darkenRollBubbles = config.omrBubblingMode == OmrBubblingMode.PRE_DARKENED_BUBBLES && rollStr.length == 10

    val showBookletDigits = (config.omrBubblingMode == OmrBubblingMode.PRE_PRINTED_DIGITS ||
            config.omrBubblingMode == OmrBubblingMode.PRE_DARKENED_BUBBLES) && bookletStr.length == 7
    val darkenBookletBubbles = config.omrBubblingMode == OmrBubblingMode.PRE_DARKENED_BUBBLES && bookletStr.length == 7

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFE2E8F0)) // Desk surface backdrop
            .padding(8.dp)
            .testTag("authentic_omr_sheet_container"),
        contentAlignment = Alignment.Center
    ) {
        // A4 Sheet Container
        Surface(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth(),
            color = Color.White,
            shape = RoundedCornerShape(2.dp),
            shadowElevation = 4.dp,
            border = BorderStroke(1.5.dp, OMR_MAGENTA)
        ) {
            Box(modifier = Modifier.padding(6.dp)) {
                // 4 Corner Registration Fiducial Anchors (Solid drop-out magenta blocks)
                Box(modifier = Modifier.size(10.dp).background(OMR_MAGENTA).align(Alignment.TopStart))
                Box(modifier = Modifier.size(10.dp).background(OMR_MAGENTA).align(Alignment.TopEnd))
                Box(modifier = Modifier.size(10.dp).background(OMR_MAGENTA).align(Alignment.BottomStart))
                Box(modifier = Modifier.size(10.dp).background(OMR_MAGENTA).align(Alignment.BottomEnd))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Top Header Section (without middle stamp box, ample stapling space)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Instruction banner
                        Box(
                            modifier = Modifier
                                .weight(1.3f)
                                .border(BorderStroke(1.dp, OMR_MAGENTA), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "The candidate should ensure that Roll No. and Test Booklet No. have been filled and marked correctly and Test Booklet Code printed in this sheet and Test Booklet is the same.",
                                fontSize = 6.8.sp,
                                fontWeight = FontWeight.Bold,
                                color = OMR_MAGENTA,
                                lineHeight = 8.5.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Title Box
                        Column(
                            modifier = Modifier.weight(1.1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "${config.examTitle.ifBlank { paper.metadata.exam.ifBlank { "UPSC CSE" } }.uppercase()} OMR ANSWER SHEET",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = OMR_MAGENTA,
                                textAlign = TextAlign.End,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "PRACTICE / MOCK TEST OMR RESPONSE SHEET",
                                fontSize = 6.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = OMR_DARK_MAGENTA,
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    // Main 2-Panel Body: Left Identification Column + Right Continuous Question Grid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Left Identification Column
                        Column(
                            modifier = Modifier
                                .width(220.dp)
                                .border(BorderStroke(1.2.dp, OMR_MAGENTA))
                                .padding(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Warning Banner
                            Surface(
                                color = OMR_SHADED_ROW,
                                border = BorderStroke(0.8.dp, OMR_MAGENTA),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Candidate MUST NOT carry ORIGINAL or OFFICE Copy",
                                    fontSize = 5.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = OMR_MAGENTA,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }

                            // 10-Digit Roll Number Grid
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(1.dp, OMR_MAGENTA))
                            ) {
                                Text(
                                    text = "ROLL NO.",
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Black,
                                    color = OMR_MAGENTA,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White)
                                        .padding(vertical = 1.5.dp),
                                    textAlign = TextAlign.Center
                                )
                                HorizontalDivider(thickness = 0.8.dp, color = OMR_MAGENTA)

                                // Digit entry row (10 boxes)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    for (colIdx in 0 until 10) {
                                        val digitChar = if (showRollDigits) rollStr[colIdx].toString() else ""
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(18.dp)
                                                .border(BorderStroke(0.5.dp, OMR_MAGENTA)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = digitChar,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color.Black
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(thickness = 0.8.dp, color = OMR_MAGENTA)

                                // 0..9 Bubbles Grid
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                                ) {
                                    for (d in 0..9) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            for (colIdx in 0 until 10) {
                                                val isDarkened = darkenRollBubbles && (rollStr[colIdx] - '0') == d
                                                MicroBubble(
                                                    text = d.toString(),
                                                    isDarkened = isDarkened,
                                                    sizeDp = 13
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 7-Digit Test Booklet Number Grid
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(1.dp, OMR_MAGENTA))
                            ) {
                                Text(
                                    text = "TEST BOOKLET No.",
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Black,
                                    color = OMR_MAGENTA,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White)
                                        .padding(vertical = 1.5.dp),
                                    textAlign = TextAlign.Center
                                )
                                HorizontalDivider(thickness = 0.8.dp, color = OMR_MAGENTA)

                                // Digit entry row (7 boxes)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    for (colIdx in 0 until 7) {
                                        val digitChar = if (showBookletDigits) bookletStr[colIdx].toString() else ""
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(18.dp)
                                                .border(BorderStroke(0.5.dp, OMR_MAGENTA)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = digitChar,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace,
                                                color = Color.Black
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(thickness = 0.8.dp, color = OMR_MAGENTA)

                                // 0..9 Bubbles Grid
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalArrangement = Arrangement.spacedBy(1.5.dp)
                                ) {
                                    for (d in 0..9) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            for (colIdx in 0 until 7) {
                                                val isDarkened = darkenBookletBubbles && (bookletStr[colIdx] - '0') == d
                                                MicroBubble(
                                                    text = d.toString(),
                                                    isDarkened = isDarkened,
                                                    sizeDp = 13
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Test Booklet Code Box
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(1.dp, OMR_MAGENTA))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Test Booklet Code", fontSize = 6.sp, fontWeight = FontWeight.Bold, color = OMR_MAGENTA)
                                    Text(
                                        text = setClean,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = OMR_MAGENTA
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    bookletSets.forEach { code ->
                                        val isCurrent = code.equals(setClean, ignoreCase = true)
                                        val isDark = isCurrent && config.omrBubblingMode == OmrBubblingMode.PRE_DARKENED_BUBBLES
                                        MicroBubble(
                                            text = code,
                                            isDarkened = isDark,
                                            sizeDp = 15
                                        )
                                    }
                                }
                            }

                            // Answer Sheet No Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(0.8.dp, OMR_MAGENTA))
                                    .padding(3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Answer Sheet No.", fontSize = 5.5.sp, fontWeight = FontWeight.Bold, color = OMR_MAGENTA)
                                    val serialNo = String.format("%08d", (paper.id.hashCode().let { if (it < 0) -it else it } % 90000000) + 10000000)
                                    Text(
                                        text = serialNo,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = OMR_MAGENTA,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }

                            // Candidate's Name Box
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(1.dp, OMR_MAGENTA))
                                    .padding(3.dp)
                            ) {
                                Text(
                                    text = "CANDIDATE'S NAME (in running handwriting)",
                                    fontSize = 5.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OMR_MAGENTA
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                HorizontalDivider(thickness = 0.8.dp, color = OMR_MAGENTA)
                            }

                            // Declaration by candidate
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(1.dp, OMR_MAGENTA))
                                    .padding(3.dp)
                            ) {
                                Text(
                                    text = "DECLARATION BY THE CANDIDATE",
                                    fontSize = 5.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = OMR_MAGENTA
                                )
                                Text(
                                    text = "\"I declare that particulars and signature on this OMR Answer Sheet are mine and same as in Attendance Sheet. I further declare that particulars filled-in and signature as well as circles darkened on this ORIGINAL Copy are same as in OFFICE Copy.\"",
                                    fontSize = 4.8.sp,
                                    color = OMR_DARK_MAGENTA,
                                    lineHeight = 6.2.sp
                                )
                            }
                        }

                        // Right Continuous Question Grid: 4 Columns of 50 Rows (001-200)
                        Row(
                            modifier = Modifier
                                .border(BorderStroke(1.2.dp, OMR_MAGENTA))
                                .background(Color.White),
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            val numCols = 4
                            val rowsPerCol = 50

                            for (colIdx in 0 until numCols) {
                                val startQ = colIdx * rowsPerCol + 1
                                val endQ = (colIdx + 1) * rowsPerCol
                                val displayEndQ = minOf(endQ, maxOf(totalQuestions, 50))

                                Column(
                                    modifier = Modifier
                                        .width(115.dp)
                                        .border(BorderStroke(0.5.dp, OMR_MAGENTA))
                                ) {
                                    // Column Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(OMR_MAGENTA)
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Q.No.", fontSize = 6.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                            Text("1", fontSize = 6.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text("2", fontSize = 6.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text("3", fontSize = 6.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text("4", fontSize = 6.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }

                                    // Rows in this column (show up to displayEndQ, max 50)
                                    for (q in startQ..displayEndQ) {
                                        val isShaded = ((q - 1) / 5) % 2 == 1
                                        val qPadded = String.format("%03d", q)

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (isShaded) OMR_SHADED_ROW else Color.White)
                                                .padding(horizontal = 3.dp, vertical = 1.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = qPadded,
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = OMR_MAGENTA
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                listOf("1", "2", "3", "4").forEach { opt ->
                                                    MicroBubble(
                                                        text = opt,
                                                        isDarkened = false,
                                                        sizeDp = 13
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Verification Area: 3 Distinct Boxes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(BorderStroke(1.dp, OMR_MAGENTA))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Box 1: Thumb impression
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .border(BorderStroke(0.8.dp, OMR_MAGENTA))
                                .padding(3.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "CANDIDATE'S LEFT HAND THUMB IMPRESSION",
                                fontSize = 5.sp,
                                fontWeight = FontWeight.Bold,
                                color = OMR_MAGENTA
                            )
                        }

                        // Box 2: Candidate Signature
                        Column(
                            modifier = Modifier
                                .weight(1.2f)
                                .height(42.dp)
                                .border(BorderStroke(0.8.dp, OMR_MAGENTA))
                                .padding(3.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "CANDIDATE'S SIGNATURE WITH TIME",
                                fontSize = 5.sp,
                                fontWeight = FontWeight.Bold,
                                color = OMR_MAGENTA
                            )
                            Text(
                                text = "(in running handwriting)",
                                fontSize = 4.2.sp,
                                color = OMR_DARK_MAGENTA
                            )
                            HorizontalDivider(thickness = 0.8.dp, color = OMR_MAGENTA)
                        }

                        // Box 3: Single Invigilator Signature Line to Sign Across
                        Column(
                            modifier = Modifier
                                .weight(1.5f)
                                .height(42.dp)
                                .border(BorderStroke(0.8.dp, OMR_MAGENTA))
                                .padding(3.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "INVIGILATOR'S SIGNATURE WITH TIME",
                                fontSize = 5.sp,
                                fontWeight = FontWeight.Bold,
                                color = OMR_MAGENTA
                            )
                            Text(
                                text = "(Sign across the blank line with time)",
                                fontSize = 4.2.sp,
                                color = OMR_DARK_MAGENTA
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(0.8.dp)
                                        .background(OMR_MAGENTA)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("(Sign & Date/Time)", fontSize = 4.8.sp, fontWeight = FontWeight.Bold, color = OMR_MAGENTA)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MicroBubble(
    text: String,
    isDarkened: Boolean,
    sizeDp: Int = 13,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(sizeDp.dp),
        shape = CircleShape,
        border = BorderStroke(0.7.dp, if (isDarkened) OMR_BUBBLE_DARK else OMR_MAGENTA),
        color = if (isDarkened) OMR_BUBBLE_DARK else Color.White
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontFamily = RobotoMonoFontFamily,
                fontSize = (sizeDp * 0.55).sp,
                fontWeight = FontWeight.Black,
                color = if (isDarkened) Color.White else OMR_MAGENTA,
                textAlign = TextAlign.Center,
                lineHeight = (sizeDp * 0.6).sp
            )
        }
    }
}

package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PaperMetadata
import com.example.ui.theme.ExamTheme

/**
 * Signature Navy Header Banner directly recreating Python ReportLab's draw_first_page banner.
 * Displays Title, Subject, Topic, Set Number, and provides one-tap Edit Details access.
 */
@Composable
fun HeaderBanner(
    metadata: PaperMetadata,
    totalQuestions: Int,
    onEditClick: (() -> Unit)? = null,
    onEditQuestionsClick: (() -> Unit)? = null,
    isEditingQuestions: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = ExamTheme.colors

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.navyBanner),
        elevation = CardDefaults.cardElevation(defaultElevation = if (colors.isDark) 0.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Set Number & Edit Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Set No Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = colors.teal.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = metadata.setNumber.ifBlank { "SET - A" }.uppercase(),
                        color = colors.navyBannerText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Action buttons (Edit Details & Edit Questions)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onEditQuestionsClick != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isEditingQuestions) colors.amberLight else Color.White.copy(alpha = 0.15f),
                            border = if (isEditingQuestions) BorderStroke(0.8.dp, colors.amber) else null,
                            modifier = Modifier
                                .clickable { onEditQuestionsClick() }
                                .testTag("banner_edit_questions_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isEditingQuestions) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = "Edit Questions",
                                    tint = if (isEditingQuestions) colors.amber else colors.navyBannerText,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isEditingQuestions) "Editing Active" else "Edit Set",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isEditingQuestions) colors.amber else colors.navyBannerText
                                )
                            }
                        }
                    }

                    if (onEditClick != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier
                                .clickable { onEditClick() }
                                .testTag("edit_paper_metadata_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Edit Details",
                                    tint = colors.navyBannerText,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Edit Details",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.navyBannerText
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Main Title
            Text(
                text = metadata.title,
                color = colors.navyBannerText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Subject Line
            Text(
                text = metadata.subject,
                color = colors.teal,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            // Topic (if available)
            if (metadata.topic.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Topic: ${metadata.topic}",
                    color = colors.navyBannerText.copy(alpha = 0.85f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }

            if (metadata.exam.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                // Exam Badge
                Box(
                    modifier = Modifier
                        .background(
                            color = colors.teal.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = metadata.exam,
                        color = colors.navyBannerText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub info pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$totalQuestions Questions",
                    color = colors.navyBannerText.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "•",
                    color = colors.navyBannerText.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
                Text(
                    text = metadata.setNumber.ifBlank { "Set A" },
                    color = colors.navyBannerText.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "•",
                    color = colors.navyBannerText.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
                Text(
                    text = "Standard MCQ Format",
                    color = colors.navyBannerText.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (metadata.rollNumber.isNotBlank() || metadata.bookletNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (metadata.rollNumber.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colors.teal.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "Roll: ${metadata.rollNumber}",
                                color = colors.navyBannerText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (metadata.bookletNumber.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colors.teal.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "Booklet: ${metadata.bookletNumber}",
                                color = colors.navyBannerText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

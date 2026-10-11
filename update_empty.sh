cat << 'INNER_EOF' > empty_state.txt
@Composable
private fun EmptyStateView(
    savedPapers: List<PaperEntity>,
    allAttempts: List<AttemptEntity>,
    onPasteClick: () -> Unit,
    onPickFile: () -> Unit,
    onOpenSavedPapers: () -> Unit,
    onLoadSample: (Int) -> Unit
) {
    val colors = ExamTheme.colors
    val savedPapersCount = savedPapers.size
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (savedPapersCount > 0) Arrangement.Top else Arrangement.Center
    ) {
        if (savedPapersCount > 0) {
            SubjectDashboard(savedPapers, allAttempts)
            Spacer(modifier = Modifier.height(32.dp))
        }

        Surface(
            shape = CircleShape,
            color = colors.tealLight,
            modifier = Modifier.size(76.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Quiz,
                    contentDescription = null,
                    tint = colors.teal,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome to ExamSet",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.inkBold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Add your own MCQ question sets by typing/pasting text, importing a text file, or accessing your previously saved sets.",
            fontSize = 13.sp,
            color = colors.muted,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Primary Action Row: Add / Paste & Add File
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPasteClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.navyBanner,
                    contentColor = colors.navyBannerText
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("empty_state_add_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add / Paste Set", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onPickFile,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkBold),
                border = BorderStroke(1.dp, colors.border),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("empty_state_file_button")
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.teal)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add File")
            }
        }

        // If saved papers exist, offer direct access
        if (savedPapersCount > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenSavedPapers,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.tealDark),
                border = BorderStroke(1.dp, colors.teal.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("empty_state_saved_papers_button")
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.teal)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Saved Question Sets ($savedPapersCount)", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sample options for convenience
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onLoadSample(1) }) {
                Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.teal)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Science Sample", color = colors.teal, fontSize = 12.sp)
            }

            TextButton(onClick = { onLoadSample(2) }) {
                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(15.dp), tint = colors.teal)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Hindi Sample", color = colors.teal, fontSize = 12.sp)
            }
        }
    }
}

data class SubjectStat(val name: String, val setCount: Int, val attemptCount: Int)

@Composable
fun SubjectDashboard(savedPapers: List<PaperEntity>, allAttempts: List<AttemptEntity>) {
    val colors = ExamTheme.colors
    
    val stats = remember(savedPapers, allAttempts) {
        savedPapers.groupBy { it.subject.trim().lowercase() }.map { (key, papers) ->
            val paperIds = papers.map { it.id }.toSet()
            val attempts = allAttempts.count { it.paperId in paperIds }
            SubjectStat(
                name = papers.first().subject,
                setCount = papers.size,
                attemptCount = attempts
            )
        }.sortedByDescending { it.setCount }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Subject Overview",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = colors.inkBold,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(stats) { stat ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                    border = BorderStroke(1.dp, colors.border),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.width(160.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = colors.tealLight,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = colors.teal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = stat.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = colors.muted, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${stat.setCount} Sets", fontSize = 12.sp, color = colors.muted)
                        }
                        
                        Spacer(modifier = Modifier.height(2.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Assignment, contentDescription = null, tint = colors.muted, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${stat.attemptCount} Attempts", fontSize = 12.sp, color = colors.muted)
                        }
                    }
                }
            }
        }
    }
}
INNER_EOF

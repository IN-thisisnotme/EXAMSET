package com.example.util

import android.util.Log
import com.example.model.VoiceAnalysisResult
import com.example.model.VoiceFillerCategory
import com.example.model.VoiceFillerOccurrence
import com.example.model.VoicePracticeMode
import com.example.model.VoicePromptItem
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "VoiceAiAnalyzer"
private const val MAX_INLINE_AUDIO_BYTES = 19 * 1024 * 1024

interface VoiceAnalysisProvider {
    suspend fun analyzeAudio(
        audioBytes: ByteArray,
        durationMs: Long,
        rmsSamples: List<Float>,
        prompt: VoicePromptItem,
        mode: VoicePracticeMode
    ): Result<VoiceAnalysisResult>
}

class FirebaseVoiceAiAnalyzer : VoiceAnalysisProvider {

    private val responseSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "verbatimTranscript" to Schema.string(
                    description = "Exact verbatim transcript of the audio. Preserve every vocal hesitation and filler sound literally, such as [em], [um], [uh], [er], [ah], [hmm], false starts, and repeated words."
                ),
                "fillers" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "token" to Schema.string(description = "Normalized filler token e.g. em, um, uh, er, ah, hmm, like, actually, basically, you know"),
                            "count" to Schema.integer(description = "Number of times this filler occurred in the audio")
                        )
                    ),
                    description = "All vocal hesitation sounds and filler phrases detected in the audio"
                ),
                "topicAccuracyPercent" to Schema.integer(
                    description = "0 to 100 score for how accurately the speaker covered the subject prompt or read the passage"
                ),
                "strengths" to Schema.array(
                    items = Schema.string(),
                    description = "1 to 3 concise strengths in delivery or subject understanding"
                ),
                "coachingTips" to Schema.array(
                    items = Schema.string(),
                    description = "1 to 3 concise actionable speaking tips for reducing em/um fillers and improving clarity"
                )
            ),
            optionalProperties = listOf("strengths", "coachingTips")
        )
    }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = this@FirebaseVoiceAiAnalyzer.responseSchema
                temperature = 0.1f
            }
        )
    }

    override suspend fun analyzeAudio(
        audioBytes: ByteArray,
        durationMs: Long,
        rmsSamples: List<Float>,
        prompt: VoicePromptItem,
        mode: VoicePracticeMode
    ): Result<VoiceAnalysisResult> {
        if (audioBytes.size <= 44) {
            return Result.failure(IllegalArgumentException("Recorded audio is empty. Please speak for at least 1 second."))
        }
        if (audioBytes.size > MAX_INLINE_AUDIO_BYTES) {
            return Result.failure(IllegalArgumentException("Recording exceeds 19 MB limit. Keep practice clips under 3 minutes."))
        }

        val instruction = buildString {
            appendLine("You are a strict verbatim speech-fluency and subject-recall evaluator.")
            appendLine("Listen carefully to the attached audio recording.")
            appendLine("CRITICAL: Do NOT clean up or omit disfluencies. Transcribe EVERY hesitation sound literally inside brackets or plain words, especially 'em', 'um', 'uh', 'er', 'ah', 'hmm', false starts, and word repetitions.")
            appendLine("Mode: ${mode.label}")
            appendLine("Subject Folder: ${prompt.folderPath}")
            appendLine("Prompt Shown to Student: ${prompt.promptQuestion}")
            if (prompt.formulaExpressions.isNotEmpty()) {
                appendLine("Key Formulas / Expressions: ${prompt.formulaExpressions.joinToString(" | ")}")
            }
            if (mode != VoicePracticeMode.FREE_SUBJECT_SPEAK && prompt.referenceSummary.isNotBlank()) {
                appendLine("Reference Subject Content: ${prompt.referenceSummary}")
                appendLine("Note: If the reference contains mathematical/chemical formulas, LaTeX symbols, or multiple expressions, accept natural spoken verbalizations (e.g., 'equals', 'plus', 'divided by', 'squared', 'yields') as accurate recall.")
            }
            appendLine("Return valid JSON matching the schema.")
        }

        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        inlineData(audioBytes, "audio/wav")
                        text(instruction)
                    }
                )
            }
            val rawJson = response.text
            if (rawJson.isNullOrBlank()) {
                return Result.failure(IllegalStateException("Gemini returned an empty response."))
            }

            val json = JSONObject(rawJson)
            val transcript = json.optString("verbatimTranscript", "").trim()

            val aiFillers = mutableListOf<VoiceFillerOccurrence>()
            val fillersArr = json.optJSONArray("fillers")
            if (fillersArr != null) {
                for (i in 0 until fillersArr.length()) {
                    val obj = fillersArr.optJSONObject(i) ?: continue
                    val token = obj.optString("token", "").trim().lowercase()
                    val count = obj.optInt("count", 0)
                    if (token.isNotBlank() && count > 0) {
                        val cat = if (token in setOf("em", "emm", "um", "umm", "uh", "uhh", "er", "err", "ah", "hmm", "hm", "erm", "uhm")) {
                            VoiceFillerCategory.VOCAL_DISFLUENCY
                        } else {
                            VoiceFillerCategory.LEXICAL_FILLER
                        }
                        aiFillers.add(VoiceFillerOccurrence(token = token, count = count, category = cat))
                    }
                }
            }

            // Merge AI-reported counts with lexical extraction from the verbatim transcript so counts stay consistent
            val transcriptFillers = VoiceFluencyEngine.extractFillersFromTranscript(transcript)
            val mergedMap = linkedMapOf<String, VoiceFillerOccurrence>()
            for (f in transcriptFillers) {
                mergedMap[f.token] = f
            }
            for (f in aiFillers) {
                val existing = mergedMap[f.token]
                if (existing == null || f.count > existing.count) {
                    mergedMap[f.token] = f
                }
            }
            val mergedFillers = mergedMap.values.sortedWith(
                compareByDescending<VoiceFillerOccurrence> { it.count }.thenBy { it.token }
            )

            val metrics = VoiceFluencyEngine.computeMetrics(
                durationMs = durationMs,
                rmsSamples = rmsSamples,
                transcript = transcript,
                referenceSummary = prompt.referenceSummary,
                mode = mode,
                explicitFillerOverrides = mergedFillers
            )

            val topicAccuracy = if (json.has("topicAccuracyPercent")) {
                json.optInt("topicAccuracyPercent", -1).takeIf { it in 0..100 }
            } else {
                metrics.keywordCoveragePercent
            }

            val strengths = buildList {
                val arr = json.optJSONArray("strengths")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).trim().takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }
            val tips = buildList {
                val arr = json.optJSONArray("coachingTips")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).trim().takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }

            Result.success(
                VoiceAnalysisResult(
                    verbatimTranscript = transcript,
                    annotatedSegments = VoiceFluencyEngine.tokenizeTranscriptForHighlight(transcript),
                    metrics = metrics,
                    topicAccuracyPercent = topicAccuracy,
                    strengths = strengths,
                    coachingTips = tips,
                    usedAiAudioAnalysis = true,
                    engineLabel = "Gemini Multimodal Audio (em/um Verbatim)"
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Voice analysis timed out", e)
            Result.failure(Exception("Audio analysis timed out after 60 seconds. Check your connection and try again.", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Voice analysis failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Unable to analyze audio right now.", e))
        }
    }
}

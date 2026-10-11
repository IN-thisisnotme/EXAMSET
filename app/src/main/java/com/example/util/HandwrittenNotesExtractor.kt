package com.example.util

import android.content.Context
import android.net.Uri
import com.example.model.ParseResult
import com.example.model.QuestionPaper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class NotesExtractionResult(
    val rawOcrText: String,
    val normalizedText: String,
    val parseResult: ParseResult?,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

/**
 * On-Device AI / OCR Extraction Engine for Handwritten Question Notes.
 * - Extracts text from camera photos locally without needing any API keys.
 * - Applies intelligent handwriting cleanup and heuristics to reconstruct standard MCQ structure.
 * - Feeds normalized text into McqParser to generate a QuestionPaper.
 *
 * Gated by FeatureToggles.ENABLE_CAMERA_NOTES_SCANNER.
 */
object HandwrittenNotesExtractor {

    /**
     * Extracts text from an image URI using on-device ML Kit Text Recognition,
     * normalizes handwriting quirks, and parses it into structured questions.
     */
    suspend fun extractFromImageUri(context: Context, imageUri: Uri): NotesExtractionResult {
        if (!FeatureToggles.ENABLE_CAMERA_NOTES_SCANNER) {
            return NotesExtractionResult(
                rawOcrText = "",
                normalizedText = "",
                parseResult = null,
                isSuccess = false,
                errorMessage = "Camera notes scanner feature is currently disabled."
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val inputImage = InputImage.fromFilePath(context, imageUri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                val rawText: String = suspendCancellableCoroutine { continuation ->
                    recognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            continuation.resume(visionText.text)
                        }
                        .addOnFailureListener { exception ->
                            continuation.resume("")
                        }
                }

                if (rawText.isBlank()) {
                    return@withContext NotesExtractionResult(
                        rawOcrText = "",
                        normalizedText = "",
                        parseResult = null,
                        isSuccess = false,
                        errorMessage = "No text could be recognized in the captured photo. Please ensure good lighting and clear handwriting."
                    )
                }

                val cleaned = normalizeHandwrittenText(rawText)
                val parseRes = McqParser.parseWithResult(cleaned)

                NotesExtractionResult(
                    rawOcrText = rawText,
                    normalizedText = cleaned,
                    parseResult = parseRes,
                    isSuccess = true,
                    errorMessage = null
                )
            } catch (e: Exception) {
                NotesExtractionResult(
                    rawOcrText = "",
                    normalizedText = "",
                    parseResult = null,
                    isSuccess = false,
                    errorMessage = "Error processing image: ${e.localizedMessage ?: "Unknown error"}"
                )
            }
        }
    }

    /**
     * Normalizes handwriting OCR output:
     * - Fixes common misread option bullets (e.g. `(a`, `(b`, `A)`, `B)`, `[A]`, `1)`, `2)`).
     * - Fixes question numbers (e.g. `Ql.`, `O1.`, `Q-1`).
     * - Corrects common answer tags (`Ans:`, `Key:`, `Answer:`).
     * - Inserts missing linebreaks so options (A)-(D) are cleanly formatted for McqParser.
     */
    fun normalizeHandwrittenText(raw: String): String {
        var text = raw.replace("\r", "\n")

        // 1. Normalize OCR question numbering glitches: Ql -> Q1, O1 -> Q1, q.1 -> Q1.
        text = text.replace(Regex("""(?im)^\s*(?:[Qq][lI]|[Oo]1)\b\s*[\.:\-]?\s*"""), "1. ")
        text = text.replace(Regex("""(?im)^\s*[Qq]\.?\s*(\d{1,4})\s*[\.:\-]?\s*"""), "$1. ")

        // 2. Normalize option headers on separate lines:
        // "( a )", "[A]", "a)", "A.", "A -" -> "(A)"
        text = text.replace(Regex("""(?im)(?:^|\s{2,}|\n)\s*[\(\[]\s*([A-Da-d1-4])\s*[\)\]]\s*"""), "\n($1) ")
        text = text.replace(Regex("""(?im)(?:^|\s{2,}|\n)\s*([A-Da-d])\s*[\.\:\)\-–—]\s*"""), "\n($1) ")

        // 3. Ensure options starting with lowercase letters are capitalized: (a) -> (A), (b) -> (B)
        text = text.replace(Regex("""(?m)^\s*\(([a-d])\)""")) { match ->
            "(${match.groupValues[1].uppercase()})"
        }

        // 4. Normalize answer key tags: "Ans :", "Ans-", "Ans.", "Key :"
        text = text.replace(Regex("""(?im)^\s*(?:Ans|Answer|Key|Correct)\s*[:\-–—\.]\s*"""), "Answer: ")

        // 5. Normalize explanation tags: "Exp :", "Exp-", "Sol :"
        text = text.replace(Regex("""(?im)^\s*(?:Exp|Explanation|Sol|Solution)\s*[:\-–—\.]\s*"""), "Explanation: ")

        // 6. Split merged option lines if two options were captured on the same line:
        // e.g. "(A) Carbon (B) Hydrogen" -> "(A) Carbon\n(B) Hydrogen"
        text = text.replace(Regex("""([^\n])\s{2,}\(([B-Db-d])\)"""), "$1\n($2)")

        // 7. Remove excessive blank lines while preserving block boundaries
        text = text.replace(Regex("""\n{3,}"""), "\n\n")

        return text.trim()
    }
}

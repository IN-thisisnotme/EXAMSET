package com.example.util

import com.example.data.ExamSetPreferences
import com.example.model.VocabularyDeck
import com.example.model.VocabularyEntry
import com.example.model.VocabularyExample
import com.example.model.VocabularyLanguageVariants
import com.example.model.VocabularyLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

enum class WordOfTheDaySource(val label: String) {
    LIVE_DICTIONARY("Live Dictionary"),
    CURATED_DICTIONARY("Built-in Dictionary"),
    SAVED_LIBRARY("From Your Library")
}

sealed class WordOfTheDayUiState {
    data class Loading(val variant: String) : WordOfTheDayUiState()
    data class Ready(
        val entry: VocabularyEntry,
        val dateLabel: String,
        val variant: String,
        val source: WordOfTheDaySource,
        val existingDeckId: String? = null,
        val existingDeckTitle: String? = null
    ) : WordOfTheDayUiState()

    data class Unavailable(
        val candidateWord: String,
        val dateLabel: String,
        val variant: String,
        val message: String
    ) : WordOfTheDayUiState()
}

private data class CuratedDailyWord(
    val ukWord: String,
    val usWord: String = ukWord,
    val ukPronunciation: String,
    val usPronunciation: String = ukPronunciation,
    val partOfSpeech: String,
    val levelCode: String,
    val meaning: String,
    val examples: List<VocabularyExample>,
    val synonyms: List<String>,
    val antonyms: List<String> = emptyList(),
    val usageNote: String = ""
)

/**
 * Fetches authentic daily vocabulary spotlights using the public Free Dictionary API
 * (`api.dictionaryapi.dev`) with UK/US phonetic selection, persisted daily in preferences,
 * and backed by a curated offline dictionary (64 complete UK/US entries) + user library
 * fallback so Word of the Day always renders properly even offline with zero saved words.
 */
object WordOfTheDayService {

    private val CURATED_OFFLINE_DICTIONARY: List<CuratedDailyWord> = listOf(
        CuratedDailyWord(
            ukWord = "Perspicacious",
            ukPronunciation = "/ˌpɜː.spɪˈkeɪ.ʃəs/",
            usPronunciation = "/ˌpɝː.spɪˈkeɪ.ʃəs/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.MASTERY.code,
            meaning = "Having a ready insight into and understanding of things; mentally sharp and discerning.",
            examples = listOf(
                VocabularyExample("Editorial", "A perspicacious observer could see the policy shift months in advance."),
                VocabularyExample("Academic", "Her perspicacious critique uncovered a flaw in the methodology.")
            ),
            synonyms = listOf("astute", "discerning", "shrewd", "perceptive"),
            antonyms = listOf("obtuse", "unobservant")
        ),
        CuratedDailyWord(
            ukWord = "Ephemeral",
            ukPronunciation = "/ɪˈfem.ər.əl/",
            usPronunciation = "/ɪˈfem.ɚ.əl/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Lasting for a very short time; transitory and fleeting.",
            examples = listOf(
                VocabularyExample("Everyday", "Trends on social media are notoriously ephemeral."),
                VocabularyExample("Literary", "Cherry blossoms are celebrated for their ephemeral spring beauty.")
            ),
            synonyms = listOf("fleeting", "transient", "short-lived", "momentary"),
            antonyms = listOf("enduring", "permanent", "perpetual")
        ),
        CuratedDailyWord(
            ukWord = "Equanimity",
            ukPronunciation = "/ˌek.wəˈnɪm.ə.ti/",
            usPronunciation = "/ˌiː.kwəˈnɪm.ə.t̬i/",
            partOfSpeech = "Noun",
            levelCode = VocabularyLevel.MASTERY.code,
            meaning = "Calmness and composure, especially in a difficult or stressful situation.",
            examples = listOf(
                VocabularyExample("Workplace", "She handled the tense board meeting with remarkable equanimity."),
                VocabularyExample("Everyday", "Practising mindfulness helps cultivate equanimity under pressure.")
            ),
            synonyms = listOf("composure", "serenity", "poise", "calmness"),
            antonyms = listOf("agitation", "panic", "anxiety")
        ),
        CuratedDailyWord(
            ukWord = "Cogent",
            ukPronunciation = "/ˈkəʊ.dʒənt/",
            usPronunciation = "/ˈkoʊ.dʒənt/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Clear, logical, and convincing in argument or presentation.",
            examples = listOf(
                VocabularyExample("Academic", "He presented a cogent case for revising the curriculum."),
                VocabularyExample("Workplace", "The report offered three cogent reasons to delay the launch.")
            ),
            synonyms = listOf("compelling", "persuasive", "lucid", "convincing"),
            antonyms = listOf("weak", "muddled", "unconvincing")
        ),
        CuratedDailyWord(
            ukWord = "Sagacious",
            ukPronunciation = "/səˈɡeɪ.ʃəs/",
            usPronunciation = "/səˈɡeɪ.ʃəs/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.MASTERY.code,
            meaning = "Having or showing keen mental discernment and sound practical judgement.",
            examples = listOf(
                VocabularyExample("Editorial", "Sagacious investors look beyond short-term market noise."),
                VocabularyExample("Formal", "Her sagacious counsel prevented a costly diplomatic error.")
            ),
            synonyms = listOf("wise", "judicious", "prudent", "insightful"),
            antonyms = listOf("foolish", "rash", "shortsighted")
        ),
        CuratedDailyWord(
            ukWord = "Ameliorate",
            ukPronunciation = "/əˈmiː.li.ə.reɪt/",
            usPronunciation = "/əˈmiːl.jə.reɪt/",
            partOfSpeech = "Verb",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "To make a bad or unsatisfactory situation better or more tolerable.",
            examples = listOf(
                VocabularyExample("News", "New transit subsidies aim to ameliorate traffic congestion."),
                VocabularyExample("Workplace", "Clear guidelines helped ameliorate friction between teams.")
            ),
            synonyms = listOf("improve", "alleviate", "ease", "mitigate"),
            antonyms = listOf("worsen", "aggravate", "exacerbate")
        ),
        CuratedDailyWord(
            ukWord = "Fastidious",
            ukPronunciation = "/fæsˈtɪd.i.əs/",
            usPronunciation = "/fæˈstɪd.i.əs/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Very attentive to accuracy and detail; hard to please regarding standards.",
            examples = listOf(
                VocabularyExample("Everyday", "He is fastidious about keeping his desk spotless."),
                VocabularyExample("Workplace", "Watchmakers require fastidious attention to tiny components.")
            ),
            synonyms = listOf("meticulous", "scrupulous", "exacting", "particular"),
            antonyms = listOf("careless", "sloppy", "indifferent")
        ),
        CuratedDailyWord(
            ukWord = "Quintessential",
            ukPronunciation = "/ˌkwɪn.tɪˈsen.ʃəl/",
            usPronunciation = "/ˌkwɪn.t̬əˈsen.ʃəl/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Representing the most perfect or typical example of a quality or class.",
            examples = listOf(
                VocabularyExample("Culture", "Afternoon tea in a garden is the quintessential British summer ritual."),
                VocabularyExample("Everyday", "This small café is the quintessential neighbourhood hangout.")
            ),
            synonyms = listOf("archetypal", "classic", "definitive", "exemplary"),
            antonyms = listOf("atypical", "uncharacteristic")
        ),
        CuratedDailyWord(
            ukWord = "Serendipity",
            ukPronunciation = "/ˌser.ənˈdɪp.ə.ti/",
            usPronunciation = "/ˌser.ənˈdɪp.ə.t̬i/",
            partOfSpeech = "Noun",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "The occurrence of finding valuable or agreeable things by chance when looking for something else.",
            examples = listOf(
                VocabularyExample("Everyday", "Meeting my co-founder at a delayed train platform was pure serendipity."),
                VocabularyExample("Academic", "Several major medical breakthroughs arose through scientific serendipity.")
            ),
            synonyms = listOf("fortuity", "fluke", "happy accident"),
            antonyms = listOf("misfortune", "mishap")
        ),
        CuratedDailyWord(
            ukWord = "Magnanimous",
            ukPronunciation = "/mæɡˈnæn.ɪ.məs/",
            usPronunciation = "/mæɡˈnæn.ə.məs/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Generous or forgiving, especially towards a rival or less powerful person.",
            examples = listOf(
                VocabularyExample("News", "The champion was magnanimous in victory and praised her opponent."),
                VocabularyExample("Everyday", "It was magnanimous of him to overlook the honest mistake.")
            ),
            synonyms = listOf("generous", "charitable", "gracious", "noble"),
            antonyms = listOf("petty", "vindictive", "spiteful")
        ),
        CuratedDailyWord(
            ukWord = "Laconic",
            ukPronunciation = "/ləˈkɒn.ɪk/",
            usPronunciation = "/ləˈkɑː.nɪk/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.MASTERY.code,
            meaning = "Using very few words; concise to the point of seeming blunt or mysterious.",
            examples = listOf(
                VocabularyExample("Literary", "His laconic reply of 'Perhaps' left everyone guessing."),
                VocabularyExample("Workplace", "The editor preferred laconic summaries over long introductions.")
            ),
            synonyms = listOf("terse", "succinct", "concise", "brief"),
            antonyms = listOf("verbose", "loquacious", "wordy")
        ),
        CuratedDailyWord(
            ukWord = "Alacrity",
            ukPronunciation = "/əˈlæk.rə.ti/",
            usPronunciation = "/əˈlæk.rə.t̬i/",
            partOfSpeech = "Noun",
            levelCode = VocabularyLevel.MASTERY.code,
            meaning = "Brisk and cheerful readiness or eagerness to do something.",
            examples = listOf(
                VocabularyExample("Workplace", "She accepted the overseas assignment with alacrity."),
                VocabularyExample("Everyday", "The students volunteered for the field trip with surprising alacrity.")
            ),
            synonyms = listOf("eagerness", "readiness", "enthusiasm", "promptness"),
            antonyms = listOf("reluctance", "apathy", "hesitation")
        ),
        CuratedDailyWord(
            ukWord = "Tenacious",
            ukPronunciation = "/təˈneɪ.ʃəs/",
            usPronunciation = "/təˈneɪ.ʃəs/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Tending to keep a firm hold of something; determined and persistent.",
            examples = listOf(
                VocabularyExample("Editorial", "Tenacious investigative reporters followed the paper trail for years."),
                VocabularyExample("Everyday", "Ivy has a tenacious grip on old stone walls.")
            ),
            synonyms = listOf("persistent", "resolute", "dogged", "steadfast"),
            antonyms = listOf("irresolute", "yielding", "half-hearted")
        ),
        CuratedDailyWord(
            ukWord = "Equivocal",
            ukPronunciation = "/ɪˈkwɪv.ə.kəl/",
            usPronunciation = "/ɪˈkwɪv.ə.kəl/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Open to more than one interpretation; intentionally ambiguous or uncertain.",
            examples = listOf(
                VocabularyExample("News", "The spokesperson gave an equivocal answer on the tax timeline."),
                VocabularyExample("Academic", "Early trial results remain equivocal and need larger samples.")
            ),
            synonyms = listOf("ambiguous", "vague", "evasive", "unclear"),
            antonyms = listOf("unequivocal", "explicit", "clear")
        ),
        CuratedDailyWord(
            ukWord = "Pragmatic",
            ukPronunciation = "/præɡˈmæt.ɪk/",
            usPronunciation = "/præɡˈmæt̬.ɪk/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Dealing with things sensibly and realistically in a way based on practical rather than theoretical considerations.",
            examples = listOf(
                VocabularyExample("Workplace", "We need a pragmatic schedule that accounts for real delays."),
                VocabularyExample("Everyday", "Buying a reliable used car was the pragmatic choice.")
            ),
            synonyms = listOf("practical", "realistic", "sensible", "down-to-earth"),
            antonyms = listOf("idealistic", "impractical", "quixotic")
        ),
        CuratedDailyWord(
            ukWord = "Resilient",
            ukPronunciation = "/rɪˈzɪl.i.ənt/",
            usPronunciation = "/rɪˈzɪl.jənt/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "Able to withstand or recover quickly from difficult conditions or setbacks.",
            examples = listOf(
                VocabularyExample("Everyday", "Coastal communities have proven remarkably resilient after storms."),
                VocabularyExample("Workplace", "Diverse suppliers make our supply chain more resilient.")
            ),
            synonyms = listOf("adaptable", "hardy", "buoyant", "tough"),
            antonyms = listOf("fragile", "brittle", "vulnerable")
        ),
        CuratedDailyWord(
            ukWord = "Corroborate",
            ukPronunciation = "/kəˈrɒb.ə.reɪt/",
            usPronunciation = "/kəˈrɑː.bə.reɪt/",
            partOfSpeech = "Verb",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "To confirm or give independent support to a statement, theory, or finding.",
            examples = listOf(
                VocabularyExample("Academic", "Satellite imagery corroborated the geologists' field measurements."),
                VocabularyExample("News", "Two witnesses independently corroborated the account.")
            ),
            synonyms = listOf("verify", "confirm", "substantiate", "validate"),
            antonyms = listOf("contradict", "refute", "disprove")
        ),
        CuratedDailyWord(
            ukWord = "Discrepancy",
            ukPronunciation = "/dɪˈskrep.ən.si/",
            usPronunciation = "/dɪˈskrep.ən.si/",
            partOfSpeech = "Noun",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "An unexpected difference between two figures, facts, or accounts that should match.",
            examples = listOf(
                VocabularyExample("Workplace", "Auditors flagged a minor discrepancy between the invoice and ledger."),
                VocabularyExample("Academic", "Researchers investigated the discrepancy in reaction times.")
            ),
            synonyms = listOf("inconsistency", "divergence", "mismatch", "variance"),
            antonyms = listOf("consistency", "agreement", "parity")
        ),
        CuratedDailyWord(
            ukWord = "Superfluous",
            ukPronunciation = "/suːˈpɜː.flu.əs/",
            usPronunciation = "/suːˈpɜːr.flu.əs/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Unnecessary, especially through being more than enough.",
            examples = listOf(
                VocabularyExample("Academic", "Trim superfluous adjectives to make your prose sharper."),
                VocabularyExample("Everyday", "Heavy coats felt superfluous once the sun came out.")
            ),
            synonyms = listOf("redundant", "excessive", "surplus", "unneeded"),
            antonyms = listOf("essential", "necessary", "vital")
        ),
        CuratedDailyWord(
            ukWord = "Obfuscate",
            ukPronunciation = "/ˈɒb.fʌs.keɪt/",
            usPronunciation = "/ˈɑːb.fʌs.keɪt/",
            partOfSpeech = "Verb",
            levelCode = VocabularyLevel.MASTERY.code,
            meaning = "To make something obscure, unclear, or difficult to understand, often deliberately.",
            examples = listOf(
                VocabularyExample("Editorial", "Dense legal jargon can obfuscate simple consumer rights."),
                VocabularyExample("Workplace", "Avoid acronyms that obfuscate the real project risks.")
            ),
            synonyms = listOf("obscure", "muddy", "complicate", "cloud"),
            antonyms = listOf("clarify", "elucidate", "illuminate")
        ),
        CuratedDailyWord(
            ukWord = "Ebullient",
            ukPronunciation = "/ɪˈbʊl.i.ənt/",
            usPronunciation = "/ɪˈbʌl.jənt/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.MASTERY.code,
            meaning = "Cheerful and full of lively energy and enthusiasm.",
            examples = listOf(
                VocabularyExample("Everyday", "The coach was in an ebullient mood after the comeback win."),
                VocabularyExample("Literary", "Her ebullient storytelling kept the entire table laughing.")
            ),
            synonyms = listOf("exuberant", "buoyant", "vivacious", "spirited"),
            antonyms = listOf("subdued", "gloomy", "melancholy")
        ),
        CuratedDailyWord(
            ukWord = "Pernicious",
            ukPronunciation = "/pəˈnɪʃ.əs/",
            usPronunciation = "/pɚˈnɪʃ.əs/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.MASTERY.code,
            meaning = "Having a harmful effect, especially in a gradual, subtle, or hidden way.",
            examples = listOf(
                VocabularyExample("Editorial", "Chronic sleep deprivation has a pernicious impact on memory."),
                VocabularyExample("Academic", "Misinformation exerts a pernicious influence on public trust.")
            ),
            synonyms = listOf("insidious", "damaging", "destructive", "malign"),
            antonyms = listOf("benign", "beneficial", "wholesome")
        ),
        CuratedDailyWord(
            ukWord = "Candour",
            usWord = "Candor",
            ukPronunciation = "/ˈkæn.də(r)/",
            usPronunciation = "/ˈkæn.dɚ/",
            partOfSpeech = "Noun",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "The quality of being open, honest, and straightforward in expression.",
            examples = listOf(
                VocabularyExample("Workplace", "Thank you for speaking with such candour during the review."),
                VocabularyExample("Editorial", "Voters appreciated the candidate's rare candour about fiscal limits.")
            ),
            synonyms = listOf("frankness", "openness", "sincerity", "honesty"),
            antonyms = listOf("evasiveness", "deceit", "guile")
        ),
        CuratedDailyWord(
            ukWord = "Mitigate",
            ukPronunciation = "/ˈmɪt.ɪ.ɡeɪt/",
            usPronunciation = "/ˈmɪt̬.ɪ.ɡeɪt/",
            partOfSpeech = "Verb",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "To make something less severe, serious, or painful.",
            examples = listOf(
                VocabularyExample("Academic", "Restored wetlands help mitigate seasonal flooding."),
                VocabularyExample("Workplace", "Automated backups mitigate the risk of data loss.")
            ),
            synonyms = listOf("alleviate", "lessen", "reduce", "soften"),
            antonyms = listOf("aggravate", "intensify", "worsen")
        ),
        CuratedDailyWord(
            ukWord = "Proliferate",
            ukPronunciation = "/prəˈlɪf.ər.eɪt/",
            usPronunciation = "/prəˈlɪf.ə.reɪt/",
            partOfSpeech = "Verb",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "To increase rapidly in number or multiply widely.",
            examples = listOf(
                VocabularyExample("News", "Electric bicycle lanes have proliferated across European cities."),
                VocabularyExample("Academic", "Microbial colonies proliferate quickly in warm conditions.")
            ),
            synonyms = listOf("multiply", "mushroom", "expand", "spread"),
            antonyms = listOf("dwindle", "shrink", "decline")
        ),
        CuratedDailyWord(
            ukWord = "Indefatigable",
            ukPronunciation = "/ˌɪn.dɪˈfæt.ɪ.ɡə.bəl/",
            usPronunciation = "/ˌɪn.dɪˈfæt̬.ɪ.ɡə.bəl/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.MASTERY.code,
            meaning = "Persisting tirelessly without showing fatigue or losing determination.",
            examples = listOf(
                VocabularyExample("Editorial", "An indefatigable volunteer, she organised food drives every weekend."),
                VocabularyExample("Academic", "His indefatigable archival research uncovered letters thought lost.")
            ),
            synonyms = listOf("tireless", "untiring", "unflagging", "relentless"),
            antonyms = listOf("lethargic", "weary", "half-hearted")
        ),
        CuratedDailyWord(
            ukWord = "Endeavour",
            usWord = "Endeavor",
            ukPronunciation = "/ɪnˈdev.ə(r)/",
            usPronunciation = "/ɪnˈdev.ɚ/",
            partOfSpeech = "Noun / Verb",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "An earnest attempt or serious effort to achieve a goal; to try hard to do something.",
            examples = listOf(
                VocabularyExample("Academic", "Space exploration remains a collaborative human endeavour."),
                VocabularyExample("Formal", "We shall endeavour to respond within two working days.")
            ),
            synonyms = listOf("undertaking", "effort", "enterprise", "strive"),
            antonyms = listOf("neglect", "idleness")
        ),
        CuratedDailyWord(
            ukWord = "Rigour",
            usWord = "Rigor",
            ukPronunciation = "/ˈrɪɡ.ə(r)/",
            usPronunciation = "/ˈrɪɡ.ɚ/",
            partOfSpeech = "Noun",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "The quality of being extremely thorough, accurate, and strict in standards.",
            examples = listOf(
                VocabularyExample("Academic", "Peer review upholds the analytical rigour of published studies."),
                VocabularyExample("Workplace", "We apply equal rigour to security testing on every release.")
            ),
            synonyms = listOf("thoroughness", "precision", "strictness", "exactitude"),
            antonyms = listOf("laxity", "carelessness")
        ),
        CuratedDailyWord(
            ukWord = "Sceptical",
            usWord = "Skeptical",
            ukPronunciation = "/ˈskep.tɪ.kəl/",
            usPronunciation = "/ˈskep.tɪ.kəl/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "Not easily convinced; having doubts or reservations until solid evidence is shown.",
            examples = listOf(
                VocabularyExample("Everyday", "Consumers are rightly sceptical of overnight miracle claims."),
                VocabularyExample("Editorial", "Economists remained sceptical about the optimistic growth forecast.")
            ),
            synonyms = listOf("doubtful", "dubious", "questioning", "unconvinced"),
            antonyms = listOf("credulous", "gullible", "convinced")
        ),
        CuratedDailyWord(
            ukWord = "Veracity",
            ukPronunciation = "/vəˈræs.ə.ti/",
            usPronunciation = "/vəˈræs.ə.t̬i/",
            partOfSpeech = "Noun",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Conformity to facts; accuracy and habitual truthfulness.",
            examples = listOf(
                VocabularyExample("News", "Fact-checkers verified the veracity of the leaked transcript."),
                VocabularyExample("Formal", "No one in the chamber doubted the witness's veracity.")
            ),
            synonyms = listOf("truthfulness", "accuracy", "authenticity", "fidelity"),
            antonyms = listOf("falsehood", "mendacity", "fabrication")
        ),
        CuratedDailyWord(
            ukWord = "Articulate",
            ukPronunciation = "/ɑːˈtɪk.jə.lət/",
            usPronunciation = "/ɑːrˈtɪk.jə.lət/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "Having or showing the ability to speak or write fluently and coherently.",
            examples = listOf(
                VocabularyExample("Everyday", "She is an articulate spokesperson for youth climate initiatives."),
                VocabularyExample("Workplace", "Articulate documentation saves hours of onboarding time.")
            ),
            synonyms = listOf("eloquent", "fluent", "lucid", "coherent"),
            antonyms = listOf("inarticulate", "halting", "muddled")
        ),
        CuratedDailyWord(
            ukWord = "Lucid",
            ukPronunciation = "/ˈluː.sɪd/",
            usPronunciation = "/ˈluː.sɪd/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Expressed clearly and easy to understand; showing clear rational thought.",
            examples = listOf(
                VocabularyExample("Academic", "The professor gave a lucid explanation of quantum entanglement."),
                VocabularyExample("Editorial", "Her lucid prose makes complex economics accessible.")
            ),
            synonyms = listOf("clear", "intelligible", "coherent", "transparent"),
            antonyms = listOf("obscure", "cryptic", "confused")
        ),
        CuratedDailyWord(
            ukWord = "Salient",
            ukPronunciation = "/ˈseɪ.li.ənt/",
            usPronunciation = "/ˈseɪl.jənt/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Most noticeable, relevant, or important among a group of features or points.",
            examples = listOf(
                VocabularyExample("Workplace", "Let us review the three most salient findings first."),
                VocabularyExample("Academic", "The essay highlights the salient differences between both treaties.")
            ),
            synonyms = listOf("prominent", "notable", "key", "conspicuous"),
            antonyms = listOf("inconspicuous", "marginal", "trivial")
        ),
        CuratedDailyWord(
            ukWord = "Nuance",
            ukPronunciation = "/ˈnjuː.ɑːns/",
            usPronunciation = "/ˈnuː.ɑːns/",
            partOfSpeech = "Noun",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "A subtle difference in or shade of meaning, expression, or tone.",
            examples = listOf(
                VocabularyExample("Everyday", "Reading literature builds sensitivity to linguistic nuance."),
                VocabularyExample("Editorial", "Headline summaries often flatten the nuance of policy debates.")
            ),
            synonyms = listOf("subtlety", "gradation", "shade", "refinement"),
            antonyms = listOf("bluntness", "oversimplification")
        ),
        CuratedDailyWord(
            ukWord = "Scrutinise",
            usWord = "Scrutinize",
            ukPronunciation = "/ˈskruː.tɪ.naɪz/",
            usPronunciation = "/ˈskruː.t̬ən.aɪz/",
            partOfSpeech = "Verb",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "To examine or inspect something closely and thoroughly.",
            examples = listOf(
                VocabularyExample("Workplace", "Inspectors will scrutinise every safety log before certification."),
                VocabularyExample("Academic", "Critics scrutinised the census methodology for sampling bias.")
            ),
            synonyms = listOf("examine", "inspect", "audit", "dissect"),
            antonyms = listOf("skim", "overlook", "gloss over")
        ),
        CuratedDailyWord(
            ukWord = "Empathy",
            ukPronunciation = "/ˈem.pə.θi/",
            usPronunciation = "/ˈem.pə.θi/",
            partOfSpeech = "Noun",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "The ability to understand and share the feelings and perspective of another person.",
            examples = listOf(
                VocabularyExample("Everyday", "Active listening is the foundation of genuine empathy."),
                VocabularyExample("Workplace", "Product design improves dramatically when guided by user empathy.")
            ),
            synonyms = listOf("compassion", "understanding", "rapport", "affinity"),
            antonyms = listOf("apathy", "indifference", "callousness")
        ),
        CuratedDailyWord(
            ukWord = "Catalyst",
            ukPronunciation = "/ˈkæt.əl.ɪst/",
            usPronunciation = "/ˈkæt̬.əl.ɪst/",
            partOfSpeech = "Noun",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "A person or thing that precipitates a major change or accelerates a process.",
            examples = listOf(
                VocabularyExample("News", "High-speed rail served as a catalyst for regional commerce."),
                VocabularyExample("Academic", "Enzymes act as biological catalysts inside living cells.")
            ),
            synonyms = listOf("spark", "impetus", "stimulus", "trigger"),
            antonyms = listOf("deterrent", "inhibitor", "obstacle")
        ),
        CuratedDailyWord(
            ukWord = "Paradigm",
            ukPronunciation = "/ˈpær.ə.daɪm/",
            usPronunciation = "/ˈper.ə.daɪm/",
            partOfSpeech = "Noun",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "A typical example, pattern, or conceptual framework of something.",
            examples = listOf(
                VocabularyExample("Academic", "Plate tectonics created a new paradigm in earth sciences."),
                VocabularyExample("Workplace", "Remote work shifted the traditional office paradigm.")
            ),
            synonyms = listOf("model", "archetype", "framework", "template"),
            antonyms = listOf("anomaly", "exception")
        ),
        CuratedDailyWord(
            ukWord = "Ubiquitous",
            ukPronunciation = "/juːˈbɪk.wɪ.təs/",
            usPronunciation = "/juːˈbɪk.wə.t̬əs/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Present, appearing, or found everywhere at the same time.",
            examples = listOf(
                VocabularyExample("Everyday", "QR codes have become ubiquitous on restaurant tables."),
                VocabularyExample("Academic", "Microplastics are now ubiquitous across marine ecosystems.")
            ),
            synonyms = listOf("omnipresent", "pervasive", "universal", "everywhere"),
            antonyms = listOf("rare", "scarce", "uncommon")
        ),
        CuratedDailyWord(
            ukWord = "Eloquent",
            ukPronunciation = "/ˈel.ə.kwənt/",
            usPronunciation = "/ˈel.ə.kwənt/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "Fluent, expressive, and persuasive in speaking or writing.",
            examples = listOf(
                VocabularyExample("Formal", "She delivered an eloquent defence of public libraries."),
                VocabularyExample("Literary", "Even silence can be an eloquent response.")
            ),
            synonyms = listOf("persuasive", "expressive", "fluent", "silver-tongued"),
            antonyms = listOf("halting", "awkward", "inarticulate")
        ),
        CuratedDailyWord(
            ukWord = "Meticulous",
            ukPronunciation = "/məˈtɪk.jə.ləs/",
            usPronunciation = "/məˈtɪk.jə.ləs/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "Showing great attention to detail; very careful and precise.",
            examples = listOf(
                VocabularyExample("Workplace", "Conservators spent months doing meticulous restoration work."),
                VocabularyExample("Everyday", "He keeps meticulous notes for every book he reads.")
            ),
            synonyms = listOf("thorough", "scrupulous", "painstaking", "precise"),
            antonyms = listOf("careless", "haphazard", "negligent")
        ),
        CuratedDailyWord(
            ukWord = "Succinct",
            ukPronunciation = "/səkˈsɪŋkt/",
            usPronunciation = "/səkˈsɪŋkt/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Briefly and clearly expressed without wasted words.",
            examples = listOf(
                VocabularyExample("Workplace", "Please open the briefing with a succinct executive summary."),
                VocabularyExample("Academic", "A succinct definition captures the essence in one sentence.")
            ),
            synonyms = listOf("concise", "compact", "crisp", "pithy"),
            antonyms = listOf("wordy", "rambling", "prolix")
        ),
        CuratedDailyWord(
            ukWord = "Trenchant",
            ukPronunciation = "/ˈtren.tʃənt/",
            usPronunciation = "/ˈtren.tʃənt/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.MASTERY.code,
            meaning = "Vigorous, incisive, and sharp in expression or style.",
            examples = listOf(
                VocabularyExample("Editorial", "The columnist wrote a trenchant critique of housing policy."),
                VocabularyExample("Academic", "Her trenchant remarks cut straight to the core contradiction.")
            ),
            synonyms = listOf("incisive", "cutting", "keen", "forceful"),
            antonyms = listOf("vague", "timid", "bland")
        ),
        CuratedDailyWord(
            ukWord = "Bolster",
            ukPronunciation = "/ˈbəʊl.stə(r)/",
            usPronunciation = "/ˈboʊl.stɚ/",
            partOfSpeech = "Verb",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "To support, strengthen, or reinforce an argument, morale, or system.",
            examples = listOf(
                VocabularyExample("News", "Fresh export orders helped bolster manufacturing confidence."),
                VocabularyExample("Academic", "Cite primary sources to bolster your thesis.")
            ),
            synonyms = listOf("strengthen", "reinforce", "buttress", "fortify"),
            antonyms = listOf("undermine", "weaken", "erode")
        ),
        CuratedDailyWord(
            ukWord = "Coalesce",
            ukPronunciation = "/ˌkəʊ.əˈles/",
            usPronunciation = "/ˌkoʊ.əˈles/",
            partOfSpeech = "Verb",
            levelCode = VocabularyLevel.MASTERY.code,
            meaning = "To come together to form one unified mass, whole, or movement.",
            examples = listOf(
                VocabularyExample("Editorial", "Scattered civic groups began to coalesce around clean-air reform."),
                VocabularyExample("Academic", "Tiny water droplets coalesce inside storm clouds.")
            ),
            synonyms = listOf("merge", "unite", "converge", "fuse"),
            antonyms = listOf("scatter", "disperse", "fragment")
        ),
        CuratedDailyWord(
            ukWord = "Disparity",
            ukPronunciation = "/dɪˈspær.ə.ti/",
            usPronunciation = "/dɪˈsper.ə.t̬i/",
            partOfSpeech = "Noun",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "A great or unfair difference between two things.",
            examples = listOf(
                VocabularyExample("News", "The report highlights a widening disparity in rural broadband speeds."),
                VocabularyExample("Academic", "Policies aimed to narrow the income disparity across regions.")
            ),
            synonyms = listOf("inequality", "gap", "imbalance", "contrast"),
            antonyms = listOf("parity", "equality", "uniformity")
        ),
        CuratedDailyWord(
            ukWord = "Formidable",
            ukPronunciation = "/ˈfɔː.mɪ.də.bəl/",
            usPronunciation = "/ˈfɔːr.mɪ.də.bəl/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Inspiring fear or deep respect through being impressively large, powerful, or capable.",
            examples = listOf(
                VocabularyExample("Everyday", "She is a formidable debater who prepares for every counter-point."),
                VocabularyExample("Editorial", "Rebuilding the coastal rail network poses a formidable challenge.")
            ),
            synonyms = listOf("daunting", "impressive", "redoubtable", "mighty"),
            antonyms = listOf("trivial", "feeble", "unimpressive")
        ),
        CuratedDailyWord(
            ukWord = "Garner",
            ukPronunciation = "/ˈɡɑː.nə(r)/",
            usPronunciation = "/ˈɡɑːr.nɚ/",
            partOfSpeech = "Verb",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "To gather or collect something, especially information, support, or praise.",
            examples = listOf(
                VocabularyExample("News", "The documentary garnered international acclaim at film festivals."),
                VocabularyExample("Workplace", "Pilot surveys helped garner early stakeholder support.")
            ),
            synonyms = listOf("gather", "earn", "accumulate", "acquire"),
            antonyms = listOf("squander", "lose", "disperse")
        ),
        CuratedDailyWord(
            ukWord = "Preclude",
            ukPronunciation = "/prɪˈkluːd/",
            usPronunciation = "/prɪˈkluːd/",
            partOfSpeech = "Verb",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "To prevent from happening; to make impossible in advance.",
            examples = listOf(
                VocabularyExample("Formal", "Tight deadlines do not preclude careful quality checks."),
                VocabularyExample("Academic", "Rainy weather precluded outdoor telescope observations.")
            ),
            synonyms = listOf("prevent", "rule out", "prohibit", "debar"),
            antonyms = listOf("allow", "enable", "facilitate")
        ),
        CuratedDailyWord(
            ukWord = "Tangible",
            ukPronunciation = "/ˈtæn.dʒə.bəl/",
            usPronunciation = "/ˈtæn.dʒə.bəl/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "Clear and definite; real enough to be touched or measured.",
            examples = listOf(
                VocabularyExample("Workplace", "The mentorship programme delivered tangible gains in retention."),
                VocabularyExample("Everyday", "There was a tangible sense of relief in the exam hall.")
            ),
            synonyms = listOf("concrete", "palpable", "measurable", "real"),
            antonyms = listOf("intangible", "abstract", "elusive")
        ),
        CuratedDailyWord(
            ukWord = "Vindicate",
            ukPronunciation = "/ˈvɪn.dɪ.keɪt/",
            usPronunciation = "/ˈvɪn.dɪ.keɪt/",
            partOfSpeech = "Verb",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "To clear someone of blame or suspicion, or show something to be right or justified.",
            examples = listOf(
                VocabularyExample("News", "New laboratory tests vindicated the engineer's early warning."),
                VocabularyExample("Editorial", "Decades later, archival records vindicated her stance.")
            ),
            synonyms = listOf("exonerate", "justify", "substantiate", "absolve"),
            antonyms = listOf("incriminate", "discredit", "condemn")
        ),
        CuratedDailyWord(
            ukWord = "Zealous",
            ukPronunciation = "/ˈzel.əs/",
            usPronunciation = "/ˈzel.əs/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Having or showing great energy, passion, or enthusiasm in pursuit of a cause.",
            examples = listOf(
                VocabularyExample("Everyday", "She is a zealous advocate for public literacy."),
                VocabularyExample("Formal", "Overly zealous enforcement can alienate cooperative partners.")
            ),
            synonyms = listOf("fervent", "ardent", "passionate", "dedicated"),
            antonyms = listOf("apathetic", "indifferent", "lukewarm")
        ),
        CuratedDailyWord(
            ukWord = "Austere",
            ukPronunciation = "/ɒˈstɪə(r)/",
            usPronunciation = "/ɑːˈstɪr/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Severe or strict in manner or appearance; having no unnecessary comforts or luxuries.",
            examples = listOf(
                VocabularyExample("Literary", "The monastery stood stark and austere against the snowline."),
                VocabularyExample("News", "Families faced an austere winter budget.")
            ),
            synonyms = listOf("spartan", "stern", "unadorned", "ascetic"),
            antonyms = listOf("ornate", "lavish", "indulgent")
        ),
        CuratedDailyWord(
            ukWord = "Complacent",
            ukPronunciation = "/kəmˈpleɪ.sənt/",
            usPronunciation = "/kəmˈpleɪ.sənt/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Showing uncritical satisfaction with oneself or achievements while ignoring potential dangers.",
            examples = listOf(
                VocabularyExample("Workplace", "Market leaders fail when they grow complacent about innovation."),
                VocabularyExample("Everyday", "Do not feel complacent just because practice tests look easy.")
            ),
            synonyms = listOf("smug", "self-satisfied", "unconcerned"),
            antonyms = listOf("vigilant", "anxious", "humble")
        ),
        CuratedDailyWord(
            ukWord = "Nascent",
            ukPronunciation = "/ˈnæs.ənt/",
            usPronunciation = "/ˈneɪ.sənt/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.MASTERY.code,
            meaning = "Just coming into existence and beginning to display signs of future potential.",
            examples = listOf(
                VocabularyExample("News", "Grants were directed toward the region's nascent clean-tech sector."),
                VocabularyExample("Academic", "A nascent democracy requires independent civic institutions.")
            ),
            synonyms = listOf("emerging", "budding", "incipient", "embryonic"),
            antonyms = listOf("mature", "established", "declining")
        ),
        CuratedDailyWord(
            ukWord = "Onerous",
            ukPronunciation = "/ˈəʊ.nər.əs/",
            usPronunciation = "/ˈoʊ.nɚ.əs/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.MASTERY.code,
            meaning = "Involving a great deal of effort, trouble, or burdensome responsibility.",
            examples = listOf(
                VocabularyExample("Workplace", "Small clinics struggled with the onerous paperwork requirements."),
                VocabularyExample("Formal", "Rebuilding the bridge was an onerous engineering task.")
            ),
            synonyms = listOf("burdensome", "arduous", "taxing", "demanding"),
            antonyms = listOf("effortless", "light", "manageable")
        ),
        CuratedDailyWord(
            ukWord = "Plausible",
            ukPronunciation = "/ˈplɔː.zə.bəl/",
            usPronunciation = "/ˈplɑː.zə.bəl/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "Seeming reasonable, credible, or probable.",
            examples = listOf(
                VocabularyExample("Academic", "Both hypotheses offer a plausible explanation of the fossil gap."),
                VocabularyExample("Everyday", "That sounds like a completely plausible excuse.")
            ),
            synonyms = listOf("credible", "believable", "reasonable", "tenable"),
            antonyms = listOf("implausible", "far-fetched", "improbable")
        ),
        CuratedDailyWord(
            ukWord = "Tranquil",
            ukPronunciation = "/ˈtræŋ.kwɪl/",
            usPronunciation = "/ˈtræŋ.kwəl/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "Free from disturbance; calm, peaceful, and quiet.",
            examples = listOf(
                VocabularyExample("Everyday", "Morning mist drifted across the tranquil mountain lake."),
                VocabularyExample("Literary", "She maintained a tranquil expression throughout the ceremony.")
            ),
            synonyms = listOf("serene", "placid", "peaceful", "calm"),
            antonyms = listOf("turbulent", "chaotic", "hectic")
        ),
        CuratedDailyWord(
            ukWord = "Volatile",
            ukPronunciation = "/ˈvɒl.ə.taɪl/",
            usPronunciation = "/ˈvɑː.lə.t̬əl/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "Liable to change rapidly and unpredictably, especially for the worse.",
            examples = listOf(
                VocabularyExample("News", "Commodity prices remained volatile amid supply disruptions."),
                VocabularyExample("Academic", "Volatile organic compounds evaporate quickly at room temperature.")
            ),
            synonyms = listOf("unstable", "erratic", "unpredictable", "mercurial"),
            antonyms = listOf("stable", "steady", "constant")
        ),
        CuratedDailyWord(
            ukWord = "Practise",
            usWord = "Practice",
            ukPronunciation = "/ˈpræk.tɪs/",
            usPronunciation = "/ˈpræk.tɪs/",
            partOfSpeech = "Verb",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "To perform an activity or skill repeatedly in order to improve or maintain proficiency.",
            examples = listOf(
                VocabularyExample("Everyday", "Speaking aloud for ten minutes daily helps you practise fluency."),
                VocabularyExample("Workplace", "Teams practise emergency drills every quarter.")
            ),
            synonyms = listOf("rehearse", "drill", "train", "hone"),
            antonyms = listOf("neglect", "abandon"),
            usageNote = "In UK English, 'practise' is the verb and 'practice' is the noun; US English uses 'practice' for both."
        ),
        CuratedDailyWord(
            ukWord = "Analyse",
            usWord = "Analyze",
            ukPronunciation = "/ˈæn.əl.aɪz/",
            usPronunciation = "/ˈæn.əl.aɪz/",
            partOfSpeech = "Verb",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "To examine something methodically and in detail in order to understand or explain it.",
            examples = listOf(
                VocabularyExample("Academic", "Students learn to analyse primary historical sources critically."),
                VocabularyExample("Workplace", "Let us analyse why regional turnout exceeded expectations.")
            ),
            synonyms = listOf("examine", "evaluate", "dissect", "assess"),
            antonyms = listOf("ignore", "overlook")
        ),
        CuratedDailyWord(
            ukWord = "Judgement",
            usWord = "Judgment",
            ukPronunciation = "/ˈdʒʌdʒ.mənt/",
            usPronunciation = "/ˈdʒʌdʒ.mənt/",
            partOfSpeech = "Noun",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "The ability to make considered decisions or come to sensible conclusions.",
            examples = listOf(
                VocabularyExample("Everyday", "Good editorial judgement takes years of careful reading."),
                VocabularyExample("Formal", "The panel reserved judgement until all records arrived.")
            ),
            synonyms = listOf("discernment", "wisdom", "acumen", "verdict"),
            antonyms = listOf("indiscretion", "folly")
        ),
        CuratedDailyWord(
            ukWord = "Altruistic",
            ukPronunciation = "/ˌæl.truˈɪs.tɪk/",
            usPronunciation = "/ˌæl.truˈɪs.tɪk/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Showing a disinterested and selfless concern for the well-being of others.",
            examples = listOf(
                VocabularyExample("Everyday", "Anonymous blood donation is a deeply altruistic act."),
                VocabularyExample("Academic", "Biologists study altruistic behaviour in cooperative species.")
            ),
            synonyms = listOf("selfless", "philanthropic", "benevolent", "generous"),
            antonyms = listOf("selfish", "egoistic", "mercenary")
        ),
        CuratedDailyWord(
            ukWord = "Candid",
            ukPronunciation = "/ˈkæn.dɪd/",
            usPronunciation = "/ˈkæn.dɪd/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "Truthful and straightforward; frank without hiding uncomfortable truths.",
            examples = listOf(
                VocabularyExample("Workplace", "We had a candid conversation about realistic timelines."),
                VocabularyExample("Everyday", "A candid photograph often captures more warmth than a posed portrait.")
            ),
            synonyms = listOf("frank", "forthright", "outspoken", "honest"),
            antonyms = listOf("guarded", "evasive", "secretive")
        ),
        CuratedDailyWord(
            ukWord = "Favourable",
            usWord = "Favorable",
            ukPronunciation = "/ˈfeɪ.vər.ə.bəl/",
            usPronunciation = "/ˈfeɪ.vɚ.ə.bəl/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "Expressing approval, support, or advantageous conditions for success.",
            examples = listOf(
                VocabularyExample("News", "Favourable winds helped the sailing team set a coastal record."),
                VocabularyExample("Workplace", "Early client feedback on the prototype has been overwhelmingly favourable.")
            ),
            synonyms = listOf("encouraging", "positive", "propitious", "advantageous"),
            antonyms = listOf("unfavourable", "adverse", "hostile")
        ),
        CuratedDailyWord(
            ukWord = "Humour",
            usWord = "Humor",
            ukPronunciation = "/ˈhjuː.mə(r)/",
            usPronunciation = "/ˈhjuː.mɚ/",
            partOfSpeech = "Noun",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "The quality of being amusing or comic, or the faculty of expressing wit gently.",
            examples = listOf(
                VocabularyExample("Everyday", "Her dry humour kept spirits high during the marathon revision session."),
                VocabularyExample("Literary", "Satire blends sharp social critique with ironic humour.")
            ),
            synonyms = listOf("wit", "levity", "comedy", "drollery"),
            antonyms = listOf("solemnity", "gravity")
        ),
        CuratedDailyWord(
            ukWord = "Catalogue",
            usWord = "Catalog",
            ukPronunciation = "/ˈkæt.əl.ɒɡ/",
            usPronunciation = "/ˈkæt̬.əl.ɑːɡ/",
            partOfSpeech = "Noun / Verb",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "A complete, systematic list of items; to record or classify items in an orderly index.",
            examples = listOf(
                VocabularyExample("Academic", "Archivists spent two years compiling a digital catalogue of manuscripts."),
                VocabularyExample("Workplace", "The report catalogues dozens of subtle accessibility gaps.")
            ),
            synonyms = listOf("directory", "inventory", "register", "index"),
            antonyms = listOf("jumble", "disorder")
        ),
        CuratedDailyWord(
            ukWord = "Scepticism",
            usWord = "Skepticism",
            ukPronunciation = "/ˈskep.tɪ.sɪ.zəm/",
            usPronunciation = "/ˈskep.tə.sɪ.zəm/",
            partOfSpeech = "Noun",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "A questioning attitude of doubt toward claims lacking verifiable evidence.",
            examples = listOf(
                VocabularyExample("Academic", "Healthy scepticism is the starting point of rigorous scientific enquiry."),
                VocabularyExample("Editorial", "Voters greeted the sudden pledge with understandable scepticism.")
            ),
            synonyms = listOf("doubt", "disbelief", "incredulity", "cynicism"),
            antonyms = listOf("credulity", "conviction", "trust")
        ),
        CuratedDailyWord(
            ukWord = "Emphasise",
            usWord = "Emphasize",
            ukPronunciation = "/ˈem.fə.saɪz/",
            usPronunciation = "/ˈem.fə.saɪz/",
            partOfSpeech = "Verb",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "To give special importance, stress, or prominence to something in speaking or writing.",
            examples = listOf(
                VocabularyExample("Academic", "Examiners emphasise clarity of reasoning over memorised slogans."),
                VocabularyExample("Workplace", "Let us emphasise measurable outcomes in tomorrow's briefing.")
            ),
            synonyms = listOf("highlight", "underscore", "accentuate", "stress"),
            antonyms = listOf("downplay", "minimise", "understate")
        ),
        CuratedDailyWord(
            ukWord = "Acumen",
            ukPronunciation = "/ˈæk.jʊ.mən/",
            usPronunciation = "/əˈkjuː.mən/",
            partOfSpeech = "Noun",
            levelCode = VocabularyLevel.MASTERY.code,
            meaning = "The ability to make good judgements and swift, insightful decisions in a particular domain.",
            examples = listOf(
                VocabularyExample("Workplace", "Her financial acumen saved the cooperative from overexpansion."),
                VocabularyExample("Editorial", "Diplomatic acumen requires listening closely to unspoken priorities.")
            ),
            synonyms = listOf("shrewdness", "astuteness", "insight", "discernment"),
            antonyms = listOf("naivety", "obtuseness")
        ),
        CuratedDailyWord(
            ukWord = "Benevolent",
            ukPronunciation = "/bəˈnev.əl.ənt/",
            usPronunciation = "/bəˈnev.əl.ənt/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Well-meaning, kindly, and motivated by a genuine wish to help others.",
            examples = listOf(
                VocabularyExample("Everyday", "A benevolent neighbour watered our courtyard plants all summer."),
                VocabularyExample("Literary", "The novel portrays a stern but fundamentally benevolent mentor.")
            ),
            synonyms = listOf("charitable", "compassionate", "generous", "benign"),
            antonyms = listOf("malevolent", "cruel", "spiteful")
        ),
        CuratedDailyWord(
            ukWord = "Circumspect",
            ukPronunciation = "/ˈsɜː.kəm.spekt/",
            usPronunciation = "/ˈsɝː.kəm.spekt/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.MASTERY.code,
            meaning = "Wary and unwilling to take unnecessary risks; carefully considering all consequences.",
            examples = listOf(
                VocabularyExample("Formal", "Central bankers were circumspect about signalling immediate rate cuts."),
                VocabularyExample("Workplace", "Be circumspect when sharing unverified timeline estimates.")
            ),
            synonyms = listOf("cautious", "prudent", "guarded", "vigilant"),
            antonyms = listOf("reckless", "impulsive", "indiscreet")
        ),
        CuratedDailyWord(
            ukWord = "Deference",
            ukPronunciation = "/ˈdef.ər.əns/",
            usPronunciation = "/ˈdef.ɚ.əns/",
            partOfSpeech = "Noun",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Polite submission and respectful regard for another person's judgement or seniority.",
            examples = listOf(
                VocabularyExample("Formal", "In deference to local elders, the ceremony opened with a traditional welcome."),
                VocabularyExample("Workplace", "Healthy debate requires respect without blind deference.")
            ),
            synonyms = listOf("respect", "regard", "courtesy", "esteem"),
            antonyms = listOf("defiance", "disrespect", "arrogance")
        ),
        CuratedDailyWord(
            ukWord = "Exacerbate",
            ukPronunciation = "/ɪɡˈzæs.ə.beɪt/",
            usPronunciation = "/ɪɡˈzæs.ɚ.beɪt/",
            partOfSpeech = "Verb",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "To make a problem, bad situation, or negative feeling noticeably worse.",
            examples = listOf(
                VocabularyExample("News", "Sudden frosts could exacerbate seasonal fruit shortages."),
                VocabularyExample("Academic", "Defensive tone often exacerbates misunderstandings in negotiation.")
            ),
            synonyms = listOf("worsen", "aggravate", "intensify", "inflame"),
            antonyms = listOf("ameliorate", "alleviate", "soothe")
        ),
        CuratedDailyWord(
            ukWord = "Frugal",
            ukPronunciation = "/ˈfruː.ɡəl/",
            usPronunciation = "/ˈfruː.ɡəl/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "Sparing or economical with money, food, or resources; avoiding waste.",
            examples = listOf(
                VocabularyExample("Everyday", "Frugal habits in university helped her graduate free of debt."),
                VocabularyExample("Editorial", "Frugal engineering achieves high reliability at modest cost.")
            ),
            synonyms = listOf("thrifty", "economical", "sparing", "prudent"),
            antonyms = listOf("extravagant", "lavish", "wasteful")
        ),
        CuratedDailyWord(
            ukWord = "Gregarious",
            ukPronunciation = "/ɡrɪˈɡeə.ri.əs/",
            usPronunciation = "/ɡrɪˈɡer.i.əs/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Fond of company; sociable and energised by being around other people.",
            examples = listOf(
                VocabularyExample("Everyday", "Our gregarious host introduced every guest within five minutes."),
                VocabularyExample("Academic", "Dolphins are highly gregarious marine mammals.")
            ),
            synonyms = listOf("sociable", "convivial", "outgoing", "affable"),
            antonyms = listOf("reclusive", "reserved", "aloof")
        ),
        CuratedDailyWord(
            ukWord = "Harbinger",
            ukPronunciation = "/ˈhɑː.bɪn.dʒə(r)/",
            usPronunciation = "/ˈhɑːr.bɪn.dʒɚ/",
            partOfSpeech = "Noun",
            levelCode = VocabularyLevel.MASTERY.code,
            meaning = "A person or thing that announces or signals the imminent arrival of another.",
            examples = listOf(
                VocabularyExample("Literary", "Swallows returning north are seen as a harbinger of spring."),
                VocabularyExample("Editorial", "Rising freight costs were a harbinger of wider inflation.")
            ),
            synonyms = listOf("herald", "forerunner", "precursor", "omen"),
            antonyms = listOf("aftermath", "sequel")
        ),
        CuratedDailyWord(
            ukWord = "Impartial",
            ukPronunciation = "/ɪmˈpɑː.ʃəl/",
            usPronunciation = "/ɪmˈpɑːr.ʃəl/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "Treating all rivals, viewpoints, or disputants equally without favouritism or bias.",
            examples = listOf(
                VocabularyExample("Formal", "An impartial mediator listened to both sides before proposing terms."),
                VocabularyExample("Academic", "Double-blind trials preserve impartial evaluation of outcomes.")
            ),
            synonyms = listOf("unbiased", "neutral", "objective", "fair-minded"),
            antonyms = listOf("biased", "partisan", "prejudiced")
        ),
        CuratedDailyWord(
            ukWord = "Juxtapose",
            ukPronunciation = "/ˌdʒʌk.stəˈpəʊz/",
            usPronunciation = "/ˌdʒʌk.stəˈpoʊz/",
            partOfSpeech = "Verb",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "To place two contrasting things close together for comparison or dramatic effect.",
            examples = listOf(
                VocabularyExample("Culture", "The exhibition juxtaposes medieval woodcuts with neon sculpture."),
                VocabularyExample("Academic", "The essay juxtaposes urban density figures with transit ridership.")
            ),
            synonyms = listOf("contrast", "compare", "set side by side"),
            antonyms = listOf("separate", "isolate")
        ),
        CuratedDailyWord(
            ukWord = "Palliative",
            ukPronunciation = "/ˈpæl.i.ə.tɪv/",
            usPronunciation = "/ˈpæl.i.eɪ.t̬ɪv/",
            partOfSpeech = "Adjective / Noun",
            levelCode = VocabularyLevel.MASTERY.code,
            meaning = "Relieving pain or easing symptoms without curing the underlying cause.",
            examples = listOf(
                VocabularyExample("News", "Short-term fee waivers offered palliative relief rather than structural reform."),
                VocabularyExample("Academic", "Community clinics expanded palliative care access for elderly patients.")
            ),
            synonyms = listOf("soothing", "alleviating", "remedial", "sedative"),
            antonyms = listOf("aggravating", "curative")
        ),
        CuratedDailyWord(
            ukWord = "Reconcile",
            ukPronunciation = "/ˈrek.ən.saɪl/",
            usPronunciation = "/ˈrek.ən.saɪl/",
            partOfSpeech = "Verb",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "To restore friendly relations, or to make two apparently conflicting facts compatible.",
            examples = listOf(
                VocabularyExample("Workplace", "Finance analysts must reconcile bank statements before month-end close."),
                VocabularyExample("Everyday", "It took candour and patience to reconcile their differences.")
            ),
            synonyms = listOf("harmonise", "resolve", "mediate", "square"),
            antonyms = listOf("estrange", "alienate", "divide")
        ),
        CuratedDailyWord(
            ukWord = "Stoic",
            ukPronunciation = "/ˈstəʊ.ɪk/",
            usPronunciation = "/ˈstoʊ.ɪk/",
            partOfSpeech = "Adjective / Noun",
            levelCode = VocabularyLevel.ADVANCED.code,
            meaning = "Enduring pain, hardship, or setback calmly without displaying distress or complaint.",
            examples = listOf(
                VocabularyExample("Literary", "She bore the long winter delay with stoic grace."),
                VocabularyExample("Everyday", "My grandfather remained stoic even during difficult hospital stays.")
            ),
            synonyms = listOf("impassive", "uncomplaining", "forbearing", "resolute"),
            antonyms = listOf("emotional", "fretful", "volatile")
        ),
        CuratedDailyWord(
            ukWord = "Tactful",
            ukPronunciation = "/ˈtækt.fəl/",
            usPronunciation = "/ˈtækt.fəl/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "Having or showing skill and sensitivity when handling difficult or delicate personal issues.",
            examples = listOf(
                VocabularyExample("Workplace", "A tactful edit turned harsh criticism into constructive guidance."),
                VocabularyExample("Everyday", "He gave a tactful reply that spared everyone embarrassment.")
            ),
            synonyms = listOf("diplomatic", "considerate", "discreet", "sensitive"),
            antonyms = listOf("tactless", "blunt", "insensitive")
        ),
        CuratedDailyWord(
            ukWord = "Unprecedented",
            ukPronunciation = "/ʌnˈpres.ɪ.den.tɪd/",
            usPronunciation = "/ʌnˈpres.ə.den.t̬ɪd/",
            partOfSpeech = "Adjective",
            levelCode = VocabularyLevel.UPPER_INTERMEDIATE.code,
            meaning = "Never done, experienced, or known before.",
            examples = listOf(
                VocabularyExample("News", "Solar capacity expanded at an unprecedented pace this decade."),
                VocabularyExample("Academic", "Archivists documented unprecedented levels of manuscript preservation.")
            ),
            synonyms = listOf("unparalleled", "novel", "unheard-of", "extraordinary"),
            antonyms = listOf("familiar", "routine", "traditional")
        )
    )

    val offlineDictionarySize: Int
        get() = CURATED_OFFLINE_DICTIONARY.size

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    fun todayDateKey(nowMillis: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))
    }

    fun formattedDateLabel(nowMillis: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).format(Date(nowMillis))
    }

    fun candidateWordFor(
        nowMillis: Long = System.currentTimeMillis(),
        variant: String = VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT,
        offset: Int = 0
    ): String {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val daySeed = cal.get(Calendar.YEAR) * 366 + cal.get(Calendar.DAY_OF_YEAR)
        val idx = abs(daySeed + offset) % CURATED_OFFLINE_DICTIONARY.size
        val item = CURATED_OFFLINE_DICTIONARY[idx]
        val word = if (variant.equals("US", ignoreCase = true)) item.usWord else item.ukWord
        return word.lowercase(Locale.ROOT)
    }

    fun curatedOfflineEntryFor(
        nowMillis: Long = System.currentTimeMillis(),
        variant: String = VocabularyLanguageVariants.DEFAULT_ENGLISH_VARIANT,
        offset: Int = 0
    ): VocabularyEntry {
        val normVariant = VocabularyLanguageVariants.normalizeVariant("English", variant)
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val daySeed = cal.get(Calendar.YEAR) * 366 + cal.get(Calendar.DAY_OF_YEAR)
        val idx = abs(daySeed + offset) % CURATED_OFFLINE_DICTIONARY.size
        return CURATED_OFFLINE_DICTIONARY[idx].toVocabularyEntry(normVariant)
    }

    private fun CuratedDailyWord.toVocabularyEntry(variant: String): VocabularyEntry {
        val isUs = variant.equals("US", ignoreCase = true)
        val resolvedWord = if (isUs) usWord else ukWord
        val resolvedPron = if (isUs) usPronunciation else ukPronunciation
        val note = usageNote.ifBlank {
            "Curated Oxford/standard lexicon (${VocabularyLanguageVariants.displayLabel(variant)})."
        }
        return VocabularyEntry(
            word = resolvedWord,
            pronunciation = resolvedPron,
            partOfSpeech = partOfSpeech,
            levelCode = levelCode,
            language = "English",
            languageVariant = variant,
            meaning = meaning,
            examples = examples,
            synonyms = synonyms,
            antonyms = antonyms,
            usageNote = note
        )
    }

    suspend fun loadDailyWord(
        prefs: ExamSetPreferences,
        decks: List<VocabularyDeck>,
        variant: String,
        offset: Int = 0,
        forceRefresh: Boolean = false,
        allowNetworkLookup: Boolean = true
    ): WordOfTheDayUiState {
        val normVariant = VocabularyLanguageVariants.normalizeVariant("English", variant)
        val dateKey = todayDateKey()
        val dateLabel = formattedDateLabel()

        // 1. Return today's cached live dictionary entry if matching date + variant + offset
        if (!forceRefresh) {
            readCachedLiveEntry(prefs, dateKey, normVariant, offset)?.let { cachedEntry ->
                val host = findExistingDeckHostingWord(cachedEntry.word, decks)
                return WordOfTheDayUiState.Ready(
                    entry = cachedEntry,
                    dateLabel = dateLabel,
                    variant = normVariant,
                    source = WordOfTheDaySource.LIVE_DICTIONARY,
                    existingDeckId = host?.id,
                    existingDeckTitle = host?.title
                )
            }
        }

        // 2. Fetch live entry from public Dictionary API (tries current candidate + US spelling fallback)
        val headword = candidateWordFor(variant = normVariant, offset = offset)
        val fetched = if (allowNetworkLookup) {
            fetchDictionaryEntry(headword, normVariant)
                ?: if (normVariant == "UK") {
                    val usCandidate = candidateWordFor(variant = "US", offset = offset)
                    if (!usCandidate.equals(headword, ignoreCase = true)) {
                        fetchDictionaryEntry(usCandidate, normVariant)
                    } else null
                } else null
        } else null

        if (fetched != null) {
            saveCachedLiveEntry(prefs, dateKey, normVariant, offset, fetched)
            val host = findExistingDeckHostingWord(fetched.word, decks)
            return WordOfTheDayUiState.Ready(
                entry = fetched,
                dateLabel = dateLabel,
                variant = normVariant,
                source = WordOfTheDaySource.LIVE_DICTIONARY,
                existingDeckId = host?.id,
                existingDeckTitle = host?.title
            )
        }

        // 3. Built-in Curated Offline Dictionary fallback (guarantees Word of the Day works offline & at 0 saved sets)
        val curated = curatedOfflineEntryFor(variant = normVariant, offset = offset)
        val curatedHost = findExistingDeckHostingWord(curated.word, decks)
        return WordOfTheDayUiState.Ready(
            entry = curated,
            dateLabel = dateLabel,
            variant = normVariant,
            source = WordOfTheDaySource.CURATED_DICTIONARY,
            existingDeckId = curatedHost?.id,
            existingDeckTitle = curatedHost?.title
        )
    }

    internal fun parseDictionaryApiJson(
        rawJson: String,
        requestedWord: String,
        variant: String
    ): VocabularyEntry? {
        return runCatching {
            val arr = JSONArray(rawJson)
            if (arr.length() == 0) return null
            val root = arr.optJSONObject(0) ?: return null
            val apiWord = root.optString("word", requestedWord).trim().ifBlank { requestedWord }
            val matchedCurated = CURATED_OFFLINE_DICTIONARY.firstOrNull {
                it.ukWord.equals(requestedWord, ignoreCase = true) ||
                    it.usWord.equals(requestedWord, ignoreCase = true)
            }
            val displayWord = when {
                matchedCurated != null && variant.equals("US", ignoreCase = true) -> matchedCurated.usWord
                matchedCurated != null -> matchedCurated.ukWord
                else -> apiWord
            }

            // Pick phonetic matching UK vs US preference when tagged in audio URL
            var phonetic = root.optString("phonetic", "").trim()
            val phoneticsArr = root.optJSONArray("phonetics")
            if (phoneticsArr != null) {
                val targetTag = if (variant.equals("US", ignoreCase = true)) "-us" else "-uk"
                var preferredText = ""
                var firstText = ""
                for (i in 0 until phoneticsArr.length()) {
                    val pObj = phoneticsArr.optJSONObject(i) ?: continue
                    val pText = pObj.optString("text", "").trim()
                    val audio = pObj.optString("audio", "").lowercase(Locale.ROOT)
                    if (pText.isNotBlank() && firstText.isBlank()) firstText = pText
                    if (pText.isNotBlank() && audio.contains(targetTag)) {
                        preferredText = pText
                        break
                    }
                }
                phonetic = preferredText.ifBlank { firstText }.ifBlank { phonetic }
            }

            val meaningsArr = root.optJSONArray("meanings") ?: return null
            var bestPos = ""
            var bestDef = ""
            val examples = mutableListOf<VocabularyExample>()
            val synonyms = mutableListOf<String>()
            val antonyms = mutableListOf<String>()

            for (i in 0 until meaningsArr.length()) {
                val mObj = meaningsArr.optJSONObject(i) ?: continue
                val pos = mObj.optString("partOfSpeech", "").trim()
                val synsArr = mObj.optJSONArray("synonyms")
                if (synsArr != null) {
                    for (s in 0 until synsArr.length()) {
                        synsArr.optString(s, "").trim().takeIf { it.isNotBlank() }?.let { synonyms.add(it) }
                    }
                }
                val antsArr = mObj.optJSONArray("antonyms")
                if (antsArr != null) {
                    for (a in 0 until antsArr.length()) {
                        antsArr.optString(a, "").trim().takeIf { it.isNotBlank() }?.let { antonyms.add(it) }
                    }
                }
                val defsArr = mObj.optJSONArray("definitions") ?: continue
                for (j in 0 until defsArr.length()) {
                    val dObj = defsArr.optJSONObject(j) ?: continue
                    val defText = dObj.optString("definition", "").trim()
                    if (defText.isNotBlank() && bestDef.isBlank()) {
                        bestDef = defText
                        bestPos = pos.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                    }
                    val exSentence = dObj.optString("example", "").trim()
                    if (exSentence.isNotBlank() && examples.size < 2) {
                        examples.add(VocabularyExample(context = pos.ifBlank { "Usage" }.replaceFirstChar { it.titlecase(Locale.ROOT) }, sentence = exSentence))
                    }
                }
            }

            if (bestDef.isBlank()) return null

            VocabularyEntry(
                word = displayWord.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
                pronunciation = phonetic,
                partOfSpeech = bestPos,
                levelCode = matchedCurated?.levelCode ?: VocabularyLevel.ADVANCED.code,
                language = "English",
                languageVariant = variant,
                meaning = bestDef,
                examples = examples,
                synonyms = synonyms.distinct().take(4),
                antonyms = antonyms.distinct().take(3),
                usageNote = "Daily lexicon spotlight (${VocabularyLanguageVariants.displayLabel(variant)})."
            )
        }.getOrNull()
    }

    private suspend fun fetchDictionaryEntry(word: String, variant: String): VocabularyEntry? = withContext(Dispatchers.IO) {
        val clean = word.trim().lowercase(Locale.ROOT)
        if (clean.isBlank()) return@withContext null
        runCatching {
            val req = Request.Builder()
                .url("https://api.dictionaryapi.dev/api/v2/entries/en/$clean")
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@use null
                parseDictionaryApiJson(body, clean, variant)
            }
        }.getOrNull()
    }

    private fun readCachedLiveEntry(
        prefs: ExamSetPreferences,
        dateKey: String,
        variant: String,
        offset: Int
    ): VocabularyEntry? {
        val raw = prefs.getWordOfTheDayCacheJson() ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            if (obj.optString("dateKey") != dateKey) return null
            if (!obj.optString("variant").equals(variant, ignoreCase = true)) return null
            if (obj.optInt("offset", 0) != offset) return null
            VocabularyEntry.fromJson(obj.optJSONObject("entry"))
        }.getOrNull()
    }

    private fun saveCachedLiveEntry(
        prefs: ExamSetPreferences,
        dateKey: String,
        variant: String,
        offset: Int,
        entry: VocabularyEntry
    ) {
        val payload = JSONObject().apply {
            put("dateKey", dateKey)
            put("variant", variant)
            put("offset", offset)
            put("entry", entry.toJson())
        }
        prefs.saveWordOfTheDayCacheJson(payload.toString())
    }

    private fun findExistingDeckHostingWord(word: String, decks: List<VocabularyDeck>): VocabularyDeck? {
        val key = VocabularyEntry.normalizeVocabularyWordKey(word)
        if (key.isBlank()) return null
        return decks.firstOrNull { deck ->
            deck.entries.any { it.normalizedWordKey() == key }
        }
    }
}

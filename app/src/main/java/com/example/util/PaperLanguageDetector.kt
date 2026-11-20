package com.example.util

/**
 * Detects the actual human language(s) present in a question paper's content
 * using explicit header declarations, Unicode script counts, and lightweight
 * stopword/diacritic disambiguation for same-script languages across Latin,
 * Devanagari, Arabic-Perso-Urdu, Bengali-Assamese, and Cyrillic scripts.
 */
object PaperLanguageDetector {

    /**
     * Quick-pick preset languages surfaced in the Prompt Builder and Import/Create sheets.
     * Custom free-text entry also supports any arbitrary language name.
     */
    val PRESET_LANGUAGES: List<String> = listOf(
        "English", "Hindi", "Bengali", "Marathi", "Telugu", "Tamil",
        "Gujarati", "Urdu", "Kannada", "Malayalam", "Odia", "Punjabi",
        "Assamese", "Sanskrit", "Nepali", "Maithili", "Konkani",
        "Spanish", "French", "German", "Portuguese", "Italian",
        "Russian", "Arabic", "Persian", "Japanese", "Korean", "Chinese",
        "Turkish", "Indonesian", "Malay", "Vietnamese", "Thai",
        "Dutch", "Polish", "Swedish", "Greek", "Ukrainian", "Swahili"
    )

    private val EXPLICIT_LANGUAGE_LINE_REGEX = Regex(
        """(?im)^\s*[#*\->\s]*(?:Language|Languages|Medium)\s*[:\-]\s*(.+?)\s*$"""
    )
    private val JSON_LANGUAGE_FIELD_REGEX = Regex(
        """(?i)\"(?:language|languages|medium)\"\s*:\s*(?:\[(.*?)\]|\"(.*?)\")"""
    )
    private val PROMPT_PLACEHOLDER_REGEX = Regex(
        """(?i)(?:\[.*?\]|same\s+language|write\s+(?:the\s+)?question|natural,?\s+academically|specify\s+language)"""
    )
    private val CUSTOM_HEADER_SPLIT_REGEX = Regex("""(?i)\s*(?:,|&|/|\+|\band\b|\bbilingual\b|\bmultilingual\b|\bmedium\b|\bonly\b|\(|\))\s*""")

    private val METADATA_LINE_REGEX = Regex(
        """(?im)^\s*(?:Title|Subject|Topic|Series|Set(?:\s*Number)?|Exam(?:ination)?|Time|Duration|Max\s*Marks|Roll(?:\s*Number)?|Booklet(?:\s*Number)?|Language|Languages|Medium|Answer\s*Key|Detailed\s*Explanations?|Correct\s*Answer|Explanation|Formula)\s*:.*$"""
    )
    private val BOILERPLATE_LABELS_REGEX = Regex(
        """(?i)\b(?:Answer\s*Key|Detailed\s*Explanations?|Correct\s*Answer|Explanation|Option\s*[A-D]|Correct|Incorrect|Formula|True|False)\b"""
    )
    private val JSON_KEYS_REGEX = Regex(
        """\"(?:num|question|options|a|b|c|d|correctAnswer|explanation|title|subject|topic|setNumber|exam|questionType|difficulty|metadata|questions|positiveMarks|negativeMarks|durationMinutes|manualCategory|language|languages)\"\s*:"""
    )
    private val OPTION_MARKER_REGEX = Regex("""\(\s*[a-dA-D]\s*\)|\b[a-dA-D]\s*[).:]""")

    private val DEVANAGARI_WORD_REGEX = Regex("""[\u0900-\u097F]+""")
    private val ARABIC_WORD_REGEX = Regex("""[\u0600-\u06FF\u0750-\u077F\uFB50-\uFDFF\uFE70-\uFEFF]+""")
    private val BENGALI_WORD_REGEX = Regex("""[\u0980-\u09FF]+""")
    private val CYRILLIC_WORD_REGEX = Regex("""[\u0400-\u04FF]+""")
    private val LATIN_WORD_REGEX = Regex("""[\p{IsLatin}]+""")

    private val EXPLICIT_LANGUAGE_ALIASES: List<Pair<Regex, String>> = listOf(
        Regex("""(?i)\benglish\b|\benglisch\b|\banglais\b|\binglés\b|\bingles\b""") to "English",
        Regex("""(?i)\bhindi\b|हिन्दी|हिंदी""") to "Hindi",
        Regex("""(?i)\bmarathi\b|मराठी""") to "Marathi",
        Regex("""(?i)\bsanskrit\b|संस्कृत""") to "Sanskrit",
        Regex("""(?i)\bnepali\b|नेपाली""") to "Nepali",
        Regex("""(?i)\bmaithili\b|मैथिली""") to "Maithili",
        Regex("""(?i)\bkonkani\b|कोंकणी""") to "Konkani",
        Regex("""(?i)\bdogri\b|डोगरी""") to "Dogri",
        Regex("""(?i)\bbodo\b|बड़ो""") to "Bodo",
        Regex("""(?i)\bsantali\b|संताली|ᱥᱟᱱᱛᱟᱲᱤ""") to "Santali",
        Regex("""(?i)\bmanipuri\b|\bmeitei\b|मणिपुरी|ꯃꯤꯇꯩ""") to "Manipuri",
        Regex("""(?i)\bkashmiri\b|कश्मीरी|کٲشُر""") to "Kashmiri",
        Regex("""(?i)\bsindhi\b|सिंधी|سنڌي""") to "Sindhi",
        Regex("""(?i)\bfrench\b|\bfrançais\b|\bfrancais\b""") to "French",
        Regex("""(?i)\bspanish\b|\bespañol\b|\bespanol\b|\bcastellano\b""") to "Spanish",
        Regex("""(?i)\bgerman\b|\bdeutsch\b""") to "German",
        Regex("""(?i)\bportuguese\b|\bportuguês\b|\bportugues\b""") to "Portuguese",
        Regex("""(?i)\bitalian\b|\bitaliano\b""") to "Italian",
        Regex("""(?i)\bdutch\b|\bnederlands\b""") to "Dutch",
        Regex("""(?i)\bpolish\b|\bpolski\b""") to "Polish",
        Regex("""(?i)\bturkish\b|\btürkçe\b|\bturkce\b""") to "Turkish",
        Regex("""(?i)\bindonesian\b|\bbahasa\s+indonesia\b""") to "Indonesian",
        Regex("""(?i)\bmalay\b|\bbahasa\s+melayu\b""") to "Malay",
        Regex("""(?i)\bvietnamese\b|\btiếng\s+việt\b|\btieng\s+viet\b""") to "Vietnamese",
        Regex("""(?i)\bswedish\b|\bsvenska\b""") to "Swedish",
        Regex("""(?i)\bnorwegian\b|\bnorsk\b""") to "Norwegian",
        Regex("""(?i)\bdanish\b|\bdansk\b""") to "Danish",
        Regex("""(?i)\bfinnish\b|\bsuomi\b""") to "Finnish",
        Regex("""(?i)\bromanian\b|\bromână\b|\bromana\b""") to "Romanian",
        Regex("""(?i)\bczech\b|\bčeština\b|\bcestina\b""") to "Czech",
        Regex("""(?i)\bhungarian\b|\bmagyar\b""") to "Hungarian",
        Regex("""(?i)\bswahili\b|\bkiswahili\b""") to "Swahili",
        Regex("""(?i)\bfilipino\b|\btagalog\b""") to "Filipino",
        Regex("""(?i)\bkorean\b|한국어""") to "Korean",
        Regex("""(?i)\bjapanese\b|日本語""") to "Japanese",
        Regex("""(?i)\bchinese\b|\bmandarin\b|\bcantonese\b|中文|漢語|汉语""") to "Chinese",
        Regex("""(?i)\burdu\b|اردو""") to "Urdu",
        Regex("""(?i)\barabic\b|العربية""") to "Arabic",
        Regex("""(?i)\bpersian\b|\bfarsi\b|فارسی""") to "Persian",
        Regex("""(?i)\bbengali\b|\bbangla\b|বাংলা""") to "Bengali",
        Regex("""(?i)\bassamese\b|অসমীয়া""") to "Assamese",
        Regex("""(?i)\btamil\b|தமிழ்""") to "Tamil",
        Regex("""(?i)\btelugu\b|తెలుగు""") to "Telugu",
        Regex("""(?i)\bkannada\b|ಕನ್ನಡ""") to "Kannada",
        Regex("""(?i)\bmalayalam\b|മലയാളം""") to "Malayalam",
        Regex("""(?i)\bgujarati\b|ગુજરાતી""") to "Gujarati",
        Regex("""(?i)\bpunjabi\b|\bgurmukhi\b|ਪੰਜਾਬੀ""") to "Punjabi",
        Regex("""(?i)\bodia\b|\boriya\b|ଓଡ଼ିଆ""") to "Odia",
        Regex("""(?i)\brussian\b|русский""") to "Russian",
        Regex("""(?i)\bukrainian\b|українська""") to "Ukrainian",
        Regex("""(?i)\bbulgarian\b|български""") to "Bulgarian",
        Regex("""(?i)\bserbian\b|српски""") to "Serbian",
        Regex("""(?i)\bgreek\b|ελληνικά""") to "Greek",
        Regex("""(?i)\bhebrew\b|עברית""") to "Hebrew",
        Regex("""(?i)\bthai\b|ไทย""") to "Thai",
        Regex("""(?i)\bburmese\b|\bmyanmar\b|မြန်မာ""") to "Burmese",
        Regex("""(?i)\bkhmer\b|ខ្មែរ""") to "Khmer",
        Regex("""(?i)\blao\b|ລາວ""") to "Lao",
        Regex("""(?i)\bsinhala\b|\bsinhalese\b|සිංහල""") to "Sinhala",
        Regex("""(?i)\btibetan\b|བོད་སྐད""") to "Tibetan",
        Regex("""(?i)\bgeorgian\b|ქართული""") to "Georgian",
        Regex("""(?i)\barmenian\b|հայերեն""") to "Armenian",
        Regex("""(?i)\bamharic\b|አማርኛ""") to "Amharic"
    )

    // --- Devanagari stopwords ---
    private val HINDI_STOPWORDS = setOf(
        "है", "हैं", "था", "थी", "थे", "नहीं", "में", "के", "की", "का", "को", "से", "पर",
        "और", "निम्नलिखित", "कौन", "किस", "क्या", "गया", "गई", "गए", "होता", "होती",
        "होते", "जाता", "जाती", "सकता", "सकती", "द्वारा", "लिए", "किया", "होगा", "होगी",
        "इसका", "इसके", "इसकी", "उसका", "उसके", "उसकी", "कहा", "जाते"
    )
    private val MARATHI_STOPWORDS = setOf(
        "आहे", "आहेत", "नाही", "नाहीत", "होते", "होता", "होती", "असते", "असतात", "मध्ये",
        "खालीलपैकी", "वरीलपैकी", "कोणते", "कोणती", "कोणता", "कोणत्या", "म्हणजे", "त्यामुळे",
        "करण्यात", "येते", "येतात", "आणि", "किंवा", "परंतु", "मग", "यांची", "याचे", "याच्या",
        "त्याचे", "त्याची", "त्याच्या", "चे", "च्या", "ची", "ला", "पासून", "पर्यंत"
    )
    private val SANSKRIT_STOPWORDS = setOf(
        "अस्ति", "सन्ति", "भवति", "भवन्ति", "अत्र", "तत्र", "यत्र", "कुत्र", "किम्", "किं",
        "कस्य", "कः", "का", "इति", "एव", "अपि", "ननु", "यथा", "तथा", "उक्तम्", "प्रश्नः",
        "अधोलिखितेषु", "समीचीनम्", "असत्यम्", "सत्यम्", "कथम्", "कदा", "च", "तु", "तस्मात्",
        "यस्मात्", "अस्य", "तस्य", "एतत्", "तत्", "नास्ति", "वर्तते"
    )
    private val NEPALI_STOPWORDS = setOf(
        "छ", "छन्", "छैन", "छैनन्", "होइन", "थियो", "थिए", "निम्नलिखितमध्ये", "कुन",
        "बाट", "र", "तर", "गर्न", "गर्ने", "भएको", "हुने", "भनिन्छ", "सकिन्छ", "लागि",
        "मा", "गर्दा", "गरेको", "भए"
    )
    private val MAITHILI_STOPWORDS = setOf(
        "अछि", "छथि", "नहि", "ककर", "केकर", "कियाक", "निम्नलिखितमे", "ई", "ओ", "सँ",
        "मे", "एहि", "ओहि", "ताहि", "कहल", "जाइत", "होइत", "अपन"
    )
    private val KONKANI_STOPWORDS = setOf(
        "आसा", "आसात", "खंयचें", "खंयची", "खंयचो", "खंयच्या", "ताका", "ताचे", "ताची",
        "पूण", "आनी", "सकयल", "दिल्ल्या", "हें", "तें", "मजगतीं", "पयलीं"
    )

    // --- Arabic-script stopwords ---
    private val ARABIC_STOPWORDS = setOf(
        "ما", "من", "في", "على", "إلى", "هو", "هي", "التي", "الذي", "الذين", "أي",
        "التالي", "التالية", "الإجابة", "الصحيحة", "صحيح", "خطأ", "تعتبر", "ليس", "بين",
        "عن", "مع", "هذا", "هذه", "ذلك", "تلك", "الشرح"
    )
    private val URDU_STOPWORDS = setOf(
        "ہے", "ہیں", "تھا", "تھی", "تھے", "نہیں", "کا", "کی", "کے", "میں", "سے", "پر",
        "اور", "کون", "سا", "سی", "کیا", "درج", "ذیل", "مندرجہ", "جواب", "درست", "غلط",
        "وضاحت", "اس", "ان", "کہ", "کو", "ہوتا", "ہوتی", "جاتا"
    )
    private val PERSIAN_STOPWORDS = setOf(
        "است", "هست", "نیست", "در", "از", "که", "به", "این", "آن", "برای", "با",
        "کدام", "کدامیک", "گزینه", "پاسخ", "صحیح", "نادرست", "توضیح", "شود", "می",
        "شود", "های", "را", "یا", "اما", "بین", "زیر"
    )

    // --- Bengali-Assamese stopwords ---
    private val BENGALI_STOPWORDS = setOf(
        "হয়", "নয়", "নিচের", "নিম্নলিখিত", "কোনটি", "কোন", "এবং", "থেকে", "করা",
        "হয়েছে", "সঠিক", "ভুল", "ব্যাখ্যা", "উত্তর", "মধ্যে", "জন্য", "এই", "যে", "একটি"
    )
    private val ASSAMESE_STOPWORDS = setOf(
        "হয়", "নহয়", "তলৰ", "কোনটো", "কোন", "আৰু", "পৰা", "কৰা", "হৈছে", "শুদ্ধ",
        "অশুদ্ধ", "উত্তৰ", "ব্যাখ্যা", "মাজত", "বাবে", "এই", "যি", "এটা"
    )

    // --- Cyrillic stopwords ---
    private val RUSSIAN_STOPWORDS = setOf(
        "какой", "какая", "какое", "какие", "из", "следующих", "ответ", "правильный",
        "верно", "неверно", "является", "что", "как", "для", "при", "или", "это",
        "пояснение", "не", "на", "по", "от", "между"
    )
    private val UKRAINIAN_STOPWORDS = setOf(
        "який", "яка", "яке", "які", "що", "це", "відповідь", "наступних", "правильна",
        "правильно", "пояснення", "та", "для", "або", "чи", "між", "від", "про", "при"
    )
    private val BULGARIAN_STOPWORDS = setOf(
        "кой", "коя", "кое", "кои", "отговор", "следните", "вярно", "грешно", "обяснение",
        "е", "са", "или", "за", "при", "от", "между", "който", "което"
    )
    private val SERBIAN_STOPWORDS = setOf(
        "који", "која", "које", "одговор", "следећих", "тачно", "нетачно", "објашњење",
        "је", "су", "или", "али", "за", "од", "између", "овај", "ово"
    )

    // --- Latin stopwords ---
    private val ENGLISH_STOPWORDS = setOf(
        "the", "is", "are", "was", "were", "which", "what", "who", "whom", "whose",
        "following", "statements", "consider", "correct", "only", "both", "neither",
        "between", "among", "with", "from", "that", "this", "these", "those", "under",
        "above", "below", "into", "about", "during", "because", "therefore", "true",
        "false", "not", "and", "for", "of", "to", "in", "by", "on", "at", "an", "or"
    )
    private val FRENCH_STOPWORDS = setOf(
        "le", "la", "les", "des", "une", "est", "sont", "dans", "pour", "avec", "sur",
        "par", "qui", "que", "quel", "quelle", "quels", "quelles", "parmi", "suivants",
        "suivantes", "réponse", "selon", "entre", "pas", "aux", "du", "cette", "ces",
        "estce", "qu", "l", "d", "ont", "été", "être", "avoir", "plus", "moins", "ou"
    )
    private val SPANISH_STOPWORDS = setOf(
        "el", "los", "las", "una", "uno", "del", "al", "con", "por", "para", "como",
        "qué", "cuál", "cuáles", "según", "entre", "sobre", "siguiente", "siguientes",
        "respuesta", "es", "son", "fue", "está", "están", "pero", "más", "también",
        "sin", "sus", "cual", "cuanto", "cuánto", "puede", "pueden"
    )
    private val GERMAN_STOPWORDS = setOf(
        "der", "die", "das", "den", "dem", "des", "ein", "eine", "einer", "eines",
        "und", "ist", "sind", "war", "mit", "für", "auf", "aus", "bei", "von", "zu",
        "zum", "zur", "nach", "über", "unter", "welche", "welcher", "welches", "welchen",
        "folgenden", "nicht", "oder", "als", "wie", "durch", "wird", "werden", "hat"
    )
    private val PORTUGUESE_STOPWORDS = setOf(
        "não", "os", "as", "uma", "uns", "umas", "dos", "das", "nos", "nas", "com",
        "por", "para", "como", "qual", "quais", "seguinte", "seguintes", "resposta",
        "entre", "sobre", "que", "é", "são", "foi", "está", "mas", "também", "mais",
        "em", "ao", "pela", "pelo", "ser", "tem"
    )
    private val ITALIAN_STOPWORDS = setOf(
        "il", "lo", "la", "i", "gli", "le", "uno", "una", "del", "della", "dello",
        "dei", "degli", "delle", "che", "chi", "quale", "quali", "con", "per", "tra",
        "fra", "nel", "nella", "sul", "sulla", "sono", "è", "non", "più", "come",
        "seguenti", "risposta", "può", "essere"
    )
    private val TURKISH_STOPWORDS = setOf(
        "hangisi", "hangisidir", "aşağıdakilerden", "değildir", "ve", "ile", "için",
        "olarak", "göre", "olan", "bir", "bu", "şu", "doğru", "yanlış", "açıklama",
        "cevap", "arasında", "kadar", "daha", "en", "ancak", "veya"
    )
    private val INDONESIAN_STOPWORDS = setOf(
        "yang", "berikut", "manakah", "adalah", "tidak", "dan", "dari", "untuk", "pada",
        "dengan", "ini", "tersebut", "benar", "salah", "pembahasan", "jawaban",
        "antara", "oleh", "sebagai", "atau", "dalam"
    )
    private val MALAY_STOPWORDS = setOf(
        "manakah", "antara", "berikut", "ialah", "adalah", "tidak", "bukan", "dan",
        "yang", "untuk", "daripada", "kepada", "betul", "salah", "jawapan", "huraian",
        "dalam", "pada", "atau", "ini", "tersebut"
    )
    private val VIETNAMESE_STOPWORDS = setOf(
        "câu", "nào", "sau", "đây", "là", "của", "không", "trong", "cho", "với",
        "đúng", "sai", "giải", "thích", "đáp", "án", "những", "các", "và", "hoặc",
        "từ", "về", "khi"
    )
    private val DUTCH_STOPWORDS = setOf(
        "welke", "van", "de", "het", "een", "zijn", "met", "voor", "uit", "bij",
        "niet", "juist", "onjuist", "volgende", "uitspraak", "uitleg", "antwoord",
        "tussen", "onder", "door", "maar", "of", "als", "wat", "wie"
    )
    private val POLISH_STOPWORDS = setOf(
        "który", "która", "które", "jest", "nie", "wśród", "poniższych", "odpowiedź",
        "prawda", "fałsz", "wyjaśnienie", "dla", "oraz", "przez", "lub", "jako",
        "według", "między", "tego", "tym", "jak"
    )
    private val SWEDISH_STOPWORDS = setOf(
        "vilken", "vilket", "vilka", "följande", "är", "och", "att", "det", "som",
        "för", "med", "till", "inte", "rätt", "fel", "förklaring", "svar", "av",
        "mellan", "eller", "vad"
    )
    private val NORWEGIAN_STOPWORDS = setOf(
        "hvilken", "hvilket", "hvilke", "følgende", "er", "og", "som", "for", "med",
        "til", "ikke", "riktig", "feil", "forklaring", "svar", "av", "mellom", "eller", "hva"
    )
    private val DANISH_STOPWORDS = setOf(
        "hvilken", "hvilket", "hvad", "følgende", "er", "og", "som", "for", "med",
        "til", "ikke", "rigtig", "forkert", "forklaring", "svar", "af", "mellem", "eller"
    )
    private val FINNISH_STOPWORDS = setOf(
        "mikä", "mitkä", "seuraavista", "on", "ovat", "ja", "ei", "ole", "oikein",
        "väärin", "selitys", "vastaus", "kuuluu", "mukaan", "tai", "välillä", "sekä"
    )
    private val ROMANIAN_STOPWORDS = setOf(
        "care", "dintre", "următoarele", "este", "sunt", "pentru", "sau", "nu", "corect",
        "răspuns", "explicație", "între", "cu", "din", "prin", "ce", "după", "și"
    )
    private val CZECH_STOPWORDS = setOf(
        "který", "která", "které", "následujících", "je", "jsou", "není", "pro",
        "odpověď", "správně", "vysvětlení", "nebo", "mezi", "podle", "co", "jak", "při"
    )
    private val HUNGARIAN_STOPWORDS = setOf(
        "melyik", "alábbiak", "közül", "igaz", "hamis", "helyes", "válasz",
        "magyarázat", "nem", "és", "egy", "az", "vagy", "szerint", "között", "mi"
    )
    private val SWAHILI_STOPWORDS = setOf(
        "kipi", "yapi", "ifuatayo", "yafuatayo", "ni", "kwa", "katika", "na", "la",
        "ya", "wa", "cha", "sahihi", "jibu", "maelezo", "au", "kati", "ambayo"
    )

    private const val MAX_DETECTION_SAMPLE_HEAD = 3200
    private const val MAX_DETECTION_SAMPLE_MID = 1400
    private const val MAX_CACHE_ENTRIES = 256

    private val paperLanguageCache = object : LinkedHashMap<String, List<String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean {
            return size > MAX_CACHE_ENTRIES
        }
    }

    /**
     * Thread-safe cached language detection keyed by paper ID, updatedAt, title, and rawText length.
     * Avoids rescanning unchanged papers across libraryIndex recomputations and Compose recompositions.
     */
    fun detectLanguagesCached(
        paperId: Long,
        updatedAt: Long,
        rawText: String,
        title: String = ""
    ): List<String> {
        val key = if (paperId > 0L) {
            "$paperId|$updatedAt|${rawText.length}|${title.hashCode()}"
        } else {
            "0|${rawText.length}|${rawText.take(256).hashCode()}|${title.hashCode()}"
        }
        synchronized(paperLanguageCache) {
            paperLanguageCache[key]?.let { return it }
        }
        val detected = detectLanguages(rawText, title)
        synchronized(paperLanguageCache) {
            paperLanguageCache[key] = detected
        }
        return detected
    }

    fun clearCache() {
        synchronized(paperLanguageCache) {
            paperLanguageCache.clear()
        }
    }

    private fun extractRepresentativeSample(rawText: String): String {
        if (rawText.length <= MAX_DETECTION_SAMPLE_HEAD + MAX_DETECTION_SAMPLE_MID) {
            return rawText
        }
        val head = rawText.substring(0, MAX_DETECTION_SAMPLE_HEAD)
        val midStart = ((rawText.length - MAX_DETECTION_SAMPLE_MID) / 2).coerceAtLeast(MAX_DETECTION_SAMPLE_HEAD)
        val midEnd = (midStart + MAX_DETECTION_SAMPLE_MID).coerceAtMost(rawText.length)
        return buildString(MAX_DETECTION_SAMPLE_HEAD + MAX_DETECTION_SAMPLE_MID + 2) {
            append(head)
            append('\n')
            append(rawText, midStart, midEnd)
        }
    }

    fun detectLanguages(rawText: String, title: String = ""): List<String> {
        val explicit = detectExplicitMetadataLanguages(rawText)
        if (explicit.isNotEmpty()) {
            return explicit
        }

        val sampledRaw = extractRepresentativeSample(rawText)
        val source = buildString(title.length + sampledRaw.length + 1) {
            if (title.isNotBlank()) append(title).append('\n')
            append(sampledRaw)
        }
        if (source.isBlank()) return listOf("English")

        val cleaned = source
            .replace(METADATA_LINE_REGEX, " ")
            .replace(JSON_KEYS_REGEX, " ")
            .replace(BOILERPLATE_LABELS_REGEX, " ")
            .replace(OPTION_MARKER_REGEX, " ")

        var latinLetters = 0
        var devanagariLetters = 0
        var hangulLetters = 0
        var kanaLetters = 0
        var hanLetters = 0
        var arabicLetters = 0
        var bengaliLetters = 0
        var tamilLetters = 0
        var teluguLetters = 0
        var kannadaLetters = 0
        var malayalamLetters = 0
        var gujaratiLetters = 0
        var gurmukhiLetters = 0
        var odiaLetters = 0
        var cyrillicLetters = 0
        var greekLetters = 0
        var hebrewLetters = 0
        var thaiLetters = 0
        var burmeseLetters = 0
        var khmerLetters = 0
        var laoLetters = 0
        var sinhalaLetters = 0
        var tibetanLetters = 0
        var georgianLetters = 0
        var armenianLetters = 0
        var ethiopicLetters = 0
        var santaliLetters = 0
        var manipuriLetters = 0

        var index = 0
        while (index < cleaned.length) {
            val cp = cleaned.codePointAt(index)
            index += Character.charCount(cp)
            if (!Character.isLetter(cp)) continue

            when (Character.UnicodeScript.of(cp)) {
                Character.UnicodeScript.LATIN -> latinLetters++
                Character.UnicodeScript.DEVANAGARI -> devanagariLetters++
                Character.UnicodeScript.HANGUL -> hangulLetters++
                Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA -> kanaLetters++
                Character.UnicodeScript.HAN -> hanLetters++
                Character.UnicodeScript.ARABIC -> arabicLetters++
                Character.UnicodeScript.BENGALI -> bengaliLetters++
                Character.UnicodeScript.TAMIL -> tamilLetters++
                Character.UnicodeScript.TELUGU -> teluguLetters++
                Character.UnicodeScript.KANNADA -> kannadaLetters++
                Character.UnicodeScript.MALAYALAM -> malayalamLetters++
                Character.UnicodeScript.GUJARATI -> gujaratiLetters++
                Character.UnicodeScript.GURMUKHI -> gurmukhiLetters++
                Character.UnicodeScript.ORIYA -> odiaLetters++
                Character.UnicodeScript.CYRILLIC -> cyrillicLetters++
                Character.UnicodeScript.GREEK -> greekLetters++
                Character.UnicodeScript.HEBREW -> hebrewLetters++
                Character.UnicodeScript.THAI -> thaiLetters++
                Character.UnicodeScript.MYANMAR -> burmeseLetters++
                Character.UnicodeScript.KHMER -> khmerLetters++
                Character.UnicodeScript.LAO -> laoLetters++
                Character.UnicodeScript.SINHALA -> sinhalaLetters++
                Character.UnicodeScript.TIBETAN -> tibetanLetters++
                Character.UnicodeScript.GEORGIAN -> georgianLetters++
                Character.UnicodeScript.ARMENIAN -> armenianLetters++
                Character.UnicodeScript.ETHIOPIC -> ethiopicLetters++
                Character.UnicodeScript.OL_CHIKI -> santaliLetters++
                Character.UnicodeScript.MEETEI_MAYEK -> manipuriLetters++
                else -> {}
            }
        }

        val totalLetters = (
            latinLetters + devanagariLetters + hangulLetters + kanaLetters +
                hanLetters + arabicLetters + bengaliLetters + tamilLetters +
                teluguLetters + kannadaLetters + malayalamLetters +
                gujaratiLetters + gurmukhiLetters + odiaLetters + cyrillicLetters +
                greekLetters + hebrewLetters + thaiLetters + burmeseLetters +
                khmerLetters + laoLetters + sinhalaLetters + tibetanLetters +
                georgianLetters + armenianLetters + ethiopicLetters +
                santaliLetters + manipuriLetters
            ).coerceAtLeast(1)

        val nonLatinDetected = mutableListOf<String>()
        fun hasScriptPresence(count: Int): Boolean {
            return count >= 12 || (count >= 4 && count.toDouble() / totalLetters >= 0.05)
        }

        if (hasScriptPresence(devanagariLetters)) {
            nonLatinDetected.addAll(disambiguateDevanagari(cleaned))
        }
        if (hasScriptPresence(hangulLetters)) nonLatinDetected.add("Korean")
        if (kanaLetters >= 6 || (kanaLetters > 0 && hanLetters > 0)) {
            nonLatinDetected.add("Japanese")
        } else if (hasScriptPresence(hanLetters)) {
            nonLatinDetected.add("Chinese")
        }
        if (hasScriptPresence(arabicLetters)) {
            nonLatinDetected.addAll(disambiguateArabicScript(cleaned))
        }
        if (hasScriptPresence(bengaliLetters)) {
            nonLatinDetected.addAll(disambiguateBengaliAssamese(cleaned))
        }
        if (hasScriptPresence(tamilLetters)) nonLatinDetected.add("Tamil")
        if (hasScriptPresence(teluguLetters)) nonLatinDetected.add("Telugu")
        if (hasScriptPresence(kannadaLetters)) nonLatinDetected.add("Kannada")
        if (hasScriptPresence(malayalamLetters)) nonLatinDetected.add("Malayalam")
        if (hasScriptPresence(gujaratiLetters)) nonLatinDetected.add("Gujarati")
        if (hasScriptPresence(gurmukhiLetters)) nonLatinDetected.add("Punjabi")
        if (hasScriptPresence(odiaLetters)) nonLatinDetected.add("Odia")
        if (hasScriptPresence(cyrillicLetters)) {
            nonLatinDetected.addAll(disambiguateCyrillic(cleaned))
        }
        if (hasScriptPresence(greekLetters)) nonLatinDetected.add("Greek")
        if (hasScriptPresence(hebrewLetters)) nonLatinDetected.add("Hebrew")
        if (hasScriptPresence(thaiLetters)) nonLatinDetected.add("Thai")
        if (hasScriptPresence(burmeseLetters)) nonLatinDetected.add("Burmese")
        if (hasScriptPresence(khmerLetters)) nonLatinDetected.add("Khmer")
        if (hasScriptPresence(laoLetters)) nonLatinDetected.add("Lao")
        if (hasScriptPresence(sinhalaLetters)) nonLatinDetected.add("Sinhala")
        if (hasScriptPresence(tibetanLetters)) nonLatinDetected.add("Tibetan")
        if (hasScriptPresence(georgianLetters)) nonLatinDetected.add("Georgian")
        if (hasScriptPresence(armenianLetters)) nonLatinDetected.add("Armenian")
        if (hasScriptPresence(ethiopicLetters)) nonLatinDetected.add("Amharic")
        if (hasScriptPresence(santaliLetters)) nonLatinDetected.add("Santali")
        if (hasScriptPresence(manipuriLetters)) nonLatinDetected.add("Manipuri")

        val nonLatinUnique = nonLatinDetected.distinct()

        if (nonLatinUnique.isEmpty()) {
            return disambiguateLatin(cleaned)
        }

        // Check whether Latin prose is substantial alongside non-Latin script(s)
        val latinWords = LATIN_WORD_REGEX.findAll(cleaned)
            .map { it.value }
            .filter { w -> w.length >= 2 && !w.all { ch -> ch.isUpperCase() } }
            .toList()
        val latinRatio = latinLetters.toDouble() / totalLetters
        val hasSubstantialLatin =
            (latinLetters >= 25 && latinRatio >= 0.20) ||
                (latinWords.size >= 6 && latinRatio >= 0.20) ||
                (latinLetters >= 80 && latinWords.size >= 12)

        return if (hasSubstantialLatin) {
            (disambiguateLatin(cleaned) + nonLatinUnique).distinct()
        } else {
            nonLatinUnique
        }
    }

    private fun detectExplicitMetadataLanguages(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()
        val found = mutableListOf<String>()

        // Check header lines near top of paper without splitting the entire rawText
        val headChunk = if (rawText.length > 3000) rawText.substring(0, 3000) else rawText
        val headLines = headChunk.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(25)
        for (line in headLines) {
            val match = EXPLICIT_LANGUAGE_LINE_REGEX.find(line) ?: continue
            val value = match.groupValues[1].trim()
            if (value.isBlank() || PROMPT_PLACEHOLDER_REGEX.containsMatchIn(value)) continue
            extractLanguageAliases(value, found)
        }

        // Check JSON "language"/"languages" fields if no header line matched
        if (found.isEmpty()) {
            val headText = rawText.take(2500)
            for (match in JSON_LANGUAGE_FIELD_REGEX.findAll(headText)) {
                val value = (match.groupValues[1].ifBlank { match.groupValues[2] }).trim()
                if (value.isBlank() || PROMPT_PLACEHOLDER_REGEX.containsMatchIn(value)) continue
                extractLanguageAliases(value, found)
            }
        }

        return found.distinct()
    }

    private fun extractLanguageAliases(value: String, out: MutableList<String>) {
        var matchedAnyAlias = false
        for ((regex, canonical) in EXPLICIT_LANGUAGE_ALIASES) {
            if (regex.containsMatchIn(value)) {
                out.add(canonical)
                matchedAnyAlias = true
            }
        }
        // Fallback for custom language names explicitly declared in Language: header
        if (!matchedAnyAlias) {
            value.split(CUSTOM_HEADER_SPLIT_REGEX)
                .map { it.trim() }
                .filter { token ->
                    token.length in 2..28 &&
                        token.all { ch -> ch.isLetter() || ch == ' ' || ch == '-' } &&
                        token.any { it.isLetter() }
                }
                .forEach { token ->
                    val normalized = token.replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase() else ch.toString()
                    }
                    out.add(normalized)
                }
        }
    }

    private fun disambiguateDevanagari(text: String): List<String> {
        val tokens = DEVANAGARI_WORD_REGEX.findAll(text).map { it.value }.toList()
        if (tokens.isEmpty()) return listOf("Hindi")

        var hindiScore = 0.0
        var marathiScore = 0.0
        var sanskritScore = 0.0
        var nepaliScore = 0.0
        var maithiliScore = 0.0
        var konkaniScore = 0.0
        var marathiLaCount = 0
        var sanskritVisargaWords = 0

        for (w in tokens) {
            if (w in HINDI_STOPWORDS) hindiScore += 2.0
            if (w in MARATHI_STOPWORDS) marathiScore += 2.0
            if (w in SANSKRIT_STOPWORDS) sanskritScore += 2.0
            if (w in NEPALI_STOPWORDS) nepaliScore += 2.0
            if (w in MAITHILI_STOPWORDS) maithiliScore += 2.2
            if (w in KONKANI_STOPWORDS) konkaniScore += 2.2

            if (w.contains('ळ')) marathiLaCount++
            if (w.length >= 2 && w.endsWith("ः")) sanskritVisargaWords++
        }

        // Distinctive Marathi character 'ळ' (also used in Konkani; Konkani stopwords break ties)
        if (marathiLaCount >= 1) {
            if (konkaniScore >= 4.0) {
                konkaniScore += (marathiLaCount * 1.5).coerceAtMost(4.0)
            } else {
                marathiScore += (marathiLaCount * 2.5).coerceAtMost(8.0)
            }
        }
        // Multiple visarga-ending words strongly indicate Sanskrit (ignore 1-2 Hindi words like प्रायः)
        if (sanskritVisargaWords >= 3) {
            sanskritScore += (sanskritVisargaWords * 1.5).coerceAtMost(9.0)
        }

        val scored = listOf(
            "Hindi" to hindiScore,
            "Marathi" to marathiScore,
            "Sanskrit" to sanskritScore,
            "Nepali" to nepaliScore,
            "Maithili" to maithiliScore,
            "Konkani" to konkaniScore
        ).filter { it.second >= 3.0 }
            .sortedByDescending { it.second }

        if (scored.isEmpty()) return listOf("Hindi")

        val topScore = scored.first().second
        return scored.filterIndexed { index, pair ->
            index == 0 || (pair.second >= 4.0 && pair.second >= topScore * 0.55)
        }.map { it.first }
    }

    private fun disambiguateArabicScript(text: String): List<String> {
        val tokens = ARABIC_WORD_REGEX.findAll(text).map { it.value }.toList()
        var arabicScore = 0.0
        var urduScore = 0.0
        var persianScore = 0.0
        var sindhiScore = 0.0

        var urduSpecificChars = 0
        var persianSpecificChars = 0
        var sindhiSpecificChars = 0

        for (w in tokens) {
            if (w in ARABIC_STOPWORDS) arabicScore += 2.0
            if (w in URDU_STOPWORDS) urduScore += 2.0
            if (w in PERSIAN_STOPWORDS) persianScore += 2.0

            for (ch in w) {
                when (ch) {
                    'ٹ', 'ڈ', 'ڑ', 'ں', 'ہ', 'ے', 'ھ' -> urduSpecificChars++
                    'پ', 'چ', 'ژ', 'گ' -> persianSpecificChars++
                    'ٻ', 'ڀ', 'ٺ', 'ٿ', 'ٽ', 'ڄ', 'ڃ', 'ڇ', 'ڊ', 'ڌ', 'ڍ', 'ڏ', 'ڙ', 'ڦ', 'ڪ', 'ڳ', 'ڱ', 'ڻ' -> sindhiSpecificChars++
                }
            }
        }

        if (sindhiSpecificChars >= 2) {
            sindhiScore += 4.0 + (sindhiSpecificChars * 1.5).coerceAtMost(8.0)
        }
        if (urduSpecificChars >= 1) {
            urduScore += (urduSpecificChars * 1.8).coerceAtMost(8.0)
        }
        if (persianSpecificChars >= 1 && urduSpecificChars == 0) {
            persianScore += (persianSpecificChars * 1.5).coerceAtMost(6.0)
        }

        val scored = listOf(
            "Arabic" to arabicScore,
            "Urdu" to urduScore,
            "Persian" to persianScore,
            "Sindhi" to sindhiScore
        ).filter { it.second >= 2.5 }
            .sortedByDescending { it.second }

        if (scored.isEmpty()) {
            return if (urduSpecificChars > 0) listOf("Urdu") else listOf("Arabic")
        }

        val topScore = scored.first().second
        return scored.filterIndexed { index, pair ->
            index == 0 || (pair.second >= 4.0 && pair.second >= topScore * 0.55)
        }.map { it.first }
    }

    private fun disambiguateBengaliAssamese(text: String): List<String> {
        val tokens = BENGALI_WORD_REGEX.findAll(text).map { it.value }.toList()
        var bengaliScore = 0.0
        var assameseScore = 0.0
        var assameseUniqueChars = 0

        for (w in tokens) {
            if (w in BENGALI_STOPWORDS) bengaliScore += 2.0
            if (w in ASSAMESE_STOPWORDS) assameseScore += 2.0
            for (ch in w) {
                if (ch == '\u09F0' || ch == '\u09F1') { // ৰ, ৱ
                    assameseUniqueChars++
                }
            }
        }

        if (assameseUniqueChars >= 1) {
            assameseScore += (assameseUniqueChars * 4.0).coerceAtMost(12.0)
        }

        val scored = listOf(
            "Bengali" to bengaliScore,
            "Assamese" to assameseScore
        ).filter { it.second >= 2.0 }
            .sortedByDescending { it.second }

        if (scored.isEmpty()) return listOf("Bengali")
        val topScore = scored.first().second
        return scored.filterIndexed { index, pair ->
            index == 0 || (pair.second >= 4.0 && pair.second >= topScore * 0.60)
        }.map { it.first }
    }

    private fun disambiguateCyrillic(text: String): List<String> {
        val tokens = CYRILLIC_WORD_REGEX.findAll(text).map { it.value.lowercase() }.toList()
        var russianScore = 0.0
        var ukrainianScore = 0.0
        var bulgarianScore = 0.0
        var serbianScore = 0.0

        var ukrainianUniqueChars = 0
        var serbianUniqueChars = 0

        for (w in tokens) {
            if (w in RUSSIAN_STOPWORDS) russianScore += 2.0
            if (w in UKRAINIAN_STOPWORDS) ukrainianScore += 2.0
            if (w in BULGARIAN_STOPWORDS) bulgarianScore += 2.0
            if (w in SERBIAN_STOPWORDS) serbianScore += 2.0

            for (ch in w) {
                when (ch) {
                    'є', 'і', 'ї', 'ґ' -> ukrainianUniqueChars++
                    'ђ', 'ј', 'љ', 'њ', 'ћ', 'џ' -> serbianUniqueChars++
                }
            }
        }

        if (ukrainianUniqueChars >= 1) {
            ukrainianScore += (ukrainianUniqueChars * 2.5).coerceAtMost(10.0)
        }
        if (serbianUniqueChars >= 1) {
            serbianScore += (serbianUniqueChars * 2.5).coerceAtMost(10.0)
        }

        val scored = listOf(
            "Russian" to russianScore,
            "Ukrainian" to ukrainianScore,
            "Bulgarian" to bulgarianScore,
            "Serbian" to serbianScore
        ).filter { it.second >= 2.0 }
            .sortedByDescending { it.second }

        if (scored.isEmpty()) return listOf("Russian")
        val topScore = scored.first().second
        return scored.filterIndexed { index, pair ->
            index == 0 || (pair.second >= 4.0 && pair.second >= topScore * 0.55)
        }.map { it.first }
    }

    private fun disambiguateLatin(text: String): List<String> {
        val tokens = LATIN_WORD_REGEX.findAll(text)
            .map { it.value.lowercase() }
            .toList()
        if (tokens.isEmpty()) return listOf("English")

        var englishHits = 0
        var frenchHits = 0
        var spanishHits = 0
        var germanHits = 0
        var portugueseHits = 0
        var italianHits = 0
        var turkishHits = 0
        var indonesianHits = 0
        var malayHits = 0
        var vietnameseHits = 0
        var dutchHits = 0
        var polishHits = 0
        var swedishHits = 0
        var norwegianHits = 0
        var danishHits = 0
        var finnishHits = 0
        var romanianHits = 0
        var czechHits = 0
        var hungarianHits = 0
        var swahiliHits = 0

        var frenchDiacritics = 0
        var spanishDiacritics = 0
        var germanDiacritics = 0
        var portugueseDiacritics = 0
        var turkishDiacritics = 0
        var vietnameseDiacritics = 0
        var polishDiacritics = 0
        var nordicÅDiacritics = 0
        var nordicØÆDiacritics = 0
        var finnishSwedishÄÖDiacritics = 0
        var romanianDiacritics = 0
        var czechDiacritics = 0
        var hungarianDiacritics = 0

        for (w in tokens) {
            if (w in ENGLISH_STOPWORDS) englishHits++
            if (w in FRENCH_STOPWORDS) frenchHits++
            if (w in SPANISH_STOPWORDS) spanishHits++
            if (w in GERMAN_STOPWORDS) germanHits++
            if (w in PORTUGUESE_STOPWORDS) portugueseHits++
            if (w in ITALIAN_STOPWORDS) italianHits++
            if (w in TURKISH_STOPWORDS) turkishHits++
            if (w in INDONESIAN_STOPWORDS) indonesianHits++
            if (w in MALAY_STOPWORDS) malayHits++
            if (w in VIETNAMESE_STOPWORDS) vietnameseHits++
            if (w in DUTCH_STOPWORDS) dutchHits++
            if (w in POLISH_STOPWORDS) polishHits++
            if (w in SWEDISH_STOPWORDS) swedishHits++
            if (w in NORWEGIAN_STOPWORDS) norwegianHits++
            if (w in DANISH_STOPWORDS) danishHits++
            if (w in FINNISH_STOPWORDS) finnishHits++
            if (w in ROMANIAN_STOPWORDS) romanianHits++
            if (w in CZECH_STOPWORDS) czechHits++
            if (w in HUNGARIAN_STOPWORDS) hungarianHits++
            if (w in SWAHILI_STOPWORDS) swahiliHits++

            for (ch in w) {
                when (ch) {
                    'é', 'è', 'ê', 'à', 'â', 'ç', 'î', 'ô', 'ù', 'û', 'œ' -> frenchDiacritics++
                }
                when (ch) {
                    'ñ', 'á', 'í', 'ó', 'ú' -> spanishDiacritics++
                }
                when (ch) {
                    'ß', 'ä', 'ö', 'ü' -> germanDiacritics++
                }
                when (ch) {
                    'ã', 'õ', 'ç', 'ê', 'ô' -> portugueseDiacritics++
                }
                when (ch) {
                    'ğ', 'ş', 'ı' -> turkishDiacritics++
                }
                when (ch) {
                    'đ', 'ơ', 'ư', 'ă', 'ả', 'ạ', 'ẩ', 'ậ', 'ắ', 'ằ', 'ẳ', 'ẵ', 'ặ',
                    'ẻ', 'ẽ', 'ẹ', 'ề', 'ế', 'ể', 'ễ', 'ệ', 'ỉ', 'ĩ', 'ị',
                    'ỏ', 'ọ', 'ồ', 'ố', 'ổ', 'ỗ', 'ộ', 'ờ', 'ớ', 'ở', 'ỡ', 'ợ',
                    'ủ', 'ũ', 'ụ', 'ừ', 'ứ', 'ử', 'ữ', 'ự', 'ỳ', 'ỷ', 'ỹ', 'ỵ' -> vietnameseDiacritics++
                }
                when (ch) {
                    'ą', 'ć', 'ę', 'ł', 'ń', 'ś', 'ź', 'ż' -> polishDiacritics++
                }
                when (ch) {
                    'å' -> nordicÅDiacritics++
                    'ø', 'æ' -> nordicØÆDiacritics++
                    'ä', 'ö' -> finnishSwedishÄÖDiacritics++
                }
                when (ch) {
                    'ă', 'î', 'ș', 'ț', 'ş', 'ţ' -> romanianDiacritics++
                }
                when (ch) {
                    'č', 'ď', 'ě', 'ň', 'ř', 'š', 'ť', 'ů', 'ž' -> czechDiacritics++
                }
                when (ch) {
                    'ő', 'ű' -> hungarianDiacritics++
                }
            }
        }
        // Include Spanish inverted punctuation outside letter tokens
        if (text.contains('¿') || text.contains('¡')) {
            spanishDiacritics += 2
        }

        // Diacritic bonus is gated behind stopword evidence so foreign names in English prose don't trigger false positives
        val engScore = englishHits.toDouble()
        val frScore = frenchHits.toDouble() + if (frenchHits >= 2) (frenchDiacritics * 0.6).coerceAtMost(4.0) else 0.0
        val esScore = spanishHits.toDouble() + if (spanishHits >= 2) (spanishDiacritics * 0.6).coerceAtMost(4.0) else 0.0
        val deScore = germanHits.toDouble() + if (germanHits >= 2) (germanDiacritics * 0.8).coerceAtMost(4.0) else 0.0
        val ptScore = portugueseHits.toDouble() + if (portugueseHits >= 2) (portugueseDiacritics * 0.6).coerceAtMost(4.0) else 0.0
        val itScore = italianHits.toDouble()
        val trScore = turkishHits.toDouble() + if (turkishHits >= 2) (turkishDiacritics * 0.8).coerceAtMost(4.5) else 0.0
        val idScore = indonesianHits.toDouble()
        val msScore = malayHits.toDouble()
        val viScore = vietnameseHits.toDouble() + if (vietnameseHits >= 2) (vietnameseDiacritics * 0.8).coerceAtMost(5.0) else 0.0
        val nlScore = dutchHits.toDouble()
        val plScore = polishHits.toDouble() + if (polishHits >= 2) (polishDiacritics * 0.8).coerceAtMost(4.5) else 0.0
        val svScore = swedishHits.toDouble() + if (swedishHits >= 2) ((nordicÅDiacritics + finnishSwedishÄÖDiacritics) * 0.6).coerceAtMost(4.0) else 0.0
        val noScore = norwegianHits.toDouble() + if (norwegianHits >= 2) ((nordicÅDiacritics + nordicØÆDiacritics) * 0.6).coerceAtMost(4.0) else 0.0
        val daScore = danishHits.toDouble() + if (danishHits >= 2) ((nordicÅDiacritics + nordicØÆDiacritics) * 0.6).coerceAtMost(4.0) else 0.0
        val fiScore = finnishHits.toDouble() + if (finnishHits >= 2) (finnishSwedishÄÖDiacritics * 0.6).coerceAtMost(4.0) else 0.0
        val roScore = romanianHits.toDouble() + if (romanianHits >= 2) (romanianDiacritics * 0.7).coerceAtMost(4.0) else 0.0
        val csScore = czechHits.toDouble() + if (czechHits >= 2) (czechDiacritics * 0.8).coerceAtMost(4.5) else 0.0
        val huScore = hungarianHits.toDouble() + if (hungarianHits >= 2) (hungarianDiacritics * 0.9).coerceAtMost(4.5) else 0.0
        val swScore = swahiliHits.toDouble()

        val candidates = listOf(
            Triple("English", engScore, englishHits),
            Triple("French", frScore, frenchHits),
            Triple("Spanish", esScore, spanishHits),
            Triple("German", deScore, germanHits),
            Triple("Portuguese", ptScore, portugueseHits),
            Triple("Italian", itScore, italianHits),
            Triple("Turkish", trScore, turkishHits),
            Triple("Indonesian", idScore, indonesianHits),
            Triple("Malay", msScore, malayHits),
            Triple("Vietnamese", viScore, vietnameseHits),
            Triple("Dutch", nlScore, dutchHits),
            Triple("Polish", plScore, polishHits),
            Triple("Swedish", svScore, swedishHits),
            Triple("Norwegian", noScore, norwegianHits),
            Triple("Danish", daScore, danishHits),
            Triple("Finnish", fiScore, finnishHits),
            Triple("Romanian", roScore, romanianHits),
            Triple("Czech", csScore, czechHits),
            Triple("Hungarian", huScore, hungarianHits),
            Triple("Swahili", swScore, swahiliHits)
        ).filter { (lang, score, hits) ->
            if (lang == "English") {
                hits >= 2 || score >= 2.0
            } else {
                hits >= 3 || (hits >= 2 && score >= 3.0)
            }
        }.sortedByDescending { it.second }

        if (candidates.isEmpty()) return listOf("English")

        // Avoid double-reporting closely related Indonesian + Malay unless both have distinct strong cues
        val topLang = candidates.first().first
        val filteredCandidates = candidates.filter { (lang, _, _) ->
            !((topLang == "Indonesian" && lang == "Malay") || (topLang == "Malay" && lang == "Indonesian"))
        }

        val topScore = filteredCandidates.first().second
        val winners = filteredCandidates.filterIndexed { idx, (_, score, hits) ->
            idx == 0 || (hits >= 3 && score >= 4.0 && score >= topScore * 0.45)
        }.map { it.first }

        return winners.ifEmpty { listOf("English") }
    }

    fun formatAvailableLabel(languages: List<String>): String {
        val clean = languages.filter { it.isNotBlank() }.distinct()
        return if (clean.isEmpty()) {
            "Available in English"
        } else {
            "Available in ${clean.joinToString(", ")}"
        }
    }
}

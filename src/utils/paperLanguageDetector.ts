const HINDI_STOPWORDS = new Set([
  'है', 'हैं', 'था', 'थी', 'थे', 'नहीं', 'में', 'के', 'की', 'का', 'को', 'से', 'पर',
  'और', 'निम्नलिखित', 'कौन', 'किस', 'क्या', 'गया', 'गई', 'गए', 'होता', 'होती',
]);
const MARATHI_STOPWORDS = new Set([
  'आहे', 'आहेत', 'नाही', 'नाहीत', 'होते', 'होता', 'होती', 'असते', 'मध्ये',
  'खालीलपैकी', 'वरीलपैकी', 'कोणते', 'कोणती', 'आणि', 'किंवा', 'परंतु',
]);
const SANSKRIT_STOPWORDS = new Set([
  'अस्ति', 'सन्ति', 'भवति', 'भवन्ति', 'अत्र', 'तत्र', 'यत्र', 'कुत्र', 'किम्', 'कस्य', 'इति', 'एव', 'अपि',
]);

const EXPLICIT_ALIASES: [RegExp, string][] = [
  [/\benglish\b|\benglisch\b|\banglais\b/i, 'English'],
  [/\bhindi\b|हिन्दी|हिंदी/i, 'Hindi'],
  [/\bmarathi\b|मराठी/i, 'Marathi'],
  [/\bsanskrit\b|संस्कृत/i, 'Sanskrit'],
  [/\bbengali\b|\bbangla\b|বাংলা/i, 'Bengali'],
  [/\btamil\b|தமிழ்/i, 'Tamil'],
  [/\btelugu\b|తెలుగు/i, 'Telugu'],
  [/\bkannada\b|ಕನ್ನಡ/i, 'Kannada'],
  [/\bmalayalam\b|മലയാളം/i, 'Malayalam'],
  [/\bgujarati\b|ગુજરાતી/i, 'Gujarati'],
  [/\bpunjabi\b|ਪੰਜਾਬੀ/i, 'Punjabi'],
  [/\bodia\b|\boriya\b|ଓଡ଼ିଆ/i, 'Odia'],
  [/\burdu\b|اردو/i, 'Urdu'],
  [/\bfrench\b|\bfrançais\b/i, 'French'],
  [/\bspanish\b|\bespañol\b/i, 'Spanish'],
  [/\bgerman\b|\bdeutsch\b/i, 'German'],
];

export const PaperLanguageDetector = {
  PRESET_LANGUAGES: [
    'English', 'Hindi', 'Bengali', 'Marathi', 'Telugu', 'Tamil',
    'Gujarati', 'Urdu', 'Kannada', 'Malayalam', 'Odia', 'Punjabi',
    'Sanskrit', 'Spanish', 'French', 'German', 'Arabic', 'Japanese',
  ],

  detectLanguages(rawText: string, title: string = ''): string[] {
    const sample = `${title}\n${rawText.slice(0, 4500)}`;
    if (!sample.trim()) return ['English'];

    // Check explicit header line like Language: Hindi & English
    const headerMatch = sample.match(/^\s*[#*\->\s]*(?:Language|Languages|Medium)\s*[:\-]\s*(.+)$/im);
    if (headerMatch?.[1]) {
      const explicit: string[] = [];
      for (const [re, name] of EXPLICIT_ALIASES) {
        if (re.test(headerMatch[1])) explicit.push(name);
      }
      if (explicit.length > 0) return Array.from(new Set(explicit));
    }

    const stripped = sample
      .replace(/^\s*(?:Title|Subject|Topic|Set|TargetExam|Correct\s*Answer|Explanation)\s*:.*$/gim, ' ')
      .replace(/\([A-E]\)|Option\s+[A-E]/gi, ' ');

    const devanagariMatches = stripped.match(/[\u0900-\u097F]+/g) || [];
    const bengaliMatches = stripped.match(/[\u0980-\u09FF]+/g) || [];
    const tamilMatches = stripped.match(/[\u0B80-\u0BFF]+/g) || [];
    const teluguMatches = stripped.match(/[\u0C00-\u0C7F]+/g) || [];
    const kannadaMatches = stripped.match(/[\u0C80-\u0CFF]+/g) || [];
    const malayalamMatches = stripped.match(/[\u0D00-\u0D7F]+/g) || [];
    const gujaratiMatches = stripped.match(/[\u0A80-\u0AFF]+/g) || [];
    const gurmukhiMatches = stripped.match(/[\u0A00-\u0A7F]+/g) || [];
    const arabicMatches = stripped.match(/[\u0600-\u06FF]+/g) || [];
    const latinWords = stripped.match(/[A-Za-z]{2,}/g) || [];

    const detected: string[] = [];

    if (devanagariMatches.length >= 3) {
      let hi = 0;
      let mr = 0;
      let sa = 0;
      for (const w of devanagariMatches) {
        if (HINDI_STOPWORDS.has(w)) hi += 2;
        if (MARATHI_STOPWORDS.has(w) || w.includes('ळ')) mr += 2.5;
        if (SANSKRIT_STOPWORDS.has(w) || w.endsWith('ः')) sa += 2;
      }
      if (mr > hi && mr >= 3) detected.push('Marathi');
      else if (sa > hi && sa >= 4) detected.push('Sanskrit');
      else detected.push('Hindi');
    }
    if (bengaliMatches.length >= 3) detected.push('Bengali');
    if (tamilMatches.length >= 3) detected.push('Tamil');
    if (teluguMatches.length >= 3) detected.push('Telugu');
    if (kannadaMatches.length >= 3) detected.push('Kannada');
    if (malayalamMatches.length >= 3) detected.push('Malayalam');
    if (gujaratiMatches.length >= 3) detected.push('Gujarati');
    if (gurmukhiMatches.length >= 3) detected.push('Punjabi');
    if (arabicMatches.length >= 3) detected.push('Urdu');

    if (detected.length === 0 || latinWords.length >= 12) {
      if (detected.length === 0 || latinWords.length > devanagariMatches.length * 0.4) {
        detected.unshift('English');
      }
    }

    return Array.from(new Set(detected));
  },
};

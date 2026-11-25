import type {
  FlashcardSrsState,
  HeadwordFilterResult,
  HeadwordQueueItem,
  LanguageWordOfTheDayState,
  VocabularyAuditFinding,
  VocabularyDeck,
  VocabularyDuplicateItem,
  VocabularyEntry,
  VocabularyExample,
  VocabularyImportAnalysis,
  VocabularyLevelCode,
  VocabularyMergeDecision,
  VocabularyPromptConfig,
  VocabularyUtilityTier,
  WordOfTheDayHistoryItem,
} from '../types/phase4';
import { SpacedRepetitionEngine } from './spacedRepetitionEngine';

export interface VocabLevelMeta {
  code: VocabularyLevelCode;
  shortBadge: string;
  label: string;
  description: string;
  sortRank: number;
}

export const VOCAB_LEVELS: Record<VocabularyLevelCode, VocabLevelMeta> = {
  A1: { code: 'A1', shortBadge: 'A1', label: 'Beginner', description: 'Core everyday survival words', sortRank: 1 },
  A2: { code: 'A2', shortBadge: 'A2', label: 'Elementary', description: 'Routine conversations & travel', sortRank: 2 },
  B1: { code: 'B1', shortBadge: 'B1', label: 'Intermediate', description: 'Independent reading & news', sortRank: 3 },
  B2: { code: 'B2', shortBadge: 'B2', label: 'Upper-Intermediate', description: 'Workplace & competitive exams', sortRank: 4 },
  C1: { code: 'C1', shortBadge: 'C1', label: 'Advanced', description: 'Editorial, academic & standardized exam precision', sortRank: 5 },
  C2: { code: 'C2', shortBadge: 'C2', label: 'Mastery', description: 'Literary & specialist nuance', sortRank: 6 },
  MIXED: { code: 'MIXED', shortBadge: 'MIXED', label: 'Mixed / Progressive', description: 'Spans multiple proficiency tiers', sortRank: 7 },
  CUSTOM: { code: 'CUSTOM', shortBadge: 'CUSTOM', label: 'Custom Level', description: 'Track-specific tier (JLPT, HSK, GRE)', sortRank: 8 },
};

export const FOLDER_LEVEL_CODES: VocabularyLevelCode[] = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];
export const PROMPT_LEVEL_CODES: VocabularyLevelCode[] = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2', 'MIXED', 'CUSTOM'];

export const WOTD_MIN_WORDS_THRESHOLD = 10;

export function parseLevelCode(raw?: string): VocabularyLevelCode {
  const clean = (raw || '').trim().toLowerCase();
  if (!clean) return 'B1';
  if (clean === 'a1' || clean.includes('begin') || clean.includes('n5') || clean.includes('hsk 1')) return 'A1';
  if (clean === 'a2' || clean.includes('element') || clean.includes('n4') || clean.includes('hsk 2')) return 'A2';
  if (clean === 'b2' || clean.includes('upper') || clean.includes('n2') || clean.includes('hsk 5')) return 'B2';
  if (clean === 'b1' || clean.includes('intermed') || clean.includes('n3') || clean.includes('hsk 4')) return 'B1';
  if (clean === 'c1' || clean.includes('advanc') || clean.includes('n1') || clean.includes('hsk 6')) return 'C1';
  if (clean === 'c2' || clean.includes('master') || clean.includes('literary')) return 'C2';
  if (clean.includes('mix') || clean.includes('progress')) return 'MIXED';
  return 'CUSTOM';
}

export function getLevelDescriptor(
  levelCode: VocabularyLevelCode,
  language: string,
  customLabel: string = ''
): { title: string; shortBadge: string; description: string } {
  const norm = (language || '').trim().toLowerCase();
  if (levelCode === 'CUSTOM' && customLabel.trim()) {
    const clean = customLabel.trim();
    return { title: clean, shortBadge: clean.slice(0, 10), description: `Custom Track: ${clean}` };
  }
  if (norm === 'japanese' || norm === 'ja') {
    const map: Record<VocabularyLevelCode, [string, string]> = {
      A1: ['JLPT N5 (Basic Survival)', 'JLPT N5'],
      A2: ['JLPT N4 (Everyday Routine)', 'JLPT N4'],
      B1: ['JLPT N3 (Practical Fluency)', 'JLPT N3'],
      B2: ['JLPT N2 (News & Workplace)', 'JLPT N2'],
      C1: ['JLPT N1 (Formal & Abstract)', 'JLPT N1'],
      C2: ['JLPT N1+ (Literary & Idioms)', 'JLPT N1+'],
      MIXED: ['Mixed / Progressive', 'MIXED'],
      CUSTOM: [customLabel || 'Custom Japanese Track', 'CUSTOM'],
    };
    const [t, b] = map[levelCode];
    return { title: t, shortBadge: b, description: VOCAB_LEVELS[levelCode].description };
  }
  if (norm === 'chinese' || norm.includes('mandarin') || norm === 'zh') {
    const map: Record<VocabularyLevelCode, [string, string]> = {
      A1: ['HSK 1 (Foundational Survival)', 'HSK 1'],
      A2: ['HSK 2–3 (Routine Dialogue)', 'HSK 2-3'],
      B1: ['HSK 4 (General Fluency)', 'HSK 4'],
      B2: ['HSK 5 (News & Professional)', 'HSK 5'],
      C1: ['HSK 6 (Advanced Academic)', 'HSK 6'],
      C2: ['HSK 7–9 (Classical & Mastery)', 'HSK 7-9'],
      MIXED: ['Mixed / Progressive', 'MIXED'],
      CUSTOM: [customLabel || 'Custom Mandarin Track', 'CUSTOM'],
    };
    const [t, b] = map[levelCode];
    return { title: t, shortBadge: b, description: VOCAB_LEVELS[levelCode].description };
  }
  if (norm === 'korean' || norm === 'ko') {
    const map: Record<VocabularyLevelCode, [string, string]> = {
      A1: ['TOPIK I-1 (Beginner)', 'TOPIK 1'],
      A2: ['TOPIK I-2 (Routine)', 'TOPIK 2'],
      B1: ['TOPIK II-3 (Practical)', 'TOPIK 3'],
      B2: ['TOPIK II-4 (Workplace)', 'TOPIK 4'],
      C1: ['TOPIK II-5 (Editorial)', 'TOPIK 5'],
      C2: ['TOPIK II-6 (Literary)', 'TOPIK 6'],
      MIXED: ['Mixed / Progressive', 'MIXED'],
      CUSTOM: [customLabel || 'Custom Korean Track', 'CUSTOM'],
    };
    const [t, b] = map[levelCode];
    return { title: t, shortBadge: b, description: VOCAB_LEVELS[levelCode].description };
  }
  const meta = VOCAB_LEVELS[levelCode] || VOCAB_LEVELS.B1;
  return {
    title: meta.code === 'MIXED' || meta.code === 'CUSTOM' ? meta.label : `${meta.label} (${meta.code})`,
    shortBadge: meta.shortBadge,
    description: meta.description,
  };
}

export const VocabularyVariants = {
  isEnglish(language: string): boolean {
    const c = (language || '').trim().toLowerCase();
    return !c || c === 'english' || c === 'uk english' || c === 'us english' || c === 'british english' || c === 'american english';
  },
  normalize(language: string, raw?: string): string {
    const clean = (raw || '').trim();
    if (!this.isEnglish(language)) return clean;
    if (!clean) return 'UK';
    const low = clean.toLowerCase();
    if (low === 'uk' || low === 'gb' || low.includes('british') || low.includes('oxford')) return 'UK';
    if (low === 'us' || low === 'usa' || low.includes('american') || low.includes('merriam')) return 'US';
    if (low === 'neutral' || low.includes('international')) return 'Neutral';
    return clean;
  },
  displayLabel(variant: string): string {
    const u = (variant || '').trim().toUpperCase();
    if (u === 'UK') return 'UK (British / Oxford)';
    if (u === 'US') return 'US (American)';
    if (u === 'NEUTRAL') return 'Neutral / International';
    return variant || 'Default';
  },
  promptDirective(language: string, variant: string): string {
    const norm = this.normalize(language, variant);
    if (!this.isEnglish(language)) {
      return norm ? `Follow ${norm} regional spelling and natural usage conventions for ${language}.` : '';
    }
    if (norm === 'UK') {
      return 'Strictly follow British / Oxford English spelling (colour, analyse, programme, centre, judgement) and UK RP/Oxford IPA pronunciation.';
    }
    if (norm === 'US') {
      return 'Strictly follow American English spelling (color, analyze, program, center, judgment) and General American IPA.';
    }
    return 'Use standard international English; note UK/US variants briefly in usageNote when relevant.';
  },
};

export function normalizeWordKey(raw: string): string {
  const trimmed = (raw || '')
    .trim()
    .replace(/\u00AD/g, '')
    .replace(/[\u2018\u2019\u02BC]/g, "'")
    .replace(/[\u2010\u2011\u2012\u2013\u2014]/g, '-')
    .replace(/\s+/g, ' ');
  if (!trimmed) return '';
  return trimmed
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/^[^\p{L}\p{N}]+|[^\p{L}\p{N}]+$/gu, '');
}

function stripOuterFences(raw: string): string {
  const cleaned = (raw || '')
    .replace(/[\uFEFF\u200B\u200C\u200D\u2060]/g, '')
    .replace(/\r\n/g, '\n')
    .trim();
  const match = /```(?:json|text|markdown|md)?\s*([\s\S]*?)```/i.exec(cleaned);
  if (match && match[1].trim()) return match[1].trim();
  return cleaned.replace(/^```(?:json|text|markdown|md)?\s*/i, '').replace(/```$/, '').trim();
}

function firstNonBlankField(obj: Record<string, unknown>, keys: string[]): string {
  for (const k of keys) {
    const v = obj[k];
    if (typeof v === 'string' && v.trim() && v.trim().toLowerCase() !== 'null') {
      return v.trim();
    }
  }
  return '';
}

function parseListField(obj: Record<string, unknown>, keys: string[]): string[] {
  for (const k of keys) {
    const v = obj[k];
    if (Array.isArray(v)) {
      const list = v.map((x) => String(x || '').trim()).filter(Boolean);
      if (list.length > 0) return list;
    } else if (typeof v === 'string' && v.trim() && v.trim().toLowerCase() !== 'none' && v.trim() !== '-') {
      return v
        .split(/[,;•|]/)
        .map((s) => s.trim())
        .filter((s) => s.length > 0 && s.toLowerCase() !== 'none');
    }
  }
  return [];
}

function parseJsonExamples(obj: Record<string, unknown>): VocabularyExample[] {
  const rawArr = obj.examples || obj.contextExamples || obj.sentences;
  if (Array.isArray(rawArr)) {
    const out: VocabularyExample[] = [];
    for (const item of rawArr) {
      if (typeof item === 'string' && item.trim()) {
        out.push({ context: 'General', sentence: item.trim(), translation: '' });
      } else if (item && typeof item === 'object') {
        const rec = item as Record<string, unknown>;
        const sentence = firstNonBlankField(rec, ['sentence', 'example', 'text']);
        if (sentence) {
          out.push({
            context: firstNonBlankField(rec, ['context', 'register', 'label']) || 'General',
            sentence,
            translation: firstNonBlankField(rec, ['translation', 'meaning']),
          });
        }
      }
    }
    return out;
  }
  const single = firstNonBlankField(obj, ['example', 'exampleSentence', 'sentence']);
  return single ? [{ context: 'General', sentence: single, translation: '' }] : [];
}

function buildEntryFromObject(
  obj: Record<string, unknown>,
  defaultLevelCode: VocabularyLevelCode,
  defaultCustomLabel: string
): VocabularyEntry | null {
  const word = firstNonBlankField(obj, ['word', 'term', 'headword', 'expression', 'phrase', 'vocab']);
  const meaning = firstNonBlankField(obj, ['meaning', 'definition', 'gloss', 'explanation', 'sense']);
  const translation = firstNonBlankField(obj, ['translation', 'translated', 'nativeMeaning', 'hindi', 'glossTranslation']);
  if (!word || (!meaning && !translation)) return null;

  const rawLevel = firstNonBlankField(obj, ['level', 'cefr', 'levelCode', 'proficiency', 'difficulty']);
  const fallbackLvl: VocabularyLevelCode = defaultLevelCode === 'MIXED' ? 'B1' : defaultLevelCode;
  let lvl = rawLevel ? parseLevelCode(rawLevel) : fallbackLvl;
  if (lvl === 'MIXED' || (lvl === 'CUSTOM' && !defaultCustomLabel && (!rawLevel || rawLevel.toLowerCase() === 'custom'))) {
    lvl = fallbackLvl;
  }

  const now = Date.now();
  return {
    id: firstNonBlankField(obj, ['id']) || `ve-${now}-${Math.random().toString(36).slice(2, 7)}`,
    word,
    pronunciation: firstNonBlankField(obj, ['pronunciation', 'ipa', 'phonetic', 'romanization', 'reading', 'pinyin']),
    partOfSpeech: firstNonBlankField(obj, ['partOfSpeech', 'pos', 'category', 'grammarType', 'wordClass']),
    levelCode: lvl,
    customLevelLabel: lvl === 'CUSTOM' ? rawLevel || defaultCustomLabel : '',
    language: firstNonBlankField(obj, ['language', 'lang', 'sourceLanguage', 'targetLanguage']),
    languageVariant: firstNonBlankField(obj, ['languageVariant', 'variant', 'origin', 'dialect', 'standard']),
    meaning: meaning || translation,
    translation,
    translationLanguage: firstNonBlankField(obj, ['translationLanguage', 'bridgeLanguage', 'meaningLanguage']),
    examples: parseJsonExamples(obj),
    synonyms: parseListField(obj, ['synonyms', 'similar']),
    antonyms: parseListField(obj, ['antonyms', 'opposites']),
    collocations: parseListField(obj, ['collocations', 'phrases', 'commonPhrases', 'expressions']),
    usageNote: firstNonBlankField(obj, ['usageNote', 'note', 'nuance', 'tip', 'usage', 'memoryHook']),
    isBookmarked: Boolean(obj.isBookmarked),
    createdAt: now,
    updatedAt: now,
  };
}

function parseStructuredTextEntries(
  cleaned: string,
  defaultLevelCode: VocabularyLevelCode,
  defaultCustomLabel: string
): VocabularyEntry[] {
  const lines = cleaned.split('\n');
  const entries: VocabularyEntry[] = [];

  let word = '';
  let pronunciation = '';
  let pos = '';
  let levelRaw = '';
  let lang = '';
  let transLang = '';
  let variant = '';
  let meaning = '';
  let translation = '';
  let examples: VocabularyExample[] = [];
  let synonyms: string[] = [];
  let antonyms: string[] = [];
  let collocations: string[] = [];
  let usageNote = '';
  let inExamples = false;

  const flush = () => {
    if (word && (meaning || translation)) {
      const lvl = levelRaw ? parseLevelCode(levelRaw) : defaultLevelCode === 'MIXED' ? 'B1' : defaultLevelCode;
      const now = Date.now();
      entries.push({
        id: `ve-${now}-${Math.random().toString(36).slice(2, 7)}`,
        word: word.trim(),
        pronunciation: pronunciation.trim(),
        partOfSpeech: pos.trim(),
        levelCode: lvl === 'MIXED' ? 'B1' : lvl,
        customLevelLabel: lvl === 'CUSTOM' ? levelRaw || defaultCustomLabel : '',
        language: lang.trim(),
        languageVariant: variant.trim(),
        meaning: (meaning || translation).trim(),
        translation: translation.trim(),
        translationLanguage: transLang.trim(),
        examples: [...examples],
        synonyms: Array.from(new Set(synonyms)),
        antonyms: Array.from(new Set(antonyms)),
        collocations: Array.from(new Set(collocations)),
        usageNote: usageNote.trim(),
        isBookmarked: false,
        createdAt: now,
        updatedAt: now,
      });
    }
    word = '';
    pronunciation = '';
    pos = '';
    levelRaw = '';
    meaning = '';
    translation = '';
    examples = [];
    synonyms = [];
    antonyms = [];
    collocations = [];
    usageNote = '';
    inExamples = false;
  };

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line || line === '---' || line === '***') {
      if (line === '---' || line === '***') flush();
      continue;
    }

    const wMatch = /^(?:\d+[.)]\s*)?(?:\*\*)?(?:WORD|TERM|HEADWORD)\s*[:-]\s*(.+)$/i.exec(line);
    if (wMatch) {
      flush();
      word = wMatch[1].replace(/\*\*/g, '').trim();
      continue;
    }

    const numBold = /^\d+[.)]\s+\*\*([^*]+)\*\*(?:\s*\(([^)]+)\))?(?:\s*[:-–—]\s*(.*))?$/.exec(line);
    if (numBold) {
      flush();
      word = numBold[1].trim();
      const p = (numBold[2] || '').trim();
      if (p) {
        if (p.includes('/') || p.startsWith('[')) pronunciation = p;
        else pos = p;
      }
      if (numBold[3]?.trim()) meaning = numBold[3].trim();
      continue;
    }

    if (!word) {
      const dashLine = /^(?:\d+[.)]\s*|[-•*]\s*)?([^:—–-]{2,60})\s*(?:—|–|->|::)\s*(.{4,})$/.exec(line);
      if (dashLine && !dashLine[1].trim().includes(' ')) {
        word = dashLine[1].replace(/\*\*/g, '').trim();
        meaning = dashLine[2].trim();
        flush();
      }
      continue;
    }

    const field = /^(?:[-•*]\s*)?(?:\*\*)?([A-Za-z /]+?)(?:\*\*)?\s*:\s*(.*)$/.exec(line);
    if (field) {
      const k = field[1].trim().toLowerCase();
      const v = field[2].replace(/\*\*/g, '').trim();
      if (['language', 'lang'].includes(k)) { lang = v; inExamples = false; }
      else if (['translation language', 'bridge language'].includes(k)) { transLang = v; inExamples = false; }
      else if (['variant', 'english variant', 'standard'].includes(k)) { variant = v; inExamples = false; }
      else if (['meaning', 'definition', 'gloss'].includes(k)) { meaning = v; inExamples = false; }
      else if (['translation', 'hindi', 'native meaning'].includes(k)) { translation = v; inExamples = false; }
      else if (['pos', 'part of speech', 'category', 'word class'].includes(k)) { pos = v; inExamples = false; }
      else if (['pronunciation', 'ipa', 'reading', 'pinyin'].includes(k)) { pronunciation = v; inExamples = false; }
      else if (['level', 'cefr', 'difficulty'].includes(k)) { levelRaw = v; inExamples = false; }
      else if (['examples', 'example', 'sentences'].includes(k)) {
        inExamples = true;
        if (v) examples.push({ context: 'General', sentence: v, translation: '' });
      } else if (['synonyms', 'similar'].includes(k)) {
        inExamples = false;
        synonyms.push(...v.split(/[,;•|]/).map((s) => s.trim()).filter(Boolean));
      } else if (['antonyms', 'opposites'].includes(k)) {
        inExamples = false;
        antonyms.push(...v.split(/[,;•|]/).map((s) => s.trim()).filter(Boolean));
      } else if (['collocations', 'phrases'].includes(k)) {
        inExamples = false;
        collocations.push(...v.split(/[,;•|]/).map((s) => s.trim()).filter(Boolean));
      } else if (['usage', 'usage note', 'note', 'nuance', 'tip'].includes(k)) {
        inExamples = false;
        usageNote = v;
      } else if (inExamples) {
        examples.push({ context: field[1].trim(), sentence: v, translation: '' });
      }
      continue;
    }

    if (inExamples) {
      const cleanedBullet = line.replace(/^[-•*\d.)]+\s*/, '').trim();
      if (cleanedBullet) {
        examples.push({ context: 'Context', sentence: cleanedBullet, translation: '' });
      }
    }
  }
  flush();
  return entries;
}

export const VocabularyEngine = {
  normalizeWordKey,

  parseVocabularyInput(
    rawInput: string,
    defaultLevelCode: VocabularyLevelCode = 'B1',
    defaultCustomLabel: string = ''
  ): {
    entries: VocabularyEntry[];
    detectedFormat: 'JSON' | 'STRUCTURED_TEXT' | 'UNKNOWN';
    warnings: string[];
  } {
    const cleaned = stripOuterFences(rawInput);
    if (!cleaned) {
      return { entries: [], detectedFormat: 'UNKNOWN', warnings: ['Input is empty.'] };
    }

    // 1. JSON attempt
    if (cleaned.startsWith('[') || cleaned.startsWith('{') || /"(?:word|term|headword)"\s*:/i.test(cleaned)) {
      try {
        const sanitized = cleaned
          .replace(/[\u201C\u201D]/g, '"')
          .replace(/,\s*([}\]])/g, '$1');
        const arrMatch = /\[[\s\S]*\]/.exec(sanitized);
        const parsed = JSON.parse(arrMatch ? arrMatch[0] : sanitized);
        const rawList: unknown[] = Array.isArray(parsed)
          ? parsed
          : parsed?.vocabulary || parsed?.words || parsed?.entries || parsed?.items || (parsed?.word ? [parsed] : []);
        const entries = rawList
          .map((item) =>
            item && typeof item === 'object'
              ? buildEntryFromObject(item as Record<string, unknown>, defaultLevelCode, defaultCustomLabel)
              : null
          )
          .filter((x): x is VocabularyEntry => Boolean(x));
        if (entries.length > 0) {
          return { entries, detectedFormat: 'JSON', warnings: [] };
        }
      } catch {
        // Fall through to structured text
      }
    }

    // 2. Structured Text attempt
    const textEntries = parseStructuredTextEntries(cleaned, defaultLevelCode, defaultCustomLabel);
    if (textEntries.length > 0) {
      return { entries: textEntries, detectedFormat: 'STRUCTURED_TEXT', warnings: [] };
    }

    return {
      entries: [],
      detectedFormat: 'UNKNOWN',
      warnings: ['Could not parse vocabulary entries. Paste a JSON array or labeled WORD:/MEANING:/EXAMPLES: blocks.'],
    };
  },

  analyzeImport(
    parsedEntries: VocabularyEntry[],
    detectedFormat: 'JSON' | 'STRUCTURED_TEXT' | 'UNKNOWN',
    warnings: string[],
    targetLanguage: string,
    existingDecks: VocabularyDeck[],
    targetDeckId: string | null
  ): VocabularyImportAnalysis {
    const normLang = targetLanguage.trim().toLowerCase();
    const existingByKey = new Map<string, { entry: VocabularyEntry; deck: VocabularyDeck }>();
    for (const deck of existingDecks) {
      if (normLang && deck.language.trim().toLowerCase() !== normLang) continue;
      for (const entry of deck.entries) {
        const key = normalizeWordKey(entry.word);
        if (key && !existingByKey.has(key)) {
          existingByKey.set(key, { entry, deck });
        }
      }
    }

    const uniqueNewEntries: VocabularyEntry[] = [];
    const duplicateItems: VocabularyDuplicateItem[] = [];
    const batchSeen = new Map<string, VocabularyEntry>();

    parsedEntries.forEach((inc, idx) => {
      const key = normalizeWordKey(inc.word);
      const host = existingByKey.get(key);
      if (host) {
        duplicateItems.push({
          id: `dup-${idx}-${inc.id}`,
          incoming: inc,
          existing: host.entry,
          existingDeckId: host.deck.id,
          existingDeckTitle: host.deck.title,
          existingLevelTitle: getLevelDescriptor(host.deck.levelCode, host.deck.language, host.deck.customLevelLabel).title,
          isSameDeck: host.deck.id === targetDeckId,
          isIntraBatch: false,
        });
      } else if (batchSeen.has(key)) {
        const first = batchSeen.get(key)!;
        duplicateItems.push({
          id: `dup-batch-${idx}-${inc.id}`,
          incoming: inc,
          existing: first,
          existingDeckId: targetDeckId || 'new',
          existingDeckTitle: 'Current Batch',
          existingLevelTitle: inc.levelCode,
          isSameDeck: true,
          isIntraBatch: true,
        });
      } else {
        batchSeen.set(key, inc);
        uniqueNewEntries.push(inc);
      }
    });

    return {
      uniqueNewEntries,
      duplicateItems,
      totalParsedCount: parsedEntries.length,
      detectedFormat,
      warnings,
    };
  },

  mergeEntrySegments(
    existing: VocabularyEntry,
    incoming: VocabularyEntry,
    decision: VocabularyMergeDecision
  ): VocabularyEntry {
    const nextExamples = [...existing.examples];
    if (decision.mergeExamples) {
      const existingSentKeys = new Set(existing.examples.map((e) => e.sentence.trim().toLowerCase()));
      for (const ex of incoming.examples) {
        if (!existingSentKeys.has(ex.sentence.trim().toLowerCase())) {
          nextExamples.push(ex);
        }
      }
    }

    const union = (a: string[], b: string[]) => Array.from(new Set([...a, ...b]));

    return {
      ...existing,
      examples: nextExamples,
      translation:
        decision.mergeTranslation && incoming.translation
          ? existing.translation && !existing.translation.toLowerCase().includes(incoming.translation.toLowerCase())
            ? `${existing.translation}; ${incoming.translation}`
            : incoming.translation
          : existing.translation,
      synonyms: decision.mergeSynonymsAndCollocations ? union(existing.synonyms, incoming.synonyms) : existing.synonyms,
      antonyms: decision.mergeSynonymsAndCollocations ? union(existing.antonyms, incoming.antonyms) : existing.antonyms,
      collocations: decision.mergeSynonymsAndCollocations ? union(existing.collocations, incoming.collocations) : existing.collocations,
      meaning: decision.mergeMeaningAndNote && incoming.meaning ? incoming.meaning : existing.meaning,
      usageNote: decision.mergeMeaningAndNote && incoming.usageNote ? incoming.usageNote : existing.usageNote,
      levelCode: decision.mergeLevelAndMeta ? incoming.levelCode : existing.levelCode,
      partOfSpeech: decision.mergeLevelAndMeta && incoming.partOfSpeech ? incoming.partOfSpeech : existing.partOfSpeech,
      pronunciation: decision.mergeLevelAndMeta && incoming.pronunciation ? incoming.pronunciation : existing.pronunciation,
      updatedAt: Date.now(),
    };
  },

  parseHeadwordIndexInput(
    rawInput: string,
    defaultLanguage: string = 'English',
    defaultLevelCode: VocabularyLevelCode = 'B2',
    defaultDomain: string = 'Competitive Exam Editorial'
  ): HeadwordQueueItem[] {
    const cleaned = stripOuterFences(rawInput);
    if (!cleaned) return [];
    const now = Date.now();
    const items: HeadwordQueueItem[] = [];

    if (cleaned.startsWith('[') || cleaned.startsWith('{')) {
      try {
        const sanitized = cleaned.replace(/,\s*([}\]])/g, '$1');
        const arr = JSON.parse(sanitized);
        const list = Array.isArray(arr) ? arr : arr?.headwords || arr?.words || [];
        for (const el of list) {
          if (typeof el === 'string' && el.trim()) {
            items.push({
              id: `hw-${now}-${Math.random().toString(36).slice(2, 7)}`,
              word: el.trim(),
              language: defaultLanguage,
              levelCode: defaultLevelCode === 'MIXED' ? 'B2' : defaultLevelCode,
              customLevelLabel: '',
              partOfSpeech: '',
              domain: defaultDomain,
              utilityReason: '',
              addedAt: now,
            });
          } else if (el && typeof el === 'object') {
            const w = firstNonBlankField(el, ['word', 'headword', 'term']);
            if (w) {
              const lvl = parseLevelCode(firstNonBlankField(el, ['level', 'levelCode', 'cefr']) || defaultLevelCode);
              items.push({
                id: `hw-${now}-${Math.random().toString(36).slice(2, 7)}`,
                word: w,
                language: firstNonBlankField(el, ['language']) || defaultLanguage,
                levelCode: lvl === 'MIXED' ? 'B2' : lvl,
                customLevelLabel: '',
                partOfSpeech: firstNonBlankField(el, ['partOfSpeech', 'pos']),
                domain: firstNonBlankField(el, ['domain', 'topic']) || defaultDomain,
                utilityReason: firstNonBlankField(el, ['utilityReason', 'reason', 'why']),
                addedAt: now,
              });
            }
          }
        }
        if (items.length > 0) return items;
      } catch {
        // fallback line-by-line
      }
    }

    for (const rawLine of cleaned.split('\n')) {
      const line = rawLine.trim().replace(/^(?:\d+[.)]|[-*•])\s+/, '');
      if (!line) continue;
      const parts = line.split(/\s*(?:—|–|->|\|)\s*/);
      const w = parts[0].replace(/\*\*/g, '').trim();
      if (w) {
        items.push({
          id: `hw-${now}-${Math.random().toString(36).slice(2, 7)}`,
          word: w,
          language: defaultLanguage,
          levelCode: defaultLevelCode === 'MIXED' ? 'B2' : defaultLevelCode,
          customLevelLabel: '',
          partOfSpeech: '',
          domain: defaultDomain,
          utilityReason: parts[1]?.trim() || '',
          addedAt: now,
        });
      }
    }
    return items;
  },

  filterAndQueueHeadwords(
    candidates: HeadwordQueueItem[],
    existingDecks: VocabularyDeck[],
    currentQueue: HeadwordQueueItem[],
    dismissedKeys: string[]
  ): HeadwordFilterResult {
    const deckKeys = new Set<string>();
    for (const d of existingDecks) {
      for (const e of d.entries) {
        deckKeys.add(`${d.language.trim().toLowerCase()}::${normalizeWordKey(e.word)}`);
      }
    }
    const queueKeys = new Set(
      currentQueue.map((q) => `${q.language.trim().toLowerCase()}::${normalizeWordKey(q.word)}`)
    );
    const dismissedSet = new Set(dismissedKeys);

    const addedItems: HeadwordQueueItem[] = [];
    let skippedAlreadyInDeckCount = 0;
    let skippedAlreadyInQueueCount = 0;
    let skippedDismissedCount = 0;

    for (const c of candidates) {
      const composite = `${c.language.trim().toLowerCase()}::${normalizeWordKey(c.word)}`;
      if (deckKeys.has(composite)) skippedAlreadyInDeckCount++;
      else if (queueKeys.has(composite)) skippedAlreadyInQueueCount++;
      else if (dismissedSet.has(composite)) skippedDismissedCount++;
      else {
        queueKeys.add(composite);
        addedItems.push(c);
      }
    }

    return {
      addedItems,
      skippedAlreadyInDeckCount,
      skippedAlreadyInQueueCount,
      skippedDismissedCount,
      totalProcessedCount: candidates.length,
    };
  },

  /**
   * Local heuristic offline lexicon audit checking missing fields, duplicate overlaps,
   * level calibration, and empty examples.
   */
  runLocalLexiconAudit(deck: VocabularyDeck, allDecks: VocabularyDeck[]): VocabularyAuditFinding[] {
    const findings: VocabularyAuditFinding[] = [];
    const crossDeckMap = new Map<string, string[]>();
    for (const d of allDecks) {
      if (d.language.toLowerCase() !== deck.language.toLowerCase()) continue;
      for (const e of d.entries) {
        const k = normalizeWordKey(e.word);
        const list = crossDeckMap.get(k) || [];
        list.push(d.title);
        crossDeckMap.set(k, list);
      }
    }

    for (const entry of deck.entries) {
      const k = normalizeWordKey(entry.word);
      const hosts = crossDeckMap.get(k) || [];
      if (hosts.length > 1) {
        findings.push({
          entryId: entry.id,
          currentEntry: entry,
          status: 'DUPLICATE_ALERT',
          issueSummary: `Appears in ${hosts.length} decks (${hosts.slice(0, 2).join(', ')})`,
          verifiedCategoryType: entry.partOfSpeech || 'lexeme',
        });
      } else if (!entry.partOfSpeech.trim() || entry.partOfSpeech.trim().toLowerCase() === 'unknown') {
        findings.push({
          entryId: entry.id,
          currentEntry: entry,
          status: 'CATEGORY_ISSUE',
          issueSummary: 'Missing grammatical part-of-speech tag.',
          verifiedCategoryType: 'noun',
          suggestedPartOfSpeech: entry.word.endsWith('ly') ? 'adverb' : 'noun',
        });
      } else if (entry.examples.length === 0) {
        findings.push({
          entryId: entry.id,
          currentEntry: entry,
          status: 'EXAMPLE_FIX',
          issueSummary: 'No usage example recorded for contextual recall.',
          verifiedCategoryType: entry.partOfSpeech,
          suggestedExamples: [
            {
              context: 'General',
              sentence: `In context, "${entry.word}" conveys: ${entry.meaning.slice(0, 60)}.`,
              translation: '',
            },
          ],
        });
      } else if (entry.meaning.trim().length < 10) {
        findings.push({
          entryId: entry.id,
          currentEntry: entry,
          status: 'MEANING_FIX',
          issueSummary: 'Definition is very short; expand nuance for accurate active recall.',
          verifiedCategoryType: entry.partOfSpeech,
        });
      } else {
        findings.push({
          entryId: entry.id,
          currentEntry: entry,
          status: 'VERIFIED',
          issueSummary: 'Definition, part of speech, and examples verified.',
          verifiedCategoryType: entry.partOfSpeech,
        });
      }
    }
    return findings;
  },

  /**
   * UTC 00:00 Word of the Day resolver (requires >= 10 unique words in that language folder).
   */
  resolveWordOfTheDay(
    decks: VocabularyDeck[],
    language: string,
    statesByLang: Record<string, LanguageWordOfTheDayState>,
    cycleNext: boolean = false,
    nowMillis: number = Date.now()
  ): {
    unlocked: boolean;
    uniqueCount: number;
    entry?: VocabularyEntry;
    sourceDeck?: VocabularyDeck;
    utcDateKey: string;
    cycleNumber: number;
    seenCount: number;
    history: WordOfTheDayHistoryItem[];
    updatedState?: LanguageWordOfTheDayState;
  } {
    const cleanLang = (language || '').trim();
    const normLang = cleanLang.toLowerCase();
    const langDecks = decks.filter((d) => d.language.trim().toLowerCase() === normLang);

    const uniqueByKey = new Map<string, { entry: VocabularyEntry; deck: VocabularyDeck }>();
    for (const d of langDecks) {
      for (const e of d.entries) {
        const k = normalizeWordKey(e.word);
        if (k && !uniqueByKey.has(k)) {
          uniqueByKey.set(k, { entry: e, deck: d });
        }
      }
    }

    const candidates = Array.from(uniqueByKey.entries())
      .map(([wordKey, pair]) => ({ wordKey, ...pair }))
      .sort((a, b) => {
        const rA = VOCAB_LEVELS[a.entry.levelCode]?.sortRank ?? 4;
        const rB = VOCAB_LEVELS[b.entry.levelCode]?.sortRank ?? 4;
        if (rA !== rB) return rA - rB;
        return a.entry.word.localeCompare(b.entry.word);
      });

    const todayUtc = new Date(nowMillis).toISOString().slice(0, 10);

    if (candidates.length < WOTD_MIN_WORDS_THRESHOLD) {
      return {
        unlocked: false,
        uniqueCount: candidates.length,
        utcDateKey: todayUtc,
        cycleNumber: 1,
        seenCount: 0,
        history: statesByLang[normLang]?.history || [],
      };
    }

    const prevState: LanguageWordOfTheDayState = statesByLang[normLang] || {
      languageKey: normLang,
      activeUtcDateKey: '',
      activeWordKey: '',
      seenWordKeysInCycle: [],
      cycleNumber: 1,
      history: [],
    };

    const byKey = new Map(candidates.map((c) => [c.wordKey, c]));
    if (!cycleNext && prevState.activeUtcDateKey === todayUtc && byKey.has(prevState.activeWordKey)) {
      const active = byKey.get(prevState.activeWordKey)!;
      return {
        unlocked: true,
        uniqueCount: candidates.length,
        entry: active.entry,
        sourceDeck: active.deck,
        utcDateKey: todayUtc,
        cycleNumber: prevState.cycleNumber,
        seenCount: prevState.seenWordKeysInCycle.length,
        history: prevState.history,
      };
    }

    let seen = prevState.seenWordKeysInCycle.filter((k) => byKey.has(k));
    let cycleNum = prevState.cycleNumber;
    let nextCandidate = candidates.find((c) => !seen.includes(c.wordKey));

    if (!nextCandidate) {
      seen = [];
      cycleNum += 1;
      nextCandidate = candidates[0];
    }

    const updatedSeen = [...seen, nextCandidate.wordKey];
    const histItem: WordOfTheDayHistoryItem = {
      utcDateKey: todayUtc,
      wordKey: nextCandidate.wordKey,
      word: nextCandidate.entry.word,
      partOfSpeech: nextCandidate.entry.partOfSpeech,
      pronunciation: nextCandidate.entry.pronunciation,
      meaning: nextCandidate.entry.meaning,
      translation: nextCandidate.entry.translation,
      levelCode: nextCandidate.entry.levelCode,
      customLevelLabel: nextCandidate.entry.customLevelLabel,
      sourceDeckId: nextCandidate.deck.id,
      sourceDeckTitle: nextCandidate.deck.title,
      shownAtMillis: nowMillis,
    };

    const nextHistory = [
      histItem,
      ...prevState.history.filter((h) => !(h.utcDateKey === todayUtc && h.wordKey === histItem.wordKey)),
    ].slice(0, 120);

    const updatedState: LanguageWordOfTheDayState = {
      languageKey: normLang,
      activeUtcDateKey: todayUtc,
      activeWordKey: nextCandidate.wordKey,
      seenWordKeysInCycle: updatedSeen,
      cycleNumber: cycleNum,
      history: nextHistory,
    };

    return {
      unlocked: true,
      uniqueCount: candidates.length,
      entry: nextCandidate.entry,
      sourceDeck: nextCandidate.deck,
      utcDateKey: todayUtc,
      cycleNumber: cycleNum,
      seenCount: updatedSeen.length,
      history: nextHistory,
      updatedState,
    };
  },

  computeLanguageStats(
    decks: VocabularyDeck[],
    srsByDeck: Record<string, Record<string, FlashcardSrsState>>
  ): {
    language: string;
    deckCount: number;
    totalWords: number;
    dueCount: number;
    masteredCount: number;
  }[] {
    const grouped = new Map<string, VocabularyDeck[]>();
    for (const d of decks) {
      const lang = d.language.trim() || 'English';
      const list = grouped.get(lang) || [];
      list.push(d);
      grouped.set(lang, list);
    }

    return Array.from(grouped.entries()).map(([language, langDecks]) => {
      let totalWords = 0;
      let dueCount = 0;
      let masteredCount = 0;
      for (const d of langDecks) {
        const srs = srsByDeck[d.id] || {};
        const stats = SpacedRepetitionEngine.computeDeckStats(d.entries, (e) => e.id, srs);
        totalWords += stats.totalCount;
        dueCount += stats.dueCount + stats.newCount;
        masteredCount += stats.masteredCount;
      }
      return {
        language,
        deckCount: langDecks.length,
        totalWords,
        dueCount,
        masteredCount,
      };
    });
  },

  compileVocabularyPrompt(cfg: VocabularyPromptConfig, existingWordsToAvoid: string[] = []): string {
    const lang = cfg.language.trim() || 'English';
    const normVar = VocabularyVariants.normalize(lang, cfg.languageVariant);
    const directive = VocabularyVariants.promptDirective(lang, normVar);
    const lvlMeta = getLevelDescriptor(cfg.levelCode, lang, cfg.customLevelLabel);
    const trans = cfg.translationLanguage.trim();
    const hasTrans = Boolean(trans && trans.toLowerCase() !== 'none');
    const topicLine = cfg.topic.trim() || `High-utility ${cfg.domain} vocabulary`;

    if (cfg.isHeadwordIndexOnly) {
      const tierLabel: Record<VocabularyUtilityTier, string> = {
        DAILY_HIGH_FREQUENCY: 'Daily High-Frequency Practical Words',
        EXAM_EDITORIAL: 'Competitive Exam Editorial Vocabulary',
        LITERARY_PRECISION: 'Literary & Formal Precision',
        GENERAL_BALANCED: 'Balanced High-Yield Mix',
      };
      return [
        `Act as a senior lexicographer curating a non-repetitive Stage-1 Headword Index for ${lang}.`,
        `Generate EXACTLY ${cfg.wordCount} distinct headwords matching level **${lvlMeta.title}**.`,
        `- Domain / Utility Tier: ${tierLabel[cfg.utilityTier]} (${cfg.domain})`,
        `- Topic Focus: ${topicLine}`,
        ...(directive ? [`- Dialect / Standard: ${directive}`] : []),
        '',
        'Output ONLY a strict JSON array of headword objects:',
        `[\n  {\n    "word": "example",\n    "partOfSpeech": "adjective",\n    "level": "${cfg.levelCode === 'MIXED' ? 'B2' : cfg.levelCode}",\n    "utilityReason": "Why this word matters for ${cfg.domain}"\n  }\n]`,
        ...(cfg.excludeAlreadySavedWords && existingWordsToAvoid.length > 0
          ? ['', `STRICTLY EXCLUDE THESE ALREADY SAVED WORDS:\n${existingWordsToAvoid.join(', ')}`]
          : []),
      ].join('\n');
    }

    const lines: string[] = [
      `Act as a master lexicographer and curriculum designer for ${lang}.`,
      `Generate EXACTLY ${cfg.wordCount} distinct vocabulary entries at **${lvlMeta.title}** for: ${topicLine}.`,
      `- Target Language: ${lang}${normVar ? ` (${VocabularyVariants.displayLabel(normVar)})` : ''}`,
      ...(hasTrans ? [`- Bridge Translation Language: ${trans}`] : []),
      ...(directive ? [`- Regional Standard: ${directive}`] : []),
      '',
    ];

    if (cfg.outputFormat === 'Structured Text') {
      lines.push('Output Format (Strict Structured Text, zero markdown fences):');
      lines.push(`1. WORD: <word>`);
      if (cfg.includePronunciation) lines.push('   PRONUNCIATION: <IPA / reading>');
      lines.push('   POS: <noun/verb/adjective/...>');
      lines.push(`   LEVEL: ${cfg.levelCode === 'MIXED' ? 'B2' : cfg.levelCode}`);
      lines.push('   MEANING: <clear definition>');
      if (hasTrans) lines.push(`   TRANSLATION: <${trans} equivalent>`);
      lines.push(`   EXAMPLES: (${cfg.examplesPerWord} contextual sentences separated by bullets)`);
      if (cfg.includeSynonymsAndCollocations) {
        lines.push('   SYNONYMS: word1, word2');
        lines.push('   COLLOCATIONS: phrase1, phrase2');
      }
      if (cfg.includeUsageNote) lines.push('   USAGE NOTE: <exam nuance or trap>');
    } else {
      lines.push('Output ONLY a strict, valid JSON array (no markdown fences):');
      lines.push(`[
  {
    "word": "cogent",${cfg.includePronunciation ? '\n    "pronunciation": "/ˈkəʊ.dʒənt/",' : ''}
    "partOfSpeech": "adjective",
    "level": "${cfg.levelCode === 'MIXED' ? 'C1' : cfg.levelCode}",
    "meaning": "Clear, logical, and convincing in argument.",${hasTrans ? `\n    "translation": "<${trans} meaning>",` : ''}
    "examples": [
      { "context": "Editorial", "sentence": "She presented a cogent case for reform." }
    ]${
      cfg.includeSynonymsAndCollocations
        ? ',\n    "synonyms": ["persuasive", "compelling"],\n    "antonyms": ["vague"],\n    "collocations": ["cogent argument"]'
        : ''
    }${cfg.includeUsageNote ? ',\n    "usageNote": "Formal analytical register."' : ''}
  }
]`);
      lines.push(`Ensure exactly ${cfg.examplesPerWord} example object(s) per word.`);
    }

    if (cfg.excludeAlreadySavedWords && existingWordsToAvoid.length > 0) {
      lines.push('');
      lines.push(`DO NOT REPEAT ANY ALREADY SAVED WORDS: ${existingWordsToAvoid.join(', ')}`);
    }
    if (cfg.customInstruction.trim()) {
      lines.push('');
      lines.push(`USER CUSTOM REQUIREMENT: ${cfg.customInstruction.trim()}`);
    }

    return lines.join('\n');
  },
};

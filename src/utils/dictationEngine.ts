import {
  DEFAULT_DICTATION_CONFIG,
  type DictationConfig,
  type DictationPassageItem,
  type DictationPhrase,
  type DictationPunctuationMode,
  type StudyNoteBlock,
} from '../types/phase4';

const PROTECTED_ABBREVIATIONS: [string, string][] = [
  ['Art.', 'Art\u0001'],
  ['Sec.', 'Sec\u0001'],
  ['Fig.', 'Fig\u0001'],
  ['Eq.', 'Eq\u0001'],
  ['No.', 'No\u0001'],
  ['Vol.', 'Vol\u0001'],
  ['Ch.', 'Ch\u0001'],
  ['Dr.', 'Dr\u0001'],
  ['Mr.', 'Mr\u0001'],
  ['Mrs.', 'Mrs\u0001'],
  ['Ms.', 'Ms\u0001'],
  ['Prof.', 'Prof\u0001'],
  ['vs.', 'vs\u0001'],
  ['etc.', 'etc\u0001'],
  ['e.g.', 'e\u0001g\u0001'],
  ['i.e.', 'i\u0001e\u0001'],
  ['U.S.', 'U\u0001S\u0001'],
  ['U.K.', 'U\u0001K\u0001'],
  ['U.N.', 'U\u0001N\u0001'],
  ['Sr.', 'Sr\u0001'],
  ['Jr.', 'Jr\u0001'],
];

const CLAUSE_CONJUNCTIONS = new Set([
  'and',
  'but',
  'because',
  'therefore',
  'however',
  'although',
  'though',
  'while',
  'whereas',
  'moreover',
  'furthermore',
  'hence',
  'since',
  'which',
  'that',
]);

function clamp(v: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, v));
}

function normalizeConfig(cfg: DictationConfig): DictationConfig {
  const clampedStart = clamp(cfg.startWpm || 35, 25, 120);
  const clampedMax = clamp(cfg.targetMaxWpm || 75, clampedStart, 120);
  return {
    ...DEFAULT_DICTATION_CONFIG,
    ...cfg,
    targetWpm: clamp(cfg.targetWpm || 45, 25, 120),
    speechBurstRate: clamp(cfg.speechBurstRate || 0.96, 0.8, 1.35),
    speechBurstWpmBaseline: clamp(cfg.speechBurstWpmBaseline || 148, 110, 185),
    startWpm: clampedStart,
    targetMaxWpm: clampedMax,
    stepWpm: clamp(cfg.stepWpm || 5, 5, 15),
  };
}

function latexToSpeechFriendly(formulaRaw: string): string {
  const trimmed = formulaRaw.trim();
  if (!trimmed) return '';
  const isComplex =
    trimmed.length > 28 ||
    trimmed.includes('\\sum') ||
    trimmed.includes('\\int') ||
    trimmed.includes('\\lim') ||
    trimmed.includes('\\begin') ||
    trimmed.includes('\\matrix') ||
    (trimmed.includes('\\sqrt{') && trimmed.length > 18);
  if (isComplex) return 'equation shown on screen';

  const readable = trimmed
    .replace(/\\frac\{([^}]+)\}\{([^}]+)\}/g, '$1 over $2')
    .replace(/\\times/g, ' times ')
    .replace(/\\cdot/g, ' dot ')
    .replace(/\\pm/g, ' plus or minus ')
    .replace(/\\leq/g, ' less than or equal to ')
    .replace(/\\geq/g, ' greater than or equal to ')
    .replace(/\\neq/g, ' not equal to ')
    .replace(/\\approx/g, ' approximately ')
    .replace(/\\alpha/g, ' alpha ')
    .replace(/\\beta/g, ' beta ')
    .replace(/\\gamma/g, ' gamma ')
    .replace(/\\theta/g, ' theta ')
    .replace(/\\lambda/g, ' lambda ')
    .replace(/\\mu/g, ' mu ')
    .replace(/\\pi/g, ' pi ')
    .replace(/\\omega/g, ' omega ')
    .replace(/\\Delta/g, ' delta ')
    .replace(/\^2/g, ' squared ')
    .replace(/\^3/g, ' cubed ')
    .replace(/=/g, ' equals ')
    .replace(/[{}]/g, ' ')
    .replace(/\\/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();

  return readable || 'equation shown on screen';
}

function sanitizeForSpeech(
  rawText: string,
  preserveStructuralMarkers: boolean,
  preserveDashes: boolean
): string {
  if (!rawText.trim()) return '';
  let text = rawText.replace(/\r\n/g, '\n');

  text = text.replace(/```[a-zA-Z0-9_-]*\n?/g, ' ').replace(/```/g, ' ');
  text = text.replace(/\$\$([^$]+)\$\$/g, (_, inner) => latexToSpeechFriendly(inner));
  text = text.replace(/\$([^$\n]+)\$/g, (_, inner) => latexToSpeechFriendly(inner));
  text = text.replace(/\\\[([\s\S]*?)\\\]/g, (_, inner) => latexToSpeechFriendly(inner));
  text = text.replace(/\\\(([^)]*)\\\)/g, (_, inner) => latexToSpeechFriendly(inner));
  text = text.replace(/\[([^\]]+)\]\([^)]+\)/g, '$1');

  text = text
    .split('\n')
    .map((line) => {
      const t = line.trim();
      let cleaned = t;
      if (preserveStructuralMarkers && /^#{1,6}\s+/.test(t)) {
        cleaned = `\u0003HD\u0003 ${t.replace(/^#{1,6}\s+/, '')}`;
      } else if (preserveStructuralMarkers && /^[-*•+]\s+/.test(t)) {
        cleaned = `\u0003BL\u0003 ${t.replace(/^[-*•+]\s+/, '')}`;
      } else {
        cleaned = t.replace(/^#{1,6}\s+/, '').replace(/^[-*•+]\s+/, '');
      }
      cleaned = cleaned
        .replace(/^>+\s*/, '')
        .replace(/\*\*/g, '')
        .replace(/__/g, '')
        .replace(/`/g, '')
        .replace(/→/g, ' leads to ')
        .replace(/->/g, ' leads to ');
      return preserveDashes
        ? cleaned.replace(/—/g, ' — ').replace(/–/g, ' – ')
        : cleaned.replace(/—/g, ', ').replace(/–/g, ', ');
    })
    .join('\n');

  return text
    .replace(/[ \t]+/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

function splitIntoSentences(paragraph: string): string[] {
  let prot = paragraph;
  for (const [abbr, marker] of PROTECTED_ABBREVIATIONS) {
    prot = prot.split(abbr).join(marker);
  }
  prot = prot.replace(/(\d)\.(\d)/g, '$1\u0002$2');

  const out: string[] = [];
  let buf = '';
  for (let i = 0; i < prot.length; i++) {
    const ch = prot[i];
    buf += ch;
    if (ch === '.' || ch === '!' || ch === '?' || ch === '।') {
      const next = prot[i + 1];
      if (!next || /\s/.test(next) || next === '"' || next === ')' || next === ']') {
        if (next === '"' || next === ')') {
          buf += next;
          i++;
        }
        const restored = buf.replace(/\u0001/g, '.').replace(/\u0002/g, '.').trim();
        if (restored) out.push(restored);
        buf = '';
      }
    }
  }
  const tail = buf.replace(/\u0001/g, '.').replace(/\u0002/g, '.').trim();
  if (tail) out.push(tail);
  return out;
}

function splitSentenceIntoClauses(sentence: string): string[] {
  const out: string[] = [];
  let buf = '';
  for (let i = 0; i < sentence.length; i++) {
    const ch = sentence[i];
    buf += ch;
    if (ch === ',' || ch === ';' || ch === ':') {
      const prev = sentence[i - 1];
      const next = sentence[i + 1];
      const isNum = prev && next && /\d/.test(prev) && /\d/.test(next);
      if (!isNum && (!next || /\s/.test(next))) {
        const piece = buf.trim();
        if (piece) out.push(piece);
        buf = '';
      }
    }
  }
  const tail = buf.trim();
  if (tail) out.push(tail);
  return out.length > 0 ? out : [sentence];
}

function chunkClauseWords(clause: string): string[] {
  const words = clause.trim().split(/\s+/).filter(Boolean);
  if (words.length <= 5) return [clause.trim()];

  const chunks: string[] = [];
  let buffer: string[] = [];

  for (let idx = 0; idx < words.length; idx++) {
    const w = words[idx];
    const cleanLower = w.toLowerCase().replace(/^[,.;:!?]+|[,.;:!?]+$/g, '');
    if (buffer.length >= 3 && CLAUSE_CONJUNCTIONS.has(cleanLower)) {
      chunks.push(buffer.join(' '));
      buffer = [];
    }
    buffer.push(w);
    const remaining = words.length - 1 - idx;
    if (buffer.length >= 5 || (buffer.length >= 4 && remaining >= 2)) {
      chunks.push(buffer.join(' '));
      buffer = [];
    }
  }

  if (buffer.length > 0) {
    if (buffer.length === 1 && chunks.length > 0 && chunks[chunks.length - 1].split(' ').length <= 5) {
      chunks[chunks.length - 1] = `${chunks[chunks.length - 1]} ${buffer[0]}`;
    } else {
      chunks.push(buffer.join(' '));
    }
  }

  return chunks;
}

function expandInlineVerbatimPunctuation(text: string): string {
  if (!text.trim()) return text;
  let out = text
    .replace(/\.\.\./g, ', ellipsis, ')
    .replace(/…/g, ', ellipsis, ')
    .replace(/\s*[—–]\s*|\s+-\s+|\s*--\s*/g, ', dash, ')
    .replace(/"([^"\n]+)"/g, ', open quote, $1, close quote, ')
    .replace(/“/g, ', open quote, ')
    .replace(/”/g, ', close quote, ')
    .replace(/(^|\s)'([^'\n]+)'(?=\s|[,.;:!?)]|$)/g, '$1, open quote, $2, close quote, ')
    .replace(/\(/g, ', open bracket, ')
    .replace(/\[/g, ', open bracket, ')
    .replace(/\)/g, ', close bracket, ')
    .replace(/\]/g, ', close bracket, ');

  // Slashes except common metric units (km/h, m/s)
  out = out.replace(/(^|\s)([A-Za-z]{3,})\/([A-Za-z]{3,})/g, '$1$2 slash $3');

  return out
    .replace(/\s*,\s*/g, ', ')
    .replace(/(?:^,\s*|,\s*$)/g, '')
    .replace(/(,\s*){2,}/g, ', ')
    .replace(/\s+/g, ' ')
    .trim();
}

function buildSpokenPhrase(
  chunk: string,
  punctuationMode: DictationPunctuationMode,
  isSentenceEnd: boolean,
  isParagraphEnd: boolean,
  isHeadingStart: boolean,
  isBulletStart: boolean
): string {
  const rawTrimmed = chunk.trim();
  const hasEllipsis = rawTrimmed.endsWith('...') || rawTrimmed.endsWith('…');
  const quoteMatch = /^([\s\S]*?)([.!?।]+)("\s*|”\s*|'\s*|\)\s*)$/.exec(rawTrimmed);

  let terminalMark = '';
  let core = rawTrimmed;
  if (hasEllipsis) {
    terminalMark = '...';
    core = rawTrimmed.replace(/(\.\.\.|…)+$/, '').trim();
  } else if (quoteMatch) {
    terminalMark = quoteMatch[2].slice(-1);
    core = (quoteMatch[1] + quoteMatch[3]).trim();
  } else {
    const lastCh = rawTrimmed.slice(-1);
    if ([',', ';', ':', '.', '!', '?', '।'].includes(lastCh)) {
      terminalMark = lastCh;
      core = rawTrimmed.slice(0, -1).trim();
    }
  }

  if (punctuationMode === 'OFF') {
    return core || rawTrimmed;
  }

  const structPrefix =
    punctuationMode === 'FULL_VERBATIM'
      ? isHeadingStart
        ? 'Heading'
        : isBulletStart
        ? 'Bullet point'
        : ''
      : '';

  const expandedBody =
    punctuationMode === 'FULL_VERBATIM'
      ? expandInlineVerbatimPunctuation(core || rawTrimmed)
      : core || rawTrimmed;

  let endCue = '';
  if (hasEllipsis && punctuationMode === 'FULL_VERBATIM') {
    endCue = isParagraphEnd ? 'ellipsis, new paragraph' : 'ellipsis';
  } else if (isParagraphEnd) {
    if (terminalMark === '?') endCue = 'question mark, new paragraph';
    else if (terminalMark === '!') endCue = 'exclamation mark, new paragraph';
    else if (terminalMark === '।') endCue = 'poorna viram, new paragraph';
    else endCue = 'full stop, new paragraph';
  } else if (isSentenceEnd) {
    if (terminalMark === '?') endCue = 'question mark';
    else if (terminalMark === '!') endCue = 'exclamation mark';
    else if (terminalMark === '।') endCue = 'poorna viram';
    else endCue = 'full stop';
  } else if (terminalMark === ',') endCue = 'comma';
  else if (terminalMark === ';') endCue = 'semicolon';
  else if (terminalMark === ':') endCue = 'colon';

  return [structPrefix, expandedBody, endCue]
    .filter(Boolean)
    .join(', ')
    .replace(/\s*,(\s*,)+/g, ',')
    .replace(/\s+/g, ' ')
    .trim();
}

export const DictationEngine = {
  normalizeConfig,

  extractPassages(blocks: StudyNoteBlock[]): DictationPassageItem[] {
    const out: DictationPassageItem[] = [];
    for (const block of blocks) {
      if (block.kind === 'one_liner') {
        const body = [block.leadTerm, block.detail].filter((s) => s.trim()).join('. ');
        if (!body) continue;
        out.push({
          id: `block_${block.blockIndex}`,
          blockIndex: block.blockIndex,
          title: block.leadTerm || `Concept ${block.displayIndex}`,
          badge: 'One-Liner',
          promptQuestion: block.leadTerm,
          rawDictationText: body,
          originalProofreadText: block.detail
            ? `${block.leadTerm} — ${block.detail}`
            : block.leadTerm,
        });
      } else if (block.kind === 'multi_liner_card') {
        const sections: string[] = [];
        if (block.paragraphs.length > 0) sections.push(...block.paragraphs.filter((p) => p.trim()));
        if (block.nestedBullets.length > 0) {
          const bLines = block.nestedBullets
            .map((nb) => nb.text.trim())
            .filter(Boolean)
            .map((t) => `• ${t}`);
          if (bLines.length > 0) sections.push(bLines.join('\n'));
        }
        if (block.correctAnswer) sections.push(`Answer: ${block.correctAnswer}`);
        if (block.keyTakeaway) sections.push(`Key Takeaway: ${block.keyTakeaway}`);
        if (block.formula) sections.push(`Formula: ${block.formula}`);
        const dictateBody = sections.length > 0 ? sections.join('\n\n') : block.title;
        if (!dictateBody.trim()) continue;
        const proofread = [block.title, ...sections].filter(Boolean).join('\n\n');
        out.push({
          id: `block_${block.blockIndex}`,
          blockIndex: block.blockIndex,
          title: block.title || `Card ${block.displayIndex}`,
          badge: block.badge || block.questionType || 'Multi-Liner',
          promptQuestion: block.title,
          rawDictationText: dictateBody,
          originalProofreadText: proofread,
        });
      } else if (block.kind === 'callout') {
        const body = [block.label, block.body].filter((s) => s.trim()).join('. ');
        if (!body) continue;
        out.push({
          id: `block_${block.blockIndex}`,
          blockIndex: block.blockIndex,
          title: block.label || 'Key Note',
          badge: 'Callout',
          promptQuestion: block.label,
          rawDictationText: body,
          originalProofreadText: block.label ? `${block.label}: ${block.body}` : block.body,
        });
      } else if (block.kind === 'paragraph') {
        const clean = block.text.trim();
        if (!clean) continue;
        out.push({
          id: `block_${block.blockIndex}`,
          blockIndex: block.blockIndex,
          title: clean.split('\n')[0].slice(0, 60) || 'Passage',
          badge: 'Passage',
          promptQuestion: '',
          rawDictationText: clean,
          originalProofreadText: clean,
        });
      } else if (block.kind === 'table') {
        const lines = block.rows
          .map((row) =>
            row
              .map((cell, idx) => {
                const header = (block.headers[idx] || '').trim();
                return header && cell.trim() ? `${header}: ${cell.trim()}` : cell.trim();
              })
              .filter(Boolean)
              .join(', ')
          )
          .filter(Boolean);
        if (lines.length === 0) continue;
        const joined = lines.join('\n');
        out.push({
          id: `block_${block.blockIndex}`,
          blockIndex: block.blockIndex,
          title: block.headers[0]?.trim() || 'Comparison Table',
          badge: 'Table',
          promptQuestion: block.headers.join(' | '),
          rawDictationText: joined,
          originalProofreadText: joined,
        });
      }
    }
    return out;
  },

  splitPassageIntoPhrases(
    rawText: string,
    punctuationMode: DictationPunctuationMode = 'OFF'
  ): DictationPhrase[] {
    const isVerbatim = punctuationMode === 'FULL_VERBATIM';
    const sanitized = sanitizeForSpeech(rawText, isVerbatim, isVerbatim);
    if (!sanitized) return [];

    const rawParagraphs = sanitized
      .split(/\n\s*\n+/)
      .map((p) => p.replace(/([.!?।])\n+/g, '$1 ').replace(/\n/g, '. ').trim())
      .filter(Boolean);

    const phrases: DictationPhrase[] = [];
    let globalPhraseIdx = 0;
    let globalSentenceIdx = 0;

    rawParagraphs.forEach((paragraph, paraIdx) => {
      const sentences = splitIntoSentences(paragraph);
      sentences.forEach((sentence, sentLocalIdx) => {
        const isLastSentenceInPara = sentLocalIdx === sentences.length - 1;
        const clauses = splitSentenceIntoClauses(sentence);

        clauses.forEach((clause, clauseIdx) => {
          const isLastClauseInSentence = clauseIdx === clauses.length - 1;
          const chunks = chunkClauseWords(clause);

          chunks.forEach((chunk, chunkIdx) => {
            const isLastChunkInClause = chunkIdx === chunks.length - 1;
            const isSentenceEnd = isLastClauseInSentence && isLastChunkInClause;
            const isParagraphEnd = isLastSentenceInPara && isSentenceEnd;

            const trimmedChunk = chunk.trim();
            const isHeadingStart = trimmedChunk.startsWith('\u0003HD\u0003');
            const isBulletStart = trimmedChunk.startsWith('\u0003BL\u0003');
            const displayClean = trimmedChunk
              .replace(/\u0003HD\u0003/g, '')
              .replace(/\u0003BL\u0003/g, '')
              .trim();

            const wordTokens = displayClean
              .split(/\s+/)
              .map((w) => w.replace(/^[,.;:!?।"'()[\]—–]+|[,.;:!?।"'()[\]—–]+$/g, ''))
              .filter(Boolean);
            const wordCount = Math.max(1, wordTokens.length);
            const charCount = Math.max(
              wordCount,
              wordTokens.reduce((acc, w) => acc + w.length, 0)
            );

            const spokenText = buildSpokenPhrase(
              displayClean,
              punctuationMode,
              isSentenceEnd,
              isParagraphEnd,
              isHeadingStart,
              isBulletStart
            );

            phrases.push({
              index: globalPhraseIdx++,
              sentenceIndex: globalSentenceIdx,
              paragraphIndex: paraIdx,
              displayText: displayClean,
              spokenText,
              wordCount,
              charCount,
              isSentenceEnd,
              isParagraphEnd,
            });
          });
        });
        globalSentenceIdx++;
      });
    });

    return phrases;
  },

  computeWritingPauseMs(phrase: DictationPhrase, cfg: DictationConfig): number {
    const safe = normalizeConfig(cfg);
    const normalizedCharWords = phrase.charCount / 5.0;
    const effectiveWordUnits = phrase.wordCount * 0.6 + normalizedCharWords * 0.4;
    const totalBudgetMs = (effectiveWordUnits / safe.targetWpm) * 60_000;
    const effectiveBurstWpm = Math.max(90, safe.speechBurstWpmBaseline * safe.speechBurstRate);
    const spokenWords = Math.max(1, phrase.spokenText.split(/\s+/).filter(Boolean).length);
    const estimatedSpeechMs = (spokenWords / effectiveBurstWpm) * 60_000;

    const cadenceTailMs = phrase.isParagraphEnd ? 650 : phrase.isSentenceEnd ? 320 : 0;
    const verbatimAllowance = safe.punctuationMode === 'FULL_VERBATIM' ? 240 : 0;
    const minPauseMs = safe.punctuationMode === 'FULL_VERBATIM' ? 480 : 300;
    const netMs = totalBudgetMs - estimatedSpeechMs + cadenceTailMs + verbatimAllowance;
    return clamp(Math.round(netMs), minPauseMs, 20_000);
  },

  computeNextElevatedWpm(currentWpm: number, cfg: DictationConfig): number {
    const safe = normalizeConfig(cfg);
    if (!safe.elevatingEnabled) return clamp(currentWpm, 25, 120);
    return clamp(currentWpm + safe.stepWpm, safe.startWpm, safe.targetMaxWpm);
  },
};

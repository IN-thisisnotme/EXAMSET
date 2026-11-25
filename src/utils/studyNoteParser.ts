import type { QuestionPaper } from '../types/exam';
import { AnswerKeyMatcher } from './answerKeyMatcher';
import { LatexHelper } from './latexHelper';
import { RichTextHelper } from './richTextHelper';
import {
  type FlashcardPromptConfig,
  type FlashcardPromptStyle,
  KNOWLEDGE_COLLECTION_META,
  type KnowledgeCollectionType,
  type KnowledgeDepth,
  type KnowledgeFormatRule,
  type KnowledgeLayerOrder,
  type KnowledgePromptConfig,
  type KnowledgeTransformAction,
  type StudyNoteBlock,
  type StudyNoteRecord,
} from '../types/phase4';

function escapeHtml(str: string): string {
  return (str || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function cleanMarkdownDecorators(text: string): string {
  return (text || '')
    .replace(/\*\*\*(.+?)\*\*\*/g, '$1')
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/__(.+?)__/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .trim();
}

function normalizeDelimiters(text: string): string {
  return (text || '')
    .replace(/\r\n/g, '\n')
    .replace(/\r/g, '\n')
    .replace(/\\\((.+?)\\\)/gs, (_, inner) => `$${inner}$`);
}

function leadingSpaces(line: string): number {
  let count = 0;
  for (const ch of line) {
    if (ch === ' ') count++;
    else if (ch === '\t') count += 2;
    else break;
  }
  return count;
}

function countIndentLevel(line: string): number {
  return Math.max(1, Math.min(3, Math.floor(leadingSpaces(line) / 2)));
}

function findNextNonEmptyLine(lines: string[], startIdx: number): number {
  for (let idx = startIdx; idx < lines.length; idx++) {
    if (lines[idx].trim().length > 0) return idx;
  }
  return -1;
}

function isDisplayMathStart(trimmed: string): boolean {
  return trimmed.startsWith('$$') || trimmed.startsWith('\\[');
}

function collectDisplayMathBlock(lines: string[], startIdx: number): { block: string; nextIdx: number } {
  const firstTrim = lines[startIdx].trim();
  const isDollar = firstTrim.startsWith('$$');
  const endDelim = isDollar ? '$$' : '\\]';
  if (firstTrim.length > 2 && firstTrim.endsWith(endDelim)) {
    return { block: firstTrim, nextIdx: startIdx + 1 };
  }
  const buf = [firstTrim];
  let j = startIdx + 1;
  while (j < lines.length) {
    const lineTrim = lines[j].trim();
    buf.push(lineTrim);
    if (lineTrim.endsWith(endDelim) || lineTrim.includes(endDelim)) {
      j++;
      break;
    }
    j++;
  }
  return { block: buf.join('\n'), nextIdx: j };
}

function isStructuralBoundary(trimmed: string): boolean {
  if (
    trimmed.startsWith('#') ||
    trimmed.startsWith('|') ||
    trimmed.startsWith('>') ||
    trimmed === '---' ||
    trimmed === '***' ||
    trimmed.startsWith('```')
  ) {
    return true;
  }
  if (/^(?:Q(?:\d+)?|Question)\s*[:.-]\s*/i.test(trimmed)) return true;
  if (
    /^(?:TOPIC|SECTION|UNIT|CHAPTER|PART|MODULE)\s*[\dIVXA-Z]*\s*[:-]\s*.+/i.test(trimmed) &&
    trimmed.length <= 120
  ) {
    return true;
  }
  return false;
}

function orientPair(
  left: string,
  right: string,
  layerOrder: KnowledgeLayerOrder,
  explicitBoldLeft: boolean
): { lead: string; detail: string; hasRecallTarget: boolean; isReversed: boolean } {
  if (layerOrder === 'BACKWARDS_ANSWER_FIRST') {
    if (right.length <= 68 && (left.length > right.length || !explicitBoldLeft)) {
      return { lead: right, detail: left, hasRecallTarget: true, isReversed: true };
    }
    return { lead: left, detail: right, hasRecallTarget: true, isReversed: false };
  }
  if (layerOrder === 'AUTO_DETECT') {
    const autoReversed =
      !explicitBoldLeft && left.length > 52 && right.length >= 2 && right.length <= 42 && !right.endsWith('.');
    if (autoReversed) {
      return { lead: right, detail: left, hasRecallTarget: true, isReversed: true };
    }
    return { lead: left, detail: right, hasRecallTarget: true, isReversed: false };
  }
  return { lead: left, detail: right, hasRecallTarget: true, isReversed: false };
}

function splitDirectKeyValue(
  line: string,
  layerOrder: KnowledgeLayerOrder
): { lead: string; detail: string; hasRecallTarget: boolean; isReversed: boolean } | null {
  const cleaned = line.trim();
  const separators = [' -> ', ' => ', ' — ', ' – ', ': ', ' - '];
  for (const sep of separators) {
    const idx = cleaned.indexOf(sep);
    if (idx >= 2 && idx < cleaned.length - sep.length) {
      const left = cleanMarkdownDecorators(cleaned.slice(0, idx));
      const right = cleanMarkdownDecorators(cleaned.slice(idx + sep.length));
      if (left && right) {
        const looksReversed = left.length > 55 && right.length >= 2 && right.length <= 48;
        if (left.length <= 80 || looksReversed || layerOrder === 'BACKWARDS_ANSWER_FIRST') {
          return orientPair(left, right, layerOrder, false);
        }
      }
    }
  }
  return null;
}

function splitLeadAndDetail(
  content: string,
  layerOrder: KnowledgeLayerOrder
): { lead: string; detail: string; hasRecallTarget: boolean; isReversed: boolean } {
  const boldMatch = /^\*\*(.+?)\*\*\s*(?:[:-–—=]|->|=>)?\s*(.*)$/.exec(content);
  if (boldMatch && boldMatch[2].trim()) {
    const left = cleanMarkdownDecorators(boldMatch[1]);
    const right = cleanMarkdownDecorators(boldMatch[2]);
    return orientPair(left, right, layerOrder, true);
  }
  const trailingBold = /^(.+?)\s*(?:->|=>|[:-–—])\s*\*\*(.+?)\*\*$/.exec(content);
  if (trailingBold) {
    const left = cleanMarkdownDecorators(trailingBold[1]);
    const right = cleanMarkdownDecorators(trailingBold[2]);
    if (right.length <= 64 && left.length > right.length) {
      return { lead: right, detail: left, hasRecallTarget: true, isReversed: true };
    }
  }
  const direct = splitDirectKeyValue(content, layerOrder);
  if (direct) return direct;
  return {
    lead: '',
    detail: cleanMarkdownDecorators(content),
    hasRecallTarget: false,
    isReversed: false,
  };
}

function isQaBlockBoundary(trimmed: string, indent: number, layerOrder: KnowledgeLayerOrder): boolean {
  if (isStructuralBoundary(trimmed)) return true;
  if (indent === 0 && /^\d{1,4}[.)]\s+/.test(trimmed)) {
    const stripped = trimmed.replace(/^\d{1,4}[.)]\s+/, '');
    if (splitDirectKeyValue(stripped, layerOrder) !== null) return true;
    if (/^\d{1,4}[.)]\s+\*\*.+?\*\*/.test(trimmed)) return true;
  }
  return false;
}

function matchTagSubLine(trimmed: string): RegExpExecArray | null {
  return /^(?:\*\*|__)?(?:Formula|Equation|Example|E\.g\.|Note|Why|Trick|Mnemonic|Hint|Memory Hook|Exam Trap|Pitfall|Trap|Application|Rule|Cloze|Answer|Correct Answer|Key Takeaway|Explanation|Solution|Status|Type|Question Type|Concept|Takeaway|Introduction|Body|Conclusion|Way Forward|Arguments|Challenges|Significance|Case Study|Keywords|Data|Committee|Criticism|Analysis|Derivation|Proof)(?:\*\*|__)?\s*[:-]\s*(.+)$/i.exec(
    trimmed
  );
}

function looksLikeFormula(text: string): boolean {
  const t = text.trim();
  if (!t) return false;
  const isPureMath =
    (t.startsWith('$$') && t.endsWith('$$')) ||
    (t.startsWith('\\[') && t.endsWith('\\]')) ||
    (t.startsWith('$') && t.endsWith('$') && t.split('$').length === 3);
  if (isPureMath) return true;
  return t.includes('=') && t.length <= 90 && !t.includes('http') && !t.includes('. ');
}

function appendSectionText(existing: string, addition: string): string {
  const add = addition.trim();
  if (!add) return existing;
  return existing ? `${existing}\n\n${add}` : add;
}

function stripAiChatter(text: string): string {
  return text
    .split('\n')
    .filter((line) => {
      const t = line.trim().toLowerCase();
      if (!t) return true;
      if (/^(here (is|are)|sure[!,.]|certainly[!,.]|below (is|are)|hope this helps|let me know)/i.test(t)) {
        return false;
      }
      if (t.startsWith('```')) return false;
      return true;
    })
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

function fixBackwardsChatGpt(text: string): string {
  const cleaned = stripAiChatter(text);
  return cleaned
    .split('\n')
    .map((raw) => {
      const indent = raw.slice(0, raw.length - raw.trimStart().length);
      const t = raw.trim();
      if (!t) return '';
      const prefixM = /^((?:\d+[.)]|[-*•‣▪])\s+)(.+)$/.exec(t);
      const prefix = prefixM ? prefixM[1] : '';
      const body = prefixM ? prefixM[2] : t;
      const normPrefix = prefix && !/^\d/.test(prefix) ? '- ' : prefix;
      const split = splitLeadAndDetail(body, 'BACKWARDS_ANSWER_FIRST');
      if (split.hasRecallTarget && split.lead && split.detail) {
        return `${indent}${normPrefix}**${split.lead}** -> ${split.detail}`;
      }
      return `${indent}${normPrefix}${body}`;
    })
    .join('\n')
    .trim();
}

function swapLeadDetail(text: string): string {
  return text
    .split('\n')
    .map((raw) => {
      const indent = raw.slice(0, raw.length - raw.trimStart().length);
      const t = raw.trim();
      const prefixM = /^((?:\d+[.)]|[-*•‣▪])\s+)(.+)$/.exec(t);
      const prefix = prefixM ? prefixM[1] : '';
      const body = prefixM ? prefixM[2] : t;
      const pair = splitDirectKeyValue(body, 'TERM_FIRST');
      if (pair && pair.lead && pair.detail) {
        return `${indent}${prefix}**${pair.detail}** -> ${pair.lead}`;
      }
      return raw;
    })
    .join('\n');
}

function mergeNestedBullets(text: string): string {
  const out: string[] = [];
  let insideNumbered = false;
  for (const raw of text.split('\n')) {
    const t = raw.trim();
    if (!t) {
      insideNumbered = false;
      out.push('');
      continue;
    }
    if (t.startsWith('#') || t.startsWith('>') || t.startsWith('|')) {
      insideNumbered = false;
      out.push(t);
    } else if (/^\d+[.)]\s+.+/.test(t)) {
      insideNumbered = true;
      out.push(t);
    } else if (insideNumbered && /^[-*•‣▪]\s+.+/.test(t)) {
      out.push(`  - ${t.slice(1).trim()}`);
    } else if (insideNumbered && raw.startsWith('  ')) {
      out.push(raw);
    } else if (insideNumbered && !t.includes('->')) {
      out.push(`  - ${t}`);
    } else {
      insideNumbered = false;
      out.push(t);
    }
  }
  return out.join('\n').trim();
}

function splitSemicolons(text: string): string {
  const out: string[] = [];
  for (const raw of text.split('\n')) {
    const t = raw.trim();
    if (t.includes(';') && !t.startsWith('|')) {
      const prefixM = /^((?:\d+[.)]|[-*•‣▪])\s+)(.+)$/.exec(t);
      const prefix = prefixM ? prefixM[1] : '1. ';
      const body = prefixM ? prefixM[2] : t;
      const parts = body.split(';').map((s) => s.trim()).filter(Boolean);
      if (parts.length >= 2) {
        const firstSplit = splitDirectKeyValue(parts[0], 'AUTO_DETECT');
        if (firstSplit) {
          out.push(`${prefix}**${firstSplit.lead}** -> ${firstSplit.detail}`);
        } else {
          out.push(`${prefix}${parts[0]}`);
        }
        for (const sub of parts.slice(1)) {
          out.push(`  - ${sub}`);
        }
      } else {
        out.push(raw);
      }
    } else {
      out.push(raw);
    }
  }
  return out.join('\n').trim();
}

function formatRichHtml(raw: string): string {
  if (!raw.trim()) return '';
  const withLatex = LatexHelper.hasLatex(raw) ? LatexHelper.toRichTextString(raw) : raw;
  const tokens = RichTextHelper.tokenize(withLatex);
  return tokens
    .map((t) => {
      let html = RichTextHelper.escapeHtml(t.text).replace(/\n/g, '<br/>');
      if (t.code) html = `<code>${html}</code>`;
      if (t.bold) html = `<strong>${html}</strong>`;
      if (t.italic) html = `<em>${html}</em>`;
      if (t.sub) html = `<sub>${html}</sub>`;
      if (t.sup) html = `<sup>${html}</sup>`;
      return html;
    })
    .join('');
}

export const StudyNoteParser = {
  detectSourceHint(rawText: string): string {
    const sample = rawText.slice(0, 800).toLowerCase();
    if (sample.includes('|') && sample.includes('---')) return 'Structured Matrix';
    if (/^\s{2,}[-*•\d]/m.test(rawText)) return 'Multi-Liner Hierarchy';
    if (/^\s*(?:\d+[.)]|[-*•])\s+/m.test(rawText)) return 'One-Liners / Bullets';
    if (rawText.includes('## ') || rawText.includes('**')) return 'Markdown Knowledge';
    return 'Knowledge Set';
  },

  applyTransform(rawText: string, action: KnowledgeTransformAction): string {
    switch (action) {
      case 'FIX_BACKWARDS_CHATGPT':
        return fixBackwardsChatGpt(rawText);
      case 'SWAP_LEAD_DETAIL':
        return swapLeadDetail(rawText);
      case 'MERGE_NESTED_BULLETS':
        return mergeNestedBullets(rawText);
      case 'STRIP_AI_CHATTER':
        return stripAiChatter(rawText);
      case 'SPLIT_SEMICOLONS':
        return splitSemicolons(rawText);
      default:
        return rawText;
    }
  },

  countStudyItems(rawText: string, rule: KnowledgeFormatRule): number {
    return this.parseBlocks(rawText, rule).filter(
      (b) => b.kind === 'one_liner' || b.kind === 'multi_liner_card'
    ).length;
  },

  parseBlocks(rawText: string, rule: KnowledgeFormatRule): StudyNoteBlock[] {
    const normalized = normalizeDelimiters(rawText).trim();
    if (!normalized) return [];

    const lines = normalized.split('\n');
    const blocks: StudyNoteBlock[] = [];
    let blockCounter = 0;
    let factSeq = 1;
    let i = 0;

    while (i < lines.length) {
      const rawLine = lines[i];
      const trimmed = rawLine.trim();
      if (!trimmed || trimmed === '---' || trimmed === '***' || trimmed.startsWith('```')) {
        i++;
        continue;
      }

      // 1. Markdown Headings
      const mdHead = /^(#{1,4})\s+(.+)$/.exec(trimmed);
      if (mdHead) {
        blocks.push({
          kind: 'heading',
          blockIndex: blockCounter++,
          level: mdHead[1].length,
          text: cleanMarkdownDecorators(mdHead[2]),
        });
        i++;
        continue;
      }

      // 2. Standalone Topic Headings
      if (
        /^(?:TOPIC|SECTION|UNIT|CHAPTER|PART|MODULE)\s*[\dIVXA-Z]*\s*[:-]\s*.+/i.test(trimmed) &&
        trimmed.length <= 120
      ) {
        blocks.push({
          kind: 'heading',
          blockIndex: blockCounter++,
          level: 2,
          text: cleanMarkdownDecorators(trimmed),
        });
        i++;
        continue;
      }

      // 3. Markdown Table
      if (
        trimmed.startsWith('|') &&
        trimmed.split('|').length >= 3 &&
        i + 1 < lines.length &&
        lines[i + 1].trim().includes('|')
      ) {
        const tableLines: string[] = [];
        let j = i;
        while (j < lines.length && lines[j].trim().includes('|')) {
          tableLines.push(lines[j].trim());
          j++;
        }
        const parsedRows = tableLines
          .filter((ln) => !/^\|?\s*[:-]+(\s*\|\s*[:-]+)+\s*\|?$/.test(ln))
          .map((row) =>
            row
              .replace(/^\|+|\|+$/g, '')
              .split('|')
              .map((c) => cleanMarkdownDecorators(c.trim()))
          )
          .filter((cells) => cells.some((c) => c.length > 0));

        if (parsedRows.length >= 2) {
          blocks.push({
            kind: 'table',
            blockIndex: blockCounter++,
            headers: parsedRows[0],
            rows: parsedRows.slice(1),
          });
          i = j;
          continue;
        }
      }

      // 4. Callout / Blockquote
      if (trimmed.startsWith('>')) {
        const calloutLines: string[] = [];
        let j = i;
        while (j < lines.length && lines[j].trim().startsWith('>')) {
          calloutLines.push(lines[j].trim().replace(/^>+/, '').trim());
          j++;
        }
        const joined = calloutLines.join('\n');
        const colonIdx = joined.indexOf(':');
        const prefixClean =
          colonIdx > 0 ? cleanMarkdownDecorators(joined.slice(0, colonIdx)) : '';
        const hasLabel = colonIdx > 0 && prefixClean.length > 0 && prefixClean.length <= 28;
        blocks.push({
          kind: 'callout',
          blockIndex: blockCounter++,
          label: hasLabel ? prefixClean : 'Key Insight',
          body: hasLabel
            ? joined.slice(colonIdx + 1).replace(/^\s*[*_]{1,3}\s*/, '').trim()
            : joined.trim(),
        });
        i = j;
        continue;
      }

      // 5. Q: / A: Pair
      const qMatch = /^(?:Q(?:\d+)?|Question)\s*[:.-]\s*(.+)$/i.exec(trimmed);
      if (qMatch) {
        const qText = cleanMarkdownDecorators(qMatch[1]);
        const paragraphs: string[] = [];
        const nested: { level: number; text: string }[] = [];
        let formula = '';
        let j = i + 1;

        while (j < lines.length) {
          const nextRaw = lines[j];
          const nextTrim = nextRaw.trim();
          if (!nextTrim) {
            const nxt = findNextNonEmptyLine(lines, j + 1);
            if (nxt === -1) break;
            if (isQaBlockBoundary(lines[nxt].trim(), leadingSpaces(lines[nxt]), rule.layerOrder)) break;
            j = nxt;
            continue;
          }
          if (isQaBlockBoundary(nextTrim, leadingSpaces(nextRaw), rule.layerOrder)) break;

          if (isDisplayMathStart(nextTrim)) {
            const { block, nextIdx } = collectDisplayMathBlock(lines, j);
            if (!formula) formula = block;
            else paragraphs.push(block);
            j = nextIdx;
            continue;
          }

          const aMatch = /^(?:A(?:ns(?:wer)?)?|Explanation|Detail)\s*[:.-]\s*(.+)$/i.exec(nextTrim);
          const qaBullet = /^[-*•‣▪]\s+(.+)$/.exec(nextTrim);
          if (aMatch) {
            paragraphs.push(aMatch[1].trim());
          } else if (qaBullet) {
            const bBody = qaBullet[1].trim();
            const fBullet = /^(?:\*\*|__)?(?:Formula|Equation)(?:\*\*|__)?\s*[:-]\s*(.+)$/i.exec(bBody);
            if (fBullet && !formula) formula = cleanMarkdownDecorators(fBullet[1]);
            else nested.push({ level: countIndentLevel(nextRaw), text: bBody });
          } else {
            if (looksLikeFormula(nextTrim) && !formula) formula = nextTrim;
            else paragraphs.push(nextTrim);
          }
          j++;
        }

        if (paragraphs.length > 0 || nested.length > 0 || formula) {
          blocks.push({
            kind: 'multi_liner_card',
            blockIndex: blockCounter++,
            displayIndex: String(factSeq++),
            title: qText,
            badge: rule.collectionType === 'LONG_ANSWER' ? 'Long Q&A' : 'Q & A',
            paragraphs,
            nestedBullets: nested,
            formula,
            isReversed: false,
            isQuestionCard: false,
            questionType: rule.collectionType === 'LONG_ANSWER' ? 'Long-Form Q&A' : 'Q & A',
            statusTag: '',
            candidateAnswer: '',
            optionsList: [],
            correctAnswer: '',
            keyTakeaway: '',
          });
          i = j;
          continue;
        }
      }

      // 6. Numbered or top-level bullet item
      const listMatch = /^(?:(\d{1,4})[.)]|[-*•‣▪])\s+(.+)$/.exec(trimmed);
      if (listMatch) {
        const baseIndent = leadingSpaces(rawLine);
        const displayIdx = listMatch[1] || String(factSeq);
        factSeq++;
        const content = listMatch[2].trim();

        const nestedBullets: { level: number; text: string }[] = [];
        const extraParagraphs: string[] = [];
        let formula = '';
        let parsedQuestionType = '';
        let parsedStatusTag = '';
        let parsedCandidateAnswer = '';
        const parsedOptions: { label: string; text: string }[] = [];
        let parsedCorrectAnswer = '';
        let parsedKeyTakeaway = '';
        let j = i + 1;

        if (rule.preserveNestedBullets || rule.collectionType === 'LONG_ANSWER') {
          while (j < lines.length) {
            const subRaw = lines[j];
            const subTrim = subRaw.trim();
            if (!subTrim) {
              const nxt = findNextNonEmptyLine(lines, j + 1);
              if (nxt === -1) break;
              const lookRaw = lines[nxt];
              const lookTrim = lookRaw.trim();
              const lookIndent = leadingSpaces(lookRaw);
              if (isStructuralBoundary(lookTrim)) break;
              const isSiblingList = lookIndent <= baseIndent && /^(?:\d{1,4}[.)]|[-*•‣▪])\s+/.test(lookTrim);
              const isNextKV = lookIndent <= baseIndent && splitDirectKeyValue(lookTrim, rule.layerOrder) !== null;
              if (isSiblingList || isNextKV) break;
              const isLongCont =
                lookIndent > baseIndent ||
                isDisplayMathStart(lookTrim) ||
                matchTagSubLine(lookTrim) !== null ||
                rule.collectionType === 'LONG_ANSWER' ||
                rule.collectionType === 'CONCEPT_CODEX';
              if (!isLongCont) break;
              j = nxt;
              continue;
            }
            if (isStructuralBoundary(subTrim)) break;

            if (isDisplayMathStart(subTrim)) {
              const { block, nextIdx } = collectDisplayMathBlock(lines, j);
              if (!formula) formula = block;
              else extraParagraphs.push(block);
              j = nextIdx;
              continue;
            }

            const subIndent = leadingSpaces(subRaw);
            const subBullet = /^(?:[-*•‣▪◦]|\d+[.)]|[a-zA-Z][.)])\s+(.+)$/.exec(subTrim);
            const isIndentedMore = subIndent > baseIndent;
            const isTagSubLine = matchTagSubLine(subTrim);

            if (isIndentedMore && subBullet) {
              const level = Math.max(1, Math.min(3, Math.floor((subIndent - baseIndent) / 2)));
              const bulletRaw = subBullet[1].trim();
              const bulletClean = cleanMarkdownDecorators(bulletRaw);
              const optMatch = /^\(([A-Ea-e])\)\s+(.+)$/.exec(bulletClean);
              const qMeta = /^(Question Type|Type|Status|Correct Answer|Key Takeaway|Explanation|Solution|Formula|Equation)\s*:\s*(.+)$/i.exec(
                bulletClean
              );

              if (optMatch && level === 1) {
                parsedOptions.push({ label: optMatch[1].toUpperCase(), text: optMatch[2].trim() });
              } else if (qMeta) {
                const key = qMeta[1].trim().toLowerCase();
                const cleanVal = qMeta[2].trim();
                const rawBody = bulletRaw.slice(bulletRaw.indexOf(':') + 1).trim();
                if (key === 'type' || key === 'question type') parsedQuestionType = cleanVal;
                else if (key === 'status') {
                  parsedStatusTag = cleanVal;
                  const mk = /Marked\s+([A-Ea-e0-9-]+)/i.exec(cleanVal);
                  if (mk) parsedCandidateAnswer = mk[1].toUpperCase();
                } else if (key === 'correct answer') parsedCorrectAnswer = cleanVal;
                else if (key === 'key takeaway' || key === 'explanation' || key === 'solution') {
                  parsedKeyTakeaway = appendSectionText(parsedKeyTakeaway, rawBody);
                } else if (key === 'formula' || key === 'equation') {
                  formula = cleanVal;
                }
              } else if (looksLikeFormula(bulletClean) && !formula) {
                formula = bulletClean;
              } else {
                nestedBullets.push({ level, text: bulletRaw });
              }
              j++;
            } else if (isTagSubLine) {
              const tag = cleanMarkdownDecorators(subTrim.split(':')[0].split('-')[0]);
              const body = isTagSubLine[1].trim();
              const tLow = tag.toLowerCase();
              if (tLow === 'formula' || tLow === 'equation') formula = cleanMarkdownDecorators(body);
              else if (tLow === 'type' || tLow === 'question type') parsedQuestionType = cleanMarkdownDecorators(body);
              else if (tLow === 'status') parsedStatusTag = cleanMarkdownDecorators(body);
              else if (tLow === 'correct answer' || tLow === 'answer') parsedCorrectAnswer = cleanMarkdownDecorators(body);
              else if (tLow === 'key takeaway' || tLow === 'explanation' || tLow === 'solution') {
                parsedKeyTakeaway = appendSectionText(parsedKeyTakeaway, body);
              } else {
                nestedBullets.push({ level: 1, text: `**${tag}:** ${body}` });
              }
              j++;
            } else if (
              isIndentedMore ||
              ((rule.collectionType === 'LONG_ANSWER' || rule.collectionType === 'CONCEPT_CODEX') &&
                !/^(?:\d{1,4}[.)]|[-*•‣▪])\s+/.test(subTrim) &&
                splitDirectKeyValue(subTrim, rule.layerOrder) === null)
            ) {
              if (looksLikeFormula(subTrim) && !formula) formula = subTrim;
              else extraParagraphs.push(subTrim);
              j++;
            } else {
              break;
            }
          }
        }

        const cleanedFull = cleanMarkdownDecorators(content);
        const qPrefixMatch = /^(Q\d+)\s*[:.]\s*(.+)$/i.exec(cleanedFull);
        const isQuestionCard =
          qPrefixMatch !== null ||
          Boolean(parsedStatusTag) ||
          Boolean(parsedCorrectAnswer) ||
          parsedOptions.length > 0 ||
          (Boolean(parsedKeyTakeaway) && rule.collectionType !== 'LONG_ANSWER');

        if (isQuestionCard) {
          const badge = qPrefixMatch ? qPrefixMatch[1].toUpperCase() : `Q${displayIdx}`;
          const stem = qPrefixMatch ? qPrefixMatch[2].trim() : cleanedFull;
          const qType = parsedQuestionType || (parsedOptions.length > 0 ? 'MCQ Review' : 'Remediation');
          blocks.push({
            kind: 'multi_liner_card',
            blockIndex: blockCounter++,
            displayIndex: badge,
            title: stem,
            badge: qType,
            paragraphs: Array.from(new Set(extraParagraphs)),
            nestedBullets,
            formula,
            isReversed: false,
            isQuestionCard: true,
            questionType: qType,
            statusTag: parsedStatusTag,
            candidateAnswer: parsedCandidateAnswer,
            optionsList: parsedOptions,
            correctAnswer: parsedCorrectAnswer,
            keyTakeaway: parsedKeyTakeaway,
          });
          i = j;
          continue;
        }

        const split = splitLeadAndDetail(content, rule.layerOrder);
        const useMulti =
          nestedBullets.length > 0 ||
          extraParagraphs.length > 0 ||
          Boolean(formula) ||
          split.detail.length > 155 ||
          rule.collectionType === 'MULTI_LINER' ||
          rule.collectionType === 'CONCEPT_CODEX' ||
          rule.collectionType === 'LONG_ANSWER';

        if (useMulti) {
          const cardTitle =
            split.lead ||
            (split.detail.length > 88 ? `${split.detail.slice(0, 88)}…` : split.detail);
          const cardParagraphs: string[] = [];
          if (split.lead && split.detail) cardParagraphs.push(split.detail);
          else if (!split.lead && split.detail && (split.detail.length > 88 || (extraParagraphs.length === 0 && nestedBullets.length === 0))) {
            cardParagraphs.push(split.detail);
          }
          cardParagraphs.push(...extraParagraphs);

          const resolvedType =
            parsedQuestionType ||
            (rule.collectionType === 'LONG_ANSWER'
              ? 'Long-Form Q&A'
              : rule.collectionType === 'FLASHCARDS'
              ? 'Flashcard'
              : formula
              ? 'Formula & Concept'
              : rule.collectionType === 'FORMULA_SHEET'
              ? 'Formula'
              : 'Concept Card');

          blocks.push({
            kind: 'multi_liner_card',
            blockIndex: blockCounter++,
            displayIndex: displayIdx,
            title: cardTitle,
            badge: split.isReversed ? 'REVERSED' : '',
            paragraphs: Array.from(new Set(cardParagraphs)),
            nestedBullets,
            formula,
            isReversed: split.isReversed,
            isQuestionCard: false,
            questionType: resolvedType,
            statusTag: '',
            candidateAnswer: '',
            optionsList: [],
            correctAnswer: '',
            keyTakeaway: parsedKeyTakeaway,
          });
        } else {
          blocks.push({
            kind: 'one_liner',
            blockIndex: blockCounter++,
            displayIndex: displayIdx,
            leadTerm: split.lead,
            detail: split.detail,
            hasRecallTarget: split.hasRecallTarget,
          });
        }

        i = j;
        continue;
      }

      // 7. Standalone Direct Key -> Value Line (unnumbered)
      const directPair = splitDirectKeyValue(trimmed, rule.layerOrder);
      if (directPair) {
        blocks.push({
          kind: 'one_liner',
          blockIndex: blockCounter++,
          displayIndex: String(factSeq++),
          leadTerm: directPair.lead,
          detail: directPair.detail,
          hasRecallTarget: true,
        });
        i++;
        continue;
      }

      // 8. Standalone Display Math Block
      if (isDisplayMathStart(trimmed)) {
        const { block, nextIdx } = collectDisplayMathBlock(lines, i);
        blocks.push({
          kind: 'paragraph',
          blockIndex: blockCounter++,
          text: block,
        });
        i = nextIdx;
        continue;
      }

      // 9. Plain Paragraph
      blocks.push({
        kind: 'paragraph',
        blockIndex: blockCounter++,
        text: trimmed,
      });
      i++;
    }

    return blocks;
  },

  /**
   * Generates a Weak-Area Review StudyNote content string from wrong/skipped questions in an Exam Paper.
   */
  compileWeakAreasFromPaper(
    paper: QuestionPaper,
    submittedAnswers: Record<number, string> = {}
  ): { title: string; rawContent: string; count: number } {
    const pos = paper.metadata.positiveMarks ?? 2.0;
    const neg = paper.metadata.negativeMarks ?? 0.66;
    const unk = paper.metadata.unattendedMarks ?? 0.0;
    const policy = paper.metadata.msqScoringPolicy || 'ALL_OR_NOTHING';

    const missed = paper.questions.filter((q) => {
      const chosen = submittedAnswers[q.num];
      if (!chosen) return true;
      const grade = AnswerKeyMatcher.scoreQuestion(q, chosen, pos, neg, unk, policy);
      return grade.status === 'INCORRECT' || grade.status === 'PARTIAL' || grade.status === 'UNANSWERED';
    });

    const lines: string[] = [`## Weak-Area & Mistake Review — ${paper.metadata.title}`, ''];
    missed.forEach((q, idx) => {
      const chosen = submittedAnswers[q.num];
      const statusText = !chosen
        ? 'Unattempted / Skipped'
        : `Marked ${chosen} (Wrong / Partial)`;
      lines.push(`${idx + 1}. **Q${q.num}: ${q.question.replace(/\n+/g, ' ').trim()}**`);
      lines.push(`  - Question Type: ${q.questionType || 'objective'}`);
      lines.push(`  - Status: ${statusText}`);
      lines.push(`  - Correct Answer: ${AnswerKeyMatcher.formatKeyForDisplay(q.correctAnswer)}`);
      if (q.formula) lines.push(`  - Formula: ${q.formula}`);
      if (q.explanation) lines.push(`  - Key Takeaway: ${q.explanation.replace(/\n+/g, ' ').trim()}`);
      lines.push('');
    });

    return {
      title: `Weak Areas: ${paper.metadata.title}`,
      rawContent: lines.join('\n').trim(),
      count: missed.length,
    };
  },

  generatePrintableHtml(note: StudyNoteRecord): string {
    const blocks = this.parseBlocks(note.rawContent, note.formatRule);
    const bookmarkedSet = new Set(note.bookmarkedIndices || []);
    const metaLabel =
      note.collectionType === 'CUSTOM' && note.customLabel
        ? note.customLabel
        : KNOWLEDGE_COLLECTION_META[note.collectionType]?.label || 'Study Notes';

    const bodyParts = blocks
      .map((block) => {
        if (block.kind === 'heading') {
          const tag = block.level <= 2 ? 'h2' : 'h3';
          return `<${tag}>${formatRichHtml(block.text)}</${tag}>`;
        }
        if (block.kind === 'one_liner') {
          const star = bookmarkedSet.has(block.blockIndex);
          return `<div class="oneliner${star ? ' star' : ''}"><span class="idx">${
            star ? '★ ' : ''
          }${escapeHtml(block.displayIndex)}.</span>${
            block.leadTerm ? `<span class="lead">${formatRichHtml(block.leadTerm)}</span> — ` : ''
          }${formatRichHtml(block.detail)}</div>`;
        }
        if (block.kind === 'multi_liner_card') {
          const star = bookmarkedSet.has(block.blockIndex);
          return `<div class="multicard${star ? ' star' : ''}">
            <div><span class="idx">${star ? '★ ' : ''}${escapeHtml(
            block.displayIndex
          )}.</span><span class="lead">${formatRichHtml(block.title)}</span>
              ${block.questionType ? `<span class="badge">${escapeHtml(block.questionType)}</span>` : ''}
              ${
                block.statusTag
                  ? `<span class="badge" style="background:#FEE2E2;color:#991B1B;">${escapeHtml(
                      block.statusTag
                    )}</span>`
                  : ''
              }
            </div>
            ${
              block.optionsList.length > 0
                ? `<ul class="nested" style="list-style:none;padding-left:0;">${block.optionsList
                    .map((o) => `<li><strong>(${escapeHtml(o.label)})</strong> ${formatRichHtml(o.text)}</li>`)
                    .join('')}</ul>`
                : ''
            }
            ${block.paragraphs
              .map((p) =>
                isDisplayMathStart(p.trim())
                  ? `<div class="formula">${formatRichHtml(p)}</div>`
                  : `<p>${formatRichHtml(p)}</p>`
              )
              .join('')}
            ${
              block.correctAnswer
                ? `<p style="color:#0F766E;"><strong>Correct Answer:</strong> ${formatRichHtml(
                    block.correctAnswer
                  )}</p>`
                : ''
            }
            ${block.formula ? `<div class="formula">${formatRichHtml(block.formula)}</div>` : ''}
            ${
              block.keyTakeaway
                ? `<p><strong>Key Takeaway:</strong> ${formatRichHtml(block.keyTakeaway)}</p>`
                : ''
            }
            ${
              block.nestedBullets.length > 0
                ? `<ul class="nested">${block.nestedBullets
                    .map((nb) => `<li>${formatRichHtml(nb.text)}</li>`)
                    .join('')}</ul>`
                : ''
            }
          </div>`;
        }
        if (block.kind === 'callout') {
          return `<div class="callout"><strong>${escapeHtml(block.label)}:</strong> ${formatRichHtml(
            block.body
          )}</div>`;
        }
        if (block.kind === 'table') {
          return `<table><thead><tr>${block.headers
            .map((h) => `<th>${formatRichHtml(h)}</th>`)
            .join('')}</tr></thead><tbody>${block.rows
            .map((r) => `<tr>${r.map((c) => `<td>${formatRichHtml(c)}</td>`).join('')}</tr>`)
            .join('')}</tbody></table>`;
        }
        return isDisplayMathStart(block.text.trim())
          ? `<div class="formula">${formatRichHtml(block.text)}</div>`
          : `<p>${formatRichHtml(block.text)}</p>`;
      })
      .join('\n');

    return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8"/>
  <title>${escapeHtml(note.title)}</title>
  <style>
    body { font-family: 'Segoe UI', Roboto, Arial, sans-serif; color: #0F172A; margin: 28px; line-height: 1.6; }
    .header { border-bottom: 2px solid #177E89; padding-bottom: 12px; margin-bottom: 18px; }
    .badge { display: inline-block; background: #EAF6F6; color: #0F5C64; padding: 2px 8px; border-radius: 6px; font-size: 11px; font-weight: 700; margin-left: 6px; }
    h1 { margin: 6px 0 4px 0; font-size: 20px; }
    h2 { font-size: 15px; color: #17324D; margin: 18px 0 8px 0; border-left: 4px solid #177E89; padding-left: 8px; }
    h3 { font-size: 13.5px; color: #334155; margin: 12px 0 6px 0; }
    .oneliner, .multicard { padding: 10px 13px; margin: 9px 0; background: #F8FAFC; border: 1px solid #D5E1E7; border-radius: 6px; break-inside: avoid; }
    .oneliner.star, .multicard.star { border-left: 4px solid #F59E0B; background: #FFFBEB; }
    .idx { font-weight: 700; color: #177E89; margin-right: 6px; }
    .lead { font-weight: 700; color: #0F172A; }
    .formula { margin: 8px 0; padding: 7px 10px; background: #EAF6F6; border-left: 3px solid #177E89; border-radius: 4px; font-family: monospace; font-size: 12.5px; }
    ul.nested { margin: 6px 0 2px 18px; padding-left: 8px; }
    ul.nested li { margin: 3px 0; font-size: 13px; }
    .callout { padding: 10px 12px; background: #EFF6FF; border-left: 4px solid #2563EB; border-radius: 6px; margin: 10px 0; }
    table { width: 100%; border-collapse: collapse; margin: 12px 0; font-size: 12.5px; }
    th, td { border: 1px solid #CBD5E1; padding: 6px 8px; text-align: left; }
    th { background: #F1F5F9; font-weight: 700; }
    p { margin: 6px 0; font-size: 13px; }
    code { background: #F1F5F9; padding: 1px 4px; border-radius: 3px; font-family: monospace; font-size: 12px; }
  </style>
</head>
<body>
  <div class="header">
    <span class="badge" style="margin-left:0;">${escapeHtml(metaLabel)}</span>
    ${note.subject ? `<span class="badge">${escapeHtml(note.subject)}</span>` : ''}
    <h1>${escapeHtml(note.title)}</h1>
  </div>
  ${bodyParts}
</body>
</html>`;
  },

  compileKnowledgePrompt(cfg: KnowledgePromptConfig): string {
    const cleanTopic = cfg.topic.trim() || cfg.subject.trim() || '[ENTER TOPIC / CHAPTER HERE]';
    const cleanSubject = cfg.subject.trim();
    const cleanExam = cfg.examTarget.trim();
    const targetLang = cfg.language.trim() || 'English';
    const meta = KNOWLEDGE_COLLECTION_META[cfg.collectionType];
    const collectionTitle =
      cfg.collectionType === 'CUSTOM' && cfg.customCollectionLabel.trim()
        ? cfg.customCollectionLabel.trim()
        : meta.label;

    const isOneLiner = cfg.collectionType === 'ONE_LINERS';
    const isLong = cfg.collectionType === 'LONG_ANSWER';
    const isTable = cfg.collectionType === 'COMPARISON_TABLE';

    const depthLine = isOneLiner
      ? 'Keep every item ultra-crisp and high-yield on a single line (<= 140 chars after `->`).'
      : isLong
      ? cfg.depth === 'CRISP'
        ? 'Write structured ~150-word model answers with Introduction, bulleted Core Arguments, and 2-line Conclusion.'
        : cfg.depth === 'DEEP'
        ? 'Write exhaustive 400+ word analytical model answers with historical context, data/citations, LaTeX, and Way Forward.'
        : 'Write ~250-word examination-grade model answers with Introduction, Core Analytical Dimensions, Data/Cases, and Way Forward.'
      : isTable
      ? 'Keep every comparison table cell concise (3–12 words) highlighting core mechanisms and exam traps.'
      : cfg.depth === 'CRISP'
      ? 'Keep every item crisp and exam-focused (1–2 lines max).'
      : cfg.depth === 'DEEP'
      ? 'Provide deep conceptual breakdowns covering definitions, mechanisms, edge-cases, and exam traps.'
      : 'Use balanced multi-layer structure: bold key concept followed by clear core reasoning and 1–2 nested sub-points.';

    const layerLine = isTable
      ? 'Format entries as clean single-line Markdown table rows (`| Parameter | Item A | Item B |`) with a `| --- |` header separator.'
      : cfg.layerOrder === 'BACKWARDS_ANSWER_FIRST'
      ? 'State the scenario/clue FIRST followed by `->` and the exact **Concept** in bold at the end (`1. Clue... -> **Target Concept**`).'
      : cfg.layerOrder === 'QA_SEPARATE_LINES'
      ? 'Format each item as `Q: <concept question>` on line 1, followed immediately on line 2 by `A: <explanation>` (no blank line between Q and A).'
      : 'Put Key Term FIRST in **bold**, followed by `->` and explanation (`1. **Term** -> Crisp detail`).';

    const styleLine =
      cfg.collectionType === 'ONE_LINERS'
        ? `Generate exactly ${cfg.itemCount} ultra-dense exam one-liners grouped under \`## Section Headings\`.`
        : cfg.collectionType === 'COMPARISON_TABLE'
        ? `Cover ${cfg.itemCount} comparison rows cleanly inside Markdown pipe tables under \`## Section Headings\`.`
        : cfg.collectionType === 'FORMULA_SHEET'
        ? `Generate exactly ${cfg.itemCount} core laws/formulas with \`  - Formula:\` and \`  - Units & Conditions:\` sub-bullets.`
        : cfg.collectionType === 'WEAK_AREA_REVIEW'
        ? `Generate ${cfg.itemCount} remediation cards using \`  - Trap:\`, \`  - Correct Rule:\`, and \`  - Memory Anchor:\` sub-bullets.`
        : cfg.collectionType === 'LONG_ANSWER'
        ? `Generate ${Math.min(cfg.itemCount, 12)} full-length long-form Q&A answers grouped under \`## Theme Headings\`.`
        : `Generate exactly ${cfg.itemCount} structured **${collectionTitle}** entries grouped under \`## Topic Headings\`.`;

    const pyqTag = cfg.includePyqTags ? ' [PYQ Focus]' : '';
    const sample = isTable
      ? `## Key Comparison\n| Parameter | Isothermal | Adiabatic |\n| --- | --- | --- |\n| Constant | Temperature ($T$) | Heat ($Q = 0$) |${
          cfg.includeCallouts ? `\n\n> Exam Tip: Adiabatic slope is always steeper than isothermal.${pyqTag}` : ''
        }`
      : cfg.layerOrder === 'QA_SEPARATE_LINES'
      ? `## Core Concepts\nQ: What governs induced EMF direction?\nA: Lenz's Law — induced EMF always opposes the flux change producing it.${pyqTag}`
      : cfg.layerOrder === 'BACKWARDS_ANSWER_FIRST'
      ? `## High-Yield Facts\n1. Voltage spike opposing magnetic flux change across an inductor${pyqTag} -> **Lenz's Law**`
      : `## Core Concept Sheet\n1. **Lenz's Law** -> Induced EMF always opposes the flux change causing it.${pyqTag}\n  - Formula: $\\mathcal{E} = -N \\frac{d\\Phi_B}{dt}$\n  - Exam Trap: Minus sign indicates direction opposition, not negative magnitude.`;

    const lines: string[] = [
      cleanExam
        ? `Act as a senior ${cleanExam} educator and curriculum designer.`
        : 'Act as a senior competitive-exam educator and curriculum designer.',
      `Create a high-yield **${collectionTitle}** study sheet for: ${cleanTopic}`,
      ...(cleanSubject && cleanSubject.toLowerCase() !== cleanTopic.toLowerCase()
        ? [`Subject Domain: ${cleanSubject}`]
        : []),
      ...(cleanExam ? [`Target Examination: ${cleanExam}`] : []),
      `Output Language: ${targetLang}`,
      '',
      'ACCURACY & STRICT FORMATTING RULES:',
      `1. ${styleLine}`,
      `2. ${depthLine}`,
      `3. ${layerLine}`,
      isOneLiner
        ? '4. Keep each numbered fact on a single crisp line with NO nested sub-bullets.'
        : '4. Indent sub-points with exactly 2 spaces (`  - Formula: ...`, `  - Example: ...`) directly under the parent item.',
      cfg.includeLatexMath
        ? '5. Wrap mathematical symbols and equations in inline `$...$` LaTeX (keep commands ASCII).'
        : '5. Write mathematical expressions in plain Unicode without LaTeX delimiters.',
      ...(cfg.includeCallouts
        ? ['6. Highlight high-risk traps/mnemonics using flush-left `> Exam Tip: ...` blockquotes.']
        : []),
      ...(cfg.includeComparisonTable && !isTable
        ? ['- Include at least one Markdown comparison table (`| Parameter | ... |`).']
        : []),
      ...(cfg.includeBilingualTerms && targetLang.toLowerCase() !== 'english'
        ? [`- Keep technical terms in English in parentheses alongside ${targetLang} explanations.`]
        : []),
      ...(cfg.includePyqTags
        ? ['- Append concise `[High-Yield]` / `[PYQ Focus]` markers at the end of explanations.']
        : []),
      '- Output ONLY the structured notes directly without conversational intro or closing chatter.',
      '',
      'EXACT OUTPUT EXAMPLE:',
      sample,
      ...(cfg.customInstruction.trim() ? ['', 'USER CUSTOM REQUIREMENT:', cfg.customInstruction.trim()] : []),
    ];
    return lines.join('\n');
  },

  compileFlashcardPrompt(cfg: FlashcardPromptConfig): string {
    const cleanTopic = cfg.topic.trim() || cfg.subject.trim() || '[ENTER DECK TOPIC HERE]';
    const targetLang = cfg.language.trim() || 'English';
    const safeCount = Math.max(5, Math.min(100, cfg.cardCount));

    const formatRule =
      cfg.cardStyle === 'QA_RETRIEVAL'
        ? 'Format EVERY flashcard strictly with `Q<N>: <Focused active-recall question>` on line 1, followed immediately on line 2 by `A: <Core answer>`.'
        : cfg.cardStyle === 'CLOZE_SCENARIO'
        ? 'Format EVERY flashcard for reverse active recall: `<N>. <Diagnostic clue or scenario> -> **<Exact Target Concept>**`.'
        : cfg.cardStyle === 'FORMULA_TRAP'
        ? 'Format EVERY flashcard strictly as `<N>. **<Law / Equation>** -> <When it applies>` followed by `  - Formula: <equation>`.'
        : 'Format EVERY flashcard strictly as `<N>. **<Front Term>** -> <Concise back definition/rule>`.';

    const extras: string[] = [];
    if (cfg.includeLatexMath && cfg.cardStyle !== 'FORMULA_TRAP') extras.push('`  - Formula: ...`');
    if (cfg.includeMnemonics) extras.push('`  - Mnemonic: ...`');
    if (cfg.includeExamples) extras.push('`  - Example: ...`');
    if (cfg.includeExamTraps) extras.push('`  - Exam Trap: ...`');

    const eq1 = cfg.includeLatexMath ? '$\\Delta G = \\Delta H - T\\Delta S$' : 'ΔG = ΔH - TΔS';
    const eq2 = cfg.includeLatexMath ? '$\\text{pH} = \\text{p}K_a + \\log([\\text{A}^-]/[\\text{HA}])$' : 'pH = pKa + log([A-]/[HA])';

    const sample =
      cfg.cardStyle === 'QA_RETRIEVAL'
        ? `## Core Active-Recall Deck\nQ1: Under what thermodynamic condition is a process spontaneous at all temperatures?\nA: When enthalpy is exothermic (${cfg.includeLatexMath ? '$\\Delta H < 0$' : 'ΔH < 0'}) and entropy increases (${cfg.includeLatexMath ? '$\\Delta S > 0$' : 'ΔS > 0'}).\n  - Formula: ${eq1}${cfg.includeExamTraps ? '\n  - Exam Trap: Exothermic alone does not guarantee spontaneity at high T.' : ''}`
        : cfg.cardStyle === 'CLOZE_SCENARIO'
        ? `## Core Active-Recall Deck\n1. Buffer pH equals pKa when acid and conjugate base concentrations match 1:1 -> **Half-Equivalence Point**\n  - Formula: ${eq2}${cfg.includeExamTraps ? '\n  - Exam Trap: Peak buffer capacity occurs at pH = pKa.' : ''}`
        : `## Core Active-Recall Deck\n1. **Gibbs Free Energy Criterion** -> Predicts reaction spontaneity at constant T and P.\n  - Formula: ${eq1}${cfg.includeExamTraps ? '\n  - Exam Trap: Negative ΔG denotes thermodynamics, not kinetic rate.' : ''}`;

    return [
      cfg.examTarget.trim()
        ? `Act as a senior spaced-repetition flashcard designer for ${cfg.examTarget.trim()}.`
        : 'Act as a senior cognitive-science educator and spaced-repetition flashcard designer.',
      `Create a high-yield **SM-2 Flashcard Deck** of exactly **${safeCount} cards** for: ${cleanTopic}`,
      ...(cfg.subject.trim() ? [`Subject Domain: ${cfg.subject.trim()}`] : []),
      `Output Language: ${targetLang}`,
      '',
      'SPACED REPETITION RULES:',
      `1. Output strictly all ${safeCount} flashcards grouped under 2–5 \`## Deck Section\` headings; do not truncate or write 'etc.'.`,
      cfg.atomicSrsMode
        ? '2. Minimum-Information Principle: Test ONE atomic fact, threshold, or formula per card (Front <= 22 words).'
        : '2. Group closely contrasting sub-facts when contrast aids retention.',
      `3. ${formatRule}`,
      extras.length > 0
        ? `4. Optional 2-space indented back helpers directly below answer: ${extras.join(', ')}.`
        : '4. Keep flashcards strictly compact without extra helper bullets.',
      cfg.includeLatexMath
        ? '5. Wrap formulas in inline `$...$` LaTeX (ASCII commands).'
        : '5. Use clean plain Unicode math without LaTeX delimiters.',
      '6. Output ONLY the structured flashcard deck directly (no intro chatter or code fences).',
      '',
      'EXACT OUTPUT EXAMPLE:',
      sample,
      ...(cfg.customInstruction.trim() ? ['', 'USER CUSTOM REQUIREMENT:', cfg.customInstruction.trim()] : []),
    ].join('\n');
  },
};

export function createFlashcardDeckStatsLabel(style: FlashcardPromptStyle): string {
  switch (style) {
    case 'QA_RETRIEVAL':
      return 'Q & A Active Recall';
    case 'TERM_DEFINITION':
      return 'Term -> Definition';
    case 'CLOZE_SCENARIO':
      return 'Scenario / Clue -> Rule';
    case 'FORMULA_TRAP':
      return 'Formula & Exam Trap';
  }
}

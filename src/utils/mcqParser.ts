import { PaperMetadata, ParseResult, QuestionItem, QuestionOptions, QuestionType } from '../types/exam';
import { AnswerKeyMatcher } from './answerKeyMatcher';
import { ExamNumberGenerator } from './examNumberGenerator';

const DEVANAGARI_DIGITS: Record<string, string> = {
  '०': '0', '१': '1', '२': '2', '३': '3', '४': '4',
  '५': '5', '६': '6', '७': '7', '८': '8', '९': '9',
};
const BENGALI_DIGITS: Record<string, string> = {
  '০': '0', '১': '1', '২': '2', '৩': '3', '৪': '4',
  '৫': '5', '৬': '6', '৭': '7', '৮': '8', '৯': '9',
};

function normalizeNumerals(input: string): string {
  return input.replace(/[०-९০-৯]/g, (ch) => DEVANAGARI_DIGITS[ch] ?? BENGALI_DIGITS[ch] ?? ch);
}

export class McqParser {
  private static readonly ANSWER_HEADING_REGEX =
    /^\s*(?:#{1,6}\s*)?(?:\*{1,3})?\s*(?:answer(?:\s*key)?|answers|correct\s*answers?|key\s*answers?|उत्तर(?:\s*कुंजी)?|उत्तरमाला)\s*(?:\*{1,3})?:?\s*$/i;

  private static readonly EXPLANATION_HEADING_REGEX =
    /^\s*(?:#{1,6}\s*)?(?:\*{1,3})?\s*(?:detailed\s*explanations?|explanations?|solutions?|detailed\s*solutions?|व्याख्या|विस्तृत\s*हल|हल)\s*(?:\*{1,3})?:?\s*$/i;

  private static readonly COMBINED_HEADING_REGEX =
    /(?:answer|उत्तर|solution|हल).*(?:detailed\s*explanation|explanation|व्याख्या)|(?:detailed\s*explanation|explanation|व्याख्या).*(?:answer|उत्तर)/i;

  private static readonly QUESTION_START_REGEX =
    /^\s*(?:#{1,6}\s*)?(?:(?:\*\*|__)?\s*)?(?:Q(?:uestion)?\.?\s*)?(\d{1,4})\s*[.:)\-]\s*(?:\*\*|__)?\s*(.*?)(?:\*\*|__)?\s*$/i;

  private static readonly OPTION_REGEX =
    /^\s*(?:(?:\*\*|__)?\s*)?[([]?([A-Ea-e])[)\].:]\s*(.*)$/;

  public static parse(rawText: string): ParseResult {
    const warnings: string[] = [];
    if (!rawText || !rawText.trim()) {
      return {
        paper: {
          metadata: this.createDefaultMetadata(),
          questions: [],
        },
        rawCount: 0,
        hasAnswerKeys: false,
        hasExplanations: false,
        detectedMsqCount: 0,
        detectedOptionECount: 0,
        warnings: ['Empty input text provided.'],
      };
    }

    const trimmed = rawText.trim();
    if (trimmed.startsWith('{') || trimmed.startsWith('[') || trimmed.startsWith('```json')) {
      try {
        let cleanJson = trimmed;
        if (cleanJson.startsWith('```')) {
          cleanJson = cleanJson.substring(cleanJson.indexOf('\n') + 1);
        }
        if (cleanJson.endsWith('```')) {
          cleanJson = cleanJson.substring(0, cleanJson.lastIndexOf('```')).trim();
        }
        const parsed = JSON.parse(cleanJson);
        const rawArray = Array.isArray(parsed)
          ? parsed
          : parsed.questions || parsed.questionSets?.[0]?.questions || [];

        const questions: QuestionItem[] = rawArray.map((item: any, idx: number) => {
          const num = Number(item.num || idx + 1);
          const question = String(item.question || item.stem || item.text || '').trim();
          let opts: QuestionOptions = { a: '', b: '', c: '', d: '' };
          if (Array.isArray(item.options)) {
            opts = {
              a: String(item.options[0] || ''),
              b: String(item.options[1] || ''),
              c: String(item.options[2] || ''),
              d: String(item.options[3] || ''),
              e: item.options[4] ? String(item.options[4]) : undefined,
            };
          } else if (item.options && typeof item.options === 'object') {
            opts = {
              a: String(item.options.a || item.options.A || ''),
              b: String(item.options.b || item.options.B || ''),
              c: String(item.options.c || item.options.C || ''),
              d: String(item.options.d || item.options.D || ''),
              e: item.options.e || item.options.E ? String(item.options.e || item.options.E) : undefined,
            };
          }
          const rawAns = item.correctAnswer ?? item.answer ?? item.ans ?? item.correctOption;
          const ans = AnswerKeyMatcher.normalizeKey(typeof rawAns === 'string' ? rawAns : '') || null;
          const exp = item.explanation || item.detailedExplanation || item.solution || item.rationale || null;

          const qItem: QuestionItem = {
            id: item.id || `q-${num}-${idx}`,
            num,
            question,
            options: opts,
            correctAnswer: ans,
            explanation: exp ? String(exp) : null,
            diagramLabel: item.diagramLabel || null,
            diagramUrl: item.diagramUrl || null,
            formula: item.formula || null,
            isNeutralized: Boolean(item.isNeutralized),
            isVerified: Boolean(item.isVerified),
            questionType: item.questionType || this.classifyQuestionType(question, opts, ans),
          };
          return qItem;
        });

        if (questions.length > 0) {
          const meta = parsed.metadata || {};
          return {
            paper: {
              metadata: {
                id: meta.id || `paper-json-${Date.now()}`,
                title: meta.title || parsed.title || 'Imported Question Set',
                subject: meta.subject || parsed.subject || 'General Studies',
                topic: meta.topic || parsed.topic || '',
                setNumber: ExamNumberGenerator.normalizeSetCode(meta.setNumber || parsed.setNumber),
                targetExam: meta.targetExam || parsed.targetExam || 'All India Competitive Exam',
                rollNumber: meta.rollNumber,
                bookletNumber: meta.bookletNumber,
                durationMinutes: meta.durationMinutes,
                positiveMarks: meta.positiveMarks,
                negativeMarks: meta.negativeMarks,
                msqScoringPolicy: meta.msqScoringPolicy,
                folderId: meta.folderId || 'default',
                createdAt: meta.createdAt || Date.now(),
              },
              questions,
            },
            rawCount: questions.length,
            hasAnswerKeys: questions.some((q) => Boolean(q.correctAnswer)),
            hasExplanations: questions.some((q) => Boolean(q.explanation)),
            detectedMsqCount: questions.filter((q) => AnswerKeyMatcher.isMultiSelect(q)).length,
            detectedOptionECount: questions.filter((q) => Boolean(q.options.e?.trim())).length,
            warnings: [],
          };
        }
      } catch {
        // fall through to text parser
      }
    }

    const normalizedText = normalizeNumerals(rawText);
    const metadata = this.extractMetadata(normalizedText);
    const { questionText, answerText, explanationText } = this.splitSections(normalizedText);

    const questions = this.parseQuestions(questionText, warnings);
    const answersMap = this.parseAnswerKeys(answerText);
    const { explanations: explanationsMap, derivedAnswers } = this.parseExplanations(explanationText);

    let answersCount = 0;
    let explanationsCount = 0;

    questions.forEach((q) => {
      if (answersMap.has(q.num)) {
        q.correctAnswer = answersMap.get(q.num)!;
        answersCount++;
      } else if (derivedAnswers.has(q.num)) {
        q.correctAnswer = derivedAnswers.get(q.num)!;
        answersCount++;
      } else if (q.correctAnswer) {
        answersCount++;
      } else {
        const inlineAnsMatch = q.question.match(
          /\b(?:Ans(?:wer)?|Correct\s*(?:Option|Answer))\b\s*[:\-]?\s*[([]?([A-Ea-e](?:\s*[,&/]\s*[A-Ea-e])*)[)\]]?/i
        );
        if (inlineAnsMatch) {
          const norm = AnswerKeyMatcher.normalizeKey(inlineAnsMatch[1]);
          if (norm) {
            q.correctAnswer = norm;
            answersCount++;
            q.question = q.question
              .replace(/\b(?:Ans(?:wer)?|Correct\s*(?:Option|Answer))\b\s*[:\-]?\s*[([]?[A-Ea-e](?:\s*[,&/]\s*[A-Ea-e])*[)\]]?/gi, '')
              .trim();
          }
        }
      }

      if (explanationsMap.has(q.num)) {
        q.explanation = explanationsMap.get(q.num)!;
        explanationsCount++;
      }

      // Re-classify MSQ once keys are bound
      if (AnswerKeyMatcher.isMultiSelect(q)) {
        q.questionType = 'multiple-select';
      }
    });

    if (questions.length === 0) {
      warnings.push('No valid MCQ questions matching standard numbering patterns were found.');
    }

    return {
      paper: {
        metadata,
        questions,
      },
      rawCount: questions.length,
      hasAnswerKeys: answersCount > 0,
      hasExplanations: explanationsCount > 0,
      detectedMsqCount: questions.filter((q) => AnswerKeyMatcher.isMultiSelect(q)).length,
      detectedOptionECount: questions.filter((q) => Boolean(q.options.e?.trim())).length,
      warnings,
    };
  }

  public static formatMultiLineExplanation(raw: string): string {
    if (!raw) return '';
    return raw
      .replace(/\s+(?=Option\s*\([A-E]\)|Core Concept|Key Idea|Memory Anchor|Distractor Analysis|Step\s+\d+:)/gi, '\n')
      .trim();
  }

  public static isPromptOrInstruction(line: string): boolean {
    const l = line.trim();
    if (!l) return false;
    if (/^(?:Act as|You are a|Generate|Create\s+\[|Create\s+\d+|Please provide|Here (?:is|are)|Below (?:is|are)|Sure|Certainly|Hope this helps|I have created)/i.test(l)) {
      return true;
    }
    if (/^(?:System\s*Prompt|User\s*Prompt|Prompt|Instructions?|Guidelines?|Rules?|Format(?:\s*Requirements)?)\s*:/i.test(l)) {
      return true;
    }
    if (/^(?:Language|Examination\s*Name|MCQ\s*Count|Syllabus\s*Specification|Topic\s*Name)\s*:/i.test(l)) {
      return true;
    }
    return /^\[.*?(?:mcq|topic|language|examination|syllabus|insert).*?\]$/i.test(l);
  }

  public static stripMarkdown(text: string): string {
    return text
      .replace(/\*\*(.*?)\*\*/g, '$1')
      .replace(/__(.*?)__/g, '$1')
      .replace(/`([^`]+)`/g, '$1')
      .replace(/^#{1,6}\s+/gm, '')
      .trim();
  }

  private static extractMetadata(rawText: string): PaperMetadata {
    const lines = rawText.split('\n').map((l) => l.trim()).filter(Boolean);
    let title = '';
    let subject = '';
    let topic = '';
    let setNumber = '';
    let targetExam = '';
    let rollNumber = '';
    let bookletNumber = '';

    const scanLimit = Math.min(35, lines.length);
    for (let i = 0; i < scanLimit; i++) {
      const line = lines[i];
      if (this.isPromptOrInstruction(line)) continue;
      const clean = this.stripMarkdown(line);

      const titleMatch = clean.match(/\b(?:title|paper\s*title|exam\s*name)\s*:\s*(.+)/i);
      if (titleMatch && !title) { title = titleMatch[1].trim(); continue; }

      const subjMatch = clean.match(/\bsubject\s*:\s*(.+)/i);
      if (subjMatch && !subject) { subject = subjMatch[1].trim(); continue; }

      const topicMatch = clean.match(/\b(?:topic|विषय|unit|chapter)\s*:\s*(.+)/i);
      if (topicMatch && !topic) { topic = topicMatch[1].trim(); continue; }

      const examMatch = clean.match(/\b(?:exam|examination|target\s*exam)\s*:\s*(.+)/i);
      if (examMatch && !targetExam) { targetExam = examMatch[1].trim(); continue; }

      const rollMatch = clean.match(/\broll(?:\s*no\.?|\s*number)?\s*[:\-]\s*([0-9]+)/i);
      if (rollMatch && !rollNumber) { rollNumber = rollMatch[1].trim(); continue; }

      const bookletMatch = clean.match(/\bbooklet(?:\s*no\.?|\s*number)?\s*[:\-]\s*([0-9]+)/i);
      if (bookletMatch && !bookletNumber) { bookletNumber = bookletMatch[1].trim(); continue; }

      const setMatch = clean.match(/\b(?:set(?:\s*no\.?|\s*code)?|series)\s*[:\-]?\s*([A-Za-z0-9\-]+)/i);
      if (setMatch && !setNumber) {
        setNumber = ExamNumberGenerator.normalizeSetCode(setMatch[1].trim());
        continue;
      }

      if (!title && line.startsWith('#')) {
        const candidate = line.replace(/^#+\s*/, '').trim();
        if (!/(?:questions?|answer|explanation|objective|mcq|instructions?)/i.test(candidate) && candidate.length <= 90) {
          title = candidate;
        }
      }
    }

    return {
      id: `paper-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 5)}`,
      title: title || 'Examination Question Paper',
      subject: subject || 'General Studies & Science',
      topic: topic || 'Comprehensive Test',
      setNumber: setNumber || '01',
      targetExam: targetExam || 'Competitive Examination',
      rollNumber: rollNumber ? ExamNumberGenerator.normalizeRollNumber(rollNumber) : '0000000001',
      bookletNumber: bookletNumber ? ExamNumberGenerator.normalizeBookletNumber(bookletNumber) : '0000001',
      durationMinutes: 60,
      positiveMarks: 2.0,
      negativeMarks: 0.66,
      unattendedMarks: 0.0,
      msqScoringPolicy: 'ALL_OR_NOTHING',
      folderId: 'default',
      createdAt: Date.now(),
    };
  }

  private static splitSections(rawText: string): { questionText: string; answerText: string; explanationText: string } {
    const lines = rawText.split('\n');
    let answerStart: number | null = null;
    let explanationStart: number | null = null;

    for (let i = 0; i < lines.length; i++) {
      const cleanLine = this.stripMarkdown(lines[i]).trim();
      if (!cleanLine) continue;

      if (this.COMBINED_HEADING_REGEX.test(cleanLine)) {
        if (answerStart === null) answerStart = i;
        if (explanationStart === null) explanationStart = i;
        continue;
      }
      if (explanationStart === null && this.EXPLANATION_HEADING_REGEX.test(cleanLine)) {
        explanationStart = i;
      } else if (answerStart === null && this.ANSWER_HEADING_REGEX.test(cleanLine)) {
        answerStart = i;
      }
    }

    if (answerStart === null && explanationStart === null) {
      const trailingPattern =
        /^\s*(?:(?:\*\*|__)?\s*)?(?:Q(?:uestion)?\.?\s*)?1\s*(?:[.:)\-]|(?=\s*[([]?[A-E][)\]]?))\s*[([]?[A-E][)\]]?/i;
      for (let i = 5; i < lines.length; i++) {
        if (trailingPattern.test(lines[i].trim())) {
          answerStart = i - 1;
          explanationStart = i - 1;
          break;
        }
      }
    }

    const validStarts = [answerStart, explanationStart].filter((idx): idx is number => idx !== null);
    const firstSectionBoundary = validStarts.length > 0 ? Math.min(...validStarts) : lines.length;

    const questionText = lines.slice(0, firstSectionBoundary).join('\n');
    let answerText = '';
    if (answerStart !== null) {
      const end = explanationStart !== null && explanationStart > answerStart ? explanationStart : lines.length;
      answerText = lines.slice(answerStart + 1, end).join('\n');
    }
    const explanationText = explanationStart !== null ? lines.slice(explanationStart + 1).join('\n') : '';

    return { questionText, answerText, explanationText };
  }

  private static parseQuestions(questionText: string, warnings: string[]): QuestionItem[] {
    const lines = questionText.split('\n');
    const questionChunks: { startLine: number; num: number; initialText: string }[] = [];

    for (let i = 0; i < lines.length; i++) {
      const match = lines[i].match(this.QUESTION_START_REGEX);
      if (!match) continue;
      const num = parseInt(match[1], 10);
      const text = match[2]?.trim() || '';
      if (Number.isNaN(num) || this.isPromptOrInstruction(text)) continue;

      if (questionChunks.length > 0) {
        const previousChunk = questionChunks[questionChunks.length - 1];
        const previousLines = lines.slice(previousChunk.startLine, i);
        const optionLetters = new Set<string>();
        for (const previousLine of previousLines) {
          const optionMatch = previousLine.match(this.OPTION_REGEX);
          if (optionMatch) optionLetters.add(optionMatch[1].toLowerCase());
        }
        const hasFourOptions = ['a', 'b', 'c', 'd'].every((l) => optionLetters.has(l));
        if (!hasFourOptions) continue;
      }

      questionChunks.push({ startLine: i, num, initialText: text });
    }

    const results: QuestionItem[] = [];
    for (let index = 0; index < questionChunks.length; index++) {
      const current = questionChunks[index];
      const nextStart = index + 1 < questionChunks.length ? questionChunks[index + 1].startLine : lines.length;
      const blockLines = lines.slice(current.startLine, nextStart);

      const { stem: rawStem, options, inlineAnswer, formula, diagramLabel } = this.extractStemAndOptions(
        current.initialText,
        blockLines
      );

      if (!options.a || !options.b || !options.c || !options.d) {
        warnings.push(`Question #${current.num} may be missing one or more options.`);
      }

      const qType = this.classifyQuestionType(rawStem, options, inlineAnswer);
      const stem = this.cleanStemHeader(rawStem);

      results.push({
        id: `q-${current.num}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`,
        num: current.num,
        question: stem,
        options,
        correctAnswer: inlineAnswer,
        explanation: null,
        questionType: qType,
        formula: formula || null,
        diagramLabel: diagramLabel || null,
      });
    }

    return results;
  }

  private static cleanStemHeader(stem: string): string {
    return stem
      .replace(/^\s*(?:#{1,6}\s*)?Assertion[–\- ]Reason\s*[:.\-]?\s*\n+/i, '')
      .replace(/^\s*(?:#{1,6}\s*)?अभिकथन[–\- ]कारण\s*[:.\-]?\s*\n+/i, '')
      .replace(/^\s*(?:#{1,6}\s*)?Match\s+the\s+Following\s*[:.\-]?\s*\n+/i, '')
      .replace(/^\s*(?:#{1,6}\s*)?Multiple\s+Choice\s+Questions?\s*[:.\-]?\s*\n+/i, '')
      .replace(/^\s*(?:#{1,6}\s*)?Integrated\s+Multi-Concept\s+Question\s*[:.\-]?\s*\n+/i, '')
      .trim();
  }

  private static extractStemAndOptions(
    initialText: string,
    blockLines: string[]
  ): {
    stem: string;
    options: QuestionOptions;
    inlineAnswer: string | null;
    formula?: string;
    diagramLabel?: string;
  } {
    const allLines: string[] = [];
    if (initialText && initialText.trim()) allLines.push(initialText);
    for (let j = 1; j < blockLines.length; j++) allLines.push(blockLines[j]);

    let inlineAnswer: string | null = null;
    let formula: string | undefined;
    let diagramLabel: string | undefined;
    const options: QuestionOptions = { a: '', b: '', c: '', d: '' };

    const cleanLines: string[] = [];
    for (let i = 0; i < allLines.length; i++) {
      const line = allLines[i].trim();

      const formulaMatch = line.match(/^\[?\s*Formula\s*:\s*(.+?)\]?$/i);
      if (formulaMatch) {
        formula = formulaMatch[1].trim();
        continue;
      }

      const diagramMatch = line.match(/^\[?\s*(?:Diagram|Figure)\s*(?:Reference)?\s*:\s*(.+?)\]?$/i);
      if (diagramMatch) {
        diagramLabel = diagramMatch[1].trim();
        continue;
      }

      const inlineMatch = line.match(
        /^(?:\*\*)?(?:Correct\s*(?:Option|Answer|Keys?)?|Ans(?:wer)?)\s*[:.]?\s*[([]?([A-Ea-e](?:\s*[,&/]\s*[A-Ea-e])*)[)\]]?(?:\*\*)?$/i
      );
      if (inlineMatch && i >= allLines.length - 3) {
        const norm = AnswerKeyMatcher.normalizeKey(inlineMatch[1]);
        if (norm) inlineAnswer = norm;
        continue;
      }
      cleanLines.push(allLines[i]);
    }

    // Horizontal options (4 or 2+2)
    for (let k = cleanLines.length - 1; k >= 0; k--) {
      const line = cleanLines[k].trim();
      if (!line) continue;
      const grid = this.extractHorizontalOptions(line);
      if (grid) {
        if (grid.a && grid.b && grid.c && grid.d) {
          options.a = grid.a;
          options.b = grid.b;
          options.c = grid.c;
          options.d = grid.d;
          if (grid.e) options.e = grid.e;
          return { stem: cleanLines.slice(0, k).join('\n').trim(), options, inlineAnswer, formula, diagramLabel };
        }
        if (grid.c && grid.d && !grid.a && !grid.b) {
          let prevIdx = k - 1;
          while (prevIdx >= 0 && !cleanLines[prevIdx].trim()) prevIdx--;
          if (prevIdx >= 0) {
            const prevGrid = this.extractHorizontalOptions(cleanLines[prevIdx]);
            if (prevGrid?.a && prevGrid.b) {
              options.a = prevGrid.a;
              options.b = prevGrid.b;
              options.c = grid.c;
              options.d = grid.d;
              return { stem: cleanLines.slice(0, prevIdx).join('\n').trim(), options, inlineAnswer, formula, diagramLabel };
            }
          }
        }
      }
    }

    // Reverse vertical scan for optional (e), (d), (c), (b), (a)
    let eIdx = -1, dIdx = -1, cIdx = -1, bIdx = -1, aIdx = -1;
    for (let i = cleanLines.length - 1; i >= 0; i--) {
      const line = cleanLines[i].trim();
      if (!line) continue;
      if (/^\s*(?:Assertion|अभिकथन|Statement)\s*[([]?[A-E][)\]]?/i.test(line)) continue;
      const m = line.match(this.OPTION_REGEX);
      if (m) {
        const letter = m[1].toLowerCase();
        if (letter === 'e' && eIdx === -1 && dIdx === -1) eIdx = i;
        else if (letter === 'd' && dIdx === -1) dIdx = i;
        else if (letter === 'c' && dIdx !== -1 && cIdx === -1 && i < dIdx) cIdx = i;
        else if (letter === 'b' && cIdx !== -1 && bIdx === -1 && i < cIdx) bIdx = i;
        else if (letter === 'a' && bIdx !== -1 && aIdx === -1 && i < bIdx) {
          aIdx = i;
          break;
        }
      }
    }

    if (aIdx !== -1 && bIdx !== -1 && cIdx !== -1 && dIdx !== -1) {
      const dEnd = eIdx !== -1 && eIdx > dIdx ? eIdx : cleanLines.length;
      options.a = this.cleanOptionText(cleanLines.slice(aIdx, bIdx), 'a');
      options.b = this.cleanOptionText(cleanLines.slice(bIdx, cIdx), 'b');
      options.c = this.cleanOptionText(cleanLines.slice(cIdx, dIdx), 'c');
      options.d = this.cleanOptionText(cleanLines.slice(dIdx, dEnd), 'd');
      if (eIdx !== -1 && eIdx > dIdx) {
        options.e = this.cleanOptionText(cleanLines.slice(eIdx), 'e');
      }
      return { stem: cleanLines.slice(0, aIdx).join('\n').trim(), options, inlineAnswer, formula, diagramLabel };
    }

    // Top-down fallback
    let currentKey: 'a' | 'b' | 'c' | 'd' | 'e' | null = null;
    const stemLines: string[] = [];
    for (let i = 0; i < cleanLines.length; i++) {
      const line = cleanLines[i].trim();
      if (!line) continue;
      if (/^\s*(?:Assertion|अभिकथन|Statement)\s*[([]?[A-E][)\]]?/i.test(line)) {
        stemLines.push(cleanLines[i]);
        continue;
      }
      const m = line.match(this.OPTION_REGEX);
      if (m) {
        const letter = m[1].toLowerCase() as 'a' | 'b' | 'c' | 'd' | 'e';
        currentKey = letter;
        options[letter] = m[2].trim();
        continue;
      }
      if (currentKey) {
        options[currentKey] = `${options[currentKey] || ''} ${line}`.trim();
      } else {
        stemLines.push(cleanLines[i]);
      }
    }

    return { stem: stemLines.join('\n').trim(), options, inlineAnswer, formula, diagramLabel };
  }

  private static cleanOptionText(lines: string[], letter: string): string {
    if (lines.length === 0) return '';
    const first = lines[0].replace(new RegExp(`^\\s*(?:(?:\\*\\*|__)?\\s*)?[(\\[]?${letter}[)\\].:]\\s*`, 'i'), '');
    const rest = lines.slice(1).map((l) => l.trim()).filter(Boolean);
    return [first.trim(), ...rest].join('\n').trim();
  }

  private static extractHorizontalOptions(
    line: string
  ): { a?: string; b?: string; c?: string; d?: string; e?: string } | null {
    if (!line) return null;
    const match5 = line.match(
      /^\s*[([]?[Aa][)\].:]\s*(.*?)\s+[([]?[Bb][)\].:]\s*(.*?)\s+[([]?[Cc][)\].:]\s*(.*?)\s+[([]?[Dd][)\].:]\s*(.*?)\s+[([]?[Ee][)\].:]\s*(.*?)\s*$/
    );
    if (match5) {
      return { a: match5[1].trim(), b: match5[2].trim(), c: match5[3].trim(), d: match5[4].trim(), e: match5[5].trim() };
    }
    const match4 = line.match(
      /^\s*[([]?[Aa][)\].:]\s*(.*?)\s+[([]?[Bb][)\].:]\s*(.*?)\s+[([]?[Cc][)\].:]\s*(.*?)\s+[([]?[Dd][)\].:]\s*(.*?)\s*$/
    );
    if (match4) {
      return { a: match4[1].trim(), b: match4[2].trim(), c: match4[3].trim(), d: match4[4].trim() };
    }
    const matchAB = line.match(/^\s*[([]?[Aa][)\].:]\s*(.*?)\s+[([]?[Bb][)\].:]\s*(.*?)\s*$/);
    if (matchAB) return { a: matchAB[1].trim(), b: matchAB[2].trim() };
    const matchCD = line.match(/^\s*[([]?[Cc][)\].:]\s*(.*?)\s+[([]?[Dd][)\].:]\s*(.*?)\s*$/);
    if (matchCD) return { c: matchCD[1].trim(), d: matchCD[2].trim() };
    return null;
  }

  public static classifyQuestionType(
    questionStem: string,
    options: QuestionOptions,
    answerKey?: string | null
  ): QuestionType {
    const stem = (questionStem || '').toLowerCase();
    const opts = `${options.a} ${options.b} ${options.c} ${options.d} ${options.e || ''}`.toLowerCase();

    if (AnswerKeyMatcher.parseSelectedLetters(answerKey).length >= 2) {
      return 'multiple-select';
    }
    if (/multiple[-\s]*select|\bmsq\b|more\s+than\s+one\s+(?:option|correct)|select\s+all/i.test(stem)) {
      return 'multiple-select';
    }
    if ((stem.includes('assertion') && stem.includes('reason')) || opts.includes('both a and r')) {
      return 'assertion-reason';
    }
    if (stem.includes('match the following') || stem.includes('list-i') || stem.includes('column i')) {
      return 'match-the-following';
    }
    if (stem.includes('chronological')) {
      return 'chronological';
    }
    if (stem.includes('pair') || stem.includes('correctly matched')) {
      return 'pair-based';
    }
    if (stem.includes('statement') || stem.includes('consider the following')) {
      return 'statement';
    }
    if (stem.includes('calculate') || stem.includes('\\frac') || /\d+\s*[%×^]/.test(stem)) {
      return 'numerical';
    }
    return 'conceptual';
  }

  public static parseAnswerKeys(text: string): Map<number, string> {
    const map = new Map<number, string>();
    if (!text) return map;

    const singleAnswerRegex =
      /(?:(?:\*\*|__)?\s*(?:Q(?:uestion)?\.?\s*)?(\d{1,4})\s*(?:\*\*|__)?\s*(?:[.:)\-]|\s*\|\s*)\s*(?:\*\*|__)?\s*[([]?([A-Ea-e](?:\s*[,&/]\s*[A-Ea-e])*)[)\]]?(?:\*\*|__)?)/gi;
    let match: RegExpExecArray | null;
    while ((match = singleAnswerRegex.exec(text)) !== null) {
      const qNum = parseInt(match[1], 10);
      const norm = AnswerKeyMatcher.normalizeKey(match[2]);
      if (!Number.isNaN(qNum) && norm && !map.has(qNum)) {
        map.set(qNum, norm);
      }
    }

    for (const line of text.split('\n')) {
      if (!line.includes('|')) continue;
      const cells = line.split('|').map((c) => c.replace(/\*\*|__/g, '').trim()).filter(Boolean);
      for (let k = 0; k < cells.length - 1; k++) {
        const num = parseInt(cells[k], 10);
        const norm = AnswerKeyMatcher.normalizeKey(cells[k + 1]);
        if (!Number.isNaN(num) && norm && /^[A-E](,[A-E])*$/.test(norm) && !map.has(num)) {
          map.set(num, norm);
        }
      }
    }
    return map;
  }

  public static parseExplanations(text: string): { explanations: Map<number, string>; derivedAnswers: Map<number, string> } {
    const explanations = new Map<number, string>();
    const derivedAnswers = new Map<number, string>();
    if (!text) return { explanations, derivedAnswers };

    const lines = text.split('\n');
    let currentNum: number | null = null;
    let currentExp: string[] = [];

    const flush = () => {
      if (currentNum !== null && currentExp.length > 0) {
        const fullText = currentExp.join('\n').trim();
        explanations.set(currentNum, fullText);
        const matchAns = fullText.match(
          /(?:Correct\s*(?:Answers?|Options?)?\s*[:.]?\s*[([]?([A-Ea-e](?:\s*[,&/]\s*[A-Ea-e])*)[)\]]?)|(?:[([]?([A-Ea-e])[)\]]?\s*[^—\n]*[—\-]\s*(?:\*\*)?\s*Correct\b)/i
        );
        if (matchAns) {
          const norm = AnswerKeyMatcher.normalizeKey(matchAns[1] || matchAns[2]);
          if (norm) derivedAnswers.set(currentNum, norm);
        }
      }
      currentExp = [];
    };

    const expStartRegex = /^\s*(?:#{1,6}\s*)?(?:(?:\*\*|__)?\s*)?(?:Q(?:uestion)?\.?\s*)?(\d{1,4})\s*[.:)\-]\s*(.*)$/i;
    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed) {
        if (currentNum !== null) currentExp.push('');
        continue;
      }
      const match = trimmed.match(expStartRegex);
      if (match) {
        flush();
        currentNum = parseInt(match[1], 10);
        if (match[2]) currentExp.push(match[2].trim());
      } else if (currentNum !== null) {
        currentExp.push(trimmed);
      }
    }
    flush();

    return { explanations, derivedAnswers };
  }

  private static createDefaultMetadata(): PaperMetadata {
    return {
      id: `paper-${Date.now().toString(36)}`,
      title: 'Examination Paper',
      subject: 'General Knowledge',
      topic: 'Standard Assessment',
      setNumber: '01',
      targetExam: 'All India Test',
      rollNumber: '0000000001',
      bookletNumber: '0000001',
      durationMinutes: 60,
      positiveMarks: 2.0,
      negativeMarks: 0.66,
      unattendedMarks: 0.0,
      msqScoringPolicy: 'ALL_OR_NOTHING',
      folderId: 'default',
      createdAt: Date.now(),
    };
  }
}

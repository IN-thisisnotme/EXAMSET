import { CorrectOption, QuestionItem, QuestionPaper, ParseResult, PaperMetadata, QuestionType, QuestionOptions } from '../types/exam';

export class McqParser {
  // Regex patterns for structural separation
  private static readonly ANSWER_HEADING_REGEX = 
    /^\s*(?:#{1,6}\s*)?(?:\*{1,3})?\s*(?:answer(?:\s*key)?|answers|correct\s*answers?|key\s*answers?|उत्तर(?:\s*कुंजी)?|उत्तरमाला)\s*(?:\*{1,3})?:?\s*$/i;

  private static readonly EXPLANATION_HEADING_REGEX = 
    /^\s*(?:#{1,6}\s*)?(?:\*{1,3})?\s*(?:detailed\s*explanations?|explanations?|solutions?|detailed\s*solutions?|व्याख्या|विस्तृत\s*हल|हल)\s*(?:\*{1,3})?:?\s*$/i;

  private static readonly COMBINED_HEADING_REGEX = 
    /(?:answer|उत्तर|solution|हल).*(?:detailed\s*explanation|explanation|व्याख्या)|(?:detailed\s*explanation|explanation|व्याख्या).*(?:answer|उत्तर)/i;

  private static readonly QUESTION_START_REGEX = 
    /^\s*(?:#{1,6}\s*)?(?:(?:\*\*|__)?\s*)?(?:Q(?:uestion)?\.?\s*)?(\d{1,4})\s*[\.\:\)\-]\s*(?:\*\*|__)?\s*(.*?)(?:\*\*|__)?\s*$/i;

  private static readonly OPTION_REGEX = 
    /^\s*(?:(?:\*\*|__)?\s*)?[\(\[]?([A-Da-d])[\)\]\.\:]\s*(.*)$/;

  /**
   * Main entry point to parse raw string into a structured QuestionPaper.
   */
  public static parse(rawText: string): ParseResult {
    const warnings: string[] = [];
    if (!rawText || !rawText.trim()) {
      return {
        paper: {
          metadata: this.createDefaultMetadata(),
          questions: []
        },
        rawCount: 0,
        hasAnswerKeys: false,
        hasExplanations: false,
        warnings: ['Empty input text provided.']
      };
    }

    const metadata = this.extractMetadata(rawText);
    const { questionText, answerText, explanationText } = this.splitSections(rawText);

    const questions = this.parseQuestions(questionText, warnings);
    const answersMap = this.parseAnswerKeys(answerText);
    const { explanations: explanationsMap, derivedAnswers } = this.parseExplanations(explanationText);

    // Merge answers and explanations into question objects
    let answersCount = 0;
    let explanationsCount = 0;

    questions.forEach((q) => {
      // 1. Apply explicit answer key if found
      if (answersMap.has(q.num)) {
        q.correctAnswer = answersMap.get(q.num)!;
        answersCount++;
      } else if (derivedAnswers.has(q.num)) {
        // 2. Fall back to answer derived from Detailed Explanation
        q.correctAnswer = derivedAnswers.get(q.num)!;
        answersCount++;
      } else if (!q.correctAnswer) {
        // 3. Look inside the question stem itself for inline answer tags (e.g. "Ans: B" or "Answer: (C)")
        const inlineAnsMatch = q.question.match(/\b(?:Ans(?:wer)?|Correct\s*(?:Option|Answer))\b\s*[:\-]?\s*[\(\[]?([A-D])[\)\]]?/i);
        if (inlineAnsMatch) {
          q.correctAnswer = inlineAnsMatch[1].toUpperCase() as CorrectOption;
          answersCount++;
          // Clean the inline answer out of the question stem
          q.question = q.question.replace(/\b(?:Ans(?:wer)?|Correct\s*(?:Option|Answer))\b\s*[:\-]?\s*[\(\[]?[A-D][\)\]]?/ig, '').trim();
        }
      }

      // Apply explanation if found
      if (explanationsMap.has(q.num)) {
        q.explanation = explanationsMap.get(q.num)!;
        explanationsCount++;
      }
    });

    if (questions.length === 0) {
      warnings.push('No valid MCQ questions matching standard numbering patterns were found.');
    }

    return {
      paper: {
        metadata,
        questions
      },
      rawCount: questions.length,
      hasAnswerKeys: answersCount > 0,
      hasExplanations: explanationsCount > 0,
      warnings
    };
  }

  /**
   * Filters prompt generation templates, system instructions, and LLM chat artifacts.
   */
  public static isPromptOrInstruction(line: string): boolean {
    const l = line.trim();
    if (!l) return false;

    // AI chat prefixes
    if (/^(?:Act as|You are a|Generate|Create\s+\[|Create\s+\d+|Please provide|Here (?:is|are)|Below (?:is|are)|Sure|Certainly|Hope this helps|I have created|In this test|This test contains)/i.test(l)) {
      return true;
    }
    // Instruction label headers
    if (/^(?:System\s*Prompt|User\s*Prompt|Prompt|Instructions?|Guidelines?|Rules?|Format(?:\s*Requirements)?)\s*:/i.test(l)) {
      return true;
    }
    // Syllabus prompt parameters
    if (/^(?:Language|Examination\s*Name|MCQ\s*Count|Syllabus\s*Specification|Topic\s*Name)\s*:/i.test(l)) {
      return true;
    }
    // Template placeholder brackets
    if (/^\[.*?(?:mcq|topic|language|examination|syllabus|insert).*?\]$/i.test(l)) {
      return true;
    }
    return false;
  }

  /**
   * Cleans markdown formatting characters (bold, italics, headers) for clean parsing.
   */
  public static stripMarkdown(text: string): string {
    return text
      .replace(/\*\*(.*?)\*\*/g, '$1')
      .replace(/__(.*?)__/g, '$1')
      .replace(/`([^`]+)`/g, '$1')
      .replace(/^#{1,6}\s+/gm, '')
      .trim();
  }

  /**
   * Extracts exam title, subject, series code, and topics from the header lines.
   */
  private static extractMetadata(rawText: string): PaperMetadata {
    const lines = rawText.split('\n').map((l) => l.trim()).filter(Boolean);
    let title = '';
    let subject = '';
    let topic = '';
    let setNumber = '';
    let targetExam = '';

    const scanLimit = Math.min(35, lines.length);
    for (let i = 0; i < scanLimit; i++) {
      const line = lines[i];
      if (this.isPromptOrInstruction(line)) continue;

      const clean = this.stripMarkdown(line);

      const titleMatch = clean.match(/\b(?:title|paper\s*title|exam\s*name)\s*:\s*(.+)/i);
      if (titleMatch && !title) {
        title = titleMatch[1].trim();
        continue;
      }

      const subjMatch = clean.match(/\bsubject\s*:\s*(.+)/i);
      if (subjMatch && !subject) {
        subject = subjMatch[1].trim();
        continue;
      }

      const topicMatch = clean.match(/\b(?:topic|विषय|unit|chapter)\s*:\s*(.+)/i);
      if (topicMatch && !topic) {
        topic = topicMatch[1].trim();
        continue;
      }

      const examMatch = clean.match(/\b(?:exam|examination|target\s*exam)\s*:\s*(.+)/i);
      if (examMatch && !targetExam) {
        targetExam = examMatch[1].trim();
        continue;
      }

      const setMatch = clean.match(/\b(?:set(?:\s*no\.?|\s*code)?|series)\s*[:\-]?\s*([A-Za-z0-9\-]+)/i);
      if (setMatch && !setNumber) {
        const found = setMatch[1].trim();
        setNumber = found.toUpperCase().startsWith('SET') ? found.toUpperCase() : `Set ${found.toUpperCase()}`;
        continue;
      }

      // If top header has Markdown H1 or H2:
      if (!title && line.startsWith('#')) {
        const candidate = line.replace(/^#+\s*/, '').trim();
        if (!/(?:questions?|answer|explanation|objective|mcq|instructions?)/i.test(candidate) && candidate.length <= 90) {
          title = candidate;
        }
      }
    }

    return {
      id: 'paper-' + Date.now().toString(36),
      title: title || 'Examination Question Paper',
      subject: subject || 'General Studies & Science',
      topic: topic || 'Comprehensive Test',
      setNumber: setNumber || 'Set A',
      targetExam: targetExam || 'Competitive Examination',
      createdAt: Date.now()
    };
  }

  /**
   * Splits input into (1) Questions, (2) Answer Key, and (3) Detailed Explanations.
   */
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

    // Fallback: If no explicit headers exist, scan for a trailing table or key sequence e.g., "1. (A)"
    if (answerStart === null && explanationStart === null) {
      const trailingPattern = /^\s*(?:(?:\*\*|__)?\s*)?(?:Q(?:uestion)?\.?\s*)?1\s*(?:[\.\:\)\-]|(?=\s*[\(\[]?[ABCD][\)\]]?))\s*[\(\[]?[ABCD][\)\]]?/i;
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
      const end = (explanationStart !== null && explanationStart > answerStart) ? explanationStart : lines.length;
      answerText = lines.slice(answerStart + 1, end).join('\n');
    }

    let explanationText = '';
    if (explanationStart !== null) {
      explanationText = lines.slice(explanationStart + 1).join('\n');
    }

    return { questionText, answerText, explanationText };
  }

  /**
   * Parses questions and extracts 4 options (A, B, C, D) supporting all question types.
   */
  private static parseQuestions(questionText: string, warnings: string[]): QuestionItem[] {
    const lines = questionText.split('\n');
    const questionChunks: { startLine: number; num: number; initialText: string }[] = [];

    for (let i = 0; i < lines.length; i++) {
      const match = lines[i].match(this.QUESTION_START_REGEX);

      if (!match) continue;

      const num = parseInt(match[1], 10);
      const text = match[2]?.trim() || '';

      if (isNaN(num) || this.isPromptOrInstruction(text)) continue;

      // A numbered line inside an existing question can be a statement,
      // sub-item, sequence step, or table content rather than a new question.
      if (questionChunks.length > 0) {
        const previousChunk = questionChunks[questionChunks.length - 1];
        const previousLines = lines.slice(previousChunk.startLine, i);

        const previousText = previousLines.join('\n');

        // Check whether the previous block already contains all four options.
        const optionLetters = new Set<string>();

        for (const previousLine of previousLines) {
          const optionMatch = previousLine.match(this.OPTION_REGEX);
          if (optionMatch) {
            optionLetters.add(optionMatch[1].toLowerCase());
          }
        }

        const hasFourOptions = ['a', 'b', 'c', 'd'].every(letter =>
          optionLetters.has(letter)
        );

        // If the previous question does not yet have four options,
        // treat this numbered line as part of the current question.
        if (!hasFourOptions) {
          continue;
        }
      }

      questionChunks.push({
        startLine: i,
        num,
        initialText: text
      });
    }
    const results: QuestionItem[] = [];

    for (let index = 0; index < questionChunks.length; index++) {
      const current = questionChunks[index];
      const nextStart = (index + 1 < questionChunks.length) ? questionChunks[index + 1].startLine : lines.length;
      const blockLines = lines.slice(current.startLine, nextStart);

      const { stem: rawStem, options, inlineAnswer } = this.extractStemAndOptions(current.initialText, blockLines);

      // Check if all options are provided
      if (!options.a || !options.b || !options.c || !options.d) {
        warnings.push(`Question #${current.num} may be missing one or more options.`);
      }

      const qType = this.classifyQuestionType(rawStem, options);
      const stem = this.cleanStemHeader(rawStem, qType);

      results.push({
        id: `q-${current.num}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`,
        num: current.num,
        question: stem,
        options,
        correctAnswer: inlineAnswer,
        explanation: '',
        questionType: qType
      });
    }

    return results;
  }

  private static cleanStemHeader(stem: string, _qType: QuestionType): string {
    let clean = stem;
    const headingPatterns = [
      /^\s*(?:#{1,6}\s*)?Assertion[–\- ]Reason\s*[:\.\-]?\s*\n+/i,
      /^\s*(?:#{1,6}\s*)?अभिकथन[–\- ]कारण\s*[:\.\-]?\s*\n+/i,
      /^\s*(?:#{1,6}\s*)?Match\s+the\s+Following\s*[:\.\-]?\s*\n+/i,
      /^\s*(?:#{1,6}\s*)?Multiple\s+Choice\s+Questions?\s*[:\.\-]?\s*\n+/i,
      /^\s*(?:#{1,6}\s*)?Objective\s+Type\s+Questions?\s*[:\.\-]?\s*\n+/i,
      /^\s*(?:#{1,6}\s*)?Integrated\s+Multi-Concept\s+Question\s*[:\.\-]?\s*\n+/i
    ];
    for (const pattern of headingPatterns) {
      if (pattern.test(clean)) {
        clean = clean.replace(pattern, '').trim();
      }
    }
    return clean;
  }

  /**
   * Robustly separates Question Stem from the final 4 Options.
   * Handles:
   * - Match the Following with matching tables / lists in stem
   * - Assertion–Reason where 'Assertion (A)' is part of the stem
   * - Multiple numbered statements (1, 2, 3, 4) in the stem
   * - Horizontal options: (a) opt1 (b) opt2 / (c) opt3 (d) opt4
   * - Inline answers at the bottom of questions
   */
  private static extractStemAndOptions(
    initialText: string,
    blockLines: string[]
  ): { stem: string; options: QuestionOptions; inlineAnswer: CorrectOption | null } {
    // Gather all lines in the block
    const allLines: string[] = [];
    if (initialText && initialText.trim().length > 0) {
      allLines.push(initialText);
    }
    for (let j = 1; j < blockLines.length; j++) {
      allLines.push(blockLines[j]);
    }

    let inlineAnswer: CorrectOption | null = null;
    const options: QuestionOptions = { a: '', b: '', c: '', d: '' };

    // 1. Check for trailing inline answer tag at the bottom (e.g. "**Correct. (a) ...**" or "Ans: B")
    const cleanLines: string[] = [];
    for (let i = 0; i < allLines.length; i++) {
      const line = allLines[i].trim();
      const inlineMatch = line.match(/(?:\*\*)?(?:Correct\s*(?:Option|Answer)?|Ans(?:wer)?)\s*[:\.]?\s*[\(\[]?([A-D])[\)\]]?/i);
      if (inlineMatch && i >= allLines.length - 2) {
        inlineAnswer = inlineMatch[1].toUpperCase() as CorrectOption;
        // Don't include this line in stem or options
      } else {
        cleanLines.push(allLines[i]);
      }
    }

    // 2. Try to find horizontal options in the last lines (e.g. (a) ... (b) ... on one line, (c) ... (d) ... on next)
    for (let k = cleanLines.length - 1; k >= 0; k--) {
      const line = cleanLines[k].trim();
      if (!line) continue;

      const inlineGrid = this.extractHorizontalOptions(line);
      if (inlineGrid) {
        // If line has 4 options:
        if (inlineGrid.a && inlineGrid.b && inlineGrid.c && inlineGrid.d) {
          options.a = inlineGrid.a;
          options.b = inlineGrid.b;
          options.c = inlineGrid.c;
          options.d = inlineGrid.d;
          const stem = cleanLines.slice(0, k).join('\n').trim();
          return { stem, options, inlineAnswer };
        }
        // If line has c and d, check if previous non-empty line has a and b
        if (inlineGrid.c && inlineGrid.d && !inlineGrid.a && !inlineGrid.b) {
          let prevIdx = k - 1;
          while (prevIdx >= 0 && !cleanLines[prevIdx].trim()) prevIdx--;
          if (prevIdx >= 0) {
            const prevGrid = this.extractHorizontalOptions(cleanLines[prevIdx]);
            if (prevGrid && prevGrid.a && prevGrid.b) {
              options.a = prevGrid.a;
              options.b = prevGrid.b;
              options.c = inlineGrid.c;
              options.d = inlineGrid.d;
              const stem = cleanLines.slice(0, prevIdx).join('\n').trim();
              return { stem, options, inlineAnswer };
            }
          }
        }
      }
    }

    // 3. Scan backwards from the bottom to find the LAST set of vertical options: (d), (c), (b), (a)
    let dIdx = -1, cIdx = -1, bIdx = -1, aIdx = -1;

    for (let i = cleanLines.length - 1; i >= 0; i--) {
      const line = cleanLines[i].trim();
      if (!line) continue;

      // Skip lines that are Assertion, Statement, or Markdown headers
      if (/^\s*(?:Assertion|अभिकथन|Statement)\s*[\(\[]?[A-D][\)\]]?/i.test(line)) {
        continue;
      }

      const match = line.match(this.OPTION_REGEX);
      if (match) {
        const letter = match[1].toLowerCase();
        if (letter === 'd' && dIdx === -1) {
          dIdx = i;
        } else if (letter === 'c' && dIdx !== -1 && cIdx === -1 && i < dIdx) {
          cIdx = i;
        } else if (letter === 'b' && cIdx !== -1 && bIdx === -1 && i < cIdx) {
          bIdx = i;
        } else if (letter === 'a' && bIdx !== -1 && aIdx === -1 && i < bIdx) {
          aIdx = i;
          break; // Found full set in reverse!
        }
      }
    }

    if (aIdx !== -1 && bIdx !== -1 && cIdx !== -1 && dIdx !== -1) {
      // Collect option texts including multi-line continuations
      const aLines = cleanLines.slice(aIdx, bIdx);
      const bLines = cleanLines.slice(bIdx, cIdx);
      const cLines = cleanLines.slice(cIdx, dIdx);
      const dLines = cleanLines.slice(dIdx);

      options.a = this.cleanOptionText(aLines, 'a');
      options.b = this.cleanOptionText(bLines, 'b');
      options.c = this.cleanOptionText(cLines, 'c');
      options.d = this.cleanOptionText(dLines, 'd');

      const stem = cleanLines.slice(0, aIdx).join('\n').trim();
      return { stem, options, inlineAnswer };
    }

    // 4. Fallback: Standard top-down option parsing
    let currentKey: 'a' | 'b' | 'c' | 'd' | null = null;
    const stemLines: string[] = [];

    for (let i = 0; i < cleanLines.length; i++) {
      const line = cleanLines[i].trim();
      if (!line) continue;

      if (/^\s*(?:Assertion|अभिकथन|Statement)\s*[\(\[]?[A-D][\)\]]?/i.test(line)) {
        stemLines.push(cleanLines[i]);
        continue;
      }

      const optMatch = line.match(this.OPTION_REGEX);
      if (optMatch) {
        const letter = optMatch[1].toLowerCase() as 'a' | 'b' | 'c' | 'd';
        currentKey = letter;
        options[letter] = optMatch[2].trim();
        continue;
      }

      if (currentKey) {
        options[currentKey] = (options[currentKey] + ' ' + line).trim();
      } else {
        stemLines.push(cleanLines[i]);
      }
    }

    return {
      stem: stemLines.join('\n').trim(),
      options,
      inlineAnswer
    };
  }

  private static cleanOptionText(lines: string[], letter: string): string {
    if (lines.length === 0) return '';
    const firstLine = lines[0].replace(new RegExp(`^\\s*(?:(?:\\*\\*|__)?\\s*)?[\\(\\[]?${letter}[\\)\\]\\.\\:]\\s*`, 'i'), '');
    const remaining = lines.slice(1).map(l => l.trim()).filter(Boolean);
    // Preserve line breaks for multi-line options so they don't collapse into a one-liner
    return [firstLine.trim(), ...remaining].join('\n').trim();
  }

  /**
   * Non-backtracking linear extraction of horizontally packed options:
   * (a) opt1 (b) opt2 (c) opt3 (d) opt4
   * or line 1: (a) opt1 (b) opt2
   * and line 2: (c) opt3 (d) opt4
   */
  private static extractHorizontalOptions(line: string): { a?: string; b?: string; c?: string; d?: string } | null {
    if (!line) return null;

    // Full 4-option single line: (a) ... (b) ... (c) ... (d) ...
    const match4 = line.match(/^\s*[\(\[]?[Aa][\)\]\.\:]\s*(.*?)\s+[\(\[]?[Bb][\)\]\.\:]\s*(.*?)\s+[\(\[]?[Cc][\)\]\.\:]\s*(.*?)\s+[\(\[]?[Dd][\)\]\.\:]\s*(.*?)\s*$/);
    if (match4) {
      return { a: match4[1].trim(), b: match4[2].trim(), c: match4[3].trim(), d: match4[4].trim() };
    }

    // 2-option line: (a) ... (b) ...
    const matchAB = line.match(/^\s*[\(\[]?[Aa][\)\]\.\:]\s*(.*?)\s+[\(\[]?[Bb][\)\]\.\:]\s*(.*?)\s*$/);
    if (matchAB) {
      return { a: matchAB[1].trim(), b: matchAB[2].trim() };
    }

    // 2-option line: (c) ... (d) ...
    const matchCD = line.match(/^\s*[\(\[]?[Cc][\)\]\.\:]\s*(.*?)\s+[\(\[]?[Dd][\)\]\.\:]\s*(.*?)\s*$/);
    if (matchCD) {
      return { c: matchCD[1].trim(), d: matchCD[2].trim() };
    }

    return null;
  }

  /**
   * Classifies question into one of the 30 standardized examination question types.
   */
  public static classifyQuestionType(questionStem: string, options: QuestionOptions): QuestionType {
    const stem = questionStem.toLowerCase();
    const opts = `${options.a} ${options.b} ${options.c} ${options.d}`.toLowerCase();

    // 1. Assertion-Reason
    if (
      (stem.includes('assertion') && (stem.includes('reason') || stem.includes('(r)'))) || 
      (stem.includes('अभिकथन') && (stem.includes('कारण') || stem.includes('(r)'))) ||
      opts.includes('both a and r') ||
      opts.includes('both assertion') ||
      opts.includes('correct explanation of a') ||
      opts.includes('r correctly explains') ||
      opts.includes('r is not the correct explanation') ||
      opts.includes('a is true, but r is false') ||
      opts.includes('a is false, but r is true') ||
      opts.includes('assertion (a) is true') ||
      opts.includes('assertion is true') ||
      opts.includes('reason (r) is true')
    ) {
      return 'Assertion–Reason';
    }

    // 2. Match the Following
    if (stem.includes('match the following') || stem.includes('match column') || 
        stem.includes('list-i') || stem.includes('list i') || stem.includes('column i') ||
        /(?:[a-d][\-–—:][1-4].*){2,}/i.test(opts)) {
      return 'Match the Following';
    }

    // 3. Chronological Order
    if (stem.includes('chronological') || stem.includes('chronology') || stem.includes('order of occurrence')) {
      return 'Chronological Order';
    }

    // 4. Ascending/Descending Order
    if (stem.includes('ascending') || stem.includes('descending') || stem.includes('increasing order') || stem.includes('decreasing order')) {
      return 'Ascending/Descending Order';
    }

    // 5. Sequence/Process
    if (stem.includes('sequence') || stem.includes('pathway') || stem.includes('stages') || stem.includes('steps of') || stem.includes('arrange') ||
        opts.includes('→') || opts.includes('->')) {
      return 'Sequence/Process';
    }

    // 6. Case-Based
    if (stem.includes('case-based') || stem.includes('case study') || stem.includes('scenario') || stem.includes('passage') || stem.includes('study the following')) {
      return 'Case-Based';
    }

    // 7. Numerical/Calculation
    if (stem.includes('calculate') || stem.includes('numerical') || stem.includes('hardy–weinberg') || stem.includes('hardy-weinberg') ||
        stem.includes('frequency of') || stem.includes('velocity') || stem.includes('capacitance') || /\d+\s*[%×\^]/.test(stem)) {
      return 'Numerical/Calculation';
    }

    // 8. Data Interpretation
    if (stem.includes('data interpretation') || stem.includes('graph') || stem.includes('chart') || stem.includes('table above')) {
      return 'Data Interpretation';
    }

    // 9. Map/Geography-Based
    if (stem.includes('tributary') || stem.includes('river') || stem.includes('strait') || stem.includes('plateau') || stem.includes('pass') || stem.includes('latitude') || stem.includes('longitude') || stem.includes('border')) {
      return 'Map/Geography-Based';
    }

    // 10. Image/Diagram-Based
    if (stem.includes('diagram') || stem.includes('figure') || stem.includes('image') || stem.includes('labeled as')) {
      return 'Image/Diagram-Based';
    }

    // 11. Pair of Statements
    if ((stem.includes('statement i') && stem.includes('statement ii')) || (stem.includes('statement 1') && stem.includes('statement 2'))) {
      return 'Pair of Statements';
    }

    // 12. Multiple Statements + Combination
    if (stem.includes('statements given above') || stem.includes('which of the above') || stem.includes('which combination') ||
        /(?:1\s+and\s+2|only\s+1|1,\s*2\s+and\s+4|1,\s*2\s+and\s+3)/i.test(opts)) {
      return 'Multiple Statements + Combination';
    }

    // 13. Incorrect / Not Correct
    if (stem.includes('not correct') || stem.includes('incorrect') || stem.includes('not true') || stem.includes('गलत') || stem.includes('असत्य')) {
      return 'Incorrect / Not Correct';
    }

    // 14. Correctly Matched / Pair-Based
    if (stem.includes('correctly matched') || stem.includes('correct pair')) {
      return 'Correctly Matched';
    }

    if (stem.includes('pair') || stem.includes('pairs')) {
      return 'Pair-Based';
    }

    // 15. Cause–Effect
    if (stem.includes('cause') && (stem.includes('effect') || stem.includes('consequence') || stem.includes('result in'))) {
      return 'Cause–Effect';
    }

    // 16. Odd-One-Out
    if (stem.includes('odd one out') || stem.includes('does not belong') || stem.includes('odd entity')) {
      return 'Odd-One-Out';
    }

    // 17. Legal/Constitutional Application
    if (stem.includes('constitution') || stem.includes('article') || stem.includes('amendment') || stem.includes('supreme court') || stem.includes('statute')) {
      return 'Legal/Constitutional Application';
    }

    // 18. Policy/Institution-Based
    if (stem.includes('ministry') || stem.includes('commission') || stem.includes('statutory body') || stem.includes('headquarters') || stem.includes('mandate')) {
      return 'Policy/Institution-Based';
    }

    // 19. Term–Definition
    if (stem.includes('defined as') || stem.includes('refers to') || stem.includes('known as') || stem.includes('term')) {
      return 'Term–Definition';
    }

    // 20. Example-Based
    if (stem.includes('example of') || stem.includes('illustrates') || stem.includes('exemplified')) {
      return 'Example-Based';
    }

    // 21. Classification
    if (stem.includes('classify') || stem.includes('classification') || stem.includes('belongs to the family') || stem.includes('phylum') || stem.includes('category')) {
      return 'Classification';
    }

    // 22. Negative-Logic Question
    if (stem.includes('how many of the above are incorrect') || stem.includes('which of the following is false')) {
      return 'Negative-Logic Question';
    }

    // 23. Quantitative Comparison
    if (stem.includes('greater than') || stem.includes('less than') || stem.includes('compare the')) {
      return 'Quantitative Comparison';
    }

    // 24. Statement-Based
    if (stem.includes('statement') || stem.includes('consider the following')) {
      return 'Statement-Based';
    }

    // 25. Conceptual/Application
    if (stem.includes('principle') || stem.includes('phenomenon') || stem.includes('applied') || stem.includes('application')) {
      return 'Conceptual/Application';
    }

    return 'Direct Fact';
  }

  /**
   * Parses Markdown tables, column lists, and keyValue maps for Answer Keys.
   * Supports: "1. **(b)**", "**1. (b)**", "1. (B)", "1: B", "| 1 | B |"
   */
  private static parseAnswerKeys(text: string): Map<number, CorrectOption> {
    const map = new Map<number, CorrectOption>();
    if (!text) return map;

    // Pattern 1: Standalone pairs: "1. **(b)**", "**1. (a)**", "1. (B)", "1: B", "1. B"
    const singleAnswerRegex = /(?:(?:\*\*|__)?\s*(?:Q(?:uestion)?\.?\s*)?(\d{1,4})\s*(?:\*\*|__)?\s*(?:[\.\:\)\-]|\s*\|\s*)\s*(?:\*\*|__)?\s*[\(\[]?([A-Da-d])[\)\]]?(?:\*\*|__)?)/gi;
    let match: RegExpExecArray | null;

    while ((match = singleAnswerRegex.exec(text)) !== null) {
      const qNum = parseInt(match[1], 10);
      const ans = match[2].toUpperCase() as CorrectOption;
      if (!isNaN(qNum) && !map.has(qNum)) {
        map.set(qNum, ans);
      }
    }

    // Pattern 2: Markdown table rows e.g. "| 1 | A | 6 | C |"
    const lines = text.split('\n');
    for (const line of lines) {
      if (line.includes('|')) {
        const cells = line.split('|').map(c => c.replace(/\*\*|__/g, '').trim()).filter(Boolean);
        for (let k = 0; k < cells.length - 1; k++) {
          const numCandidate = parseInt(cells[k], 10);
          const ansCandidate = cells[k + 1].toUpperCase();
          if (!isNaN(numCandidate) && /^[ABCD]$/.test(ansCandidate) && !map.has(numCandidate)) {
            map.set(numCandidate, ansCandidate as CorrectOption);
          }
        }
      }
    }

    return map;
  }

  /**
   * Parses Detailed Explanations associated with question numbers.
   * Also extracts correct answers derived from explanations like:
   * "(b) ... — Correct.", "(b) ... — **Correct.**", "Correct. (a)"
   */
  private static parseExplanations(text: string): { explanations: Map<number, string>; derivedAnswers: Map<number, CorrectOption> } {
    const explanations = new Map<number, string>();
    const derivedAnswers = new Map<number, CorrectOption>();
    if (!text) return { explanations, derivedAnswers };

    const lines = text.split('\n');
    let currentNum: number | null = null;
    let currentExp: string[] = [];

    const flush = () => {
      if (currentNum !== null && currentExp.length > 0) {
        const fullText = currentExp.join('\n').trim();
        explanations.set(currentNum, fullText);

        // Derive correct answer if present in explanation text:
        // E.g. "(b) ... — **Correct.**" or "(b) ... — Correct." or "Correct. (a)" or "Correct Answer: (B)"
        const matchAns = fullText.match(/(?:Correct\s*(?:Answer|Option)?\s*[:\.]?\s*[\(\[]?([A-D])[\)\]]?)|(?:[\(\[]?([A-D])[\)\]]?\s*[^—\n]*[—\-]\s*(?:\*\*)?\s*Correct\b)/i);
        if (matchAns) {
          const letter = (matchAns[1] || matchAns[2]).toUpperCase() as CorrectOption;
          derivedAnswers.set(currentNum, letter);
        }
      }
      currentExp = [];
    };

    // Heading matches: "### 1. ...", "50. Chromosomes after meiosis I", "1. Assertion–Reason", "99. PCR primers"
    const expStartRegex = /^\s*(?:#{1,6}\s*)?(?:(?:\*\*|__)?\s*)?(?:Q(?:uestion)?\.?\s*)?(\d{1,4})\s*[\.\:\)\-]\s*(.*)$/i;

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
        if (match[2]) {
          currentExp.push(match[2].trim());
        }
      } else if (currentNum !== null) {
        currentExp.push(trimmed);
      }
    }
    flush();

    return { explanations, derivedAnswers };
  }

  private static createDefaultMetadata(): PaperMetadata {
    return {
      id: 'paper-' + Date.now().toString(36),
      title: 'Examination Paper',
      subject: 'General Knowledge',
      topic: 'Standard Assessment',
      setNumber: 'Set A',
      targetExam: 'All India Test',
      createdAt: Date.now()
    };
  }
}

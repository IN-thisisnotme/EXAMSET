export interface MathSegment {
  content: string;
  isMath: boolean;
  isDisplay?: boolean;
}

const GREEK_SYMBOLS: Record<string, string> = {
  '\\alpha': 'α', '\\beta': 'β', '\\gamma': 'γ', '\\delta': 'δ',
  '\\epsilon': 'ε', '\\varepsilon': 'ε', '\\zeta': 'ζ', '\\eta': 'η',
  '\\theta': 'θ', '\\vartheta': 'ϑ', '\\iota': 'ι', '\\kappa': 'κ',
  '\\lambda': 'λ', '\\mu': 'μ', '\\micro': 'μ', '\\nu': 'ν', '\\xi': 'ξ',
  '\\pi': 'π', '\\varpi': 'ϖ', '\\rho': 'ρ', '\\varrho': 'ϱ',
  '\\sigma': 'σ', '\\varsigma': 'ς', '\\tau': 'τ',
  '\\upsilon': 'υ', '\\phi': 'ϕ', '\\varphi': 'φ', '\\chi': 'χ',
  '\\psi': 'ψ', '\\omega': 'ω',
  '\\Gamma': 'Γ', '\\Delta': 'Δ', '\\Theta': 'Θ', '\\Lambda': 'Λ',
  '\\Xi': 'Ξ', '\\Pi': 'Π', '\\Sigma': 'Σ', '\\Upsilon': 'Υ',
  '\\Phi': 'Φ', '\\Psi': 'Ψ', '\\Omega': 'Ω', '\\ohm': 'Ω',
};

const MATH_OPERATORS: Record<string, string> = {
  '\\sin': 'sin', '\\cos': 'cos', '\\tan': 'tan', '\\cot': 'cot',
  '\\sec': 'sec', '\\csc': 'csc', '\\arcsin': 'arcsin', '\\arccos': 'arccos',
  '\\arctan': 'arctan', '\\sinh': 'sinh', '\\cosh': 'cosh', '\\tanh': 'tanh',
  '\\log': 'log', '\\ln': 'ln', '\\exp': 'exp', '\\lim': 'lim',
  '\\max': 'max', '\\min': 'min', '\\sup': 'sup', '\\inf': 'inf',
  '\\det': 'det', '\\gcd': 'gcd', '\\ker': 'ker', '\\dim': 'dim',
  '\\mod': 'mod', '\\Pr': 'Pr',
  '\\times': '×', '\\div': '÷', '\\pm': '±', '\\mp': '∓',
  '\\cdot': '·', '\\ast': '∗', '\\star': '⋆', '\\circ': '°', '\\bullet': '•',
  '\\leq': '≤', '\\le': '≤', '\\geq': '≥', '\\ge': '≥',
  '\\neq': '≠', '\\ne': '≠', '\\approx': '≈', '\\equiv': '≡',
  '\\cong': '≅', '\\sim': '∼', '\\simeq': '≃', '\\asymp': '≍', '\\propto': '∝',
  '\\to': '→', '\\rightarrow': '→', '\\leftarrow': '←', '\\leftrightarrow': '↔',
  '\\Rightarrow': '⇒', '\\Leftarrow': '⇐', '\\Leftrightarrow': '⇔', '\\mapsto': '↦',
  '\\longrightarrow': '⟶', '\\longleftarrow': '⟵', '\\longleftrightarrow': '⟷',
  '\\Longrightarrow': '⟹', '\\Longleftarrow': '⟸', '\\Longleftrightarrow': '⟺',
  '\\implies': '⟹', '\\iff': '⟺',
  '\\infty': '∞', '\\partial': '∂', '\\nabla': '∇', '\\prime': '′',
  '\\int': '∫', '\\iint': '∬', '\\iiint': '∭', '\\oint': '∮',
  '\\sum': '∑', '\\prod': '∏', '\\coprod': '∐',
  '\\in': '∈', '\\notin': '∉', '\\ni': '∋', '\\notni': '∌',
  '\\subset': '⊂', '\\supset': '⊃', '\\subseteq': '⊆', '\\supseteq': '⊇',
  '\\subsetneq': '⊊', '\\supsetneq': '⊋', '\\cup': '∪', '\\cap': '∩',
  '\\bigcup': '⋃', '\\bigcap': '⋂', '\\setminus': '∖', '\\emptyset': '∅',
  '\\forall': '∀', '\\exists': '∃', '\\nexists': '∄', '\\neg': '¬', '\\lnot': '¬',
  '\\land': '∧', '\\lor': '∨', '\\wedge': '∧', '\\vee': '∨',
  '\\aleph': 'ℵ', '\\hbar': 'ℏ', '\\Re': 'ℜ', '\\Im': 'ℑ',
  '\\degree': '°', '\\angle': '∠', '\\triangle': '△', '\\square': '□',
  '\\perp': '⊥', '\\parallel': '∥',
  '\\rightleftharpoons': '⇌', '\\leftrightharpoons': '⇋',
  '\\uparrow': '↑', '\\downarrow': '↓', '\\updownarrow': '↕',
  '\\dots': '…', '\\ldots': '…', '\\cdots': '⋯', '\\vdots': '⋮', '\\ddots': '⋱',
  '\\quad': '  ', '\\qquad': '    ', '\\,': ' ', '\\;': ' ', '\\:': ' ', '\\!': '',
  '\\therefore': '∴', '\\because': '∵', '\\checkmark': '✓',
  '\\copyright': '©', '\\dagger': '†', '\\ddagger': '‡',
};

const ALL_SYMBOL_MAP: Record<string, string> = { ...MATH_OPERATORS, ...GREEK_SYMBOLS };

const NUMBER_SETS: Record<string, string> = {
  R: 'ℝ', N: 'ℕ', Z: 'ℤ', Q: 'ℚ', C: 'ℂ', H: 'ℍ', P: 'ℙ',
};

function unwrapCommandWithBraces(text: string, command: string, transform: (inner: string) => string): string {
  let result = text;
  const prefix = `\\${command}`;
  let idx = result.indexOf(prefix);
  let guard = 0;

  while (idx !== -1 && guard < 60) {
    guard++;
    // Ensure command boundary (next char is whitespace or '{')
    const afterCmd = result[idx + prefix.length];
    if (afterCmd && /[a-zA-Z]/.test(afterCmd)) {
      idx = result.indexOf(prefix, idx + prefix.length);
      continue;
    }

    let braceStart = idx + prefix.length;
    while (braceStart < result.length && /\s/.test(result[braceStart])) {
      braceStart++;
    }
    if (braceStart < result.length && result[braceStart] === '{') {
      let depth = 1;
      let cur = braceStart + 1;
      while (cur < result.length && depth > 0) {
        if (result[cur] === '{') depth++;
        else if (result[cur] === '}') depth--;
        if (depth > 0) cur++;
      }
      if (depth === 0) {
        const inner = result.slice(braceStart + 1, cur);
        const replacement = transform(inner);
        result = result.slice(0, idx) + replacement + result.slice(cur + 1);
        idx = result.indexOf(prefix);
        continue;
      }
    }
    idx = result.indexOf(prefix, idx + prefix.length);
  }
  return result;
}

function formatFractionPart(raw: string): string {
  const trimmed = raw.trim();
  if (!trimmed) return '';
  if (trimmed.startsWith('(') && trimmed.endsWith(')')) return trimmed;
  const isCompound = /[ +\-/*×·÷]|\\times|\\cdot|\\div/.test(trimmed);
  return isCompound ? `(${trimmed})` : trimmed;
}

function replaceFractionsWithBalancedBraces(text: string): string {
  let expr = text;
  let loop = 0;

  while ((expr.includes('\\frac') || expr.includes('\\dfrac')) && loop < 50) {
    loop++;
    let changed = false;

    for (const cmd of ['\\frac', '\\dfrac']) {
      let idx = expr.indexOf(cmd);
      while (idx !== -1) {
        let numBraceStart = idx + cmd.length;
        while (numBraceStart < expr.length && /\s/.test(expr[numBraceStart])) numBraceStart++;
        if (numBraceStart < expr.length && expr[numBraceStart] === '{') {
          let depth = 1;
          let numCur = numBraceStart + 1;
          while (numCur < expr.length && depth > 0) {
            if (expr[numCur] === '{') depth++;
            else if (expr[numCur] === '}') depth--;
            if (depth > 0) numCur++;
          }
          if (depth === 0) {
            const numContent = expr.slice(numBraceStart + 1, numCur).trim();
            let denBraceStart = numCur + 1;
            while (denBraceStart < expr.length && /\s/.test(expr[denBraceStart])) denBraceStart++;
            if (denBraceStart < expr.length && expr[denBraceStart] === '{') {
              depth = 1;
              let denCur = denBraceStart + 1;
              while (denCur < expr.length && depth > 0) {
                if (expr[denCur] === '{') depth++;
                else if (expr[denCur] === '}') depth--;
                if (depth > 0) denCur++;
              }
              if (depth === 0) {
                const denContent = expr.slice(denBraceStart + 1, denCur).trim();
                const replacement = `${formatFractionPart(numContent)} / ${formatFractionPart(denContent)}`;
                expr = expr.slice(0, idx) + replacement + expr.slice(denCur + 1);
                changed = true;
                break;
              }
            }
          }
        }
        idx = expr.indexOf(cmd, idx + cmd.length);
      }
      if (changed) break;
    }

    if (!changed) {
      const singleReplaced = expr.replace(/\\(?:frac|dfrac)\s+([A-Za-z0-9])\s+([A-Za-z0-9])/g, '$1 / $2');
      if (singleReplaced !== expr) {
        expr = singleReplaced;
      } else {
        const unbraced = expr.replace(/\\(?:frac|dfrac)\s*([0-9])\s*([0-9])/g, '$1 / $2');
        if (unbraced !== expr) expr = unbraced;
        else break;
      }
    }
  }
  return expr;
}

function formatChemistry(formula: string): string {
  let result = formula.trim();
  result = result.replace(/_\{([^{}]+)\}/g, '<sub>$1</sub>');
  result = result.replace(/_([0-9]+)/g, '<sub>$1</sub>');
  result = result.replace(/([A-Z][a-z]?|[)\]])([0-9]+)/g, '$1<sub>$2</sub>');
  result = result.replace(/\^\{([^{}]+)\}/g, '<sup>$1</sup>');
  result = result.replace(/\^([0-9+-]+)/g, '<sup>$1</sup>');
  result = result
    .replace(/<=>/g, '⇌')
    .replace(/<->/g, '⇌')
    .replace(/->/g, '→')
    .replace(/<-/g, '←');
  return result;
}

function replaceLatexEnvironment(
  text: string,
  environment: string,
  formatter: (rows: string[][]) => string
): string {
  const pattern = new RegExp(`\\\\begin\\{${environment}\\}(?:\\{[^}]*\\})?([\\s\\S]*?)\\\\end\\{${environment}\\}`, 'g');
  return text.replace(pattern, (_match, rawBody: string) => {
    const normalizedBody = rawBody
      .trim()
      .replace(/\\\[\s*\d+\s*(?:pt|em|ex|mm)?\s*\]/g, '')
      .replace(/\\\s*$/gm, '\\\\')
      .replace(/(\d)\\(?=\d)/g, '$1\\\\')
      .replace(/(\d)\\\\(?=\d)/g, '$1 \\\\ ');

    const rowStrings =
      normalizedBody.includes('\\\\') || normalizedBody.includes('\\cr')
        ? normalizedBody.split(/\\\\|\\cr\b/)
        : normalizedBody.split(/\r?\n/);

    const rows = rowStrings
      .map((r) =>
        r
          .trim()
          .split('&')
          .map((c) => c.trim().replace(/\s+/g, ' '))
          .filter(Boolean)
      )
      .filter((r) => r.length > 0);

    return formatter(rows);
  });
}

function replaceStructuredLatex(expr: string): string {
  let result = expr;

  result = result.replace(/\\binom\{([^{}]+)\}\{([^{}]+)\}/g, '($1 choose $2)');
  result = result.replace(/\\vec\{([^{}]+)\}/g, '$1\u20D7');
  result = result.replace(/\\bar\{([^{}]+)\}/g, '$1\u0304');
  result = result.replace(/\\hat\{([^{}]+)\}/g, '$1\u0302');
  result = result.replace(/\\dot\{([^{}]+)\}/g, '$1\u0307');

  result = result.replace(/\\vec\s+([A-Za-z0-9])/g, '$1\u20D7');
  result = result.replace(/\\bar\s+([A-Za-z0-9])/g, '$1\u0304');
  result = result.replace(/\\hat\s+([A-Za-z0-9])/g, '$1\u0302');
  result = result.replace(/\\dot\s+([A-Za-z0-9])/g, '$1\u0307');

  result = result.replace(/\\mathbb\{([A-Za-z])\}/g, (_m, k) => NUMBER_SETS[k] || k);
  result = result.replace(/\\ce\{((?:[^{}]+|\{[^{}]*\})*)\}/g, (_m, inner) => formatChemistry(inner));

  result = replaceLatexEnvironment(result, 'matrix', (rows) => rows.map((r) => r.join('  ')).join(' ; '));
  result = replaceLatexEnvironment(result, 'pmatrix', (rows) => `( ${rows.map((r) => r.join('  ')).join(' ; ')} )`);
  result = replaceLatexEnvironment(result, 'bmatrix', (rows) => `[ ${rows.map((r) => r.join('  ')).join(' ; ')} ]`);
  result = replaceLatexEnvironment(result, 'cases', (rows) => `{ ${rows.map((r) => r.join('  ')).join(' ; ')} }`);
  result = replaceLatexEnvironment(result, 'aligned', (rows) => rows.map((r) => r.join('  ')).join('\n'));
  result = replaceLatexEnvironment(result, 'array', (rows) => rows.map((r) => r.join('    ')).join('\n'));

  return result;
}

export const LatexHelper = {
  normalizeLatexAndMarkdown(text: string): string {
    if (!text) return text;
    let res = text;
    res = res.replace(/\\\*\\\*/g, '**');
    res = res.replace(/\\_\\_/g, '__');
    res = res.replace(/(?<!\\)\\\*(?!\*)/g, '*');
    res = res.replace(/\\\|/g, '|');
    res = res.replace(/^\|\s*([A-Za-z\u0900-\u097F\s&:\-]+?)\s*\|$/gm, '**$1**');
    res = res.replace(/(?<!\\),\s*\\+(?![a-zA-Z])\s*/g, ', ');
    res = res.replace(/\\{2,}(?!(?:hline\b|cr\b))([a-zA-Z]+)/g, '\\$1');
    res = res.replace(/\\{2,}([\[\]\(\),;!:\{\}])/g, '\\$1');
    return res;
  },

  hasLatex(text: string): boolean {
    if (!text || !text.trim()) return false;
    const normalized = this.normalizeLatexAndMarkdown(text);
    if (normalized.includes('$') || normalized.includes('\\(') || normalized.includes('\\[')) {
      return true;
    }
    if (/\\(?:text|mathrm|operatorname|mathbf|mathit|textbf|textit|frac|dfrac|sqrt|binom|vec|bar|hat|dot|mathbb|ce|begin|end|degree|circ|displaystyle|displaytext|boxed|boldsymbol|array)\b/.test(normalized)) {
      return true;
    }
    const cmds = normalized.match(/\\[A-Za-z]+|\\[,;!:]/g) || [];
    if (cmds.some((c) => ALL_SYMBOL_MAP[c] !== undefined)) return true;
    if (/(?:[A-Za-z0-9)\]}])(?:\^|_)(?:\{[^{}]+\}|[A-Za-z0-9+-]+|\\[A-Za-z]+)/.test(normalized)) return true;
    return false;
  },

  segmentLatex(text: string): MathSegment[] {
    if (!text) return [];
    const normalized = this.normalizeLatexAndMarkdown(text);
    if (!this.hasLatex(normalized)) return [{ content: normalized, isMath: false }];

    const segments: MathSegment[] = [];
    const pattern = /(\$\$([\s\S]+?)\$\$|\\+\[([\s\S]+?)\\+\]|\$([^$\n]+?)\$|\\+\(([\s\S]+?)\\+\))/g;
    let lastIndex = 0;
    let match: RegExpExecArray | null;

    while ((match = pattern.exec(normalized)) !== null) {
      if (match.index > lastIndex) {
        segments.push({ content: normalized.slice(lastIndex, match.index), isMath: false });
      }
      const raw = match[0];
      let content = raw;
      let isDisplay = false;
      if (raw.startsWith('$$')) {
        content = raw.slice(2, -2);
        isDisplay = true;
      } else if (raw.startsWith('$')) {
        content = raw.slice(1, -1);
      } else if (/^\\+\[/.test(raw)) {
        content = raw.replace(/^\\+\[|\\+\]$/g, '');
        isDisplay = true;
      } else if (/^\\+\(/.test(raw)) {
        content = raw.replace(/^\\+\(|\\+\)$/g, '');
      }
      segments.push({ content: content.trim(), isMath: true, isDisplay });
      lastIndex = match.index + raw.length;
    }

    if (lastIndex < normalized.length) {
      segments.push({ content: normalized.slice(lastIndex), isMath: false });
    }
    return segments;
  },

  formatMathExpression(latex: string): string {
    try {
      const normalized = this.normalizeLatexAndMarkdown(latex);
      let expr = normalized
        .trim()
        .replace(/^\\+\[|\\+\]$/g, '')
        .replace(/^\\+\(|\\+\)$/g, '')
        .replace(/\\\[|\\\]|\\\(|\\\)/g, '');

      expr = replaceStructuredLatex(expr);
      expr = expr.replace(/\\(?:displaystyle|textstyle|scriptstyle|scriptscriptstyle|displaytext)\s*/g, '');
      expr = expr.replace(/\\rm\s*/g, '');

      expr = unwrapCommandWithBraces(expr, 'boxed', (inner) => `[ ${inner} ]`);
      expr = unwrapCommandWithBraces(expr, 'text', (inner) => inner);
      expr = unwrapCommandWithBraces(expr, 'mathrm', (inner) => inner);
      expr = unwrapCommandWithBraces(expr, 'displaytext', (inner) => inner);
      expr = unwrapCommandWithBraces(expr, 'operatorname', (inner) => inner);
      expr = unwrapCommandWithBraces(expr, 'mathbf', (inner) => `<b>${inner}</b>`);
      expr = unwrapCommandWithBraces(expr, 'textbf', (inner) => `<b>${inner}</b>`);
      expr = unwrapCommandWithBraces(expr, 'boldsymbol', (inner) => `<b>${inner}</b>`);
      expr = unwrapCommandWithBraces(expr, 'mathit', (inner) => `<i>${inner}</i>`);
      expr = unwrapCommandWithBraces(expr, 'textit', (inner) => `<i>${inner}</i>`);

      expr = replaceFractionsWithBalancedBraces(expr);

      expr = expr.replace(/\\sqrt\s*\[([^\]]+)\]\s*\{([^{}]+)\}/g, '<sup>$1</sup>√($2)');
      expr = expr.replace(/\\sqrt\s*\{([^{}]+)\}/g, '√($1)');
      expr = expr.replace(/\\sqrt\s+([A-Za-z0-9])/g, '√($1)');

      expr = expr.replace(/\^\{?\\(?:circ|degree)\}?/g, '°');
      expr = expr.replace(/\^\{([^{}]+)\}/g, '<sup>$1</sup>');
      expr = expr.replace(/_\{([^{}]+)\}/g, '<sub>$1</sub>');

      expr = expr.replace(/\\[A-Za-z]+|\\[,;!:]/g, (cmd) => ALL_SYMBOL_MAP[cmd] ?? cmd);

      expr = expr.replace(
        /([A-Za-z0-9)\]}'\u2032\u0370-\u03FF])\^([+-]?[0-9]+|[a-zA-Z\u0370-\u03FF])/g,
        '$1<sup>$2</sup>'
      );
      expr = expr.replace(
        /([A-Za-z0-9)\]}'\u2032\u0370-\u03FF])_([0-9]+|[a-zA-Z\u0370-\u03FF]+\b)/g,
        '$1<sub>$2</sub>'
      );

      expr = expr.replace(/\\(?:quad|qquad)\b/g, '  ');
      expr = expr.replace(/\\[,;! ]/g, ' ');
      expr = expr.replace(/\\left\s*([(\[{|.])?/g, (_m, g) => (g && g !== '.' ? g : ''));
      expr = expr.replace(/\\right\s*([)\]}|.])?/g, (_m, g) => (g && g !== '.' ? g : ''));
      expr = expr.replace(/\\left|\\right/g, '');
      expr = expr.replace(/\\\{/g, '{').replace(/\\\}/g, '}');

      return expr;
    } catch {
      return latex;
    }
  },

  toRichTextString(text: string): string {
    const normalized = this.normalizeLatexAndMarkdown(text);
    if (!this.hasLatex(normalized)) return normalized;

    const prepared = normalized.replace(
      /^[ \t]*\[[ \t]*\r?\n([\s\S]*?)\r?\n[ \t]*\][ \t]*$/gm,
      (full, inner: string) =>
        inner.includes('\\') || inner.includes('_') || inner.includes('^') || inner.includes('&')
          ? `\\[\n${inner}\n\\]`
          : full
    );

    const segments = this.segmentLatex(prepared);
    return segments
      .map((seg) => (seg.isMath ? this.formatMathExpression(seg.content) : this.formatMathExpression(seg.content)))
      .join('');
  },
};

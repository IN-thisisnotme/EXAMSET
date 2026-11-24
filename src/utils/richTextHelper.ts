const SUB_MAP: Record<string, string> = {
  '₀': '0', '₁': '1', '₂': '2', '₃': '3', '₄': '4',
  '₅': '5', '₆': '6', '₇': '7', '₈': '8', '₉': '9',
  '₊': '+', '₋': '-', '₌': '=',
};

const SUP_MAP: Record<string, string> = {
  '⁰': '0', '¹': '1', '²': '2', '³': '3', '⁴': '4',
  '⁵': '5', '⁶': '6', '⁷': '7', '⁸': '8', '⁹': '9',
  '⁺': '+', '⁻': '-', '⁼': '=',
};

export interface RichTextToken {
  text: string;
  bold: boolean;
  italic: boolean;
  code: boolean;
  sub: boolean;
  sup: boolean;
}

export const RichTextHelper = {
  sanitizeSubSuper(text: string): string {
    if (!text) return '';
    let result = text;
    for (const [k, v] of Object.entries(SUB_MAP)) {
      result = result.split(k).join(`<sub>${v}</sub>`);
    }
    for (const [k, v] of Object.entries(SUP_MAP)) {
      result = result.split(k).join(`<sup>${v}</sup>`);
    }
    return result;
  },

  prepareTaggedText(text: string): string {
    if (!text) return '';
    let prepared = text
      .replace(/\\\*\\\*/g, '**')
      .replace(/\\_\\_/g, '__')
      .replace(/(?<!\\)\\\*(?!\*)/g, '*')
      .replace(/\\\|/g, '|')
      .replace(/^\|\s*([A-Za-z\u0900-\u097F\s&:\-]+?)\s*\|$/gm, '**$1**')
      .replace(/(?<!\\),\s*\\+(?![a-zA-Z])\s*/g, ', ');

    prepared = this.sanitizeSubSuper(prepared);
    // Only convert paired __word__ to bold so fill-in-the-blanks like _____ remain intact
    prepared = prepared.replace(/(?<!_)__([^\s_](?:[\s\S]*?[^\s_])?)__(?!_)/g, '<b>$1</b>');
    return prepared;
  },

  tokenize(text: string, defaultBold: boolean = false): RichTextToken[] {
    if (!text) return [];
    const tagged = this.prepareTaggedText(text);
    const regex = /(<b>|<\/b>|<i>|<\/i>|<sub>|<\/sub>|<sup>|<\/sup>|\*\*|\*|`)/gi;
    const parts = tagged.split(regex);

    const out: RichTextToken[] = [];
    let bold = defaultBold;
    let italic = false;
    let code = false;
    let sub = false;
    let sup = false;

    for (const part of parts) {
      if (!part) continue;
      const lower = part.toLowerCase();
      if (lower === '<b>') {
        bold = true;
      } else if (lower === '</b>') {
        bold = defaultBold;
      } else if (lower === '**') {
        bold = !bold;
      } else if (lower === '<i>') {
        italic = true;
      } else if (lower === '</i>') {
        italic = false;
      } else if (lower === '*') {
        italic = !italic;
      } else if (lower === '`') {
        code = !code;
      } else if (lower === '<sub>') {
        sub = true;
      } else if (lower === '</sub>') {
        sub = false;
      } else if (lower === '<sup>') {
        sup = true;
      } else if (lower === '</sup>') {
        sup = false;
      } else {
        out.push({ text: part, bold, italic, code, sub, sup });
      }
    }
    return out;
  },

  escapeHtml(text: string): string {
    return String(text || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  },

  toSafeHtml(text: string): string {
    const tokens = this.tokenize(text);
    return tokens
      .map((t) => {
        let html = this.escapeHtml(t.text).replace(/\n/g, '<br/>');
        if (t.code) html = `<code style="font-family:monospace;background:#f1f5f9;padding:0 3px;border-radius:3px;">${html}</code>`;
        if (t.bold) html = `<strong>${html}</strong>`;
        if (t.italic) html = `<em>${html}</em>`;
        if (t.sub) html = `<sub>${html}</sub>`;
        if (t.sup) html = `<sup>${html}</sup>`;
        return html;
      })
      .join('');
  },
};

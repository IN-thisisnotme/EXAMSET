/**
 * Browser Web Speech Synthesis utility with BCP-47 language resolution
 * and graceful fallback when SpeechSynthesis is unavailable (e.g., restricted WebViews).
 */

const LANGUAGE_BCP47_MAP: Record<string, string> = {
  english: 'en-GB',
  uk: 'en-GB',
  us: 'en-US',
  hindi: 'hi-IN',
  sanskrit: 'hi-IN',
  marathi: 'mr-IN',
  bengali: 'bn-IN',
  tamil: 'ta-IN',
  telugu: 'te-IN',
  gujarati: 'gu-IN',
  kannada: 'kn-IN',
  malayalam: 'ml-IN',
  punjabi: 'pa-IN',
  japanese: 'ja-JP',
  korean: 'ko-KR',
  chinese: 'zh-CN',
  mandarin: 'zh-CN',
  'mandarin chinese': 'zh-CN',
  french: 'fr-FR',
  german: 'de-DE',
  spanish: 'es-ES',
  italian: 'it-IT',
  portuguese: 'pt-PT',
  arabic: 'ar-SA',
  russian: 'ru-RU',
};

export function resolveSpeechLocale(language: string, variant: string = ''): string {
  const normLang = (language || '').trim().toLowerCase();
  const normVar = (variant || '').trim().toUpperCase();
  if (!normLang || normLang.includes('english')) {
    if (normVar === 'US') return 'en-US';
    if (normVar === 'NEUTRAL') return 'en-US';
    return 'en-GB';
  }
  for (const [k, bcp] of Object.entries(LANGUAGE_BCP47_MAP)) {
    if (normLang === k || normLang.includes(k)) return bcp;
  }
  return 'en-US';
}

export function isSpeechSupported(): boolean {
  return (
    typeof window !== 'undefined' &&
    'speechSynthesis' in window &&
    typeof SpeechSynthesisUtterance !== 'undefined'
  );
}

export function cancelSpeech(): void {
  if (!isSpeechSupported()) return;
  try {
    window.speechSynthesis.cancel();
  } catch {
    // ignore
  }
}

export function speakText(
  text: string,
  options: {
    lang?: string;
    rate?: number;
    pitch?: number;
    onEnd?: () => void;
    onError?: () => void;
  } = {}
): { supported: boolean; fallbackTimerId: number | null } {
  const clean = (text || '').trim();
  if (!clean) {
    options.onEnd?.();
    return { supported: isSpeechSupported(), fallbackTimerId: null };
  }

  if (!isSpeechSupported()) {
    const words = clean.split(/\s+/).length;
    const estMs = Math.max(700, Math.min(9000, Math.round((words / 2.4) * 1000)));
    const timer = window.setTimeout(() => {
      options.onEnd?.();
    }, estMs);
    return { supported: false, fallbackTimerId: timer };
  }

  try {
    window.speechSynthesis.cancel();
    const utt = new SpeechSynthesisUtterance(clean);
    utt.lang = options.lang || 'en-GB';
    utt.rate = Math.max(0.5, Math.min(2.0, options.rate ?? 1.0));
    utt.pitch = options.pitch ?? 1.0;

    let settled = false;
    const safeDone = (cb?: () => void) => {
      if (settled) return;
      settled = true;
      cb?.();
    };

    utt.onend = () => safeDone(options.onEnd);
    utt.onerror = () => safeDone(options.onError || options.onEnd);

    window.speechSynthesis.speak(utt);
    return { supported: true, fallbackTimerId: null };
  } catch {
    options.onError?.();
    return { supported: false, fallbackTimerId: null };
  }
}

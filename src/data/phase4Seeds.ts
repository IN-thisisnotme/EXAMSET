import {
  DEFAULT_KNOWLEDGE_RULE,
  type StudyNoteRecord,
  type VocabularyDeck,
  type VocabularyEntry,
} from '../types/phase4';

const now = 1732400000000;

export const STARTER_STUDY_NOTES: StudyNoteRecord[] = [
  {
    id: 'note-starter-physics',
    title: 'Thermodynamics & Electromagnetism Core',
    subject: 'Physics',
    sourceAi: 'ExamSet Starter Sheet',
    folderId: 'default',
    collectionType: 'MULTI_LINER',
    customLabel: '',
    linkedPaperId: null,
    formatRule: {
      ...DEFAULT_KNOWLEDGE_RULE,
      collectionType: 'MULTI_LINER',
      includeLatexMath: true,
      includeCallouts: true,
    },
    bookmarkedIndices: [1],
    srsStates: {},
    createdAt: now,
    updatedAt: now,
    rawContent: `## Core Laws & Conservation
1. **First Law of Thermodynamics** -> Conservation of energy relating heat absorbed ($Q$), internal energy change ($\\Delta U$), and work ($W$).
  - Formula: $\\Delta U = Q - W$
  - Exam Trap: For an isothermal ideal gas process, $\\Delta U = 0$, so $Q = W$.

2. **Lenz's Law** -> Induced EMF always opposes the change in magnetic flux producing it.
  - Formula: $\\mathcal{E} = -N \\frac{d\\Phi_B}{dt}$
  - Mnemonic: Energy conservation forbids self-reinforcing current loops.

> Exam Tip: Always verify adiabatic vs. isothermal PV slopes — the adiabatic curve is steeper by a factor of $\\gamma$.

| Process | Constant Parameter | Internal Energy ($\\Delta U$) | Work Done ($W$) |
| --- | --- | --- | --- |
| Isothermal | Temperature ($T$) | $0$ | $nRT \\ln(V_2/V_1)$ |
| Adiabatic | Heat ($Q = 0$) | $-W$ | $\\frac{P_1 V_1 - P_2 V_2}{\\gamma - 1}$ |
| Isochoric | Volume ($V$) | $n C_v \\Delta T$ | $0$ |`,
  },
  {
    id: 'note-starter-flashcards',
    title: 'Indian Polity Constitutional Articles (SM-2 Deck)',
    subject: 'Polity & Governance',
    sourceAi: 'ExamSet Starter Deck',
    folderId: 'default',
    collectionType: 'FLASHCARDS',
    customLabel: 'Flashcards',
    linkedPaperId: null,
    formatRule: {
      ...DEFAULT_KNOWLEDGE_RULE,
      collectionType: 'FLASHCARDS',
      layerOrder: 'QA_SEPARATE_LINES',
    },
    bookmarkedIndices: [],
    srsStates: {},
    createdAt: now - 3600000,
    updatedAt: now - 3600000,
    rawContent: `## Writs & Constitutional Remedies
Q1: Which Constitutional Article empowers the Supreme Court to issue five prerogative writs for Fundamental Rights?
A: Article 32 — Right to Constitutional Remedies (termed the "Heart and Soul" of the Constitution by Dr. B.R. Ambedkar).
  - Exam Trap: High Courts under Article 226 have wider writ jurisdiction covering ordinary legal rights as well.

Q2: Distinguish Writ of Prohibition from Writ of Certiorari.
A: Prohibition prevents an inferior tribunal from exceeding jurisdiction BEFORE final order; Certiorari quashes an unlawful order AFTER it is passed.
  - Mnemonic: Prohibition = Prevent beforehand; Certiorari = Correct/Quash afterward.

Q3: Under which Article can the President of India seek an advisory opinion from the Supreme Court?
A: Article 143 — optional/non-binding on matters of public importance (143(1)), mandatory advice on pre-1950 treaties (143(2)).`,
  },
];

function mkEntry(
  id: string,
  word: string,
  pos: string,
  pronunciation: string,
  levelCode: VocabularyEntry['levelCode'],
  meaning: string,
  example: string,
  synonyms: string[],
  usageNote: string
): VocabularyEntry {
  return {
    id,
    word,
    partOfSpeech: pos,
    pronunciation,
    levelCode,
    customLevelLabel: '',
    language: 'English',
    languageVariant: 'UK',
    meaning,
    translation: '',
    translationLanguage: '',
    examples: [{ context: 'Editorial', sentence: example, translation: '' }],
    synonyms,
    antonyms: [],
    collocations: [],
    usageNote,
    isBookmarked: false,
    createdAt: now,
    updatedAt: now,
  };
}

export const STARTER_VOCAB_DECKS: VocabularyDeck[] = [
  {
    id: 'vocab-starter-editorial-b2',
    title: 'Editorial & Analytical Precision (B2–C1)',
    language: 'English',
    languageVariant: 'UK',
    translationLanguage: '',
    levelCode: 'B2',
    customLevelLabel: '',
    domain: 'Competitive Exam Editorial',
    createdAt: now,
    updatedAt: now,
    entries: [
      mkEntry(
        've-1',
        'ameliorate',
        'verb',
        '/əˈmiː.li.ə.reɪt/',
        'C1',
        'To make a bad or unsatisfactory situation better or more tolerable.',
        'Targeted fiscal transfers helped ameliorate rural distress during the drought.',
        ['mitigate', 'alleviate', 'improve'],
        'Formal register; takes an undesirable condition as direct object (never ameliorate an already good situation).'
      ),
      mkEntry(
        've-2',
        'cogent',
        'adjective',
        '/ˈkəʊ.dʒənt/',
        'C1',
        'Clear, logical, and convincing in argument or presentation.',
        'The committee submitted a cogent analysis of supply-side bottlenecks.',
        ['persuasive', 'compelling', 'lucid'],
        'Commonly collocates with argument, case, reasoning, evidence.'
      ),
      mkEntry(
        've-3',
        'ephemeral',
        'adjective',
        '/ɪˈfem.ər.əl/',
        'C1',
        'Lasting for only a very short period of time.',
        'Speculative commodity rallies often prove ephemeral once inventories normalise.',
        ['transient', 'fleeting', 'short-lived'],
        'Often contrasted with perennial or enduring in reading comprehension.'
      ),
      mkEntry(
        've-4',
        'pragmatic',
        'adjective',
        '/præɡˈmæt.ɪk/',
        'B2',
        'Dealing with things sensibly and realistically based on practical considerations.',
        'A pragmatic regulatory timeline avoided disruption to small manufacturers.',
        ['practical', 'realistic', 'matter-of-fact'],
        'Frequently contrasted with dogmatic or idealistic.'
      ),
      mkEntry(
        've-5',
        'ubiquitous',
        'adjective',
        '/juːˈbɪk.wɪ.təs/',
        'B2',
        'Present, appearing, or found everywhere at once.',
        'Digital payment QR codes have become ubiquitous across neighbourhood markets.',
        ['omnipresent', 'pervasive', 'universal'],
        'High-frequency GRE/UPSC/IELTS adjective.'
      ),
    ],
  },
];

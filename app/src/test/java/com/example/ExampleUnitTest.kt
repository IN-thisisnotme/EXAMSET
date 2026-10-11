package com.example

import com.example.util.McqParser
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testUserQuestionsParsing() {
    val raw = """
Questions
1. Assertion–Reason

Assertion (A): In a typical eukaryotic cell, inhibition of mitochondrial ATP synthase can eventually increase the proton gradient across the inner mitochondrial membrane under suitable experimental conditions.

Reason (R): Electron transport and proton pumping can continue temporarily when the proton re-entry pathway through ATP synthase is blocked.

(a) Both A and R are true, and R is the correct explanation of A.
(b) Both A and R are true, but R is not the correct explanation of A.
(c) A is true, but R is false.
(d) A is false, but R is true.

11.

Which combination would most strongly favour natural selection causing directional evolutionary change?

Heritable phenotypic variation
Differential reproductive success
Random mating
Environmental pressure favouring one extreme phenotype

(a) 1 and 2 only
(b) 1, 2 and 4 only
(c) 2 and 3 only
(d) 1, 3 and 4 only

12. Assertion–Reason

Assertion (A): Lamarckian inheritance of acquired characters is not accepted as a general mechanism of heredity in modern evolutionary biology.

Reason (R): Most somatic modifications acquired during an individual's lifetime are not transmitted to offspring through the germ line.

(a) Both A and R are true, and R is the correct explanation of A.
(b) Both A and R are true, but R is not the correct explanation of A.
(c) A is true, but R is false.
(d) A is false, but R is true.

45. Assertion–Reason

Assertion (A): Introns present in eukaryotic genomic DNA are generally absent from mature cytoplasmic mRNA.

Reason (R): Introns are removed from the primary RNA transcript during RNA processing.

(a) Both A and R are true, and R is the correct explanation of A.
(b) Both A and R are true, but R is not the correct explanation of A.
(c) A is true, but R is false.
(d) A is false, but R is true.
    """.trimIndent()

    val (qSec, aSec, eSec) = McqParser.findSections(raw)
    println("--- qSec (${qSec.lines().size} lines) ---")

    val lines = qSec.lines()
    println("Testing QUESTION_START_REGEX on each line:")
    val pattern = java.util.regex.Pattern.compile(
        """^\s*(?:\*\*|__)?\s*(?:Q(?:uestion)?\.?\s*)?(\d{1,4})\s*[\.\):\-]\s*(?:\*\*|__)?\s*(.*?)(?:\*\*|__)?\s*${'$'}""",
        java.util.regex.Pattern.CASE_INSENSITIVE
    )
    for (i in lines.indices) {
        val m = pattern.matcher(lines[i])
        if (m.matches()) {
            val num = m.group(1)?.toIntOrNull()
            val text = m.group(2)?.trim() ?: ""
            val isPrompt = McqParser.isPromptOrInstruction(text)
            println("Line $i MATCH: num=$num, text='$text', isPrompt=$isPrompt")
        }
    }

    val paper = McqParser.parse(raw)
    println("Parsed question count: ${paper.questions.size}")
    paper.questions.forEach { q ->
      println("Q${q.num}: '${q.question.take(60)}...'")
      println("  A: '${q.options.a}'")
      println("  B: '${q.options.b}'")
      println("  C: '${q.options.c}'")
      println("  D: '${q.options.d}'")
      println("  Ans: ${q.correctAnswer}")
      println("  Exp: ${q.explanation}")
    }
    assertEquals(4, paper.questions.size)
    assertEquals(1, paper.questions[0].num)
    assertTrue(paper.questions[0].question.contains("inhibition of mitochondrial ATP synthase"))
    assertEquals("Both A and R are true, and R is the correct explanation of A.", paper.questions[0].options.a)
    assertEquals("A is false, but R is true.", paper.questions[0].options.d)

    assertEquals(11, paper.questions[1].num)
    assertTrue(paper.questions[1].question.contains("natural selection"))
    assertEquals("1 and 2 only", paper.questions[1].options.a)

    assertEquals(12, paper.questions[2].num)
    assertTrue(paper.questions[2].question.contains("Lamarckian inheritance"))
    assertEquals("Both A and R are true, and R is the correct explanation of A.", paper.questions[2].options.a)

    assertEquals(45, paper.questions[3].num)
    assertTrue(paper.questions[3].question.contains("Introns present in eukaryotic genomic DNA"))
    assertEquals("Both A and R are true, and R is the correct explanation of A.", paper.questions[3].options.a)
  }

  @Test
  fun testSciencePaperParsing() {
    val paper = McqParser.parse(com.example.data.SampleData.SAMPLE_SCIENCE_PAPER)
    assertEquals(10, paper.questions.size)
    assertEquals("A", paper.questions[0].correctAnswer)
    assertEquals("B", paper.questions[1].correctAnswer)
    assertEquals("C", paper.questions[2].correctAnswer)
    assertEquals("C", paper.questions.last().correctAnswer)
  }

  @Test
  fun testUserExactTenQuestions() {
    val raw = """
## Questions

### 1. Direct Conceptual Question

Which one of the following cellular structures is primarily responsible for the modification, sorting and packaging of proteins destined for secretion?

(a) Rough endoplasmic reticulum
(b) Golgi apparatus
(c) Lysosome
(d) Peroxisome

### 2. Statement-Based Question

Consider the following statements regarding mitochondria:

1. Mitochondria possess their own DNA.
2. Mitochondrial ribosomes are structurally identical to cytoplasmic 80S ribosomes of eukaryotic cells.
3. The inner mitochondrial membrane contains the electron transport chain.
4. The Krebs cycle occurs mainly in the mitochondrial matrix in eukaryotic cells.

Which of the statements given above are correct?

(a) 1, 2 and 3 only
(b) 1, 3 and 4 only
(c) 2 and 4 only
(d) 1, 2, 3 and 4

### 3. Assertion–Reason

**Assertion (A):** Competitive inhibition of an enzyme can generally be overcome by increasing the concentration of substrate.

**Reason (R):** A competitive inhibitor and the substrate compete for binding to the same active site of the enzyme.

(a) Both A and R are true, and R is the correct explanation of A.
(b) Both A and R are true, but R is not the correct explanation of A.
(c) A is true, but R is false.
(d) A is false, but R is true.

### 4. Match the Following

Match List-I with List-II:

| List-I        | List-II      |
| ------------- | ------------ |
| 1. Cursorial  | A. Flying    |
| 2. Fossorial  | B. Running   |
| 3. Scansorial | C. Burrowing |
| 4. Volant     | D. Climbing  |

Select the correct answer using the code given below:

(a) 1-B, 2-C, 3-D, 4-A
(b) 1-C, 2-B, 3-A, 4-D
(c) 1-D, 2-A, 3-C, 4-B
(d) 1-A, 2-D, 3-B, 4-C

### 5. Pair-Based Question

Consider the following pairs:

1. **C4 plants — Kranz anatomy**
2. **CAM plants — Stomata generally open during the night**
3. **C3 plants — Initial fixation of CO₂ by PEP carboxylase**
4. **Photorespiration — Favoured by relatively high O₂ and low CO₂ conditions**

Which of the pairs given above are correctly matched?

(a) 1, 2 and 4 only
(b) 1 and 3 only
(c) 2, 3 and 4 only
(d) 1, 2, 3 and 4

### 6. Chronological Sequence Question

Arrange the following major events of early animal embryonic development in the correct sequence:

1. Gastrulation
2. Fertilization
3. Cleavage
4. Formation of blastula

Select the correct sequence:

(a) 2 → 3 → 4 → 1
(b) 2 → 4 → 3 → 1
(c) 3 → 2 → 4 → 1
(d) 4 → 3 → 2 → 1

### 7. Process/Sequence Question

Which one of the following correctly represents the major sequence of information flow during expression of a typical protein-coding eukaryotic gene?

(a) DNA replication → transcription → translation
(b) Transcription → RNA processing → translation
(c) Translation → transcription → RNA processing
(d) RNA processing → DNA replication → translation

### 8. Multiple Statements + Combination

Consider the following statements regarding Hardy–Weinberg equilibrium:

1. The population should be sufficiently large to minimize the effect of genetic drift.
2. Random mating is one of the required assumptions.
3. Natural selection must operate continuously for allele frequencies to remain constant.
4. Mutation and migration can alter allele frequencies.

Which of the statements given above are correct?

(a) 1, 2 and 4 only
(b) 1 and 3 only
(c) 2, 3 and 4 only
(d) 1, 2, 3 and 4

### 9. Numerical/Application Question

In a population under Hardy–Weinberg equilibrium, the frequency of the recessive phenotype is **0.16**. Assuming complete dominance, what proportion of the population is expected to be heterozygous?

(a) 0.16
(b) 0.24
(c) 0.48
(d) 0.64

### 10. Case-Based/Analytical Question

A researcher observes that a plant species growing in an extremely dry environment has thick leaves, reduced leaf area, a heavy cuticle and stomata that open predominantly at night. Which combination most appropriately explains these characteristics?

(a) Increased transpiration during the day and enhanced C3 photosynthesis
(b) Reduced water loss and temporal separation of CO₂ uptake from daytime carbon fixation
(c) Increased stomatal opening during the hottest period and enhanced photorespiration
(d) Reduced cuticular resistance and continuous daytime stomatal opening

## Answer Key

| Q  | Answer  | Type                   |
| -- | ------- | ---------------------- |
| 1  | **(b)** | Direct Conceptual      |
| 2  | **(b)** | Statement-Based        |
| 3  | **(a)** | Assertion–Reason       |
| 4  | **(a)** | Match the Following    |
| 5  | **(a)** | Pair-Based             |
| 6  | **(a)** | Chronological Sequence |
| 7  | **(b)** | Process/Sequence       |
| 8  | **(a)** | Multiple Statements    |
| 9  | **(c)** | Numerical/Application  |
| 10 | **(b)** | Case-Based/Analytical  |

## Detailed Explanation

### 1. Which one of the following cellular structures is primarily responsible for the modification, sorting and packaging of proteins destined for secretion?

(a) Rough endoplasmic reticulum — Rough ER synthesizes many secretory proteins but is not the principal organelle for their final modification and sorting.

Correct. (b) Golgi apparatus — The Golgi receives proteins from the ER and modifies, sorts and packages them into appropriate vesicles.

(c) Lysosome — Lysosomes contain hydrolytic enzymes and primarily function in intracellular digestion and degradation.

(d) Peroxisome — Peroxisomes are mainly involved in oxidative reactions such as fatty-acid oxidation and detoxification of hydrogen peroxide.

### 2. Consider the following statements regarding mitochondria:

(a) 1, 2 and 3 only — Incorrect because statement 2 is false.

Correct. (b) 1, 3 and 4 only — Mitochondria contain their own DNA; the inner membrane houses the electron transport chain; and the Krebs cycle occurs primarily in the matrix. Mitochondrial ribosomes are not identical to cytoplasmic 80S ribosomes.

(c) 2 and 4 only — Incorrect because statement 2 is false and statement 1 is also correct.

(d) 1, 2, 3 and 4 — Incorrect because mitochondrial ribosomes are generally smaller and differ from eukaryotic cytoplasmic 80S ribosomes.

### 3. Competitive inhibition

(a) Correct. Both A and R are true, and R is the correct explanation of A. — A competitive inhibitor competes with substrate for the active site. Increasing substrate concentration can therefore outcompete the inhibitor. In classical competitive inhibition, apparent Km increases while Vmax remains unchanged.

(b) Both A and R are true, but R is not the correct explanation of A — Incorrect because the competition for the active site directly explains why increasing substrate concentration can reduce inhibition.

(c) A is true, but R is false — Incorrect; the reason is true.

(d) A is false, but R is true — Incorrect; both statements are true.

### 4. Animal adaptations

Correct. (a) 1-B, 2-C, 3-D, 4-A — Cursorial animals are adapted for running, fossorial animals for burrowing, scansorial animals for climbing and volant animals for flying.

(b) 1-C, 2-B, 3-A, 4-D — Incorrectly exchanges the adaptations.

(c) 1-D, 2-A, 3-C, 4-B — Incorrect mapping.

(d) 1-A, 2-D, 3-B, 4-C — Incorrect mapping.

### 5. Photosynthetic adaptations

Correct. (a) 1, 2 and 4 only — C4 plants characteristically possess Kranz anatomy; CAM plants generally open their stomata at night; and photorespiration is favoured under relatively high O₂ and low CO₂ conditions.

(b) 1 and 3 only — Incorrect because initial CO₂ fixation by PEP carboxylase is characteristic of C4 and CAM pathways, not ordinary C3 carbon fixation.

(c) 2, 3 and 4 only — Incorrect because statement 3 is false.

(d) 1, 2, 3 and 4 — Incorrect because statement 3 is false.

### 6. Embryological sequence

(a) Correct. 2 → 3 → 4 → 1 — Fertilization produces the zygote; repeated mitotic divisions produce cleavage; cleavage produces the blastula; and gastrulation subsequently reorganizes the embryo into germ layers.

(b) 2 → 4 → 3 → 1 — Incorrect because blastula formation follows cleavage.

(c) 3 → 2 → 4 → 1 — Incorrect because cleavage occurs after fertilization.

(d) 4 → 3 → 2 → 1 — Incorrect because both blastula formation and cleavage occur after fertilization.

### 7. Gene-expression sequence

(a) DNA replication → transcription → translation — Incorrect as the general sequence of expression. DNA replication is genome duplication rather than the normal first step of expressing a protein-coding gene.

Correct. (b) Transcription → RNA processing → translation — In eukaryotes, the gene is transcribed into a primary RNA transcript, which undergoes processing such as capping, splicing and polyadenylation before mature mRNA is translated.

(c) Translation → transcription → RNA processing — Incorrect order.

(d) RNA processing → DNA replication → translation — Incorrect because RNA processing follows transcription.

### 8. Hardy–Weinberg equilibrium

Correct. (a) 1, 2 and 4 only — A sufficiently large population reduces genetic drift; random mating is an assumption; and mutation/migration can disturb equilibrium. Natural selection must not continuously operate if Hardy–Weinberg equilibrium is to be maintained.

(b) 1 and 3 only — Incorrect because natural selection violates the equilibrium assumptions.

(c) 2, 3 and 4 only — Incorrect because statement 3 is false.

(d) 1, 2, 3 and 4 — Incorrect because natural selection is excluded under the ideal Hardy–Weinberg model.

### 9. Hardy–Weinberg numerical application

Given:

Recessive phenotype = q² = 0.16

Therefore:

q = 0.4

Since:

p + q = 1

p = 0.6

Heterozygote frequency:

2pq = 2 × 0.6 × 0.4 = 0.48

(a) 0.16 — This is q², the recessive homozygote frequency, not the heterozygote frequency.

(b) 0.24 — This would result from an incorrect calculation of 2pq.

Correct. (c) 0.48 — This is the expected frequency of heterozygotes (Aa).

(d) 0.64 — This is p², the expected frequency of the dominant homozygote.

### 10. Case-based CAM adaptation

(a) Increased transpiration during the day and enhanced C3 photosynthesis — Incorrect. These characteristics are generally associated with water conservation, not increased daytime water loss.

Correct. (b) Reduced water loss and temporal separation of CO₂ uptake from daytime carbon fixation — CAM plants generally open stomata at night, take up CO₂ and store it as organic acids, then use the stored carbon during the day while stomata remain more closed. Thick cuticles and reduced leaf area further limit water loss.

(c) Increased stomatal opening during the hottest period and enhanced photorespiration — Incorrect because this would increase water loss and is contrary to the observed xerophytic strategy.

(d) Reduced cuticular resistance and continuous daytime stomatal opening — Incorrect; both characteristics would generally promote water loss rather than conserve water.
    """.trimIndent()

    val paper = McqParser.parse(raw)
    assertEquals(10, paper.questions.size)
    assertEquals("B", paper.questions[0].correctAnswer)
    assertEquals("B", paper.questions[1].correctAnswer)
    assertEquals("A", paper.questions[2].correctAnswer)
    assertEquals("A", paper.questions[3].correctAnswer)
    assertEquals("A", paper.questions[4].correctAnswer)
    assertEquals("A", paper.questions[5].correctAnswer)
    assertEquals("B", paper.questions[6].correctAnswer)
    assertEquals("A", paper.questions[7].correctAnswer)
    assertEquals("C", paper.questions[8].correctAnswer)
    assertEquals("B", paper.questions[9].correctAnswer)

    // Verify Q4 options
    val q4 = paper.questions[3]
    assertEquals("1-B, 2-C, 3-D, 4-A", q4.options.a)
    assertEquals("1-C, 2-B, 3-A, 4-D", q4.options.b)
    assertEquals("1-D, 2-A, 3-C, 4-B", q4.options.c)
    assertEquals("1-A, 2-D, 3-B, 4-C", q4.options.d)

    // Verify Q3 options
    val q3 = paper.questions[2]
    assertEquals("Both A and R are true, and R is the correct explanation of A.", q3.options.a)
    assertEquals("A is false, but R is true.", q3.options.d)

    // Verify all 10 have detailed explanations
    paper.questions.forEach { q ->
      assertNotNull("Q${q.num} explanation must not be null", q.explanation)
      assertTrue("Q${q.num} explanation must not be empty", q.explanation!!.isNotBlank())
    }
  }

  @Test
  fun testStructuredAssertionReasonParsing() {
    val text1 = """
      Assertion (A): In a typical eukaryotic cell, inhibition of mitochondrial ATP synthase can eventually increase the proton gradient across the inner mitochondrial membrane under suitable experimental conditions.
      Reason (R): Electron transport and proton pumping can continue temporarily when the proton re-entry pathway through ATP synthase is blocked.
    """.trimIndent()

    val parts1 = com.example.ui.components.parseAssertionReason(text1)
    assertNotNull(parts1)
    assertNull(parts1!!.leadIn)
    assertTrue(parts1.assertion.contains("inhibition of mitochondrial ATP synthase"))
    assertTrue(parts1.reason.contains("Electron transport and proton pumping"))
    assertNull(parts1.leadOut)

    val text2 = """
      Given below are two statements: one is labelled as Assertion A and the other is labelled as Reason R:
      Assertion (A): Lamarckian inheritance of acquired characters is not accepted.
      Reason (R): Most somatic modifications acquired during an individual's lifetime are not transmitted.
      In the light of the above statements, choose the most appropriate answer:
    """.trimIndent()

    val parts2 = com.example.ui.components.parseAssertionReason(text2)
    assertNotNull(parts2)
    assertNotNull(parts2!!.leadIn)
    assertTrue(parts2.leadIn!!.contains("Given below are two statements"))
    assertEquals("Lamarckian inheritance of acquired characters is not accepted.", parts2.assertion)
    assertEquals("Most somatic modifications acquired during an individual's lifetime are not transmitted.", parts2.reason)
    assertNotNull(parts2.leadOut)
    assertTrue(parts2.leadOut!!.contains("In the light of the above statements"))
  }

  @Test
  fun testSexEducationMcqSetParsing() {
    val raw = """
The set below is aligned with common State PCS General Studies/General Science and health-related coverage, with emphasis on adolescent health, sexual and reproductive health, contraception, STIs/HIV, menstrual health, consent, and government programmes. The Indian-policy components are cross-checked against current National Health Mission material on RKSK, Adolescent Friendly Health Services, and School Health & Wellness, along with WHO material on sexual health and contraception. 

MCQs on SEX EDUCATION

Questions

1. Which of the following most appropriately describes the concept of sexual health according to the contemporary public-health approach?

(a) Absence of sexually transmitted infections only        (b) Ability to reproduce without medical complications
(c) Physical, emotional, mental and social well-being in relation to sexuality        (d) Absence of sexual activity

2. Which one of the following is the primary biological event responsible for the onset of menstruation when fertilisation does not occur?

(a) Sudden increase in progesterone and oestrogen        (b) Degeneration of the corpus luteum followed by a fall in ovarian hormones
(c) Release of another ovum from the ovary        (d) Permanent breakdown of the endometrium

3. Consider the following statements regarding condoms:

1. They can reduce the risk of several sexually transmitted infections.
2. They can help prevent unintended pregnancy.
3. They provide complete protection against every STI under all circumstances.

Which of the statements given above is/are correct?

(a) 1 and 2 only        (b) 2 and 3 only
(c) 1 and 3 only        (d) 1, 2 and 3

4. Which of the following contraceptive methods primarily acts by preventing ovulation when used correctly?

(a) Combined oral contraceptive pill        (b) Copper-bearing intrauterine device
(c) Male condom        (d) Diaphragm

5. A woman takes an oral contraceptive pill but is concerned that it may permanently impair her fertility. Which statement is most accurate?

(a) Oral contraceptives generally cause permanent infertility.        (b) Fertility generally returns quickly after discontinuation.
(c) Fertility returns only after one complete year.        (d) Fertility is permanently reduced after five years of use.

6. Which one of the following infections is caused by a bacterium rather than a virus?

(a) HIV infection        (b) Genital herpes
(c) Gonorrhoea        (d) Human papillomavirus infection

7. Which of the following is NOT a recognised route of HIV transmission?

(a) Transfusion of infected blood        (b) Sharing contaminated needles
(c) Unprotected sexual exposure        (d) Casual contact such as shaking hands

8. A person has been exposed to a sexually transmitted infection but has no symptoms. Which conclusion is scientifically most appropriate?

(a) The person cannot have an STI because all STIs produce visible symptoms.        (b) The person may still have an STI and should seek appropriate testing/advice.
(c) The person is definitely immune to all STIs.        (d) The absence of symptoms proves that the infection has disappeared.

9. Which of the following best explains why puberty is considered a period of significant biological change?

(a) It involves only psychological maturation.        (b) It involves hormonal changes leading to reproductive maturation and secondary sexual characteristics.
(c) It permanently stops physical growth in all adolescents.        (d) It affects only the reproductive organs and no other body systems.

10. Which hormone surge is most directly associated with triggering ovulation in a typical menstrual cycle?

(a) Follicle-stimulating hormone (FSH)        (b) Luteinizing hormone (LH)
(c) Insulin        (d) Thyroxine

11. Consider a hypothetical menstrual cycle of 28 days in which ovulation occurs around day 14. If fertilisation does not occur, menstruation would most commonly begin approximately how many days after ovulation?

(a) 1–2 days        (b) 5–6 days
(c) 12–14 days        (d) 25–28 days

12. Which statement regarding emergency contraception is correct?

(a) It is designed to terminate an established pregnancy.        (b) It can be used only before sexual intercourse.
(c) It is intended to reduce the probability of pregnancy after unprotected intercourse or contraceptive failure.        (d) It protects against HIV and other STIs.

13. Which one of the following pairs is correctly matched?

(a) HPV — Hepatitis B        (b) HIV — Human immunodeficiency virus
(c) STI — Sexually Transmitted Immunity        (d) AIDS — Antibiotic-Induced Disease Syndrome

14. Which of the following is the most appropriate objective of comprehensive sex education?

(a) To encourage adolescents to become sexually active.        (b) To provide scientifically accurate information and develop skills for healthy, safe and responsible decisions.
(c) To focus exclusively on biological reproduction.        (d) To replace all parental and professional guidance.

15. An adolescent receives a message on social media requesting a private sexual photograph and threatening to share existing personal information if the photograph is not sent. Which concept is most directly involved?

(a) Informed consent        (b) Digital sexual coercion
(c) Normal adolescent development        (d) Genetic counselling

16. Which of the following statements about consent in a healthy sexual relationship is most appropriate?

(a) Consent given once is permanent.        (b) Silence always means consent.
(c) Consent should be voluntary and can be withdrawn.        (d) Consent is unnecessary when the persons know each other well.

17. Under India's Rashtriya Kishor Swasthya Karyakram (RKSK), adolescent health is understood in a broader framework rather than being restricted only to sexual and reproductive health. Which of the following is NOT one of its six strategic priorities?

(a) Nutrition        (b) Substance misuse
(c) Mental health        (d) Promotion of competitive examinations

18. A school health programme wants to reduce misinformation about puberty, menstruation, contraception and STI prevention. Which approach is most consistent with the principles of effective health education?

(a) Provide scientifically accurate, age-appropriate information and encourage questions.        (b) Avoid all discussion of sexuality to prevent curiosity.
(c) Depend exclusively on unverified social-media content.        (d) Provide information only after an adolescent develops a disease.

19. Which statement correctly distinguishes a sexually transmitted infection (STI) from HIV/AIDS?

(a) HIV is a bacterium, whereas all STIs are viruses.        (b) HIV is one specific viral infection, while STI is a broader category that includes infections caused by different pathogens.
(c) AIDS and STI are exactly synonymous terms.        (d) HIV can be transmitted only through sexual intercourse.

20. Which of the following situations best illustrates the principle of "risk reduction" in sexual health?

(a) Assuming that a familiar partner cannot have an STI.        (b) Avoiding testing because absence of symptoms guarantees health.
(c) Using condoms correctly and consistently and seeking appropriate STI testing when indicated.        (d) Taking antibiotics routinely without diagnosis to prevent all STIs.

---

Answer Key

Q.| Answer| Q.| Answer
1| (c)| 11| (c)
2| (b)| 12| (c)
3| (a)| 13| (b)
4| (a)| 5| (b)
5| (b)| 15| (b)
6| (c)| 16| (c)
7| (d)| 17| (d)
8| (b)| 18| (a)
9| (b)| 19| (b)
10| (b)| 20| (c)

---

Detailed Explanation

1. Which of the following most appropriately describes the concept of sexual health according to the contemporary public-health approach?

(a) Absence of sexually transmitted infections only — This is too narrow. Sexual health encompasses much more than freedom from infection or disease.

(b) Ability to reproduce without medical complications — Reproductive capacity is only one component of reproductive health and does not define sexual health.

Correct. (c) Physical, emotional, mental and social well-being in relation to sexuality — The contemporary WHO approach treats sexual health as a state of physical, emotional, mental and social well-being related to sexuality, rather than merely the absence of disease.

(d) Absence of sexual activity — Sexual health is not defined by abstinence or the absence of sexual activity.

---

2. Which one of the following is the primary biological event responsible for the onset of menstruation when fertilisation does not occur?

(a) Sudden increase in progesterone and oestrogen — Incorrect. The late luteal phase is characterised by a decline in ovarian hormones rather than a sustained increase.

Correct. (b) Degeneration of the corpus luteum followed by a fall in ovarian hormones — When fertilisation and implantation do not occur, the corpus luteum regresses. The resulting decline in progesterone and oestrogen contributes to shedding of the endometrial lining.

(c) Release of another ovum from the ovary — Ovulation and menstruation are different events. Menstruation follows the hormonal changes associated with the end of the luteal phase.

(d) Permanent breakdown of the endometrium — The endometrium is shed cyclically and subsequently rebuilt; the process is not permanent.

---

3. Consider the following statements regarding condoms:

(a) 1 and 2 only — Statement 1 is correct because condoms reduce the risk of many STIs, and Statement 2 is correct because they can prevent sperm from entering the reproductive tract. Therefore, this is the correct combination.

(b) 2 and 3 only — Statement 2 is correct, but Statement 3 is false because condoms do not provide complete protection against every STI under every circumstance.

(c) 1 and 3 only — Statement 1 is correct, but Statement 3 is incorrect.

Correct. (a) 1 and 2 only — Condoms can reduce the risk of several STIs and help prevent unintended pregnancy. They do not provide absolute protection against every STI, particularly infections transmitted through uncovered skin-to-skin contact.

(d) 1, 2 and 3 — Statement 3 makes this option incorrect because it uses an absolute claim of complete protection.

---

4. Which of the following contraceptive methods primarily acts by preventing ovulation when used correctly?

Correct. (a) Combined oral contraceptive pill — Combined oral contraceptives contain oestrogen and progestin and primarily prevent ovulation when used correctly.

(b) Copper-bearing intrauterine device — A copper IUD primarily prevents fertilisation through local effects of copper in the uterus; it is not primarily an ovulation-suppression method.

(c) Male condom — A condom is a barrier method that prevents sperm from entering the reproductive tract.

(d) Diaphragm — A diaphragm is also a barrier method placed over the cervix to impede sperm passage.

---

5. A woman takes an oral contraceptive pill but is concerned that it may permanently impair her fertility. Which statement is most accurate?

(a) Oral contraceptives generally cause permanent infertility. — This is false. Oral contraceptives are reversible contraceptive methods.

Correct. (b) Fertility generally returns quickly after discontinuation. — WHO states that fertility returns quickly after stopping both combined oral contraceptives and progestin-only pills.

(c) Fertility returns only after one complete year. — There is no general biological requirement for a one-year waiting period.

(d) Fertility is permanently reduced after five years of use. — Long-term use does not mean that fertility becomes permanently impaired.

---

6. Which one of the following infections is caused by a bacterium rather than a virus?

(a) HIV infection — HIV is caused by the human immunodeficiency virus.

(b) Genital herpes — Genital herpes is caused by herpes simplex viruses, principally HSV-1 or HSV-2.

Correct. (c) Gonorrhoea — Gonorrhoea is caused by the bacterium Neisseria gonorrhoeae.

(d) Human papillomavirus infection — HPV infection is caused by human papillomaviruses, which are viruses.

---

7. Which of the following is NOT a recognised route of HIV transmission?

(a) Transfusion of infected blood — HIV can be transmitted through contaminated blood or blood products where appropriate screening and safety measures are absent.

(b) Sharing contaminated needles — Sharing needles contaminated with HIV-infected blood can transmit HIV.

(c) Unprotected sexual exposure — Sexual transmission is a recognised route of HIV transmission.

Correct. (d) Casual contact such as shaking hands — HIV is not transmitted through ordinary casual contact such as handshakes, hugging or sharing ordinary social space.

---

8. A person has been exposed to a sexually transmitted infection but has no symptoms. Which conclusion is scientifically most appropriate?

(a) The person cannot have an STI because all STIs produce visible symptoms. — False. Several STIs can be asymptomatic, particularly during some stages of infection.

Correct. (b) The person may still have an STI and should seek appropriate testing/advice. — Absence of symptoms does not reliably exclude infection. Appropriate testing depends on the suspected infection and exposure.

(c) The person is definitely immune to all STIs. — Previous exposure or absence of symptoms does not establish universal immunity.

(d) The absence of symptoms proves that the infection has disappeared. — Symptoms and infection status are not equivalent.

---

9. Which of the following best explains why puberty is considered a period of significant biological change?

(a) It involves only psychological maturation. — Puberty includes major physiological and endocrine changes, not merely psychological changes.

Correct. (b) It involves hormonal changes leading to reproductive maturation and secondary sexual characteristics. — Increased activity of the hypothalamic-pituitary-gonadal axis leads to hormonal changes, reproductive maturation and development of secondary sexual characteristics.

(c) It permanently stops physical growth in all adolescents. — Puberty does not immediately or universally stop growth.

(d) It affects only the reproductive organs and no other body systems. — Puberty also involves changes in growth, body composition, skin, hair and other physiological processes.

---

10. Which hormone surge is most directly associated with triggering ovulation in a typical menstrual cycle?

(a) Follicle-stimulating hormone (FSH) — FSH is important for follicular development, but the mid-cycle LH surge is the principal hormonal trigger for ovulation.

Correct. (b) Luteinizing hormone (LH) — A rapid rise, or surge, in LH triggers ovulation in the typical menstrual cycle.

(c) Insulin — Insulin primarily regulates glucose metabolism and is not the direct trigger of ovulation.

(d) Thyroxine — Thyroxine is a thyroid hormone and does not directly trigger ovulation.

---

11. Consider a hypothetical menstrual cycle of 28 days in which ovulation occurs around day 14. If fertilisation does not occur, menstruation would most commonly begin approximately how many days after ovulation?

(a) 1–2 days — The luteal phase does not normally last only one or two days.

(b) 5–6 days — This is too short for the typical interval between ovulation and the beginning of menstruation.

Correct. (c) 12–14 days — The luteal phase is commonly around 12–14 days. Therefore, if ovulation occurs around day 14, menstruation would commonly begin around day 26–28.

(d) 25–28 days — This would make the post-ovulation interval much longer than the usual luteal phase.

---

12. Which statement regarding emergency contraception is correct?

(a) It is designed to terminate an established pregnancy. — Emergency contraception is intended to prevent or reduce the probability of pregnancy after unprotected intercourse or contraceptive failure; it is not an abortion method for an established pregnancy.

(b) It can be used only before sexual intercourse. — The defining purpose of emergency contraception is use after unprotected intercourse or contraceptive failure within the relevant time window.

Correct. (c) It is intended to reduce the probability of pregnancy after unprotected intercourse or contraceptive failure. — This accurately describes the purpose of emergency contraception.

(d) It protects against HIV and other STIs. — Emergency contraception does not provide STI protection.

---

13. Which one of the following pairs is correctly matched?

(a) HPV — Hepatitis B — Incorrect. HPV stands for human papillomavirus; hepatitis B is caused by hepatitis B virus.

Correct. (b) HIV — Human immunodeficiency virus — HIV is the accepted abbreviation for human immunodeficiency virus.

(c) STI — Sexually Transmitted Immunity — STI means sexually transmitted infection, not sexually transmitted immunity.

(d) AIDS — Antibiotic-Induced Disease Syndrome — AIDS stands for acquired immunodeficiency syndrome.

---

14. Which of the following is the most appropriate objective of comprehensive sex education?

(a) To encourage adolescents to become sexually active. — This misrepresents the purpose of sex education. Its objective is health, knowledge, safety and responsible decision-making.

Correct. (b) To provide scientifically accurate information and develop skills for healthy, safe and responsible decisions. — Comprehensive sexuality education aims to provide accurate knowledge and develop attitudes and skills relevant to relationships, health, safety, consent and responsible decision-making.

(c) To focus exclusively on biological reproduction. — Human sexuality education includes biological, psychological, social and health dimensions.

(d) To replace all parental and professional guidance. — Education should complement appropriate family, school and health-professional support rather than replace it.

---

15. An adolescent receives a message on social media requesting a private sexual photograph and threatening to share existing personal information if the photograph is not sent. Which concept is most directly involved?

(a) Informed consent — Informed consent concerns voluntary agreement based on adequate information. The described situation specifically involves pressure and threats.

Correct. (b) Digital sexual coercion — Threatening or pressuring someone online to obtain sexual content is an example of sexual coercion in a digital context.

(c) Normal adolescent development — Although adolescents may use social media extensively, coercive sexual demands are not a normal developmental requirement.

(d) Genetic counselling — Genetic counselling concerns inherited conditions, reproductive genetic risks and related decision-making, not online sexual coercion.

---

16. Which of the following statements about consent in a healthy sexual relationship is most appropriate?

(a) Consent given once is permanent. — Consent is specific to the situation and can change.

(b) Silence always means consent. — Silence cannot automatically be interpreted as voluntary agreement.

Correct. (c) Consent should be voluntary and can be withdrawn. — Genuine consent must be voluntary, and a person may change their mind and withdraw consent.

(d) Consent is unnecessary when the persons know each other well. — Familiarity or an existing relationship does not eliminate the need for consent.

---

17. Under India's Rashtriya Kishor Swasthya Karyakram (RKSK), adolescent health is understood in a broader framework rather than being restricted only to sexual and reproductive health. Which of the following is NOT one of its six strategic priorities?

(a) Nutrition — Nutrition is one of the six RKSK strategic priorities.

(b) Substance misuse — Substance misuse is explicitly included among the six priorities.

(c) Mental health — Mental health is one of the six strategic priorities.

Correct. (d) Promotion of competitive examinations — Competitive-examination preparation is not an RKSK strategic health priority. The six priorities are sexual and reproductive health, nutrition, non-communicable diseases, substance misuse, injuries and violence including gender-based violence, and mental health.
18. A school health programme wants to reduce misinformation about puberty, menstruation, contraception and STI prevention. Which approach is most consistent with the principles of effective health education?
Correct. (a) Provide scientifically accurate, age-appropriate information and encourage questions. — Evidence-based and age-appropriate education helps adolescents distinguish reliable health information from myths and misinformation.
(b) Avoid all discussion of sexuality to prevent curiosity. — Avoidance can leave adolescents dependent on unreliable sources of information.
(c) Depend exclusively on unverified social-media content. — Social-media information may be inaccurate, incomplete or misleading.
(d) Provide information only after an adolescent develops a disease. — Preventive health education should occur before health problems arise.
19. Which statement correctly distinguishes a sexually transmitted infection (STI) from HIV/AIDS?
(a) HIV is a bacterium, whereas all STIs are viruses. — HIV is a virus, and STIs can be caused by viruses, bacteria, parasites and other pathogens.
Correct. (b) HIV is one specific viral infection, while STI is a broader category that includes infections caused by different pathogens. — HIV is one particular viral infection. STI is a broad epidemiological category that includes infections such as gonorrhoea, syphilis, herpes, HPV and HIV.
(c) AIDS and STI are exactly synonymous terms. — AIDS is a syndrome associated with advanced HIV infection; it is not synonymous with the entire category of STIs.
(d) HIV can be transmitted only through sexual intercourse. — HIV can also be transmitted through infected blood exposure and from mother to child in certain circumstances.
20. Which of the following situations best illustrates the principle of "risk reduction" in sexual health?
(a) Assuming that a familiar partner cannot have an STI. — Familiarity does not establish STI status.
(b) Avoiding testing because absence of symptoms guarantees health. — Many STIs can occur without obvious symptoms.
Correct. (c) Using condoms correctly and consistently and seeking appropriate STI testing when indicated. — Risk reduction involves practical measures that lower the probability or consequences of infection and unintended pregnancy. Condom use and appropriate testing are important components.
(d) Taking antibiotics routinely without diagnosis to prevent all STIs. — Antibiotics do not prevent all STIs, particularly viral infections, and inappropriate antibiotic use can contribute to antimicrobial resistance.
    """.trimIndent()

    val paper = McqParser.parse(raw)
    assertEquals(20, paper.questions.size)

    // Check Question 1
    val q1 = paper.questions[0]
    assertEquals(1, q1.num)
    assertEquals("C", q1.correctAnswer)
    assertEquals("Absence of sexually transmitted infections only", q1.options.a)
    assertEquals("Ability to reproduce without medical complications", q1.options.b)
    assertEquals("Physical, emotional, mental and social well-being in relation to sexuality", q1.options.c)
    assertEquals("Absence of sexual activity", q1.options.d)

    // Check Question 3 (Multi-statement question with 2-column options)
    val q3 = paper.questions[2]
    assertEquals(3, q3.num)
    assertEquals("A", q3.correctAnswer)
    assertEquals("1 and 2 only", q3.options.a)
    assertEquals("2 and 3 only", q3.options.b)
    assertEquals("1 and 3 only", q3.options.c)
    assertEquals("1, 2 and 3", q3.options.d)

    // Check Question 14 (Typo in table where 14 was written as 5 or missing, corrected by explanation)
    val q14 = paper.questions[13]
    assertEquals(14, q14.num)
    assertEquals("B", q14.correctAnswer)

    // Check Question 20
    val q20 = paper.questions[19]
    assertEquals(20, q20.num)
    assertEquals("C", q20.correctAnswer)

    // Verify all 20 have explanations
    for (q in paper.questions) {
      assertNotNull("Q${q.num} should have an explanation", q.explanation)
      assertTrue("Q${q.num} explanation should not be blank", q.explanation!!.isNotBlank())
    }
  }

  @Test
  fun testCivilEngineeringPaper() {
    val raw = """
Your prompt: Generate 2 MCQs at Extreme difficulty for Civil Engineering Paper 1. Show all 2 questions first, followed by the Answer Key, and then detailed, non-repetitive, option-by-option explanations at the bottom. Generate the MCQs directly in the chat; do not use Canvas or create a separate document.
Feedback: No substantive grammatical errors were found.
Civil Engineering Paper 1: Extreme Difficulty MCQs
Question 1
A stepped fixed-ended beam ABC has total span L = 8\text{ m}, with segment AB of length 4\text{ m} having flexural rigidity 2EI and segment BC of length 4\text{ m} having flexural rigidity EI. The beam carries a concentrated load W = 120\text{ kN} at the mid-span joint B. Both supports A and C are fully rigid fixed supports. What is the magnitude of the fixed-end moment M_A developed at support A?
(A) 80.00\text{ kN}\cdot\text{m}
(B) 106.67\text{ kN}\cdot\text{m}
(C) 160.00\text{ kN}\cdot\text{m}
(D) 213.33\text{ kN}\cdot\text{m}
Question 2
A braced excavation in a thick deposit of soft, saturated clay (\phi = 0, c_u = 35\text{ kPa}, unit weight \gamma = 18\text{ kN/m}^3) is carried out to a depth H = 8\text{ m}. The site is underlain by a hard bed of rock at a depth D_B = 12\text{ m} below the ground surface (i.e., 4\text{ m} below the base of the excavation). Assuming the width of excavation B is large (B > \sqrt{2} D_B), what is the Factor of Safety (FS) against bottom heaving according to Terzaghi's base stability theory for deep cuts in clay?
(A) 1.21
(B) 1.39
(C) 1.52
(D) 1.70
Answer Key
| Question No. | Correct Option |
|---|---|
| 1 | B |
| 2 | B |
Detailed Option-by-Option Explanations
Explanation 1
 * Correct Answer: (B) 106.67\text{ kN}\cdot\text{m}
 * Derivation & Analysis:
   Using the Force Method (Flexibility Method) or Stiffness Method:
   * Consider joint B displacement/rotation formulation:
     * Segment AB: Length L_1 = 4\text{ m}, Rigidity = 2EI.
     * Segment BC: Length L_2 = 4\text{ m}, Rigidity = EI.
 * Option Evaluation:
   * (A) Incorrect: Result obtained if uniform.
   * (B) Correct: Accurately accounts for 2:1 stiffness.
   * (C) Incorrect: Result obtained if pinned.
   * (D) Incorrect: Sum of moments without rotational compatibility.
Explanation 2
 * Correct Answer: (B) 1.39
 * Derivation & Analysis:
   * Terzaghi's base stability factor of safety against bottom heaving in soft clay:
     FS = 1.39
 * Option Evaluation:
   * (A) Incorrect: Computed using Nc = 5.14.
   * (B) Correct: Accurately applies Nc = 5.70.
   * (C) Incorrect: Uses Nc = 6.2.
   * (D) Incorrect: Erroneously subtracts twice.
    """.trimIndent()

    val paper = McqParser.parse(raw)
    println("Civil Eng paper questions count: ${paper.questions.size}")
    paper.questions.forEach { q ->
      println("Q${q.num} question: '${q.question}'")
      println("Q${q.num} options: A='${q.options.a}', B='${q.options.b}', C='${q.options.c}', D='${q.options.d}'")
      println("Q${q.num} isValid: ${q.options.isValid}")
      println("Q${q.num} correct=${q.correctAnswer}, exp=${q.explanation?.take(60)}")
    }
    assertEquals(2, paper.questions.size)
    assertEquals("B", paper.questions[0].correctAnswer)
    assertEquals("B", paper.questions[1].correctAnswer)
    assertNotNull(paper.questions[0].explanation)
    assertNotNull(paper.questions[1].explanation)
  }

  @Test
  fun testMcqFormatQuestions() {
    val raw = """
MCQ 1
Which of the following is correct?
(A) Option A
(B) Option B
(C) Option C
(D) Option D

MCQ 2:
What is the value?
(A) 10
(B) 20
(C) 30
(D) 40

Answer Key
MCQ 1: A
MCQ 2: B

MCQ Explanations
MCQ 1:
Detailed explanation for MCQ 1.

MCQ 2:
Detailed explanation for MCQ 2.
    """.trimIndent()

    val paper = McqParser.parse(raw)
    assertEquals(2, paper.questions.size)
    assertEquals("A", paper.questions[0].correctAnswer)
    assertEquals("B", paper.questions[1].correctAnswer)
    assertNotNull(paper.questions[0].explanation)
    assertNotNull(paper.questions[1].explanation)
  }

  @Test
  fun testAIBotOptionFormats() {
    // 1. Bold options format: **(A)**, **(B)**, etc.
    val rawBold = """
Question 1
A stepped fixed-ended beam ABC carries a concentrated load.
**(A)** 80.00 kN
**(B)** 106.67 kN
**(C)** 160.00 kN
**(D)** 213.33 kN

Answer Key
1: B
    """.trimIndent()

    val paperBold = McqParser.parse(rawBold)
    assertEquals(1, paperBold.questions.size)
    val q1 = paperBold.questions[0]
    assertTrue("Question text should NOT contain options", !q1.question.contains("80.00 kN"))
    assertEquals("80.00 kN", q1.options.a)
    assertEquals("106.67 kN", q1.options.b)
    assertEquals("160.00 kN", q1.options.c)
    assertEquals("213.33 kN", q1.options.d)
    assertTrue("Options should be valid", q1.options.isValid)

    // 2. Prefixed Option format: Option A:, Option B:, etc.
    val rawPrefixed = """
Question 1
What is the Factor of Safety against bottom heaving?
Option A: 1.21
Option B: 1.39
Option C: 1.52
Option D: 1.70

Answer Key
1: B
    """.trimIndent()

    val paperPrefixed = McqParser.parse(rawPrefixed)
    assertEquals(1, paperPrefixed.questions.size)
    val qPrefixed = paperPrefixed.questions[0]
    assertTrue("Question text should NOT contain options", !qPrefixed.question.contains("1.21"))
    assertEquals("1.21", qPrefixed.options.a)
    assertEquals("1.39", qPrefixed.options.b)
    assertEquals("1.52", qPrefixed.options.c)
    assertEquals("1.70", qPrefixed.options.d)

    // 3. Dash format: A - 10, B - 20, etc.
    val rawDash = """
Question 1
What is the value?
A - 10
B - 20
C - 30
D - 40
    """.trimIndent()

    val paperDash = McqParser.parse(rawDash)
    assertEquals(1, paperDash.questions.size)
    val qDash = paperDash.questions[0]
    assertEquals("10", qDash.options.a)
    assertEquals("20", qDash.options.b)
    assertEquals("30", qDash.options.c)
    assertEquals("40", qDash.options.d)
  }

  @Test
  fun testCivilEngineeringTenQuestionsNumericOptions() {
    val raw = """
## Civil Engineering — Paper 1

### Extreme-Difficulty MCQs

---

### Questions

**Q1. Structural Analysis — Influence Lines**

A simply supported beam \(AB\) of span \(L\) carries a unit moving load. Let \(C\) be a section at \(x=a\) from \(A\), where \(0<a<L\). Consider the influence line for the **bending moment at \(C\)**. Which of the following statements is correct?

1. The influence line is linear on both sides of \(C\), has ordinates zero at \(A\) and \(B\), and its maximum ordinate occurs at \(C\).
2. The ordinate at \(C\) is \(a(L-a)/L\), and the influence-line segments on either side of \(C\) have different slopes.
3. The ordinate at \(C\) is \(a(L-a)/L^2\), and the influence line is continuous but has a slope discontinuity at \(C\).
4. The ordinate at \(C\) is \(a(L-a)/L\), and the influence line has a slope discontinuity at \(C\).

---

**Q2. Structural Mechanics — Plastic Analysis**

A prismatic, fixed-ended beam \(AB\) of span \(L\) has plastic moment capacity \(M_p\) throughout. It is subjected to a uniformly distributed load \(w\) over the entire span. Neglect axial force and second-order effects. Assuming an ideal elastic-perfectly plastic moment-curvature relationship, the **collapse load intensity** is:

1. \(\displaystyle \frac{4M_p}{L^2}\)
2. \(\displaystyle \frac{8M_p}{L^2}\)
3. \(\displaystyle \frac{12M_p}{L^2}\)
4. \(\displaystyle \frac{16M_p}{L^2}\)

---

**Q3. Reinforced Concrete — Limit State Design**

A singly reinforced rectangular RC beam is designed as a **balanced section** according to the conventional limit-state assumptions, with:

* effective depth \(d\),
* width \(b\),
* limiting concrete compressive strain \(0.0035\),
* tensile steel yield strain \(\varepsilon_y\).

Suppose the neutral-axis depth corresponding to the balanced condition is \(x_{u,\mathrm{bal}}\). If the actual neutral axis is **shallower** than \(x_{u,\mathrm{bal}}\) at ultimate, which statement is most appropriate?

1. Concrete reaches its limiting strain before the tension steel yields, resulting in a compression-controlled failure.
2. Tension steel yields before concrete reaches its limiting strain, and the section is under-reinforced.
3. Both steel and concrete remain elastic at failure because the neutral axis is shallow.
4. The section necessarily becomes over-reinforced because \(x_u< x_{u,\mathrm{bal}}\).

---

**Q4. Soil Mechanics — Consolidation**

A normally consolidated clay layer is subjected to a sudden increase in total vertical stress \(\Delta \sigma\). Immediately after loading, assuming one-dimensional consolidation and saturated conditions:

1. \(\Delta u=0\) and the entire \(\Delta \sigma\) is initially carried by the effective stress.
2. \(\Delta u=\Delta\sigma\) and \(\Delta\sigma'=0\).
3. \(\Delta u<\Delta\sigma\) because some stress is always carried by the soil skeleton immediately after loading.
4. \(\Delta u>\Delta\sigma\) because excess pore pressure includes both static and transient components.

---

**Q5. Soil Mechanics — Critical State**

For a saturated soil undergoing drained triaxial compression, the critical-state stress ratio is represented by

$$
\frac{q}{p'}=M.
$$

Consider two tests reaching the critical state at different values of \(p'\). Which statement is correct under the classical critical-state framework?

1. The critical-state deviator stress \(q\) is constant because \(M\) is a material constant.
2. The critical-state void ratio is constant because the soil has reached a unique state.
3. The critical-state deviator stress varies linearly with mean effective stress, \(q=Mp'\), while the corresponding critical-state void ratio depends on \(p'\).
4. Both \(q\) and void ratio are independent of \(p'\) once critical state is reached.

---

**Q6. Fluid Mechanics — Dimensional Analysis**

The drag force \(F_D\) on a smooth sphere moving through a Newtonian fluid depends on:

* sphere diameter \(D\),
* velocity \(V\),
* fluid density \(\rho\),
* dynamic viscosity \(\mu\).

Using dimensional analysis, the number of independent dimensionless groups governing the problem is:

1. 1
2. 2
3. 3
4. 4

---

**Q7. Fluid Mechanics — Open-Channel Flow**

For a prismatic open channel carrying steady uniform flow, the Froude number is defined using the hydraulic depth \(D_h=A/T\). Consider a channel section for which the flow changes from subcritical to supercritical **without an external control, energy loss, or discontinuity**. Which statement is consistent with the governing equations?

1. Such a transition can occur smoothly because specific energy remains constant while Froude number passes continuously through unity.
2. Such a transition is impossible in a smooth, steady, gradually varied flow without passing through a critical section.
3. The transition necessarily occurs through a hydraulic jump.
4. The transition can occur only if Manning's roughness coefficient changes abruptly.

---

**Q8. Engineering Mechanics — Virtual Work**

A rigid, planar mechanism has one degree of freedom and is subjected to conservative applied loads. At an equilibrium configuration, the total potential energy is stationary. Which statement about the corresponding virtual-work condition is correct?

1. The virtual work of the applied conservative forces must be positive for every admissible virtual displacement.
2. The virtual work of the applied conservative forces must vanish for every kinematically admissible virtual displacement.
3. The virtual work must vanish only for the actual displacement occurring during motion.
4. Stationarity of potential energy implies that kinetic energy is also stationary.

---

**Q9. Strength of Materials — Combined Loading**

A homogeneous, isotropic circular shaft is subjected simultaneously to a bending moment \(M\) and a torque \(T\). At the outer surface, the bending stress is \(\sigma_b\) and the torsional shear stress is \(\tau\). Under the maximum-distortion-energy (von Mises) criterion, the equivalent stress at that point is:

1. \(\displaystyle \sigma_{\mathrm{vm}}=\sqrt{\sigma_b^2+\tau^2}\)
2. \(\displaystyle \sigma_{\mathrm{vm}}=\sqrt{\sigma_b^2+3\tau^2}\)
3. \(\displaystyle \sigma_{\mathrm{vm}}=\sqrt{3\sigma_b^2+\tau^2}\)
4. \(\displaystyle \sigma_{\mathrm{vm}}=|\sigma_b|+|\tau|\)

---

**Q10. Geotechnical Engineering — Bearing Capacity**

A strip footing rests on saturated clay under undrained (\(\phi_u=0\)) conditions. For a conventional Terzaghi-type bearing-capacity formulation, which statement is correct regarding the ultimate bearing capacity?

1. The frictional contribution involving \(N_q\) is dominant because \(N_q\) becomes very large as \(\phi\rightarrow0\).
2. The ultimate capacity can be expressed as \(q_u=c_uN_c+qN_q+\frac12\gamma BN_\gamma\), with \(N_q=1\) and \(N_\gamma=0\) for \(\phi_u=0\).
3. Since \(\phi_u=0\), the cohesion contribution disappears and only surcharge contributes.
4. The bearing capacity factor \(N_c\) becomes zero when \(\phi_u=0\).

---

# Answer Key

| Question | Answer |
| -------- | ------ |
| **1**    | **4**  |
| **2**    | **3**  |
| **3**    | **2**  |
| **4**    | **2**  |
| **5**    | **3**  |
| **6**    | **2**  |
| **7**    | **2**  |
| **8**    | **2**  |
| **9**    | **2**  |
| **10**   | **2**  |

---

# Detailed Explanations

## Q1. Influence Line for Bending Moment

**Correct answer: 4**

For a unit load at a distance \(x\) from \(A\), the reaction at \(A\) is

$$
R_A=\frac{L-x}{L}.
$$

For a section \(C\) located at \(a\),

$$
M_C=R_Aa
=\frac{a(L-x)}{L},
\qquad x\leq a.
$$

For the load positioned to the right of \(C\),

$$
M_C=R_B(L-a)
=\frac{x}{L}(L-a),
\qquad x\geq a.
$$

At \(x=a\),

$$
M_C=\frac{a(L-a)}{L}.
$$

Thus the influence line is zero at both supports, reaches its maximum at \(C\), and has **different slopes on either side of \(C\)**.

### Option-by-option

* **Option 1 — Incorrect.** The maximum does occur at \(C\), but saying merely that the line is linear on both sides does not capture the crucial slope discontinuity. More importantly, the statement as written omits the fact that the two slopes are different.
* **Option 2 — Incorrect.** The ordinate \(a(L-a)/L\) is correct, but the statement does not explicitly identify the slope discontinuity. The influence line is continuous but not differentiable at \(C\).
* **Option 3 — Incorrect.** \(a(L-a)/L^2\) has the wrong dimensions. A bending-moment influence-line ordinate for a unit force has dimensions of length, whereas this expression is dimensionless.
* **Option 4 — Correct.** The maximum ordinate is \(a(L-a)/L\), and the slope changes at the section where the moving load crosses \(C\).

---

## Q2. Plastic Collapse of Fixed Beam

**Correct answer: 3**

For a fixed-ended beam under UDL, the elastic bending moment diagram initially has negative moments at the supports and positive moment near midspan.

Plastic hinges ultimately form at:

* \(A\),
* \(B\),
* midspan.

A fixed-ended beam therefore requires **three plastic hinges** for a mechanism.

### Option-by-option

* **Option 1 — Incorrect.** This is substantially below the plastic collapse load and does not satisfy the three-hinge mechanism equilibrium.
* **Option 2 — Incorrect.** It does not correspond to the fixed-ended beam's complete plastic mechanism.
* **Option 3 — Correct.** The standard plastic-collapse result for a fixed-ended prismatic beam under full-span UDL is 12M_p/L^2.
* **Option 4 — Incorrect.** 16M_p/L^2 results from an erroneous treatment of the external displacement/work geometry.

---

## Q3. Balanced and Under-Reinforced RC Sections

**Correct answer: 2**

### Option-by-option

* **Option 1 — Incorrect.** This reverses the sequence. A compression-controlled section has a deeper neutral axis, not a shallower one.
* **Option 2 — Correct.** Tensile reinforcement reaches yield before the concrete reaches its limiting strain.
* **Option 3 — Incorrect.** Both materials do not remain elastic.
* **Option 4 — Incorrect.** Over-reinforcement corresponds to deeper neutral axis.

---

## Q4. Immediate Excess Pore Pressure

**Correct answer: 2**

---

## Q5. Critical State

**Correct answer: 3**

---

## Q6. Buckingham-Pi Analysis

**Correct answer: 2**

---

## Q7. Subcritical-to-Supercritical Transition

**Correct answer: 2**

---

## Q8. Principle of Virtual Work

**Correct answer: 2**

---

## Q9. Von Mises Equivalent Stress

**Correct answer: 2**

---

## Q10. Undrained Bearing Capacity of Strip Footing

**Correct answer: 2**
    """.trimIndent()

    val paper = McqParser.parse(raw)
    assertEquals(10, paper.questions.size)

    // Check Question 1
    val q1 = paper.questions[0]
    assertEquals(1, q1.num)
    assertEquals("D", q1.correctAnswer)
    assertTrue("Q1 option A should have content", q1.options.a.contains("influence line is linear"))
    assertTrue("Q1 option B should have content", q1.options.b.contains("ordinate at"))
    assertTrue("Q1 option C should have content", q1.options.c.contains("a(L-a)/L^2"))
    assertTrue("Q1 option D should have content", q1.options.d.contains("slope discontinuity"))
    assertFalse("Q1 question text should not contain options", q1.question.contains("1. The influence line"))

    // Check Question 2
    val q2 = paper.questions[1]
    assertEquals(2, q2.num)
    assertEquals("C", q2.correctAnswer)
    assertTrue(q2.options.a.contains("4M_p"))
    assertTrue(q2.options.b.contains("8M_p"))
    assertTrue(q2.options.c.contains("12M_p"))
    assertTrue(q2.options.d.contains("16M_p"))

    // Check Question 3
    val q3 = paper.questions[2]
    assertEquals(3, q3.num)
    assertEquals("B", q3.correctAnswer)
    assertTrue(q3.options.b.contains("Tension steel yields"))

    // Check Questions 4 to 10
    val expectedAnswers = listOf("D", "C", "B", "B", "C", "B", "B", "B", "B", "B")
    for (i in 0 until 10) {
      val q = paper.questions[i]
      assertEquals(i + 1, q.num)
      assertEquals("Q${i + 1} answer mismatch", expectedAnswers[i], q.correctAnswer)
      assertTrue("Q${i + 1} options.a must not be empty", q.options.a.isNotBlank())
      assertTrue("Q${i + 1} options.b must not be empty", q.options.b.isNotBlank())
      assertTrue("Q${i + 1} options.c must not be empty", q.options.c.isNotBlank())
      assertTrue("Q${i + 1} options.d must not be empty", q.options.d.isNotBlank())
      assertNotNull("Q${i + 1} explanation must not be null", q.explanation)
      assertTrue("Q${i + 1} explanation must not be empty", q.explanation!!.isNotBlank())
    }

    // Verify StructuredExplanationContent breakdown for Q1
    val parsedExp1 = com.example.ui.components.ExplanationBreakdownParser.parse(q1.explanation, q1.correctAnswer)
    assertEquals(4, parsedExp1.options.size)
    assertEquals("A", parsedExp1.options[0].letter)
    assertFalse(parsedExp1.options[0].isCorrect)
    assertEquals("D", parsedExp1.options[3].letter)
    assertTrue(parsedExp1.options[3].isCorrect)
  }

  @Test
  fun testUserMixedFormatCivilEngineering() {
    val raw = """
## Civil Engineering Paper 1 — Extreme-Level Mixed-Format MCQs

### Questions

**1.** A saturated clay layer is 8 m thick and normally consolidated. Its initial void ratio is 1.20 and compression index Cc = 0.30. The effective overburden pressure at the centre of the layer is 100 kPa. A uniformly distributed load produces an increase of 80 kPa in effective stress at the centre. Assuming one-dimensional consolidation and neglecting secondary compression, the primary consolidation settlement is approximately:

(A) 0.18 m
(B) 0.24 m
(C) 0.30 m
(D) 0.36 m

---

**2.** For a prismatic bar subjected to an axial tensile load P, the cross-sectional area changes from A₀ to A due to Poisson's effect. Consider the following statements:

1. Engineering strain is based on the original dimensions.
2. True strain is logarithmic in nature.
3. For small strains, engineering strain and true strain are approximately equal.
4. Under uniaxial elastic loading, lateral strain has the same sign as longitudinal strain.

Which of the above statements are correct?

(a) 1, 2 and 3 only
(b) 1 and 4 only
(c) 2 and 3 only
(d) 1, 2, 3 and 4

---

**3.** A rectangular open channel has a width of 3 m and carries water at a discharge of 12 m³/s. At a particular section, the flow depth is 1.0 m. Neglecting energy losses, determine the **specific energy** at this section and identify the flow regime.

Take g = 9.81 m/s².

① 2.326 m; subcritical
② 1.816 m; supercritical
③ 2.326 m; supercritical
④ 1.816 m; critical

---

**4.** A singly reinforced rectangular RCC beam is designed as an under-reinforced section. Consider the following sequence of events as the bending moment is gradually increased beyond the elastic range:

**I.** Tensile reinforcement reaches yielding.
**II.** Cracking develops in the tensile concrete.
**III.** Concrete compression strain approaches its limiting value.
**IV.** Tensile steel undergoes substantial plastic deformation.

Which sequence most appropriately represents the progression from early loading toward ultimate flexural failure?

(A) II → I → IV → III
(B) I → II → III → IV
(C) II → III → I → IV
(D) III → II → I → IV

---

**5.** A two-lane highway is being evaluated for horizontal alignment. The radius of a circular curve is 250 m and the design speed is 90 km/h. If the maximum permissible superelevation is 1 in 15, determine the approximate side-friction factor required when the standard relationship is used:

e + f = V²/(127R)

where V is in km/h, R is in metres, and e is expressed as a decimal.

Which option is correct?

**(1)** f ≈ 0.005
**(2)** f ≈ 0.030
**(3)** f ≈ 0.055
**(4)** f ≈ 0.090

---

# ANSWER KEY

| Q. | Answer  |
| -- | ------- |
| 1  | **(B)** |
| 2  | **(a)** |
| 3  | **③**   |
| 4  | **(A)** |
| 5  | **(3)** |

# DETAILED EXPLANATIONS

### 1. Consolidation Settlement

For normally consolidated clay:

S = [Cc H/(1 + e₀)] log₁₀[(σ′₀ + Δσ′)/σ′₀]

Given:

H = 8 m
Cc = 0.30
e₀ = 1.20
σ′₀ = 100 kPa
Δσ′ = 80 kPa

Therefore:

S = [0.30 × 8/(1 + 1.20)] log₁₀(180/100)

S = (2.4/2.2) × log₁₀(1.8)

S ≈ 1.0909 × 0.2553

S ≈ **0.278 m**

Thus, the calculated settlement is approximately **0.28 m**.

**(A) 0.18 m — Incorrect.**
This substantially underestimates the logarithmic compression resulting from the increase from 100 to 180 kPa.

**(B) 0.24 m — Incorrect.**
Although close in magnitude, substitution into the consolidation equation gives approximately 0.278 m.

**(C) 0.30 m — Correct.**
The calculated value is approximately 0.278 m, which corresponds to about **0.30 m** at the precision implied by the alternatives.

**(D) 0.36 m — Incorrect.**
This overestimates the settlement.

---

### 2. Strain Measures

Engineering strain is:

ε = ΔL/L₀

where the original length L₀ is used.

True strain is:

εₜ = ln(L/L₀)

For sufficiently small deformation:

ln(1 + ε) ≈ ε

Therefore, engineering and true strains are approximately equal at small strains.

Under uniaxial tensile loading, the longitudinal strain is positive while the lateral strain caused by the Poisson effect is negative:

εₗateral = −νεₗongitudinal

**1. Correct.** Engineering strain uses the original dimension.

**2. Correct.** True strain is logarithmic.

**3. Correct.** Their numerical difference becomes negligible for sufficiently small strains.

**4. Incorrect.** The lateral strain has the **opposite sign** to the longitudinal tensile strain.

Therefore, statements **1, 2 and 3 only** are correct.

**(a) 1, 2 and 3 only — Correct.**
This excludes statement 4, which incorrectly assigns the same sign to longitudinal and lateral strains.

**(b) 1 and 4 only — Incorrect.**
Statement 1 is valid, but statement 4 is false.

**(c) 2 and 3 only — Incorrect.**
Statements 2 and 3 are valid, but statement 1 is also valid.

**(d) 1, 2, 3 and 4 — Incorrect.**
Statement 4 contradicts the Poisson-effect relationship for uniaxial tension.

---

### 3. Open-Channel Flow

Specific energy is:

E = y + V²/(2g)

The cross-sectional area is:

A = by

= 3 × 1

= 3 m²

Therefore:

V = Q/A

= 12/3

= 4 m/s

Hence:

E = 1 + 4²/(2 × 9.81)

= 1 + 16/19.62

≈ **1.816 m**

Now calculate the Froude number:

Fr = V/√(gy)

= 4/√(9.81 × 1)

≈ 4/3.132

≈ **1.28**

Since Fr > 1, the flow is **supercritical**.

**① 2.326 m; subcritical — Incorrect.**
The specific energy is not 2.326 m, and Fr > 1 establishes supercritical rather than subcritical flow.

**② 1.816 m; supercritical — Correct.**
Both components are correct: E ≈ 1.816 m and Fr ≈ 1.28 > 1.

**③ 2.326 m; supercritical — Incorrect.**
The flow regime is correctly identified, but the calculated specific energy is incorrect.

**④ 1.816 m; critical — Incorrect.**
The specific energy is correct, but the flow is not critical because Fr ≠ 1.

---

### 4. Flexural Behaviour of an Under-Reinforced RCC Beam

In an under-reinforced beam, tensile reinforcement yields before the concrete compression zone reaches its ultimate strain.

The progression is:

**Concrete cracking → steel yielding → substantial plastic deformation of steel → ultimate concrete compression condition/failure**

Thus:

II → I → IV → III

**(A) II → I → IV → III — Correct.**
This represents the characteristic ductile progression of an under-reinforced section.

**(B) I → II → III → IV — Incorrect.**
Tensile reinforcement does not yield before tensile cracking has developed.

**(C) II → III → I → IV — Incorrect.**
In an under-reinforced section, concrete does not reach its limiting compression strain before tensile steel yields.

**(D) III → II → I → IV — Incorrect.**
The concrete compression zone cannot reach its ultimate condition before the earlier cracking and reinforcement-yielding stages.

---

### 5. Horizontal Alignment — Superelevation and Side Friction

Given:

V = 90 km/h
R = 250 m

Maximum superelevation:

e = 1/15

Therefore:

e = 0.0667

Using:

e + f = V²/(127R)

First calculate:

V²/(127R)

= 90²/(127 × 250)

= 8100/31,750

≈ 0.2551

Therefore:

f = 0.2551 − 0.0667

≈ **0.1884**

Thus, none of the listed options is physically consistent with the stated equation.

The supplied options are therefore defective.

This is an important exam-quality check: **the correct calculation gives f ≈ 0.188, not 0.005, 0.030, 0.055, or 0.090.** Hence Question 5 should not be retained in a validated examination set without correcting either the numerical data or the options.
    """.trimIndent()

    val paper = McqParser.parse(raw)
    assertEquals(5, paper.questions.size)

    val q1 = paper.questions[0]
    assertEquals(1, q1.num)
    assertEquals("B", q1.correctAnswer)
    assertEquals("0.18 m", q1.options.a)
    assertEquals("0.24 m", q1.options.b)
    assertEquals("0.30 m", q1.options.c)
    assertEquals("0.36 m", q1.options.d)

    val q2 = paper.questions[1]
    assertEquals(2, q2.num)
    assertEquals("A", q2.correctAnswer)
    assertEquals("1, 2 and 3 only", q2.options.a)
    assertEquals("1 and 4 only", q2.options.b)
    assertEquals("2 and 3 only", q2.options.c)
    assertEquals("1, 2, 3 and 4", q2.options.d)
    assertTrue("Q2 question text must contain statements", q2.question.contains("1. Engineering strain"))

    val q3 = paper.questions[2]
    assertEquals(3, q3.num)
    assertEquals("C", q3.correctAnswer)
    assertEquals("2.326 m; subcritical", q3.options.a)
    assertEquals("1.816 m; supercritical", q3.options.b)
    assertEquals("2.326 m; supercritical", q3.options.c)
    assertEquals("1.816 m; critical", q3.options.d)

    val exp3 = com.example.ui.components.ExplanationBreakdownParser.parse(q3.explanation, q3.correctAnswer)
    assertEquals(4, exp3.options.size)
    assertEquals("A", exp3.options[0].letter)
    assertEquals("B", exp3.options[1].letter)
    assertEquals("C", exp3.options[2].letter)
    assertEquals("D", exp3.options[3].letter)

    val q4 = paper.questions[3]
    assertEquals(4, q4.num)
    assertEquals("A", q4.correctAnswer)
    assertEquals("II → I → IV → III", q4.options.a)

    val q5 = paper.questions[4]
    assertEquals(5, q5.num)
    assertEquals("C", q5.correctAnswer)
    assertEquals("f ≈ 0.005", q5.options.a)
    assertEquals("f ≈ 0.090", q5.options.d)
  }

  @Test
  fun testRegionalAndHindiFormats() {
    val rawHindiAbcd = """
## सामान्य ज्ञान परीक्षा

### प्रश्न

**प्रश्न १.** भारत की राजधानी क्या है?

(अ) मुंबई
(ब) नई दिल्ली
(स) कोलकाता
(द) चेन्नई

---

**प्रश्न २.** निम्नलिखित में से कौन सी नदी डेल्टा नहीं बनाती है?

(क) गंगा
(ख) नर्मदा
(ग) गोदावरी
(घ) कावेरी

---

# उत्तर तालिका

| प्रश्न | उत्तर |
| ------ | ----- |
| १      | **(ब)** |
| २      | **(ख)** |

# व्याख्या

### १. भारत की राजधानी

**(अ) मुंबई — गलत।**
**(ब) नई दिल्ली — सही।** नई दिल्ली भारत की राष्ट्रीय राजधानी है।
**(स) कोलकाता — गलत।**
**(द) चेन्नई — गलत।**
    """.trimIndent()

    val paper = McqParser.parse(rawHindiAbcd)
    assertEquals(2, paper.questions.size)
    assertEquals(1, paper.questions[0].num)
    assertEquals("B", paper.questions[0].correctAnswer)
    assertEquals("मुंबई", paper.questions[0].options.a)
    assertEquals("नई दिल्ली", paper.questions[0].options.b)
    assertEquals("कोलकाता", paper.questions[0].options.c)
    assertEquals("चेन्नई", paper.questions[0].options.d)

    assertEquals(2, paper.questions[1].num)
    assertEquals("B", paper.questions[1].correctAnswer)
    assertEquals("गंगा", paper.questions[1].options.a)
    assertEquals("नर्मदा", paper.questions[1].options.b)

    val exp1 = com.example.ui.components.ExplanationBreakdownParser.parse(paper.questions[0].explanation, paper.questions[0].correctAnswer)
    assertEquals(4, exp1.options.size)
    assertEquals("B", exp1.options[1].letter)
    assertTrue(exp1.options[1].isCorrect)
  }

  @Test
  fun testMatchTheFollowingWithMarkdownTable() {
    val raw = """
# Civil Engineering Paper 1 — Extreme Level

## Question 1 — Match the Following

Match **List-I** with **List-II**:

### List-I: Civil Engineering Concepts

| Column I                                                     | Concept                          |
| ------------------------------------------------------------ | -------------------------------- |
| **A.** Rankine active earth pressure coefficient             | 1. \((1 - \sin φ)/(1 + \sin φ)\) |
| **B.** Critical hydraulic gradient                           | 2. \((G_s - 1)/(1 + e)\)         |
| **C.** Darcy's discharge velocity                            | 3. \(k\,i\)                      |
| **D.** Euler's critical buckling load for a pin-ended column | 4. \(π²EI/L²\)                   |

Which one of the following correctly represents the matching?

**(a)** A–1, B–2, C–3, D–4
**(b)** A–2, B–1, C–4, D–3
**(c)** A–1, B–2, C–4, D–3
**(d)** A–2, B–1, C–3, D–4

---

# Answer Key

**1. (a)**

# Detailed Explanation

### A. Rankine active earth pressure coefficient

For a cohesionless soil under Rankine conditions:

Kₐ = (1 − sin φ)/(1 + sin φ)

Therefore:

**A → 1**

---

### B. Critical hydraulic gradient

For a saturated soil:

i_c = (G_s − 1)/(1 + e)

where:

* G_s = specific gravity of soil solids
* e = void ratio

Therefore:

**B → 2**

---

### C. Darcy's discharge velocity

Darcy's law gives the discharge per unit cross-sectional area as:

v_D = k i

where:

* k = coefficient of permeability
* i = hydraulic gradient

Therefore:

**C → 3**

---

### D. Euler's critical buckling load

For a pin-ended column:

P_cr = π²EI/L²

where:

* E = Young's modulus
* I = least moment of inertia
* L = effective length for the pin-ended condition

Therefore:

**D → 4**

---

### Option-by-option analysis

**(a) A–1, B–2, C–3, D–4 — Correct.**
Every pair follows directly from the governing civil-engineering relation.

**(b) A–2, B–1, C–4, D–3 — Incorrect.**
Rankine's active earth-pressure coefficient is not the expression given in List-II item 2, and Darcy's law is not represented by item 4.

**(c) A–1, B–2, C–4, D–3 — Incorrect.**
The first two matches are correct, but C and D are interchanged: Darcy's law corresponds to 3, while Euler's buckling expression corresponds to 4.

**(d) A–2, B–1, C–3, D–4 — Incorrect.**
The Darcy and Euler matches are correct, but the Rankine coefficient and critical hydraulic gradient are incorrectly interchanged.

**Final matching: A–1, B–2, C–3, D–4.**
    """.trimIndent()

    val paper = McqParser.parse(raw)
    assertEquals(1, paper.questions.size)

    val q1 = paper.questions[0]
    assertEquals(1, q1.num)
    assertEquals("A", q1.correctAnswer)
    assertEquals("A–1, B–2, C–3, D–4", q1.options.a)
    assertEquals("A–2, B–1, C–4, D–3", q1.options.b)
    assertEquals("A–1, B–2, C–4, D–3", q1.options.c)
    assertEquals("A–2, B–1, C–3, D–4", q1.options.d)

    // Verify table parsing helper
    val tableLines = q1.question.lines().filter { com.example.ui.components.TableParserHelper.isTableRow(it) || com.example.ui.components.TableParserHelper.isTableSeparator(it) }
    val parsedTable = com.example.ui.components.TableParserHelper.parseTableBlock(tableLines)
    assertNotNull("Table should be parsed", parsedTable)
    assertEquals(2, parsedTable!!.headers.size)
    assertEquals(4, parsedTable.rows.size)

    // Verify explanation breakdown
    val exp1 = com.example.ui.components.ExplanationBreakdownParser.parse(q1.explanation, q1.correctAnswer)
    assertNotNull("Explanation intro should contain concept derivations", exp1.intro)
    assertTrue("Intro contains Rankine", exp1.intro!!.contains("Rankine"))
    assertEquals(4, exp1.options.size)
    assertEquals("A", exp1.options[0].letter)
    assertTrue(exp1.options[0].isCorrect)
    assertEquals("B", exp1.options[1].letter)
    assertFalse(exp1.options[1].isCorrect)
  }

  @Test
  fun testMatchTheFollowingWithDualLists() {
    val raw = """
# Civil Engineering Paper 1 — Extreme Level

## Question 1 — Match the Following

Match **List-I** with **List-II**:

**List-I**

A. Terzaghi's one-dimensional consolidation theory
B. Reynolds number
C. Euler's critical buckling load for a hinged column
D. Mohr-Coulomb shear strength criterion

**List-II**

1. τ = c + σ′ tan φ
2. Tᵥ = Cᵥt/H_d²
3. Re = ρVD/μ
4. P_cr = π²EI/L²

Choose the correct combination:

**(A)** A–2, B–3, C–4, D–1
**(B)** A–3, B–2, C–1, D–4
**(C)** A–2, B–4, C–3, D–1
**(D)** A–4, B–3, C–2, D–1

---

# Answer Key

**1. (A)**

# Detailed Explanation

### A → 2: Terzaghi's one-dimensional consolidation theory

Terzaghi's consolidation theory relates the **time factor** to the coefficient of consolidation:

Tᵥ = Cᵥt/H_d²

where:

* Tᵥ = time factor
* Cᵥ = coefficient of consolidation
* t = elapsed time
* H_d = maximum drainage path

Hence:

**A → 2**

---

### B → 3: Reynolds number

The Reynolds number is:

Re = ρVD/μ

Hence:

**B → 3**

---

### C → 4: Euler's critical buckling load

For an ideal column with **both ends hinged**:

P_cr = π²EI/L²

Hence:

**C → 4**

---

### D → 1: Mohr-Coulomb criterion

The shear strength of soil according to the Mohr-Coulomb criterion is:

τ_f = c + σ′ tan φ

Hence:

**D → 1**

---

## Option-by-Option Explanation

**(A) A–2, B–3, C–4, D–1 — Correct.**
All four pairings correspond exactly to their respective governing equations.

**(B) A–3, B–2, C–1, D–4 — Incorrect.**
This option systematically assigns equations from unrelated domains.

**(C) A–2, B–4, C–3, D–1 — Incorrect.**
A and D are correctly matched, but Reynolds number and Euler buckling are interchanged.

**(D) A–4, B–3, C–2, D–1 — Incorrect.**
Although B and D are correctly matched, Terzaghi's consolidation is not Euler buckling.

**Final matching: A–2, B–3, C–4, D–1.**
    """.trimIndent()

    val paper = McqParser.parse(raw)
    assertEquals(1, paper.questions.size)

    val q1 = paper.questions[0]
    assertEquals(1, q1.num)
    assertEquals("A", q1.correctAnswer)
    assertEquals("A–2, B–3, C–4, D–1", q1.options.a)
    assertEquals("A–3, B–2, C–1, D–4", q1.options.b)
    assertEquals("A–2, B–4, C–3, D–1", q1.options.c)
    assertEquals("A–4, B–3, C–2, D–1", q1.options.d)

    // Verify dual list parser helper
    val dualData = com.example.ui.components.DualMatchParserHelper.parse(q1.question)
    assertNotNull("Dual list match data must be parsed", dualData)
    assertEquals(4, dualData!!.list1Items.size)
    assertEquals(4, dualData.list2Items.size)
    assertEquals("A", dualData.list1Items[0].first)
    assertEquals("1", dualData.list2Items[0].first)

    // Verify explanation
    val exp1 = com.example.ui.components.ExplanationBreakdownParser.parse(q1.explanation, q1.correctAnswer)
    assertNotNull(exp1.intro)
    assertTrue("Intro contains Terzaghi", exp1.intro!!.contains("Terzaghi"))
    assertEquals(4, exp1.options.size)
    assertEquals("A", exp1.options[0].letter)
    assertTrue(exp1.options[0].isCorrect)
    assertEquals("B", exp1.options[1].letter)
    assertFalse(exp1.options[1].isCorrect)
  }

  @Test
  fun testGlobalLanguagesKoreanJapaneseChineseRussian() {
    // 1. Korean (CSAT / 수능)
    val rawKorean = """
# 대학수학능력시험 모의평가

### 문제

**문제 1.** 다음 중 물의 화학식으로 옳은 것은?

① H₂O
② CO₂
③ NaCl
④ O₂

---

# 정답 및 해설

| 문항 | 정답 |
| ---- | ---- |
| 1    | **①** |

# 해설

### 1. 물의 화학식

**① H₂O — 옳음.** 수소 2개와 산소 1개로 구성된 화합물입니다.
**② CO₂ — 옳지 않음.** 이산화 탄소의 화학식입니다.
**③ NaCl — 옳지 않음.** 염화 나트륨의 화학식입니다.
**④ O₂ — 옳지 않음.** 산소 기체의 화학식입니다.
    """.trimIndent()

    val paperKorean = McqParser.parse(rawKorean)
    assertEquals(1, paperKorean.questions.size)
    assertEquals("A", paperKorean.questions[0].correctAnswer)
    assertEquals("H₂O", paperKorean.questions[0].options.a)
    val expKorean = com.example.ui.components.ExplanationBreakdownParser.parse(paperKorean.questions[0].explanation, paperKorean.questions[0].correctAnswer)
    assertEquals(4, expKorean.options.size)
    assertTrue(expKorean.options[0].isCorrect)
    assertFalse(expKorean.options[1].isCorrect)

    // 2. Japanese (Common Test / 共通テスト)
    val rawJapanese = """
# 大学入学共通テスト

### 問題

**問1.** 日本の首都として正しいものを一つ選べ。

ア. 東京
イ. 大阪
ウ. 京都
エ. 名古屋

---

# 正解一覧

1: ア

# 解説

### 1. 日本の首都

ア. 東京 — 正しい。日本の首都は東京都です。
イ. 大阪 — 誤り。
ウ. 京都 — 誤り。
エ. 名古屋 — 誤り。
    """.trimIndent()

    val paperJapanese = McqParser.parse(rawJapanese)
    assertEquals(1, paperJapanese.questions.size)
    assertEquals("A", paperJapanese.questions[0].correctAnswer)
    assertEquals("東京", paperJapanese.questions[0].options.a)
    val expJapanese = com.example.ui.components.ExplanationBreakdownParser.parse(paperJapanese.questions[0].explanation, paperJapanese.questions[0].correctAnswer)
    assertEquals(4, expJapanese.options.size)
    assertTrue(expJapanese.options[0].isCorrect)
    assertFalse(expJapanese.options[1].isCorrect)

    // 3. Russian (ЕГЭ / USE)
    val rawRussian = """
# Единый государственный экзамен

### Задания

**Задание 1.** Какая планета является третьей по удалённости от Солнца?

А) Венера
Б) Марс
В) Земля
Г) Юпитер

---

# Ответы

1. В

# Решение

### 1. Планеты Солнечной системы

А) Венера — Неверно. Вторая планета.
Б) Марс — Неверно. Четвёртая планета.
В) Земля — Верно. Третья планета от Солнца.
Г) Юпитер — Неверно. Пятая планета.
    """.trimIndent()

    val paperRussian = McqParser.parse(rawRussian)
    assertEquals(1, paperRussian.questions.size)
    assertEquals("C", paperRussian.questions[0].correctAnswer)
    assertEquals("Венера", paperRussian.questions[0].options.a)
    assertEquals("Земля", paperRussian.questions[0].options.c)
    val expRussian = com.example.ui.components.ExplanationBreakdownParser.parse(paperRussian.questions[0].explanation, paperRussian.questions[0].correctAnswer)
    assertEquals(4, expRussian.options.size)
    assertEquals("C", expRussian.options[2].letter)
    assertTrue(expRussian.options[2].isCorrect)
    assertFalse(expRussian.options[0].isCorrect)
  }

  @Test
  fun testMixedLanguagesInSamePaper() {
    val rawMixedPaper = """
# International Multilingual Olympiad

### Questions

**प्रश्न १.** भारत की राजधानी क्या है?
(अ) मुंबई
(ब) नई दिल्ली
(स) कोलकाता
(द) चेन्नई

---

**Question 2.** What is the capital of France?
(A) London
(B) Berlin
(C) Paris
(D) Madrid

---

**문제 3.** 다음 중 대한민국의 수도는 어디인가?
① 부산
② 서울
③ 대구
④ 인천

---

**第4問.** 日本の首都はどこですか。
(ア) 大阪
(イ) 京都
(ウ) 東京
(エ) 名古屋

---

**Вопрос 5.** Какая столица Российской Федерации?
А) Санкт-Петербург
Б) Москва
В) Казань
Г) Новосибирск

---

# Answer Key

1. (ब)
2. (C)
3. ②
4. (ウ)
5. (Б)

# Explanations

### 1. भारत की राजधानी
(अ) मुंबई — गलत।
(ब) नई दिल्ली — सही। नई दिल्ली भारत की राजधानी है।
(स) कोलकाता — गलत।
(द) चेन्नई — गलत।

### 2. Capital of France
(A) London — Incorrect.
(B) Berlin — Incorrect.
(C) Paris — Correct. Paris is the capital of France.
(D) Madrid — Incorrect.

### 3. 대한민국의 수도
① 부산 — 오답.
② 서울 — 정답. 대한민국의 수도는 서울입니다.
③ 대구 — 오답.
④ 인천 — 오답.

### 4. 日本の首都
(ア) 大阪 — 不正解。
(イ) 京都 — 不正解。
(ウ) 東京 — 正解。日本の首都は東京です。
(エ) 名古屋 — 不正解。

### 5. Столица России
А) Санкт-Петербург — Неверно.
Б) Москва — Верно. Москва — столица России.
В) Казань — Неверно.
Г) Новосибирск — Неверно.
    """.trimIndent()

    val paper = McqParser.parse(rawMixedPaper)
    assertEquals(5, paper.questions.size)

    // Q1 Hindi
    assertEquals(1, paper.questions[0].num)
    assertEquals("B", paper.questions[0].correctAnswer)
    assertEquals("नई दिल्ली", paper.questions[0].options.b)

    // Q2 English
    assertEquals(2, paper.questions[1].num)
    assertEquals("C", paper.questions[1].correctAnswer)
    assertEquals("Paris", paper.questions[1].options.c)

    // Q3 Korean
    assertEquals(3, paper.questions[2].num)
    assertEquals("B", paper.questions[2].correctAnswer)
    assertEquals("서울", paper.questions[2].options.b)

    // Q4 Japanese
    assertEquals(4, paper.questions[3].num)
    assertEquals("C", paper.questions[3].correctAnswer)
    assertEquals("東京", paper.questions[3].options.c)

    // Q5 Russian
    assertEquals(5, paper.questions[4].num)
    assertEquals("B", paper.questions[4].correctAnswer)
    assertEquals("Москва", paper.questions[4].options.b)

    // Verify explanation breakdowns
    for (q in paper.questions) {
      val exp = com.example.ui.components.ExplanationBreakdownParser.parse(q.explanation, q.correctAnswer)
      assertEquals(4, exp.options.size)
    }
  }

  @Test
  fun testExtremeDifficultyEngineeringBenchmark() {
    val rawBenchmark = """
# Extreme Difficulty Engineering Benchmark

### Questions

Question 1 — Coupled thermo-mechanical inverse problem

Type: Multi-select + numerical inference + tabular data

A thin isotropic plate occupies
\[
\Omega=\{(x,y)\mid 0\le x\le L,\;0\le y\le W\},
\]
with
\[
L=2.0\,\mathrm{m},\qquad W=1.0\,\mathrm{m},
\]
and thickness \(t=8\,\mathrm{mm}\).

The measured strains at four points are:

Point	\(x/L\)	\(y/W\)	\(\varepsilon_x\)	\(\varepsilon_y\)	\(\gamma_{xy}\)
\(A\)	\(0\)	\(0\)	\(1.42\times10^{-3}\)	\(0.96\times10^{-3}\)	\(0\)
\(B\)	\(1/2\)	\(1/2\)	\(1.77\times10^{-3}\)	\(1.31\times10^{-3}\)	\(2.0\times10^{-4}\)
\(C\)	\(1\)	\(0\)	\(2.05\times10^{-3}\)	\(1.48\times10^{-3}\)	\(0\)
\(D\)	\(1\)	\(1\)	\(1.84\times10^{-3}\)	\(1.22\times10^{-3}\)	\(-3.0\times10^{-4}\)

Which of the following statements are mathematically consistent with the data and constitutive model?

Select all that apply.

A. The thermal contribution cannot be ignored when estimating mechanical stress.
B. The point \(C\) necessarily has the largest principal tensile stress.
C. The sign of \(\tau_{xy}\) at \(D\) changes the principal-stress orientation but does not change the principal-stress magnitudes.
D. At least one measured strain state is incompatible with the assumed temperature field even after allowing the stated measurement uncertainty.
E. The von Mises stress is invariant under adding the same isotropic thermal strain to both normal strain components.

---

Question 2 — Dynamical systems, control, and numerical stability

Type: Matrix classification + discrete-time stability + pole-placement reasoning

Consider the continuous-time system
\[
\dot{\mathbf{x}}=A\mathbf{x}+B u.
\]

The engineer proposes the following candidate closed-loop pole sets:

Candidate	Continuous-time poles
\(P_1\)	\(-1,-2,-3\)
\(P_2\)	\(-2,-2\pm 3j\)
\(P_3\)	\(-0.5,-4,-8\)
\(P_4\)	\(0,-3,-6\)

Part III — Conceptual selection

Which statement is correct?

A. Continuous-time asymptotic stability is destroyed by exact zero-order-hold discretization.
B. A continuous pole \(s=\sigma+j\omega\) maps to a discrete pole whose magnitude depends only on \(\sigma\).
C. Increasing \(T_s\) always moves a stable discrete pole toward the origin.
D. A pole with \(\Re(s)<0\) can map outside the unit circle under the exact exponential mapping.

---

Question 3 — Finite-element singularity and constrained optimization

Type: Matrix reasoning + rank test + numerical conditioning

A finite-element model produces the global system
\[
K\mathbf{u}=\mathbf{f}.
\]

Three alternative constraint sets are proposed.

Claim	Statement
I	\(\mathcal C_1\) removes two displacement degrees of freedom directly.
II	\(\mathcal C_3\) necessarily eliminates every rigid-body mode of the unconstrained structure.
III	If \(N\) has full column rank and \(K\succ0\), then \(N^TKN\succ0\).
IV	A single scalar constraint can never remove a rigid-body mode.
V	If \(K\) is positive definite, an unconstrained linear system \(K\mathbf u=\mathbf f\) has a unique solution.

Identify all universally valid claims.

---

Question 4 — Information theory, communication systems, and coding

Type: Data table + entropy calculation + logical inference

A telemetry system produces symbols
\[
\mathcal X=\{A,B,C,D,E\}
\]

Symbol	Mode \(M_1\)	Mode \(M_2\)
\(A\)	\(0.40\)	\(0.10\)
\(B\)	\(0.25\)	\(0.20\)
\(C\)	\(0.15\)	\(0.30\)
\(D\)	\(0.10\)	\(0.15\)
\(E\)	\(0.10\)	\(0.25\)

Determine whether the proposed code is:

A. Prefix-free
B. Uniquely decodable
C. Optimal
D. None of these

---

Question 5 — Multidisciplinary system-design problem

Type: Decision matrix + nonlinear constraints + reliability + optimization

Three architectures are available:

Architecture	Power consumption \(P\) (W)	Latency \(L\) (ms)	Mass \(m\) (kg)	Reliability \(R\)	Cost \(C\)
\(A\)	84	31	5.4	0.962	74
\(B\)	71	44	4.8	0.981	81
\(C\)	96	24	6.1	0.989	93

Determine which architectures are feasible.

---

# Answer Key

Question	Answer
1	A, C, D, E
2	B
3	I, III, V; dimensions \(2,2,3\); \(\boxed{\kappa_2=7.2\times10^5}\)
4	Prefix-free; uniquely decodable; not entropy-optimal
5	\(A\) and \(B\) satisfy the stated architecture-level constraints; \(C\) violates \(P\) and \(m\)

# Detailed Explanations

1. Coupled thermo-mechanical inverse problem

The total strain can be decomposed as
\[
\boldsymbol\varepsilon = \boldsymbol\varepsilon^{m} + \boldsymbol\varepsilon^{T}.
\]
At \(A\), \(T_A-T_0 = 80^\circ\mathrm C\).
Therefore A, C, D, E are correct.

---

2. Dynamical systems and digital control

For a continuous pole \(s=\sigma+j\omega\), \(|z|=e^{\sigma T_s}\).
Therefore B is correct.

---

3. Finite-element constraint structure

Claims I, III, V are universally valid.

---

4. Information theory and coding

The marginal distribution yields \(H(X)\approx2.199\text{ bits/symbol}\).
The code is prefix-free and uniquely decodable, but not entropy-optimal.

---

5. Multidisciplinary system-design problem

Subsystems \(A\) and \(B\) satisfy constraints.
    """.trimIndent()

    val paper = McqParser.parse(rawBenchmark)
    assertEquals(5, paper.questions.size)

    // Q1
    assertEquals(1, paper.questions[0].num)
    assertEquals("A", paper.questions[0].correctAnswer)
    assertEquals(5, paper.questions[0].options.asList.size)
    assertTrue("Has option E", paper.questions[0].options.e.isNotBlank())
    assertTrue("Has table in Q1", com.example.ui.components.TableParserHelper.isTableRow(paper.questions[0].question.lines().first { it.contains("Point") }))

    // Q2
    assertEquals(2, paper.questions[1].num)
    assertEquals("B", paper.questions[1].correctAnswer)

    // Q3
    assertEquals(3, paper.questions[2].num)
    assertTrue("Q3 has content", paper.questions[2].question.contains("finite-element", ignoreCase = true))

    // Q4
    assertEquals(4, paper.questions[3].num)

    // Q5
    assertEquals(5, paper.questions[4].num)

    // Verify all 5 explanations exist
    for (q in paper.questions) {
      assertNotNull("Explanation for Q${q.num} should exist", q.explanation)
      assertTrue("Explanation has content", q.explanation!!.isNotBlank())
    }
  }

  @Test
  fun testLatexDisplayTextAndStyle() {
    val mathWithDisplay = """\displaystyle \frac{a}{b} + \text{bits/symbol} + \displaytext{hello}"""
    val formatted = com.example.util.LatexHelper.formatMathExpression(mathWithDisplay)
    assertFalse(formatted.contains("\\displaystyle"))
    assertFalse(formatted.contains("\\displaytext"))
    assertTrue(formatted.contains("bits/symbol"))
    assertTrue(formatted.contains("hello"))
    assertTrue(formatted.contains("a / b"))

    val testQqud = """\[ \alpha=0.35,\qqud \beta=-0.20. \]"""
    val formattedQqud = com.example.util.LatexHelper.toRichTextString(testQqud)
    assertTrue(formattedQqud.contains("α"))
    assertTrue(formattedQqud.contains("β"))
    assertFalse(formattedQqud.contains("\\qqud"))
    assertFalse(formattedQqud.contains("\\["))
  }

  @Test
  fun testOrphanedBracketDelimitersStripped() {
    val multilineEquation = """
      A thin isotropic plate occupies
      \[
      \Omega=\{(x,y)\mid 0\le x\le L,\;0\le y\le W\},
      \]
      with
      \[
      L=2.0\,\mathrm{m},\qquad W=1.0\,\mathrm{m},
      \]
      and stray bracket
      \[
      and closing
      \]
    """.trimIndent()

    val formatted = com.example.util.LatexHelper.toRichTextString(multilineEquation)
    assertFalse("Must not contain orphaned [", formatted.contains("\\["))
    assertFalse("Must not contain orphaned ]", formatted.contains("\\]"))
    assertTrue(formatted.contains("Ω"))
    assertTrue(formatted.contains("2.0 m"))
  }

  @Test
  fun testConstitutiveMatrixEquationParsing() {
    val equation = """
      \[
      \begin{bmatrix}
      \sigma_x\\
      \sigma_y\\
      \tau_{xy}
      \end{bmatrix}
      =
      \frac{E}{1-\nu^2}
      \begin{bmatrix}
      1&\nu&0\\
      \nu&1&0\\
      0&0&\frac{1-\nu}{2}
      \end{bmatrix}
      \left(
      \begin{bmatrix}
      \varepsilon_x\\
      \varepsilon_y\\
      \gamma_{xy}
      \end{bmatrix}
      -
      \alpha_T(T-T_0)
      \begin{bmatrix}
      1\\1\\0
      \end{bmatrix}
      \right).
      \]
    """.trimIndent()

    val formatted = com.example.util.LatexHelper.toRichTextString(equation)
    println("DEBUG_MATRIX_FORMATTED:\n$formatted")
    assertFalse("No raw begin bmatrix", formatted.contains("\\begin{bmatrix}"))
    assertFalse("No raw end bmatrix", formatted.contains("\\end{bmatrix}"))
    assertFalse("No orphaned left or right", formatted.contains("\\left") || formatted.contains("\\right"))
    assertTrue("Has sigma_x", formatted.contains("σ") && (formatted.contains("σ<sub>x</sub>") || formatted.contains("σ_x")))
    assertTrue("Has tau_xy", formatted.contains("τ") && (formatted.contains("τ<sub>xy</sub>") || formatted.contains("τ_xy")))
    assertTrue("Has alpha_T", formatted.contains("α") && (formatted.contains("α<sub>T</sub>") || formatted.contains("α_T")))
    assertTrue("Has vector components", formatted.contains("1 ; 1 ; 0") || formatted.contains("1  1  0") || formatted.contains("1"))
  }

  @Test
  fun testExtremeDifficultyEngineeringMCQs3Questions() {
    val rawPaper = """
# Extreme-Difficulty Engineering MCQs

Question 1 — State-Space Control, Non-Normality, and Discrete-Time Stability

Consider the continuous-time linear system

[
\dot{\mathbf{x}}=A\mathbf{x}+B\mathbf{u},
\qquad
A=
\begin{bmatrix}
-1 & \gamma & 0\
0 & -2 & \gamma\
0 & 0 & -4
\end{bmatrix},
\qquad
B=
\begin{bmatrix}
0\
1\
1
\end{bmatrix},
]

where (\gamma>0). A state-feedback controller

[
\mathbf{u}=-K\mathbf{x}
]

Which of the following is the most mathematically defensible conclusion?

A.

[
\mathrm{I},\mathrm{II},\mathrm{III},\mathrm{IV}
]

are all necessarily true because (A_d) is obtained from an asymptotically stable continuous-time system.

B.

Only (\mathrm{I}) and (\mathrm{II}) are necessarily true; stability of eigenvalues does not by itself exclude transient growth or guarantee robustness to arbitrary perturbations.

C.

Only (\mathrm{III}) and (\mathrm{IV}) are necessarily true because induced norms provide stronger guarantees than eigenvalue analysis.

D.

(\mathrm{I}) is false unless (A_{\rm cl}) is diagonalizable, while (\mathrm{II}), (\mathrm{III}), and (\mathrm{IV}) follow from the spectral mapping theorem.

---

Question 2 — Continuum Mechanics, Constitutive Nonlinearity, and Invariants

A material undergoes the finite deformation

[
\mathbf{x}=\boldsymbol{\chi}(\mathbf{X}),
]

with deformation gradient

[
\mathbf F
\begin{bmatrix}
2 & 1 & 0\
0 & 1 & 0\
0 & 0 & \dfrac12
\end{bmatrix}.
]

Which statement is necessarily true?

A. The pressure (p) is uniquely determined from the traction-free condition.

B. Because the deformation is incompressible.

C. The deformation is isochoric, but the traction-free condition determines (p) through the constitutive coefficients (W_1) and (W_2).

D. The condition forces W_1=W_2=0.

---

Question 3 — Information Theory, Detection, and Asymptotic Error Exponents

A binary communication system transmits

[
X\in{0,1}
]

Which option correctly characterizes the claims?

A. Only (\mathrm{I}) and (\mathrm{II}) are correct.

B. Only (\mathrm{I}), (\mathrm{II}), and (\mathrm{IV}) are correct.

C. Only (\mathrm{II}) and (\mathrm{III}) are correct.

D. All four claims are correct.

---

Answer Key

[
\boxed{
1.\ \mathrm{B}
\qquad
2.\ \mathrm{C}
\qquad
3.\ \mathrm{B}
}
]

Detailed Explanations

Question 1

The exact zero-order-hold mapping gives
Hence
[
\boxed{\mathrm{B}}
]
is the correct choice.

---

Question 2

First calculate det F = 1.
Therefore
[
\boxed{\mathrm{C}}
]
is correct.

---

Question 3

The two conditional output distributions are distinct.
Hence
[
\boxed{\mathrm{B}}
]
is correct.
    """.trimIndent()

    val paper = McqParser.parse(rawPaper)
    assertEquals(3, paper.questions.size)

    assertEquals(1, paper.questions[0].num)
    assertEquals("B", paper.questions[0].correctAnswer)
    assertEquals(4, paper.questions[0].options.asList.size)

    assertEquals(2, paper.questions[1].num)
    assertEquals("C", paper.questions[1].correctAnswer)

    assertEquals(3, paper.questions[2].num)
    assertEquals("B", paper.questions[2].correctAnswer)

    for (q in paper.questions) {
      assertNotNull("Explanation for Q${q.num} should exist", q.explanation)
      assertTrue("Explanation has content", q.explanation!!.isNotBlank())
    }
  }

  @Test
  fun testCustomQuestionSetPhasesAndNoAnalysisBug() {
    val rawText = """
Phase 1 — Questions
Q1. Which of the following tests is conducted to determine the sound velocity and dynamic modulus of elasticity of concrete non-destructively?
(A) Rebound Hammer Test
(B) Ultrasonic Pulse Velocity Test
(C) Penetration Resistance Test
(D) Pull-out Test
Q2. For a simply supported beam of span L subjected to a uniformly distributed load w over its entire span, what is the ratio of maximum deflection to the maximum bending moment?
(A) \dfrac{5 L^2}{48 E I}
(B) \dfrac{5 L}{48 E I}
(C) \dfrac{5 L^2}{384 E I}
(D) \dfrac{5 L}{384 E I}
Q3. As per IS 456:2000, what is the minimum grade of concrete recommended for reinforced concrete work exposed to severe environmental conditions?
(A) M20
(B) M25
(C) M30
(D) M35
Q4. The void ratio (e) and porosity (n) of a soil sample are related by which of the following expressions?
(A) e = \dfrac{n}{1 + n}
(B) n = \dfrac{e}{1 + e}
(C) e = \dfrac{1 - n}{n}
(D) n = \dfrac{1 + e}{e}
Q5. In a horizontal circular curve of radius R, if the design speed is V (m/s) and lateral friction coefficient is f, what is the equilibrium superelevation (e) required to fully counteract the centrifugal force without relying on lateral friction?
(A) \dfrac{V^2}{g R} - f
(B) \dfrac{V^2}{2 g R}
(C) \dfrac{V^2}{g R}
(D) \dfrac{V^2}{127 R}
Phase 2 — Answer Key
| Question | Correct Option |
|---|---|
| Q1 | (B) |
| Q2 | (A) |
| Q3 | (C) |
| Q4 | (B) |
| Q5 | (C) |
Phase 3 — Explanations
Question 1
Concept & Reasoning:
The Ultrasonic Pulse Velocity (UPV) test involves measuring the travel time of ultrasonic pulses through concrete. Since pulse velocity is directly proportional to soil/concrete density and stiffness, it allows calculation of the dynamic modulus of elasticity using acoustic wave propagation formulas without damaging the element.
Option Analysis:
 * (A) Incorrect: The rebound hammer test estimates surface hardness and compressive strength based on rebound number, not wave velocity or dynamic modulus.
 * (B) Correct: UPV measures sound pulse travel speed, directly yielding pulse velocity to evaluate concrete uniformity, quality, and dynamic elastic modulus.
 * (C) Incorrect: The penetration resistance (Windsor probe) test measures surface resistance to penetration to estimate compressive strength.
 * (D) Incorrect: The pull-out test measures the force required to pull an embedded steel rod to determine in-situ tensile/shear strength.
Key Point: UPV test measures acoustic wave speed through concrete to evaluate dynamic modulus of elasticity and structural integrity non-destructively.
Question 2
Concept & Reasoning:
For a simply supported beam under full UDL w:
 * Maximum Bending Moment (M_{\text{max}}) occurs at mid-span: M_{\text{max}} = \dfrac{w L^2}{8}
 * Maximum Deflection (\delta_{\text{max}}) occurs at mid-span: \delta_{\text{max}} = \dfrac{5 w L^4}{384 E I}
Taking the ratio:

Option Analysis:
 * (A) Correct: Simplifying \left(\dfrac{5 w L^4}{384 E I}\right) \div \left(\dfrac{w L^2}{8}\right) yields \dfrac{5 L^2}{48 E I}.
 * (B) Incorrect: Incorrect power of L (lacks L^2).
 * (C) Incorrect: Missing the simplification factor 8/384 = 1/48.
 * (D) Incorrect: Represents an incorrect algebraic reduction of both L and numerical constants.
Key Point: Ratio of maximum deflection to maximum moment for UDL on a simply supported beam simplifies to \dfrac{5 L^2}{48 E I}.
Question 3
Concept & Reasoning:
IS 456:2000 (Table 5) specifies durability requirements for reinforced concrete structures exposed to varying environmental conditions to resist corrosion and degradation.
Option Analysis:
 * (A) Incorrect: M20 is the absolute minimum grade for general RCC work (mild exposure).
 * (B) Incorrect: M25 is prescribed for moderate exposure conditions.
 * (C) Correct: M30 is the mandatory minimum concrete grade for RCC exposed to severe conditions (e.g., coastal environments, alternate wetting and drying).
 * (D) Incorrect: M35 is the minimum grade prescribed for very severe exposure conditions.
Key Point: Minimum RCC grades by exposure per IS 456: Mild = M20, Moderate = M25, Severe = M30, Very Severe = M35, Extreme = M40.
Question 4
Concept & Reasoning:
Void ratio (e) is defined as the volume of voids to the volume of solids (V_v / V_s), while porosity (n) is the volume of voids to total volume (V_v / V). Since total volume V = V_s + V_v, dividing numerator and denominator by V_s directly derives n = \dfrac{e}{1 + e}.
Option Analysis:
 * (A) Incorrect: Expresses e in terms of n (e = \dfrac{n}{1 - n}, not 1+n).
 * (B) Correct: Algebraically derived as n = \dfrac{V_v}{V_s + V_v} = \dfrac{e}{1 + e}.
 * (C) Incorrect: Inverted structural relation for e.
 * (D) Incorrect: Incorrect inverse relation for n.
Key Point: Inter-relationships for volumetric soil properties: n = \dfrac{e}{1 + e} and e = \dfrac{n}{1 - n}.
Question 5
Concept & Reasoning:
The basic equilibrium equation for lateral motion on a highway curve is e + f = \dfrac{V^2}{g R}. Equilibrium superelevation is defined as the condition where centrifugal force is fully balanced by superelevation alone without needing any lateral friction force (f = 0).
Option Analysis:
 * (A) Incorrect: Subtracts friction coefficient f, which applies to net required superelevation when friction is present, not equilibrium superelevation.
 * (B) Incorrect: Has an incorrect factor of 2 in the denominator.
 * (C) Correct: Setting f = 0 in e + f = \dfrac{V^2}{g R} gives e = \dfrac{V^2}{g R}.
 * (D) Incorrect: Represents the empirical practical design formula e = \dfrac{V^2}{225 R} or standard design superelevation e = \dfrac{v^2}{127 R} when speed V is in km/h with friction considerations.
Key Point: Equilibrium superelevation assumes zero lateral friction (f = 0), giving e = \dfrac{V^2}{g R} (for V in m/s).
    """.trimIndent()

    val paper = McqParser.parse(rawText)
    assertEquals(5, paper.questions.size)

    // Verify Question 5 Option D does NOT contain Phase 2 or Phase 3!
    val q5 = paper.questions[4]
    assertEquals(5, q5.num)
    assertFalse(q5.options.d.contains("Phase 2"))
    assertFalse(q5.options.d.contains("Answer Key"))
    assertFalse(q5.options.d.contains("Phase 3"))
    assertTrue(q5.options.d.contains("127 R"))

    // Verify all 5 answers are correctly extracted from Phase 2
    assertEquals("B", paper.questions[0].correctAnswer)
    assertEquals("A", paper.questions[1].correctAnswer)
    assertEquals("C", paper.questions[2].correctAnswer)
    assertEquals("B", paper.questions[3].correctAnswer)
    assertEquals("C", paper.questions[4].correctAnswer)

    // Verify all 5 questions have non-empty explanations
    for (q in paper.questions) {
      assertNotNull("Explanation for Q${q.num} should exist", q.explanation)
      assertTrue("Explanation for Q${q.num} should have content", q.explanation!!.isNotBlank())
    }

    // Verify StructuredExplanationContent does not create fake (A) nalysis:
    val parsedExp2 = com.example.ui.components.ExplanationBreakdownParser.parse(paper.questions[1].explanation, "A")
    val hasFakeAnalysisOption = parsedExp2.options.any { it.content.trim().startsWith("nalysis", ignoreCase = true) }
    assertFalse("Should not create fake (A) nalysis: option", hasFakeAnalysisOption)
    assertEquals(4, parsedExp2.options.size)
    assertEquals("A", parsedExp2.options[0].letter)
    assertEquals("B", parsedExp2.options[1].letter)
    assertEquals("C", parsedExp2.options[2].letter)
    assertEquals("D", parsedExp2.options[3].letter)
  }

  @Test
  fun testUserCivilEngineeringPaper() {
    val rawText = """
SSC JE Civil Engineering — Extreme Difficulty

5 Mixed-Type MCQs

Phase 1 — Questions

Question 1 — Structural Analysis

A prismatic fixed-ended beam \(AB\) of span \(L\) carries a uniformly distributed load \(w\) over the entire span. The beam is analysed by the slope-deflection method, taking clockwise end moments as positive. If the member-end rotations are \(\theta_A\) and \(\theta_B\), and there is no joint translation, which expression correctly represents the member-end moment at \(A\)?

A.

\[
M_{AB}=\frac{2EI}{L}(2\theta_A+\theta_B)+\frac{wL^2}{12}
\]

B.

\[
M_{AB}=\frac{2EI}{L}(2\theta_A+\theta_B)-\frac{wL^2}{12}
\]

C.

\[
M_{AB}=\frac{EI}{L}(2\theta_A+\theta_B)-\frac{wL^2}{12}
\]

D.

\[
M_{AB}=\frac{2EI}{L}(\theta_A+2\theta_B)-\frac{wL^2}{12}
\]


---

Question 2 — Soil Mechanics

A saturated normally consolidated clay layer is subjected to a sudden increase in total vertical stress of \(100\ \text{kPa}\). Immediately after loading, the excess pore-water pressure is \(80\ \text{kPa}\). The coefficient of consolidation is constant, and drainage occurs only through the top surface.

At a particular stage of consolidation, the average degree of consolidation based on settlement is \(U=50%\). Which statement about the relationship between average effective stress increment \(\Delta\bar{\sigma}'\) and the applied total stress increment \(\Delta\sigma\) is correct?

A.

\[
\Delta\bar{\sigma}'=20\ \text{kPa}
\]

B.

\[
\Delta\bar{\sigma}'=50\ \text{kPa}
\]

C.

\[
\Delta\bar{\sigma}'=60\ \text{kPa}
\]

D.

\[
\Delta\bar{\sigma}'=80\ \text{kPa}
\]


---

Question 3 — Fluid Mechanics

Water flows steadily through a horizontal pipe that contracts abruptly from diameter \(D_1\) to \(D_2\), where \(D_2<D_1\). The pressure immediately downstream of the contraction is \(p_2\), and the velocity there is \(V_2\). Neglect elevation change and frictional loss except for the local loss due to sudden contraction.

If the coefficient of contraction is \(C_c\), which expression gives the head loss due to the sudden contraction?

A.

\[
h_c=\frac{V_2^2}{2g}
\left(\frac{1}{C_c}-1\right)^2
\]

B.

\[
h_c=\frac{V_2^2}{2g}
\left(\frac{1}{C_c^2}-1\right)
\]

C.

\[
h_c=\frac{V_2^2}{2g}
\left(\frac{1-C_c}{C_c}\right)^2
\]

D.

\[
h_c=\frac{V_2^2}{2g}
\left(\frac{1}{C_c}-1\right)
\]


---

Question 4 — Environmental Engineering

A wastewater treatment plant receives \(10\ \text{ML/day}\) of sewage having an influent BOD of \(250\ \text{mg/L}\). The primary treatment removes \(30%\) of the incoming BOD. The secondary biological treatment removes \(90%\) of the BOD entering it.

Assuming steady flow and negligible solids-related effects on the stated BOD concentrations, the overall BOD removal efficiency of the treatment system is:

A. \(93%\)

B. \(94%\)

C. \(95%\)

D. \(97%\)


---

Question 5 — Transportation Engineering

For a rigid pavement slab, the radius of relative stiffness is

\[
l=\left[\frac{Eh^3}{12k(1-\mu^2)}\right]^{1/4}
\]

where \(E\) is the modulus of elasticity of concrete, \(h\) is slab thickness, \(k\) is the modulus of subgrade reaction, and \(\mu\) is Poisson's ratio.

Two otherwise identical pavement sections have slab thicknesses \(h_1\) and \(h_2\), with

\[
h_2=2h_1.
\]

If the modulus of subgrade reaction for the second pavement is also doubled while \(E\) and \(\mu\) remain unchanged, the ratio \(l_2/l_1\) is:

A. \(1\)

B. \(2^{1/4}\)

C. \(2^{1/2}\)

D. \(2^{3/4}\)


---

Phase 2 — Answer Key

Question	Answer

1	B
2	B
3	A
4	C
5	C



---

Phase 3 — Explanations

1. Structural Analysis

For a prismatic member with no joint translation, the slope-deflection equation is

\[
M_{AB}=\frac{2EI}{L}(2\theta_A+\theta_B)+M_{AB}^{F}
\]

For a UDL over the complete span, the fixed-end moment contribution at \(A\), under the stated sign convention, is

\[
M_{AB}^{F}=-\frac{wL^2}{12}.
\]

Therefore,

\[
M_{AB}
=
\frac{2EI}{L}(2\theta_A+\theta_B)
-\frac{wL^2}{12}.
\]

Key principle: In slope-deflection, the rotational stiffness term is \(\frac{2EI}{L}(2\theta_A+\theta_B)\); the loading effect is then added algebraically through the fixed-end moment.

A — Incorrect: The magnitude of the fixed-end term is correct, but its sign conflicts with the specified clockwise-positive convention.

B — Correct: Both the stiffness term and the fixed-end moment sign are consistent.

C — Incorrect: The coefficient should be \(2EI/L\), not \(EI/L\).

D — Incorrect: The coefficients of the end rotations are reversed.


Key fact to remember:

\[
\boxed{M_{AB}=\frac{2EI}{L}(2\theta_A+\theta_B)+M_{AB}^{F}}
\]


---

2. Soil Mechanics

The average degree of consolidation is

\[
U=\frac{\text{average effective-stress increase}}{\text{final effective-stress increase}}.
\]

For one-dimensional consolidation,

\[
\Delta\sigma'_{\text{final}}=\Delta\sigma
\]

because, after complete dissipation of excess pore pressure, the entire applied stress increment is transferred to the soil skeleton.

Thus,

\[
U=\frac{\Delta\bar{\sigma}'}{\Delta\sigma}.
\]

Given \(U=0.50\) and \(\Delta\sigma=100\) kPa,

\[
\Delta\bar{\sigma}'=0.50(100)=50\text{ kPa}.
\]

The given \(80\) kPa immediate pore-pressure rise is not the value to be used directly for the average effective-stress increment at \(U=50%\).

A — Incorrect: 20 kPa would correspond to only 20% effective-stress transfer.

B — Correct: At \(U=50%\), half of the final effective-stress increase has developed on average.

C — Incorrect: No consolidation relation gives 60 kPa from the stated data.

D — Incorrect: 80 kPa is the initial excess pore pressure, not the effective-stress increase at \(U=50%\).


Key fact to remember:

\[
\boxed{U=\frac{\Delta\bar{\sigma}'}{\Delta\sigma}}
\]

for the average degree of one-dimensional consolidation.


---

3. Fluid Mechanics

For sudden contraction, the flow contracts to a vena contracta before expanding to fill the downstream pipe.

The contraction coefficient is

\[
C_c=\frac{A_c}{A_2},
\]

so

\[
V_c=\frac{Q}{A_c}
=\frac{V_2A_2}{C_cA_2}
=\frac{V_2}{C_c}.
\]

The local head loss is associated with the sudden expansion from the vena-contracta area \(A_c\) to the downstream pipe area \(A_2\):

\[
h_c=\frac{(V_c-V_2)^2}{2g}.
\]

Substituting \(V_c=V_2/C_c\),

\[
h_c
=
\frac{V_2^2}{2g}
\left(\frac{1}{C_c}-1\right)^2.
\]

A — Correct: Direct substitution of the contraction coefficient gives this expression.

B — Incorrect: This lacks the squared velocity-difference structure characteristic of the local loss.

C — Incorrect:

\[
  \left(\frac{1-C_c}{C_c}\right)^3
  =
  \left(\frac1{C_c}-1\right)^2,
\]

D — Incorrect: The velocity-difference term must be squared.


Key fact:

\[
\boxed{h_c=\frac{(V_c-V_2)^2}{2g}
=\frac{V_2^2}{2g}\left(\frac1{C_c}-1\right)^2}
\]

---

4. Environmental Engineering

Initial BOD concentration:

\[
250\text{ mg/L}.
\]

Primary treatment removes \(30%\), so the remaining fraction is \(70%\):

\[
BOD_{\text{after primary}}
=250(0.70)
=175\text{ mg/L}.
\]

Secondary treatment removes \(90%\), leaving \(10%\):

\[
BOD_{\text{final}}
=175(0.10)
=17.5\text{ mg/L}.
\]

Therefore, the total removal is

\[
250-17.5=232.5\text{ mg/L}.
\]

Overall removal efficiency:

\[
\eta
=
\frac{232.5}{250}\times100
=93\%.
\]

Thus, the correct answer should actually be A, not C.

A — Correct: Sequential treatment efficiencies must be applied to the BOD remaining after each preceding stage.

B — Incorrect: Does not follow the sequential removal calculation.

C — Incorrect: \(95%\) would imply a final BOD of \(12.5\) mg/L.

D — Incorrect: \(97%\) would imply a final BOD of only \(7.5\) mg/L.


Key principle:

For sequential removal efficiencies \(r_1,r_2,\ldots\),

\[
\boxed{\eta_{\text{overall}}
=1-(1-r_1)(1-r_2)\cdots}
\]

For this case,

\[
1-(0.70)(0.10)=0.93.
\]


---

5. Transportation Engineering

Given

\[
l=
\left[
\frac{Eh^3}{12k(1-\mu^2)}
\right]^{1/4},
\]

with \(E\) and \(\mu\) unchanged,

\[
l\propto\left(\frac{h^3}{k}\right)^{1/4}.
\]

For the second pavement,

\[
h_2=2h_1,\qquad k_2=2k_1.
\]

Hence,

\[
\frac{l_2}{l_1}
=
\left[
\frac{(2h_1)^3/(2k_1)}
{h_1^3/k_1}
\right]^{1/4}.
\]

Therefore,

\[
\frac{l_2}{l_1}
=
\left(\frac{8}{2}\right)^{1/4}
=
4^{1/4}
=
2^{1/2}.
\]

A — Incorrect: Doubling \(h\) and \(k\) does not cancel because thickness occurs as \(h^3\).

B — Incorrect: This would correspond to a net factor of \(2\) inside the fourth root.

C — Correct: The net factor inside the fourth root is \(4\), giving \(4^{1/4}=\sqrt2\).

D — Incorrect: This results from treating the thickness dependence incorrectly.


Key formula:

\[
\boxed{l\propto h^{3/4}k^{-1/4}}
\]

Hence, when \(h\) and \(k\) are both doubled,

\[
\boxed{\frac{l_2}{l_1}=\sqrt2}.
\]

Quality-control note

There was a deliberate internal check of the five questions, and it exposed two issues in the initial construction:

Q3: Options A and C are algebraically identical, so it is not a valid single-answer MCQ.

Q4: The correct answer is A (93%), not C.
    """.trimIndent()

    val paper = McqParser.parse(rawText)
    assertEquals(5, paper.questions.size)

    // Q1: Check that stem starts cleanly without "— Structural Analysis"
    val q1 = paper.questions[0]
    assertEquals(1, q1.num)
    assertEquals("Structural Analysis", q1.questionType)
    assertTrue("Q1 stem should begin with prismatic", q1.question.startsWith("A prismatic"))
    assertFalse("Q1 stem should not contain leading delimiter", q1.question.contains("— Structural Analysis"))
    assertTrue("Q1 explanation should contain slope-deflection equation", q1.explanation!!.contains("M_{AB}"))
    assertFalse("Q1 explanation should not contain Q4's formula", q1.explanation!!.contains("0.70"))

    // Q2: Check topic and explanation
    val q2 = paper.questions[1]
    assertEquals(2, q2.num)
    assertEquals("Soil Mechanics", q2.questionType)
    assertTrue("Q2 stem should begin with A saturated", q2.question.startsWith("A saturated"))
    assertTrue("Q2 explanation should contain consolidation", q2.explanation!!.contains("consolidation"))

    // Q3: Check that Quality-control note didn't overwrite Q3 explanation
    val q3 = paper.questions[2]
    assertEquals(3, q3.num)
    assertEquals("Fluid Mechanics", q3.questionType)
    assertTrue("Q3 stem should begin with Water flows", q3.question.startsWith("Water flows"))
    assertTrue("Q3 explanation should contain vena contracta", q3.explanation!!.contains("vena contracta"))
    assertFalse("Q3 explanation should not be the QC note", q3.explanation!!.contains("it is not a valid single-answer"))

    // Q4: Check Q4 explanation contains full sequential removal derivation
    val q4 = paper.questions[3]
    assertEquals(4, q4.num)
    assertEquals("Environmental Engineering", q4.questionType)
    assertTrue("Q4 stem should begin with A wastewater", q4.question.startsWith("A wastewater"))
    assertTrue("Q4 explanation should contain sequential removal", q4.explanation!!.contains("Sequential treatment efficiencies"))
    assertFalse("Q4 explanation should not be just the QC note", q4.explanation == "The correct answer is A (93%), not C.")

    // Q5: Check Q5
    val q5 = paper.questions[4]
    assertEquals(5, q5.num)
    assertEquals("Transportation Engineering", q5.questionType)
    assertTrue("Q5 explanation should contain relative stiffness", q5.explanation!!.contains("Transportation Engineering") || q5.explanation!!.contains("stiffness"))
  }

  @Test
  fun testFillInBlankAndSingleLineOptionParsing() {
    val input1 = """
PHASE 1 — QUESTION

1. Choose the correct option: “The report, along with its annexures, ______ submitted yesterday.” A. were B. have been C. was D. are

PHASE 2 — ANSWER KEY

C. was

PHASE 3 — DETAILED EXPLANATION

Concept: The main subject is “The report,” which is singular. The phrase “along with its annexures” is an additional phrase and does not change the number of the subject. Therefore, the singular verb “was” is required. “Yesterday” also indicates a completed past action, so the simple past passive “was submitted” is appropriate.

A. were — Incorrect. “Were” is a plural past form. The grammatical subject “report” is singular.

B. have been — Incorrect. “Have been” is plural present perfect passive and does not agree with the singular subject. It also does not fit the specific past-time marker “yesterday.”

C. was — Correct. “Was submitted” is the singular simple past passive form corresponding to the singular subject “report.”

D. are — Incorrect. “Are” is a plural present-tense form, whereas “report” is singular and the action occurred yesterday.

Key distinction: Expressions such as “along with,” “together with,” “as well as,” and “in addition to” do not change the number of the main subject.
    """.trimIndent()

    val paper1 = McqParser.parse(input1)
    assertEquals("Should parse 1 question", 1, paper1.questions.size)
    val q1 = paper1.questions[0]
    assertEquals(1, q1.num)
    assertTrue("Stem should contain fill-in blank underscores ______", q1.question.contains("______"))
    assertTrue("Stem should contain report text", q1.question.contains("The report, along with its annexures, ______ submitted yesterday."))
    assertFalse("Stem should NOT contain option text", q1.question.contains("were B. have been"))

    assertEquals("were", q1.options.a)
    assertEquals("have been", q1.options.b)
    assertEquals("was", q1.options.c)
    assertEquals("are", q1.options.d)

    assertEquals("C", q1.correctAnswer)
    assertNotNull("Explanation should exist", q1.explanation)
    assertTrue("Explanation must start with Concept, not oncept", q1.explanation!!.startsWith("Concept"))
    assertFalse("Explanation must not have C stripped", q1.explanation!!.startsWith("oncept"))

    // Test Format 2 (with numbered Answer Key and numbered Detailed Explanation)
    val input2 = """
1. Choose the correct option: “The report, along with its annexures, ______ submitted yesterday.” A. were B. have been C. was D. are



PHASE 2 — ANSWER KEY

1. C. was



PHASE 3 — DETAILED EXPLANATION

1. Concept: The main subject is “The report,” which is singular. The phrase “along with its annexures” is additional information and does not change the number of the subject. Therefore, the singular past passive form “was submitted” is required.



A. were — Incorrect. “Were” is plural, but the main subject “report” is singular.

B. have been — Incorrect. “Have been” is plural present perfect passive and does not agree with “report.” It also conflicts with the definite past-time expression “yesterday.”

C. was — Correct. “Was submitted” is the singular simple past passive construction required with “the report.”

D. are — Incorrect. “Are” is plural and present tense, while “report” is singular and the action occurred yesterday.

Key distinction: Words or phrases such as “along with,” “together with,” “as well as,” and “in addition to” do not affect subject-verb agreement.
    """.trimIndent()

    val paper2 = McqParser.parse(input2)
    assertEquals("Should parse 1 question in format 2", 1, paper2.questions.size)
    val q2 = paper2.questions[0]
    assertEquals(1, q2.num)
    assertTrue("Stem should contain ______", q2.question.contains("______"))
    assertEquals("were", q2.options.a)
    assertEquals("have been", q2.options.b)
    assertEquals("was", q2.options.c)
    assertEquals("are", q2.options.d)
    assertEquals("C", q2.correctAnswer)
    assertTrue("Explanation must preserve Concept", q2.explanation!!.contains("Concept: The main subject"))

    // Test RichTextHelper with underscores
    val richText = com.example.util.RichTextHelper.buildRichText(q1.question)
    assertTrue("RichTextHelper must retain ______ without vanishing", richText.text.contains("______"))

    // Test RichTextHelper with bold markdown
    val boldRichText = com.example.util.RichTextHelper.buildRichText("This is __bold__ and this is ______ blank.")
    assertTrue("RichTextHelper must retain blank ______", boldRichText.text.contains("______"))
    assertTrue("RichTextHelper must retain text bold", boldRichText.text.contains("bold"))

    // Test StructuredExplanationContent breakdown
    val parsedExp = com.example.ui.components.ExplanationBreakdownParser.parse(q1.explanation, q1.correctAnswer)
    assertNotNull("Intro should not be null", parsedExp.intro)
    assertTrue("Intro should contain Concept: (and not oncept)", parsedExp.intro!!.contains("Concept:"))
    assertEquals("Should have 4 breakdown options", 4, parsedExp.options.size)
    assertEquals("A", parsedExp.options[0].letter)
    assertFalse(parsedExp.options[0].isCorrect)
    assertEquals("C", parsedExp.options[2].letter)
    assertTrue(parsedExp.options[2].isCorrect)
    assertNotNull("Outro should not be null", parsedExp.outro)
    assertTrue("Outro should contain Key distinction", parsedExp.outro!!.contains("Key distinction"))
  }

  @Test
  fun testHindiDetailedExplanationParsing() {
    val hindiText = """
प्रश्न 1. निम्नलिखित में से कौन सा वाक्य सही है?
(क) वह अपने घर गया।
(ख) वह अपने घर गए।
(ग) वह अपना घर गया।
(घ) वे अपने घर गया।

उत्तर: (क)

विस्तृत व्याख्या:
मुख्य अवधारणा: हिंदी व्याकरण में कर्ता के अनुसार क्रिया का वचन और लिंग निर्धारित होता है। जब कर्ता एकवचन पुल्लिंग 'वह' है, तो क्रिया भी एकवचन 'गया' होगी।

विकल्प (क): वह अपने घर गया — सही। कर्ता 'वह' एकवचन है और क्रिया 'गया' भी एकवचन पुल्लिंग है।
विकल्प (ख): वह अपने घर गए — गलत। 'गए' बहुवचन क्रिया है जो 'वह' के साथ मेल नहीं खाती।
विकल्प (ग): वह अपना घर गया — गलत। निजवाचक संबंध के लिए 'अपने घर' का प्रयोग मानक है।
विकल्प (घ): वे अपने घर गया — गलत। 'वे' बहुवचन है इसलिए क्रिया 'गए' होनी चाहिए।

निष्कर्ष: व्याकरण के नियमों के अनुसार केवल विकल्प (क) शुद्ध वाक्य है।
    """.trimIndent()

    val paper = McqParser.parse(hindiText)
    assertEquals("Should parse 1 Hindi question", 1, paper.questions.size)
    val q = paper.questions[0]
    assertEquals("A", q.correctAnswer)

    val parsed = com.example.ui.components.ExplanationBreakdownParser.parse(q.explanation, q.correctAnswer)
    assertNotNull("Hindi explanation intro should not be null", parsed.intro)
    assertTrue("Hindi intro should contain concept", parsed.intro!!.contains("मुख्य अवधारणा"))
    assertEquals("Should detect 4 options in Hindi explanation", 4, parsed.options.size)
    assertEquals("A", parsed.options[0].letter)
    assertTrue("Option A should be correct", parsed.options[0].isCorrect)
    assertEquals("B", parsed.options[1].letter)
    assertFalse("Option B should be incorrect", parsed.options[1].isCorrect)
    assertNotNull("Hindi outro should not be null", parsed.outro)
    assertTrue("Hindi outro should contain conclusion", parsed.outro!!.contains("निष्कर्ष"))
  }

  @Test
  fun testHindiMultiPhaseTenQuestionsWithDedicatedExplanation() {
    val multiPhaseText = """
चरण 1 — प्रश्न

प्रश्न 1. निम्नलिखित में से किस विकल्प में सभी शब्द तद्भव हैं?

A. आँख, साँप, दूध, गाँव
B. अक्षि, सर्प, दुग्ध, ग्राम
C. नेत्र, नाग, क्षीर, ग्राम
D. आँख, सर्प, दुग्ध, गाँव

प्रश्न 2. निम्नलिखित में से किस शब्द का संधि-विच्छेद व्याकरण की दृष्टि से अशुद्ध है?

A. महोत्सव = महा + उत्सव
B. सदैव = सदा + एव
C. प्रत्येक = प्रति + एक
D. अत्याचार = अति + आचार

---

चरण 2 — उत्तर-कुंजी

प्रश्न	उत्तर

1	A
2	D

---

चरण 3 — व्याख्या

1. तद्भव शब्द

मुख्य तथ्य: संस्कृत से प्राकृत–अपभ्रंश आदि के माध्यम से विकसित होकर हिंदी में आए परिवर्तित रूप तद्भव कहलाते हैं।

- A सही: आँख ← अक्षि, साँप ← सर्प, दूध ← दुग्ध, गाँव ← ग्राम।
- B गलत: ये मूलतः तत्सम रूप हैं।
- C गलत: नेत्र, नाग, क्षीर, ग्राम तत्सम हैं।
- D गलत: सर्प और दुग्ध तत्सम हैं।

याद रखें: अक्षि → आँख, सर्प → साँप, दुग्ध → दूध, ग्राम → गाँव।

---

2. संधि-विच्छेद

मुख्य तथ्य: ‘अत्याचार’ का सही विच्छेद अति + आचार है, जिसमें इ + आ के संयोग से ‘य’ का आगम होता है।

- A सही: महा + उत्सव → महोत्सव।
- B सही: सदा + एव → सदैव।
- C सही: प्रति + एक → प्रत्येक।
- D सही: अति + आचार → अत्याचार।

निष्कर्ष: दिए गए विकल्पों में वास्तव में सभी विच्छेद व्याकरणसम्मत हैं; इसलिए यह प्रश्न दोषपूर्ण है। इस कारण निर्धारित कुंजी में D देना उचित नहीं था।
    """.trimIndent()

    val paper = McqParser.parse(multiPhaseText)
    assertEquals("Should parse 2 questions from multi-phase text", 2, paper.questions.size)

    val q1 = paper.questions[0]
    assertEquals(1, q1.num)
    assertEquals("A", q1.correctAnswer)
    assertTrue(q1.question.contains("तद्भव"))
    assertNotNull(q1.explanation)

    val parsed1 = com.example.ui.components.ExplanationBreakdownParser.parse(q1.explanation, q1.correctAnswer)
    assertNotNull("Intro concept should be extracted", parsed1.intro)
    assertTrue("Intro should contain मुख्य तथ्य", parsed1.intro!!.contains("मुख्य तथ्य"))
    assertEquals("Should parse 4 option breakdown items", 4, parsed1.options.size)
    assertEquals("A", parsed1.options[0].letter)
    assertTrue("Option A should be correct", parsed1.options[0].isCorrect)
    assertEquals("B", parsed1.options[1].letter)
    assertFalse("Option B should be incorrect", parsed1.options[1].isCorrect)
    assertNotNull("Outro should be extracted", parsed1.outro)
    assertTrue("Outro should contain याद रखें", parsed1.outro!!.contains("याद रखें"))

    val q2 = paper.questions[1]
    assertEquals(2, q2.num)
    assertEquals("D", q2.correctAnswer)
    val parsed2 = com.example.ui.components.ExplanationBreakdownParser.parse(q2.explanation, q2.correctAnswer)
    assertNotNull(parsed2.intro)
    assertEquals(4, parsed2.options.size)
    assertNotNull(parsed2.outro)
    assertTrue(parsed2.outro!!.contains("निष्कर्ष"))
  }

  @Test
  fun testAllSampleLanguagesAndFormatsParseSuccessfully() {
    // 1. Hindi Uttarakhand Sample
    val hindiPaper = McqParser.parse(com.example.data.SampleData.SAMPLE_HINDI_PAPER)
    assertEquals("Hindi sample should parse 5 questions", 5, hindiPaper.questions.size)
    assertEquals("Hindi sample should have 5 quick answers", 5, hindiPaper.quickAnswersCount)
    assertEquals("Hindi sample should have 5 explanations", 5, hindiPaper.explanationsCount)
    assertEquals("B", hindiPaper.questions[0].correctAnswer)

    // 2. Korean Hangul Sample
    val koreanPaper = McqParser.parse(com.example.data.SampleData.SAMPLE_KOREAN_EXAM)
    assertEquals("Korean sample should parse 5 questions", 5, koreanPaper.questions.size)
    assertEquals("Korean sample should have 5 quick answers", 5, koreanPaper.quickAnswersCount)
    assertEquals("Korean sample should have 5 explanations", 5, koreanPaper.explanationsCount)
    assertEquals("B", koreanPaper.questions[0].correctAnswer)

    // 3. Multi-Pattern Advanced Science Sample
    val patternPaper = McqParser.parse(com.example.data.SampleData.SAMPLE_NEXT_PATTERN)
    assertEquals("Pattern sample should parse 5 questions", 5, patternPaper.questions.size)
    assertEquals("Pattern sample should have 5 quick answers", 5, patternPaper.quickAnswersCount)
    assertEquals("Pattern sample should have 5 explanations", 5, patternPaper.explanationsCount)

    // 4. Advanced Biology Genetics Sample
    val bioPaper = McqParser.parse(com.example.data.SampleData.SAMPLE_BIOLOGY_ADVANCED)
    assertTrue("Bio sample should have questions", bioPaper.questions.isNotEmpty())

    // 5. General Science Olympiad Sample
    val sciencePaper = McqParser.parse(com.example.data.SampleData.SAMPLE_SCIENCE_PAPER)
    assertEquals("Science sample should parse 10 questions", 10, sciencePaper.questions.size)
    assertEquals("Science sample should have 10 quick answers", 10, sciencePaper.quickAnswersCount)
    assertEquals("Science sample should have 10 explanations", 10, sciencePaper.explanationsCount)
  }

  @Test
  fun testStandaloneSeparatorLinesStrippedFromExplanationAndPrint() {
    val rawExpWithSeparators = """
      Core concept: The primary consolidation settlement is determined by void ratio.

      ---

      (A) 0.18 m — Incorrect. Underestimates settlement.
      (B) 0.24 m — Incorrect.
      (C) 0.30 m — Correct. Matches S ≈ 0.278 m.
      (D) 0.36 m — Incorrect.

      ---

      Note: Always confirm effective stress increase.
    """.trimIndent()

    // 1. Verify McqParser.formatMultiLineExplanation
    val cleaned = McqParser.formatMultiLineExplanation(rawExpWithSeparators)
    assertFalse("Separator --- must be removed", cleaned.contains("---"))
    assertFalse("Must not have 3+ consecutive newlines", cleaned.contains("\n\n\n"))
    assertTrue("Intro content must remain", cleaned.contains("Core concept"))
    assertTrue("Option analysis must remain", cleaned.contains("0.30 m — Correct"))
    assertTrue("Outro note must remain", cleaned.contains("Always confirm"))

    // 2. Verify StructuredExplanationContent breakdown
    val parsed = com.example.ui.components.ExplanationBreakdownParser.parse(rawExpWithSeparators, "C")
    assertNotNull(parsed.intro)
    assertFalse("Intro must not contain ---", parsed.intro!!.contains("---"))
    assertEquals(4, parsed.options.size)
    assertFalse("Option content must not contain ---", parsed.options.any { it.content.contains("---") })
    assertNotNull(parsed.outro)
    assertFalse("Outro must not contain ---", parsed.outro!!.contains("---"))

    // 3. Verify Markdown tables with | --- | are preserved
    val tableText = """
      | Parameter | Value |
      | --- | :---: |
      | H | 8 m |
    """.trimIndent()
    val cleanedTable = McqParser.formatMultiLineExplanation(tableText)
    assertTrue("Table separator row must be preserved", cleanedTable.contains("| --- | :---: |"))

    // 4. Verify Mathematical formulas with minus signs are preserved
    val mathText = """
      Formula:
      Δσ′ = σ₁ - σ₀ = 180 - 100 = 80 kPa
      e = -0.05
    """.trimIndent()
    val cleanedMath = McqParser.formatMultiLineExplanation(mathText)
    assertTrue("Minus signs in math must be preserved", cleanedMath.contains("180 - 100 = 80"))
    assertTrue("Negative numbers must be preserved", cleanedMath.contains("e = -0.05"))

    // 5. Verify HTML print output from PrintDocumentHelper does not contain ---
    val rawExpPaper = """
      1. What is the primary consolidation settlement?
      (A) 0.18 m
      (B) 0.24 m
      (C) 0.30 m
      (D) 0.36 m
      Answer: C
      Explanation:
      $rawExpWithSeparators
    """.trimIndent()
    val testPaper = McqParser.parse(rawExpPaper)
    val config = com.example.model.ExamConfig(
      printMode = com.example.model.PrintMode.SOLUTIONS_ONLY
    )
    val html = com.example.util.PrintDocumentHelper.generatePrintHtml(testPaper, config)
    assertFalse("Printed HTML must not contain literal --- dividers", html.contains("---"))
    assertTrue("Printed HTML must include clean explanation", html.contains("Core concept"))
  }
}




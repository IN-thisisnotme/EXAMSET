package com.example.util

import com.example.model.FolderItem

/**
 * Universal Examination Catalogue Entry for Indian & Global Competitive Examinations.
 */
data class ExamCatalogueEntry(
    val category: String,
    val fullPath: String,
    val keywords: List<String> = emptyList(),
    val accentColor: String = "#0284C7"
) {
    val rootFolder: String
        get() = fullPath.substringBefore("/").substringBefore(">").trim()

    val leafFolder: String
        get() = fullPath.substringAfterLast("/").substringAfterLast(">").trim()
}

/**
 * Comprehensive Universal Examination Taxonomy covering Categories A through J:
 * - A: UPSC & Central Government (Civil Services, SSC, Central Authorities)
 * - B: Railways, Engineering & Technical (RRB, SSC JE, State AE/JE, PSUs, Disciplines)
 * - C: All 28 Indian States & 8 UTs (State PSCs, Subordinate Services, Police, CET/PET)
 * - D: Banking, Insurance & Financial Regulators (IBPS, SBI, RBI, NABARD, SEBI, LIC)
 * - E: Medical, Health Sciences & Nursing (NEET UG/PG, INI-CET, NORCET, Paramedical)
 * - F: School, University & Academic Entrance (JEE, CUET, GATE, NET, CTET, TETs)
 * - G: International Admissions & Languages (SAT, GRE, GMAT, IELTS, TOEFL, JLPT)
 * - H: Professional Certifications & Licensing (CA, CFA, USMLE, NCLEX, IT Cloud)
 * - I: Defence, Police & Paramilitary (NDA, CDS, AFCAT, Agniveer, CAPF, Central Forces)
 * - J: Olympiads, Competitions & Custom Assessments
 */
object ExamCatalogueRegistry {

    val MASTER_CATALOGUE: List<ExamCatalogueEntry> = listOf(
        // ==================== CATEGORY A: UPSC & CENTRAL GOVERNMENT ====================
        ExamCatalogueEntry("UPSC", "UPSC CSE > Prelims > GS Paper 1", listOf("civil services", "ias", "ips", "upsc", "prelims", "general studies"), "#0284C7"),
        ExamCatalogueEntry("UPSC", "UPSC CSE > Prelims > CSAT Paper 2", listOf("csat", "aptitude", "reasoning", "reading comprehension", "maths", "upsc"), "#7C3AED"),
        ExamCatalogueEntry("UPSC", "UPSC CSE > Mains > GS Paper I (History, Geo, Society)", listOf("mains", "gs1", "history", "geography", "society"), "#0284C7"),
        ExamCatalogueEntry("UPSC", "UPSC CSE > Mains > GS Paper II (Polity, Governance, IR)", listOf("mains", "gs2", "polity", "constitution", "governance", "ir"), "#059669"),
        ExamCatalogueEntry("UPSC", "UPSC CSE > Mains > GS Paper III (Economy, Sci-Tech, Envt)", listOf("mains", "gs3", "economy", "environment", "security", "sci-tech"), "#D97706"),
        ExamCatalogueEntry("UPSC", "UPSC CSE > Mains > GS Paper IV (Ethics & Integrity)", listOf("mains", "gs4", "ethics", "integrity", "case studies"), "#DC2626"),
        ExamCatalogueEntry("UPSC", "UPSC CSE > Mains > Essay", listOf("mains", "essay", "essay paper"), "#0D9488"),
        ExamCatalogueEntry("UPSC", "UPSC > IFoS > Prelims", listOf("indian forest service", "ifos", "forestry"), "#16A34A"),
        ExamCatalogueEntry("UPSC", "UPSC > CDS > Elementary Mathematics", listOf("cds", "defence", "maths", "elementary"), "#0284C7"),
        ExamCatalogueEntry("UPSC", "UPSC > CDS > General Knowledge", listOf("cds", "defence", "gk", "general knowledge"), "#059669"),
        ExamCatalogueEntry("UPSC", "UPSC > CDS > English", listOf("cds", "defence", "english", "grammar"), "#7C3AED"),
        ExamCatalogueEntry("UPSC", "UPSC > NDA & NA > Mathematics", listOf("nda", "na", "defence", "maths", "calculus"), "#0284C7"),
        ExamCatalogueEntry("UPSC", "UPSC > NDA & NA > General Ability Test (GAT)", listOf("nda", "na", "gat", "general ability", "english", "science"), "#059669"),
        ExamCatalogueEntry("UPSC", "UPSC > CAPF (AC) > Paper 1 (General Ability & Intelligence)", listOf("capf", "assistant commandant", "bsf", "crpf", "cisf", "itbp"), "#D97706"),
        ExamCatalogueEntry("UPSC", "UPSC > CAPF (AC) > Paper 2 (GS, Essay & Comprehension)", listOf("capf", "essay", "comprehension", "descriptive"), "#DC2626"),
        ExamCatalogueEntry("UPSC", "UPSC > EPFO > EO-AO & APFC (General Ability Test)", listOf("epfo", "apfc", "enforcement officer", "labour laws", "social security"), "#0D9488"),
        ExamCatalogueEntry("UPSC", "UPSC > Combined Geo-Scientist > Prelims", listOf("cgse", "geology", "geophysics", "geoscientist"), "#4F46E5"),
        ExamCatalogueEntry("UPSC", "UPSC > CMS > Combined Medical Services", listOf("cms", "medical", "mbbs", "doctor"), "#059669"),
        ExamCatalogueEntry("UPSC", "UPSC > IES-ISS > Statistics & Economics", listOf("ies", "iss", "statistics", "economics"), "#0284C7"),

        // SSC (Staff Selection Commission)
        ExamCatalogueEntry("SSC", "SSC > CGL > Tier 1 (CBT)", listOf("cgl", "ssc", "tier 1", "combined graduate level"), "#2563EB"),
        ExamCatalogueEntry("SSC", "SSC > CGL > Tier 2 (Paper 1 & 2)", listOf("cgl", "ssc", "tier 2", "mains", "computer knowledge"), "#1D4ED8"),
        ExamCatalogueEntry("SSC", "SSC > CHSL > Tier 1 (10+2 Level)", listOf("chsl", "ssc", "tier 1", "10+2", "clerk", "ldc"), "#059669"),
        ExamCatalogueEntry("SSC", "SSC > CHSL > Tier 2 (CBT)", listOf("chsl", "tier 2", "skill test"), "#047857"),
        ExamCatalogueEntry("SSC", "SSC > CPO > Paper 1 (Delhi Police & CAPF SI)", listOf("cpo", "sub-inspector", "delhi police", "capf", "si"), "#D97706"),
        ExamCatalogueEntry("SSC", "SSC > CPO > Paper 2 (English Comprehension)", listOf("cpo", "paper 2", "english", "si"), "#B45309"),
        ExamCatalogueEntry("SSC", "SSC > MTS & Havaldar > Session 1 & 2", listOf("mts", "multitasking", "havaldar", "ssc"), "#7C3AED"),
        ExamCatalogueEntry("SSC", "SSC > GD Constable > General Duty", listOf("gd", "constable", "crpf", "bsf", "cisf", "rifleman"), "#DC2626"),
        ExamCatalogueEntry("SSC", "SSC > Selection Post > Matriculation Level", listOf("selection post", "phase", "matriculation"), "#0D9488"),
        ExamCatalogueEntry("SSC", "SSC > Selection Post > Graduate Level", listOf("selection post", "graduate", "cgl level"), "#0284C7"),
        ExamCatalogueEntry("SSC", "SSC > Stenographer > Grade C & D", listOf("stenographer", "steno", "shorthand"), "#4F46E5"),
        ExamCatalogueEntry("SSC", "SSC > JHT > Junior Hindi Translator", listOf("jht", "hindi", "translator"), "#059669"),

        // ==================== CATEGORY B: RAILWAYS, ENGINEERING & TECHNICAL (JE > AE) ====================
        // RRB (Railway Recruitment Board)
        ExamCatalogueEntry("RRB", "RRB > NTPC > Stage 1 (CBT-1)", listOf("ntpc", "rrb", "railway", "cbt 1", "cbt-1", "stage 1"), "#0284C7"),
        ExamCatalogueEntry("RRB", "RRB > NTPC > Stage 2 (CBT-2 Level 2, 3, 5, 6)", listOf("ntpc", "cbt 2", "stage 2", "level 5", "level 6", "station master"), "#1D4ED8"),
        ExamCatalogueEntry("RRB", "RRB > Group D > Level 1 (CBT)", listOf("group d", "railway", "trackman", "pointsman", "cbt"), "#D97706"),
        ExamCatalogueEntry("RRB", "RRB > ALP > CBT-1 (Assistant Loco Pilot)", listOf("alp", "loco pilot", "cbt 1", "assistant loco pilot"), "#059669"),
        ExamCatalogueEntry("RRB", "RRB > ALP > CBT-2 (Part A & Part B Technical)", listOf("alp", "cbt 2", "trade test", "technical"), "#047857"),
        ExamCatalogueEntry("RRB", "RRB > Technician > Grade 1 & Grade 3", listOf("technician", "railway technician", "signal", "telecom"), "#7C3AED"),
        ExamCatalogueEntry("RRB", "RRB > JE > CBT-1 (Non-Technical Foundation)", listOf("rrb je", "junior engineer", "cbt 1", "railway je", "je"), "#DC2626"),
        ExamCatalogueEntry("RRB", "RRB > JE > CBT-2 (Technical Core Disciplines)", listOf("rrb je", "junior engineer", "cbt 2", "technical", "je"), "#991B1B"),
        ExamCatalogueEntry("RRB", "RRB > Senior Section Engineer (SSE)", listOf("sse", "senior section engineer", "railway engineer"), "#0D9488"),
        ExamCatalogueEntry("RRB", "RRB > Paramedical Categories", listOf("paramedical", "nurse", "pharmacist", "lab technician"), "#059669"),
        ExamCatalogueEntry("RRB", "RRB > RPF SI > CBT", listOf("rpf", "si", "railway police", "sub inspector"), "#D97706"),
        ExamCatalogueEntry("RRB", "RRB > RPF Constable > CBT", listOf("rpf", "constable", "railway protection force"), "#B45309"),

        // SSC JE (Junior Engineer)
        ExamCatalogueEntry("Engineering", "SSC JE > Civil > Paper 1 (CBT)", listOf("ssc je", "civil", "junior engineer", "paper 1", "building materials", "surveying"), "#0284C7"),
        ExamCatalogueEntry("Engineering", "SSC JE > Civil > Paper 2", listOf("ssc je", "civil", "paper 2", "rcc", "structures", "hydraulics"), "#1D4ED8"),
        ExamCatalogueEntry("Engineering", "SSC JE > Electrical > Paper 1 (CBT)", listOf("ssc je", "electrical", "paper 1", "circuits", "machines", "power"), "#D97706"),
        ExamCatalogueEntry("Engineering", "SSC JE > Electrical > Paper 2", listOf("ssc je", "electrical", "paper 2"), "#B45309"),
        ExamCatalogueEntry("Engineering", "SSC JE > Mechanical > Paper 1 (CBT)", listOf("ssc je", "mechanical", "paper 1", "thermodynamics", "som", "tom"), "#059669"),
        ExamCatalogueEntry("Engineering", "SSC JE > Mechanical > Paper 2", listOf("ssc je", "mechanical", "paper 2", "fluid mechanics"), "#047857"),

        // State AE > JE (Assistant & Junior Engineer Across States)
        ExamCatalogueEntry("Engineering", "State AE-JE > PWD > Civil Engineering", listOf("pwd", "ae", "je", "civil", "state engineering", "assistant engineer", "junior engineer"), "#0284C7"),
        ExamCatalogueEntry("Engineering", "State AE-JE > Irrigation & WRD > Civil Engineering", listOf("wrd", "irrigation", "water resources", "ae", "je", "civil"), "#0D9488"),
        ExamCatalogueEntry("Engineering", "State AE-JE > Electricity Boards (UPPCL, BSPHCL, RVUNL) > Electrical", listOf("uppcl", "bsphcl", "rvunl", "mahatransco", "kptcl", "electricity board", "electrical", "ae", "je"), "#D97706"),
        ExamCatalogueEntry("Engineering", "State AE-JE > Municipal & Urban Development > Engineering", listOf("municipal", "corporation", "urban", "je", "ae"), "#7C3AED"),
        ExamCatalogueEntry("Engineering", "Metro Rail > DMRC & State Metro > Junior Engineer (JE)", listOf("dmrc", "metro", "junior engineer", "je", "electronics", "civil", "electrical"), "#DC2626"),
        ExamCatalogueEntry("Engineering", "Metro Rail > DMRC & State Metro > Assistant Engineer (AE)", listOf("dmrc", "metro", "ae", "assistant manager"), "#991B1B"),

        // UPSC ESE > IES (Engineering Services)
        ExamCatalogueEntry("Engineering", "UPSC ESE > Prelims > Paper 1 (General Studies & Aptitude)", listOf("ese", "ies", "upsc", "engineering services", "gs aptitude", "ethics", "project management"), "#4F46E5"),
        ExamCatalogueEntry("Engineering", "UPSC ESE > Prelims > Paper 2 (Civil Engineering)", listOf("ese", "ies", "civil", "structures", "geotech"), "#0284C7"),
        ExamCatalogueEntry("Engineering", "UPSC ESE > Prelims > Paper 2 (Electrical Engineering)", listOf("ese", "ies", "electrical", "power systems", "machines"), "#D97706"),
        ExamCatalogueEntry("Engineering", "UPSC ESE > Prelims > Paper 2 (Mechanical Engineering)", listOf("ese", "ies", "mechanical", "thermal", "production"), "#059669"),
        ExamCatalogueEntry("Engineering", "UPSC ESE > Prelims > Paper 2 (Electronics & Telecom)", listOf("ese", "ies", "electronics", "telecom", "vlsi", "communication"), "#7C3AED"),

        // GATE & PSUs
        ExamCatalogueEntry("Engineering", "GATE > Engineering > Civil Engineering (CE)", listOf("gate", "civil", "ce", "structures", "geotechnical"), "#0284C7"),
        ExamCatalogueEntry("Engineering", "GATE > Engineering > Computer Science & IT (CS)", listOf("gate", "cs", "computer science", "algorithms", "dbms", "os"), "#0D9488"),
        ExamCatalogueEntry("Engineering", "GATE > Engineering > Electrical Engineering (EE)", listOf("gate", "ee", "electrical", "power systems"), "#D97706"),
        ExamCatalogueEntry("Engineering", "GATE > Engineering > Electronics & Communication (EC)", listOf("gate", "ec", "electronics", "signals", "vlsi"), "#7C3AED"),
        ExamCatalogueEntry("Engineering", "GATE > Engineering > Mechanical Engineering (ME)", listOf("gate", "me", "mechanical", "thermo", "fluid"), "#059669"),
        ExamCatalogueEntry("Engineering", "GATE > Engineering > Data Science & AI (DA)", listOf("gate", "da", "data science", "ai", "machine learning"), "#4F46E5"),
        ExamCatalogueEntry("Engineering", "PSU > ISRO > Scientist Engineer (SC)", listOf("isro", "space", "scientist", "engineer", "drdo"), "#DC2626"),
        ExamCatalogueEntry("Engineering", "PSU > DRDO > CEPTAM & Scientist B", listOf("drdo", "defence research", "scientist", "ceptam"), "#991B1B"),
        ExamCatalogueEntry("Engineering", "PSU > BARC & NPCIL > Scientific Officer", listOf("barc", "npcil", "bhabha", "nuclear"), "#4F46E5"),
        ExamCatalogueEntry("Engineering", "PSU > AAI > ATC (Air Traffic Control)", listOf("aai", "atc", "airports authority", "physics", "maths"), "#0284C7"),
        ExamCatalogueEntry("Engineering", "PSU > ONGC, IOCL, NTPC, BHEL & SAIL > Executive Trainee", listOf("ongc", "iocl", "ntpc", "bhel", "sail", "powergrid", "psu recruitment"), "#D97706"),

        // ==================== CATEGORY C: ALL 28 INDIAN STATES & 8 UTs ====================
        // Uttar Pradesh (UPPSC, UPSSSC, UP Police)
        ExamCatalogueEntry("State PSC", "State PSC > Uttar Pradesh (UPPSC) > PCS Prelims (GS 1)", listOf("uppsc", "up pcs", "uttar pradesh", "pcs", "prelims", "gs 1"), "#0284C7"),
        ExamCatalogueEntry("State PSC", "State PSC > Uttar Pradesh (UPPSC) > PCS Prelims (CSAT Paper 2)", listOf("uppsc", "csat", "up pcs", "aptitude"), "#7C3AED"),
        ExamCatalogueEntry("State PSC", "State PSC > Uttar Pradesh (UPPSC) > RO-ARO Prelims", listOf("uppsc", "ro aro", "samiksha adhikari", "hindi", "gs"), "#059669"),
        ExamCatalogueEntry("State PSC", "State PSC > Uttar Pradesh (UPSSSC) > PET (Preliminary Eligibility)", listOf("upsssc", "pet", "lekhpal", "junior assistant"), "#D97706"),
        ExamCatalogueEntry("State PSC", "State Police > Uttar Pradesh > Police SI & Constable", listOf("up police", "daroga", "si", "constable", "upprpb"), "#DC2626"),

        // Bihar (BPSC, BSSC, BPSSC, BTSC)
        ExamCatalogueEntry("State PSC", "State PSC > Bihar (BPSC) > CCE Prelims (General Studies)", listOf("bpsc", "bihar", "cce", "combined competitive", "prelims", "bihar special"), "#0284C7"),
        ExamCatalogueEntry("State PSC", "State PSC > Bihar (BPSC) > Assistant Engineer (AE)", listOf("bpsc ae", "assistant engineer", "bihar engineering"), "#1D4ED8"),
        ExamCatalogueEntry("State PSC", "State PSC > Bihar (BSSC) > CGL Prelims (Graduate Level)", listOf("bssc", "cgl", "bihar ssc", "bssc cgl"), "#059669"),
        ExamCatalogueEntry("State PSC", "State Police > Bihar (BPSSC) > Daroga (Sub-Inspector)", listOf("bpssc", "bihar daroga", "si", "police"), "#DC2626"),

        // Rajasthan (RPSC, RSSB > RSMSSB)
        ExamCatalogueEntry("State PSC", "State PSC > Rajasthan (RPSC) > RAS Prelims (GK & GS)", listOf("rpsc", "ras", "rajasthan", "prelims", "gk", "rajasthan special"), "#D97706"),
        ExamCatalogueEntry("State PSC", "State PSC > Rajasthan (RSMSSB) > CET (Graduate & 12th Level)", listOf("rsmssb", "rssb", "cet", "common eligibility", "rajasthan cet"), "#0284C7"),
        ExamCatalogueEntry("State PSC", "State PSC > Rajasthan > Patwari & Gram Sevak (VDO)", listOf("patwari", "vdo", "gram sevak", "rajasthan"), "#059669"),

        // Madhya Pradesh (MPPSC, MPESB > Vyapam)
        ExamCatalogueEntry("State PSC", "State PSC > Madhya Pradesh (MPPSC) > State Service Prelims", listOf("mppsc", "madhya pradesh", "mp", "state service", "prelims"), "#0284C7"),
        ExamCatalogueEntry("State PSC", "State PSC > Madhya Pradesh (MPESB) > Sub-Engineer & Patwari", listOf("mpesb", "vyapam", "peb", "sub engineer", "patwari"), "#D97706"),

        // Maharashtra (MPSC)
        ExamCatalogueEntry("State PSC", "State PSC > Maharashtra (MPSC) > Rajyaseva Prelims", listOf("mpsc", "maharashtra", "rajyaseva", "prelims", "csat"), "#0284C7"),
        ExamCatalogueEntry("State PSC", "State PSC > Maharashtra (MPSC) > Combined Group B & C", listOf("mpsc combined", "group b", "group c", "psi", "sti", "aso"), "#059669"),
        ExamCatalogueEntry("State PSC", "State PSC > Maharashtra > Talathi & ZP Bharti", listOf("talathi", "zp", "gramsevak", "maharashtra"), "#D97706"),

        // West Bengal (WBPSC, WBSSC)
        ExamCatalogueEntry("State PSC", "State PSC > West Bengal (WBCS) > Executive Prelims", listOf("wbcs", "west bengal", "wbpsc", "executive", "bengal"), "#0284C7"),
        ExamCatalogueEntry("State PSC", "State PSC > West Bengal (WBPSC) > Miscellaneous & Clerkship", listOf("wbpsc", "clerkship", "miscellaneous"), "#059669"),

        // Tamil Nadu (TNPSC)
        ExamCatalogueEntry("State PSC", "State PSC > Tamil Nadu (TNPSC) > Group 1 Prelims", listOf("tnpsc", "tamil nadu", "group 1", "ccse", "prelims"), "#0284C7"),
        ExamCatalogueEntry("State PSC", "State PSC > Tamil Nadu (TNPSC) > Group 2 & 2A", listOf("tnpsc", "group 2", "group 2a", "tamil"), "#059669"),
        ExamCatalogueEntry("State PSC", "State PSC > Tamil Nadu (TNPSC) > Group 4 & VAO", listOf("tnpsc", "group 4", "vao", "village administrative officer"), "#D97706"),

        // Karnataka (KPSC, KEA)
        ExamCatalogueEntry("State PSC", "State PSC > Karnataka (KPSC) > KAS Prelims (Paper 1 & 2)", listOf("kpsc", "kas", "karnataka", "prelims", "gazetted probationers"), "#0284C7"),
        ExamCatalogueEntry("State PSC", "State PSC > Karnataka (KEA) > FDA & SDA", listOf("kea", "fda", "sda", "karnataka police", "psi"), "#059669"),

        // Andhra Pradesh & Telangana (APPSC, TGPSC > TSPSC)
        ExamCatalogueEntry("State PSC", "State PSC > Andhra Pradesh (APPSC) > Group 1 & 2 Prelims", listOf("appsc", "andhra pradesh", "group 1", "group 2"), "#0284C7"),
        ExamCatalogueEntry("State PSC", "State PSC > Telangana (TGPSC) > Group 1, 2 & 3 Prelims", listOf("tgpsc", "tspsc", "telangana", "group 1", "group 2"), "#059669"),

        // Odisha, Punjab, Haryana, Gujarat, Kerala, Jharkhand, Chhattisgarh, Uttarakhand, Himachal, Assam, Delhi & UTs
        ExamCatalogueEntry("State PSC", "State PSC > Odisha (OPSC) > OAS Prelims", listOf("opsc", "odisha", "oas", "civil services"), "#0284C7"),
        ExamCatalogueEntry("State PSC", "State PSC > Odisha (OSSSC) > Combined Recruitment (RI, AMIN)", listOf("osssc", "ri", "amin", "odisha"), "#D97706"),
        ExamCatalogueEntry("State PSC", "State PSC > Punjab (PPSC) > Civil Services Prelims", listOf("ppsc", "punjab", "civil services", "pcs"), "#0284C7"),
        ExamCatalogueEntry("State PSC", "State PSC > Haryana (HPSC) > HCS Prelims", listOf("hpsc", "haryana", "hcs", "civil services"), "#059669"),
        ExamCatalogueEntry("State PSC", "State PSC > Haryana (HSSC) > CET Group C & D", listOf("hssc", "cet", "haryana cet"), "#D97706"),
        ExamCatalogueEntry("State PSC", "State PSC > Gujarat (GPSC) > Class 1 & 2 Prelims", listOf("gpsc", "gujarat", "class 1", "class 2"), "#0284C7"),
        ExamCatalogueEntry("State PSC", "State PSC > Kerala (Kerala PSC) > KAS & Degree Level Prelims", listOf("kerala psc", "kas", "degree level", "ldc"), "#059669"),
        ExamCatalogueEntry("State PSC", "State PSC > Jharkhand (JPSC) > Combined Civil Services Prelims", listOf("jpsc", "jharkhand", "prelims", "cce"), "#0284C7"),
        ExamCatalogueEntry("State PSC", "State PSC > Jharkhand (JSSC) > CGL (JGGLCCE)", listOf("jssc", "jgglcce", "jharkhand cgl"), "#D97706"),
        ExamCatalogueEntry("State PSC", "State PSC > Chhattisgarh (CGPSC) > State Service Prelims", listOf("cgpsc", "chhattisgarh", "state service"), "#0284C7"),
        ExamCatalogueEntry("State PSC", "State PSC > Uttarakhand (UKPSC) > PCS Prelims & RO-ARO", listOf("ukpsc", "uttarakhand", "pcs", "ro aro"), "#059669"),
        ExamCatalogueEntry("State PSC", "State PSC > Himachal Pradesh (HPPSC) > HPAS Prelims", listOf("hppsc", "himachal", "hpas", "administrative service"), "#0284C7"),
        ExamCatalogueEntry("State PSC", "State PSC > Assam (APSC) > CCE Prelims", listOf("apsc", "assam", "cce", "adre"), "#0D9488"),
        ExamCatalogueEntry("State PSC", "State PSC > Jammu & Kashmir (JKPSC) > KAS Prelims", listOf("jkpsc", "jkssb", "jammu", "kashmir", "kas"), "#0284C7"),
        ExamCatalogueEntry("State PSC", "State PSC > Delhi (DSSSB) > TGT, PGT, PRT & Non-Teaching", listOf("dsssb", "delhi", "teaching", "daso", "special educator"), "#7C3AED"),

        // ==================== CATEGORY D: BANKING, INSURANCE & REGULATORS ====================
        ExamCatalogueEntry("Banking", "Banking > IBPS > PO Prelims", listOf("ibps", "po", "probationary officer", "prelims", "bank"), "#0284C7"),
        ExamCatalogueEntry("Banking", "Banking > IBPS > PO Mains", listOf("ibps po", "mains", "banking awareness", "data analysis"), "#1D4ED8"),
        ExamCatalogueEntry("Banking", "Banking > IBPS > Clerk Prelims", listOf("ibps", "clerk", "csa", "clerical", "prelims"), "#059669"),
        ExamCatalogueEntry("Banking", "Banking > IBPS > SO (Specialist Officer - IT, Law, HR, Rajbhasha)", listOf("ibps so", "it officer", "law officer", "agriculture"), "#7C3AED"),
        ExamCatalogueEntry("Banking", "Banking > IBPS RRB > Officer Scale 1 (PO) Prelims", listOf("ibps rrb", "rrb po", "scale 1", "gramin bank"), "#D97706"),
        ExamCatalogueEntry("Banking", "Banking > IBPS RRB > Office Assistant (Clerk)", listOf("ibps rrb", "office assistant", "rrb clerk"), "#B45309"),
        ExamCatalogueEntry("Banking", "Banking > SBI > PO Prelims", listOf("sbi", "sbi po", "probationary officer", "prelims"), "#0284C7"),
        ExamCatalogueEntry("Banking", "Banking > SBI > PO Mains", listOf("sbi po", "mains", "reasoning", "general economy"), "#1D4ED8"),
        ExamCatalogueEntry("Banking", "Banking > SBI > Clerk (Junior Associate)", listOf("sbi", "sbi clerk", "junior associate"), "#059669"),
        ExamCatalogueEntry("Banking", "Banking > RBI > Grade B Phase 1", listOf("rbi", "rbi grade b", "reserve bank", "phase 1", "economic social issues"), "#DC2626"),
        ExamCatalogueEntry("Banking", "Banking > RBI > Assistant Prelims", listOf("rbi assistant", "rbi", "assistant"), "#0D9488"),
        ExamCatalogueEntry("Banking", "Banking > NABARD, SEBI & IRDAI > Assistant Manager (Grade A)", listOf("nabard", "sebi", "irdai", "pfrda", "grade a", "regulator"), "#4F46E5"),
        ExamCatalogueEntry("Banking", "Insurance > LIC > AAO Prelims (Assistant Administrative Officer)", listOf("lic", "lic aao", "insurance", "aao"), "#D97706"),
        ExamCatalogueEntry("Banking", "Insurance > LIC & NIACL > ADO & Assistant", listOf("lic ado", "niacl", "nicl", "uiic", "insurance assistant"), "#059669"),

        // ==================== CATEGORY E: MEDICAL, HEALTH SCIENCES & LIFE SCIENCES ====================
        ExamCatalogueEntry("Medical", "Medical > NEET UG > Physics, Chemistry & Biology", listOf("neet", "neet ug", "medical entrance", "mbbs", "bds", "biology", "physics", "chemistry"), "#059669"),
        ExamCatalogueEntry("Medical", "Medical > NEET PG > Clinical & Pre-Clinical", listOf("neet pg", "md", "ms", "medical post graduation", "doctor"), "#047857"),
        ExamCatalogueEntry("Medical", "Medical > INI-CET > AIIMS, JIPMER, PGIMER Entrance", listOf("ini cet", "inicet", "aiims", "jipmer", "pgimer"), "#0284C7"),
        ExamCatalogueEntry("Medical", "Medical > FMGE & NExT > Screening Examination", listOf("fmge", "next", "foreign medical graduate"), "#7C3AED"),
        ExamCatalogueEntry("Medical", "Nursing > AIIMS NORCET > Nursing Officer Examination", listOf("norcet", "aiims nursing", "nursing officer", "b.sc nursing"), "#D97706"),
        ExamCatalogueEntry("Medical", "Medical > ESIC > Medical Officer & Paramedical Recruitment", listOf("esic", "medical officer", "pharmacist", "paramedical"), "#DC2626"),

        // ==================== CATEGORY F: SCHOOL, UNIVERSITY & ACADEMIC ENTRANCE ====================
        ExamCatalogueEntry("Entrance", "Entrance > JEE Main > Paper 1 (Physics, Chemistry & Maths)", listOf("jee main", "jee", "engineering entrance", "iit", "nit"), "#0284C7"),
        ExamCatalogueEntry("Entrance", "Entrance > JEE Advanced > Paper 1 & 2", listOf("jee advanced", "iit jee", "advanced maths"), "#1D4ED8"),
        ExamCatalogueEntry("Entrance", "Entrance > CUET UG > Common University Entrance Test", listOf("cuet", "cuet ug", "central university", "general test"), "#059669"),
        ExamCatalogueEntry("Entrance", "Entrance > CUET PG > Postgraduate Entrance", listOf("cuet pg", "postgraduate", "domain knowledge"), "#047857"),
        ExamCatalogueEntry("Entrance", "Entrance > CAT > Quantitative, DILR & VARC", listOf("cat", "iim", "mba entrance", "varc", "dilr", "quant"), "#D97706"),
        ExamCatalogueEntry("Entrance", "Entrance > CLAT > UG Legal Reasoning, GK & Logical", listOf("clat", "law entrance", "nlu", "legal aptitude"), "#7C3AED"),
        ExamCatalogueEntry("Teaching", "Teaching > UGC NET > Paper 1 (Teaching & Research Aptitude)", listOf("ugc net", "net paper 1", "jrf", "assistant professor", "research aptitude"), "#4F46E5"),
        ExamCatalogueEntry("Teaching", "Teaching > CSIR NET > Life Sciences, Chemical & Physical Sciences", listOf("csir net", "csir", "jrf", "life sciences", "chemical"), "#0D9488"),
        ExamCatalogueEntry("Teaching", "Teaching > CTET > Paper 1 (Primary Class I-V)", listOf("ctet", "ctet paper 1", "child pedagogy", "primary teacher"), "#059669"),
        ExamCatalogueEntry("Teaching", "Teaching > CTET > Paper 2 (Upper Primary Class VI-VIII)", listOf("ctet", "ctet paper 2", "maths science", "social studies"), "#047857"),
        ExamCatalogueEntry("Teaching", "Teaching > KVS, NVS & EMRS > PGT, TGT & PRT Recruitment", listOf("kvs", "nvs", "emrs", "pgt", "tgt", "prt"), "#D97706"),

        // ==================== CATEGORY G: INTERNATIONAL ADMISSIONS & LANGUAGES ====================
        ExamCatalogueEntry("Global", "International > SAT > Math, Reading & Writing", listOf("sat", "college board", "us admissions", "reading writing math"), "#0284C7"),
        ExamCatalogueEntry("Global", "International > GRE > Verbal, Quantitative & Analytical", listOf("gre", "ets", "graduate record", "quant", "verbal"), "#1D4ED8"),
        ExamCatalogueEntry("Global", "International > GMAT > Focus Edition (Quant, Verbal, Data)", listOf("gmat", "mba", "business school", "data insights"), "#D97706"),
        ExamCatalogueEntry("Global", "Language > IELTS > Academic & General Training", listOf("ielts", "english", "listening", "reading", "writing", "speaking"), "#DC2626"),
        ExamCatalogueEntry("Global", "Language > TOEFL iBT > English Language Test", listOf("toefl", "toefl ibt", "ets"), "#059669"),
        ExamCatalogueEntry("Global", "Language > JLPT > Japanese Proficiency (N5 to N1)", listOf("jlpt", "japanese", "kanji", "grammar", "n5", "n4", "n3", "n2", "n1"), "#7C3AED"),

        // ==================== CATEGORY H: PROFESSIONAL & LICENSING CERTIFICATIONS ====================
        ExamCatalogueEntry("Professional", "Professional > CA Foundation > Paper 1 to 4", listOf("ca foundation", "icai", "chartered accountant", "accounts", "law"), "#0284C7"),
        ExamCatalogueEntry("Professional", "Professional > CA Intermediate > Group 1 & Group 2", listOf("ca inter", "icai", "taxation", "auditing"), "#1D4ED8"),
        ExamCatalogueEntry("Professional", "Professional > CFA > Level 1 & Level 2", listOf("cfa", "cfa institute", "portfolio", "finance", "ethics"), "#D97706"),
        ExamCatalogueEntry("Professional", "Professional > USMLE > Step 1 & Step 2 CK", listOf("usmle", "step 1", "step 2 ck", "united states medical licensing"), "#059669"),
        ExamCatalogueEntry("Professional", "Professional > NCLEX-RN > Next Gen Nursing Examination", listOf("nclex", "nclex rn", "registered nurse"), "#DC2626"),
        ExamCatalogueEntry("Professional", "IT Cloud > AWS > Solutions Architect & Cloud Practitioner", listOf("aws", "cloud", "solutions architect", "developer"), "#D97706"),

        // ==================== CATEGORY I: DEFENSE, POLICE & PARAMILITARY ====================
        ExamCatalogueEntry("Defense", "Defense > AFCAT > General Awareness, Reasoning & Verbal", listOf("afcat", "air force", "flying branch", "ground duty"), "#0284C7"),
        ExamCatalogueEntry("Defense", "Defense > Agniveer > Army General Duty, Technical & Clerk", listOf("agniveer", "army", "rally", "soldier gd"), "#16A34A"),
        ExamCatalogueEntry("Defense", "Defense > Indian Navy > SSR & MR", listOf("navy", "ssr", "mr", "sailor"), "#1D4ED8"),
        ExamCatalogueEntry("Defense", "Defense > Indian Air Force > Airman Group X & Y", listOf("air force", "group x", "group y", "airman"), "#0284C7"),
        ExamCatalogueEntry("Defense", "Paramilitary > CRPF, BSF, CISF & ITBP > Tradesman & Constable", listOf("paramilitary", "crpf", "bsf", "cisf", "itbp", "ssb", "constable"), "#DC2626"),

        // ==================== CATEGORY J: OLYMPIADS, COMPETITIONS & CUSTOM ====================
        ExamCatalogueEntry("Olympiad", "Olympiad > Science & Math > IMO & IPhO", listOf("imo", "ipho", "mathematics olympiad", "physics olympiad", "hbcse"), "#7C3AED"),
        ExamCatalogueEntry("Olympiad", "Olympiad > Chemistry & Biology > IChO & IBO", listOf("icho", "ibo", "chemistry olympiad", "biology olympiad"), "#059669"),
        ExamCatalogueEntry("School", "School > CBSE Board > Class 10 & 12 Objective Practice", listOf("cbse", "class 10", "class 12", "board exam", "term 1"), "#0284C7"),
        ExamCatalogueEntry("Aptitude", "Assessment > General Aptitude > Quantitative & Logical Reasoning", listOf("aptitude", "quantitative", "reasoning", "campus placement", "mock test"), "#4F46E5")
    )

    /**
     * Universal Canonical Normalizer:
     * Converts any mixed `/` or `>` inputs into standard `Parent > Child > Subchild` format,
     * removing empty tokens and trimming spaces defensively.
     */
    fun normalizeFolderPath(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace("/", " > ")
            .split(">")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" > ")
    }

    /**
     * Search the examination catalogue with intelligent prefix, word-boundary,
     * and subfolder matching.
     */
    fun searchCatalogue(
        query: String,
        customFolders: List<FolderItem> = emptyList(),
        maxResults: Int = 10
    ): List<ExamCatalogueEntry> {
        val q = query.trim().lowercase()

        // 1. Build custom entries from user-created folders and their hierarchies
        val customEntries = customFolders.map { f ->
            val parentName = if (f.parentId != null) customFolders.find { it.id == f.parentId }?.name else null
            val fullPath = if (parentName != null) "$parentName > ${f.name}" else f.name
            ExamCatalogueEntry(
                category = "Custom Folder",
                fullPath = fullPath,
                keywords = listOf(f.name.lowercase()),
                accentColor = f.color
            )
        }

        val cleanQ = q.replace("/", ">").trim().lowercase()
        if (cleanQ.isBlank()) {
            // When query is empty, return user's custom folders first, followed by top universal presets
            val defaultPicks = listOf(
                "UPSC CSE > Prelims > GS Paper 1",
                "SSC > CGL > Tier 1 (CBT)",
                "RRB > NTPC > Stage 1 (CBT-1)",
                "SSC JE > Civil > Paper 1 (CBT)",
                "State PSC > Bihar (BPSC) > CCE Prelims (General Studies)",
                "State PSC > Uttar Pradesh (UPPSC) > PCS Prelims (GS 1)",
                "Banking > IBPS > PO Prelims",
                "Medical > NEET UG > Physics, Chemistry & Biology",
                "Entrance > JEE Main > Paper 1 (Physics, Chemistry & Maths)"
            )
            val masterDefaults = MASTER_CATALOGUE.filter { it.fullPath in defaultPicks }
            return (customEntries.take(4) + masterDefaults).distinctBy { it.fullPath }.take(maxResults)
        }

        val pool = customEntries + MASTER_CATALOGUE
        val isSubfolderQuery = cleanQ.contains(">")
        val activeSegment = if (isSubfolderQuery) cleanQ.substringAfterLast(">").trim() else cleanQ
        val parentSegment = if (isSubfolderQuery) cleanQ.substringBeforeLast(">").trim() else ""

        return pool
            .asSequence()
            .map { entry ->
                val fullLower = entry.fullPath.lowercase()
                val catLower = entry.category.lowercase()
                val segments = fullLower.split(">").map { it.trim() }
                val rootFolder = segments.firstOrNull() ?: ""
                val leafFolder = segments.lastOrNull() ?: ""
                val words = fullLower.split(" ", ">", "/", "-", "(", ")", ",").filter { it.isNotBlank() }

                var score = 0
                val isCustom = entry in customEntries

                if (isSubfolderQuery) {
                    // When user types "UPSC > " or "UPSC > Pre", match parent and active segment
                    val parentMatches = parentSegment.isBlank() || fullLower.contains(parentSegment)
                    if (parentMatches) {
                        score += 50
                        if (activeSegment.isBlank()) {
                            score += 30
                        } else {
                            val segmentMatchesPrefix = segments.any { it.startsWith(activeSegment) }
                            val wordMatchesPrefix = words.any { it.startsWith(activeSegment) }
                            if (segmentMatchesPrefix) score += 60
                            else if (wordMatchesPrefix) score += 40
                            else if (fullLower.contains(activeSegment)) score += 10
                            else score = 0 // Does not match active subfolder
                        }
                    } else {
                        score = 0
                    }
                } else {
                    // Standard root/folder query: strict word & segment prefix matching
                    val startsWithExact = fullLower.startsWith(cleanQ) || catLower.startsWith(cleanQ)
                    val segmentStartsWith = segments.any { it.startsWith(cleanQ) }
                    val wordStartsWith = words.any { it.startsWith(cleanQ) }
                    val keywordStartsWith = entry.keywords.any { it.startsWith(cleanQ) || it.split(" ").any { w -> w.startsWith(cleanQ) } }

                    if (startsWithExact) {
                        score += 100
                    } else if (segmentStartsWith) {
                        score += 80
                    } else if (wordStartsWith) {
                        score += 60
                    } else if (keywordStartsWith) {
                        score += 40
                    } else if (cleanQ.length >= 3 && fullLower.contains(cleanQ)) {
                        // Loose substring only for queries with at least 3 characters
                        score += 15
                    }
                }

                if (isCustom && score > 0) {
                    score += 50 // Prioritize user-created folders
                }

                entry to score
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.fullPath }
            .take(maxResults)
            .toList()
    }
}

package com.craftflowtechnologies.meetingmind.core.learning

import com.craftflowtechnologies.meetingmind.core.model.NoteBlockType
import com.craftflowtechnologies.meetingmind.core.model.SectionSource
import com.craftflowtechnologies.meetingmind.core.model.TemplateSection

/**
 * First-class academic and professional study domains for Learning.
 * Drives ASR vocabulary biasing, note template sections, spoken notation normalization,
 * and discipline-specific active recall archetypes.
 */
enum class SubjectDomain(
    val displayName: String,
    val shortLabel: String,
    val summary: String
) {
    GENERAL(
        "General Studies",
        "General",
        "Structured concepts, definitions, examples, and review questions"
    ),
    MATH_QUANT(
        "Mathematics & Statistics",
        "Math",
        "Definitions, theorems, step-by-step derivations, and proofs"
    ),
    ENGINEERING(
        "Engineering & Applied Physics",
        "Engineering",
        "Physical models, SI variables, governing equations, and Given/Find/Solution"
    ),
    ACCOUNTING_FINANCE(
        "Accounting & Finance",
        "Accounting",
        "IFRS/GAAP rules, Dr/Cr journal entries, T-accounts, and schedules"
    ),
    PROGRAMMING_CS(
        "Programming & Computer Science",
        "CS & Code",
        "Algorithms, syntax-highlighted code, dry-run trace tables, and Big-O"
    ),
    ECONOMICS(
        "Economics",
        "Economics",
        "Models, elasticity/equilibrium equations, shifts, and policy trade-offs"
    ),
    CHEMISTRY(
        "Chemistry",
        "Chemistry",
        "Reactions, stoichiometry, mechanisms, equilibrium, and thermochemistry"
    ),
    SCIENCES_MED(
        "Biology & Medical Sciences",
        "Bio & Med",
        "Pathways, mechanisms, differential tables, and clinical pearls"
    ),
    HUMANITIES_LAW(
        "Law, Business & Humanities",
        "Law & Arts",
        "IRAC case briefs, primary arguments, evidence quotes, and critiques"
    ),
    HIGH_SCHOOL(
        "High School & Exam Prep",
        "High School",
        "Learning objectives, clear lesson steps, worked examples, and past-paper drills"
    );

    /** Domain-specific terminology used to bias speech recognition for technical lectures. */
    fun vocabularyHints(): List<String> = when (this) {
        GENERAL -> listOf(
            "Syllabus", "Definition", "Hypothesis", "Framework", "Methodology",
            "Synthesis", "Counterexample", "Prerequisite", "Key takeaway", "Rubric"
        )
        MATH_QUANT -> listOf(
            "Eigenvalues", "Eigenvectors", "Jacobian", "Hessian", "Lagrange multiplier",
            "Orthogonal", "Determinant", "Cauchy-Schwarz", "Bayes' theorem", "Covariance",
            "Homoscedasticity", "p-value", "Confidence interval", "Convergence",
            "Isomorphism", "Taylor series", "Partial derivative", "Stochastic"
        )
        ENGINEERING -> listOf(
            "Kirchhoff", "Thevenin", "Norton", "Laplace transform", "Fourier transform",
            "Bernoulli", "Reynolds number", "Navier-Stokes", "Stress tensor", "Young's modulus",
            "Thermodynamics", "Entropy", "Enthalpy", "Bode plot", "Transfer function",
            "PID controller", "Free body diagram", "Impedance", "Phasor"
        )
        ACCOUNTING_FINANCE -> listOf(
            "IFRS 16", "IFRS 15", "IFRS 9", "IAS 16", "GAAP", "Debit", "Credit",
            "Amortisation", "Depreciation", "Accruals", "Deferred revenue", "Retained earnings",
            "Trial balance", "T-account", "Contra asset", "Goodwill", "Impairment",
            "EBITDA", "WACC", "Net present value", "Discounted cash flow"
        )
        PROGRAMMING_CS -> listOf(
            "Dijkstra", "Bellman-Ford", "Memoization", "Dynamic programming", "Time complexity",
            "Space complexity", "Amortized", "Binary search tree", "Red-black tree", "Hash table",
            "Topological sort", "Concurrency", "Deadlock", "Mutex", "Semaphore",
            "Polymorphism", "Idempotent", "Recursion", "Big-O"
        )
        ECONOMICS -> listOf(
            "Elasticity", "Pareto efficiency", "Nash equilibrium", "Marginal utility",
            "Marginal cost", "Deadweight loss", "Consumer surplus", "Producer surplus",
            "IS-LM", "Phillips curve", "Comparative advantage", "Oligopoly",
            "Monopolistic competition", "Ceteris paribus", "Keynesian", "Monetary policy",
            "Gini coefficient"
        )
        CHEMISTRY -> listOf(
            "Stoichiometry", "Le Chatelier", "Enthalpy", "Gibbs free energy",
            "Electronegativity", "Hybridization", "Nucleophile", "Electrophile",
            "SN1", "SN2", "Molarity", "Titration", "Buffer", "Henderson-Hasselbalch",
            "Oxidation state", "Redox", "Arrhenius", "Valence"
        )
        SCIENCES_MED -> listOf(
            "Pathophysiology", "Homeostasis", "Glycolysis", "Krebs cycle",
            "Oxidative phosphorylation", "Action potential", "Pharmacokinetics",
            "Pharmacodynamics", "Differential diagnosis", "Etiology", "Prognosis",
            "Contraindication", "Mitosis", "Meiosis", "Transcription", "Translation",
            "Immunoglobulin", "Cytokine"
        )
        HUMANITIES_LAW -> listOf(
            "Estoppel", "Mens rea", "Actus reus", "Stare decisis", "Ratio decidendi",
            "Obiter dicta", "Tort", "Fiduciary duty", "Consideration", "Promissory estoppel",
            "IRAC", "Jurisprudence", "Historiography", "Epistemology", "Hermeneutics",
            "Dialectic", "Hegemony", "Primary source"
        )
        HIGH_SCHOOL -> listOf(
            "IGCSE", "GCSE", "A-Level", "O-Level", "AP Exam", "IB Diploma", "ECZ",
            "Marking scheme", "Command word", "Worked example", "Past paper",
            "Quadratic formula", "Pythagoras", "Photosynthesis", "Osmosis",
            "Periodic table", "Simultaneous equations"
        )
    }

    /** Canonical note template sections for this discipline, pairing AI synthesis with learner workspace. */
    fun templateSections(): List<TemplateSection> = when (this) {
        GENERAL -> listOf(
            TemplateSection("concepts", "Key concepts", SectionSource.AI, NoteBlockType.BULLET, "Core ideas introduced in the session"),
            TemplateSection("definitions", "Definitions", SectionSource.AI, NoteBlockType.BULLET, "Precise terms and meanings"),
            TemplateSection("examples", "Examples & illustrations", SectionSource.AI, NoteBlockType.PARAGRAPH, "Concrete cases discussed"),
            TemplateSection("my_notes", "My notes", SectionSource.USER, NoteBlockType.PARAGRAPH, "Your own takeaways and connections"),
            TemplateSection("questions", "Questions", SectionSource.USER, NoteBlockType.BULLET, "Points to clarify or test yourself on")
        )
        MATH_QUANT -> listOf(
            TemplateSection("definitions_axioms", "Definitions & assumptions", SectionSource.AI, NoteBlockType.BULLET, "Formal definitions, notation, and domain conditions"),
            TemplateSection("theorems", "Theorems & properties", SectionSource.AI, NoteBlockType.PARAGRAPH, "Exact statements and necessary/sufficient conditions"),
            TemplateSection("derivations_proofs", "Step-by-step derivations & proofs", SectionSource.AI, NoteBlockType.NUMBERED, "Ordered algebraic or logical steps"),
            TemplateSection("worked_examples", "Worked examples", SectionSource.AI, NoteBlockType.PARAGRAPH, "Solved quantitative problems from the lecture"),
            TemplateSection("my_notes", "My notes", SectionSource.USER, NoteBlockType.PARAGRAPH, "Intuition, scratch steps, and edge cases"),
            TemplateSection("questions", "Questions", SectionSource.USER, NoteBlockType.BULLET, "Steps or proofs to revisit")
        )
        ENGINEERING -> listOf(
            TemplateSection("physical_model", "Physical model & assumptions", SectionSource.AI, NoteBlockType.PARAGRAPH, "System boundary, idealizations, and operating regime"),
            TemplateSection("variables_units", "Variables & SI units", SectionSource.AI, NoteBlockType.TABLE, "Symbols, physical meanings, and SI units"),
            TemplateSection("governing_equations", "Governing equations", SectionSource.AI, NoteBlockType.BULLET, "Conservation laws and constitutive relations"),
            TemplateSection("given_find_solution", "Given / Find / Solution", SectionSource.AI, NoteBlockType.NUMBERED, "Structured engineering problem walkthrough"),
            TemplateSection("my_notes", "My notes", SectionSource.USER, NoteBlockType.PARAGRAPH, "Design rules of thumb and lab observations"),
            TemplateSection("questions", "Questions", SectionSource.USER, NoteBlockType.BULLET, "Assumptions or derivations to check")
        )
        ACCOUNTING_FINANCE -> listOf(
            TemplateSection("standards_rules", "IFRS / GAAP standards & recognition rules", SectionSource.AI, NoteBlockType.BULLET, "Applicable standards, measurement bases, and thresholds"),
            TemplateSection("journal_entries", "Journal entries (Dr / Cr)", SectionSource.AI, NoteBlockType.TABLE, "Account debits, credits, and narrations"),
            TemplateSection("t_accounts_schedules", "T-accounts & schedules", SectionSource.AI, NoteBlockType.TABLE, "Amortisation, depreciation, or valuation schedules"),
            TemplateSection("worked_adjustments", "Worked adjustments", SectionSource.AI, NoteBlockType.NUMBERED, "Step-by-step calculations and reconciliations"),
            TemplateSection("my_notes", "My notes", SectionSource.USER, NoteBlockType.PARAGRAPH, "Exam traps and standard exceptions"),
            TemplateSection("questions", "Questions", SectionSource.USER, NoteBlockType.BULLET, "Treatments or disclosures to clarify")
        )
        PROGRAMMING_CS -> listOf(
            TemplateSection("problem_specification", "Problem & invariants", SectionSource.AI, NoteBlockType.PARAGRAPH, "Inputs, outputs, constraints, and data structures"),
            TemplateSection("algorithm_code", "Algorithm & code", SectionSource.AI, NoteBlockType.CODE, "Clean implementation or pseudocode"),
            TemplateSection("trace_table", "Dry-run trace table", SectionSource.AI, NoteBlockType.TABLE, "State evolution across sample input"),
            TemplateSection("complexity_analysis", "Time & space complexity", SectionSource.AI, NoteBlockType.BULLET, "Big-O bounds, best/average/worst cases, and trade-offs"),
            TemplateSection("my_notes", "My notes", SectionSource.USER, NoteBlockType.PARAGRAPH, "Edge cases, bugs to avoid, and alternative patterns"),
            TemplateSection("questions", "Questions", SectionSource.USER, NoteBlockType.BULLET, "Invariants or bounds to verify")
        )
        ECONOMICS -> listOf(
            TemplateSection("economic_model", "Model & assumptions", SectionSource.AI, NoteBlockType.PARAGRAPH, "Agents, market structure, and ceteris paribus assumptions"),
            TemplateSection("equilibrium_equations", "Equilibrium & elasticity equations", SectionSource.AI, NoteBlockType.BULLET, "Optimality conditions, elasticities, and identities"),
            TemplateSection("curve_shifts", "Comparative statics & shifts", SectionSource.AI, NoteBlockType.NUMBERED, "Shock -> curve shift -> new equilibrium mechanism"),
            TemplateSection("policy_tradeoffs", "Welfare & policy trade-offs", SectionSource.AI, NoteBlockType.BULLET, "Efficiency, incidence, deadweight loss, and distributional effects"),
            TemplateSection("my_notes", "My notes", SectionSource.USER, NoteBlockType.PARAGRAPH, "Diagram sketches and real-world intuition"),
            TemplateSection("questions", "Questions", SectionSource.USER, NoteBlockType.BULLET, "Mechanisms or graph shifts to review")
        )
        CHEMISTRY -> listOf(
            TemplateSection("chemical_principles", "Core principles & trends", SectionSource.AI, NoteBlockType.BULLET, "Bonding, periodicity, or kinetic principles"),
            TemplateSection("reactions_equations", "Balanced reactions & equations", SectionSource.AI, NoteBlockType.BULLET, "Reactants, products, states, and conditions"),
            TemplateSection("mechanisms_stoichiometry", "Mechanisms & stoichiometry", SectionSource.AI, NoteBlockType.NUMBERED, "Electron movement, limiting reagents, and molar calculations"),
            TemplateSection("equilibrium_thermo", "Equilibrium & thermochemistry", SectionSource.AI, NoteBlockType.PARAGRAPH, "ΔH, ΔG, Keq, and Le Chatelier shifts"),
            TemplateSection("my_notes", "My notes", SectionSource.USER, NoteBlockType.PARAGRAPH, "Lab observations, colour changes, and exceptions"),
            TemplateSection("questions", "Questions", SectionSource.USER, NoteBlockType.BULLET, "Mechanisms or calculations to double-check")
        )
        SCIENCES_MED -> listOf(
            TemplateSection("mechanisms_pathways", "Pathways & physiological mechanisms", SectionSource.AI, NoteBlockType.NUMBERED, "Ordered biological or pathophysiological cascade"),
            TemplateSection("differential_comparison", "Differential & comparison table", SectionSource.AI, NoteBlockType.TABLE, " Distinguishing features, findings, and treatments".trim()),
            TemplateSection("clinical_pearls", "High-yield & clinical pearls", SectionSource.AI, NoteBlockType.BULLET, "Classic presentations, red flags, and contraindications"),
            TemplateSection("my_notes", "My notes", SectionSource.USER, NoteBlockType.PARAGRAPH, "Mnemonics and ward/lab connections"),
            TemplateSection("questions", "Questions", SectionSource.USER, NoteBlockType.BULLET, "Pathways or differentials to review")
        )
        HUMANITIES_LAW -> listOf(
            TemplateSection("issue_rule", "Issue, rule & historical context", SectionSource.AI, NoteBlockType.PARAGRAPH, "Core legal question, governing rule, or thesis"),
            TemplateSection("arguments_analysis", "Primary arguments & IRAC analysis", SectionSource.AI, NoteBlockType.NUMBERED, "Application of rule/theory to facts and counter-arguments"),
            TemplateSection("evidence_authorities", "Authorities & primary quotes", SectionSource.AI, NoteBlockType.QUOTE, "Key cases, statutes, or primary text excerpts"),
            TemplateSection("critique_holding", "Holding, synthesis & critique", SectionSource.AI, NoteBlockType.PARAGRAPH, "Ratio decidendi, scholarly critique, and broader significance"),
            TemplateSection("my_notes", "My notes", SectionSource.USER, NoteBlockType.PARAGRAPH, "Essay angles and seminar reflections"),
            TemplateSection("questions", "Questions", SectionSource.USER, NoteBlockType.BULLET, "Counter-arguments or authorities to check")
        )
        HIGH_SCHOOL -> listOf(
            TemplateSection("learning_objectives", "Learning objectives", SectionSource.AI, NoteBlockType.BULLET, "What the syllabus expects you to know and do"),
            TemplateSection("lesson_steps", "Clear lesson breakdown", SectionSource.AI, NoteBlockType.NUMBERED, "Step-by-step explanation in plain language"),
            TemplateSection("worked_examples", "Worked examples", SectionSource.AI, NoteBlockType.PARAGRAPH, "Method marks shown step by step"),
            TemplateSection("exam_tips", "Past-paper tips & common mistakes", SectionSource.AI, NoteBlockType.BULLET, "Command words and marking-scheme pointers"),
            TemplateSection("my_notes", "My notes", SectionSource.USER, NoteBlockType.PARAGRAPH, "Summary in your own words"),
            TemplateSection("questions", "Questions", SectionSource.USER, NoteBlockType.BULLET, "Past-paper drills or teacher questions")
        )
    }

    /** Discipline-specific active recall and exam practice question formats. */
    fun practiceArchetypes(): List<String> = when (this) {
        GENERAL -> listOf(
            "Define & contrast core concepts",
            "Explain in your own words (Teach-back)",
            "Apply concept to an unseen scenario"
        )
        MATH_QUANT -> listOf(
            "State theorem & required hypotheses",
            "Step-by-step quantitative worked problem",
            "Spot the flawed proof step",
            "Derive formula from first principles"
        )
        ENGINEERING -> listOf(
            "Given / Find / Solution numerical problem",
            "SI unit & dimensional consistency check",
            "Predict system response when a parameter doubles",
            "State physical assumptions of governing equation"
        )
        ACCOUNTING_FINANCE -> listOf(
            "Prepare Dr / Cr journal entry",
            "Compute carrying amount / amortisation schedule",
            "Identify IFRS / GAAP recognition treatment",
            "Financial ratio & cash-flow impact analysis"
        )
        PROGRAMMING_CS -> listOf(
            "Dry-run trace table on edge-case input",
            "Time & space Big-O complexity derivation",
            "Identify & fix subtle off-by-one / concurrency bug",
            "Select optimal data structure with trade-offs"
        )
        ECONOMICS -> listOf(
            "Shock -> curve shift -> new equilibrium walkthrough",
            "Elasticity / marginal optimality calculation",
            "Welfare & deadweight loss incidence analysis",
            "Evaluate policy trade-offs & unintended effects"
        )
        CHEMISTRY -> listOf(
            "Balance equation & limiting-reagent stoichiometry",
            "Predict equilibrium shift (Le Chatelier)",
            "Reaction mechanism & nucleophile/electrophile role",
            "Thermochemistry / pH buffer calculation"
        )
        SCIENCES_MED -> listOf(
            "Clinical vignette differential diagnosis",
            "Trace multi-step physiological / metabolic pathway",
            "Mechanism of action & adverse effect match",
            "Interpret lab / physiological perturbation"
        )
        HUMANITIES_LAW -> listOf(
            "IRAC issue-spotting hypothetical",
            "Distinguish precedent / ratio decidendi",
            "Construct thesis & steelman counter-argument",
            "Evaluate primary source evidence"
        )
        HIGH_SCHOOL -> listOf(
            "Structured past-paper calculation (show working)",
            "Command-word short answer (State / Describe / Explain)",
            "Multiple-choice quick diagnostic",
            "Teach it back in simple steps"
        )
    }

    companion object {
        /** Forgiving lookup by enum name, short label, or display name; defaults to [GENERAL]. */
        fun fromName(raw: String?): SubjectDomain {
            if (raw.isNullOrBlank()) return GENERAL
            val cleaned = raw.trim()
            entries.firstOrNull {
                it.name.equals(cleaned, ignoreCase = true) ||
                    it.shortLabel.equals(cleaned, ignoreCase = true) ||
                    it.displayName.equals(cleaned, ignoreCase = true)
            }?.let { return it }

            val normalizedEnum = cleaned.uppercase()
                .replace(Regex("[^A-Z0-9]+"), "_")
                .trim('_')
            entries.firstOrNull { it.name == normalizedEnum }?.let { return it }

            return when (normalizedEnum) {
                "MATH", "MATHEMATICS", "STATISTICS", "STATS", "CALCULUS", "ALGEBRA" -> MATH_QUANT
                "PHYSICS", "APPLIED_PHYSICS", "MECHANICS", "CIRCUITS" -> ENGINEERING
                "ACCOUNTING", "FINANCE", "ACC", "FINANCIAL_ACCOUNTING" -> ACCOUNTING_FINANCE
                "CS", "CODE", "CODING", "COMPUTER_SCIENCE", "PROGRAMMING", "SOFTWARE" -> PROGRAMMING_CS
                "ECON", "MACROECONOMICS", "MICROECONOMICS" -> ECONOMICS
                "CHEM", "ORGANIC_CHEMISTRY", "INORGANIC_CHEMISTRY" -> CHEMISTRY
                "BIOLOGY", "BIO", "MEDICINE", "MEDICAL", "ANATOMY", "PHYSIOLOGY", "PHARMACOLOGY" -> SCIENCES_MED
                "LAW", "HUMANITIES", "HISTORY", "LITERATURE", "PHILOSOPHY", "ARTS", "BUSINESS_LAW" -> HUMANITIES_LAW
                "HIGHSCHOOL", "EXAM_PREP", "IGCSE", "GCSE", "A_LEVEL", "ECZ" -> HIGH_SCHOOL
                else -> GENERAL
            }
        }

        private data class KeywordRule(val pattern: Regex, val weight: Int)

        private fun word(term: String, weight: Int = 2): KeywordRule =
            KeywordRule(Regex("\\b${Regex.escape(term)}\\b", RegexOption.IGNORE_CASE), weight)

        private val DOMAIN_RULES: Map<SubjectDomain, List<KeywordRule>> = mapOf(
            HIGH_SCHOOL to listOf(
                word("igcse", 6), word("gcse", 6), word("a-level", 6), word("a level", 6),
                word("o-level", 6), word("ecz", 6), word("ap exam", 5), word("ib diploma", 5),
                word("high school", 5), word("grade 9", 5), word("grade 10", 5),
                word("grade 11", 5), word("grade 12", 5), word("form 3", 5), word("form 4", 5),
                word("form 5", 5), word("past paper", 4), word("marking scheme", 4),
                word("command word", 3), word("exam prep", 3)
            ),
            MATH_QUANT to listOf(
                word("calculus", 4), word("linear algebra", 5), word("real analysis", 5),
                word("eigenvalue", 5), word("eigenvalues", 5), word("eigenvector", 5),
                word("jacobian", 5), word("hessian", 5), word("theorem", 3), word("lemma", 4),
                word("corollary", 4), word("proof", 3), word("integral", 3), word("derivative", 3),
                word("partial derivative", 4), word("matrix", 3), word("matrices", 3),
                word("determinant", 4), word("probability", 3), word("statistics", 3),
                word("covariance", 4), word("variance", 3), word("bayes", 4),
                word("hypothesis test", 4), word("p-value", 4), word("taylor series", 5),
                word("differential equation", 4), word("trigonometry", 4)
            ),
            ENGINEERING to listOf(
                word("kirchhoff", 5), word("thevenin", 5), word("norton", 5),
                word("thermodynamics", 5), word("fluid mechanics", 5), word("statics", 4),
                word("dynamics", 3), word("solid mechanics", 5), word("young's modulus", 5),
                word("stress tensor", 5), word("free body diagram", 5), word("reynolds number", 5),
                word("bernoulli", 4), word("navier-stokes", 5), word("laplace transform", 4),
                word("fourier transform", 4), word("transfer function", 5), word("bode plot", 5),
                word("pid controller", 5), word("impedance", 4), word("circuit", 4),
                word("circuits", 4), word("voltage", 3), word("ohm's law", 5),
                word("torque", 4), word("heat transfer", 5), word("si units", 3),
                word("engineering", 4), word("applied physics", 4)
            ),
            ACCOUNTING_FINANCE to listOf(
                word("ifrs", 5), word("gaap", 5), word("ias", 4), word("journal entry", 5),
                word("journal entries", 5), word("t-account", 5), word("t-accounts", 5),
                word("trial balance", 5), word("balance sheet", 4), word("income statement", 4),
                word("cash flow", 3), word("debit", 3), word("credit", 3),
                word("amortisation", 4), word("amortization", 4), word("depreciation", 4),
                word("accrual", 4), word("accruals", 4), word("deferred revenue", 5),
                word("retained earnings", 5), word("goodwill", 4), word("impairment", 4),
                word("wacc", 5), word("ebitda", 5), word("net present value", 4),
                word("npv", 4), word("accounting", 4), word("corporate finance", 5),
                word("general ledger", 5)
            ),
            PROGRAMMING_CS to listOf(
                word("algorithm", 4), word("algorithms", 4), word("data structure", 5),
                word("data structures", 5), word("big-o", 5), word("big o", 5),
                word("time complexity", 5), word("space complexity", 5), word("dijkstra", 5),
                word("memoization", 5), word("dynamic programming", 5), word("recursion", 4),
                word("binary search", 5), word("hash table", 5), word("linked list", 5),
                word("graph traversal", 5), word("concurrency", 4), word("deadlock", 5),
                word("mutex", 5), word("semaphore", 5), word("compiler", 4),
                word("operating system", 4), word("sql", 3), word("python", 3),
                word("kotlin", 3), word("java", 3), word("javascript", 3),
                word("computer science", 5), word("programming", 4), word("pseudocode", 4)
            ),
            ECONOMICS to listOf(
                word("microeconomics", 5), word("macroeconomics", 5), word("economics", 4),
                word("elasticity", 5), word("price elasticity", 5), word("pareto", 5),
                word("nash equilibrium", 5), word("marginal utility", 5), word("marginal cost", 5),
                word("marginal revenue", 5), word("deadweight loss", 5), word("consumer surplus", 5),
                word("producer surplus", 5), word("supply and demand", 5), word("law of demand", 5),
                word("is-lm", 5), word("phillips curve", 5), word("comparative advantage", 5),
                word("oligopoly", 5), word("monopoly", 4), word("ceteris paribus", 5),
                word("monetary policy", 4), word("fiscal policy", 4), word("gdp", 3),
                word("inflation", 3), word("keynesian", 5)
            ),
            CHEMISTRY to listOf(
                word("stoichiometry", 5), word("le chatelier", 5), word("chemistry", 4),
                word("organic chemistry", 5), word("inorganic chemistry", 5),
                word("molarity", 5), word("titration", 5), word("enthalpy", 4),
                word("gibbs free energy", 5), word("electronegativity", 5),
                word("hybridization", 5), word("nucleophile", 5), word("electrophile", 5),
                word("sn1", 5), word("sn2", 5), word("covalent", 4), word("ionic bond", 4),
                word("oxidation state", 5), word("redox", 5), word("henderson-hasselbalch", 5),
                word("buffer solution", 5), word("chemical equilibrium", 5),
                word("periodic table", 3), word("valence", 4), word("thermochemistry", 5)
            ),
            SCIENCES_MED to listOf(
                word("pathophysiology", 5), word("pharmacology", 5), word("pharmacokinetics", 5),
                word("pharmacodynamics", 5), word("anatomy", 4), word("physiology", 4),
                word("glycolysis", 5), word("krebs cycle", 5), word("citric acid cycle", 5),
                word("oxidative phosphorylation", 5), word("homeostasis", 4),
                word("action potential", 5), word("differential diagnosis", 5),
                word("clinical pearl", 5), word("clinical", 3), word("patient", 3),
                word("contraindication", 5), word("etiology", 5), word("prognosis", 4),
                word("mitosis", 4), word("meiosis", 4), word("immunoglobulin", 5),
                word("cytokine", 5), word("biology", 4), word("cellular respiration", 5),
                word("cardiovascular", 4), word("neurology", 4)
            ),
            HUMANITIES_LAW to listOf(
                word("irac", 5), word("mens rea", 5), word("actus reus", 5),
                word("stare decisis", 5), word("ratio decidendi", 5), word("obiter dicta", 5),
                word("estoppel", 5), word("promissory estoppel", 5), word("tort law", 5),
                word("contract law", 5), word("constitutional law", 5), word("criminal law", 5),
                word("fiduciary duty", 5), word("plaintiff", 4), word("defendant", 4),
                word("jurisprudence", 5), word("case brief", 5), word("precedent", 4),
                word("historiography", 5), word("epistemology", 5), word("hermeneutics", 5),
                word("primary source", 4), word("literary critique", 5), word("humanities", 4),
                word("philosophy", 4), word("world history", 4)
            )
        )

        /**
         * Classifies a lecture/study note into the best-matching [SubjectDomain] using weighted
         * keyword and phrase evidence across [title] (weighted 3x) and [sampleText] (1x).
         */
        fun detect(title: String, sampleText: String = ""): SubjectDomain {
            if (title.isBlank() && sampleText.isBlank()) return GENERAL

            var bestDomain = GENERAL
            var bestScore = 0

            for ((domain, rules) in DOMAIN_RULES) {
                var score = 0
                for (rule in rules) {
                    if (title.isNotBlank() && rule.pattern.containsMatchIn(title)) {
                        score += rule.weight * 3
                    }
                    if (sampleText.isNotBlank() && rule.pattern.containsMatchIn(sampleText)) {
                        score += rule.weight
                    }
                }
                if (score > bestScore) {
                    bestScore = score
                    bestDomain = domain
                }
            }

            // Require at least one meaningful domain match before moving off GENERAL.
            return if (bestScore >= 3) bestDomain else GENERAL
        }
    }
}

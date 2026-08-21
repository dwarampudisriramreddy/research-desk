package com.ram.researchdesk

import android.util.Log
import com.ram.researchdesk.Paper

private val STOP_WORDS = setOf(
    "the", "and", "for", "are", "but", "not", "you", "all", "can", "had",
    "her", "was", "one", "our", "out", "has", "his", "how", "its", "may",
    "new", "now", "old", "see", "way", "who", "did", "get", "got", "let",
    "say", "she", "too", "use", "with", "that", "this", "will", "each",
    "make", "like", "long", "look", "many", "some", "than", "them", "then",
    "these", "from", "have", "been", "said", "more", "when", "what", "your",
    "which", "their", "about", "would", "there", "could", "other", "into",
    "just", "also", "after", "study", "studies", "used", "using", "between",
    "among", "based", "compared", "association", "associated", "cross",
    "sectional", "observational", "objective", "aim", "purpose", "background",
    "conclusion", "conclusions", "methods", "results",
    "doi", "http", "https", "org", "com", "edu", "gov", "article",
)

fun blob(a: Paper): String =
    "${a.title} ${a.abstract ?: ""} ${a.journal ?: ""}".lowercase()

private fun hash(s: String): String {
    var h = 0
    for (ch in s) {
        h = 0x1FFFFFFF and (31 * h + ch.code)
    }
    return h.toString(36)
}

fun extractTerms(text: String): List<String> {
    val words = text.lowercase()
        .replace(Regex("""[^a-z0-9\s-]"""), " ")
        .split(Regex("""\s+"""))
        .filter { it.length > 2 && it !in STOP_WORDS }

    val bigrams = mutableListOf<String>()
    for (i in 0 until words.size - 1) {
        val b = "${words[i]} ${words[i + 1]}"
        if (words[i] !in STOP_WORDS && words[i + 1] !in STOP_WORDS) {
            bigrams.add(b)
        }
    }

    val freq = linkedMapOf<String, Int>()
    for (w in words) {
        freq[w] = (freq[w] ?: 0) + 1
    }
    for (b in bigrams) {
        freq[b] = (freq[b] ?: 0) + 2
    }

    return freq.entries.sortedByDescending { it.value }.take(8).map { it.key }
}

data class Cluster(
    val id: String,
    val name: String,
    val paperIds: List<String>,
    val evidence: String,
    val potentialGaps: List<String> = emptyList(),
    val limitations: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
)

fun strengthOf(papers: List<Paper>): String {
    if (papers.isEmpty()) return "uncertain"
    val reviews = papers.count { p ->
        Regex("""review|meta""").containsMatchIn("${p.studyDesign ?: ""} ${p.articleType ?: ""}")
    }
    if (reviews >= 2 && papers.size >= 6) return "high"
    if (papers.size >= 8) return "moderate"
    if (papers.size >= 4) return "moderate"
    if (papers.size >= 2) return "limited"
    return "emerging"
}

fun clusterPapers(subject: Subject, papers: List<Paper>): List<Cluster> {
    Log.d("CLUSTER", "Clustering ${papers.size} papers for ${subject.name}")
    if (papers.isEmpty()) return emptyList()

    val paperTerms = papers.map { extractTerms(blob(it)) }

    val termPaperMap = linkedMapOf<String, MutableSet<Int>>()
    for (i in papers.indices) {
        for (t in paperTerms[i]) {
            termPaperMap.getOrPut(t) { mutableSetOf() }.add(i)
        }
    }

    val assigned = mutableSetOf<Int>()
    val clusters = mutableListOf<Cluster>()

    val sortedTerms = termPaperMap.entries.sortedByDescending { it.value.size }

    for (entry in sortedTerms) {
        if (entry.value.size < 2) continue
        val members = entry.value.filterNot { it in assigned }.toSet()
        if (members.size < 2) continue

        assigned.addAll(members)

        val memberPapers = members.map { papers[it] }
        val gaps = mutableListOf<String>()
        if (memberPapers.none { it.mentionsIndia }) {
            gaps.add("This topic is described internationally but Indian samples are thin in this retrieval.")
        }
        if (memberPapers.size < 3) {
            gaps.add("Few recent papers clearly about \"${entry.key}\".")
        }

        val clusterKeywords = mutableSetOf(entry.key)
        for (m in members) {
            clusterKeywords.addAll(paperTerms[m].take(3))
        }

        clusters.add(
            Cluster(
                id = "auto-${hash(entry.key)}",
                name = entry.key,
                paperIds = memberPapers.map { it.id ?: "" }.filter { it.isNotEmpty() },
                evidence = strengthOf(memberPapers),
                potentialGaps = gaps,
                limitations = if (memberPapers.any { it.abstract == null })
                    listOf("Abstracts missing for some records — findings cannot be fully extracted.")
                else
                    emptyList(),
                keywords = clusterKeywords.take(6).toList(),
            )
        )

        if (clusters.size >= 12) break
    }

    val leftover = papers.filterIndexed { i, _ -> i !in assigned }
    if (leftover.isNotEmpty()) {
        clusters.add(
            Cluster(
                id = "other",
                name = "Other retrieved work",
                paperIds = leftover.mapNotNull { it.id },
                evidence = strengthOf(leftover),
            )
        )
    }

    Log.d("CLUSTER", "Created ${clusters.size} clusters: ${clusters.joinToString(", ") { it.name }}")
    return clusters
}

fun countByDesign(papers: List<Paper>): Map<String, Int> {
    val counts = linkedMapOf<String, Int>()
    for (p in papers) {
        val design = p.studyDesign ?: "unclear"
        counts[design] = (counts[design] ?: 0) + 1
    }
    return counts
}

fun countByCountry(papers: List<Paper>): Map<String, Int> {
    val counts = linkedMapOf<String, Int>()
    for (p in papers) {
        if (p.countryCodes.isEmpty()) {
            counts["unknown"] = (counts["unknown"] ?: 0) + 1
        } else {
            for (c in p.countryCodes) {
                counts[c] = (counts[c] ?: 0) + 1
            }
        }
    }
    return counts
}

fun countByYear(papers: List<Paper>): Map<Int, Int> {
    val counts = linkedMapOf<Int, Int>()
    for (p in papers) {
        val y = p.year
        if (y != null) {
            counts[y] = (counts[y] ?: 0) + 1
        }
    }
    return counts
}

fun hasObjectiveMeasure(p: Paper): Boolean {
    val text = "${p.title} ${p.abstract ?: ""}".lowercase()
    return Regex(
        """caliper|probe|meter|sensor|measurement|index|score|scale|""" +
            """imagej|photograph|cast|cast|radiograph|opg|bitewing|""" +
            """surface roughness|ph|flow rate|buffering|""" +
            """blood pressure|pulse|oximeter|spirometry"""
    ).containsMatchIn(text)
}

fun hasSubjectiveOnly(p: Paper): Boolean {
    val text = "${p.title} ${p.abstract ?: ""}".lowercase()
    val hasObjective = hasObjectiveMeasure(p)
    val hasSurvey = Regex("""questionnaire|survey|kappa|likert|self-report""").containsMatchIn(text)
    return hasSurvey && !hasObjective
}

fun extractMethods(p: Paper): List<String> {
    val text = "${p.title} ${p.abstract ?: ""}".lowercase()
    val methods = mutableListOf<String>()
    if (Regex("""questionnaire|survey""").containsMatchIn(text)) methods.add("questionnaire")
    if (Regex("""caliper|ruler|measurement""").containsMatchIn(text)) methods.add("clinical measurement")
    if (Regex("""photograph|imagej|digital""").containsMatchIn(text)) methods.add("digital imaging")
    if (Regex("""pH|strip|biosensor""").containsMatchIn(text)) methods.add("chairside test")
    if (Regex("""cast|impression""").containsMatchIn(text)) methods.add("cast analysis")
    if (Regex("""radiograph|opg|x-ray""").containsMatchIn(text)) methods.add("radiographic")
    if (Regex("""blood|serum|saliva""").containsMatchIn(text)) methods.add("biomarker")
    if (methods.isEmpty()) methods.add("unspecified")
    return methods
}

data class Gap(
    val id: String,
    val statement: String,
    val confidence: String,
    val novelty: String,
    val why: String,
    val unknown: String,
    val candidateQuestion: String,
    val ugFeasible: Boolean = true,
    val feasibilityNote: String = "",
    val paperIds: List<String> = emptyList(),
    val category: String = "",
)

fun buildGaps(subject: Subject, papers: List<Paper>, clusters: List<Cluster>): List<Gap> {
    Log.d("GAPS", "Building gaps: ${papers.size} papers, ${clusters.size} clusters (rule-based)")
    if (papers.isEmpty()) return emptyList()
    val gaps = mutableListOf<Gap>()

    val india = papers.filter { it.mentionsIndia }
    val countries = countByCountry(papers)

    if (papers.isNotEmpty() && india.isEmpty()) {
        gaps.add(
            Gap(
                id = "gap-pop-${hash(subject.id)}",
                statement = "Among ${papers.size} retrieved papers, none clearly report an Indian sample. " +
                    "The evidence base is entirely international.",
                confidence = if (papers.size >= 8) "moderate" else "possible",
                novelty = "underexplored-in-india",
                why = "Local replication with a measurable clinical outcome is justified. " +
                    "This is not a claim of global novelty — it fills a population-specific evidence gap.",
                unknown = "Whether the same exposure–outcome relationship holds among the local patient population.",
                candidateQuestion = "Does the relationship described in the retrieved international papers hold among " +
                    "adults attending a dental college locally?",
                ugFeasible = true,
                feasibilityNote = "Cross-sectional clinical measurement plus a short exposure interview can finish in 8–10 weeks.",
                paperIds = papers.take(8).map { it.id ?: "" },
                category = "population",
            )
        )
    } else if (india.isNotEmpty()) {
        val local = india.filter { p ->
            Regex(
                """andhra|godavari|kakinada|rajahmundry|visakhapatnam|vijayawada""",
                setOf(RegexOption.IGNORE_CASE)
            ).containsMatchIn(blob(p))
        }
        if (local.isEmpty() && india.size >= 2) {
            gaps.add(
                Gap(
                    id = "gap-local-${hash(subject.id)}",
                    statement = "Indian data exist (${india.size} paper${if (india.size == 1) "" else "s"}), " +
                        "but no study from Andhra Pradesh or East Godavari was identified.",
                    confidence = "moderate",
                    novelty = "potentially-novel-locally",
                    why = "A local extension is justified to test whether findings from other Indian states " +
                        "generalize to the coastal Andhra setting.",
                    unknown = "Whether findings from other Indian states generalize to the local population.",
                    candidateQuestion = "A local extension among routine patients or students, using the same primary outcome " +
                        "as the strongest retrieved Indian paper.",
                    ugFeasible = true,
                    feasibilityNote = "Recruitment from the college OPD or class is realistic within 12 weeks.",
                    paperIds = india.take(8).map { it.id ?: "" },
                    category = "population",
                )
            )
        }
    }

    val designCounts = countByDesign(papers)
    val surveyCount = (designCounts["survey"] ?: 0) + (designCounts["questionnaire"] ?: 0)

    if (papers.size >= 4 && surveyCount >= papers.size * 0.5) {
        gaps.add(
            Gap(
                id = "gap-method-${hash("survey")}",
                statement = "$surveyCount of ${papers.size} papers rely on questionnaires or surveys. " +
                    "Objective clinical or physiologic measurements are largely absent.",
                confidence = "moderate",
                novelty = "methodological-extension",
                why = "An undergraduate project can add a chairside measurement (e.g., caliper, pH probe, " +
                    "photograph + ImageJ) to the same exposure to strengthen the evidence base.",
                unknown = "Whether an objective measure changes the association observed in survey-only studies.",
                candidateQuestion = "Is the self-reported measure consistent with an objective clinical measurement " +
                    "in the same participants?",
                ugFeasible = true,
                feasibilityNote = "Add one objective measurement to an existing questionnaire-based design. " +
                    "Calibrate on 10 participants first.",
                paperIds = papers.filter { hasSubjectiveOnly(it) }.take(6).map { it.id ?: "" },
                category = "method",
            )
        )
    }

    val objectiveCount = papers.count { hasObjectiveMeasure(it) }
    if (papers.size >= 4 && objectiveCount < papers.size * 0.25) {
        gaps.add(
            Gap(
                id = "gap-obj-${hash(subject.id)}",
                statement = "Only $objectiveCount of ${papers.size} papers include an objective clinical or " +
                    "instrument-based measurement. Most evidence is opinion- or recall-based.",
                confidence = if (papers.size >= 6) "moderate" else "possible",
                novelty = "instrumental-validation",
                why = "A study using an objective instrument (caliper, pH meter, ImageJ, probe) would add " +
                    "methodological rigor to a field dominated by self-report.",
                unknown = "Whether instrument-based measurement changes the direction or strength of the reported association.",
                candidateQuestion = "Does instrument-based measurement of the primary outcome agree with or improve upon " +
                    "the self-report measure used in prior studies?",
                ugFeasible = true,
                feasibilityNote = "Use departmental instruments. Calibrate on 10 units. Finishable in 8–10 weeks.",
                paperIds = papers.take(6).map { it.id ?: "" },
                category = "method",
            )
        )
    }

    val reviewCount = designCounts.entries
        .filter { Regex("""review|meta""").containsMatchIn(it.key) }
        .sumOf { it.value }
    if (papers.size >= 10 && reviewCount == 0) {
        gaps.add(
            Gap(
                id = "gap-review-${hash(subject.id)}",
                statement = "${papers.size} primary studies were retrieved but no systematic review or meta-analysis. " +
                    "The field lacks synthesis.",
                confidence = "moderate",
                novelty = "synthesis-needed",
                why = "A structured mini-review or scoping review using a documented search strategy " +
                    "could consolidate findings and identify contradictions.",
                unknown = "Whether a formal synthesis changes the conclusions drawn from individual studies.",
                candidateQuestion = "What is the overall direction and consistency of evidence on this topic, " +
                    "and where do studies disagree?",
                ugFeasible = true,
                feasibilityNote = "A scoping or narrative review is realistic in 6–8 weeks. " +
                    "Document the search strategy and inclusion criteria.",
                paperIds = papers.take(10).map { it.id ?: "" },
                category = "evidence",
            )
        )
    }

    val yearCounts = countByYear(papers)
    val years = yearCounts.keys.sorted()
    if (years.size >= 3) {
        val recent = yearCounts[years.last()] ?: 0
        val oldest = yearCounts[years.first()] ?: 0
        if (oldest > recent * 2 && papers.size >= 6) {
            gaps.add(
                Gap(
                    id = "gap-temporal-${hash(subject.id)}",
                    statement = "Publication activity appears to decline in recent years " +
                        "($oldest papers in ${years.first()} vs $recent in ${years.last()}). " +
                        "The evidence may be becoming outdated.",
                    confidence = "possible",
                    novelty = "temporal-renewal",
                    why = "A fresh local study using current methods could update the evidence base " +
                        "for a topic that was more active in the past.",
                    unknown = "Whether the decline reflects saturation, shifted interest, or methodological barriers.",
                    candidateQuestion = "Can the same research question be addressed with updated methods or a local population " +
                        "to contribute to a declining evidence base?",
                    ugFeasible = true,
                    feasibilityNote = "Replicate a high-citation older study with current methods and local participants.",
                    paperIds = papers.filter { it.year == years.first() }.take(5).map { it.id ?: "" },
                    category = "temporal",
                )
            )
        }
    }

    if (countries.size <= 2 && papers.size >= 6) {
        val topCountry = countries.entries.sortedByDescending { it.value }
        val dominant = topCountry.first()
        if (dominant.value >= papers.size * 0.6) {
            gaps.add(
                Gap(
                    id = "gap-geo-${hash(subject.id)}",
                    statement = "${dominant.value} of ${papers.size} papers originate from ${dominant.key}. " +
                        "The evidence lacks geographic diversity.",
                    confidence = "moderate",
                    novelty = "cross-cultural-validation",
                    why = "Cross-cultural or multi-site validation is needed. A local study adds diversity " +
                        "to a geographically concentrated evidence base.",
                    unknown = "Whether findings from ${dominant.key} generalize to other settings and populations.",
                    candidateQuestion = "Can the findings from ${dominant.key}-based studies be replicated in a local population?",
                    ugFeasible = true,
                    feasibilityNote = "Simple cross-sectional replication with local participants. " +
                        "Use the same measurement protocol as the dominant-country studies.",
                    paperIds = papers.take(6).map { it.id ?: "" },
                    category = "geographic",
                )
            )
        }
    }

    for (c in clusters.filter { it.id != "other" }) {
        val subset = papers.filter { c.paperIds.contains(it.id) }

        if (subset.isEmpty()) {
            gaps.add(
                Gap(
                    id = "gap-cluster-${hash(c.name)}",
                    statement = "This search did not retrieve recent papers clearly about \"${c.name}\". " +
                        "That is a search finding, not proof the topic is unstudied.",
                    confidence = "possible",
                    novelty = "search-finding",
                    why = "A narrower or synonym-expanded search is required before calling this a true gap.",
                    unknown = "Whether ${c.name} has been studied under different keywords or in different databases.",
                    candidateQuestion = "After a second search with expanded synonyms, is there a measurable outcome " +
                        "related to ${c.name} that can be studied locally?",
                    ugFeasible = true,
                    feasibilityNote = "Re-run search with cluster keywords before locking a protocol.",
                    paperIds = emptyList(),
                    category = "cluster",
                )
            )
        }

        if (subset.size >= 2) {
            val methods = subset.flatMap { extractMethods(it) }
            val methodCounts = linkedMapOf<String, Int>()
            for (m in methods) {
                methodCounts[m] = (methodCounts[m] ?: 0) + 1
            }
            val dominantMethod = methodCounts.entries.sortedByDescending { it.value }
            if (dominantMethod.isNotEmpty() &&
                dominantMethod.first().value >= subset.size * 0.7
            ) {
                gaps.add(
                    Gap(
                        id = "gap-diversity-${hash(c.name)}",
                        statement = "Papers on \"${c.name}\" predominantly use ${dominantMethod.first().key} " +
                            "(${dominantMethod.first().value}/${subset.size}). " +
                            "Methodological diversity is limited.",
                        confidence = "moderate",
                        novelty = "method-diversification",
                        why = "Applying a different measurement approach to the same research question " +
                            "can reveal whether findings are method-dependent.",
                        unknown = "Whether the dominant method is the best measure or whether alternatives " +
                            "yield different results.",
                        candidateQuestion = "Does an alternative measurement approach for ${c.name} yield " +
                            "results consistent with the dominant method?",
                        ugFeasible = true,
                        feasibilityNote = "Use a departmental alternative (e.g., digital vs manual, " +
                            "sensor vs strip) on the same participants.",
                        paperIds = subset.map { it.id ?: "" },
                        category = "method",
                    )
                )
            }
        }
    }

    val result = gaps.take(10)
    Log.d(
        "GAPS",
        "Built ${result.size} gaps: ${
            result.joinToString("; ") { g ->
                "[${g.category}] ${g.statement.substring(0, g.statement.length.coerceIn(0, 50))}..."
            }
        }"
    )
    return result
}

data class EthicsFlag(
    val label: String,
    val realistic: Boolean,
    val concern: String,
)

data class Project(
    val id: String,
    val title: String,
    val domain: String,
    val researchQuestion: String,
    val hypothesis: String,
    val evidenceBasis: String,
    val gap: String,
    val whyDifferent: String,
    val curriculumConnection: String,
    val studyDesign: String,
    val setting: String,
    val population: String,
    val sampleSizeApproach: String,
    val primaryOutcome: String,
    val secondaryOutcomes: List<String> = emptyList(),
    val dataCollection: String,
    val statistics: String,
    val costInr: String,
    val durationWeeks: Int = 10,
    val ethics: String,
    val ethicsFlags: List<EthicsFlag> = emptyList(),
    val limitations: List<String> = emptyList(),
    val publicationPotential: String = "",
    val keywords: List<String> = emptyList(),
    val supportingPaperIds: List<String> = emptyList(),
    val similarity: String = "",
    val scores: Map<String, Any> = emptyMap(),
    val totalScore: Int = 0,
)

fun defaultPopulation(subject: Subject): String {
    return "adults attending the dental college OPD in East Godavari"
}

fun estimateCost(category: String, methods: List<String>): String {
    val joined = methods.joinToString(" ").lowercase()
    if (category == "method" || Regex("""print|resin|scanner|cad|cam""").containsMatchIn(joined)) {
        return "₹0–₹3,000 (existing printer/scanner only)"
    }
    if (category == "evidence") {
        return "₹0 (literature-based only)"
    }
    if (Regex("""pH|strip|disclosing|sensor|biosensor""").containsMatchIn(joined)) {
        return "₹300–₹1,200 (consumables)"
    }
    if (Regex("""caliper|ruler|photograph|imagej|cast""").containsMatchIn(joined)) {
        return "₹0–₹500"
    }
    return "₹0–₹2,000 (existing departmental equipment)"
}

fun designForGap(category: String): String {
    return when (category) {
        "population", "geographic" ->
            "Cross-sectional observational study (local replication)"
        "method" ->
            "Cross-sectional study with dual measurement (new instrument + existing method)"
        "evidence" ->
            "Scoping or narrative review with documented search strategy"
        "temporal" ->
            "Cross-sectional study replicating an older high-impact study with current methods"
        "cluster" ->
            "Pilot exploratory study after expanded search"
        else ->
            "Cross-sectional observational study"
    }
}

fun dataCollectionForGap(category: String, methods: List<String>): String {
    return when (category) {
        "method" ->
            "Consecutive sampling. Measure primary outcome using both the dominant method " +
                "from the literature AND an alternative method. " +
                "One trained examiner; intra-examiner calibration on 10 participants. " +
                "Record both measurements on the same data sheet."
        "evidence" ->
            "Systematic search of PubMed, Scopus, Web of Science, and OpenAlex. " +
                "Screen titles and abstracts. Extract data into a structured form. " +
                "Assess risk of bias using a appropriate tool."
        "temporal" ->
            "Follow the original study protocol as closely as possible. " +
                "Use the same inclusion/exclusion criteria. " +
                "Add one updated measurement if the original method is outdated."
        "population", "geographic" ->
            "Consecutive sampling from the college OPD or class. " +
                "Measure primary outcome using validated clinical instruments. " +
                "One trained examiner; intra-examiner calibration on 10 participants."
        else ->
            "Consecutive sampling. Clinical measurement and structured questionnaire. " +
                "One trained examiner; intra-examiner calibration on 10 participants."
    }
}

fun statisticsForGap(category: String): String {
    return when (category) {
        "evidence" ->
            "Narrative synthesis. PRISMA flow diagram if a systematic search is conducted. " +
                "Vote counting or harvest plot for direction of effect. " +
                "No meta-analysis unless ≥3 studies use comparable measures."
        "method" ->
            "Bland–Altman analysis or Cohen kappa for agreement between methods. " +
                "McNemar test for paired proportions. " +
                "Descriptive statistics; α = 0.05."
        else ->
            "Descriptive statistics; Shapiro–Wilk; " +
                "t-test or Mann–Whitney / ANOVA or Kruskal–Wallis; " +
                "Pearson or Spearman correlation; chi-square. " +
                "α = 0.05. No causal language. Report 95% confidence intervals."
    }
}

fun titleForGap(gap: Gap, subject: Subject, papers: List<Paper>): String {
    val terms = extractTerms(gap.statement)
    val topicWords = terms.filter { it.length > 3 }.take(3)
    val topicPhrase = if (topicWords.isNotEmpty())
        topicWords.joinToString(" ") { w -> w.take(1).uppercase() + w.substring(1) }
    else
        "Research Topic"

    return when (gap.category) {
        "method" -> "$topicPhrase: comparing measurement methods in a local population"
        "evidence" -> "$topicPhrase: a scoping review of available evidence"
        "population" -> "$topicPhrase: a local cross-sectional study"
        "geographic" -> "$topicPhrase: geographic validation in a local population"
        "temporal" -> "$topicPhrase: an updated local study"
        else -> "$topicPhrase: a local cross-sectional study"
    }
}

data class ProjectScores(
    val scores: Map<String, Any>,
    val totalScore: Int,
)

fun scoreProject(p: Project, gap: Gap): ProjectScores {
    val papers = p.supportingPaperIds.size
    val scores = linkedMapOf<String, Any>(
        "evidence" to minOf(10, 4 + papers),
        "gap_clarity" to minOf(10, 5 + if (gap.confidence == "moderate") 3 else 1),
        "novelty" to minOf(10, 5 + if (gap.novelty.contains("novel")) 3 else 1),
        "feasibility" to if (p.durationWeeks <= 10) 10 else if (p.durationWeeks <= 12) 8 else 5,
        "curriculum_fit" to 9,
        "cost" to if (Regex("""₹0|₹300|₹500""").containsMatchIn(p.costInr)) 10 else 7,
        "ethics_simple" to if (
            !Regex("""blood|radiation|culture|children|biopsy""", setOf(RegexOption.IGNORE_CASE))
                .containsMatchIn(p.gap)
        ) 9 else 6,
        "publication" to if (papers >= 3) 7 else if (papers >= 1) 6 else 5,
    )
    val total = scores.values.sumOf { it as Int }
    return ProjectScores(scores = scores, totalScore = total)
}

fun buildProjects(
    subject: Subject,
    papers: List<Paper>,
    gaps: List<Gap>,
    count: Int = 8,
): List<Project> {
    Log.d("PROJECTS", "Building projects from ${gaps.size} gaps (rule-based)")
    val pop = defaultPopulation(subject)
    val setting = "Dental college and hospital, East Godavari, Andhra Pradesh, India"
    val projects = mutableListOf<Project>()

    for (gap in gaps) {
        val related = papers.filter { gap.paperIds.contains(it.id) }

        val terms = extractTerms(gap.statement)
        val title = titleForGap(gap, subject, papers)
        val design = designForGap(gap.category)

        val base = Project(
            id = "proj-${hash(title)}",
            title = title,
            domain = subject.name,
            researchQuestion = gap.candidateQuestion,
            hypothesis = "The measurement or analysis described in the gap will yield actionable data " +
                "that addresses the evidence gap identified in this retrieval.",
            evidenceBasis = if (related.isNotEmpty())
                "Grounded in ${related.size} retrieved paper(s). Gap identified: ${gap.category}."
            else
                "No directly linked papers; this is a gap-driven proposal.",
            gap = gap.statement,
            whyDifferent = gap.why,
            curriculumConnection = subject.name,
            studyDesign = design,
            setting = setting,
            population = pop,
            sampleSizeApproach = "Do not invent n. Estimate using OpenEpi or G*Power after a pilot of 15–20 " +
                "or a published prevalence. Report assumptions. Verification required.",
            primaryOutcome = terms.firstOrNull() ?: "primary outcome",
            secondaryOutcomes = terms.drop(1).take(2),
            dataCollection = dataCollectionForGap(gap.category, terms),
            statistics = statisticsForGap(gap.category),
            costInr = estimateCost(gap.category, terms),
            durationWeeks = if (gap.category == "evidence") 8 else 10,
            ethics = "IEC approval before any recruitment or data collection. " +
                "Written consent. Identifiers kept separate from clinical sheets.",
            limitations = buildList {
                add("Single-centre design limits generalizability.")
                add("Abstract-level evidence from retrieval may miss contrary full-text findings.")
                if (gap.category == "evidence") {
                    add("A scoping review is not a systematic review — it maps the field but does not quantify effect sizes.")
                }
            },
            publicationPotential = "Suitable for a peer-reviewed dental or interdisciplinary journal " +
                "if methods are clean and claims stay modest. Publication is never guaranteed.",
            keywords = terms,
            supportingPaperIds = related.take(6).map { it.id ?: "" },
            similarity = if (related.isNotEmpty())
                "Related to ${related.size} retrieved titles"
            else
                "Gap-driven — second search advised before locking protocol",
        )

        val scored = scoreProject(base, gap)
        projects.add(
            base.copy(
                scores = scored.scores,
                totalScore = scored.totalScore,
            )
        )
    }

    projects.sortByDescending { it.totalScore }
    val result = projects.take(count)
    Log.d(
        "PROJECTS",
        "Built ${result.size} projects: ${
            result.joinToString("; ") { p -> p.title.substring(0, p.title.length.coerceIn(0, 50)) }
        }"
    )
    return result
}

data class TimelineEntry(
    val week: String,
    val work: String,
)

data class BudgetItem(
    val item: String,
    val cost: String,
)

data class Protocol(
    val title: String,
    val researchQuestion: String,
    val aim: String,
    val background: String,
    val literatureReview: String,
    val gap: String,
    val rationale: String,
    val hypothesis: String,
    val objectives: List<String> = emptyList(),
    val studyDesign: String,
    val setting: String,
    val population: String,
    val sampleSize: String,
    val sampling: String,
    val variables: String,
    val primaryOutcome: String,
    val procedure: String,
    val instrument: String,
    val statistics: String,
    val ethics: String,
    val expectedFindings: String,
    val timeline: List<TimelineEntry> = emptyList(),
    val budget: List<BudgetItem> = emptyList(),
    val verificationNotes: List<String> = emptyList(),
)

fun timelineForGap(category: String): List<TimelineEntry> {
    return when (category) {
        "evidence" ->
            listOf(
                TimelineEntry(week = "Weeks 1–2", work = "Define search strategy, databases, inclusion/exclusion criteria"),
                TimelineEntry(week = "Weeks 3–5", work = "Search, screening, data extraction into structured form"),
                TimelineEntry(week = "Weeks 6–7", work = "Narrative synthesis, draft write-up"),
                TimelineEntry(week = "Week 8", work = "Supervisor review, final revision"),
            )
        "method" ->
            listOf(
                TimelineEntry(week = "Weeks 1–2", work = "IEC submission, instrument calibration pilot on 10 participants"),
                TimelineEntry(week = "Weeks 3–8", work = "Recruitment and dual-method data collection"),
                TimelineEntry(week = "Weeks 9–10", work = "Agreement analysis (Bland–Altman / kappa), draft report"),
                TimelineEntry(week = "Weeks 11–12", work = "Supervisor review, final write-up"),
            )
        else ->
            listOf(
                TimelineEntry(week = "Weeks 1–2", work = "IEC submission, instrument pilot, examiner calibration"),
                TimelineEntry(week = "Weeks 3–8", work = "Recruitment and data collection"),
                TimelineEntry(week = "Weeks 9–10", work = "Data entry, analysis, draft report"),
                TimelineEntry(week = "Weeks 11–12", work = "Supervisor review, final write-up, optional abstract"),
            )
    }
}

fun buildProtocol(project: Project, subject: Subject, papers: List<Paper>): Protocol {
    val related = papers.filter { project.supportingPaperIds.contains(it.id) }

    return Protocol(
        title = project.title,
        researchQuestion = project.researchQuestion,
        aim = "To examine ${project.primaryOutcome} in relation to the stated exposure among ${project.population}.",
        background = "This protocol is derived from a live literature retrieval for ${subject.name}. " +
            "It is a proposed undergraduate project, not an established finding.",
        literatureReview = if (related.isNotEmpty())
            related.take(8).mapIndexed { i, p ->
                "${i + 1}. ${p.title} (${p.year ?: "year unverified"}" +
                    (p.journal?.let { "; $it" } ?: "") +
                    (p.doi?.let { "; DOI $it" } ?: "") +
                    "). Sources: ${p.sources.joinToString(", ")}."
            }.joinToString("\n")
        else
            "No supporting papers linked — re-run search before submitting to IEC.",
        gap = project.gap,
        rationale = project.whyDifferent,
        hypothesis = project.hypothesis,
        objectives = listOf(
            "To measure ${project.primaryOutcome} among eligible participants.",
            "To record the primary exposure using a structured item or existing clinic instrument.",
            "To estimate the association with appropriate statistics and 95% confidence intervals.",
        ),
        studyDesign = project.studyDesign,
        setting = project.setting,
        population = project.population,
        sampleSize = project.sampleSizeApproach,
        sampling = "Consecutive sampling during the data-collection window after IEC approval.",
        variables = "Independent: exposure + age + sex. Dependent: ${project.primaryOutcome}.",
        primaryOutcome = project.primaryOutcome,
        procedure = project.dataCollection,
        instrument = "Chairside clinical instruments already available.",
        statistics = project.statistics,
        ethics = project.ethics,
        expectedFindings = "A measurable association or method agreement may be observed. " +
            "Null results are informative and should be reported.",
        timeline = timelineForGap(
            if (project.gap.contains("review") || project.gap.contains("synthesis"))
                "evidence"
            else if (project.gap.contains("method") || project.gap.contains("instrument"))
                "method"
            else
                "default"
        ),
        budget = listOf(
            BudgetItem(item = "Consumables / printing", cost = project.costInr),
            BudgetItem(item = "Capital equipment", cost = "₹0 — use existing college instruments only"),
        ),
        verificationNotes = listOf(
            "Sample size is not invented — calculate before IEC.",
            "Do not claim global novelty from a local gap.",
            "Google Scholar-only records need DOI/PMID verification before citing as peer-reviewed.",
            "Publication is never guaranteed.",
        ),
    )
}

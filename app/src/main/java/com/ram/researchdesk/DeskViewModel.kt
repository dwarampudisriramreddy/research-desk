package com.ram.researchdesk

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

// ---------------------------------------------------------------------------
// Shared LLM runtime — one engine for the whole app, shared between the Home
// screen (download/init card) and every Desk (chat + analysis).
// ---------------------------------------------------------------------------

sealed class LlmRuntimeState {
    data object Checking : LlmRuntimeState()
    data object NotDownloaded : LlmRuntimeState()
    data class Downloading(val progress: DownloadProgress) : LlmRuntimeState()
    data object Initializing : LlmRuntimeState()
    data object Ready : LlmRuntimeState()
    data class Error(val message: String) : LlmRuntimeState()
}

object LlmRuntime {

    private val _state = MutableStateFlow<LlmRuntimeState>(LlmRuntimeState.Checking)
    val state: StateFlow<LlmRuntimeState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var engineRef: LlmEngine? = null

    val ready: Boolean get() = engineRef?.isReady == true
    var backendName: String = ""
        private set

    /**
     * Ensures the model is downloaded and the engine is initialized.
     * Safe to call repeatedly and from multiple screens; concurrent callers
     * are serialized by a mutex.
     */
    suspend fun ensureReady(context: Context, autoDownload: Boolean = true): Boolean =
        mutex.withLock {
            val app = context.applicationContext

            if (engineRef?.isReady == true) {
                _state.value = LlmRuntimeState.Ready
                return@withLock true
            }

            if (!ModelDownloader.isDownloaded(app)) {
                if (!autoDownload) {
                    _state.value = LlmRuntimeState.NotDownloaded
                    return@withLock false
                }
                debugLog.log("LLM", "=== MODEL DOWNLOAD START ===")
                debugLog.log("LLM", "Model not cached, downloading (~550 MB)")
                _state.value = LlmRuntimeState.Downloading(DownloadProgress(0, 1))
                val result = ModelDownloader.download(app) { progress ->
                    _state.value = LlmRuntimeState.Downloading(progress)
                }
                val failure = result.exceptionOrNull()
                if (failure != null) {
                    debugLog.log("LLM", "=== DOWNLOAD FAILED: $failure ===")
                    _state.value = LlmRuntimeState.Error("Download failed: ${failure.message}")
                    return@withLock false
                }
                debugLog.log("LLM", "=== DOWNLOAD COMPLETE ===")
            } else {
                debugLog.log("LLM", "Model already cached, skipping download")
            }

            _state.value = LlmRuntimeState.Initializing
            debugLog.log("LLM", "=== LLM INITIALIZATION START ===")
            val eng = LlmEngine(app)
            withContext(Dispatchers.Default) {
                eng.initialize(ModelDownloader.modelPath(app))
            }
            return@withLock if (eng.isReady) {
                engineRef?.close()
                engineRef = eng
                backendName = eng.backendName
                debugLog.log("LLM", "=== ENGINE READY (${eng.backendName}) ===")
                _state.value = LlmRuntimeState.Ready
                true
            } else {
                eng.close()
                debugLog.log("LLM", "=== LLM INIT FAILED: ${eng.error} ===")
                _state.value = LlmRuntimeState.Error(eng.error ?: "Initialization failed")
                false
            }
        }

    /** One-shot chat turn with a fresh system prompt (mirrors Flutter's per-call conversation). */
    suspend fun chat(systemPrompt: String, userMessage: String): String {
        val eng = engineRef ?: throw IllegalStateException("LLM not initialized")
        if (!eng.isReady) throw IllegalStateException("LLM not ready")
        eng.resetConversation(LlmConfig(systemPrompt = systemPrompt))
        return withContext(Dispatchers.Default) {
            eng.sendMessageSync(userMessage)
        }
    }

    fun deleteModel(context: Context) {
        val app = context.applicationContext
        engineRef?.close()
        engineRef = null
        ModelDownloader.deleteModel(app)
        debugLog.log("LLM", "Model deleted from storage")
        _state.value = LlmRuntimeState.NotDownloaded
    }

    fun markNotDownloaded() {
        _state.value = LlmRuntimeState.NotDownloaded
    }
}

// ---------------------------------------------------------------------------
// Desk UI state
// ---------------------------------------------------------------------------

data class DeskChatMessage(
    val role: String, // "user" or "ai"
    val text: String,
)

/** Source toggles shown in the search panel: id to display label. */
val DESK_SOURCES: List<Pair<String, String>> = listOf(
    "pubmed" to "PubMed",
    "scopus" to "Scopus",
    "wos" to "WoS",
    "europepmc" to "Europe PMC",
    "openalex" to "OpenAlex",
    "crossref" to "Crossref",
)

data class DeskUiState(
    val year: Year? = null,
    val subject: Subject? = null,
    val query: String = "",
    val yearFrom: Int = 2021,
    val yearTo: Int = Calendar.getInstance().get(Calendar.YEAR),
    val sourcesEnabled: Set<String> = DEFAULT_SOURCES,
    val searching: Boolean = false,
    val analyzing: Boolean = false,
    val litResult: LiteratureResult? = null,
    val clusters: List<Cluster> = emptyList(),
    val gaps: List<Gap> = emptyList(),
    val projects: List<Project> = emptyList(),
    val protocol: Protocol? = null,
    val error: String? = null,
    val tab: Int = 0,
    val llmReady: Boolean = false,
    val chatMessages: List<DeskChatMessage> = emptyList(),
    val chatSending: Boolean = false,
    val debugEntries: List<DebugEntry> = emptyList(),
) {
    val papers: List<Paper> get() = litResult?.papers ?: emptyList()

    companion object {
        val DEFAULT_SOURCES: Set<String> = linkedSetOf(
            "pubmed", "scopus", "wos", "europepmc", "openalex", "crossref",
        )
    }
}

/**
 * Port of the state management inside flutter_app/lib/screens/desk_screen.dart
 * plus the analysis logic of flutter_app/lib/services/llm_service.dart.
 */
class DeskViewModel(
    application: Application,
    yearId: String,
    subjectId: String,
) : AndroidViewModel(application) {

    private val subject: Subject? = getSubject(yearId, subjectId)
    private val year: Year? = getYear(yearId)

    private val _uiState = MutableStateFlow(
        DeskUiState(
            year = year,
            subject = subject,
            query = subject?.defaultQuery.orEmpty(),
        )
    )
    val uiState: StateFlow<DeskUiState> = _uiState.asStateFlow()

    init {
        // Seed with existing entries, then stream new ones.
        _uiState.update { it.copy(debugEntries = DebugLog.entries) }
        viewModelScope.launch {
            DebugLog.stream.collect { entry ->
                _uiState.update { state -> state.copy(debugEntries = state.debugEntries + entry) }
            }
        }
        viewModelScope.launch {
            LlmRuntime.state.collect { st ->
                _uiState.update { it.copy(llmReady = st is LlmRuntimeState.Ready) }
            }
        }
        debugLog.log("DESK", "Opened desk: ${subject?.name ?: "unknown"} (${year?.name ?: "?"})")
    }

    // --- Input handlers -----------------------------------------------------

    fun setQuery(q: String) = _uiState.update { it.copy(query = q) }

    fun setYearFrom(y: Int) = _uiState.update { it.copy(yearFrom = y) }

    fun setYearTo(y: Int) = _uiState.update { it.copy(yearTo = y) }

    fun toggleSource(id: String) = _uiState.update {
        val next = if (id in it.sourcesEnabled) {
            if (it.sourcesEnabled.size <= 1) it.sourcesEnabled // keep at least one
            else it.sourcesEnabled - id
        } else {
            it.sourcesEnabled + id
        }
        it.copy(sourcesEnabled = next)
    }

    fun selectTab(index: Int) = _uiState.update { it.copy(tab = index) }

    // --- Pipeline: search -> cluster -> analyze ------------------------------

    fun runSearch() {
        val subj = subject ?: return
        val q = _uiState.value.query.trim()
        if (q.isEmpty()) {
            _uiState.update { it.copy(error = "Enter a search query to retrieve papers.") }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    searching = true,
                    error = null,
                    litResult = null,
                    clusters = emptyList(),
                    gaps = emptyList(),
                    projects = emptyList(),
                    protocol = null,
                )
            }
            try {
                val snapshot = _uiState.value
                val result = withContext(Dispatchers.IO) {
                    runLiteratureSearch(
                        query = q,
                        yearFrom = snapshot.yearFrom,
                        yearTo = snapshot.yearTo,
                        sources = snapshot.sourcesEnabled.toList(),
                    )
                }
                val clusters = withContext(Dispatchers.Default) {
                    clusterPapers(subj, result.papers)
                }
                _uiState.update {
                    it.copy(litResult = result, clusters = clusters, searching = false)
                }
                analyzeWithLlm(clusters, result.papers)
            } catch (e: Exception) {
                debugLog.log("SEARCH", "Search failed: $e")
                _uiState.update {
                    it.copy(error = e.message ?: e.toString(), searching = false)
                }
            }
        }
    }

    private suspend fun analyzeWithLlm(clusters: List<Cluster>, papers: List<Paper>) {
        val subj = subject ?: return
        _uiState.update { it.copy(analyzing = true, gaps = emptyList(), projects = emptyList()) }

        var gaps: List<Gap>
        var projects: List<Project>

        if (LlmRuntime.ready) {
            gaps = analyzeGapsWithLlm(subj.name, papers, clusters)
            if (gaps.isEmpty()) gaps = buildGaps(subj, papers, clusters)
            projects = generateProjectsWithLlm(subj.name, gaps, papers)
            if (projects.isEmpty()) projects = buildProjects(subj, papers, gaps)
        } else {
            gaps = buildGaps(subj, papers, clusters)
            projects = buildProjects(subj, papers, gaps)
        }

        _uiState.update { it.copy(gaps = gaps, projects = projects, analyzing = false) }
    }

    // --- Protocol -------------------------------------------------------------

    fun generateProtocol(project: Project) {
        val subj = subject ?: return
        val lit = _uiState.value.litResult ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(analyzing = true, tab = 5) }
            var protocol: Protocol? = null
            if (LlmRuntime.ready) {
                protocol = generateProtocolWithLlm(project, subj.name, lit.papers)
            }
            if (protocol == null) {
                protocol = withContext(Dispatchers.Default) {
                    buildProtocol(project, subj, lit.papers)
                }
            }
            _uiState.update { it.copy(protocol = protocol, analyzing = false) }
        }
    }

    // --- Chat -------------------------------------------------------------------

    fun sendChatMessage(rawText: String) {
        val subj = subject ?: return
        val text = rawText.trim()
        val s = _uiState.value
        if (text.isEmpty() || s.chatSending || !s.llmReady) return

        _uiState.update {
            it.copy(
                chatMessages = it.chatMessages + DeskChatMessage("user", text),
                chatSending = true,
            )
        }
        viewModelScope.launch {
            try {
                val systemPrompt = buildChatSystemPrompt(subj, _uiState.value)
                debugLog.log("LLM", "Chat: \"${text.take(80)}${if (text.length > 80) "..." else ""}\"")
                val response = LlmRuntime.chat(systemPrompt, text)
                debugLog.log("LLM", "Chat response: ${response.length} chars")
                _uiState.update {
                    it.copy(
                        chatMessages = it.chatMessages + DeskChatMessage("ai", response),
                        chatSending = false,
                    )
                }
            } catch (e: Exception) {
                debugLog.log("LLM", "Chat failed: $e")
                _uiState.update {
                    it.copy(
                        chatMessages = it.chatMessages + DeskChatMessage("ai", "Error: ${e.message}"),
                        chatSending = false,
                    )
                }
            }
        }
    }

    // --- Debug tab actions ------------------------------------------------------

    fun deleteModelFile() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val f = File(ModelDownloader.modelPath(app))
            if (f.exists()) {
                val sizeMb = f.length() / 1024.0 / 1024.0
                f.delete()
                debugLog.log("LLM", "Deleted model file (%.1f MB)".format(sizeMb))
            } else {
                debugLog.log("LLM", "No model file to delete")
            }
            LlmRuntime.deleteModel(app)
        }
    }

    fun clearDebugLog() {
        DebugLog.clear()
        _uiState.update { it.copy(debugEntries = emptyList()) }
    }

    // ---------------------------------------------------------------------------
    // LLM analysis (port of llm_service.dart)
    // ---------------------------------------------------------------------------

    private suspend fun analyzeGapsWithLlm(
        subjectName: String,
        papers: List<Paper>,
        clusters: List<Cluster>,
    ): List<Gap> {
        if (!LlmRuntime.ready) {
            debugLog.log("LLM", "analyzeGaps skipped - not ready")
            return emptyList()
        }
        debugLog.log("LLM", "--- analyzeGaps: ${papers.size} papers, ${clusters.size} clusters ---")

        val paperSummaries = papers.take(30).joinToString("\n") { p ->
            "- \"${p.title}\" (${p.year ?: "?"}) ${p.journal ?: ""} " +
                "[${p.studyDesign ?: "unknown"}] ${if (p.mentionsIndia) "[India]" else ""} " +
                "DOI:${p.doi ?: "none"}"
        }
        val clusterSummary = clusters.joinToString("\n") { c ->
            "- ${c.name}: ${c.paperIds.size} papers (${c.evidence})"
        }

        val prompt = """
            You are a research methodology expert for BDS (dental) students in India.

            Analyze these papers for $subjectName and identify real research gaps.

            PAPERS RETRIEVED (${papers.size}):
            $paperSummaries

            CLUSTERS:
            $clusterSummary

            Return a JSON array of gaps. Each gap must have these fields:
            {
              "statement": "clear gap statement",
              "confidence": "high|moderate|possible",
              "novelty": "one of: underexplored-in-india, methodological-extension, cross-cultural-validation, synthesis-needed, temporal-renewal",
              "why": "why this gap matters for an undergrad project",
              "candidateQuestion": "specific research question",
              "feasibilityNote": "how to do this in 8-12 weeks with minimal budget",
              "category": "one of: population, method, evidence, geographic, temporal"
            }

            Rules:
            - Be specific to the actual papers, not generic
            - Focus on gaps an undergraduate can realistically fill
            - Consider local (East Godavari, Andhra Pradesh) feasibility
            - Max 6 gaps
            - Return ONLY the JSON array, no other text
        """.trimIndent()

        return try {
            debugLog.log("LLM", "Gap analysis prompt sent...")
            val raw = LlmRuntime.chat(
                "You are a dental research methodology expert. Output only valid JSON.",
                prompt,
            )
            debugLog.log("LLM", "Parsing gap response...")
            val gaps = parseGaps(raw)
            debugLog.log("LLM", "Parsed ${gaps.size} gaps from LLM")
            gaps
        } catch (e: Exception) {
            debugLog.log("LLM", "analyzeGaps FAILED: $e")
            emptyList()
        }
    }

    private fun parseGaps(raw: String): List<Gap> = try {
        val arr = JSONArray(extractJson(raw))
        debugLog.log("LLM", "Extracted JSON: ${raw.take(300)}...")
        (0 until arr.length()).map { i ->
            val g = arr.getJSONObject(i)
            Gap(
                id = "llm-gap-${hashId(g.optString("statement"))}",
                statement = g.optString("statement"),
                confidence = g.optString("confidence", "possible"),
                novelty = g.optString("novelty", "underexplored-in-india"),
                why = g.optString("why"),
                unknown = "",
                candidateQuestion = g.optString("candidateQuestion"),
                ugFeasible = true,
                feasibilityNote = g.optString("feasibilityNote"),
                category = g.optString("category", "population"),
                paperIds = emptyList(),
            )
        }
    } catch (e: Exception) {
        debugLog.log("LLM", "Gap JSON parse FAILED: $e")
        debugLog.log("LLM", "Raw response: ${raw.take(500)}")
        emptyList()
    }

    private suspend fun generateProjectsWithLlm(
        subjectName: String,
        gaps: List<Gap>,
        papers: List<Paper>,
    ): List<Project> {
        if (!LlmRuntime.ready || gaps.isEmpty()) {
            debugLog.log("LLM", "generateProjects skipped (ready=${LlmRuntime.ready}, gaps=${gaps.size})")
            return emptyList()
        }
        debugLog.log("LLM", "--- generateProjects: ${gaps.size} gaps ---")

        val gapSummaries = gaps.take(5).joinToString("\n") { g ->
            "- [${g.category}] ${g.statement}\n  Q: ${g.candidateQuestion}"
        }

        val prompt = """
            You are a dental research project designer for BDS undergrads in East Godavari, India.

            Based on these research gaps for $subjectName:
            $gapSummaries

            Generate a project for EACH gap. Return a JSON array:
            {
              "title": "specific project title",
              "gapCategory": "same as the gap category",
              "researchQuestion": "focused question",
              "hypothesis": "testable hypothesis",
              "studyDesign": "specific design for this gap type",
              "population": "local population description",
              "primaryOutcome": "measurable primary outcome",
              "dataCollection": "step-by-step data collection method",
              "statistics": "specific statistical tests for this design",
              "costInr": "realistic cost in INR",
              "durationWeeks": 10,
              "ethics": "specific ethics considerations",
              "limitations": ["limitation 1", "limitation 2"]
            }

            Rules:
            - Use departmental instruments only (calipers, pH strips, ImageJ, probes)
            - No extra radiation, no new blood tests, no UTM
            - Statistics must match the study design
            - Cost must be realistic for Indian dental college
            - Max 5 projects
            - Return ONLY the JSON array
        """.trimIndent()

        return try {
            debugLog.log("LLM", "Project generation prompt sent...")
            val raw = LlmRuntime.chat(
                "You are a dental research project designer. Output only valid JSON.",
                prompt,
            )
            debugLog.log("LLM", "Parsing project response...")
            val projects = parseProjects(raw, subjectName, gaps)
            debugLog.log("LLM", "Parsed ${projects.size} projects from LLM")
            projects
        } catch (e: Exception) {
            debugLog.log("LLM", "generateProjects FAILED: $e")
            emptyList()
        }
    }

    private fun parseProjects(raw: String, subjectName: String, gaps: List<Gap>): List<Project> = try {
        val arr = JSONArray(extractJson(raw))
        (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            val relatedGap = gaps.firstOrNull { it.category == p.optString("gapCategory") } ?: gaps.first()

            val limitations = mutableListOf<String>()
            p.optJSONArray("limitations")?.let { la ->
                for (k in 0 until la.length()) limitations.add(la.getString(k))
            }

            Project(
                id = "llm-proj-${hashId(p.optString("title"))}",
                title = p.optString("title"),
                domain = subjectName,
                researchQuestion = p.optString("researchQuestion"),
                hypothesis = p.optString("hypothesis"),
                evidenceBasis = "LLM-generated from gap analysis.",
                gap = relatedGap.statement,
                whyDifferent = relatedGap.why,
                curriculumConnection = subjectName,
                studyDesign = p.optString("studyDesign"),
                setting = "Dental college, East Godavari, Andhra Pradesh, India",
                population = p.optString("population"),
                sampleSizeApproach = "Calculate using OpenEpi or G*Power. Do not invent n.",
                primaryOutcome = p.optString("primaryOutcome"),
                dataCollection = p.optString("dataCollection"),
                statistics = p.optString("statistics"),
                costInr = p.optString("costInr", "\u20B90\u2013\u20B92,000").ifEmpty { "\u20B90\u2013\u20B92,000" },
                durationWeeks = p.optInt("durationWeeks", 10),
                ethics = p.optString("ethics"),
                limitations = limitations,
                publicationPotential = "Suitable for peer-reviewed journal if methods are clean.",
                keywords = extractKeywords(p.optString("title")),
                supportingPaperIds = relatedGap.paperIds,
                similarity = "LLM-generated proposal",
            )
        }
    } catch (e: Exception) {
        debugLog.log("LLM", "Project JSON parse FAILED: $e")
        debugLog.log("LLM", "Raw: ${raw.take(500)}")
        emptyList()
    }

    private suspend fun generateProtocolWithLlm(
        project: Project,
        subjectName: String,
        papers: List<Paper>,
    ): Protocol? {
        if (!LlmRuntime.ready) {
            debugLog.log("LLM", "generateProtocol skipped - not ready")
            return null
        }
        debugLog.log("LLM", "--- generateProtocol: ${project.title} ---")

        val related = papers
            .filter { project.supportingPaperIds.contains(it.id) }
            .take(5)
            .joinToString("\n") { p -> "- ${p.title} (${p.year ?: "?"}) ${p.journal ?: ""}" }

        val prompt = """
            Generate a complete 12-week IEC-ready research protocol for:

            Title: ${project.title}
            Question: ${project.researchQuestion}
            Design: ${project.studyDesign}
            Outcome: ${project.primaryOutcome}
            Population: ${project.population}
            Setting: ${project.setting}

            Supporting papers:
            $related

            Return JSON with these fields:
            {
              "aim": "one sentence aim",
              "background": "2-3 sentence background",
              "objectives": ["obj1", "obj2", "obj3"],
              "sampleSize": "how to calculate, not a number",
              "sampling": "sampling method",
              "variables": "IV and DV",
              "procedure": "step by step procedure",
              "instrument": "instruments needed",
              "expectedFindings": "what might be found",
              "timeline": [{"week": "Weeks 1-2", "work": "task"}],
              "budget": [{"item": "item", "cost": "cost"}],
              "verificationNotes": ["note1", "note2"]
            }

            Return ONLY the JSON.
        """.trimIndent()

        return try {
            debugLog.log("LLM", "Protocol generation prompt sent...")
            val raw = LlmRuntime.chat(
                "You are a dental research protocol writer. Output only valid JSON.",
                prompt,
            )
            debugLog.log("LLM", "Parsing protocol response...")
            val protocol = parseProtocol(raw, project, subjectName)
            debugLog.log("LLM", "Protocol parsed successfully")
            protocol
        } catch (e: Exception) {
            debugLog.log("LLM", "generateProtocol FAILED: $e")
            null
        }
    }

    private fun parseProtocol(raw: String, project: Project, subjectName: String): Protocol? = try {
        val j = JSONObject(extractJson(raw))

        val objectives = mutableListOf<String>()
        j.optJSONArray("objectives")?.let { oa ->
            for (k in 0 until oa.length()) objectives.add(oa.getString(k))
        }

        val timeline = mutableListOf<TimelineEntry>()
        j.optJSONArray("timeline")?.let { ta ->
            for (k in 0 until ta.length()) {
                val t = ta.getJSONObject(k)
                timeline.add(TimelineEntry(week = t.optString("week"), work = t.optString("work")))
            }
        }

        val budget = mutableListOf<BudgetItem>()
        j.optJSONArray("budget")?.let { ba ->
            for (k in 0 until ba.length()) {
                val b = ba.getJSONObject(k)
                budget.add(BudgetItem(item = b.optString("item"), cost = b.optString("cost")))
            }
        }

        val verificationNotes = mutableListOf<String>()
        j.optJSONArray("verificationNotes")?.let { va ->
            for (k in 0 until va.length()) verificationNotes.add(va.getString(k))
        }

        Protocol(
            title = project.title,
            researchQuestion = project.researchQuestion,
            aim = j.optString("aim"),
            background = j.optString("background"),
            literatureReview = "See supporting papers in project details.",
            gap = project.gap,
            rationale = project.whyDifferent,
            hypothesis = project.hypothesis,
            objectives = objectives,
            studyDesign = project.studyDesign,
            setting = project.setting,
            population = project.population,
            sampleSize = j.optString("sampleSize", project.sampleSizeApproach)
                .ifEmpty { project.sampleSizeApproach },
            sampling = j.optString("sampling"),
            variables = j.optString("variables"),
            primaryOutcome = project.primaryOutcome,
            procedure = j.optString("procedure"),
            instrument = j.optString("instrument"),
            statistics = project.statistics,
            ethics = project.ethics,
            expectedFindings = j.optString("expectedFindings"),
            timeline = timeline,
            budget = budget,
            verificationNotes = verificationNotes,
        )
    } catch (e: Exception) {
        debugLog.log("LLM", "Protocol JSON parse FAILED: $e")
        null
    }

    // --- Chat system prompt -------------------------------------------------------

    private fun buildChatSystemPrompt(subject: Subject, ui: DeskUiState): String {
        val papers = ui.papers
        val paperSummaries = papers.take(20).joinToString("\n") { p ->
            "- \"${p.title}\" (${p.year ?: "?"}) ${p.journal ?: ""} [${p.studyDesign ?: "unknown"}]"
        }
        val clusterNames = ui.clusters.joinToString(", ") { it.name }
        val gapSummaries = ui.gaps.joinToString("\n") { g -> "- [${g.category}] ${g.statement}" }
        val projectSummaries = ui.projects.joinToString("\n") { p -> "- ${p.title}: ${p.researchQuestion}" }
        val protocolBlock = ui.protocol?.let { proto ->
            "\nACTIVE PROTOCOL: ${proto.title}\nAIM: ${proto.aim}\nDESIGN: ${proto.studyDesign}"
        } ?: ""

        return """
            You are a research methodology AI assistant for BDS (dental) students at a college in East Godavari, Andhra Pradesh, India.

            You help students understand research gaps, design projects, and build protocols for their undergraduate research.

            CONTEXT for ${subject.name}:
            PAPERS (${papers.size}): $paperSummaries
            CLUSTERS: $clusterNames
            GAPS: $gapSummaries
            PROJECTS: $projectSummaries$protocolBlock

            RULES:
            - Be specific to the actual papers and data above
            - Suggest feasible projects using departmental equipment only (calipers, pH strips, probes, ImageJ)
            - No extra radiation, no new blood tests, no UTM
            - Keep answers concise and actionable
            - If asked about gaps, reference the specific gaps from the list
            - If asked to design a project, use the gap category to suggest the right study design
            - Always consider local (East Godavari, Andhra Pradesh) feasibility and cost in INR
        """.trimIndent()
    }

    // --- Helpers --------------------------------------------------------------------

    private fun extractJson(raw: String): String {
        val start = if (raw.contains('[')) raw.indexOf('[') else raw.indexOf('{')
        val end = if (raw.contains(']')) raw.lastIndexOf(']') else raw.lastIndexOf('}')
        if (start == -1 || end == -1 || end <= start) throw IllegalArgumentException("No JSON found")
        return raw.substring(start, end + 1)
    }

    private fun extractKeywords(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 3 }
            .take(6)

    companion object {
        internal fun hashId(s: String): String {
            var h = 0
            for (c in s) h = 0x1FFFFFFF and (31 * h + c.code)
            return h.toString(36)
        }

        fun factory(application: Application, yearId: String, subjectId: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { DeskViewModel(application, yearId, subjectId) }
            }
    }
}

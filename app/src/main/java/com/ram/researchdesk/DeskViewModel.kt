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
    data class Downloading(val progress: DownloadProgress, val model: LlmModel) : LlmRuntimeState()
    data class Initializing(val model: LlmModel) : LlmRuntimeState()
    data object Ready : LlmRuntimeState()
    data class Error(val message: String) : LlmRuntimeState()
}

object LlmRuntime {

    private val _state = MutableStateFlow<LlmRuntimeState>(LlmRuntimeState.Checking)
    val state: StateFlow<LlmRuntimeState> = _state.asStateFlow()

    @Volatile private var engineRef: LlmEngine? = null
    private var initJob: kotlinx.coroutines.Job? = null

    val ready: Boolean get() = engineRef?.isReady == true
    var backendName: String = ""
        private set
    var loadedModel: LlmModel? = null
        private set

    /**
     * Kick off model download + engine init. Non-blocking — updates state as
     * it progresses. Safe to call multiple times; second call while init is
     * in progress is a no-op.
     */
    private var initModel: LlmModel? = null

    fun startInit(context: Context, autoDownload: Boolean = true, model: LlmModel = ModelDownloader.selectedModel(context)) {
        if (engineRef?.isReady == true && loadedModel == model) {
            _state.value = LlmRuntimeState.Ready
            return
        }
        // Same model already initializing — no-op
        if (initJob?.isActive == true && initModel == model) return
        // Different model requested — cancel old init
        initJob?.cancel()
        initModel = model

        initJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val app = context.applicationContext

            // If switching models, release old engine first
            if (loadedModel != null && loadedModel != model) {
                debugLog.log("LLM", "Switching model: ${loadedModel?.displayName} -> ${model.displayName}")
                engineRef?.stopResponse()
                engineRef?.close()
                engineRef = null
            }

            // Download if needed
            if (!ModelDownloader.isDownloaded(app, model)) {
                if (!autoDownload) {
                    _state.value = LlmRuntimeState.NotDownloaded
                    return@launch
                }
                debugLog.log("LLM", "=== MODEL DOWNLOAD START: ${model.displayName} (${model.sizeMB} MB) ===")
                _state.value = LlmRuntimeState.Downloading(DownloadProgress(0, 1), model)
                LlmNotificationService.startDownload(app)
                val result = ModelDownloader.download(app, model) { progress ->
                    _state.value = LlmRuntimeState.Downloading(progress, model)
                    LlmNotificationService.updateProgress(
                        app,
                        (progress.percent * 100).toInt(),
                        "Downloading ${model.displayName}... ${(progress.bytesReceived / 1024 / 1024)}/${(progress.totalBytes / 1024 / 1024)} MB",
                    )
                }
                val failure = result.exceptionOrNull()
                if (failure != null) {
                    debugLog.log("LLM", "=== DOWNLOAD FAILED: $failure ===")
                    _state.value = LlmRuntimeState.Error("Download failed: ${failure.message}")
                    LlmNotificationService.stop(app)
                    return@launch
                }
                debugLog.log("LLM", "=== DOWNLOAD COMPLETE: ${model.displayName} ===")
            } else {
                debugLog.log("LLM", "Model already cached: ${model.displayName}")
            }

            // Init engine
            _state.value = LlmRuntimeState.Initializing(model)
            LlmNotificationService.markInit(app)
            debugLog.log("LLM", "=== LLM INITIALIZATION START: ${model.displayName} ===")
            val eng = LlmEngine(app)
            eng.initialize(ModelDownloader.modelPath(app, model))

            if (eng.isReady) {
                engineRef?.close()
                engineRef = eng
                backendName = eng.backendName
                loadedModel = model
                debugLog.log("LLM", "=== ENGINE READY: ${model.displayName} (${eng.backendName}) ===")
                _state.value = LlmRuntimeState.Ready
                LlmNotificationService.markReady(app, eng.backendName)
            } else {
                eng.close()
                debugLog.log("LLM", "=== LLM INIT FAILED: ${eng.error} ===")
                _state.value = LlmRuntimeState.Error(eng.error ?: "Initialization failed")
                LlmNotificationService.stop(app)
            }
        }
    }

    /** Suspend version for callers that need to await readiness. */
    suspend fun ensureReady(context: Context, autoDownload: Boolean = true): Boolean {
        if (engineRef?.isReady == true) return true
        startInit(context, autoDownload)
        initJob?.join()
        return engineRef?.isReady == true
    }

    /** Stop any in-progress inference and release engine when app backgrounds. */
    fun releaseOnBackground() {
        val eng = engineRef ?: return
        debugLog.log("LLM", "Releasing engine (background)")
        eng.stopResponse()
        eng.close()
        engineRef = null
        _state.value = LlmRuntimeState.Initializing(loadedModel ?: LlmModel.DEFAULT)
    }

    /** Stop any in-progress inference without releasing the engine. */
    fun stopInference() {
        engineRef?.stopResponse()
    }

    /** Streaming chat — results arrive via callback on the main thread. */
    fun streamChat(
        systemPrompt: String,
        userMessage: String,
        onToken: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val eng = engineRef
        if (eng == null || !eng.isReady) {
            onError("LLM not ready")
            return
        }
        eng.resetConversation(LlmConfig(systemPrompt = systemPrompt))
        eng.sendMessage(
            input = userMessage,
            resultListener = { partial, done ->
                if (done) onDone() else onToken(partial)
            },
            onError = onError,
        )
    }

    /** Blocking chat — call from background coroutine only. */
    suspend fun chat(systemPrompt: String, userMessage: String): String {
        val eng = engineRef ?: throw IllegalStateException("LLM not initialized")
        if (!eng.isReady) throw IllegalStateException("LLM not ready")
        eng.resetConversation(LlmConfig(systemPrompt = systemPrompt))
        return eng.sendMessageSync(userMessage)
    }

    fun deleteModel(context: Context) {
        val app = context.applicationContext
        engineRef?.close()
        engineRef = null
        ModelDownloader.deleteModel(app)
        loadedModel = null
        LlmNotificationService.stop(app)
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

data class ProjectIdea(
    val title: String,
    val rationale: String,
    val researchQuestion: String,
    val hypothesis: String,
    val design: String,
    val population: String,
    val primaryOutcome: String,
    val methods: String,
    val duration: String,
    val feasibility: String,
    val notCoveredBy: List<String>,
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
    val chatSending: Boolean = false,
    val debugEntries: List<DebugEntry> = emptyList(),
    val filterClusterId: String? = null,
    val filterDesign: String? = null,
    val filterIndiaOnly: Boolean = false,
    val thinkingText: String = "",
    val projectIdeas: List<ProjectIdea> = emptyList(),
) {
    val papers: List<Paper> get() = litResult?.papers ?: emptyList()

    val filteredPapers: List<Paper> get() {
        var list = papers
        if (filterClusterId != null) {
            val cluster = clusters.find { it.id == filterClusterId }
            if (cluster != null) {
                val ids = cluster.paperIds.toSet()
                list = list.filter { it.id in ids }
            }
        }
        if (filterDesign != null) {
            list = list.filter { it.studyDesign.equals(filterDesign, ignoreCase = true) }
        }
        if (filterIndiaOnly) {
            list = list.filter { it.mentionsIndia }
        }
        return list
    }

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

    /** Chat messages are a separate stream — only ChatTab observes them. */
    private val _chatMessages = MutableStateFlow<List<DeskChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<DeskChatMessage>> = _chatMessages.asStateFlow()

    init {
        _uiState.update { it.copy(debugEntries = DebugLog.entries) }
        viewModelScope.launch {
            DebugLog.batchStream.collect { batch ->
                _uiState.update { state -> state.copy(debugEntries = state.debugEntries + batch) }
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

    fun setFilterCluster(clusterId: String?) = _uiState.update {
        it.copy(filterClusterId = clusterId, tab = TAB_PAPERS)
    }

    fun setFilterDesign(design: String?) = _uiState.update {
        it.copy(filterDesign = design)
    }

    fun toggleFilterIndia() = _uiState.update {
        it.copy(filterIndiaOnly = !it.filterIndiaOnly)
    }

    fun clearFilters() = _uiState.update {
        it.copy(filterClusterId = null, filterDesign = null, filterIndiaOnly = false)
    }

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
                _uiState.update { it.copy(thinkingText = "Expanding search query with AI...") }
                val searchQueries = withContext(Dispatchers.IO) {
                    expandSearchQuery(subj.name, q)
                }
                if (searchQueries != null) {
                    debugLog.log("SEARCH", "Query expanded: PubMed=${searchQueries.pubmed.take(80)}...")
                    debugLog.log("SEARCH", "Keywords: ${searchQueries.keywords.joinToString(", ")}")
                } else {
                    debugLog.log("SEARCH", "LLM unavailable, using raw query")
                }
                _uiState.update { it.copy(thinkingText = "Searching ${snapshot.sourcesEnabled.size} databases...") }
                val result = withContext(Dispatchers.IO) {
                    runLiteratureSearch(
                        query = q,
                        yearFrom = 2021,
                        yearTo = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
                        sources = snapshot.sourcesEnabled.toList(),
                        searchQueries = searchQueries,
                    )
                }
                _uiState.update { it.copy(thinkingText = "Clustering ${result.papers.size} papers by topic...") }
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
        _uiState.update { it.copy(analyzing = true, gaps = emptyList(), projects = emptyList(), thinkingText = "Reading ${papers.size} paper abstracts...") }

        var gaps: List<Gap>
        var projects: List<Project>

        if (LlmRuntime.ready) {
            _uiState.update { it.copy(thinkingText = "Identifying research gaps...") }
            gaps = analyzeGapsWithLlm(subj.name, papers, clusters)
            if (gaps.isEmpty()) gaps = buildGaps(subj, papers, clusters)

            // GC pause between LLM calls to prevent OOM on low-RAM devices
            System.gc()
            kotlinx.coroutines.delay(500)

            _uiState.update { it.copy(thinkingText = "Generating project ideas...") }
            projects = generateProjectsWithLlm(subj.name, gaps, papers)
            if (projects.isEmpty()) projects = buildProjects(subj, papers, gaps)
        } else {
            gaps = buildGaps(subj, papers, clusters)
            projects = buildProjects(subj, papers, gaps)
        }

        _uiState.update { it.copy(gaps = gaps, projects = projects, analyzing = false, thinkingText = "") }
    }

    // --- Project ideas from coverage gaps -----------------------------------

    fun generateProjectIdeas() {
        val subj = subject ?: return
        val papers = _uiState.value.papers
        if (papers.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(analyzing = true, thinkingText = "Analyzing paper content for gaps...") }

            val subjectKeywords = subj.keywords.map { it.lowercase() }
            val paperSummaries = papers.map { p ->
                val blob = buildString {
                    append("TITLE: ${p.title}")
                    append("\nABSTRACT: ${p.abstract ?: "No abstract"}")
                    append("\nDESIGN: ${p.studyDesign ?: "unknown"}")
                    append("\nYEAR: ${p.year ?: "unknown"}")
                }
                val covered = subjectKeywords.filter { blob.lowercase().contains(it) }
                val notCovered = subjectKeywords.filter { !blob.lowercase().contains(it) }
                Triple(p.title, covered, notCovered)
            }

            val allCovered = paperSummaries.flatMap { it.second }.distinct()
            val allNotCovered = subjectKeywords.filter { kw -> paperSummaries.all { kw !in it.third } }

            val paperBlock = papers.mapIndexed { i, p ->
                "PAPER ${i + 1}: ${p.title}\n${p.abstract ?: "No abstract"}\nDesign: ${p.studyDesign ?: "unknown"}"
            }.joinToString("\n\n")

            if (LlmRuntime.ready) {
                _uiState.update { it.copy(thinkingText = "Generating project ideas from gaps...") }
                val ideas = generateProjectIdeasWithLlm(subj.name, paperBlock, allCovered, allNotCovered)
                _uiState.update { it.copy(projectIdeas = ideas, analyzing = false, thinkingText = "") }
            } else {
                val ideas = buildProjectIdeas(subj, papers, allCovered, allNotCovered)
                _uiState.update { it.copy(projectIdeas = ideas, analyzing = false, thinkingText = "") }
            }
        }
    }

    private suspend fun generateProjectIdeasWithLlm(
        subjectName: String,
        paperBlock: String,
        covered: List<String>,
        notCovered: List<String>,
    ): List<ProjectIdea> {
        val prompt = """
You are a dental research methodology expert for BDS undergraduates in East Godavari, Andhra Pradesh, India.

Below are the papers retrieved for $subjectName. Read each abstract carefully.

PAPERS:
$paperBlock

Topics already covered by these papers: ${covered.joinToString(", ")}
Topics NOT covered by any paper: ${notCovered.joinToString(", ")}

Your task: generate 3-4 specific, original research project ideas that fill the gaps left by these papers.

For each project idea, you must:
1. Read what each paper actually studied (methods, outcomes, populations)
2. Identify what NONE of the papers studied — specific variables, measurements, populations, or settings that are missing
3. Design a project that a BDS undergraduate can complete in 8-12 weeks using only departmental equipment

Return a JSON array of 3-4 project ideas:
{
  "title": "specific project title",
  "rationale": "2-3 sentences: explain what the existing papers covered and what specific gap this project fills. Reference what was missing.",
  "researchQuestion": "a precise, testable question with measurable variables",
  "hypothesis": "directional hypothesis with expected direction of effect",
  "design": "study design (cross-sectional, case-control, etc.)",
  "population": "specific population and sampling strategy",
  "primaryOutcome": "the main variable to measure, with instrument",
  "methods": "step-by-step data collection plan",
  "duration": "realistic timeline in weeks",
  "feasibility": "what departmental equipment is needed",
  "notCoveredBy": ["paper titles this project specifically addresses the gap of"]
}

RULES:
- Each project MUST be based on what the papers did NOT cover — read the abstracts carefully
- Do NOT repeat what papers already studied
- Use instruments available in a dental college: calipers, pH strips, probes, ImageJ, questionnaires
- No extra radiation, no new blood tests, no UTM
- Be specific — name exact variables and measurements
- Return ONLY the JSON array
        """.trimIndent()

        return try {
            val raw = LlmRuntime.chat("You are a dental research expert. Output only valid JSON.", prompt)
            val json = JSONObject(extractSearchJson(raw))
            val arr = json.optJSONArray("ideas") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                ProjectIdea(
                    title = obj.optString("title", ""),
                    rationale = obj.optString("rationale", ""),
                    researchQuestion = obj.optString("researchQuestion", ""),
                    hypothesis = obj.optString("hypothesis", ""),
                    design = obj.optString("design", ""),
                    population = obj.optString("population", ""),
                    primaryOutcome = obj.optString("primaryOutcome", ""),
                    methods = obj.optString("methods", ""),
                    duration = obj.optString("duration", ""),
                    feasibility = obj.optString("feasibility", ""),
                    notCoveredBy = obj.optJSONArray("notCoveredBy")?.let { a ->
                        (0 until a.length()).map { a.getString(it) }
                    } ?: emptyList(),
                )
            }
        } catch (e: Exception) {
            Log.e("PROJECTS", "LLM project ideas failed: $e")
            emptyList()
        }
    }

    private fun buildProjectIdeas(
        subj: Subject,
        papers: List<Paper>,
        covered: List<String>,
        notCovered: List<String>,
    ): List<ProjectIdea> {
        if (notCovered.isEmpty()) return emptyList()
        return notCovered.take(3).map { kw ->
            ProjectIdea(
                title = "Exploring $kw in ${subj.name.lowercase()}: a cross-sectional study",
                rationale = "The retrieved papers covered ${covered.take(5).joinToString(", ")} but none addressed $kw. This gap justifies a focused investigation.",
                researchQuestion = "What is the prevalence or association of $kw among dental patients/students?",
                hypothesis = "There is a significant association between $kw and the primary outcome.",
                design = "Cross-sectional study",
                population = "Dental students or OPD patients at a dental college in East Godavari",
                primaryOutcome = "Measurement of $kw using validated instruments",
                methods = "Questionnaire + clinical measurement. Calibrate on 10 participants. Collect data over 6 weeks.",
                duration = "8-10 weeks",
                feasibility = "Departmental equipment: questionnaire, clinical instruments, basic statistical software",
                notCoveredBy = papers.take(3).mapNotNull { it.title },
            )
        }
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

        _chatMessages.update { it + DeskChatMessage("user", text) }
        _uiState.update { it.copy(chatSending = true) }
        debugLog.log("LLM", "Chat: \"${text.take(80)}${if (text.length > 80) "..." else ""}\"")

        val buffer = StringBuilder()
        val systemPrompt = buildChatSystemPrompt(subj, _uiState.value)

        LlmRuntime.streamChat(
            systemPrompt = systemPrompt,
            userMessage = text,
            onToken = { token ->
                buffer.append(token)
                val current = _chatMessages.value
                val lastIdx = current.lastIndex
                if (lastIdx >= 0 && current[lastIdx].role == "ai") {
                    _chatMessages.value = current.toMutableList().apply {
                        set(lastIdx, DeskChatMessage("ai", buffer.toString()))
                    }
                } else {
                    _chatMessages.update { it + DeskChatMessage("ai", buffer.toString()) }
                }
            },
            onDone = {
                debugLog.log("LLM", "Chat response: ${buffer.length} chars")
                _uiState.update { it.copy(chatSending = false) }
            },
            onError = { error ->
                debugLog.log("LLM", "Chat failed: $error")
                val finalText = if (buffer.isNotEmpty()) buffer.toString() else "Error: $error"
                val current = _chatMessages.value
                val lastIdx = current.lastIndex
                if (lastIdx >= 0 && current[lastIdx].role == "ai") {
                    _chatMessages.value = current.toMutableList().apply {
                        set(lastIdx, DeskChatMessage("ai", finalText))
                    }
                } else {
                    _chatMessages.update { it + DeskChatMessage("ai", finalText) }
                }
                _uiState.update { it.copy(chatSending = false) }
            },
        )
    }

    fun stopChat() {
        LlmRuntime.stopInference()
        _uiState.update { it.copy(chatSending = false) }
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

        val paperBlocks = papers.take(20).mapIndexed { i, p ->
            buildString {
                append("${i + 1}. \"${p.title}\"")
                p.year?.let { append(" ($it)") }
                p.journal?.let { append(" — $it") }
                p.studyDesign?.let { append(" [$it]") }
                if (p.mentionsIndia) append(" [India]")
                append("\n")
                val abs = p.abstract?.trim()?.take(300) ?: ""
                if (abs.isNotEmpty()) append("   Abstract: $abs\n")
                else append("   (no abstract available)\n")
            }
        }.joinToString("\n")

        val clusterSummary = clusters.filter { it.id != "other" }.joinToString("\n") { c ->
            "• ${c.name} (${c.paperIds.size} papers, ${c.evidence})"
        }

        val prompt = """
You are a dental research methodology expert for BDS undergraduates in East Godavari, Andhra Pradesh, India.

Analyze the ${papers.size} papers below for $subjectName. Read each abstract carefully.

PAPERS:
$paperBlocks

CLUSTERS:
$clusterSummary

Your task: identify what the papers collectively covered and what they collectively did NOT cover.

Think about:
1. What variables, measurements, and outcomes did the papers as a group examine?
2. What variables, measurements, or outcomes did none of the papers examine?
3. What populations or settings are missing from the evidence?
4. What contradictions exist between papers?

Return a JSON array of 4-6 gaps. Each gap object:
{
  "statement": "2-3 sentences summarizing: the papers collectively covered [list what they covered], but collectively did NOT cover [list what is missing]. Example: 'The ${papers.size} papers collectively measured X, Y, and Z using methods A and B. However, none of the papers measured W, and no study examined V in any population.'",
  "confidence": "high|moderate|possible",
  "novelty": "underexplored-in-india|methodological-extension|cross-cultural-validation|synthesis-needed|temporal-renewal",
  "candidateQuestion": "a specific, testable research question with measurable variables that addresses exactly what is missing",
  "feasibilityNote": "how an undergrad can do this in 8-12 weeks using only departmental equipment",
  "category": "population|method|evidence|geographic|temporal|outcome",
  "relatedPapers": "list paper numbers (1, 2, 3...) that this gap relates to"
}

RULES:
- Each gap MUST state what the papers collectively covered and what they collectively did NOT cover
- Do NOT list individual papers — summarize the collective coverage
- Do NOT say "more research is needed" — say WHAT is missing and WHAT specific study would fill it
- Do NOT repeat the same gap in different words
- Focus on gaps a BDS student can fill with calipers, pH strips, probes, ImageJ, questionnaires
- No extra radiation, no new blood tests, no UTM
- Return ONLY the JSON array
        """.trimIndent()

        return try {
            debugLog.log("LLM", "Gap analysis prompt sent (${prompt.length} chars)...")
            val raw = LlmRuntime.chat(
                "You are a dental research methodology expert. Output only valid JSON arrays.",
                prompt,
            )
            debugLog.log("LLM", "Parsing gap response (${raw.length} chars)...")
            val gaps = parseGaps(raw, papers)
            debugLog.log("LLM", "Parsed ${gaps.size} gaps from LLM")
            gaps
        } catch (e: Exception) {
            debugLog.log("LLM", "analyzeGaps FAILED: $e")
            emptyList()
        }
    }

    private fun parseGaps(raw: String, papers: List<Paper>): List<Gap> = try {
        val arr = JSONArray(extractJson(raw))
        debugLog.log("LLM", "Extracted JSON: ${raw.take(300)}...")
        (0 until arr.length()).map { i ->
            val g = arr.getJSONObject(i)
            // Map related paper numbers back to paper IDs
            val relatedIds = mutableListOf<String>()
            g.optString("relatedPapers", "").split(Regex("[,\\s]+")).forEach { token ->
                val num = token.trim().toIntOrNull()
                if (num != null && num in 1..papers.size) {
                    relatedIds.add(papers[num - 1].id ?: "")
                }
            }
            Gap(
                id = "llm-gap-${hashId(g.optString("statement"))}",
                statement = g.optString("statement"),
                confidence = g.optString("confidence", "possible"),
                novelty = g.optString("novelty", "underexplored-in-india"),
                why = g.optString("why"),
                unknown = g.optString("unknown", ""),
                candidateQuestion = g.optString("candidateQuestion"),
                ugFeasible = true,
                feasibilityNote = g.optString("feasibilityNote"),
                category = g.optString("category", "population"),
                paperIds = relatedIds.filter { it.isNotEmpty() },
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

        val gapBlocks = gaps.take(5).mapIndexed { i, g ->
            buildString {
                append("GAP ${i + 1} [${g.category}] (confidence: ${g.confidence}):\n")
                append("  Statement: ${g.statement}\n")
                append("  Research question: ${g.candidateQuestion}\n")
                append("  Feasibility: ${g.feasibilityNote}\n")
                if (g.paperIds.isNotEmpty()) {
                    append("  Related papers: ${g.paperIds.joinToString(", ") { id ->
                        papers.find { it.id == id }?.title?.take(60) ?: id
                    }}\n")
                }
            }
        }.joinToString("\n")

        // Include a few key paper abstracts for context
        val paperContext = papers.take(10).joinToString("\n") { p ->
            val abs = p.abstract?.trim()?.take(200) ?: ""
            "- \"${p.title}\" (${p.year ?: "?"}) ${p.journal ?: ""}" +
                if (abs.isNotEmpty()) "\n  $abs" else ""
        }

        val prompt = """
You are a dental research project designer for BDS undergraduates in East Godavari, Andhra Pradesh, India.

SUBJECT: $subjectName

RESEARCH GAPS TO ADDRESS:
$gapBlocks

KEY PAPERS FOR REFERENCE:
$paperContext

For EACH gap above, design ONE specific, realistic project. Each project must:
- Address that specific gap with a concrete, measurable study
- Use ONLY departmental equipment (calipers, pH strips, probes, intraoral camera, ImageJ, survey forms)
- No extra radiation, no new blood tests, no UTM
- Be completable in 8-12 weeks by a single BDS student
- Cost under ₹5,000

Return a JSON array of 4-5 projects:
{
  "title": "specific title with the actual variable name (e.g., 'Comparing caliper and digital photograph measurements of gingival recession in adults with chronic periodontitis')",
  "gapIndex": 1,
  "researchQuestion": "specific question with measurable variables (e.g., 'Is caliper measurement of gingival recession width equivalent to digital photograph measurement using ImageJ?')",
  "hypothesis": "testable hypothesis (e.g., 'Caliper and digital photograph measurements of gingival recession width will show good agreement (kappa > 0.7) in adults with chronic periodontitis')",
  "studyDesign": "specific design (e.g., 'Cross-sectional diagnostic accuracy study with consecutive sampling')",
  "population": "specific local population (e.g., 'Adults aged 18-65 with chronic periodontitis attending the Periodontics OPD')",
  "primaryOutcome": "specific measurable outcome with instrument (e.g., 'Gingival recession width measured in mm using both a UNC-15 caliper and ImageJ on intraoral photographs')",
  "secondaryOutcomes": ["outcome 2", "outcome 3"],
  "dataCollection": "step-by-step: what is measured, how, by whom, in what order",
  "statistics": "specific tests for this exact design (e.g., 'Bland-Altman plot for agreement, paired t-test for mean difference, ICC for reliability')",
  "costInr": "realistic cost breakdown",
  "durationWeeks": 10,
  "ethics": "specific ethics points for this study",
  "limitations": ["specific limitation 1", "specific limitation 2"]
}

RULES:
- Title must name the SPECIFIC variables and population, not generic "Research Topic"
- Hypothesis must be TESTABLE with the proposed methods
- Primary outcome must include the MEASUREMENT TOOL and UNIT
- Statistics must match the study design exactly
- Each project must be DIFFERENT from the others — no repeated templates
- Return ONLY the JSON array
        """.trimIndent()

        return try {
            debugLog.log("LLM", "Project generation prompt sent (${prompt.length} chars)...")
            val raw = LlmRuntime.chat(
                "You are a dental research project designer. Output only valid JSON arrays.",
                prompt,
            )
            debugLog.log("LLM", "Parsing project response (${raw.length} chars)...")
            val projects = parseProjects(raw, subjectName, gaps, papers)
            debugLog.log("LLM", "Parsed ${projects.size} projects from LLM")
            projects
        } catch (e: Exception) {
            debugLog.log("LLM", "generateProjects FAILED: $e")
            emptyList()
        }
    }

    private fun parseProjects(raw: String, subjectName: String, gaps: List<Gap>, papers: List<Paper>): List<Project> = try {
        val arr = JSONArray(extractJson(raw))
        (0 until arr.length()).map { i ->
            val p = arr.getJSONObject(i)
            // Match to gap by index (1-based from prompt)
            val gapIdx = p.optInt("gapIndex", 1) - 1
            val relatedGap = gaps.getOrNull(gapIdx) ?: gaps.firstOrNull() ?: Gap(
                id = "", statement = "", confidence = "possible", novelty = "",
                why = "", unknown = "", candidateQuestion = "",
            )

            val limitations = mutableListOf<String>()
            p.optJSONArray("limitations")?.let { la ->
                for (k in 0 until la.length()) limitations.add(la.getString(k))
            }

            val secondaryOutcomes = mutableListOf<String>()
            p.optJSONArray("secondaryOutcomes")?.let { sa ->
                for (k in 0 until sa.length()) secondaryOutcomes.add(sa.getString(k))
            }

            Project(
                id = "llm-proj-${hashId(p.optString("title"))}",
                title = p.optString("title"),
                domain = subjectName,
                researchQuestion = p.optString("researchQuestion"),
                hypothesis = p.optString("hypothesis"),
                evidenceBasis = "Derived from ${relatedGap.category} gap analysis of ${papers.size} retrieved papers.",
                gap = relatedGap.statement,
                whyDifferent = relatedGap.why,
                curriculumConnection = subjectName,
                studyDesign = p.optString("studyDesign"),
                setting = "Dental college and hospital, East Godavari, Andhra Pradesh, India",
                population = p.optString("population"),
                sampleSizeApproach = "Calculate using OpenEpi or G*Power after a pilot of 15-20. Do not invent n.",
                primaryOutcome = p.optString("primaryOutcome"),
                secondaryOutcomes = secondaryOutcomes,
                dataCollection = p.optString("dataCollection"),
                statistics = p.optString("statistics"),
                costInr = p.optString("costInr", "\u20B90\u2013\u20B92,000").ifEmpty { "\u20B90\u2013\u20B92,000" },
                durationWeeks = p.optInt("durationWeeks", 10),
                ethics = p.optString("ethics"),
                limitations = limitations,
                publicationPotential = "Suitable for peer-reviewed dental journal if methods are rigorous and claims stay modest.",
                keywords = extractKeywords(p.optString("title")),
                supportingPaperIds = relatedGap.paperIds,
                similarity = "Gap-driven proposal from ${relatedGap.category} analysis",
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
            .joinToString("\n") { p ->
                val abs = p.abstract?.trim()?.take(200) ?: ""
                "- \"${p.title}\" (${p.year ?: "?"}) ${p.journal ?: ""}" +
                    if (abs.isNotEmpty()) "\n  $abs" else ""
            }

        val prompt = """
You are a dental research protocol writer for a BDS undergraduate in East Godavari, Andhra Pradesh, India.

PROJECT:
Title: ${project.title}
Research question: ${project.researchQuestion}
Hypothesis: ${project.hypothesis}
Design: ${project.studyDesign}
Primary outcome: ${project.primaryOutcome}
Population: ${project.population}
Setting: ${project.setting}
Gap this addresses: ${project.gap}
Why this is needed: ${project.whyDifferent}
Budget: ${project.costInr}

SUPPORTING PAPERS:
$related

Write a complete 10-12 week IEC-ready protocol. Be SPECIFIC to this project — no generic filler.

Return JSON:
{
  "aim": "one sentence aim with the SPECIFIC variables and population",
  "background": "2-3 sentences summarizing what is known from the supporting papers and what is missing",
  "objectives": ["specific objective 1", "specific objective 2", "specific objective 3"],
  "sampleSize": "how to calculate (e.g., 'Use OpenEpi for proportion, assuming p=0.5, 95% CI, 5% precision')",
  "sampling": "specific sampling method with inclusion/exclusion criteria",
  "variables": "Independent: [specific vars]. Dependent: [specific outcome]. Confounders: [list]",
  "procedure": "numbered steps: 1. Obtain IEC approval 2. Recruit using [specific criteria] 3. Measure [specific outcome] using [specific tool] 4. Record data on [specific form] ...",
  "instrument": "specific instruments with brand/model if relevant (e.g., 'UNC-15 periodontal probe, iPhone intraoral camera, ImageJ 1.53t')",
  "expectedFindings": "what specifically might be found and what it would mean clinically",
  "timeline": [{"week": "Weeks 1-2", "work": "specific task"}],
  "budget": [{"item": "specific item", "cost": "cost in INR"}],
  "verificationNotes": ["specific caution about this study"]
}

RULES:
- Every section must reference the SPECIFIC variables from the project, not generic "primary outcome"
- Procedure must be detailed enough for a student to follow step-by-step
- Timeline must fit in 12 weeks
- Budget must be under ₹5,000 total
- Include at least 5 specific verification notes (e.g., "Do not claim causation from cross-sectional data")
- Return ONLY the JSON
        """.trimIndent()

        return try {
            debugLog.log("LLM", "Protocol generation prompt sent (${prompt.length} chars)...")
            val raw = LlmRuntime.chat(
                "You are a dental research protocol writer. Output only valid JSON.",
                prompt,
            )
            debugLog.log("LLM", "Parsing protocol response (${raw.length} chars)...")
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

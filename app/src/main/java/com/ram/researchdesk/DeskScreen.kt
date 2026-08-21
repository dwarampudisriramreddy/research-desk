package com.ram.researchdesk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TAB_NAMES = listOf(
    "Papers", "Landscape", "Clusters",
    "Protocol", "Debug", "Chat",
)

private const val TAB_PAPERS = 0
private const val TAB_LANDSCAPE = 1
private const val TAB_CLUSTERS = 2
private const val TAB_PROTOCOL = 3
private const val TAB_DEBUG = 4
private const val TAB_CHAT = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeskScreen(viewModel: DeskViewModel, onBack: () -> Unit) {
    val ui by viewModel.uiState.collectAsState()
    val subject = ui.subject

    if (subject == null) {
        Scaffold(topBar = { TopAppBar(title = { Text("Error") }) }) { padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text("Subject not found") }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(subject.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (ui.llmReady) {
                        SuggestionChip(
                            onClick = { viewModel.selectTab(TAB_CHAT) },
                            label = { Text("AI Chat", fontSize = 11.sp) },
                            icon = {
                                Icon(
                                    Icons.Default.AutoAwesome, contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    } else {
                        SuggestionChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("AI Off", fontSize = 11.sp) },
                            icon = {
                                Icon(
                                    Icons.Default.AutoAwesome, contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (ui.searching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            SearchPanel(ui = ui, viewModel = viewModel)
            SourceStatusRow(ui = ui)

            if (ui.analyzing) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = ui.thinkingText.ifEmpty { "Analyzing..." },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            SecondaryScrollableTabRow(
                selectedTabIndex = ui.tab,
                edgePadding = 8.dp,
                modifier = Modifier.height(44.dp),
            ) {
                TAB_NAMES.forEachIndexed { i, name ->
                    Tab(
                        selected = ui.tab == i,
                        onClick = { viewModel.selectTab(i) },
                        text = {
                            Text(
                                text = if (i == TAB_PAPERS && ui.litResult != null) {
                                    "$name ${ui.papers.size}"
                                } else name,
                                fontSize = 12.sp,
                            )
                        },
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (ui.error != null && ui.litResult == null) {
                    ErrorView(error = ui.error!!, onRetry = viewModel::runSearch)
                } else {
                    TabBody(ui = ui, viewModel = viewModel)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Search panel
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchPanel(ui: DeskUiState, viewModel: DeskViewModel) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        ) {
            Text(
                text = ui.year?.name ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = ui.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Literature query...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSend = { viewModel.runSearch() }),
                )
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Button(
                    onClick = viewModel::runSearch,
                    enabled = !ui.searching,
                ) {
                    if (ui.searching) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Search")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DESK_SOURCES.forEach { (id, label) ->
                    FilterChip(
                        selected = id in ui.sourcesEnabled,
                        onClick = { viewModel.toggleSource(id) },
                        label = { Text(label, fontSize = 11.sp) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Source status chips
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceStatusRow(ui: DeskUiState) {
    val result = ui.litResult ?: return
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        result.sources.forEach { s ->
            val bg = if (s.ok) Color(0xFF2E7D32).copy(alpha = 0.10f)
            else Color(0xFFEF6C00).copy(alpha = 0.10f)
            val fg = if (s.ok) Color(0xFF2E7D32) else Color(0xFFEF6C00)
            Surface(shape = RoundedCornerShape(12.dp), color = bg) {
                Text(
                    text = "${s.label} ${if (s.ok) s.count else "\u2014"}",
                    fontSize = 11.sp,
                    color = fg,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tab body routing
// ---------------------------------------------------------------------------

@Composable
private fun TabBody(ui: DeskUiState, viewModel: DeskViewModel) {
    when (ui.tab) {
        TAB_DEBUG -> DebugTab(ui = ui, viewModel = viewModel)
        TAB_CHAT -> ChatTab(ui = ui, viewModel = viewModel)
        else -> {
            if (ui.litResult == null && !ui.searching) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tap Search to begin")
                }
            } else if (ui.litResult == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (ui.tab) {
                    TAB_LANDSCAPE -> LandscapeTab(ui = ui, viewModel = viewModel)
                    TAB_PAPERS -> PapersTab(ui = ui, viewModel = viewModel)
                    TAB_CLUSTERS -> ClustersTab(ui = ui, viewModel = viewModel)
                    TAB_PROTOCOL -> ProtocolTab(ui = ui)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Landscape tab
// ---------------------------------------------------------------------------

@Composable
private fun LandscapeTab(ui: DeskUiState, viewModel: DeskViewModel) {
    val subject = ui.subject ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("What the literature is doing", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "This desk starts with ${subject.name.lowercase()} as taught in this BDS year, " +
                            "then retrieves live papers from PubMed, Scopus, " +
                            "Web of Science, and open indexes.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (ui.clusters.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Research clusters", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        ui.clusters.forEach { c ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setFilterCluster(c.id) }
                                    .padding(bottom = 8.dp),
                            ) {
                                Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    text = "${c.paperIds.size} papers \u00B7 ${c.evidence}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
        ui.litResult?.let { result ->
            item { SearchTransparencyCard(meta = result.meta) }
        }
    }
}

@Composable
private fun SearchTransparencyCard(meta: SearchMeta) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Search transparency", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Databases: ${meta.databases.joinToString(", ")}\n" +
                    "Date: ${meta.searchDate} \u00B7 Period: ${meta.period}\n" +
                    "Terms: ${meta.terms}\n" +
                    "Screened ${meta.screened} \u00B7 included ${meta.included}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            meta.notes.forEach { n ->
                Text(
                    text = "\u2022 $n",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Papers tab
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PapersTab(ui: DeskUiState, viewModel: DeskViewModel) {
    val papers = ui.filteredPapers
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PaperFilters(ui = ui, viewModel = viewModel)
        }
        if (papers.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No papers match the current filters.")
                }
            }
        } else {
            items(papers) { paper -> PaperCard(paper = paper) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaperFilters(ui: DeskUiState, viewModel: DeskViewModel) {
    val hasFilters = ui.filterClusterId != null || ui.filterDesign != null || ui.filterIndiaOnly
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ui.clusters.forEach { c ->
                FilterChip(
                    selected = ui.filterClusterId == c.id,
                    onClick = {
                        viewModel.setFilterCluster(if (ui.filterClusterId == c.id) null else c.id)
                    },
                    label = { Text(c.name, fontSize = 10.sp) },
                )
            }
            val designs = ui.papers.mapNotNull { it.studyDesign }.distinct().sorted()
            designs.forEach { d ->
                FilterChip(
                    selected = ui.filterDesign == d,
                    onClick = {
                        viewModel.setFilterDesign(if (ui.filterDesign == d) null else d)
                    },
                    label = { Text(d, fontSize = 10.sp) },
                )
            }
            FilterChip(
                selected = ui.filterIndiaOnly,
                onClick = { viewModel.toggleFilterIndia() },
                label = { Text("India only", fontSize = 10.sp) },
            )
        }
        if (hasFilters) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { viewModel.clearFilters() }) {
                Text("Clear filters", fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${papers.size} of ${ui.papers.size} papers",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PaperCard(paper: Paper) {
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (paper.mentionsIndia) MiniBadge(text = "India", container = Color(0xFF2E7D32).copy(alpha = 0.12f))
                if (paper.isOpenAccess) MiniBadge(text = "Open access", container = MaterialTheme.colorScheme.secondaryContainer)
                if (paper.confidence == "high") MiniBadge(text = "High confidence", container = MaterialTheme.colorScheme.tertiaryContainer)
            }
            Spacer(Modifier.height(6.dp))
            Text(paper.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOfNotNull(
                    paper.year?.toString(),
                    paper.journal,
                    paper.studyDesign,
                    paper.citationCount?.let { "$it citations" },
                ).joinToString(" \u00B7 "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (paper.authors.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                val shown = paper.authors.take(5).joinToString(", ")
                val extra = if (paper.authors.size > 5) ", et al." else ""
                Text(
                    text = shown + extra,
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                paper.sources.forEach { src ->
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(src, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = paper.abstract ?: "No abstract available for this record.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val link = paper.url ?: paper.doi?.let { "https://doi.org/$it" }
                    if (link != null) {
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = { runCatching { uriHandler.openUri(link) } }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Open source page", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniBadge(text: String, container: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = container) {
        Text(
            text = text,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Clusters tab
// ---------------------------------------------------------------------------

@Composable
private fun ClustersTab(ui: DeskUiState, viewModel: DeskViewModel) {
    if (ui.clusters.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Clusters appear after a retrieval.")
        }
        return
    }
    val papersById = remember(ui.litResult) { ui.papers.associateBy { it.id } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ui.clusters) { cluster ->
            ClusterCard(cluster = cluster, papersById = papersById, onClick = {
                viewModel.setFilterCluster(cluster.id)
            })
        }
    }
}

@Composable
private fun ClusterCard(cluster: Cluster, papersById: Map<String?, Paper>, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cluster.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = cluster.evidence,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${cluster.paperIds.size} papers",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (cluster.keywords.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    cluster.keywords.take(6).forEach { kw ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(kw, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            if (cluster.potentialGaps.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                cluster.potentialGaps.forEach { g ->
                    Text("\u2022 $g", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (cluster.limitations.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                cluster.limitations.forEach { l ->
                    Text(l, fontSize = 11.sp, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val memberTitles = cluster.paperIds.mapNotNull { papersById[it]?.title }.take(5)
            if (memberTitles.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(6.dp))
                memberTitles.forEach { t ->
                    Text(
                        text = "\u2013 $t",
                        fontSize = 11.sp,
                        maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Gaps tab
// ---------------------------------------------------------------------------

@Composable
private fun GapsTab(ui: DeskUiState) {
    if (ui.gaps.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Gaps are inferred after retrieval.")
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ui.gaps) { gap -> GapCard(gap = gap) }
    }
}

@Composable
private fun GapCard(gap: Gap) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        text = gap.category.ifEmpty { "gap" },
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(
                        text = gap.confidence,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (!gap.ugFeasible) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            text = "not UG-feasible",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(gap.statement, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (gap.candidateQuestion.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(gap.candidateQuestion, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (gap.feasibilityNote.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(gap.feasibilityNote, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Projects tab
// ---------------------------------------------------------------------------

@Composable
private fun ProjectsTab(ui: DeskUiState, onGenerateProtocol: (Project) -> Unit) {
    if (ui.projects.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Projects appear after a retrieval and gap pass.",
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(ui.projects) { project -> ProjectCard(project = project, onGenerateProtocol = onGenerateProtocol) }
    }
}

@Composable
private fun ProjectCard(project: Project, onGenerateProtocol: (Project) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(project.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        text = project.domain,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (project.totalScore > 0) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(
                            text = "Score ${project.totalScore}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = "${project.durationWeeks} weeks \u00B7 ${project.costInr}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ProtocolSection("Research question", project.researchQuestion)
            ProtocolSection("Hypothesis", project.hypothesis)
            ProtocolSection("Study design", project.studyDesign)
            ProtocolSection("Population", project.population)
            ProtocolSection("Primary outcome", project.primaryOutcome)
            if (project.secondaryOutcomes.isNotEmpty()) {
                ProtocolSection("Secondary outcomes", project.secondaryOutcomes.joinToString("; "))
            }
            ProtocolSection("Data collection", project.dataCollection)
            ProtocolSection("Statistics", project.statistics)
            ProtocolSection("Ethics", project.ethics)
            if (project.limitations.isNotEmpty()) {
                ProtocolSection("Limitations", project.limitations.joinToString("\n") { "\u2022 $it" })
            }

            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.Button(onClick = { onGenerateProtocol(project) }) {
                Text("Generate 12-week protocol")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Protocol tab
// ---------------------------------------------------------------------------

@Composable
private fun ProtocolTab(ui: DeskUiState) {
    val protocol = ui.protocol
    if (protocol == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Pick a project on the Projects tab, then generate a 12-week protocol.",
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(protocol.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "IEC-ready draft \u00B7 verify every claim before submission",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { ProtocolCard(title = "Aim", body = protocol.aim) }
        item { ProtocolCard(title = "Background", body = protocol.background) }
        item { ProtocolCard(title = "Research question", body = protocol.researchQuestion) }
        item { ProtocolCard(title = "Gap", body = protocol.gap) }
        item { ProtocolCard(title = "Rationale", body = protocol.rationale) }
        item { ProtocolCard(title = "Hypothesis", body = protocol.hypothesis) }
        if (protocol.objectives.isNotEmpty()) {
            item { ProtocolCard(title = "Objectives", body = protocol.objectives.mapIndexed { i, o -> "${i + 1}. $o" }.joinToString("\n")) }
        }
        item { ProtocolCard(title = "Study design", body = protocol.studyDesign) }
        item { ProtocolCard(title = "Setting", body = protocol.setting) }
        item { ProtocolCard(title = "Population", body = protocol.population) }
        item { ProtocolCard(title = "Sample size", body = protocol.sampleSize) }
        item { ProtocolCard(title = "Sampling", body = protocol.sampling) }
        item { ProtocolCard(title = "Variables", body = protocol.variables) }
        item { ProtocolCard(title = "Primary outcome", body = protocol.primaryOutcome) }
        item { ProtocolCard(title = "Procedure", body = protocol.procedure) }
        item { ProtocolCard(title = "Instrument", body = protocol.instrument) }
        item { ProtocolCard(title = "Statistics", body = protocol.statistics) }
        item { ProtocolCard(title = "Ethics", body = protocol.ethics) }
        item { ProtocolCard(title = "Expected findings", body = protocol.expectedFindings) }
        item { ProtocolCard(title = "Literature review", body = protocol.literatureReview) }

        if (protocol.timeline.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Timeline", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        protocol.timeline.forEach { t ->
                            Row(Modifier.padding(bottom = 6.dp)) {
                                Text(t.week, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(90.dp))
                                Text(t.work, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (protocol.budget.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Budget", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        protocol.budget.forEach { b ->
                            Row(Modifier.padding(bottom = 6.dp)) {
                                Text(b.item, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(150.dp))
                                Text(b.cost, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (protocol.verificationNotes.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Verification notes", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        protocol.verificationNotes.forEach { n ->
                            Text(
                                text = "\u26A0 $n",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtocolCard(title: String, body: String) {
    if (body.isBlank()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(2.dp))
            Text(body, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun ProtocolSection(title: String, body: String) {
    if (body.isBlank()) return
    Column(Modifier.padding(top = 6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(body, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// Debug tab
// ---------------------------------------------------------------------------

@Composable
private fun DebugTab(ui: DeskUiState, viewModel: DeskViewModel) {
    val listState = rememberLazyListState()

    LaunchedEffect(ui.debugEntries.size) {
        if (ui.debugEntries.isNotEmpty()) {
            listState.scrollToItem(ui.debugEntries.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Terminal, contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Debug Console (${ui.debugEntries.size} entries)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::deleteModelFile) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete Model", fontSize = 11.sp)
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = viewModel::clearDebugLog) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear", fontSize = 11.sp)
                }
            }
        }

        if (ui.debugEntries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No debug output yet.\nSearch or open a desk to see logs.",
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
            ) {
                itemsIndexed(ui.debugEntries) { _, entry ->
                    val isError = entry.message.contains("fail", ignoreCase = true) ||
                        entry.message.contains("error", ignoreCase = true)
                    Text(
                        text = entry.toString(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chat tab (integrated LLM chat panel)
// ---------------------------------------------------------------------------

private val CHAT_SUGGESTIONS = listOf(
    "What gaps exist?",
    "Suggest a project",
    "Design my methods",
    "What statistics?",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatTab(ui: DeskUiState, viewModel: DeskViewModel) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val chatMessages by viewModel.chatMessages.collectAsState()

    LaunchedEffect(chatMessages.size, ui.chatSending) {
        val target = chatMessages.size + (if (ui.chatSending) 0 else -1)
        if (target >= 0) listState.animateScrollToItem(target.coerceAtLeast(0))
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        if (!ui.llmReady) {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    text = "AI model not loaded. Go back to Home and download it to enable chat.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (chatMessages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Ask about research gaps,\nprojects, or methodology",
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CHAT_SUGGESTIONS.forEach { suggestion ->
                            SuggestionChip(
                                onClick = { viewModel.sendChatMessage(suggestion) },
                                label = { Text(suggestion, fontSize = 12.sp) },
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(chatMessages) { _, msg -> ChatBubble(msg = msg) }
                    if (ui.chatSending) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "AI is thinking...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(tonalElevation = 3.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask the AI...", fontSize = 14.sp) },
                    shape = RoundedCornerShape(24.dp),
                    singleLine = false,
                    maxLines = 4,
                    enabled = ui.llmReady && !ui.chatSending,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendChatMessage(inputText)
                                inputText = ""
                            }
                        },
                    ),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendChatMessage(inputText)
                            inputText = ""
                        }
                    },
                    enabled = ui.llmReady && !ui.chatSending && inputText.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: DeskChatMessage) {
    val isUser = msg.role == "user"
    val bubbleColor =
        if (isUser) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHighest
    val textColor =
        if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 320.dp),
        ) {
            Text(
                text = msg.text,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = textColor,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Error view
// ---------------------------------------------------------------------------

@Composable
private fun ErrorView(error: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = error,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.FilledTonalButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Retry")
            }
        }
    }
}

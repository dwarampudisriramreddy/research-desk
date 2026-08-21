package com.ram.researchdesk

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val LIT_UA = "BDSResearchDesk/1.0 (East Godavari undergraduate literature desk)"

private val SCOPUS_ISSNS = listOf(
    "0022-0345", "0303-6979", "0022-3492", "0008-6568", "0301-5661",
    "0300-5712", "0905-7161", "1365-2842", "1432-6981", "0003-9969",
    "1354-523X", "0904-2512", "0143-2885", "0109-5641", "0022-3913",
    "0889-5406", "0003-3219", "0141-5387", "0960-7439", "0250-832X",
    "1368-8375", "1043-3074", "1472-6831", "0020-6530", "2212-4403",
    "0266-4356", "0901-5027", "0278-2391", "1600-0722", "0007-0612",
    "0002-8177", "0022-3255", "0734-0664", "1751-6161", "0142-9612",
    "1742-7061", "0021-9290", "0148-0731", "1350-4533", "0268-0033",
    "0018-9294", "1424-8220", "0956-5663", "2214-8604", "1748-6041",
    "1552-4973", "0021-8782", "0022-3751", "2057-4347", "1059-941X",
    "2196-1042", "0970-9290", "0972-124X",
)

private val WOS_ISSNS = listOf(
    "0022-0345", "0303-6979", "0022-3492", "0008-6568", "0301-5661",
    "0300-5712", "0905-7161", "1365-2842", "1432-6981", "0003-9969",
    "1354-523X", "0143-2885", "0109-5641", "0022-3913", "0889-5406",
    "0003-3219", "0141-5387", "0960-7439", "1368-8375", "1043-3074",
    "1472-6831", "2212-4403", "0266-4356", "0901-5027", "0278-2391",
    "0007-0612", "0002-8177", "1751-6161", "0142-9612", "1742-7061",
    "0021-9290", "0148-0731", "1350-4533", "0018-9294", "1424-8220",
    "0956-5663", "2214-8604", "1748-6041", "0021-8782", "0022-3751",
    "2057-4347", "1059-941X",
)

// --- org.json helpers ---

private fun JSONObject.str(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return when (val v = opt(name)) {
        null -> null
        else -> v.toString()
    }
}

private fun JSONObject.intVal(name: String): Int? {
    if (!has(name) || isNull(name)) return null
    return when (val v = opt(name)) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull()
        else -> null
    }
}

private fun JSONArray.stringList(): List<String> =
    (0 until length()).mapNotNull { i ->
        if (isNull(i)) null else opt(i)?.toString()
    }

private fun JSONArray.objList(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray.strAt(index: Int): String? {
    if (index >= length() || isNull(index)) return null
    return opt(index)?.toString()
}

private fun JSONArray.intAt(index: Int): Int? {
    if (index >= length() || isNull(index)) return null
    return when (val v = opt(index)) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull()
        else -> null
    }
}

// --- HTTP ---

private class HttpResponse(val statusCode: Int, val body: String)

private fun buildUrl(base: String, params: Map<String, String>): String {
    val builder = Uri.parse(base).buildUpon()
    for ((k, v) in params) builder.appendQueryParameter(k, v)
    return builder.build().toString()
}

private suspend fun fetchTimeout(url: String, timeoutMs: Long = 12_000L): HttpResponse =
    withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs.toInt()
            conn.readTimeout = timeoutMs.toInt()
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", LIT_UA)
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            HttpResponse(code, body)
        } finally {
            conn.disconnect()
        }
    }

// --- Helpers ---

private val DOI_URL_PREFIX = Regex("^https?://(dx\\.)?doi\\.org/", RegexOption.IGNORE_CASE)
private val DOI_COLON_PREFIX = Regex("^doi:", RegexOption.IGNORE_CASE)
private val YEAR_RE = Regex("\\b(19|20)\\d{2}\\b")
private val INDIA_RE = Regex("\\bindia\\b|\\bindian\\b", RegexOption.IGNORE_CASE)
private val TAGS_RE = Regex("<[^>]+>")
private val WHITESPACE_RE = Regex("\\s+")

fun decodeEntities(s: String?): String {
    if (s.isNullOrEmpty()) return ""
    return s.replace("&amp;", "&")
        .replace("&nbsp;", " ")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(TAGS_RE, " ")
        .replace(WHITESPACE_RE, " ")
        .trim()
}

fun normalizeDoi(doi: String?): String? {
    if (doi.isNullOrEmpty()) return null
    val stripped = doi.replaceFirst(DOI_URL_PREFIX, "")
        .replaceFirst(DOI_COLON_PREFIX, "")
        .trim()
    return stripped.ifEmpty { null }
}

fun normalizeTitle(t: String): String =
    t.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

fun yearFromText(text: String?): Int? {
    if (text == null) return null
    return YEAR_RE.find(text)?.value?.toIntOrNull()
}

fun reconstructAbstract(inverted: JSONObject?): String {
    if (inverted == null || inverted.length() == 0) return ""
    val slots = ArrayList<String?>()
    for (key in inverted.keys()) {
        val positions = inverted.optJSONArray(key) ?: continue
        for (i in 0 until positions.length()) {
            val p = positions.opt(i)
            val idx = when (p) {
                is Number -> p.toInt()
                is String -> p.toIntOrNull()
                else -> null
            }
            if (idx != null) {
                while (slots.size <= idx) slots.add(null)
                slots[idx] = key
            }
        }
    }
    return slots.filterNotNull().joinToString(" ")
}

fun inferDesign(title: String, abs: String?, type: String?): String? {
    val t = "$title ${abs ?: ""} ${type ?: ""}".lowercase()
    return when {
        Regex("meta[- ]analysis").containsMatchIn(t) -> "meta-analysis"
        Regex("systematic review").containsMatchIn(t) -> "systematic review"
        Regex("randomized|rct\\b").containsMatchIn(t) -> "randomized trial"
        Regex("cross[- ]sectional").containsMatchIn(t) -> "cross-sectional"
        Regex("case[- ]control").containsMatchIn(t) -> "case-control"
        Regex("cohort|longitudinal|prospective|retrospective").containsMatchIn(t) -> "cohort"
        Regex("in vitro|laboratory").containsMatchIn(t) -> "in vitro"
        Regex("review").containsMatchIn(t) -> "review"
        Regex("questionnaire|survey").containsMatchIn(t) -> "survey"
        else -> type
    }
}

fun mentionsIndia(blob: String, codes: List<String>): Boolean =
    INDIA_RE.containsMatchIn(blob) || codes.contains("IN")

fun articleId(doi: String?, pmid: String?, title: String?): String =
    if (doi != null) "doi:${doi.lowercase()}"
    else if (pmid != null) "pmid:$pmid"
    else "t:${hash(normalizeTitle(title ?: ""))}"

private fun hash(s: String): String {
    var h = 0L
    for (ch in s) {
        h = 0x1FFFFFFFL and ((31L * h) + ch.code.toLong())
    }
    return h.toString(36)
}

// --- Paper model ---

data class Paper(
    val id: String? = null,
    val title: String,
    val year: Int? = null,
    val journal: String? = null,
    val authors: List<String> = emptyList(),
    val abstract: String? = null,
    val doi: String? = null,
    val pmid: String? = null,
    val pmcid: String? = null,
    val url: String? = null,
    val pdfUrl: String? = null,
    val citationCount: Int? = null,
    val articleType: String? = null,
    val studyDesign: String? = null,
    val isOpenAccess: Boolean = false,
    val peerReviewed: String = "unknown",
    val confidence: String = "moderate",
    val sources: List<String> = emptyList(),
    val countryCodes: List<String> = emptyList(),
    val mentionsIndia: Boolean = false,
)

// --- Source fetchers ---

private val PM_ID_RE = Regex("<PMID[^>]*>(\\d+)<")
private val ABSTRACT_TEXT_RE = Regex("<AbstractText[^>]*>([\\s\\S]*?)</AbstractText>")
private val DOI_COLON_SPACE_RE = Regex("^doi:\\s*", RegexOption.IGNORE_CASE)
private val NON_DIGIT_RE = Regex("\\D")
private val PMCID_RE = Regex("PMC\\d+")

/**
 * Use the LLM to expand a natural-language research query into structured
 * search strings optimised for each database. Returns null if LLM is unavailable
 * or fails — caller falls back to the raw query.
 */
suspend fun expandSearchQuery(subjectName: String, rawQuery: String): SearchQueries? {
    if (!LlmRuntime.ready) return null
    return try {
        val prompt = """
You are a medical librarian. Convert this dental research query into optimised search strings for different databases.

SUBJECT: $subjectName
RAW QUERY: $rawQuery

Return JSON with these fields:
{
  "pubmed": "Boolean query with MeSH terms AND free-text synonyms, using field tags like [MeSH Terms], [Title/Abstract]. Use AND/OR correctly. Keep under 300 chars.",
  "generic": "Space-separated keywords with synonyms, no Boolean operators. Good for OpenAlex, Crossref, Europe PMC. 8-12 key terms max.",
  "keywords": ["phrase1", "phrase2", "phrase3", "phrase4", "phrase5", "phrase6"]
}

RULES for pubmed field:
- Include MeSH terms where they exist (e.g., "Dental Students"[MeSH], "Blood Pressure"[MeSH])
- Add free-text synonyms with OR (e.g., "blood pressure" OR "hypertension" OR "BP")
- Use [Title/Abstract] for free-text terms
- Include the subject context (dental, oral, dentistry)
- Structure: (MeSH term) OR (free text synonyms) AND (MeSH term) OR (free text) ...

RULES for generic field:
- Include synonyms and related terms a researcher would use
- Include both US and UK spellings if relevant (e.g., "colour" OR "color")
- Include abbreviated and full forms (e.g., "BP" OR "blood pressure")
- 8-12 terms covering the concept broadly

RULES for keywords:
- Return 6 MEANINGFUL multi-word search phrases, NOT single words
- Each keyword should be 2-4 words that describe a specific aspect of the research
- Examples of GOOD keywords: "dental caries prevalence", "oral hygiene practices", "periodontal disease risk factors", "fluoride effectiveness children"
- Examples of BAD single words to NEVER return: "were", "colour", "they", "study", "data", "results", "patients", "high", "low"
- Every keyword must contain at least one domain-specific dental/medical term
- Each phrase must be something a researcher would actually type into a search engine

Return ONLY the JSON.
        """.trimIndent()

        val raw = LlmRuntime.chat(
            "You are a medical librarian. Output only valid JSON.",
            prompt,
        )
        val json = JSONObject(extractSearchJson(raw))
        SearchQueries(
            pubmed = json.optString("pubmed", rawQuery).ifEmpty { rawQuery },
            generic = json.optString("generic", rawQuery).ifEmpty { rawQuery },
            keywords = json.optJSONArray("keywords")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
        )
    } catch (e: Exception) {
        Log.e("SEARCH", "LLM query expansion failed: $e")
        null
    }
}

internal fun extractSearchJson(raw: String): String {
    val first = raw.indexOf('{')
    val last = raw.lastIndexOf('}')
    if (first >= 0 && last > first) return raw.substring(first, last + 1)
    return raw
}

data class SearchQueries(
    val pubmed: String,
    val generic: String,
    val keywords: List<String>,
)

suspend fun searchPubMed(query: String, yearFrom: Int, yearTo: Int): List<Paper> {
    val tokens = query.split(Regex("\\s+")).filter { it.length > 1 }.take(5).joinToString(" ")
    val term = "($tokens) AND ($yearFrom:$yearTo[dp])"

    val es = buildUrl(
        "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi",
        mapOf(
            "db" to "pubmed",
            "term" to term,
            "retmax" to "20",
            "retmode" to "json",
            "sort" to "relevance",
        ),
    )
    val esRes = fetchTimeout(es)
    if (esRes.statusCode != 200) throw Exception("PubMed esearch ${esRes.statusCode}")
    val esJson = JSONObject(esRes.body)
    val ids = esJson.optJSONObject("esearchresult")?.optJSONArray("idlist")?.stringList() ?: emptyList()
    if (ids.isEmpty()) return emptyList()

    val sum = buildUrl(
        "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi",
        mapOf(
            "db" to "pubmed",
            "id" to ids.joinToString(","),
            "retmode" to "json",
        ),
    )
    val sumRes = fetchTimeout(sum)
    if (sumRes.statusCode != 200) {
        throw Exception("PubMed esummary ${sumRes.statusCode}")
    }
    val sumJson = JSONObject(sumRes.body)
    val result = sumJson.optJSONObject("result")

    // abstracts via efetch XML
    val absMap = mutableMapOf<String, String>()
    try {
        val fetchUrl = buildUrl(
            "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi",
            mapOf(
                "db" to "pubmed",
                "id" to ids.take(15).joinToString(","),
                "retmode" to "xml",
            ),
        )
        val fRes = fetchTimeout(fetchUrl, timeoutMs = 15_000L)
        if (fRes.statusCode == 200) {
            val xml = fRes.body
            val blocks = xml.split("<PubmedArticle>").drop(1)
            for (b in blocks) {
                val pmidM = PM_ID_RE.find(b)
                val absM = ABSTRACT_TEXT_RE.find(b)
                if (pmidM != null && absM != null) {
                    absMap[pmidM.groupValues[1]] = decodeEntities(absM.groupValues[1])
                }
            }
        }
    } catch (_: Exception) {
    }

    return ids.map { id ->
        val r = result?.optJSONObject(id) ?: JSONObject()
        val authors = r.optJSONArray("authors")?.objList()
            ?.mapNotNull { it.str("name") }
            ?.take(12)
            ?: emptyList()
        val year = yearFromText(r.str("pubdate")) ?: yearFromText(r.str("epubdate"))
        val title = decodeEntities(r.str("title") ?: "Untitled")
        val abs = absMap[id]
        val doiStr = normalizeDoi((r.str("elocationid") ?: "").replaceFirst(DOI_COLON_SPACE_RE, ""))
        val blob = "$title ${abs ?: ""}"
        val pubTypes = r.optJSONArray("pubtype")?.stringList()
        Paper(
            id = null,
            title = title,
            year = year,
            journal = r.str("fulljournalname") ?: r.str("source"),
            authors = authors,
            abstract = abs,
            doi = doiStr,
            pmid = id,
            pmcid = null,
            url = "https://pubmed.ncbi.nlm.nih.gov/$id/",
            pdfUrl = null,
            citationCount = null,
            articleType = pubTypes?.firstOrNull(),
            studyDesign = inferDesign(title, abs, pubTypes?.joinToString(" ")),
            isOpenAccess = false,
            peerReviewed = "likely",
            confidence = "high",
            sources = listOf("pubmed"),
            countryCodes = emptyList(),
            mentionsIndia = mentionsIndia(blob, emptyList()),
        )
    }
}

suspend fun searchEuropePmc(query: String, yearFrom: Int, yearTo: Int): List<Paper> {
    val url = buildUrl(
        "https://www.ebi.ac.uk/europepmc/webservices/rest/search",
        mapOf(
            "query" to "$query AND (FIRST_PDATE:[$yearFrom-01-01 TO $yearTo-12-31])",
            "format" to "json",
            "pageSize" to "20",
            "resultType" to "core",
        ),
    )
    val res = fetchTimeout(url)
    if (res.statusCode != 200) throw Exception("Europe PMC ${res.statusCode}")
    val json = JSONObject(res.body)
    val rows = json.optJSONObject("resultList")?.optJSONArray("result") ?: return emptyList()
    return (0 until rows.length()).map { i ->
        val r = rows.optJSONObject(i) ?: JSONObject()
        val title = decodeEntities(r.str("title") ?: "Untitled")
        val abs = r.str("abstractText")?.let { decodeEntities(it) }
        val blob = "$title ${abs ?: ""}"
        val pubTypes = r.optJSONObject("pubTypeList")?.optJSONArray("pubType")?.stringList()
        Paper(
            id = null,
            title = title,
            year = if (r.has("pubYear") && !r.isNull("pubYear")) r.str("pubYear")?.toIntOrNull()
            else yearFromText(r.str("firstPublicationDate")),
            journal = r.str("journalTitle"),
            authors = (r.str("authorString") ?: "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(12),
            abstract = abs,
            doi = normalizeDoi(r.str("doi")),
            pmid = r.str("pmid"),
            pmcid = r.str("pmcid"),
            url = when {
                r.str("pmid") != null -> "https://europepmc.org/article/MED/${r.str("pmid")}"
                r.str("doi") != null -> "https://doi.org/${r.str("doi")}"
                else -> null
            },
            pdfUrl = null,
            citationCount = r.intVal("citedByCount"),
            articleType = pubTypes?.firstOrNull(),
            studyDesign = inferDesign(title, abs, pubTypes?.joinToString(" ")),
            isOpenAccess = r.str("isOpenAccess") == "Y" || r.str("pmcid") != null,
            peerReviewed = "likely",
            confidence = "high",
            sources = listOf("europepmc"),
            countryCodes = emptyList(),
            mentionsIndia = mentionsIndia(blob, emptyList()),
        )
    }
}

fun mapOpenAlex(w: JSONObject, source: String): Paper {
    val countries = mutableListOf<String>()
    val authorships = w.optJSONArray("authorships")
    if (authorships != null) {
        for (i in 0 until authorships.length()) {
            val a = authorships.optJSONObject(i) ?: continue
            val insts = a.optJSONArray("institutions") ?: continue
            for (j in 0 until insts.length()) {
                val cc = insts.optJSONObject(j)?.str("country_code")
                if (cc != null) countries.add(cc)
            }
        }
    }
    val title = decodeEntities(w.str("display_name") ?: w.str("title") ?: "Untitled")
    val abs = reconstructAbstract(w.optJSONObject("abstract_inverted_index"))
    val doi = normalizeDoi(w.str("doi"))
    val rawPmid = w.optJSONObject("ids")?.str("pmid")
    val pmid = rawPmid?.replace(NON_DIGIT_RE, "")
    val rawPmcid = w.optJSONObject("ids")?.str("pmcid")
    val pmcidM = rawPmcid?.uppercase()?.let { PMCID_RE.find(it) }
    val blob = "$title $abs"
    return Paper(
        id = null,
        title = title,
        year = w.intVal("publication_year"),
        journal = w.optJSONObject("primary_location")?.optJSONObject("source")?.str("display_name"),
        authors = authorships?.objList()
            ?.mapNotNull { it.optJSONObject("author")?.str("display_name") }
            ?.take(12)
            ?: emptyList(),
        abstract = abs.ifEmpty { null },
        doi = doi,
        pmid = pmid,
        pmcid = pmcidM?.value,
        url = w.optJSONObject("primary_location")?.str("landing_page_url")
            ?: doi?.let { "https://doi.org/$it" },
        pdfUrl = w.optJSONObject("open_access")?.str("oa_url")
            ?: w.optJSONObject("primary_location")?.str("pdf_url"),
        citationCount = w.intVal("cited_by_count"),
        articleType = w.str("type"),
        studyDesign = inferDesign(title, abs, w.str("type")),
        isOpenAccess = w.optJSONObject("open_access")?.optBoolean("is_oa") == true,
        peerReviewed = if (source == "scopus" || source == "wos") "likely" else "unknown",
        confidence = if (doi != null || pmid != null) "high" else "moderate",
        sources = listOf(source),
        countryCodes = countries,
        mentionsIndia = mentionsIndia(blob, countries),
    )
}

suspend fun searchOpenAlex(
    query: String,
    yearFrom: Int,
    yearTo: Int,
    issns: List<String>?,
    source: String,
): List<Paper> {
    val filters = mutableListOf(
        "from_publication_date:$yearFrom-01-01",
        "to_publication_date:$yearTo-12-31",
        "is_retracted:false",
    )
    if (!issns.isNullOrEmpty()) {
        val unique = issns.distinct().take(40).joinToString("|")
        filters.add("primary_location.source.issn:$unique")
    }
    val url = buildUrl(
        "https://api.openalex.org/works",
        mapOf(
            "search" to query,
            "filter" to filters.joinToString(","),
            "per_page" to if (issns != null) "15" else "20",
            "sort" to "relevance_score:desc",
            "mailto" to "research-desk@local",
        ),
    )
    val res = fetchTimeout(url, timeoutMs = 15_000L)
    if (res.statusCode != 200) throw Exception("OpenAlex ${res.statusCode}")
    val json = JSONObject(res.body)
    val results = json.optJSONArray("results") ?: return emptyList()
    return (0 until results.length()).mapNotNull { i ->
        results.optJSONObject(i)?.let { mapOpenAlex(it, source) }
    }
}

suspend fun searchCrossref(query: String, yearFrom: Int, yearTo: Int): List<Paper> {
    val url = buildUrl(
        "https://api.crossref.org/works",
        mapOf(
            "query" to query,
            "filter" to "from-pub-date:$yearFrom,until-pub-date:$yearTo,type:journal-article",
            "rows" to "15",
            "select" to "DOI,title,author,issued,container-title,abstract,URL,type,is-referenced-by-count",
        ),
    )
    val res = fetchTimeout(url, timeoutMs = 15_000L)
    if (res.statusCode != 200) throw Exception("Crossref ${res.statusCode}")
    val json = JSONObject(res.body)
    val items = json.optJSONObject("message")?.optJSONArray("items") ?: return emptyList()
    return (0 until items.length()).mapNotNull { i ->
        val item = items.optJSONObject(i) ?: return@mapNotNull null
        val title = decodeEntities(item.optJSONArray("title")?.strAt(0) ?: "Untitled")
        val abs = item.str("abstract")?.let { decodeEntities(it) }
        val authors = item.optJSONArray("author")?.objList()
            ?.mapNotNull { a ->
                listOfNotNull(a.str("given"), a.str("family")).joinToString(" ").ifEmpty { null }
            }
            ?.take(12)
            ?: emptyList()
        val blob = "$title ${abs ?: ""}"
        val doiRaw = item.str("DOI")
        Paper(
            id = null,
            title = title,
            year = item.optJSONObject("issued")?.optJSONArray("date-parts")?.optJSONArray(0)?.intAt(0),
            journal = item.optJSONArray("container-title")?.strAt(0),
            authors = authors,
            abstract = abs,
            doi = normalizeDoi(doiRaw),
            pmid = null,
            pmcid = null,
            url = item.str("URL") ?: doiRaw?.let { "https://doi.org/$it" },
            pdfUrl = null,
            citationCount = item.intVal("is-referenced-by-count"),
            articleType = item.str("type"),
            studyDesign = inferDesign(title, abs, item.str("type")),
            isOpenAccess = false,
            peerReviewed = "likely",
            confidence = "high",
            sources = listOf("crossref"),
            countryCodes = emptyList(),
            mentionsIndia = mentionsIndia(blob, emptyList()),
        )
    }
}

// --- Merge ---

private val AUTHORITATIVE_SOURCES = setOf("pubmed", "scopus", "wos")

fun mergeArticles(items: List<Paper>): List<Paper> {
    val byKey = LinkedHashMap<String, Paper>()
    for (a in items) {
        val k = when {
            a.doi != null -> "doi:${a.doi.lowercase()}"
            a.pmid != null -> "pmid:${a.pmid}"
            else -> "t:${normalizeTitle(a.title)}"
        }
        val prev = byKey[k]
        if (prev == null) {
            byKey[k] = a
        } else {
            val authoritative = prev.sources.any { it in AUTHORITATIVE_SOURCES } ||
                a.sources.any { it in AUTHORITATIVE_SOURCES }
            byKey[k] = prev.copy(
                year = prev.year ?: a.year,
                journal = prev.journal ?: a.journal,
                authors = prev.authors.ifEmpty { a.authors },
                abstract = prev.abstract ?: a.abstract,
                doi = prev.doi ?: a.doi,
                pmid = prev.pmid ?: a.pmid,
                pmcid = prev.pmcid ?: a.pmcid,
                url = prev.url ?: a.url,
                pdfUrl = prev.pdfUrl ?: a.pdfUrl,
                citationCount = maxOf(prev.citationCount ?: 0, a.citationCount ?: 0),
                articleType = prev.articleType ?: a.articleType,
                studyDesign = prev.studyDesign ?: a.studyDesign,
                isOpenAccess = prev.isOpenAccess || a.isOpenAccess,
                peerReviewed = if (authoritative) "likely" else prev.peerReviewed,
                confidence = if (authoritative) "high" else prev.confidence,
                sources = (prev.sources + a.sources).distinct(),
                countryCodes = (prev.countryCodes + a.countryCodes).distinct(),
                mentionsIndia = prev.mentionsIndia || a.mentionsIndia,
            )
        }
    }
    return byKey.values.map { a ->
        a.copy(id = articleId(a.doi, a.pmid, a.title))
    }
}

// --- Source status ---

data class SourceStatus(
    val id: String,
    val label: String,
    val ok: Boolean,
    val count: Int = 0,
    val note: String? = null,
    val latencyMs: Int = 0,
)

data class SearchMeta(
    val databases: List<String>,
    val searchDate: String,
    val period: String,
    val terms: String,
    val screened: Int,
    val included: Int,
    val notes: List<String>,
)

data class LiteratureResult(
    val papers: List<Paper>,
    val sources: List<SourceStatus>,
    val meta: SearchMeta,
)

// --- Main search ---

private data class SourceOutcome(
    val id: String,
    val label: String,
    val hits: List<Paper>,
    val status: SourceStatus,
)

private val DEFAULT_SOURCES = listOf("pubmed", "europepmc", "openalex", "scopus", "wos", "crossref")

private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

suspend fun runLiteratureSearch(
    query: String,
    yearFrom: Int = 2021,
    yearTo: Int = 2026,
    sources: List<String>? = null,
    searchQueries: SearchQueries? = null,
): LiteratureResult = coroutineScope {
    val q = query.trim()
    if (q.isEmpty()) {
        Log.d("SEARCH", "Empty query, returning early")
        return@coroutineScope LiteratureResult(
            papers = emptyList(),
            sources = emptyList(),
            meta = SearchMeta(
                databases = emptyList(),
                searchDate = today(),
                period = "$yearFrom\u2013$yearTo",
                terms = "",
                screened = 0,
                included = 0,
                notes = listOf("Enter a search query to retrieve papers."),
            ),
        )
    }
    val truncated = q.take(400)
    Log.d("SEARCH", "=== SEARCH START ===")
    Log.d("SEARCH", "Query: \"$truncated\"")
    Log.d("SEARCH", "Period: $yearFrom\u2013$yearTo")
    val enabled = (sources?.takeIf { it.isNotEmpty() } ?: DEFAULT_SOURCES).toSet()
    Log.d("SEARCH", "Sources: ${enabled.joinToString(", ")}")

    fun timed(id: String, label: String, fn: suspend () -> List<Paper>): Deferred<SourceOutcome> = async {
        val t0 = System.currentTimeMillis()
        try {
            val hits = fn()
            SourceOutcome(
                id = id,
                label = label,
                hits = hits,
                status = SourceStatus(
                    id = id,
                    label = label,
                    ok = true,
                    count = hits.size,
                    latencyMs = (System.currentTimeMillis() - t0).toInt(),
                ),
            )
        } catch (e: Exception) {
            SourceOutcome(
                id = id,
                label = label,
                hits = emptyList(),
                status = SourceStatus(
                    id = id,
                    label = label,
                    ok = false,
                    count = 0,
                    note = e.toString().take(180),
                    latencyMs = (System.currentTimeMillis() - t0).toInt(),
                ),
            )
        }
    }

    val jobs = mutableListOf<Deferred<SourceOutcome>>()
    val pmQuery = searchQueries?.pubmed ?: truncated
    val genericQuery = searchQueries?.generic ?: truncated
    if ("pubmed" in enabled) {
        jobs.add(timed("pubmed", "PubMed") { searchPubMed(pmQuery, yearFrom, yearTo) })
    }
    if ("europepmc" in enabled) {
        jobs.add(timed("europepmc", "Europe PMC") { searchEuropePmc(genericQuery, yearFrom, yearTo) })
    }
    if ("openalex" in enabled) {
        jobs.add(timed("openalex", "OpenAlex") { searchOpenAlex(genericQuery, yearFrom, yearTo, null, "openalex") })
    }
    if ("scopus" in enabled) {
        jobs.add(timed("scopus", "Scopus") { searchOpenAlex(genericQuery, yearFrom, yearTo, SCOPUS_ISSNS, "scopus") })
    }
    if ("wos" in enabled) {
        jobs.add(timed("wos", "Web of Science") { searchOpenAlex(genericQuery, yearFrom, yearTo, WOS_ISSNS, "wos") })
    }
    if ("crossref" in enabled) {
        jobs.add(timed("crossref", "Crossref") { searchCrossref(genericQuery, yearFrom, yearTo) })
    }

    val settled = jobs.awaitAll()
    val hits = settled.flatMap { it.hits }
    val sourceStatuses = settled.map { it.status }
    for (s in sourceStatuses) {
        Log.d(
            "SEARCH",
            "${s.label}: ${if (s.ok) "${s.count} papers" else "FAILED"} (${s.latencyMs}ms)${s.note?.let { " - $it" } ?: ""}",
        )
    }
    Log.d("SEARCH", "Total raw hits: ${hits.size}")
    val merged = mergeArticles(hits)
    Log.d("SEARCH", "After merge: ${merged.size} unique papers")
    val sorted = merged.sortedWith(
        compareByDescending<Paper> { it.confidence == "high" }.thenByDescending { it.year ?: 0 },
    )

    LiteratureResult(
        papers = sorted,
        sources = sourceStatuses,
        meta = SearchMeta(
            databases = sourceStatuses.filter { it.ok }.map { it.label },
            searchDate = today(),
            period = "$yearFrom\u2013$yearTo",
            terms = if (searchQueries != null) "$q [expanded]" else q,
            screened = merged.size,
            included = merged.size,
            notes = buildList {
                add("Targeted retrieval, not an exhaustive systematic review.")
                if (searchQueries != null) {
                    add("Search queries expanded with MeSH terms and synonyms via AI.")
                }
                add("Scopus and Web of Science: internationally indexed journals via ISSN match (OpenAlex).")
                add("Engineering and biomaterials journals are included in the Scopus/WoS ISSN set.")
                add("No matching paper does not prove that none exists.")
            },
        ),
    )
}

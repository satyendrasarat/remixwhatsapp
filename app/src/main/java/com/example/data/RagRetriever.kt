package com.example.data

import kotlin.math.ln

data class RetrievedChunk(
    val chunk: KnowledgeChunkEntity,
    val score: Double
) {
    val citationLabel: String
        get() = "${chunk.documentName} • p.${chunk.pageNumber} (${chunk.sectionTitle})"
}

/**
 * Local lexical BM25-inspired RAG retriever operating over indexed PDF chunks stored in Room.
 */
object RagRetriever {

    private val stopWords = setOf(
        "the", "is", "at", "which", "on", "a", "an", "and", "or", "for", "to", "in", "of",
        "with", "my", "me", "i", "you", "your", "we", "our", "it", "this", "that", "can",
        "do", "does", "are", "be", "was", "were", "have", "has", "had", "what", "when",
        "where", "who", "why", "how", "please", "hi", "hello", "hey", "thanks", "thank"
    )

    fun retrieveTopChunks(
        query: String,
        allChunks: List<KnowledgeChunkEntity>,
        topK: Int = 4
    ): List<RetrievedChunk> {
        if (allChunks.isEmpty() || topK <= 0) return emptyList()

        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) {
            return allChunks.take(topK).map { RetrievedChunk(it, 0.1) }
        }

        val docCount = allChunks.size.toDouble()
        val chunkTokenLists = allChunks.map { chunk ->
            val combined = "${chunk.sectionTitle} ${chunk.keywords} ${chunk.content}"
            tokenize(combined)
        }
        val avgDocLen = chunkTokenLists.map { it.size }.average().coerceAtLeast(1.0)

        // Document frequency per query term
        val dfMap = mutableMapOf<String, Int>()
        for (term in queryTokens.toSet()) {
            dfMap[term] = chunkTokenLists.count { tokens -> tokens.any { tokenMatches(it, term) } }
        }

        val k1 = 1.5
        val b = 0.75

        val scored = allChunks.mapIndexed { index, chunk ->
            val tokens = chunkTokenLists[index]
            val docLen = tokens.size.toDouble().coerceAtLeast(1.0)
            val titleTokens = tokenize(chunk.sectionTitle)
            val keywordTokens = tokenize(chunk.keywords)

            var score = 0.0
            for (term in queryTokens.toSet()) {
                val df = dfMap[term] ?: 0
                if (df == 0) continue

                val tf = tokens.count { tokenMatches(it, term) }.toDouble()
                if (tf <= 0.0) continue

                val idf = ln(((docCount - df + 0.5) / (df + 0.5)) + 1.0)
                val bm25 = idf * ((tf * (k1 + 1.0)) / (tf + k1 * (1.0 - b + b * (docLen / avgDocLen))))
                score += bm25

                // Boost matches in section title or extracted keywords
                if (titleTokens.any { tokenMatches(it, term) }) score += 1.4
                if (keywordTokens.any { tokenMatches(it, term) }) score += 1.1
            }

            // Phrase overlap bonus
            val normalizedQuery = query.lowercase().trim()
            if (normalizedQuery.length >= 6 && chunk.content.lowercase().contains(normalizedQuery)) {
                score += 3.0
            }

            RetrievedChunk(chunk = chunk, score = score)
        }

        val positiveMatches = scored
            .filter { it.score > 0.0 }
            .sortedByDescending { it.score }
            .take(topK)

        // If no exact lexical hits (e.g., phrasing uses synonyms), include top chunks so Gemini still sees context
        return positiveMatches.ifEmpty {
            allChunks.take(topK.coerceAtMost(3)).map { RetrievedChunk(it, 0.05) }
        }
    }

    private fun tokenize(text: String): List<String> {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 && it !in stopWords }
    }

    private fun tokenMatches(token: String, queryTerm: String): Boolean {
        if (token == queryTerm) return true
        if (queryTerm.length >= 4 && (token.startsWith(queryTerm) || queryTerm.startsWith(token))) {
            return true
        }
        return false
    }
}

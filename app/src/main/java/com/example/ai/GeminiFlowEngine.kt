package com.example.ai

import android.util.Log
import com.example.data.AssistantTone
import com.example.data.ChatMessageEntity
import com.example.data.FlowConfigEntity
import com.example.data.MessageSender
import com.example.data.RetrievedChunk
import com.example.data.SentimentLevel
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

private const val TAG = "GeminiFlowEngine"
private const val FAST_FLOW_MODEL = "gemini-3.5-flash-lite"
private const val PDF_EXTRACTOR_MODEL = "gemini-3.8-flash"
private const val PRIMARY_TIMEOUT_MS = 35_000L
private const val FALLBACK_TIMEOUT_MS = 35_000L
private const val RETRY_BACKOFF_MS = 1_800L

data class ExtractedPdfSection(
    val sectionTitle: String,
    val pageNumber: Int,
    val content: String,
    val keywords: String
)

data class PdfExtractionResult(
    val summary: String,
    val sections: List<ExtractedPdfSection>
)

data class FlowDecisionResult(
    val sentiment: SentimentLevel,
    val frustrationScore: Int,
    val wantsHumanAgent: Boolean,
    val escalationReason: String,
    val replyText: String,
    val citedSources: List<String>,
    val groundedInKnowledge: Boolean,
    val confidenceScore: Int
)

/**
 * Powered by Firebase AI Logic (`Firebase.ai`).
 * Uses `gemini-3.5-flash-lite` with minimal thinking for low-latency real-time WhatsApp
 * sentiment guardrails and RAG replies, and `gemini-3.8-flash` for multimodal PDF indexing,
 * with automatic cross-model timeout resilience.
 */
class GeminiFlowEngine {

    private val pdfExtractionSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "1-2 sentence summary of the PDF."),
            "sections" to Schema.array(
                description = "Semantic knowledge chunks extracted from the PDF.",
                items = Schema.obj(
                    properties = mapOf(
                        "sectionTitle" to Schema.string(description = "Short topic heading."),
                        "pageNumber" to Schema.integer(description = "1-based page number."),
                        "content" to Schema.string(description = "Factual text preserving prices, rules, and dates."),
                        "keywords" to Schema.string(description = "Comma-separated search keywords.")
                    )
                )
            )
        )
    )

    private val flowDecisionSchema = Schema.obj(
        properties = mapOf(
            "sentiment" to Schema.enumeration(
                values = listOf("POSITIVE", "NEUTRAL", "CONCERNED", "FRUSTRATED", "ANGRY"),
                description = "Customer sentiment."
            ),
            "frustrationScore" to Schema.integer(
                description = "Frustration score 0 (calm) to 100 (very angry)."
            ),
            "wantsHumanAgent" to Schema.boolean(
                description = "True if user requests a human, agent, or representative."
            ),
            "escalationReason" to Schema.string(
                description = "Short escalation reason or empty string."
            ),
            "replyText" to Schema.string(
                description = "Concise WhatsApp reply grounded in the PDF sources."
            ),
            "citedSources" to Schema.array(
                items = Schema.string(),
                description = "Source labels used from PDF chunks."
            ),
            "groundedInKnowledge" to Schema.boolean(
                description = "True if PDF sources answered the query."
            ),
            "confidenceScore" to Schema.integer(
                description = "Confidence 0-100."
            )
        )
    )

    private val pdfExtractorConfig by lazy {
        generationConfig {
            responseMimeType = "application/json"
            responseSchema = pdfExtractionSchema
            temperature = 0.1f
            maxOutputTokens = 4096
            thinkingConfig = thinkingConfig {
                thinkingLevel = ThinkingLevel.LOW
            }
        }
    }

    private val flowAssistantConfig by lazy {
        generationConfig {
            responseMimeType = "application/json"
            responseSchema = flowDecisionSchema
            temperature = 0.2f
            maxOutputTokens = 1024
            thinkingConfig = thinkingConfig {
                thinkingLevel = ThinkingLevel.MINIMAL
            }
        }
    }

    private val pdfExtractorPrimary by lazy {
        Firebase.ai.generativeModel(
            modelName = PDF_EXTRACTOR_MODEL,
            generationConfig = pdfExtractorConfig
        )
    }

    private val pdfExtractorFallback by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_FLOW_MODEL,
            generationConfig = pdfExtractorConfig
        )
    }

    private val flowAssistantPrimary by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_FLOW_MODEL,
            generationConfig = flowAssistantConfig
        )
    }

    private val flowAssistantFallback by lazy {
        Firebase.ai.generativeModel(
            modelName = PDF_EXTRACTOR_MODEL,
            generationConfig = flowAssistantConfig
        )
    }

    /**
     * Reads a PDF document natively with Gemini multimodal input and decomposes it into
     * clean, searchable RAG knowledge chunks.
     */
    suspend fun extractPdfKnowledge(
        pdfBytes: ByteArray,
        fileName: String
    ): PdfExtractionResult = withContext(Dispatchers.IO) {
        val requestContent = content {
            inlineData(pdfBytes, "application/pdf")
            text(
                """
                Extract all factual knowledge from "$fileName" into 1 to 12 concise semantic sections for WhatsApp RAG support.
                Preserve exact numbers, prices, deadlines, and policy rules. Provide comma-separated search keywords per section.
                """.trimIndent()
            )
        }

        try {
            val response = generateWithResilience(
                operationLabel = "PDF indexing ($fileName)",
                primary = pdfExtractorPrimary,
                fallback = pdfExtractorFallback
            ) { model ->
                model.generateContent(requestContent)
            }

            val rawJson = response.text
                ?: throw IllegalStateException("Gemini returned an empty response while indexing $fileName.")

            val root = JSONObject(rawJson)
            val summary = root.optString("summary", "Indexed PDF document: $fileName").trim()
            val sectionsArray = root.optJSONArray("sections")
            val sections = buildList {
                if (sectionsArray != null) {
                    for (i in 0 until sectionsArray.length()) {
                        val obj = sectionsArray.optJSONObject(i) ?: continue
                        val contentText = obj.optString("content").trim()
                        if (contentText.isBlank()) continue
                        add(
                            ExtractedPdfSection(
                                sectionTitle = obj.optString("sectionTitle", "Section ${i + 1}").trim(),
                                pageNumber = obj.optInt("pageNumber", 1).coerceAtLeast(1),
                                content = contentText,
                                keywords = obj.optString("keywords", "").trim()
                            )
                        )
                    }
                }
            }

            if (sections.isEmpty()) {
                throw IllegalStateException("No readable text sections could be extracted from $fileName.")
            }

            PdfExtractionResult(summary = summary, sections = sections)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "PDF indexing timed out for $fileName", e)
            throw IllegalStateException("PDF indexing timed out. Please check your connection and try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "PDF extraction failed for $fileName", e)
            throw IllegalStateException(e.localizedMessage ?: "Failed to index PDF with Gemini.", e)
        }
    }

    /**
     * Evaluates customer sentiment, live-agent intent guardrails, and composes a RAG-grounded
     * WhatsApp reply using top retrieved PDF chunks and recent chat context.
     */
    suspend fun analyzeAndComposeReply(
        contactName: String,
        incomingMessage: String,
        recentHistory: List<ChatMessageEntity>,
        retrievedChunks: List<RetrievedChunk>,
        config: FlowConfigEntity
    ): FlowDecisionResult = withContext(Dispatchers.IO) {
        val tone = AssistantTone.entries.firstOrNull { it.name == config.tone }
            ?: AssistantTone.HELPFUL_WARM

        val historyBlock = if (recentHistory.isEmpty()) {
            "None."
        } else {
            recentHistory.take(6).reversed().joinToString("\n") { msg ->
                val role = when (msg.senderType) {
                    MessageSender.CUSTOMER.name -> "Customer"
                    MessageSender.AI_BOT.name -> "Bot"
                    MessageSender.HUMAN_AGENT.name -> "Agent"
                    else -> "System"
                }
                "$role: ${msg.content.take(240)}"
            }
        }

        val knowledgeBlock = if (retrievedChunks.isEmpty()) {
            "NONE"
        } else {
            retrievedChunks.mapIndexed { idx, item ->
                "[SOURCE ${idx + 1}: ${item.citationLabel}]\n${item.chunk.content}"
            }.joinToString("\n\n")
        }

        val groundingRule = if (config.strictRagOnly) {
            "STRICT RAG: Only state facts from SOURCES. If missing, set groundedInKnowledge=false, say you don't have that on file, and offer a human agent."
        } else {
            "Use SOURCES as primary truth; handle greetings warmly."
        }

        val prompt = """
            WhatsApp support AI with safety guardrails.
            Tone: ${tone.instruction} ${config.customSystemPrompt}
            Rules:
            - wantsHumanAgent=true if user asks for a human/agent/representative/manager.
            - frustrationScore 0-100 (0-25 calm, 26-55 confused/concerned, 56-75 frustrated/impatient, 76-100 angry/furious/dispute).
            - $groundingRule
            - Keep replyText concise for WhatsApp (no markdown headers). Cite used source labels in citedSources.

            HISTORY:
            $historyBlock

            SOURCES:
            $knowledgeBlock

            NEW MESSAGE ($contactName):
            "$incomingMessage"
        """.trimIndent()

        try {
            val response = generateWithResilience(
                operationLabel = "Flow analysis ($contactName)",
                primary = flowAssistantPrimary,
                fallback = flowAssistantFallback
            ) { model ->
                model.generateContent(prompt)
            }

            val rawJson = response.text
                ?: throw IllegalStateException("Gemini returned an empty response.")

            val json = JSONObject(rawJson)
            val sentiment = SentimentLevel.fromRaw(json.optString("sentiment", "NEUTRAL"))
            val frustrationScore = json.optInt("frustrationScore", 0).coerceIn(0, 100)
            val wantsHumanAgent = json.optBoolean("wantsHumanAgent", false)
            val escalationReason = json.optString("escalationReason", "").trim()
            val replyText = json.optString("replyText", "").trim()
            val groundedInKnowledge = json.optBoolean("groundedInKnowledge", retrievedChunks.isNotEmpty())
            val confidenceScore = json.optInt("confidenceScore", 80).coerceIn(0, 100)

            val citedArray = json.optJSONArray("citedSources")
            val citedSources = buildList {
                if (citedArray != null) {
                    for (i in 0 until citedArray.length()) {
                        val src = citedArray.optString(i).trim()
                        if (src.isNotBlank()) add(src)
                    }
                }
            }

            FlowDecisionResult(
                sentiment = sentiment,
                frustrationScore = frustrationScore,
                wantsHumanAgent = wantsHumanAgent,
                escalationReason = escalationReason,
                replyText = replyText,
                citedSources = citedSources,
                groundedInKnowledge = groundedInKnowledge,
                confidenceScore = confidenceScore
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Flow analysis timed out on both primary and fallback models", e)
            throw IllegalStateException("Gemini request timed out. Please check your internet connection and try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Flow analysis failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Gemini flow processing failed.", e)
        }
    }

    /**
     * Runs a Gemini request with automatic cross-model fallback and brief backoff retry
     * when the primary model hits a temporary spike in demand (HTTP 503/429) or timeout.
     */
    private suspend fun generateWithResilience(
        operationLabel: String,
        primary: GenerativeModel,
        fallback: GenerativeModel,
        invoke: suspend (GenerativeModel) -> GenerateContentResponse
    ): GenerateContentResponse {
        var lastError: Exception? = null

        val attempts: List<Pair<GenerativeModel, Long>> = listOf(
            primary to PRIMARY_TIMEOUT_MS,
            fallback to FALLBACK_TIMEOUT_MS,
            fallback to FALLBACK_TIMEOUT_MS
        )

        for ((index, attempt) in attempts.withIndex()) {
            val (model, timeoutMs) = attempt
            if (index == 2) {
                delay(RETRY_BACKOFF_MS)
            }
            try {
                return withTimeout(timeoutMs) {
                    invoke(model)
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "$operationLabel timed out on attempt ${index + 1}/${attempts.size}", e)
                lastError = e
            } catch (e: CancellationException) {
                // Always preserve cooperative coroutine cancellation
                throw e
            } catch (e: Exception) {
                if (!isTransientModelError(e) || index == attempts.lastIndex) {
                    throw e
                }
                Log.w(
                    TAG,
                    "$operationLabel hit transient demand/server error on attempt ${index + 1}/${attempts.size}, switching/retrying: ${e.localizedMessage}",
                    e
                )
                lastError = e
            }
        }

        throw lastError ?: IllegalStateException("$operationLabel failed after fallback retries.")
    }

    private fun isTransientModelError(error: Throwable): Boolean {
        val message = buildString {
            var current: Throwable? = error
            while (current != null) {
                append(' ')
                append(current.message.orEmpty())
                current = current.cause
            }
        }.lowercase(Locale.ROOT)

        return message.contains("high demand") ||
            message.contains("503") ||
            message.contains("429") ||
            message.contains("unavailable") ||
            message.contains("overloaded") ||
            message.contains("resource_exhausted") ||
            message.contains("temporarily") ||
            message.contains("try again") ||
            message.contains("deadline") ||
            message.contains("rate limit")
    }
}

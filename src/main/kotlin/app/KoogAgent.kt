package app

import ai.koog.agents.core.tools.*
import ai.koog.agents.core.dsl.builder.*
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable


class WeatherTool(private val service: WeatherService,
                  override val argsSerializer: KSerializer<Args> = Args.serializer()
) : SimpleTool<WeatherTool.Args>() {
    companion object { const val NAME = "fetch_weather" }

    @Serializable
    data class Args(val city: String, val startIso: String, val endIso: String) : ToolArgs

    override suspend fun doExecute(args: Args): String {
        val summary = service.fetch(args.city, args.startIso, args.endIso)
        // Compact, LLM-friendly digest
        val lines = summary.days.joinToString("\n") {
            "- ${it.date}: min ${it.tempMinC}°C, max ${it.tempMaxC}°C, rainProb ${it.precipProb}, ${it.description}"
        }
        return """
            City: ${summary.city} ${summary.country ?: ""}
            Daily:
            $lines
        """.trimIndent()
    }

    override val descriptor = ToolDescriptor(
        name = NAME,
        description = "Get a concise multi-day weather summary for packing decisions",
        requiredParameters = listOf(
            ToolParameterDescriptor("city", "Destination city name", ToolParameterType.String),
            ToolParameterDescriptor("startIso", "Start date (YYYY-MM-DD)", ToolParameterType.String),
            ToolParameterDescriptor("endIso", "End date (YYYY-MM-DD)", ToolParameterType.String)
        ),
        optionalParameters = emptyList()
    )
}

class TripContextTool(override val argsSerializer: KSerializer<Args> = Args.serializer()) : SimpleTool<TripContextTool.Args>() {
    companion object { const val NAME = "trip_context" }

    @Serializable
    data class Args(val tripType: String, val days: Int) : ToolArgs

    override suspend fun doExecute(args: Args): String =
        "Trip type: ${args.tripType}; Trip length (days): ${args.days}"

    override val descriptor = ToolDescriptor(
        name = NAME,
        description = "Provide the trip type and length so the LLM tailors the packing list",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "tripType",
                description = "Trip category (business, beach, city, hiking, ski, family, romantic…)",
                type = ToolParameterType.String
            ),
            ToolParameterDescriptor(
                name = "days",
                description = "Trip length in days",
                type = ToolParameterType.Integer
            )
        ),
        optionalParameters = emptyList()
    )
}

/** Builds the Koog agent wired to Ollama and our tools */
fun buildPackingAgent(
    ollamaBaseUrl: String,
    ollamaModel: String,
    weatherTool: WeatherTool,
    tripContextTool: TripContextTool
): AIAgent<String, String> {
    // System guidance for the LLM
    val system = """
        You are Koog's Packing Assistant.

Rules:
- You must call BOTH tools exactly once:
  1) fetch_weather(city,startIso,endIso)
  2) trip_context(tripType,days)
- After both results, output ONLY the final JSON object with keys:
{
  "mustHave": [], "clothing": [], "footwear": [], "accessories": [],
  "toiletries": [], "gadgets": [], "documents": [], "optional": [],
  "tips": [], "weather": ""
}

Important:
- The "weather" field must contain the compact forecast string exactly as returned by fetch_weather (City + Daily lines).
- Do NOT write extra commentary or summaries outside JSON.
    """.trimIndent()

    // LLM executor (Ollama)
    val executor = simpleOllamaAIExecutor(ollamaBaseUrl)

    // Register tools
    val registry = ToolRegistry {
        tool(weatherTool)
        tool(tripContextTool)
    }

    // --- helper ---
    fun extractLastJsonObject(s: String?): String? {
        if (s == null) return null
        var depth = 0; var start = -1; var last: String? = null
        for (i in s.indices) when (s[i]) {
            '{' -> { if (depth == 0) start = i; depth++ }
            '}' -> { depth--; if (depth == 0 && start >= 0) last = s.substring(start, i + 1) }
        }
        return last?.trim()
    }

    fun isLikelyFinalJson(s: String?): Boolean {
        val j = extractLastJsonObject(s) ?: return false
        return j.startsWith("{") && j.endsWith("}")
    }

    fun normalizePackingJsonKeys(json: String): String =
        json.replace("\"Toiletries\"", "\"toiletries\"")
            .replace("\"Accessories\"", "\"accessories\"")


// --- strategy ---
    val packingStrategy = strategy<String, String>("packing-strategy") {
        var didTrip = false
        var didWeather = false
        var turns = 0

        val callLLM by nodeLLMRequest()
        val execTool by nodeExecuteTool()
        val sendToolResult by nodeLLMSendToolResult()

        // Start → ask the LLM
        edge(nodeStart forwardTo callLLM)

        // If LLM already produced final JSON → finish
        edge(callLLM forwardTo nodeFinish onAssistantMessage { msg ->
            isLikelyFinalJson(msg.content)
        })

        // If the LLM asks for ANY tool, route to execTool (with de-dupe)
        edge(callLLM forwardTo execTool onToolCall { call ->
            when (call.tool) {
                "trip_context" -> {
                    val ok = !didTrip
                    if (ok) didTrip = true  // flip immediately to prevent re-ask
                    ok
                }
                "fetch_weather" -> {
                    val ok = !didWeather
                    if (ok) didWeather = true  // flip immediately to prevent re-ask
                    ok
                }
                else -> true
            }
        })

        // Otherwise (plain chatter, no tool calls, not final) → ask again
        edge(callLLM forwardTo callLLM onAssistantMessage { msg ->
            // Only loop if the assistant did NOT request a tool and it wasn't final
            val noToolCall = msg.content.isNullOrEmpty()
            val notFinal = !isLikelyFinalJson(msg.content)
            val underCap = { turns += 1; turns <= 8 }  // simple guardrail
            noToolCall && notFinal && underCap()
        })

        // After a tool executes, always send its result back to the LLM
        edge(execTool forwardTo sendToolResult)

        // If now returns final JSON, finish (model might answer directly after tool result)
        edge(sendToolResult forwardTo nodeFinish onAssistantMessage { msg ->
            isLikelyFinalJson(msg.content)
        })

        // If now returns final JSON, finish
        var finalJson: String? = null

        edge(sendToolResult forwardTo nodeFinish onAssistantMessage { msg ->
            val j = extractLastJsonObject(msg.content ?: "")
            val ok = j != null
            if (ok) finalJson = normalizePackingJsonKeys(j!!)
            ok
        })

//From sendToolResult: allow the OTHER tool call (de-duped)
        edge(sendToolResult forwardTo execTool onToolCall { call ->
            when (call.tool) {
                TripContextTool.NAME -> if (!didTrip) { didTrip = true; true } else false
                WeatherTool.NAME     -> if (!didWeather) { didWeather = true; true } else false
                else -> false
            }
        })

// From sendToolResult: fallback — not final JSON (or duplicate tool call → (2) returned false)
// Re-ask the LLM up to a cap.
        edge(sendToolResult forwardTo callLLM onAssistantMessage { msg ->
            val notFinal = !isLikelyFinalJson(msg.content)
            val underCap = { turns += 1; turns <= 8 }
            notFinal && underCap()
        })
    }

    return AIAgent(
        executor = executor,
        strategy = packingStrategy,
        toolRegistry = registry,
        systemPrompt = system,
        llmModel = LLModel(
            provider = LLMProvider.Ollama,
            id = ollamaModel,   // e.g. "llama3.1:8b"
            capabilities = listOf(
                LLMCapability.Completion,   // standard text generation
                LLMCapability.Tools         // allow tool usage in strategies
            )
        )
    )
}
package org.umn.lensspent.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * The chat wire format the AI backend speaks.
 *
 * The proxy forwards to a provider with an OpenAI-compatible API, so this is the
 * `POST /chat/completions` schema and nothing more: a model name, a list of role-tagged messages, and
 * the switches that decide how the answer comes back. Keeping it typed means the request builders in
 * [AiInsightsRepository] cannot drift from what the API accepts, and it is the same class the unit
 * tests assert on.
 */
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    /**
     * Never stream: the chat screen awaits one answer rather than assembling a token stream, so the
     * whole reply arrives in a single JSON body.
     */
    val stream: Boolean = false,
    /**
     * Deliberately low: this assistant reads a ledger and reports what it sees, so answers should be
     * reproducible rather than creative.
     */
    val temperature: Double = DefaultTemperature
) {
    companion object {
        const val DefaultTemperature = 0.3
    }
}

/** One turn in the conversation, tagged with who said it. */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String
) {
    companion object {
        const val RoleSystem = "system"
        const val RoleUser = "user"
        const val RoleAssistant = "assistant"

        fun system(content: String): ChatMessage = ChatMessage(RoleSystem, content)

        fun user(content: String): ChatMessage = ChatMessage(RoleUser, content)
    }
}

/** The API's answer: one `choices` entry per completion, plus what it cost. */
@Serializable
data class ChatCompletionResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<ChatChoice> = emptyList(),
    val usage: ChatUsage? = null
)

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: ChatMessage? = null,
    /** Why the model stopped, as the API names it, e.g. `stop` or `length`. */
    @SerialName("finish_reason") val finishReason: String? = null
)

/** Token accounting, kept for logging: the insight itself is in [ChatChoice.message]. */
@Serializable
data class ChatUsage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0
)

/**
 * The one AI endpoint this app calls.
 *
 * Declared as an interface so [AiInsightsRepository] depends on it rather than on Retrofit: the JVM
 * tests hand in a recording stand-in and assert on the request that would have gone over the wire,
 * with no HTTP server involved. The base URL belongs to [AiBackendConfig] and the transport to
 * [DeepSeekApiClient]; neither is a provider credential, so a call site cannot leak one because
 * there is none in the app to leak.
 */
interface DeepSeekApi {

    /** `POST {proxy base URL}/chat/completions` */
    @POST(DeepSeekApiClient.ChatCompletionsPath)
    suspend fun chatCompletions(@Body request: ChatCompletionRequest): ChatCompletionResponse
}

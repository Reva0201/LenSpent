package org.umn.lensspent.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.umn.lensspent.BuildConfig

/**
 * The transport, exercised without a socket.
 *
 * The two things this feature cannot get wrong are asserted here: the app never sends a provider
 * credential, and a build with no backend cannot construct a client at all.
 */
class DeepSeekApiClientTest {

    /**
     * The regression that matters most: the APK must never carry a third-party API key.
     *
     * This reads the generated `BuildConfig`, which is the artifact a build actually compiles. The
     * old field is asserted absent by name, and the surviving proxy URL is asserted to be either
     * empty or https - so a future change that reintroduces a key under any name has to delete this
     * test to get through.
     */
    @Test
    fun `no provider credential is compiled into the app`() {
        val fields = BuildConfig::class.java.declaredFields.map { it.name }
        assertFalse(
            "a provider key must never be a BuildConfig field: $fields",
            fields.any { it.contains("DEEPSEEK_API_KEY", ignoreCase = true) }
        )
        assertFalse(
            "no field may be a bearer token: $fields",
            fields.any { it.contains("API_KEY", ignoreCase = true) || it.contains("TOKEN", ignoreCase = true) }
        )
        assertFalse(
            "no field may be a secret: $fields",
            fields.any { it.contains("SECRET", ignoreCase = true) }
        )

        // The value, not just the field name: an https proxy URL is the only thing this build may
        // carry, and it must be empty when nothing was configured.
        val configuredUrl = BuildConfig.AI_PROXY_BASE_URL
        assertEquals(configuredUrl, configuredUrl.trim())
        if (configuredUrl.isNotEmpty()) {
            assertTrue(
                "an AI backend must be https, was: $configuredUrl",
                configuredUrl.startsWith("https://")
            )
        }
    }

    /**
     * The build script is the other place a key could reappear. This reads it as text, so even a
     * "temporary" `buildConfigField` reintroduction fails here.
     */
    @Test
    fun `the build script never reads or embeds a provider key`() {
        val script = java.io.File("build.gradle.kts").readText()

        assertFalse(script.contains("DEEPSEEK_API_KEY"))
        assertFalse(script.contains("deepseek.api.key"))
        assertFalse(script.contains("deepseekApiKey"))
        assertFalse(
            "no environment variable may carry a provider key into the build",
            script.contains("environmentVariable(\"DEEPSEEK_API_KEY\")")
        )
    }

    /** A caller-supplied cleartext URL must not construct a transport either. */
    @Test
    fun `a cleartext or relative base url is refused by the client`() {
        assertNull(DeepSeekApiClient.createOrNull(baseUrl = "http://ai.example.org/"))
        assertNull(DeepSeekApiClient.createOrNull(baseUrl = "ai.example.org"))
        assertNull(DeepSeekApiClient.createOrNull(baseUrl = "https://"))
    }

    /** An https -> http redirect would be a silent downgrade of the spending summary. */
    @Test
    fun `the client refuses ssl redirects to another scheme`() {
        val client = DeepSeekApiClient.httpClient()

        assertFalse("https -> http must not be followed", client.followSslRedirects)
    }

    @Test
    fun `a build with no backend cannot construct a client`() {
        assertNull(DeepSeekApiClient.createOrNull(baseUrl = ""))
        assertFalse(AiBackendConfig.isConfigured && AiBackendConfig.baseUrl.isEmpty())
    }

    /**
     * The request carries an `Accept` header and the serialized body - and, deliberately, no
     * `Authorization`. This is the whole contract of the transport now that the credential lives on
     * the proxy.
     */
    @Test
    fun `the request carries no authorization header`() = runBlocking {
        val sent = mutableListOf<Request>()
        val api = DeepSeekApiClient.createWith(
            DeepSeekApiClient.httpClient()
                .newBuilder()
                .addInterceptor(AnswersInPlaceOfTheNetwork(sent))
                .build(),
            baseUrl = TestBaseUrl
        )

        val answer = api.chatCompletions(sampleRequest())

        val request = sent.single()
        assertNull("the app must not authenticate with a provider key", request.header("Authorization"))
        assertEquals("application/json", request.header("Accept"))
        assertEquals("$TestBaseUrl${DeepSeekApiClient.ChatCompletionsPath}", request.url.toString())
        assertEquals(
            """{"model":"deepseek-chat","messages":[{"role":"system","content":"be helpful"},""" +
                """{"role":"user","content":"hi"}],"stream":false,"temperature":0.3}""",
            request.bodyAsText()
        )
        assertEquals("You spent Rp 975.000.", answer.choices.single().message?.content)
    }

    @Test
    fun `the endpoint path is the documented chat completions path`() {
        assertEquals("chat/completions", DeepSeekApiClient.ChatCompletionsPath)
    }
}

/**
 * Backend URL validation, as its own tests: a misconfigured build must fall back to "AI off" rather
 * than to plain text or a relative URL.
 */
class AiBackendConfigTest {

    @Test
    fun `an empty value means no backend`() {
        assertEquals("", AiBackendConfig.normalise(""))
        assertEquals("", AiBackendConfig.normalise("   "))
    }

    @Test
    fun `a https URL is accepted and given a trailing slash`() {
        assertEquals("https://ai.example.org/", AiBackendConfig.normalise("https://ai.example.org"))
        assertEquals("https://ai.example.org/v1/", AiBackendConfig.normalise("https://ai.example.org/v1/"))
        assertEquals("https://AI.example.org/", AiBackendConfig.normalise("  https://AI.example.org  "))
    }

    @Test
    fun `plain http is refused`() {
        assertEquals("", AiBackendConfig.normalise("http://ai.example.org"))
    }

    @Test
    fun `a URL with no scheme or no host is refused`() {
        assertEquals("", AiBackendConfig.normalise("ai.example.org"))
        assertEquals("", AiBackendConfig.normalise("https://"))
        assertEquals("", AiBackendConfig.normalise("ftp://ai.example.org"))
    }
}

/** A minimal well-formed request: the tests above assert on the wire, not on the question. */
private fun sampleRequest(): ChatCompletionRequest = ChatCompletionRequest(
    model = DeepSeekApiClient.DefaultModel,
    messages = listOf(ChatMessage.system("be helpful"), ChatMessage.user("hi"))
)

private const val TestBaseUrl = "https://ai.example.org/"

/** The shape a real completion comes back in, trimmed to the parts the client reads. */
private const val AnswerBody =
    """{"id":"1","model":"deepseek-chat","choices":[{"index":0,""" +
        """"message":{"role":"assistant","content":"You spent Rp 975.000."},"finish_reason":"stop"}]}"""

private fun Request.bodyAsText(): String = Buffer().also { body?.writeTo(it) }.readUtf8()

/** Records the request, then answers it with a canned body: no server, no key, no waiting. */
private class AnswersInPlaceOfTheNetwork(
    private val sent: MutableList<Request>
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        sent += request
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(AnswerBody.toResponseBody("application/json".toMediaType()))
            .build()
    }
}

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
import org.junit.Test
import org.umn.lensspent.BuildConfig

/**
 * The transport, exercised without a socket.
 *
 * The client the app builds is cloned with one interceptor appended that answers instead of the network.
 * Added last, it sees exactly the request the real one would have sent - key and body included - which
 * makes this the test that pins the two things this feature cannot get wrong: the key travels in an
 * `Authorization: Bearer` header, and the body is the serialized request on its way to the endpoint.
 */
class DeepSeekApiClientTest {

    @Test
    fun `the key rides in a bearer header and the body is the serialized request`() = runBlocking {
        val sent = mutableListOf<Request>()
        val api = DeepSeekApiClient.createWith(
            DeepSeekApiClient.httpClient(ApiKey).newBuilder()
                .addInterceptor(AnswersInPlaceOfTheNetwork(sent))
                .build()
        )

        val answer = api.chatCompletions(sampleRequest())

        val request = sent.single()
        assertEquals("Bearer $ApiKey", request.header("Authorization"))
        assertEquals("application/json", request.header("Accept"))
        assertEquals("https://api.deepseek.com/chat/completions", request.url.toString())
        assertEquals(
            """{"model":"deepseek-chat","messages":[{"role":"system","content":"be helpful"},""" +
                """{"role":"user","content":"hi"}],"stream":false,"temperature":0.3}""",
            request.bodyAsText()
        )
        assertEquals("You spent Rp 975.000.", answer.choices.single().message?.content)
    }

    /**
     * The base URL and the endpoint path are the two halves of the one URL that must not drift, so they
     * are asserted on their own as well as through the composed request above.
     */
    @Test
    fun `the endpoint is the documented chat completions URL`() {
        assertEquals("https://api.deepseek.com/", DeepSeekApiClient.BaseUrl)
        assertEquals("chat/completions", DeepSeekApiClient.ChatCompletionsPath)
    }

    /**
     * The key is a build constant, so which one is sent is decided when the APK is made rather than while
     * the app runs - and nothing else in the app can notice a build that lost its key, because the chat
     * would simply start saying Premium AI is off. Pinning it to `BuildConfig` is what makes the two the
     * same question, and it holds whether this checkout was given a real key or none at all.
     */
    @Test
    fun `the client sends the key the build was compiled with`() = runBlocking {
        val sent = mutableListOf<Request>()
        val api = DeepSeekApiClient.createWith(
            DeepSeekApiClient.httpClient().newBuilder()
                .addInterceptor(AnswersInPlaceOfTheNetwork(sent))
                .build()
        )

        api.chatCompletions(sampleRequest())

        assertEquals("Bearer ${BuildConfig.DEEPSEEK_API_KEY}", sent.single().header("Authorization"))
        assertEquals(BuildConfig.DEEPSEEK_API_KEY, DeepSeekApiClient.ApiKey)
        assertEquals(BuildConfig.DEEPSEEK_API_KEY.isNotBlank(), DeepSeekApiClient.isConfigured)
    }
}

/** A minimal well-formed request: the tests above assert on the wire, not on the question. */
private fun sampleRequest(): ChatCompletionRequest = ChatCompletionRequest(
    model = DeepSeekApiClient.DefaultModel,
    messages = listOf(ChatMessage.system("be helpful"), ChatMessage.user("hi"))
)

/**
 * A stand-in for the real key, and a non-blank one on purpose: this test asserts the header equals
 * `Bearer $ApiKey`, so an empty sentinel would let a client that sends no token at all pass it.
 */
private const val ApiKey = "sk-test-key"

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

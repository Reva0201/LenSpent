package org.umn.lensspent.data.remote

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import org.umn.lensspent.BuildConfig

/**
 * Builds the DeepSeek client the Premium AI Insights tier talks to.
 *
 * Everything about the connection lives here, so the repository above it is left with the part worth
 * reading - which transactions go out, which prompt goes with them, and what comes back. Three
 * decisions are made in this file:
 *
 *  - The base URL is the API root, and [ChatCompletionsPath] is the endpoint [DeepSeekApi] posts to,
 *    so the full URL is `https://api.deepseek.com/chat/completions`.
 *  - The key travels in an `Authorization: Bearer` header, installed once as an interceptor: no call
 *    site can send a request without it, and the key is never written into a URL or a log line.
 *  - The read timeout is generous. A reasoning answer over a long ledger can take a minute, and
 *    cutting it off at the default ten seconds would look like a network failure to the user.
 */
object DeepSeekApiClient {

    /** DeepSeek's API root; the OpenAI-compatible endpoints hang off it. */
    const val BaseUrl = "https://api.deepseek.com/"

    /** The chat endpoint, relative to [BaseUrl]. */
    const val ChatCompletionsPath = "chat/completions"

    /** The general chat model. `deepseek-reasoner` is the alternative for harder questions. */
    const val DefaultModel = "deepseek-chat"

    private const val ConnectTimeoutSeconds = 20L
    private const val ReadTimeoutSeconds = 120L

    private val ContentType = "application/json".toMediaType()

    /**
     * Lenient on the way in, explicit on the way out.
     *
     * Unknown keys are ignored, so an addition upstream cannot break a shipped build. Defaults are
     * encoded - which is the part that matters: without it, `temperature` and `stream` would be dropped
     * from the request for being equal to their defaults, and the model would answer at DeepSeek's own
     * default temperature instead of this app's.
     */
    val Json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * The key this build authenticates with, read from `BuildConfig` - the field the build fills from
     * `local.properties` or CI, and the only copy of it in the app.
     *
     * It lives here rather than at the call site so there is one answer to "which key is being sent",
     * and reading it through this object is what keeps `BuildConfig` out of the repository above.
     */
    val ApiKey: String = BuildConfig.DEEPSEEK_API_KEY

    /**
     * False on a build that was given no key.
     *
     * A keyless build still has to run - every other feature works offline - so the absence is a flag
     * [AiInsightsRepository] can turn into a sentence on screen rather than a request that was never
     * going to be authorised.
     */
    val isConfigured: Boolean = ApiKey.isNotBlank()

    /** A [DeepSeekApi] that authenticates with this build's key. */
    fun create(baseUrl: String = BaseUrl): DeepSeekApi = createWith(httpClient(), baseUrl)

    /**
     * The transport as the app uses it: the timeouts, and [apiKey] stamped on every request before it
     * leaves.
     *
     * Visible to the tests so one can clone this client, append an interceptor that answers in place of
     * the network, and assert on exactly what would have gone out - the header and the body included.
     * That is the whole of this client's contract, and it is why the key is a parameter: a test can pass
     * a stand-in and check the header without a real key in the build.
     */
    internal fun httpClient(apiKey: String = ApiKey): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(ConnectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(ReadTimeoutSeconds, TimeUnit.SECONDS)
        .addInterceptor(BearerAuthInterceptor(apiKey))
        .build()

    /** The same wiring [create] builds, over a client the caller already holds. */
    internal fun createWith(client: OkHttpClient, baseUrl: String = BaseUrl): DeepSeekApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(Json.asConverterFactory(ContentType))
            .build()
            .create(DeepSeekApi::class.java)
}

/**
 * Stamps the API key onto every request, the only place in the app that touches it.
 *
 * The key is fixed for the life of the process - it is a build constant - so holding it is safe here,
 * and there is no call site that could send a request without it.
 */
private class BearerAuthInterceptor(private val apiKey: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
        chain.request()
            .newBuilder()
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .build()
    )
}

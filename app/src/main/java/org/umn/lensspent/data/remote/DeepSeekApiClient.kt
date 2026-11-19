package org.umn.lensspent.data.remote

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Builds the HTTP client for the optional AI Insights feature.
 *
 * Three decisions live here, and all three are about what the app is *not* allowed to do:
 *
 *  - **No credential is compiled in.** The app talks to an https proxy that the developer controls,
 *    and that proxy authenticates with the model provider. Consequently there is no
 *    `Authorization` header, no bearer interceptor and no key that could be extracted from the APK.
 *  - **No destination means no client.** [createOrNull] returns null when the build has no backend,
 *    so there is no constructed transport that a future call site could accidentally use.
 *  - **HTTPS only.** [AiBackendConfig] rejects anything else before the URL reaches this file.
 *
 * The read timeout is generous: a reasoning answer over a ledger can take a minute, and cutting it
 * off at the default ten seconds would look like a network failure to the user.
 */
object DeepSeekApiClient {

    /**
     * The endpoint the proxy exposes, relative to its base URL.
     *
     * Kept identical to the provider's own path so the proxy stays a thin, auditable hop: it owns the
     * credential and the rate limit, and forwards an already-minimised body.
     */
    const val ChatCompletionsPath = "chat/completions"

    /** The general chat model the proxy is expected to use. */
    const val DefaultModel = "deepseek-chat"

    private const val ConnectTimeoutSeconds = 20L
    private const val ReadTimeoutSeconds = 120L

    private val ContentType = "application/json".toMediaType()

    /**
     * Lenient on the way in, explicit on the way out.
     *
     * Unknown keys are ignored, so an addition upstream cannot break a shipped build. Defaults are
     * encoded - which is the part that matters: without it, `temperature` and `stream` would be
     * dropped from the request for being equal to their defaults, and the model would answer at the
     * provider's own default temperature instead of this app's.
     */
    val Json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** A [DeepSeekApi] over [baseUrl], or null when this build has no safe destination. */
    fun createOrNull(baseUrl: String = AiBackendConfig.baseUrl): DeepSeekApi? {
        // Re-validated here rather than trusting the caller: a cleartext or relative URL must not be
        // able to construct a transport, even if a future call site passes its own string.
        val safeBaseUrl = AiBackendConfig.normalise(baseUrl)
        if (safeBaseUrl.isEmpty()) return null
        return createWith(httpClient(), safeBaseUrl)
    }

    /**
     * The transport as the app uses it: timeouts and an `Accept` header, and nothing identifying.
     *
     * Visible to the tests so one can clone this client, append an interceptor that answers in place
     * of the network, and assert on exactly what would have gone out. That is the whole of this
     * client's contract - which is why there is deliberately no credential to assert on.
     *
     * `followSslRedirects(false)` refuses an https -> http redirect. Same-protocol redirects are still
     * followed, so a proxy that canonicalises its path keeps working, but a compromised or
     * misconfigured backend cannot downgrade the spending summary to cleartext.
     */
    internal fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(ConnectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(ReadTimeoutSeconds, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .addInterceptor(AcceptJsonInterceptor)
        .build()

    /** The same wiring [createOrNull] builds, over a client the caller already holds. */
    internal fun createWith(client: OkHttpClient, baseUrl: String): DeepSeekApi =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(Json.asConverterFactory(ContentType))
            .build()
            .create(DeepSeekApi::class.java)
}

/**
 * Declares the response format and nothing else.
 *
 * There is deliberately no `Authorization` interceptor: the provider credential belongs to the
 * backend this app points at, not to an APK that anyone can unzip.
 */
private object AcceptJsonInterceptor : okhttp3.Interceptor {

    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response = chain.proceed(
        chain.request()
            .newBuilder()
            .header("Accept", "application/json")
            .build()
    )
}

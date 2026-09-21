package com.warivo.os.fleet

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** What came back from an ingest call. */
sealed interface IngestResult {
    data class Ok(val body: JSONObject) : IngestResult
    /** Network-level failure: keep the spool and try again. */
    data class Unreachable(val reason: String) : IngestResult
    /** The server answered and refused. Retrying the same payload will not help. */
    data class Rejected(val code: Int, val reason: String) : IngestResult
}

/**
 * The whole network layer: one POST.
 *
 * `HttpURLConnection` rather than OkHttp or Retrofit. The protocol is a single endpoint
 * (docs/FLEET.md §2), so a client library would be a dependency, a proguard rule and a
 * transitive tree to carry into the Path B ROM in exchange for nothing.
 */
class FleetClient {

    suspend fun ingest(endpoint: String, token: String, body: JSONObject): IngestResult =
        withContext(Dispatchers.IO) {
            val url = runCatching { URL(endpoint.trimEnd('/') + "/v1/ingest") }.getOrNull()
                ?: return@withContext IngestResult.Rejected(0, "endpoint is not a URL")

            // The app sets usesCleartextTraffic=false, so an http:// endpoint fails deep
            // in the stack with an opaque error. Say so plainly instead.
            if (!url.protocol.equals("https", ignoreCase = true)) {
                return@withContext IngestResult.Rejected(0, "endpoint must be https")
            }

            var connection: HttpURLConnection? = null
            try {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                connection.outputStream.bufferedWriter().use { it.write(body.toString()) }

                val code = connection.responseCode
                val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use(BufferedReader::readText)
                    .orEmpty()

                when {
                    code in 200..299 -> {
                        val parsed = runCatching {
                            if (text.isBlank()) JSONObject() else JSONObject(text)
                        }.getOrNull()
                        // A 2xx with a malformed body still means the samples landed, so
                        // treat it as success with no config rather than resending them.
                        IngestResult.Ok(parsed ?: JSONObject())
                    }
                    // 5xx and 429 are the server's problem, not the payload's: keep the
                    // spool and back off.
                    code >= 500 || code == 429 ->
                        IngestResult.Unreachable("HTTP $code")
                    else ->
                        IngestResult.Rejected(code, text.take(200).ifBlank { "HTTP $code" })
                }
            } catch (e: Exception) {
                Log.d(TAG, "ingest failed: ${e.javaClass.simpleName}: ${e.message}")
                IngestResult.Unreachable(e.javaClass.simpleName)
            } finally {
                runCatching { connection?.disconnect() }
            }
        }

    private companion object {
        const val TAG = "WarivoFleetClient"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
        const val USER_AGENT = "WarivoOS/0.1"
    }
}

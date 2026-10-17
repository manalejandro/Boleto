package com.manalejandro.boleto.core

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/** Minimal HTTP GET helper built on HttpURLConnection (no extra dependencies). */
object Http {

    private const val DEFAULT_TIMEOUT_MS = 20_000

    @Throws(IOException::class)
    fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Int = DEFAULT_TIMEOUT_MS): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            instanceFollowRedirects = true
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code")
            }
            val stream = connection.inputStream
            val body = if ("gzip".equals(connection.contentEncoding, ignoreCase = true)) {
                GZIPInputStream(stream).use { it.readBytes().decodeToString() }
            } else {
                stream.use { it.readBytes().decodeToString() }
            }
            return body
        } finally {
            connection.disconnect()
        }
    }
}

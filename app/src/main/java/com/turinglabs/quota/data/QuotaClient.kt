package com.turinglabs.quota.data

import com.turinglabs.quota.model.UsagePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed class QuotaError : Exception() {
    data object NotPaired : QuotaError()
    data object InvalidCode : QuotaError()
    data object ExpiredCode : QuotaError()
    data object Unauthorized : QuotaError()
    data class Server(val status: Int) : QuotaError()
    data object Network : QuotaError()
    data object InvalidResponse : QuotaError()
    data object Keystore : QuotaError()
}

/** Talks to quota-server: `POST /v1/pair` and `GET /v1/usage` (API version 1). */
class QuotaClient(private val store: QuotaStore) {
    suspend fun pair(server: String, code: String, deviceName: String) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("code", code.filter(Char::isDigit)).put("name", deviceName).toString()
        val (status, text) = request("${server.trimEnd('/')}/v1/pair", "POST", body = body)
        when (status) {
            200 -> {
                val token = runCatching { JSONObject(text).getString("token") }.getOrNull()
                if (token.isNullOrEmpty()) throw QuotaError.InvalidResponse
                if (!store.saveToken(token)) throw QuotaError.Keystore
                store.serverUrl = server.trimEnd('/')
            }
            401 -> throw QuotaError.InvalidCode
            410 -> throw QuotaError.ExpiredCode
            else -> throw QuotaError.Server(status)
        }
    }

    suspend fun fetchUsage(): UsagePayload = withContext(Dispatchers.IO) {
        val server = store.serverUrl ?: throw QuotaError.NotPaired
        val token = store.token() ?: throw QuotaError.NotPaired
        val (status, text) = request("$server/v1/usage", "GET", token = token)
        when (status) {
            200 -> {
                val payload = runCatching { UsagePayload.parse(text) }.getOrNull() ?: throw QuotaError.InvalidResponse
                store.saveCache(text)
                payload
            }
            401 -> throw QuotaError.Unauthorized
            else -> throw QuotaError.Server(status)
        }
    }

    private fun request(url: String, method: String, body: String? = null, token: String? = null): Pair<Int, String> {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (error: Exception) {
            throw QuotaError.Network
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            return status to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } catch (error: IOException) {
            throw QuotaError.Network
        } finally {
            connection.disconnect()
        }
    }
}

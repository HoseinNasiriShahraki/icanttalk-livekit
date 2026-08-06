package com.icanttalk.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class TokenClient {
    data class TokenResponse(val serverUrl: String, val participantToken: String)

    suspend fun requestToken(
        endpoint: String,
        accessKey: String,
        roomName: String,
        participantName: String,
        participantIdentity: String,
        avatarId: String,
    ): TokenResponse = withContext(Dispatchers.IO) {
        val json = post(endpoint, accessKey, JSONObject()
            .put("room_name", roomName)
            .put("participant_name", participantName.trim())
            .put("participant_identity", participantIdentity)
            .put("avatar_id", avatarId)
            .put("platform", "android"))
        val serverUrl = json.optString("server_url").ifBlank { json.optString("url") }.ifBlank { json.optString("livekit_url") }
        val token = json.optString("participant_token").ifBlank { json.optString("token") }
        require(serverUrl.startsWith("wss://") || serverUrl.startsWith("ws://")) { "The token endpoint returned an invalid LiveKit server URL." }
        require(token.isNotBlank()) { "The token endpoint did not return participant_token." }
        TokenResponse(serverUrl, token)
    }

    suspend fun requestPresence(endpoint: String, accessKey: String): List<RoomPresence> = withContext(Dispatchers.IO) {
        val json = try {
            post(
                endpoint,
                accessKey,
                JSONObject()
                    .put("action", "room_presence")
                    .put("room_names", org.json.JSONArray(listOf("Room 1", "Room 2")))
                    .put("client_version", "1.1.1"),
            )
        } catch (error: IllegalStateException) {
            if (error.message?.contains("Unknown room", ignoreCase = true) == true) {
                throw IllegalStateException("Room previews require the v1.1.1 Django endpoint.")
            }
            throw error
        }
        val roomsJson = json.optJSONArray("rooms") ?: return@withContext emptyList()
        buildList {
            for (roomIndex in 0 until roomsJson.length()) {
                val room = roomsJson.optJSONObject(roomIndex) ?: continue
                val peopleJson = room.optJSONArray("participants")
                val people = buildList {
                    if (peopleJson != null) for (index in 0 until peopleJson.length()) {
                        val person = peopleJson.optJSONObject(index) ?: continue
                        add(PresenceParticipant(
                            identity = person.optString("identity"),
                            name = person.optString("name").ifBlank { person.optString("identity") },
                            avatarId = person.optString("avatar_id", "01"),
                            platform = person.optString("platform", "unknown"),
                            camera = person.optBoolean("camera"),
                            screenShare = person.optBoolean("screen_share"),
                        ))
                    }
                }
                add(RoomPresence(room.optString("name"), people))
            }
        }
    }

    private fun post(endpoint: String, accessKey: String, payload: JSONObject): JSONObject {
        validateEndpoint(endpoint)
        require(accessKey.isNotBlank()) { "Access key is missing." }
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 20_000; doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-iCANTTalk-Access-Key", accessKey)
        }
        try {
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (status !in 200..299) {
                throw IllegalStateException(json.optString("error").ifBlank { json.optString("detail") }.ifBlank { "Endpoint returned HTTP $status." })
            }
            return json
        } finally { connection.disconnect() }
    }

    private fun validateEndpoint(endpoint: String) {
        val uri = runCatching { URI(endpoint.trim()) }.getOrElse { throw IllegalArgumentException("Token endpoint is not a valid URL.") }
        require(uri.scheme == "https" || uri.scheme == "http") { "Token endpoint must use HTTP or HTTPS." }
        require(!uri.host.isNullOrBlank()) { "Token endpoint must include a host." }
    }
}

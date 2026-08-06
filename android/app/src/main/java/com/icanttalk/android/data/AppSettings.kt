package com.icanttalk.android.data

enum class VoiceMode { VOICE_ACTIVITY, PUSH_TO_TALK }
enum class NoiseMode { OFF, STANDARD, KRISP }
enum class VideoQuality(val width: Int, val height: Int) {
    P480(854, 480), P720(1280, 720), P1080(1920, 1080),
}

object AvatarIds {
    val all: List<String> = (1..37).map { it.toString().padStart(2, '0') }
}

data class AppSettings(
    val username: String = "",
    val tokenEndpoint: String = "",
    val installationId: String = "",
    val avatarId: String = "01",
    val voiceMode: VoiceMode = VoiceMode.VOICE_ACTIVITY,
    val noiseMode: NoiseMode = NoiseMode.STANDARD,
    val cameraQuality: VideoQuality = VideoQuality.P720,
    val screenQuality: VideoQuality = VideoQuality.P720,
    val screenFps: Int = 30,
    val echoCancellation: Boolean = true,
    val autoGainControl: Boolean = true,
)

data class PresenceParticipant(
    val identity: String,
    val name: String,
    val avatarId: String,
    val platform: String,
    val camera: Boolean,
    val screenShare: Boolean,
)

data class RoomPresence(val name: String, val participants: List<PresenceParticipant>)

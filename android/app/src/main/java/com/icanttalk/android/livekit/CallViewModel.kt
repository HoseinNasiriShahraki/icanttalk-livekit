package com.icanttalk.android.livekit

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.icanttalk.android.data.AppSettings
import com.icanttalk.android.data.NoiseMode
import com.icanttalk.android.data.RoomPresence
import com.icanttalk.android.data.SecureSettingsStore
import com.icanttalk.android.data.TokenClient
import com.icanttalk.android.data.VideoQuality
import com.icanttalk.android.data.VoiceMode
import com.icanttalk.android.service.CallForegroundService
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.audio.AudioSwitchHandler
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.CameraPosition
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.RemoteTrackPublication
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoCaptureParameter
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.room.track.screencapture.ScreenCaptureParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CallViewModel(application: Application) : AndroidViewModel(application) {
    enum class ConnectionStatus { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING }

    data class ParticipantUi(
        val id: String,
        val name: String,
        val avatarId: String,
        val isLocal: Boolean,
        val isSpeaking: Boolean,
        val microphoneEnabled: Boolean,
        val cameraPublished: Boolean,
        val cameraWatching: Boolean,
        val cameraTrack: VideoTrack?,
        val screenPublished: Boolean,
        val screenWatching: Boolean,
        val screenTrack: VideoTrack?,
        val screenAudioPublished: Boolean,
        val voiceVolume: Float,
        val screenAudioVolume: Float,
        val screenAudioMuted: Boolean,
    )

    data class UiState(
        val settings: AppSettings,
        val hasAccessKey: Boolean,
        val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
        val currentRoom: String? = null,
        val participants: List<ParticipantUi> = emptyList(),
        val roomPresence: List<RoomPresence> = defaultPresence(),
        val presenceLoading: Boolean = false,
        val microphoneEnabled: Boolean = false,
        val cameraEnabled: Boolean = false,
        val screenShareEnabled: Boolean = false,
        val deafened: Boolean = false,
        val audioDevices: List<String> = emptyList(),
        val selectedAudioDeviceIndex: Int = -1,
        val krispState: String = "Off",
        val error: String? = null,
        val notice: String? = null,
        val settingsOpen: Boolean = false,
    )

    private val settingsStore = SecureSettingsStore(application)
    private val tokenClient = TokenClient()
    private var room: Room? = null
    private var roomEventJob: Job? = null
    private var presenceJob: Job? = null
    private val participantVolumes = mutableMapOf<String, Float>()
    private val screenAudioVolumes = mutableMapOf<String, Float>()
    private val screenAudioMuted = mutableMapOf<String, Boolean>()
    private val watchStates = mutableMapOf<String, Boolean>()
    private var microphoneBeforeDeafen = true
    private var krispProcessor: Any? = null

    private val _uiState = MutableStateFlow(
        settingsStore.load().let { initialSettings ->
            val hasKey = settingsStore.hasAccessKey()
            UiState(
                settings = initialSettings,
                hasAccessKey = hasKey,
                settingsOpen = initialSettings.username.isBlank() || !hasKey,
            )
        }
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val liveRoom: Room?
        get() = room

    init {
        startPresencePolling()
    }

    fun openSettings() {
        _uiState.value = _uiState.value.copy(settingsOpen = true, error = null, notice = null)
    }

    fun closeSettings() {
        _uiState.value = _uiState.value.copy(settingsOpen = false)
    }

    fun saveSettings(settings: AppSettings, accessKeyInput: String) {
        val normalized = settings.copy(
            username = settings.username.trim().take(40),
            tokenEndpoint = settings.tokenEndpoint.trim(),
            avatarId = settings.avatarId.takeIf { it.matches(Regex("\\d{2}")) } ?: "01",
            screenFps = if (settings.screenFps == 60) 60 else 30,
        )
        if (normalized.username.isBlank()) return setError("Enter a display name.")
        if (!normalized.tokenEndpoint.startsWith("http://") && !normalized.tokenEndpoint.startsWith("https://")) {
            return setError("The token endpoint must begin with http:// or https://.")
        }
        if (!_uiState.value.hasAccessKey && accessKeyInput.isBlank()) return setError("Enter the iCANTtalk access key.")

        val previous = _uiState.value.settings
        val accessKeyToSave = accessKeyInput.trim().takeIf { it.isNotBlank() }
        settingsStore.save(normalized, accessKeyToSave)
        _uiState.value = _uiState.value.copy(
            settings = normalized,
            hasAccessKey = settingsStore.hasAccessKey(),
            settingsOpen = false,
            error = null,
            notice = if (previous.noiseMode != normalized.noiseMode && room != null) {
                "Settings saved. Reconnect to apply the noise-filter mode."
            } else {
                "Settings saved."
            },
        )
        refreshPresence(false)

        if (_uiState.value.status == ConnectionStatus.CONNECTED && previous.voiceMode != normalized.voiceMode) {
            val targetMic = normalized.voiceMode == VoiceMode.VOICE_ACTIVITY && !_uiState.value.deafened
            room?.let { currentRoom ->
                viewModelScope.launch(Dispatchers.IO) {
                    if (currentRoom.localParticipant.setMicrophoneEnabled(targetMic)) {
                        microphoneBeforeDeafen = targetMic
                        withContext(Dispatchers.Main) {
                            _uiState.value = _uiState.value.copy(microphoneEnabled = targetMic)
                            refreshParticipants()
                        }
                    }
                }
            }
        }
    }

    fun refreshPresence(showLoading: Boolean = true) {
        val settings = _uiState.value.settings
        val accessKey = settingsStore.accessKey()
        if (settings.tokenEndpoint.isBlank() || accessKey.isBlank()) return
        if (showLoading) _uiState.value = _uiState.value.copy(presenceLoading = true)
        viewModelScope.launch {
            try {
                val result = tokenClient.requestPresence(settings.tokenEndpoint, accessKey)
                val normalized = listOf(ROOM_1, ROOM_2).map { roomName ->
                    result.firstOrNull { it.name == roomName } ?: RoomPresence(roomName, emptyList())
                }
                _uiState.value = _uiState.value.copy(roomPresence = normalized, presenceLoading = false)
            } catch (error: Throwable) {
                _uiState.value = _uiState.value.copy(
                    presenceLoading = false,
                    error = if (showLoading) error.message ?: "Unable to load room previews." else _uiState.value.error,
                )
            }
        }
    }

    private fun startPresencePolling() {
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            while (isActive) {
                refreshPresence(false)
                delay(5_000)
            }
        }
    }

    fun joinRoom(roomName: String) {
        if (roomName != ROOM_1 && roomName != ROOM_2) return setError("Unknown room.")
        if (_uiState.value.status != ConnectionStatus.DISCONNECTED) return

        val settings = _uiState.value.settings
        val accessKey = settingsStore.accessKey()
        if (settings.username.isBlank() || accessKey.isBlank()) {
            _uiState.value = _uiState.value.copy(settingsOpen = true, error = "Set your name, endpoint, and access key first.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                status = ConnectionStatus.CONNECTING,
                currentRoom = roomName,
                error = null,
                notice = "Requesting room access…",
            )
            try {
                val token = tokenClient.requestToken(
                    endpoint = settings.tokenEndpoint,
                    accessKey = accessKey,
                    roomName = roomName,
                    participantName = settings.username,
                    participantIdentity = "android-${settings.installationId}",
                    avatarId = settings.avatarId,
                )

                val newRoom = createRoom(settings)
                room = newRoom
                if (settings.noiseMode == NoiseMode.KRISP) {
                    runCatching { prepareKrisp(newRoom) }
                        .onFailure { error ->
                            Log.w(TAG, "Krisp initialization failed; continuing with standard WebRTC filtering.", error)
                            _uiState.value = _uiState.value.copy(
                                krispState = "Unavailable — Standard active",
                                notice = "Krisp is unavailable on this device. Standard noise filtering remains active.",
                            )
                        }
                } else {
                    _uiState.value = _uiState.value.copy(
                        krispState = if (settings.noiseMode == NoiseMode.STANDARD) "Standard" else "Off",
                    )
                }
                startEventCollection(newRoom)

                _uiState.value = _uiState.value.copy(notice = "Connecting to $roomName…")
                newRoom.connect(token.serverUrl, token.participantToken, ConnectOptions(autoSubscribe = true))

                val initialMicEnabled = settings.voiceMode == VoiceMode.VOICE_ACTIVITY
                newRoom.localParticipant.setMicrophoneEnabled(initialMicEnabled)
                microphoneBeforeDeafen = initialMicEnabled

                CallForegroundService.startCall(getApplication<Application>(), roomName)
                _uiState.value = _uiState.value.copy(
                    status = ConnectionStatus.CONNECTED,
                    microphoneEnabled = initialMicEnabled,
                    cameraEnabled = false,
                    screenShareEnabled = false,
                    deafened = false,
                    notice = "Connected to $roomName.",
                )
                refreshParticipants()
                refreshAudioDevices()
                refreshPresence(false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                cleanupRoom()
                _uiState.value = _uiState.value.copy(
                    status = ConnectionStatus.DISCONNECTED,
                    currentRoom = null,
                    error = error.message ?: "Unable to join the room.",
                    notice = null,
                )
            }
        }
    }

    fun disconnect() {
        if (_uiState.value.status == ConnectionStatus.DISCONNECTED) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(status = ConnectionStatus.DISCONNECTING, notice = "Disconnecting…")
            cleanupRoom()
            _uiState.value = _uiState.value.copy(
                status = ConnectionStatus.DISCONNECTED,
                currentRoom = null,
                participants = emptyList(),
                microphoneEnabled = false,
                cameraEnabled = false,
                screenShareEnabled = false,
                deafened = false,
                audioDevices = emptyList(),
                selectedAudioDeviceIndex = -1,
                notice = "Disconnected.",
            )
            refreshPresence(false)
        }
    }

    fun toggleMicrophone() {
        val currentRoom = room ?: return
        if (_uiState.value.deafened) return
        viewModelScope.launch(Dispatchers.IO) {
            val enabled = !_uiState.value.microphoneEnabled
            if (currentRoom.localParticipant.setMicrophoneEnabled(enabled)) {
                microphoneBeforeDeafen = enabled
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(microphoneEnabled = enabled)
                    refreshParticipants()
                }
            }
        }
    }

    fun pushToTalkPressed() {
        if (_uiState.value.settings.voiceMode != VoiceMode.PUSH_TO_TALK || _uiState.value.deafened) return
        setMicrophoneForPtt(true)
    }

    fun pushToTalkReleased() {
        if (_uiState.value.settings.voiceMode != VoiceMode.PUSH_TO_TALK) return
        setMicrophoneForPtt(false)
    }

    private fun setMicrophoneForPtt(enabled: Boolean) {
        val currentRoom = room ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (currentRoom.localParticipant.setMicrophoneEnabled(enabled)) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(microphoneEnabled = enabled)
                    refreshParticipants()
                }
            }
        }
    }

    fun toggleDeafen() {
        val currentRoom = room ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val shouldDeafen = !_uiState.value.deafened
            if (shouldDeafen) {
                microphoneBeforeDeafen = _uiState.value.microphoneEnabled
                currentRoom.localParticipant.setMicrophoneEnabled(false)
            } else {
                val restoreMic = _uiState.value.settings.voiceMode != VoiceMode.PUSH_TO_TALK && microphoneBeforeDeafen
                currentRoom.localParticipant.setMicrophoneEnabled(restoreMic)
            }
            applyAllRemoteVolumes(currentRoom, shouldDeafen)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    deafened = shouldDeafen,
                    microphoneEnabled = if (shouldDeafen) false else
                        (_uiState.value.settings.voiceMode != VoiceMode.PUSH_TO_TALK && microphoneBeforeDeafen),
                )
                refreshParticipants()
            }
        }
    }

    fun toggleCamera() {
        val currentRoom = room ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val enabled = !_uiState.value.cameraEnabled
            if (currentRoom.localParticipant.setCameraEnabled(enabled)) {
                CallForegroundService.setCamera(getApplication<Application>(), enabled)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(cameraEnabled = enabled)
                    refreshParticipants()
                    refreshPresence(false)
                }
            }
        }
    }

    fun flipCamera() {
        val track = room?.localParticipant?.getTrackPublication(Track.Source.CAMERA)?.track as? LocalVideoTrack ?: return
        val position = when (track.options.position) {
            CameraPosition.FRONT -> CameraPosition.BACK
            CameraPosition.BACK -> CameraPosition.FRONT
            else -> CameraPosition.FRONT
        }
        track.switchCamera(position = position)
    }

    fun startScreenShare(permissionData: Intent) {
        val currentRoom = room ?: return
        if (_uiState.value.screenShareEnabled) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = _uiState.value.settings
                currentRoom.screenShareTrackCaptureDefaults = LocalVideoTrackOptions(
                    isScreencast = true,
                    position = null,
                    captureParams = settings.screenQuality.capture(settings.screenFps, adapt = false),
                )
                CallForegroundService.setScreen(getApplication<Application>(), true)
                val enabled = currentRoom.localParticipant.setScreenShareEnabled(
                    true,
                    ScreenCaptureParams(
                        mediaProjectionPermissionResultData = permissionData,
                        onStop = {
                            CallForegroundService.setScreen(getApplication<Application>(), false)
                            _uiState.value = _uiState.value.copy(screenShareEnabled = false)
                            refreshParticipants()
                            refreshPresence(false)
                        },
                    ),
                )
                if (!enabled) CallForegroundService.setScreen(getApplication<Application>(), false)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        screenShareEnabled = enabled,
                        error = if (enabled) null else "Android did not start screen sharing.",
                    )
                    refreshParticipants()
                    refreshPresence(false)
                }
            } catch (error: Throwable) {
                CallForegroundService.setScreen(getApplication<Application>(), false)
                withContext(Dispatchers.Main) { setError(error.message ?: "Unable to start screen sharing.") }
            }
        }
    }

    fun stopScreenShare() {
        val currentRoom = room ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { currentRoom.localParticipant.setScreenShareEnabled(false) }
            CallForegroundService.setScreen(getApplication<Application>(), false)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(screenShareEnabled = false)
                refreshParticipants()
                refreshPresence(false)
            }
        }
    }

    fun setWatching(participantId: String, source: Track.Source, watching: Boolean) {
        val participant = findRemoteParticipant(participantId) ?: return
        val publication = participant.getTrackPublication(source) as? RemoteTrackPublication ?: return
        watchStates[watchKey(participantId, source)] = watching
        publication.setSubscribed(watching)
        if (source == Track.Source.SCREEN_SHARE) {
            (participant.getTrackPublication(Track.Source.SCREEN_SHARE_AUDIO) as? RemoteTrackPublication)
                ?.setSubscribed(watching)
        }
        refreshParticipants()
    }

    fun setParticipantVolume(participantId: String, value: Float) {
        val clamped = value.coerceIn(0f, 2f)
        participantVolumes[participantId] = clamped
        val participant = findRemoteParticipant(participantId) ?: return
        val track = participant.getTrackPublication(Track.Source.MICROPHONE)?.track as? RemoteAudioTrack
        track?.setVolume(if (_uiState.value.deafened) 0.0 else clamped.toDouble())
        refreshParticipants()
    }

    fun setScreenAudioVolume(participantId: String, value: Float) {
        val clamped = value.coerceIn(0f, 2f)
        screenAudioVolumes[participantId] = clamped
        applyScreenAudioVolume(participantId)
        refreshParticipants()
    }

    fun toggleScreenAudioMute(participantId: String) {
        screenAudioMuted[participantId] = !(screenAudioMuted[participantId] ?: false)
        applyScreenAudioVolume(participantId)
        refreshParticipants()
    }

    private fun applyScreenAudioVolume(participantId: String) {
        val participant = findRemoteParticipant(participantId) ?: return
        val volume = screenAudioVolumes.getOrPut(participantId) { 1f }
        val muted = screenAudioMuted[participantId] ?: false
        val track = participant.getTrackPublication(Track.Source.SCREEN_SHARE_AUDIO)?.track as? RemoteAudioTrack
        track?.setVolume(if (_uiState.value.deafened || muted) 0.0 else volume.toDouble())
    }

    fun selectAudioDevice(index: Int) {
        val handler = room?.audioHandler as? AudioSwitchHandler ?: return
        val devices = handler.availableAudioDevices
        if (index !in devices.indices) return
        handler.selectDevice(devices[index])
        refreshAudioDevices()
    }

    fun refreshAudioDevices() {
        val handler = room?.audioHandler as? AudioSwitchHandler
        if (handler == null) {
            _uiState.value = _uiState.value.copy(audioDevices = emptyList(), selectedAudioDeviceIndex = -1)
            return
        }
        val devices = handler.availableAudioDevices
        _uiState.value = _uiState.value.copy(
            audioDevices = devices.map(::audioDeviceLabel),
            selectedAudioDeviceIndex = devices.indexOf(handler.selectedAudioDevice),
        )
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(error = null, notice = null)
    }

    private fun createRoom(settings: AppSettings): Room {
        val standardNoise = settings.noiseMode != NoiseMode.OFF
        val options = RoomOptions(
            adaptiveStream = true,
            dynacast = true,
            audioTrackCaptureDefaults = LocalAudioTrackOptions(
                noiseSuppression = standardNoise,
                echoCancellation = settings.echoCancellation,
                autoGainControl = settings.autoGainControl,
                highPassFilter = standardNoise,
                typingNoiseDetection = standardNoise,
            ),
            videoTrackCaptureDefaults = LocalVideoTrackOptions(
                captureParams = settings.cameraQuality.capture(30, adapt = false),
                position = CameraPosition.FRONT,
            ),
            screenShareTrackCaptureDefaults = LocalVideoTrackOptions(
                isScreencast = true,
                position = null,
                captureParams = settings.screenQuality.capture(settings.screenFps, adapt = false),
            ),
        )
        return LiveKit.create(appContext = getApplication<Application>(), options = options)
    }

    /**
     * The Krisp Android artifact has changed package layout between releases. Reflection keeps
     * this application source compatible with the supported artifact while still attaching the
     * processor through LiveKit's public audioProcessingController API.
     */
    private suspend fun prepareKrisp(targetRoom: Room) = withContext(Dispatchers.IO) {
        _uiState.value = _uiState.value.copy(krispState = "Initializing…")

        // The public artifact has kept the KrispAudioProcessor API stable, while its
        // package has varied across releases. Reflection keeps this client compatible
        // with the Maven artifact selected in build.gradle.kts without hard-coding a
        // package that would break compilation after a package-only release change.
        val classNames = listOf(
            "io.livekit.krisp.KrispAudioProcessor",
            "io.livekit.krispnoisefilter.KrispAudioProcessor",
            "io.livekit.android.krisp.KrispAudioProcessor",
            "io.livekit.android.audio.KrispAudioProcessor",
            "io.livekit.android.audio.processor.KrispAudioProcessor",
            "io.livekit.android.audio.processing.KrispAudioProcessor",
            "io.livekit.noisefilter.KrispAudioProcessor",
            "io.livekit.KrispAudioProcessor",
        )
        val processorClass = classNames.firstNotNullOfOrNull { name ->
            runCatching { Class.forName(name) }.getOrNull()
        } ?: throw IllegalStateException(
            "Krisp processor class was not found. Verify io.livekit:krisp-noise-filter is installed.",
        )

        val application = getApplication<Application>()
        val processor = createKrispProcessor(processorClass, application)
        initializeKrispProcessor(processorClass, processor)

        val controller = targetRoom.audioProcessingController
        val captureProperty = controller.javaClass.methods.firstOrNull {
            it.name == "setCapturePostProcessor" && it.parameterTypes.size == 1
        }
        val legacyAttach = controller.javaClass.methods.firstOrNull {
            it.name == "setCapturePostProcessing" && it.parameterTypes.size == 1
        }
        when {
            captureProperty != null -> captureProperty.invoke(controller, processor)
            legacyAttach != null -> legacyAttach.invoke(controller, processor)
            else -> throw IllegalStateException(
                "This LiveKit Android SDK does not expose capture post-processing.",
            )
        }
        krispProcessor = processor
        _uiState.value = _uiState.value.copy(krispState = "Krisp enabled")
    }

    private fun createKrispProcessor(processorClass: Class<*>, application: Application): Any {
        val direct = processorClass.methods.firstOrNull { method ->
            method.name == "getInstance" &&
                Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0].isAssignableFrom(application.javaClass)
        } ?: processorClass.methods.firstOrNull { method ->
            method.name == "getInstance" && Modifier.isStatic(method.modifiers) && method.parameterTypes.size == 1
        }
        if (direct != null) {
            return direct.invoke(null, application)
                ?: throw IllegalStateException("Krisp initialization returned no processor.")
        }

        val companion = runCatching {
            processorClass.getDeclaredField("Companion").apply { isAccessible = true }.get(null)
        }.getOrNull() ?: throw IllegalStateException("Krisp getInstance() is unavailable.")
        val companionMethod = companion.javaClass.methods.firstOrNull { method ->
            method.name == "getInstance" && method.parameterTypes.size == 1
        } ?: throw IllegalStateException("Krisp getInstance() is unavailable.")
        return companionMethod.invoke(companion, application)
            ?: throw IllegalStateException("Krisp initialization returned no processor.")
    }

    private suspend fun initializeKrispProcessor(processorClass: Class<*>, processor: Any) {
        processorClass.methods.firstOrNull { it.name == "init" && it.parameterCount == 0 }?.let {
            it.invoke(processor)
            return
        }

        val suspendInit = processorClass.methods.firstOrNull { method ->
            method.name == "init" &&
                method.parameterCount == 1 &&
                Continuation::class.java.isAssignableFrom(method.parameterTypes.last())
        } ?: throw IllegalStateException("Krisp init() is unavailable.")

        suspendCoroutine<Unit> { continuation ->
            try {
                val result = suspendInit.invoke(processor, continuation)
                if (result !== COROUTINE_SUSPENDED) continuation.resume(Unit)
            } catch (error: InvocationTargetException) {
                continuation.resumeWithException(error.targetException ?: error)
            } catch (error: Throwable) {
                continuation.resumeWithException(error)
            }
        }
    }

    private fun startEventCollection(targetRoom: Room) {
        roomEventJob?.cancel()
        roomEventJob = viewModelScope.launch {
            targetRoom.events.collect { event ->
                when (event) {
                    is RoomEvent.FailedToConnect -> setError(event.error.message ?: "LiveKit connection failed.")
                    is RoomEvent.TrackPublished -> {
                        // New streams start visible. Users can then unsubscribe independently.
                        val publication = event.publication as? RemoteTrackPublication
                        publication?.let { watchStates[watchKey(participantId(event.participant), it.source)] = true }
                    }
                    is RoomEvent.Disconnected -> {
                        if (_uiState.value.status == ConnectionStatus.CONNECTED) {
                            val disconnectedRoom = room
                            room = null
                            CallForegroundService.stop(getApplication<Application>())
                            clearPerCallState()
                            _uiState.value = _uiState.value.copy(
                                status = ConnectionStatus.DISCONNECTED,
                                currentRoom = null,
                                participants = emptyList(),
                                microphoneEnabled = false,
                                cameraEnabled = false,
                                screenShareEnabled = false,
                                deafened = false,
                                audioDevices = emptyList(),
                                selectedAudioDeviceIndex = -1,
                                error = "The room connection ended.",
                            )
                            viewModelScope.launch(Dispatchers.IO) { runCatching { disconnectedRoom?.release() } }
                            refreshPresence(false)
                        }
                    }
                    else -> Unit
                }
                refreshParticipants()
                refreshAudioDevices()
            }
        }
    }

    private fun refreshParticipants() {
        val currentRoom = room ?: run {
            _uiState.value = _uiState.value.copy(participants = emptyList())
            return
        }
        val people = buildList {
            add(participantToUi(currentRoom.localParticipant, true))
            currentRoom.remoteParticipants.values.sortedBy { it.name.orEmpty().lowercase() }
                .forEach { add(participantToUi(it, false)) }
        }
        _uiState.value = _uiState.value.copy(participants = people)
    }

    private fun participantToUi(participant: Participant, isLocal: Boolean): ParticipantUi {
        val id = if (isLocal) participant.identity?.toString() ?: _uiState.value.settings.installationId else participantId(participant)
        val cameraPublication = participant.getTrackPublication(Track.Source.CAMERA)
        val screenPublication = participant.getTrackPublication(Track.Source.SCREEN_SHARE)
        val screenAudioPublication = participant.getTrackPublication(Track.Source.SCREEN_SHARE_AUDIO)
        val cameraWatching = if (isLocal) true else watchStates[watchKey(id, Track.Source.CAMERA)]
            ?: (cameraPublication as? RemoteTrackPublication)?.subscribed ?: true
        val screenWatching = if (isLocal) true else watchStates[watchKey(id, Track.Source.SCREEN_SHARE)]
            ?: (screenPublication as? RemoteTrackPublication)?.subscribed ?: true
        val metadataAvatar = runCatching {
            val metadata = JSONObject(participant.metadata.orEmpty())
            metadata.optString("avatar_id").ifBlank { metadata.optString("avatarId") }
        }.getOrDefault("").takeIf { it in com.icanttalk.android.data.AvatarIds.all }

        return ParticipantUi(
            id = id,
            name = participant.name?.takeIf { it.isNotBlank() } ?: participant.identity?.toString() ?: if (isLocal) "You" else "Guest",
            avatarId = metadataAvatar ?: if (isLocal) _uiState.value.settings.avatarId else stableAvatarFor(id),
            isLocal = isLocal,
            isSpeaking = participant.isSpeaking,
            microphoneEnabled = participant.audioTrackPublications.any { (publication, _) ->
                publication.source == Track.Source.MICROPHONE && !publication.muted
            },
            cameraPublished = cameraPublication != null && !cameraPublication.muted,
            cameraWatching = cameraWatching,
            cameraTrack = if (cameraWatching) cameraPublication?.track as? VideoTrack else null,
            screenPublished = screenPublication != null && !screenPublication.muted,
            screenWatching = screenWatching,
            screenTrack = if (screenWatching) screenPublication?.track as? VideoTrack else null,
            screenAudioPublished = screenAudioPublication != null && !screenAudioPublication.muted,
            voiceVolume = if (isLocal) 1f else participantVolumes.getOrPut(id) { 1f },
            screenAudioVolume = if (isLocal) 1f else screenAudioVolumes.getOrPut(id) { 1f },
            screenAudioMuted = if (isLocal) false else screenAudioMuted[id] ?: false,
        )
    }

    private fun findRemoteParticipant(id: String): RemoteParticipant? =
        room?.remoteParticipants?.values?.firstOrNull { participantId(it) == id }

    private fun participantId(participant: Participant): String =
        participant.identity?.toString() ?: participant.sid.toString()

    private fun watchKey(participantId: String, source: Track.Source) = "$participantId:${source.name}"

    private fun stableAvatarFor(identity: String): String {
        // Legacy clients may connect without profile metadata. Give those users a
        // stable identity-based avatar instead of showing avatar 01 for everyone.
        val hash = identity.fold(0x811c9dc5.toInt()) { value, character ->
            (value xor character.code) * 0x01000193
        }
        val index = (hash.toLong() and 0xffffffffL).rem(com.icanttalk.android.data.AvatarIds.all.size).toInt()
        return com.icanttalk.android.data.AvatarIds.all[index]
    }

    private fun applyAllRemoteVolumes(targetRoom: Room, forceMute: Boolean) {
        targetRoom.remoteParticipants.values.forEach { participant ->
            val id = participantId(participant)
            val voice = if (forceMute) 0.0 else participantVolumes.getOrPut(id) { 1f }.toDouble()
            (participant.getTrackPublication(Track.Source.MICROPHONE)?.track as? RemoteAudioTrack)?.setVolume(voice)
            val shareMuted = screenAudioMuted[id] ?: false
            val share = if (forceMute || shareMuted) 0.0 else screenAudioVolumes.getOrPut(id) { 1f }.toDouble()
            (participant.getTrackPublication(Track.Source.SCREEN_SHARE_AUDIO)?.track as? RemoteAudioTrack)?.setVolume(share)
        }
    }

    private suspend fun cleanupRoom() {
        val target = room
        room = null
        roomEventJob?.cancel()
        roomEventJob = null
        if (target != null) {
            withContext(Dispatchers.IO) {
                runCatching { target.localParticipant.setScreenShareEnabled(false) }
                runCatching { target.disconnect() }
                runCatching { target.release() }
            }
        }
        krispProcessor = null
        CallForegroundService.stop(getApplication<Application>())
        clearPerCallState()
    }

    private fun clearPerCallState() {
        participantVolumes.clear()
        screenAudioVolumes.clear()
        screenAudioMuted.clear()
        watchStates.clear()
    }

    private fun VideoQuality.capture(fps: Int, adapt: Boolean): VideoCaptureParameter =
        VideoCaptureParameter(width, height, fps.coerceIn(1, 60), adaptOutputToDimensions = adapt)

    private fun audioDeviceLabel(device: Any): String =
        device.toString().substringAfterLast('.').replace("AudioDevice", "").ifBlank { "Audio device" }

    private fun setError(message: String) {
        _uiState.value = _uiState.value.copy(error = message, notice = null)
    }

    override fun onCleared() {
        presenceJob?.cancel()
        roomEventJob?.cancel()
        room?.disconnect()
        room?.release()
        room = null
        CallForegroundService.stop(getApplication<Application>())
        super.onCleared()
    }

    companion object {
        private const val TAG = "iCANTtalk"
        const val ROOM_1 = "Room 1"
        const val ROOM_2 = "Room 2"
        private fun defaultPresence() = listOf(RoomPresence(ROOM_1, emptyList()), RoomPresence(ROOM_2, emptyList()))
    }
}

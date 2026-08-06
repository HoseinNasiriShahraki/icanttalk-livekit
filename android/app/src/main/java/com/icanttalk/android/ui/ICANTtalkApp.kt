package com.icanttalk.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icanttalk.android.R
import com.icanttalk.android.data.AppSettings
import com.icanttalk.android.data.AvatarIds
import com.icanttalk.android.data.NoiseMode
import com.icanttalk.android.data.RoomPresence
import com.icanttalk.android.data.VideoQuality
import com.icanttalk.android.data.VoiceMode
import com.icanttalk.android.livekit.CallViewModel
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack

@Composable
fun ICANTtalkApp(
    viewModel: CallViewModel,
    requestScreenShare: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ICANTtalkTheme {
        if (state.status == CallViewModel.ConnectionStatus.CONNECTED ||
            state.status == CallViewModel.ConnectionStatus.DISCONNECTING
        ) {
            CallScreen(
                state = state,
                room = viewModel.liveRoom,
                onDisconnect = viewModel::disconnect,
                onToggleMic = viewModel::toggleMicrophone,
                onToggleDeafen = viewModel::toggleDeafen,
                onToggleCamera = viewModel::toggleCamera,
                onFlipCamera = viewModel::flipCamera,
                onToggleScreen = {
                    if (state.screenShareEnabled) viewModel.stopScreenShare() else requestScreenShare()
                },
                onPttPress = viewModel::pushToTalkPressed,
                onPttRelease = viewModel::pushToTalkReleased,
                onVoiceVolume = viewModel::setParticipantVolume,
                onScreenAudioVolume = viewModel::setScreenAudioVolume,
                onScreenAudioMute = viewModel::toggleScreenAudioMute,
                onWatching = viewModel::setWatching,
                onSelectAudioDevice = viewModel::selectAudioDevice,
                onRefreshAudioDevices = viewModel::refreshAudioDevices,
                onSettings = viewModel::openSettings,
                onDismissMessage = viewModel::dismissMessage,
            )
        } else {
            LobbyScreen(
                state = state,
                onJoin = viewModel::joinRoom,
                onSettings = viewModel::openSettings,
                onRefreshPresence = { viewModel.refreshPresence(true) },
                onDismissMessage = viewModel::dismissMessage,
            )
        }

        if (state.settingsOpen) {
            SettingsDialog(
                current = state.settings,
                hasAccessKey = state.hasAccessKey,
                onDismiss = viewModel::closeSettings,
                onSave = viewModel::saveSettings,
            )
        }
    }
}

@Composable
private fun LobbyScreen(
    state: CallViewModel.UiState,
    onJoin: (String) -> Unit,
    onSettings: () -> Unit,
    onRefreshPresence: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    Scaffold(
        topBar = {
            AppHeader(
                subtitle = "Voice • webcam • screen sharing",
                avatarId = state.settings.avatarId,
                onSettings = onSettings,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Choose a room", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "The preview updates without joining. Android and Windows users share the same LiveKit rooms.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onRefreshPresence) {
                        if (state.presenceLoading) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "Refresh room previews")
                    }
                }
            }

            items(state.roomPresence, key = { it.name }) { room ->
                RoomPreviewCard(
                    room = room,
                    enabled = state.status == CallViewModel.ConnectionStatus.DISCONNECTED,
                    onJoin = { onJoin(room.name) },
                )
            }

            item {
                if (state.status == CallViewModel.ConnectionStatus.CONNECTING) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(state.notice ?: "Connecting…")
                    }
                }
                MessageBanner(state.error, state.notice, onDismissMessage)
                if (state.settings.tokenEndpoint.startsWith("http://")) {
                    Text(
                        "Your token endpoint uses unencrypted HTTP. Use HTTPS before broad distribution.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomPreviewCard(room: RoomPresence, enabled: Boolean, onJoin: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Headset, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(room.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${room.participants.size}/10 users", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onJoin, enabled = enabled) { Text("Join") }
            }
            Spacer(Modifier.height(12.dp))
            if (room.participants.isEmpty()) {
                Text("Room is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(room.participants, key = { it.identity }) { person ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                            Box {
                                AvatarImage(person.avatarId, person.name, 48.dp)
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 3.dp),
                                ) {
                                    if (person.camera) Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(12.dp))
                                    if (person.screenShare) Icon(Icons.Default.Monitor, null, modifier = Modifier.size(12.dp))
                                }
                            }
                            Text(person.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(subtitle: String, avatarId: String, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher),
            contentDescription = null,
            modifier = Modifier.size(42.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("iCANTtalk", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AvatarImage(avatarId, "Your profile picture", 36.dp)
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
    }
}

private data class FullscreenMedia(
    val name: String,
    val track: VideoTrack,
    val isScreen: Boolean,
    val mirror: Boolean,
)

@Composable
private fun CallScreen(
    state: CallViewModel.UiState,
    room: Room?,
    onDisconnect: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleDeafen: () -> Unit,
    onToggleCamera: () -> Unit,
    onFlipCamera: () -> Unit,
    onToggleScreen: () -> Unit,
    onPttPress: () -> Unit,
    onPttRelease: () -> Unit,
    onVoiceVolume: (String, Float) -> Unit,
    onScreenAudioVolume: (String, Float) -> Unit,
    onScreenAudioMute: (String) -> Unit,
    onWatching: (String, Track.Source, Boolean) -> Unit,
    onSelectAudioDevice: (Int) -> Unit,
    onRefreshAudioDevices: () -> Unit,
    onSettings: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    var fullscreen by remember { mutableStateOf<FullscreenMedia?>(null) }
    val screenShares = state.participants.filter { it.screenPublished }
    val cameras = state.participants.filter { it.cameraPublished || !it.isLocal }

    Scaffold(
        topBar = {
            Column {
                AppHeader(
                    subtitle = "${state.currentRoom.orEmpty()} • ${state.participants.size}/10 • ${state.krispState}",
                    avatarId = state.settings.avatarId,
                    onSettings = onSettings,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AudioDeviceMenu(state.audioDevices, state.selectedAudioDeviceIndex, onRefreshAudioDevices, onSelectAudioDevice)
                    Spacer(Modifier.weight(1f))
                    if (state.deafened) Text("Deafened", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        bottomBar = {
            CallControls(
                state = state,
                onDisconnect = onDisconnect,
                onToggleMic = onToggleMic,
                onToggleDeafen = onToggleDeafen,
                onToggleCamera = onToggleCamera,
                onFlipCamera = onFlipCamera,
                onToggleScreen = onToggleScreen,
                onPttPress = onPttPress,
                onPttRelease = onPttRelease,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { MessageBanner(state.error, state.notice, onDismissMessage) }

            if (screenShares.isNotEmpty()) {
                item { SectionTitle("Screen shares") }
                items(screenShares, key = { "screen-${it.id}" }) { person ->
                    ScreenShareCard(
                        participant = person,
                        room = room,
                        onWatching = { watching -> onWatching(person.id, Track.Source.SCREEN_SHARE, watching) },
                        onVolume = { onScreenAudioVolume(person.id, it) },
                        onMute = { onScreenAudioMute(person.id) },
                        onFullscreen = { track -> fullscreen = FullscreenMedia("${person.name}'s screen", track, true, false) },
                    )
                }
            }

            item { SectionTitle("Webcams and participants") }
            item {
                if (room != null) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(160.dp),
                        modifier = Modifier.fillMaxWidth().height(minOf(((cameras.size.coerceAtLeast(1) + 1) / 2) * 220, 660).dp),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        userScrollEnabled = false,
                    ) {
                        items(cameras, key = { "camera-${it.id}" }) { person ->
                            ParticipantCard(
                                participant = person,
                                room = room,
                                onWatching = { watching -> onWatching(person.id, Track.Source.CAMERA, watching) },
                                onFullscreen = { track -> fullscreen = FullscreenMedia(person.name, track, false, person.isLocal) },
                            )
                        }
                    }
                }
            }

            item { SectionTitle("Voice volumes") }
            items(state.participants.filterNot { it.isLocal }, key = { "voice-${it.id}" }) { person ->
                VoiceVolumeRow(person = person, onVolume = { onVoiceVolume(person.id, it) })
            }
        }
    }

    fullscreen?.let { media ->
        FullscreenVideoDialog(media = media, room = room, onDismiss = { fullscreen = null })
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ParticipantCard(
    participant: CallViewModel.ParticipantUi,
    room: Room,
    onWatching: (Boolean) -> Unit,
    onFullscreen: (VideoTrack) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (participant.isSpeaking) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (participant.cameraWatching && participant.cameraTrack != null) {
                    // FIT is intentional: vertical/portrait webcams stay proportional instead of cropping or stretching.
                    LiveKitVideoRenderer(
                        room = room,
                        track = participant.cameraTrack,
                        modifier = Modifier.fillMaxSize(),
                        mirror = participant.isLocal,
                        fit = true,
                    )
                } else {
                    AvatarImage(participant.avatarId, participant.name, 82.dp)
                    if (participant.cameraPublished && !participant.cameraWatching) {
                        Text(
                            "Camera hidden on this device",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
                        )
                    }
                }
                Row(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                    if (!participant.isLocal && participant.cameraPublished) {
                        IconButton(onClick = { onWatching(!participant.cameraWatching) }) {
                            Icon(
                                if (participant.cameraWatching) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (participant.cameraWatching) "Stop watching camera" else "Start watching camera",
                                tint = Color.White,
                            )
                        }
                    }
                    participant.cameraTrack?.takeIf { participant.cameraWatching }?.let { track ->
                        IconButton(onClick = { onFullscreen(track) }) {
                            Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen webcam", tint = Color.White)
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(participant.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!participant.microphoneEnabled) Icon(Icons.Default.MicOff, contentDescription = "Muted", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ScreenShareCard(
    participant: CallViewModel.ParticipantUi,
    room: Room?,
    onWatching: (Boolean) -> Unit,
    onVolume: (Float) -> Unit,
    onMute: () -> Unit,
    onFullscreen: (VideoTrack) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Monitor, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("${participant.name}'s screen", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                if (!participant.isLocal) {
                    IconButton(onClick = { onWatching(!participant.screenWatching) }) {
                        Icon(
                            if (participant.screenWatching) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            if (participant.screenWatching) "Stop watching screen" else "Start watching screen",
                        )
                    }
                }
                participant.screenTrack?.takeIf { participant.screenWatching }?.let { track ->
                    IconButton(onClick = { onFullscreen(track) }) { Icon(Icons.Default.Fullscreen, "Fullscreen screen share") }
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (room != null && participant.screenWatching && participant.screenTrack != null) {
                    LiveKitVideoRenderer(room, participant.screenTrack, Modifier.fillMaxSize(), fit = true)
                } else {
                    OutlinedButton(
                        onClick = { if (!participant.isLocal) onWatching(true) },
                        enabled = !participant.isLocal,
                    ) {
                        Icon(Icons.Default.Visibility, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (participant.isLocal) "Screen share is active" else "Start watching")
                    }
                }
            }
            if (!participant.isLocal && participant.screenAudioPublished) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onMute) {
                        Icon(
                            if (participant.screenAudioMuted || participant.screenAudioVolume == 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Mute shared-screen audio",
                        )
                    }
                    Slider(
                        value = participant.screenAudioVolume,
                        onValueChange = onVolume,
                        valueRange = 0f..2f,
                        modifier = Modifier.weight(1f),
                    )
                    Text(if (participant.screenAudioMuted) "Muted" else "${(participant.screenAudioVolume * 100).toInt()}%")
                }
            }
        }
    }
}

@Composable
private fun VoiceVolumeRow(person: CallViewModel.ParticipantUi, onVolume: (Float) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(14.dp)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarImage(person.avatarId, person.name, 42.dp)
        Spacer(Modifier.width(10.dp))
        Text(person.name, modifier = Modifier.width(90.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Slider(
            value = person.voiceVolume,
            onValueChange = onVolume,
            valueRange = 0f..2f,
            modifier = Modifier.weight(1f),
        )
        Text("${(person.voiceVolume * 100).toInt()}%")
    }
}

@Composable
private fun FullscreenVideoDialog(media: FullscreenMedia, room: Room?, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                if (room != null) {
                    // Preserve the complete frame for portrait and landscape media.
                    LiveKitVideoRenderer(
                        room = room,
                        track = media.track,
                        modifier = Modifier.fillMaxSize(),
                        mirror = media.mirror,
                        fit = true,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit fullscreen",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CallControls(
    state: CallViewModel.UiState,
    onDisconnect: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleDeafen: () -> Unit,
    onToggleCamera: () -> Unit,
    onFlipCamera: () -> Unit,
    onToggleScreen: () -> Unit,
    onPttPress: () -> Unit,
    onPttRelease: () -> Unit,
) {
    Surface(tonalElevation = 5.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.settings.voiceMode == VoiceMode.PUSH_TO_TALK) {
                IconButton(
                    onClick = {},
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            onPttPress()
                            tryAwaitRelease()
                            onPttRelease()
                        })
                    },
                ) { Icon(if (state.microphoneEnabled) Icons.Default.Mic else Icons.Default.MicOff, "Hold to talk") }
            } else {
                IconButton(onClick = onToggleMic) { Icon(if (state.microphoneEnabled) Icons.Default.Mic else Icons.Default.MicOff, "Microphone") }
            }
            IconButton(onClick = onToggleDeafen) { Icon(if (state.deafened) Icons.Default.VolumeOff else Icons.Default.Headphones, "Deafen") }
            IconButton(onClick = onToggleCamera) { Icon(if (state.cameraEnabled) Icons.Default.PhotoCamera else Icons.Default.PhotoCamera, "Camera") }
            if (state.cameraEnabled) IconButton(onClick = onFlipCamera) { Icon(Icons.Default.Cameraswitch, "Flip camera") }
            IconButton(onClick = onToggleScreen) {
                Icon(if (state.screenShareEnabled) Icons.Default.StopScreenShare else Icons.Default.Monitor, "Screen sharing")
            }
            IconButton(onClick = onDisconnect) { Icon(Icons.Default.CallEnd, "Disconnect", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun AudioDeviceMenu(
    devices: List<String>,
    selectedIndex: Int,
    onOpen: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { onOpen(); expanded = true }) {
            Icon(Icons.Default.Headphones, null)
            Spacer(Modifier.width(6.dp))
            Text(devices.getOrNull(selectedIndex) ?: "Audio output", maxLines = 1)
            Icon(Icons.Default.MoreVert, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            devices.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { expanded = false; onSelect(index) },
                )
            }
        }
    }
}

@Composable
private fun AvatarImage(avatarId: String, label: String, size: androidx.compose.ui.unit.Dp) {
    Image(
        painter = painterResource(AvatarCatalog.resource(avatarId)),
        contentDescription = label,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(CircleShape).border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape).background(Color.Black, CircleShape),
    )
}

@Composable
private fun MessageBanner(error: String?, notice: String?, onDismiss: () -> Unit) {
    val message = error ?: notice ?: return
    Surface(
        color = if (error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

@Composable
private fun SettingsDialog(
    current: AppSettings,
    hasAccessKey: Boolean,
    onDismiss: () -> Unit,
    onSave: (AppSettings, String) -> Unit,
) {
    var draft by remember(current) { mutableStateOf(current) }
    var accessKey by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.92f).imePadding(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Divider()
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedTextField(
                        value = draft.username,
                        onValueChange = { draft = draft.copy(username = it.take(40)) },
                        label = { Text("Display name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.tokenEndpoint,
                        onValueChange = { draft = draft.copy(tokenEndpoint = it) },
                        label = { Text("Django token endpoint") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = accessKey,
                        onValueChange = { accessKey = it },
                        label = { Text(if (hasAccessKey) "Access key (leave blank to keep current)" else "Access key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )

                    Text("Profile picture", style = MaterialTheme.typography.titleMedium)
                    Text("A random picture is assigned on first launch. Choosing another picture is optional.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(AvatarIds.all) { id ->
                            Box(
                                modifier = Modifier
                                    .border(if (id == draft.avatarId) 3.dp else 1.dp, if (id == draft.avatarId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .padding(3.dp)
                                    .clickable { draft = draft.copy(avatarId = id) },
                            ) { AvatarImage(id, "Profile picture $id", 54.dp) }
                        }
                    }

                    ChoiceSection("Voice mode") {
                        FilterChip(selected = draft.voiceMode == VoiceMode.VOICE_ACTIVITY, onClick = { draft = draft.copy(voiceMode = VoiceMode.VOICE_ACTIVITY) }, label = { Text("Voice activity") })
                        FilterChip(selected = draft.voiceMode == VoiceMode.PUSH_TO_TALK, onClick = { draft = draft.copy(voiceMode = VoiceMode.PUSH_TO_TALK) }, label = { Text("Push to talk") })
                    }
                    ChoiceSection("Noise filtering") {
                        NoiseMode.entries.forEach { mode ->
                            FilterChip(
                                selected = draft.noiseMode == mode,
                                onClick = { draft = draft.copy(noiseMode = mode) },
                                label = { Text(when (mode) { NoiseMode.OFF -> "Off"; NoiseMode.STANDARD -> "Standard"; NoiseMode.KRISP -> "Krisp" }) },
                            )
                        }
                    }
                    Text("Krisp removes stronger ambient and transient noise. Reconnect after changing this mode.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)

                    SwitchRow("Echo cancellation", draft.echoCancellation) { draft = draft.copy(echoCancellation = it) }
                    SwitchRow("Automatic gain control", draft.autoGainControl) { draft = draft.copy(autoGainControl = it) }

                    QualitySection("Webcam quality", draft.cameraQuality) { draft = draft.copy(cameraQuality = it) }
                    QualitySection("Screen-share quality", draft.screenQuality) { draft = draft.copy(screenQuality = it) }
                    ChoiceSection("Screen-share FPS") {
                        listOf(30, 60).forEach { fps -> FilterChip(selected = draft.screenFps == fps, onClick = { draft = draft.copy(screenFps = fps) }, label = { Text("$fps FPS") }) }
                    }
                }
                Divider()
                Button(
                    onClick = { onSave(draft, accessKey) },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) { Text("Save settings") }
            }
        }
    }
}

@Composable
private fun ChoiceSection(title: String, content: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun QualitySection(title: String, value: VideoQuality, onChange: (VideoQuality) -> Unit) {
    ChoiceSection(title) {
        VideoQuality.entries.forEach { quality ->
            FilterChip(
                selected = value == quality,
                onClick = { onChange(quality) },
                label = { Text(when (quality) { VideoQuality.P480 -> "480p"; VideoQuality.P720 -> "720p"; VideoQuality.P1080 -> "1080p" }) },
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

package com.icanttalk.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import livekit.org.webrtc.RendererCommon

@Composable
fun LiveKitVideoRenderer(
    room: Room,
    track: VideoTrack,
    modifier: Modifier = Modifier,
    mirror: Boolean = false,
    fit: Boolean = false,
) {
    val context = LocalContext.current
    val renderer = remember(track, room, context) {
        TextureViewRenderer(context).apply {
            room.initVideoRenderer(this)
            setMirror(mirror)
            // Hardware scaling may crop rotated portrait frames on some devices.
            setEnableHardwareScaler(!fit)
            setScalingType(
                if (fit) RendererCommon.ScalingType.SCALE_ASPECT_FIT
                else RendererCommon.ScalingType.SCALE_ASPECT_FILL
            )
        }
    }

    DisposableEffect(track, renderer) {
        track.addRenderer(renderer)
        onDispose {
            track.removeRenderer(renderer)
            renderer.release()
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier,
        update = {
            it.setMirror(mirror)
            it.setEnableHardwareScaler(!fit)
            it.setScalingType(
                if (fit) RendererCommon.ScalingType.SCALE_ASPECT_FIT
                else RendererCommon.ScalingType.SCALE_ASPECT_FILL
            )
        },
    )
}

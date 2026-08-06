# iCANTTalk cross-platform product specification — v1.1.0

## Scope

Windows 10/11 x64 and Android 8+ clients using one Django token/presence endpoint and one LiveKit deployment.

## Fixed rooms

- Room 1
- Room 2
- Click or tap a room to join.
- Lobby previews show connected users in both rooms without joining.
- The interface is designed for up to 10 participants per room.

## Identity and profile

- No user-account backend.
- Editable display name stored locally.
- Persistent random installation identity.
- One random profile picture from the bundled 37-image pool on first launch.
- Profile picture remains optional to configure and can be changed later.
- Avatar ID and platform are carried in LiveKit participant metadata.

## Audio

- Microphone mute/unmute.
- Deafen/undeafen.
- Voice activity and in-app hold-to-talk.
- Echo cancellation and automatic gain control.
- Noise modes: Off, Standard WebRTC, and Krisp Enhanced.
- Per-participant local voice volume.
- Independent screen-share audio mute and volume for each remote sharer.

## Video and subscriptions

- Remote camera streams can be individually watched or unsubscribed.
- Remote screen-share streams can be individually watched or unsubscribed.
- Multiple simultaneous webcam and screen-share subscriptions are supported.
- Fullscreen camera and screen-share presentation.
- Portrait and landscape camera aspect ratios are preserved.
- Webcam 480p/720p/1080p.
- Screen share 480p/720p/1080p at 30/60 FPS.
- Windows supports full-display and individual-window capture plus loopback audio.
- Android supports full-device MediaProjection capture; internal app-audio publishing is excluded.

## Presence

- Both clients poll the protected Django endpoint every five seconds.
- Django uses the LiveKit Room Service API to list participants in Room 1 and Room 2.
- Presence includes display name, identity, avatar, platform, camera status, and screen-share status.
- Results are cached briefly to reduce repeated LiveKit API calls.

## Security

- Django signs short-lived LiveKit tokens.
- Clients never receive the LiveKit API secret.
- Access key is protected by Windows `safeStorage` or Android Keystore.
- Token and presence requests share the `X-iCANTTalk-Access-Key` header.
- Only Room 1 and Room 2 are permitted by the supplied endpoint.

## Lifecycle and packaging

- Closing Windows fully disconnects and exits.
- Android uses a foreground service for active microphone/camera/screen-share sessions.
- Windows output: assisted NSIS x64 `.exe` installer.
- Android output: debug or signed release APK.
- Manual updates only.

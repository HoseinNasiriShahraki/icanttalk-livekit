# iCANTTalk for Windows — v1.1.1

A Windows 10/11 x64 LiveKit client for two permanent rooms: **Room 1** and **Room 2**. The application is voice-first and intentionally excludes chat, file transfer, account databases, recording, friends, and server/channel management.

This maintenance release fixes four issues reported after v1.1.0:

- Remote avatars now come from each participant's LiveKit metadata; legacy users receive a stable identity-based fallback instead of inheriting your avatar.
- Webcam and screen-share fullscreen now use a native Electron fullscreen window plus a dedicated media overlay.
- Room-preview errors clearly identify an outdated Django endpoint.
- The existing multi-stream watch, screen-audio, and toolbar behavior is preserved.

## v1.1.1 maintenance fixes

- Start or stop watching each remote webcam independently.
- Start or stop watching each remote screen share independently.
- Watch multiple webcams and multiple screen shares at the same time.
- Local mute and 0–100% volume control for every remote screen-share audio track.
- Fullscreen controls for webcams and screen shares.
- Portrait and landscape camera rendering with preserved aspect ratio.
- Taskbar-safe call controls that remain inside the application layout while sharing.
- Room 1 and Room 2 participant previews before joining.
- Off, Standard WebRTC, and Krisp enhanced microphone filtering modes.
- 37 bundled profile pictures; a random one is stored on first launch and can be changed later.
- Updated iCANTTalk application and installer icon.

Existing features remain available: mute, deafen, voice activity, in-window push-to-talk, device selection, webcam quality selection, multi-source desktop capture, 480p/720p/1080p screen sharing, 30/60 FPS, Windows loopback system audio, pause/resume, per-user voice volume, and manual installer updates.

## Required deployment order

Room previews and cross-platform profile pictures require the **v1.1.1 Django endpoint** included in `django_endpoint/`.

1. Deploy the v1.1.1 Django endpoint and restart Django/Gunicorn.
2. Confirm the presence request works.
3. Rebuild and reinstall the Windows application.
4. Rebuild and reinstall the Android application.

Both clients must use the same endpoint, access key, LiveKit project, and exact room names.

## Build the Windows installer

Install Node.js 22 or newer on Windows 10/11 x64. Open PowerShell in the project directory and run:

```powershell
.\BUILD_WINDOWS.ps1
```

The installer is created at:

```text
release\iCANTTalk-Setup-1.1.1-x64.exe
```

For development:

```powershell
npm install
npm run dev
```

## First run

1. Run the installer and enter the shared iCANTTalk access key.
2. Open Settings.
3. Enter a display name and Django endpoint.
4. Keep the random profile picture or select another picture.
5. Select Off, Standard, or Krisp noise filtering.
6. Save, then click Room 1 or Room 2.

Krisp downloads/initializes its processing resources the first time Enhanced filtering is used. If initialization fails, the client keeps the standard WebRTC processing path available.

## Endpoint contract

Token request:

```http
POST /api-v2/command/livekit-token/
Content-Type: application/json
X-iCANTTalk-Access-Key: <shared-key>
```

```json
{
  "room_name": "Room 1",
  "participant_identity": "stable-installation-identity",
  "participant_name": "Alice",
  "avatar_id": "07",
  "platform": "windows"
}
```

Token response:

```json
{
  "server_url": "wss://your-project.livekit.cloud",
  "participant_token": "eyJ..."
}
```

Room-preview request to the same endpoint:

```json
{
  "action": "room_presence"
}
```

Room-preview response:

```json
{
  "rooms": [
    {
      "name": "Room 1",
      "participants": [
        {
          "identity": "...",
          "name": "Alice",
          "avatar_id": "07",
          "platform": "windows",
          "camera": true,
          "screen_share": false
        }
      ]
    },
    { "name": "Room 2", "participants": [] }
  ]
}
```

## Security and production notes

- Use HTTPS for Django and WSS/TLS for LiveKit in production. The source permits HTTP only because the current endpoint uses it.
- The LiveKit API secret remains on Django and is never stored in either client.
- The installer key is migrated into Electron `safeStorage` on first launch.
- Code-sign the installer and executable before broad distribution.
- The room-preview API is protected by the same app access key and is cached/rate-limited in the supplied Django view.
- The application contains no recording feature.
- Android does not publish internal device audio in this version. Windows screen-share loopback audio can be received and controlled by both clients.

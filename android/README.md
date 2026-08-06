# iCANTtalk for Android — v1.1.1

Native Kotlin/Jetpack Compose client for the same LiveKit **Room 1** and **Room 2** used by iCANTTalk for Windows.

## Cross-platform compatibility

Windows and Android clients use the same:

- Django token/presence endpoint
- `X-iCANTTalk-Access-Key`
- LiveKit project URL and credentials held by Django
- exact room names `Room 1` and `Room 2`
- participant metadata fields for avatar and platform
- standard LiveKit microphone, camera, and screen-share tracks

A Windows and Android user joining the same room can therefore hear and see one another, watch multiple concurrent streams, and locally control incoming screen-share audio.

This maintenance release fixes four issues reported after v1.1.0:

- Remote avatars use participant metadata, with a stable identity-based fallback for legacy clients.
- Fullscreen has only a transparent close control; the username/title bar was removed.
- Fullscreen camera and screen shares use aspect-fit letterboxing, including portrait sources.
- Room-preview errors identify an outdated Django presence endpoint.

## v1.1.1 maintenance fixes

- Start or stop watching each remote webcam independently.
- Start or stop watching each remote screen share independently.
- Watch multiple webcam and screen-share tracks at once.
- Local screen-share audio mute and 0–200% volume controls.
- Fullscreen camera and screen-share viewer.
- Responsive portrait/landscape video rendering using FIT scaling.
- Navigation-bar-safe bottom controls.
- Lobby participant previews for Room 1 and Room 2 before joining.
- Off, Standard WebRTC, and Krisp enhanced microphone filtering.
- 37 bundled profile pictures, random selection on first launch, editable in Settings.
- Updated launcher and notification artwork.

Existing features remain available: voice activity, in-app hold-to-talk, mute, deafen, per-user voice volume, audio route selection, front/back camera, 480p/720p/1080p camera, full-device screen sharing, 480p/720p/1080p at 30/60 FPS, and foreground-call handling.

## Required deployment order

The lobby preview and shared avatar features need the updated endpoint under `django_endpoint/`.

1. Deploy the v1.1.1 Django endpoint.
2. Restart Django/Gunicorn.
3. Rebuild and reinstall Windows.
4. Rebuild and reinstall Android.

## Requirements

- Android Studio and Android SDK Platform 36
- JDK 17
- Android 8.0 or newer (`minSdk 26`)
- A physical device for meaningful microphone, camera, Bluetooth, and screen-share testing

## Build from PowerShell

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\BUILD_ANDROID.ps1
```

Output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Install with ADB:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r `
  ".\app\build\outputs\apk\debug\app-debug.apk"
```

## First run

1. Open Settings.
2. Enter a display name.
3. Enter the same Django endpoint used by Windows.
4. Enter the same `ICANTTALK_ACCESS_KEY` configured on Django.
5. Keep the random avatar or select another.
6. Select Off, Standard, or Krisp noise filtering.
7. Save, then tap Room 1 or Room 2.

Android requests microphone and camera permissions. Starting screen sharing triggers the system MediaProjection consent dialog.

## Endpoint contract

Token request:

```json
{
  "room_name": "Room 1",
  "participant_name": "Android User",
  "participant_identity": "android-installation-id",
  "avatar_id": "12",
  "platform": "android"
}
```

Presence request to the same endpoint:

```json
{
  "action": "room_presence"
}
```

The endpoint must return `server_url` and `participant_token` for tokens, and a `rooms` array for presence.

## Platform differences

- Android shares the full device screen rather than individual application windows.
- This source does not publish Android internal application audio. It can receive Windows screen-share audio and apply local mute/volume controls.
- Input/output routing is constrained by Android device and manufacturer behavior.
- Push-to-talk is active while the application receives the press; it is not a global hardware-key hook.
- The current default endpoint uses HTTP for compatibility with the existing server. Use HTTPS before distribution.

## Production distribution

The generated debug APK is intended for testing. Configure a release signing key and build a signed release APK/AAB before broad distribution. Never commit the keystore or passwords.

## Versions

- App: 1.1.1
- LiveKit Android SDK: 2.27.0
- Krisp Android filter: 0.0.15
- Android Gradle Plugin: 8.13.2
- Gradle: 8.13
- Kotlin: 2.3.21
- Java: 17
- Compile SDK: 36
- Target SDK: 35

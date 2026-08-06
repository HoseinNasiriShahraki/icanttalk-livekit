# Architecture

## Components

```text
Windows Electron client ─┐
                         ├── HTTPS token/presence API ── Django
Android native client ───┘                                 │
                                                          │ signs JWTs / lists rooms
                                                          ▼
                                                       LiveKit
```

### Windows client

- Electron main process protects access-key operations and desktop capture.
- React/TypeScript renderer provides room, participant, media, and settings UI.
- LiveKit JavaScript SDK publishes and subscribes to microphone, camera, and screen tracks.

### Android client

- Kotlin and Jetpack Compose UI.
- LiveKit Android SDK handles real-time media.
- Foreground service supports ongoing calls and Android MediaProjection screen sharing.
- Android Keystore protects the shared access key at rest.

### Django service

The service has two responsibilities:

1. Validate the application access key and issue room-scoped, short-lived LiveKit tokens.
2. Query LiveKit room participants for the pre-join Room 1 and Room 2 preview.

It does not relay media. Audio, video, and screen tracks flow between clients and LiveKit.

## Participant metadata

Each join token includes metadata containing:

```json
{
  "avatar_id": "07",
  "platform": "windows",
  "client_version": "1.1.1"
}
```

Clients use this metadata to render the correct bundled avatar and platform information.

## Trust boundaries

- LiveKit API secret: Django only
- Application access key: Django and installed clients
- LiveKit participant token: short-lived client credential
- User preferences: local device storage

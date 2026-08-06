# iCANTtalk v1.1.1

This maintenance release improves cross-platform identity, fullscreen viewing, and room previews.

## Highlights

- Each participant now displays their own profile picture.
- Windows webcam and screen-share fullscreen controls are functional.
- Android fullscreen uses a clean transparent exit button.
- Android fullscreen video uses aspect-fit instead of cropping.
- Django room-presence support is normalized for Room 1 and Room 2.
- Existing multi-stream watching, screen-audio controls, profile selection, and noise-filtering options remain available.

## Deployment order

1. Deploy `server/django` and restart all Django/Gunicorn workers.
2. Build and install the Windows v1.1.1 client.
3. Build and install the Android v1.1.1 client.
4. Have connected users disconnect and reconnect so current participant metadata is included in their new LiveKit token.

## Important notes

- The Windows installer is unsigned unless you add a code-signing certificate.
- The default Android build is a debug APK; configure release signing before distribution.
- Use HTTPS for the Django endpoint and WSS for LiveKit in production.
- Never distribute LiveKit API secrets or the shared application access key.

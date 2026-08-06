# Changelog

All notable changes to iCANTtalk are documented here.

## [1.1.1] — 2026-08-06

### Fixed

- Corrected remote avatar resolution so participants no longer inherit the local user's avatar.
- Replaced unreliable Windows element fullscreen behavior with an Electron/native fullscreen media overlay.
- Simplified Android fullscreen UI to a transparent exit control.
- Changed Android fullscreen rendering to aspect-fit so portrait and landscape streams are not cropped.
- Updated room-presence handling and diagnostics for outdated Django deployments.

### Included from 1.1.0

- Per-track start/stop watching for remote webcams and screen shares.
- Multiple simultaneous remote stream viewing.
- Incoming screen-share audio mute and volume controls.
- Room previews for both permanent rooms.
- Random and selectable bundled avatars.
- Krisp enhanced noise-filtering option with WebRTC fallback.
- Updated Windows and Android application icons.

## [1.0.0]

- Initial Windows and Android LiveKit clients.
- Two permanent rooms.
- Voice, webcam, screen sharing, device controls, and Django token endpoint.

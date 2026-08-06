# iCANTtalk v1.1 cross-platform acceptance checklist

Use two devices with different participant names and the deployed v1.1 Django endpoint.

## Endpoint and lobby

- [ ] Incorrect access keys are rejected.
- [ ] Token requests return `server_url` and `participant_token`.
- [ ] Room 1 preview lists Windows and Android users without joining.
- [ ] Room 2 preview lists Windows and Android users without joining.
- [ ] Preview avatars and platform labels are correct.
- [ ] Camera/screen-share preview status updates after publication changes.

## Rooms

- [ ] Windows Room 1 and Android Room 1 show each other.
- [ ] Windows Room 2 and Android Room 2 show each other.
- [ ] Room 1 participants do not appear as connected participants in Room 2.

## Voice and filtering

- [ ] Android microphone is audible on Windows.
- [ ] Windows microphone is audible on Android.
- [ ] Mute, deafen, voice activity, and in-app PTT work.
- [ ] Per-user voice volume changes only the selected participant locally.
- [ ] Standard filtering works.
- [ ] Krisp initializes and reduces ambient noise without disconnecting the microphone.
- [ ] Switching filtering mode and reconnecting applies the chosen mode.

## Cameras

- [ ] Each remote camera has Start Watching and Stop Watching behavior.
- [ ] Stopping one camera does not stop other cameras.
- [ ] Multiple cameras are visible simultaneously.
- [ ] Portrait Android camera is not stretched or cropped incorrectly.
- [ ] Landscape camera is rendered correctly.
- [ ] Webcam fullscreen opens and closes correctly.

## Screen shares

- [ ] Each remote share has Start Watching and Stop Watching behavior.
- [ ] Stopping one share does not stop other shares.
- [ ] Multiple screen shares are visible simultaneously.
- [ ] Screen-share fullscreen opens and closes correctly.
- [ ] Windows shared system audio is audible on Android.
- [ ] Each share has independent mute and volume control.
- [ ] Muting one share does not mute voice or another share.
- [ ] Android full-device share is visible on Windows.
- [ ] Windows monitor/window share is visible on Android.

## Profiles and icon

- [ ] Clean installation receives a random bundled profile picture.
- [ ] The avatar can be changed in Settings.
- [ ] The selected avatar persists after restart.
- [ ] The same avatar appears in room preview and connected participant lists.
- [ ] Updated icon appears in Windows shortcuts/installer and Android launcher.

## Layout and lifecycle

- [ ] Windows controls remain above the taskbar during screen sharing.
- [ ] Android controls remain above gesture/navigation bars.
- [ ] Fullscreen exits without losing the call.
- [ ] Android foreground-call notification appears.
- [ ] Disconnect stops microphone, camera, screen share, and foreground service.
- [ ] Rejoining works after disconnecting.

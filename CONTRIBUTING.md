# Contributing

Thank you for improving iCANTtalk.

## Development workflow

1. Create a branch from `main`:

```bash
git checkout -b fix/short-description
```

2. Keep changes focused on one issue.
3. Do not commit generated build directories, dependencies, credentials, keystores, certificates, or `.env` files.
4. Build the affected platform locally.
5. Update documentation and `CHANGELOG.md` when behavior changes.
6. Open a pull request using the provided template.

## Commit style

Use clear imperative messages:

```text
fix: preserve remote participant avatars
feat: add per-stream subscription control
docs: explain Android release signing
```

## Windows checks

```powershell
cd windows
npm install
npm run build
```

For a full installer check:

```powershell
.\BUILD_WINDOWS.ps1
```

## Android checks

Use JDK 17 and Android SDK Platform 36:

```powershell
cd android
.\BUILD_ANDROID.ps1
```

## Django checks

```bash
python -m compileall server/django/icanttalk_livekit
```

Test both token and presence operations against a non-production environment.

## Cross-platform acceptance checks

At minimum, test:

- Windows and Android users in Room 1
- Windows and Android users in Room 2
- Audio in both directions
- Camera in portrait and landscape orientations
- Multiple simultaneous screen shares
- Start/stop watching controls
- Per-share audio mute and volume
- Fullscreen enter and exit
- Room previews before joining
- Avatar consistency after reconnecting

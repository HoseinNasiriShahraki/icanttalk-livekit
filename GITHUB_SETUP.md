# Publish iCANTtalk to GitHub

## Recommended repository name

Use **`icanttalk`** when it is available. It is the shortest, cleanest, and easiest repository URL:

```text
https://github.com/YOUR_USERNAME/icanttalk
```

Other good options:

| Name | Best use |
|---|---|
| `icanttalk` | Best overall and recommended |
| `icanttalk-app` | Clear product repository name |
| `icanttalk-livekit` | Highlights the real-time media stack |
| `icanttalk-cross-platform` | Emphasizes Windows and Android |
| `icanttalk-voice` | Emphasizes voice-first functionality |
| `icanttalk-desktop-mobile` | Explicit but longer |
| `icanttalk-community` | Suitable for a future public/community version |

Avoid spaces, mixed capitalization, version numbers, and names such as `discord-clone`, which weaken the product identity and can create trademark confusion.

## GitHub repository description

Copy this into the repository **About** field:

```text
Cross-platform LiveKit voice rooms for Windows and Android with webcam, multi-stream screen sharing, room presence, avatars, and Django token authentication.
```

## Suggested topics

```text
livekit
webrtc
electron
react
typescript
android
kotlin
jetpack-compose
django
voice-chat
video-conferencing
screen-sharing
windows-app
android-app
```

## Recommended visibility

Create the repository as **Private** first. Confirm that no real API key, API secret, access key, certificate, keystore, password, or `.env` file is present before changing it to Public.

## Option A — GitHub website and Git

1. Sign in to GitHub.
2. Select **New repository**.
3. Enter `icanttalk`.
4. Choose **Private**.
5. Do **not** initialize it with a README, `.gitignore`, or license because this package already contains them.
6. Create the repository.
7. Open PowerShell inside this repository folder and run:

```powershell
git init
git add .
git commit -m "Initial release: iCANTtalk v1.1.1"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/icanttalk.git
git push -u origin main
```

Replace `YOUR_USERNAME` with your GitHub username.

## Option B — GitHub CLI

Install GitHub CLI, then run:

```powershell
gh auth login
gh repo create icanttalk --private --source=. --remote=origin --push
```

## Create the first release

After the source is pushed:

```powershell
git tag -a v1.1.1 -m "iCANTtalk v1.1.1"
git push origin v1.1.1
```

On GitHub:

1. Open **Releases**.
2. Select **Draft a new release**.
3. Select tag `v1.1.1`.
4. Title it `iCANTtalk v1.1.1`.
5. Paste the contents of `RELEASE_NOTES_v1.1.1.md`.
6. Attach the compiled Windows installer and signed Android APK/AAB only after testing them.

Do not attach `.env`, keystores, signing certificates, LiveKit credentials, or access keys.

## Enable useful repository settings

Recommended settings:

- Issues: enabled
- Discussions: optional
- Wiki: disabled unless you plan to maintain it
- Projects: optional
- Secret scanning: enabled when available
- Dependabot alerts: enabled
- Branch protection for `main`: require pull requests and successful CI checks

## Before making the repository public

Run these checks:

```powershell
git status
git ls-files | Select-String -Pattern "\.env$|keystore|\.jks$|\.p12$|\.pfx$|secret|password"
```

Search the source for private values:

```powershell
Get-ChildItem -Recurse -File |
  Select-String -Pattern "LIVEKIT_API_SECRET|ICANTTALK_ACCESS_KEY|BEGIN PRIVATE KEY"
```

Expected matches should be variable names and examples only—not real values.

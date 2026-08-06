# Building

## Windows

### Requirements

- Windows 10/11 x64
- Node.js 22+
- PowerShell

```powershell
cd windows
.\BUILD_WINDOWS.ps1
```

Expected output:

```text
release\iCANTTalk-Setup-1.1.1-x64.exe
```

The generated installer is unsigned until code signing is configured.

## Android

### Requirements

- JDK 17
- Android SDK Platform 36
- Android Build Tools

```powershell
cd android
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\BUILD_ANDROID.ps1
```

Expected output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

### Install test APK

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r `
  ".\app\build\outputs\apk\debug\app-debug.apk"
```

A debug APK is not appropriate for broad public distribution. Configure a private release keystore and build a signed release APK or AAB. Never commit the keystore or passwords.

## CI

GitHub Actions workflows under `.github/workflows` perform Windows, Android, and Django verification. CI artifacts are for testing and are not automatically trusted or signed production releases.

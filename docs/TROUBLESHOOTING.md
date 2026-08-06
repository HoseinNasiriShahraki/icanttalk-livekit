# Troubleshooting

## Room preview reports `Unknown room`

Deploy the current `server/django/icanttalk_livekit/views.py` and restart every Django/Gunicorn process. The current endpoint must process `action: room_presence` before token-room validation.

## Everyone has the same avatar

Update both clients and the Django endpoint, then disconnect and reconnect every participant. Avatar data is included in participant metadata at join time.

## Krisp shows an error

The call can remain connected while enhanced filtering fails. Select **Standard** noise suppression to retain WebRTC echo cancellation and noise suppression. Check device support, package initialization, connectivity, and Android logcat or Electron developer logs.

## Windows token endpoint rejects HTTP

Production endpoints should use HTTPS. Localhost HTTP may be allowed for development. Do not transmit shared access keys or participant tokens over public plain HTTP.

## Android build cannot find SDK Platform 36

Install Android SDK Platform 36 from Android Studio's SDK Manager, then verify:

```powershell
Test-Path "$env:LOCALAPPDATA\Android\Sdk\platforms\android-36\android.jar"
```

## Gradle PKIX certificate error

Check Windows time, VPN/proxy/antivirus TLS inspection, and the active JDK trust store. Do not disable TLS verification or replace Maven repositories with HTTP.

## Windows installer build fails on symbolic links

Enable Windows Developer Mode or run the terminal with the required symbolic-link privilege, clear the affected Electron Builder cache, and rebuild.

# Security Policy

## Supported version

Security fixes currently target the latest release on the `main` branch.

| Version | Supported |
|---|---|
| 1.1.x | Yes |
| 1.0.x | No |

## Reporting a vulnerability

Do not open a public issue containing credentials, exploit details, private endpoints, or personal data. Contact the repository owner privately through GitHub and provide:

- Affected platform and version
- Reproduction steps
- Security impact
- Relevant logs with credentials removed
- Suggested mitigation, when available

## Secrets that must never be committed

- `LIVEKIT_API_SECRET`
- Real `LIVEKIT_API_KEY` values when the repository is public
- `ICANTTALK_ACCESS_KEY`
- Django production `.env` files
- Android keystores and passwords
- Windows code-signing certificates and passwords
- Private TLS keys

## Architecture boundaries

- LiveKit API credentials belong only on the Django server.
- Windows and Android clients receive short-lived participant tokens.
- The shared client access key is not a replacement for per-user authentication.
- Use HTTPS for the token endpoint and WSS/TLS for LiveKit in production.
- Rotate exposed credentials immediately and remove them from Git history.

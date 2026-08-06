# Privacy model

## Data handled by the clients

The clients store locally:

- Display name
- Selected bundled avatar
- Token endpoint URL
- Audio/video preferences
- Stable installation identity
- Encrypted shared application access key

## Real-time media

Microphone, camera, and screen-share tracks are transmitted through the configured LiveKit deployment. The project does not include call recording.

## Django service

The supplied endpoint validates a shared application key, issues LiveKit participant tokens, and queries room presence. It does not store user accounts, chat history, files, or call recordings.

## Deployment responsibility

Operators are responsible for their LiveKit provider, server logs, reverse proxy, retention settings, legal notices, consent requirements, and applicable privacy law. Do not claim end-to-end encryption unless the deployed LiveKit configuration and clients explicitly provide and verify it.

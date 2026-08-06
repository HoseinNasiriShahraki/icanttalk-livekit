# iCANTTalk Django endpoint — v1.1.1

This Django app serves both Windows and Android clients. One POST endpoint issues LiveKit participant tokens and returns lobby room previews.

## Install

Copy `icanttalk_livekit` into the Django project, then install:

```bash
pip install -r requirements.txt
```

Add the app:

```python
INSTALLED_APPS = [
    # ...
    "icanttalk_livekit",
]
```

Include its URLs at the project root:

```python
from django.urls import include, path

urlpatterns = [
    path("", include("icanttalk_livekit.urls")),
]
```

Available endpoint paths:

```text
/api-v2/command/livekit-token/
/api/icanttalk/livekit-token/
```

## Environment

```env
LIVEKIT_URL=wss://your-project.livekit.cloud
LIVEKIT_API_KEY=your-livekit-api-key
LIVEKIT_API_SECRET=your-livekit-api-secret
ICANTTALK_ACCESS_KEY=your-separate-client-access-key
```

Restart Django/Gunicorn after changing the variables.

## Token operation

Header:

```text
X-iCANTTalk-Access-Key: <ICANTTALK_ACCESS_KEY>
```

Body:

```json
{
  "room_name": "Room 1",
  "participant_identity": "stable-device-id",
  "participant_name": "Alice",
  "avatar_id": "07",
  "platform": "windows"
}
```

`room_name` is restricted to Room 1 or Room 2. Valid platforms are `windows` and `android`; valid avatar IDs are `01` through `37`.

## Presence operation

Use the same endpoint and access-key header:

```json
{
  "action": "room_presence",
  "room_names": ["Room 1", "Room 2"],
  "client_version": "1.1.1"
}
```

The response contains Room 1 and Room 2 with each participant's identity, name, avatar, platform, camera status, and screen-share status. Results are cached for three seconds. The default limits are 30 token requests/minute and 300 presence requests/minute per IP/access-key fingerprint.

## Reverse proxy

Make sure the proxy preserves the request body and header. When using `X-Forwarded-For`, configure the proxy to overwrite it rather than accepting a client-supplied chain.

## Cache

Django's local-memory cache works for a single process. For multiple Gunicorn workers or hosts, configure Redis or another shared Django cache so presence caching and rate limiting are consistent.

## Upgrade note

If a client reports `Unknown room` while refreshing previews, the server is still running the older token-only view. Replace the deployed `icanttalk_livekit/views.py` and restart every Gunicorn/Django process. Existing participants must reconnect once so their selected avatar metadata is present on the new LiveKit token.

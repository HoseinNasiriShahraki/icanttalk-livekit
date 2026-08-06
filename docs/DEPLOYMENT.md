# Deployment

## Order

1. Deploy the Django endpoint.
2. Configure LiveKit and application secrets on the server.
3. Restart every Django/Gunicorn worker.
4. Verify token and room-presence operations.
5. Build and install the Windows client.
6. Build and install the Android client.
7. Reconnect existing participants after metadata-related updates.

## Django configuration

Copy `server/django/icanttalk_livekit` into the Django project and install:

```bash
pip install -r server/django/requirements.txt
```

Add:

```python
INSTALLED_APPS = [
    # ...
    "icanttalk_livekit",
]
```

Include:

```python
from django.urls import include, path

urlpatterns = [
    path("", include("icanttalk_livekit.urls")),
]
```

Environment:

```env
LIVEKIT_URL=wss://your-project.livekit.cloud
LIVEKIT_API_KEY=your-livekit-api-key
LIVEKIT_API_SECRET=your-livekit-api-secret
ICANTTALK_ACCESS_KEY=your-separate-client-access-key
```

Use HTTPS for the public endpoint. Keep all real values outside the repository.

## Reverse proxy

Preserve:

- Request body
- `Content-Type`
- `X-iCANTTalk-Access-Key`
- Correct client IP headers when rate limiting is enabled

## Scaling

Django's local-memory cache is acceptable for one process. Configure Redis or another shared cache for multiple Gunicorn workers or multiple servers so presence caching and rate limiting are consistent.

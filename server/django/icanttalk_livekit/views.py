import hashlib
import json
import logging
import os
import re
import secrets
from datetime import timedelta

from asgiref.sync import async_to_sync
from django.core.cache import cache
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_POST
from livekit import api

logger = logging.getLogger(__name__)
ALLOWED_ROOMS = ("Room 1", "Room 2")
ALLOWED_AVATARS = {f"{number:02d}" for number in range(1, 38)}
ALLOWED_PLATFORMS = {"windows", "android"}
IDENTITY_PATTERN = re.compile(r"^[A-Za-z0-9._:-]{8,128}$")
MAX_TOKEN_REQUESTS_PER_MINUTE = 30
MAX_PRESENCE_REQUESTS_PER_MINUTE = 300
PRESENCE_CACHE_KEY = "icanttalk:room-presence:v1"
PRESENCE_CACHE_SECONDS = 3


def _client_ip(request) -> str:
    forwarded = request.META.get("HTTP_X_FORWARDED_FOR", "")
    return forwarded.split(",", 1)[0].strip() if forwarded else request.META.get("REMOTE_ADDR", "unknown")


def _rate_limited(request, access_key: str, bucket: str, limit: int) -> bool:
    fingerprint = hashlib.sha256(access_key.encode("utf-8")).hexdigest()[:16]
    key = f"icanttalk-api:{bucket}:{_client_ip(request)}:{fingerprint}"
    if cache.add(key, 1, timeout=60):
        return False
    try:
        return cache.incr(key) > limit
    except ValueError:
        cache.set(key, 1, timeout=60)
        return False


def _environment():
    values = {
        "url": os.environ.get("LIVEKIT_URL", "").strip(),
        "key": os.environ.get("LIVEKIT_API_KEY", "").strip(),
        "secret": os.environ.get("LIVEKIT_API_SECRET", "").strip(),
    }
    return values if all(values.values()) else None


def _normalize_room_name(value):
    rendered = re.sub(r"[\s_-]+", " ", str(value or "").strip().lower())
    aliases = {
        "room 1": "Room 1",
        "room1": "Room 1",
        "1": "Room 1",
        "room 2": "Room 2",
        "room2": "Room 2",
        "2": "Room 2",
    }
    return aliases.get(rendered)


def _track_source_name(track) -> str:
    source = getattr(track, "source", None)
    source_name = getattr(source, "name", "")
    rendered = str(source_name or source).upper().replace("TRACK_SOURCE_", "")
    return rendered.replace("-", "_").replace(" ", "_")


def _track_source_matches(track, *names: str) -> bool:
    source_name = _track_source_name(track)
    return source_name in {name.upper() for name in names}


async def _fetch_room_presence():
    env = _environment()
    if not env:
        raise RuntimeError("LiveKit environment variables are incomplete.")

    client = api.LiveKitAPI(url=env["url"], api_key=env["key"], api_secret=env["secret"])
    rooms = []
    try:
        for room_name in ALLOWED_ROOMS:
            try:
                response = await client.room.list_participants(api.ListParticipantsRequest(room=room_name))
                participants = []
                for participant in response.participants:
                    try:
                        metadata = json.loads(participant.metadata or "{}")
                    except (TypeError, json.JSONDecodeError):
                        metadata = {}
                    avatar_id = str(metadata.get("avatar_id", "01"))
                    if avatar_id not in ALLOWED_AVATARS:
                        avatar_id = "01"
                    platform = str(metadata.get("platform", "unknown"))
                    if platform not in ALLOWED_PLATFORMS:
                        platform = "unknown"
                    tracks = list(getattr(participant, "tracks", []))
                    participants.append(
                        {
                            "identity": participant.identity,
                            "name": participant.name or participant.identity,
                            "avatar_id": avatar_id,
                            "platform": platform,
                            "camera": any(_track_source_matches(track, "CAMERA") for track in tracks),
                            "screen_share": any(_track_source_matches(track, "SCREEN_SHARE") for track in tracks),
                        }
                    )
                rooms.append({"name": room_name, "participants": participants})
            except Exception as error:
                # An inactive room can be absent. Return it as empty without breaking the other room.
                logger.info("Unable to list participants for %s: %s", room_name, error)
                rooms.append({"name": room_name, "participants": []})
    finally:
        await client.aclose()
    return rooms


@csrf_exempt
@require_POST
def livekit_token(request):
    configured_access_key = os.environ.get("ICANTTALK_ACCESS_KEY", "")
    supplied_access_key = request.headers.get("X-iCANTTalk-Access-Key", "")
    if not configured_access_key:
        return JsonResponse({"error": "Server access key is not configured."}, status=503)
    if not secrets.compare_digest(supplied_access_key, configured_access_key):
        return JsonResponse({"error": "Invalid access key."}, status=403)
    try:
        body = json.loads(request.body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        return JsonResponse({"error": "Request body must be valid JSON."}, status=400)
    if not isinstance(body, dict):
        return JsonResponse({"error": "Request body must be a JSON object."}, status=400)

    action = str(body.get("action") or body.get("operation") or "").strip().lower()
    if action in {"room_presence", "presence", "list_rooms", "room-preview"}:
        if _rate_limited(request, supplied_access_key, "presence", MAX_PRESENCE_REQUESTS_PER_MINUTE):
            return JsonResponse({"error": "Too many room-preview requests. Try again shortly."}, status=429)
        try:
            rooms = cache.get(PRESENCE_CACHE_KEY)
            if rooms is None:
                rooms = async_to_sync(_fetch_room_presence)()
                cache.set(PRESENCE_CACHE_KEY, rooms, timeout=PRESENCE_CACHE_SECONDS)
            return JsonResponse({"api_version": "1.1.1", "rooms": rooms}, status=200)
        except Exception:
            logger.exception("Unable to load LiveKit room presence")
            return JsonResponse({"error": "Unable to load room previews."}, status=502)

    if _rate_limited(request, supplied_access_key, "token", MAX_TOKEN_REQUESTS_PER_MINUTE):
        return JsonResponse({"error": "Too many token requests. Try again shortly."}, status=429)

    room_name = _normalize_room_name(body.get("room_name"))
    participant_name = str(body.get("participant_name", "")).strip()
    participant_identity = str(body.get("participant_identity", "")).strip()
    avatar_id = str(body.get("avatar_id", "01"))
    platform = str(body.get("platform", "unknown"))

    if room_name not in ALLOWED_ROOMS:
        return JsonResponse({"error": "Unknown room. Allowed values are Room 1 and Room 2."}, status=400)
    if not 1 <= len(participant_name) <= 40:
        return JsonResponse({"error": "Participant name must contain 1 to 40 characters."}, status=400)
    if not IDENTITY_PATTERN.fullmatch(participant_identity):
        return JsonResponse({"error": "Invalid participant identity."}, status=400)
    if avatar_id not in ALLOWED_AVATARS:
        avatar_id = "01"
    if platform not in ALLOWED_PLATFORMS:
        platform = "unknown"

    env = _environment()
    if not env:
        return JsonResponse({"error": "LiveKit environment variables are incomplete."}, status=503)

    metadata = json.dumps({"avatar_id": avatar_id, "platform": platform}, separators=(",", ":"))
    try:
        token = (
            api.AccessToken(api_key=env["key"], api_secret=env["secret"])
            .with_identity(participant_identity)
            .with_name(participant_name)
            .with_metadata(metadata)
            .with_grants(
                api.VideoGrants(
                    room_join=True,
                    room=room_name,
                    can_publish=True,
                    can_subscribe=True,
                    can_publish_data=False,
                )
            )
            .with_ttl(timedelta(minutes=20))
            .to_jwt()
        )
    except Exception:
        logger.exception("Unable to generate LiveKit token")
        return JsonResponse({"error": "Unable to generate a LiveKit token."}, status=500)

    return JsonResponse({"server_url": env["url"], "participant_token": token}, status=201)

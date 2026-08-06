# Django API contract

## Endpoint

Default routes supplied by the app:

```text
/api-v2/command/livekit-token/
/api/icanttalk/livekit-token/
```

All operations use:

```http
POST /api-v2/command/livekit-token/
Content-Type: application/json
X-iCANTTalk-Access-Key: <shared-application-key>
```

## Token request

```json
{
  "room_name": "Room 1",
  "participant_identity": "stable-device-identity",
  "participant_name": "Alice",
  "avatar_id": "07",
  "platform": "windows"
}
```

Constraints:

- `room_name`: `Room 1` or `Room 2`
- `avatar_id`: `01` through `37`
- `platform`: `windows` or `android`

Success:

```json
{
  "server_url": "wss://your-project.livekit.cloud",
  "participant_token": "eyJ..."
}
```

## Room-presence request

```json
{
  "action": "room_presence",
  "room_names": ["Room 1", "Room 2"],
  "client_version": "1.1.1"
}
```

Success:

```json
{
  "api_version": "1.1.1",
  "rooms": [
    {
      "name": "Room 1",
      "participants": [
        {
          "identity": "...",
          "name": "Alice",
          "avatar_id": "07",
          "platform": "windows",
          "camera": true,
          "screen_share": false
        }
      ]
    },
    {
      "name": "Room 2",
      "participants": []
    }
  ]
}
```

## Common errors

- `403 Invalid access key`: client and server access keys differ.
- `400 Unknown room`: token request contains a room other than Room 1 or Room 2, or an older endpoint incorrectly handles a presence request.
- `503 LiveKit environment variables are incomplete`: server configuration is missing.
- `429 Too many requests`: rate limit exceeded.

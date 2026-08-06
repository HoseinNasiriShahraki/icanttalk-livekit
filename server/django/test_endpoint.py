import argparse
import json
import urllib.request


def post(endpoint: str, access_key: str, body: dict) -> dict:
    request = urllib.request.Request(
        endpoint,
        data=json.dumps(body).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "X-iCANTTalk-Access-Key": access_key,
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=15) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--endpoint", required=True)
    parser.add_argument("--access-key", required=True)
    parser.add_argument("--presence", action="store_true")
    parser.add_argument("--room", default="Room 1", choices=("Room 1", "Room 2"))
    args = parser.parse_args()
    if args.presence:
        body = {"action": "room_presence"}
    else:
        body = {
            "room_name": args.room,
            "participant_identity": "endpoint-test-device-0001",
            "participant_name": "Endpoint Test",
            "avatar_id": "01",
            "platform": "windows",
        }
    print(json.dumps(post(args.endpoint, args.access_key, body), indent=2))


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
import argparse
import json
import uuid
import urllib.request
import urllib.error


def main() -> int:
    parser = argparse.ArgumentParser(description="Test the Django endpoint used by iCANTtalk.")
    parser.add_argument("--endpoint", required=True)
    parser.add_argument("--access-key", required=True)
    parser.add_argument("--room", choices=["Room 1", "Room 2"], default="Room 1")
    parser.add_argument("--name", default="Android API Test")
    args = parser.parse_args()

    payload = json.dumps(
        {
            "room_name": args.room,
            "participant_name": args.name,
            "participant_identity": f"android-test-{uuid.uuid4()}",
        }
    ).encode("utf-8")

    request = urllib.request.Request(
        args.endpoint,
        method="POST",
        data=payload,
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
            "X-iCANTTalk-Access-Key": args.access_key,
        },
    )

    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            body = json.loads(response.read().decode("utf-8"))
            token = body.get("participant_token") or body.get("token")
            server_url = body.get("server_url") or body.get("url") or body.get("livekit_url")
            if not token or not server_url:
                raise RuntimeError("Response is missing server_url or participant_token.")
            print("Endpoint is compatible.")
            print(f"Server URL: {server_url}")
            print(f"Token received: {token[:24]}…")
            return 0
    except urllib.error.HTTPError as error:
        print(f"HTTP {error.code}: {error.read().decode('utf-8', errors='replace')}")
        return 1
    except Exception as error:
        print(f"Test failed: {error}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

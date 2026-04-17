import json
import os
import urllib.error
import urllib.request


def handler(event, context):
    """EventBridge-triggered scheduler. POSTs to the EC2 internal feed-sync endpoint.

    Expects two environment variables, both set by Terraform:
      APP_BASE_URL     — e.g. http://10.0.1.23:8085 (EC2 private IP, VPC-internal)
      INTERNAL_API_KEY — shared secret matching memoryvault.internal.api-key on EC2
    """
    base_url = os.environ["APP_BASE_URL"].rstrip("/")
    api_key = os.environ["INTERNAL_API_KEY"]

    url = f"{base_url}/api/internal/sync/feeds"
    req = urllib.request.Request(url, method="POST")
    req.add_header("X-Internal-Key", api_key)
    req.add_header("Content-Type", "application/json")

    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = json.loads(resp.read() or b"{}")
            print(f"Feed sync triggered: status={resp.status} body={body}")
            return {"statusCode": resp.status, "body": body}
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", errors="replace")
        print(f"Feed sync HTTP error: status={e.code} body={detail}")
        raise
    except Exception as e:
        print(f"Feed sync failed: {type(e).__name__}: {e}")
        raise

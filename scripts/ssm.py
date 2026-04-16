#!/usr/bin/env python3
"""Run a shell command on the MemoryVault EC2 instance via SSM.

Usage:
  ./scripts/ssm.py <command> [args ...]

Examples:
  ./scripts/ssm.py docker ps -a
  ./scripts/ssm.py "grep COGNITO /etc/memoryvault/env"
  ./scripts/ssm.py "docker logs memoryvault --tail 50"

Resolves the EC2 instance at runtime via the tag Name=memoryvault-app
(matches the deploy workflow, so future instance replacements don't
break this script). Requires `aws` CLI configured with credentials
that can run SSM commands on the instance.
"""
from __future__ import annotations

import json
import subprocess
import sys
import time


def resolve_instance_id() -> str:
    out = subprocess.check_output(
        [
            "aws", "ec2", "describe-instances",
            "--filters",
            "Name=tag:Name,Values=memoryvault-app",
            "Name=instance-state-name,Values=running",
            "--query", "Reservations[0].Instances[0].InstanceId",
            "--output", "text",
        ],
        text=True,
    ).strip()
    if not out or out == "None":
        raise SystemExit("No running EC2 instance tagged memoryvault-app found.")
    return out


def run_remote(command: str, instance_id: str, timeout_s: int = 120) -> int:
    params = json.dumps({"commands": [command]})
    cmd_id = subprocess.check_output(
        [
            "aws", "ssm", "send-command",
            "--instance-ids", instance_id,
            "--document-name", "AWS-RunShellScript",
            "--parameters", params,
            "--query", "Command.CommandId",
            "--output", "text",
        ],
        text=True,
    ).strip()

    deadline = time.time() + timeout_s
    while time.time() < deadline:
        time.sleep(2)
        raw = subprocess.run(
            [
                "aws", "ssm", "get-command-invocation",
                "--command-id", cmd_id,
                "--instance-id", instance_id,
            ],
            capture_output=True, text=True,
        )
        if raw.returncode != 0:
            # "InvocationDoesNotExist" while SSM is still routing — retry
            continue
        result = json.loads(raw.stdout)
        status = result.get("Status", "Pending")
        if status in ("Success", "Failed", "Cancelled", "TimedOut"):
            stdout = result.get("StandardOutputContent", "") or ""
            stderr = result.get("StandardErrorContent", "") or ""
            if stdout:
                sys.stdout.write(stdout)
                if not stdout.endswith("\n"):
                    sys.stdout.write("\n")
            if stderr:
                sys.stderr.write(stderr)
                if not stderr.endswith("\n"):
                    sys.stderr.write("\n")
            return 0 if status == "Success" else 1

    raise SystemExit(f"SSM command did not complete within {timeout_s}s.")


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__, file=sys.stderr)
        return 2
    command = " ".join(sys.argv[1:])
    instance_id = resolve_instance_id()
    return run_remote(command, instance_id)


if __name__ == "__main__":
    sys.exit(main())

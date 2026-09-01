#!/usr/bin/env python3
"""Send one authenticated Minecraft RCON command without third-party packages.

The command response is written to stdout.  Keeping this helper dependency-free makes it
usable in the packaged-suite jobs before any Python packages have been installed.
"""

from __future__ import annotations

import os
import socket
import struct
import sys
import time
from collections.abc import Callable


DEFAULT_TIMEOUT_SECONDS = 30.0
TIMEOUT_ENVIRONMENT_VARIABLE = "PACKAGED_SUITE_RCON_TIMEOUT_SECONDS"
DEFAULT_ATTEMPTS = 3
ATTEMPTS_ENVIRONMENT_VARIABLE = "PACKAGED_SUITE_RCON_ATTEMPTS"
DEFAULT_RETRY_DELAY_SECONDS = 0.25


def packet(request_id: int, packet_type: int, payload: str) -> bytes:
    body = struct.pack("<ii", request_id, packet_type) + payload.encode("utf-8") + b"\0\0"
    return struct.pack("<i", len(body)) + body


def receive(sock: socket.socket) -> tuple[int, int, bytes]:
    raw_length = sock.recv(4)
    if len(raw_length) != 4:
        raise RuntimeError("RCON response ended before its length prefix")
    (length,) = struct.unpack("<i", raw_length)
    data = bytearray()
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise RuntimeError("RCON response ended before its packet body")
        data.extend(chunk)
    request_id, packet_type = struct.unpack("<ii", data[:8])
    return request_id, packet_type, bytes(data[8:-2])


def run_command(
    host: str,
    port: int,
    password: str,
    request_id: int,
    command: str,
    timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS,
) -> str:
    """Authenticate, execute one command, and return its first RCON response packet."""

    with socket.create_connection((host, port), timeout=timeout_seconds) as connection:
        connection.settimeout(timeout_seconds)
        connection.sendall(packet(request_id, 3, password))
        response_id, _, _ = receive(connection)
        if response_id != request_id:
            raise RuntimeError("Minecraft RCON authentication failed")

        command_request_id = request_id + 1
        connection.sendall(packet(command_request_id, 2, command))
        response_id, _, payload = receive(connection)
        if response_id != command_request_id:
            raise RuntimeError(
                "Minecraft RCON returned a response for an unexpected request "
                f"({response_id}, expected {command_request_id})"
            )
        return payload.decode("utf-8", errors="replace")


def run_command_with_retries(
    host: str,
    port: int,
    password: str,
    request_id: int,
    command: str,
    timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS,
    attempts: int = DEFAULT_ATTEMPTS,
    retry_delay_seconds: float = DEFAULT_RETRY_DELAY_SECONDS,
    on_retry: Callable[[int, int, BaseException], None] | None = None,
) -> str:
    """Execute a command, replacing a failed RCON connection a bounded number of times.

    Forge 26.2 can occasionally leave one short-lived RCON client waiting forever even while
    the server thread and a fresh RCON connection remain healthy.  Each retry therefore opens a
    completely new authenticated connection.  The packaged-suite commands are deliberately
    idempotent so replaying a command whose response was lost is safe.
    """

    if attempts < 1:
        raise ValueError("RCON attempts must be at least one")

    for attempt in range(1, attempts + 1):
        try:
            return run_command(
                host,
                port,
                password,
                request_id,
                command,
                timeout_seconds,
            )
        except (OSError, RuntimeError) as exception:
            if attempt >= attempts:
                raise
            if on_retry is not None:
                on_retry(attempt, attempts, exception)
            if retry_delay_seconds > 0:
                time.sleep(retry_delay_seconds)

    raise AssertionError("RCON retry loop exhausted without returning or raising")


def configured_timeout(environment: dict[str, str] | None = None) -> float:
    """Return the validated per-connection timeout used by the packaged-suite harness."""

    values = os.environ if environment is None else environment
    raw_timeout = values.get(TIMEOUT_ENVIRONMENT_VARIABLE, str(DEFAULT_TIMEOUT_SECONDS))
    try:
        timeout_seconds = float(raw_timeout)
    except ValueError as exception:
        raise ValueError(
            f"{TIMEOUT_ENVIRONMENT_VARIABLE} must be a number, got {raw_timeout!r}"
        ) from exception
    if not 1.0 <= timeout_seconds <= 300.0:
        raise ValueError(
            f"{TIMEOUT_ENVIRONMENT_VARIABLE} must be between 1 and 300 seconds, "
            f"got {raw_timeout!r}"
        )
    return timeout_seconds


def configured_attempts(environment: dict[str, str] | None = None) -> int:
    """Return the validated number of fresh RCON connections allowed per command."""

    values = os.environ if environment is None else environment
    raw_attempts = values.get(ATTEMPTS_ENVIRONMENT_VARIABLE, str(DEFAULT_ATTEMPTS))
    try:
        attempts = int(raw_attempts)
    except ValueError as exception:
        raise ValueError(
            f"{ATTEMPTS_ENVIRONMENT_VARIABLE} must be an integer, got {raw_attempts!r}"
        ) from exception
    if not 1 <= attempts <= 10:
        raise ValueError(
            f"{ATTEMPTS_ENVIRONMENT_VARIABLE} must be between 1 and 10, "
            f"got {raw_attempts!r}"
        )
    return attempts


def main() -> None:
    if len(sys.argv) != 6:
        raise SystemExit("usage: rcon-command.py HOST PORT PASSWORD REQUEST_ID COMMAND")
    host, raw_port, password, raw_request_id, command = sys.argv[1:]
    request_id = int(raw_request_id)
    port = int(raw_port)

    def report_retry(attempt: int, attempts: int, exception: BaseException) -> None:
        print(
            f"Transient RCON connection failure after attempt {attempt}/{attempts}: "
            f"{exception}; opening a fresh connection",
            file=sys.stderr,
        )

    try:
        response = run_command_with_retries(
            host,
            port,
            password,
            request_id,
            command,
            configured_timeout(),
            configured_attempts(),
            on_retry=report_retry,
        )
    except (OSError, RuntimeError, ValueError) as exception:
        raise SystemExit(
            f"RCON command {command!r} to {host}:{port} failed: {exception}"
        ) from exception
    if response:
        print(response)


if __name__ == "__main__":
    main()

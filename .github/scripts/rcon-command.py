#!/usr/bin/env python3
"""Send one authenticated Minecraft RCON command without third-party packages.

The command response is written to stdout.  Keeping this helper dependency-free makes it
usable in the packaged-suite jobs before any Python packages have been installed.
"""

from __future__ import annotations

import socket
import struct
import sys


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
) -> str:
    """Authenticate, execute one command, and return its first RCON response packet."""

    with socket.create_connection((host, port), timeout=10) as connection:
        connection.settimeout(10)
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


def main() -> None:
    if len(sys.argv) != 6:
        raise SystemExit("usage: rcon-command.py HOST PORT PASSWORD REQUEST_ID COMMAND")
    host, raw_port, password, raw_request_id, command = sys.argv[1:]
    request_id = int(raw_request_id)
    response = run_command(host, int(raw_port), password, request_id, command)
    if response:
        print(response)


if __name__ == "__main__":
    main()

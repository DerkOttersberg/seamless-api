#!/usr/bin/env python3
"""Send one authenticated Minecraft RCON command without third-party packages."""

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


def main() -> None:
    if len(sys.argv) != 6:
        raise SystemExit("usage: rcon-command.py HOST PORT PASSWORD REQUEST_ID COMMAND")
    host, raw_port, password, raw_request_id, command = sys.argv[1:]
    request_id = int(raw_request_id)
    with socket.create_connection((host, int(raw_port)), timeout=10) as connection:
        connection.settimeout(10)
        connection.sendall(packet(request_id, 3, password))
        response_id, _, _ = receive(connection)
        if response_id != request_id:
            raise RuntimeError("Minecraft RCON authentication failed")
        connection.sendall(packet(request_id + 1, 2, command))


if __name__ == "__main__":
    main()

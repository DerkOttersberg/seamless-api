#!/usr/bin/env python3
"""Protocol-level tests for the dependency-free RCON helper."""

from __future__ import annotations

import importlib.util
import socket
import struct
import threading
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "rcon-command.py"
SPEC = importlib.util.spec_from_file_location("rcon_command", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
RCON = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(RCON)


def read_packet(connection: socket.socket) -> tuple[int, int, str]:
    request_id, packet_type, payload = RCON.receive(connection)
    return request_id, packet_type, payload.decode("utf-8")


class RconProtocolTest(unittest.TestCase):
    def test_packet_round_trip_preserves_unicode(self) -> None:
        left, right = socket.socketpair()
        self.addCleanup(left.close)
        self.addCleanup(right.close)
        left.sendall(RCON.packet(17, 2, "say héllo"))
        self.assertEqual((17, 2, "say héllo"), read_packet(right))

    def test_receive_reassembles_fragmented_body(self) -> None:
        left, right = socket.socketpair()
        self.addCleanup(left.close)
        self.addCleanup(right.close)
        encoded = RCON.packet(3, 0, "fragmented")
        for byte in encoded:
            left.sendall(bytes((byte,)))
        self.assertEqual((3, 0, "fragmented"), read_packet(right))

    def test_run_command_returns_server_response(self) -> None:
        listener = socket.socket()
        self.addCleanup(listener.close)
        listener.bind(("127.0.0.1", 0))
        listener.listen(1)
        port = listener.getsockname()[1]
        server_error: list[BaseException] = []

        def serve() -> None:
            try:
                connection, _ = listener.accept()
                with connection:
                    auth_id, auth_type, password = read_packet(connection)
                    self.assertEqual((40, 3, "secret"), (auth_id, auth_type, password))
                    connection.sendall(RCON.packet(auth_id, 2, ""))
                    command_id, command_type, command = read_packet(connection)
                    self.assertEqual((41, 2, "data get block 0 0 0"), (command_id, command_type, command))
                    connection.sendall(RCON.packet(command_id, 0, "fixture-response"))
            except BaseException as exception:  # pragma: no cover - reported in the caller
                server_error.append(exception)

        thread = threading.Thread(target=serve)
        thread.start()
        response = RCON.run_command("127.0.0.1", port, "secret", 40, "data get block 0 0 0")
        thread.join(timeout=5)
        self.assertFalse(thread.is_alive())
        if server_error:
            raise server_error[0]
        self.assertEqual("fixture-response", response)

    def test_run_command_rejects_wrong_response_id(self) -> None:
        listener = socket.socket()
        self.addCleanup(listener.close)
        listener.bind(("127.0.0.1", 0))
        listener.listen(1)
        port = listener.getsockname()[1]

        def serve() -> None:
            connection, _ = listener.accept()
            with connection:
                auth_id, _, _ = read_packet(connection)
                connection.sendall(RCON.packet(auth_id, 2, ""))
                read_packet(connection)
                connection.sendall(RCON.packet(999, 0, "wrong"))

        thread = threading.Thread(target=serve)
        thread.start()
        with self.assertRaisesRegex(RuntimeError, "unexpected request"):
            RCON.run_command("127.0.0.1", port, "secret", 10, "list")
        thread.join(timeout=5)


if __name__ == "__main__":
    unittest.main()

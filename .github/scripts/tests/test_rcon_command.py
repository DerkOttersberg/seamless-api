#!/usr/bin/env python3
"""Protocol-level tests for the dependency-free RCON helper."""

from __future__ import annotations

import importlib.util
import socket
import struct
import threading
import unittest
from unittest import mock
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
    def test_configured_timeout_defaults_to_thirty_seconds(self) -> None:
        self.assertEqual(30.0, RCON.configured_timeout({}))

    def test_configured_timeout_accepts_bounded_override(self) -> None:
        self.assertEqual(
            45.5,
            RCON.configured_timeout({"PACKAGED_SUITE_RCON_TIMEOUT_SECONDS": "45.5"}),
        )

    def test_configured_timeout_rejects_invalid_override(self) -> None:
        for invalid in ("not-a-number", "0", "301"):
            with self.subTest(invalid=invalid):
                with self.assertRaisesRegex(ValueError, "PACKAGED_SUITE_RCON_TIMEOUT_SECONDS"):
                    RCON.configured_timeout(
                        {"PACKAGED_SUITE_RCON_TIMEOUT_SECONDS": invalid}
                    )

    def test_configured_attempts_defaults_to_three(self) -> None:
        self.assertEqual(3, RCON.configured_attempts({}))

    def test_configured_attempts_accepts_bounded_override(self) -> None:
        self.assertEqual(
            5,
            RCON.configured_attempts({"PACKAGED_SUITE_RCON_ATTEMPTS": "5"}),
        )

    def test_configured_attempts_rejects_invalid_override(self) -> None:
        for invalid in ("not-an-integer", "0", "11"):
            with self.subTest(invalid=invalid):
                with self.assertRaisesRegex(ValueError, "PACKAGED_SUITE_RCON_ATTEMPTS"):
                    RCON.configured_attempts(
                        {"PACKAGED_SUITE_RCON_ATTEMPTS": invalid}
                    )

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

    def test_run_command_retries_on_a_fresh_connection(self) -> None:
        retry_events: list[tuple[int, int, str]] = []
        with mock.patch.object(
            RCON,
            "run_command",
            side_effect=[socket.timeout("timed out"), "second-connection-response"],
        ) as run_command:
            response = RCON.run_command_with_retries(
                "127.0.0.1",
                25575,
                "secret",
                50,
                "list",
                timeout_seconds=1.0,
                attempts=3,
                retry_delay_seconds=0,
                on_retry=lambda attempt, attempts, exception: retry_events.append(
                    (attempt, attempts, str(exception))
                ),
            )

        self.assertEqual("second-connection-response", response)
        self.assertEqual(2, run_command.call_count)
        self.assertEqual([(1, 3, "timed out")], retry_events)

    def test_run_command_stops_after_configured_attempts(self) -> None:
        with mock.patch.object(
            RCON,
            "run_command",
            side_effect=RuntimeError("bad response"),
        ) as run_command:
            with self.assertRaisesRegex(RuntimeError, "bad response"):
                RCON.run_command_with_retries(
                    "127.0.0.1",
                    25575,
                    "secret",
                    60,
                    "list",
                    attempts=2,
                    retry_delay_seconds=0,
                )

        self.assertEqual(2, run_command.call_count)


if __name__ == "__main__":
    unittest.main()

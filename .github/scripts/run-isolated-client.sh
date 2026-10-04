#!/usr/bin/env bash
# Client QA runs only on a private virtual display; never the user's desktop.
set -euo pipefail

if (($# == 0)); then
  printf 'Usage: %s <client command> [arguments...]\n' "$0" >&2
  exit 2
fi
for command_name in Xvfb xvfb-run timeout nice taskset flock; do
  command -v "$command_name" >/dev/null || {
    printf 'Missing isolated-client prerequisite: %s\n' "$command_name" >&2
    exit 2
  }
done

exec 9>"${ISOLATED_CLIENT_LOCK_FILE:-/tmp/seamless-isolated-minecraft.lock}"
lock_wait_seconds="${ISOLATED_CLIENT_LOCK_WAIT_SECONDS:-50}"
[[ "$lock_wait_seconds" =~ ^[0-9]+$ ]] && ((lock_wait_seconds >= 1 && lock_wait_seconds <= 900)) || exit 2
flock --wait "$lock_wait_seconds" 9 || {
  printf 'Another isolated Minecraft client remained busy for %s seconds.\n' "$lock_wait_seconds" >&2
  exit 2
}

timeout_seconds="${ISOLATED_CLIENT_TIMEOUT_SECONDS:-600}"
if [[ ! "$timeout_seconds" =~ ^[0-9]+$ ]] ||
   ((timeout_seconds < 30 || timeout_seconds > 1800)); then
  printf 'Invalid isolated client timeout.\n' >&2
  exit 2
fi

# Remove WSLg's desktop targets before creating a private X server/Xauthority.
# Two logical CPUs, software rendering, one process tree, bounded duration.
exec env -u DISPLAY -u WAYLAND_DISPLAY -u XAUTHORITY \
  LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe SDL_VIDEODRIVER=x11 \
  nice -n 10 taskset -c 0,1 \
  timeout --signal=TERM --kill-after=20s "${timeout_seconds}s" \
  xvfb-run -a -s '-screen 0 1280x720x24 -nolisten tcp -noreset' \
  bash -c '
    set -euo pipefail
    [[ "$DISPLAY" =~ ^:[0-9]+$ ]] && [[ "$DISPLAY" != ":0" ]] || {
      printf "Refusing a non-isolated display: %s\n" "$DISPLAY" >&2
      exit 2
    }
    [[ -z "${WAYLAND_DISPLAY:-}" ]] || exit 2
    printf "ISOLATED_CLIENT_DISPLAY=%s (private Xvfb; no desktop input)\n" "$DISPLAY"
    exec "$@"
  ' isolated-client "$@"

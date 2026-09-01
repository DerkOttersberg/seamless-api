#!/usr/bin/env bash
set -Eeuo pipefail

readonly MINECRAFT_VERSION="26.2"
readonly FABRIC_LOADER_VERSION="0.19.3"
readonly FABRIC_INSTALLER_VERSION="1.1.2"
readonly FABRIC_API_VERSION="0.159.0+26.2"
readonly FORGE_VERSION="26.2-65.1.3"
readonly NEOFORGE_VERSION="26.2.0.75"
readonly RCON_PASSWORD="release-hardening-local-only"
readonly WORKBENCH_POS="0 200 0"
readonly HISTORICAL_WORKBENCH_POS="512 200 0"

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly RCON_PORT="${PACKAGED_SUITE_RCON_PORT:-25575}"
readonly RCON_TIMEOUT_SECONDS="${PACKAGED_SUITE_RCON_TIMEOUT_SECONDS:-30}"
export PACKAGED_SUITE_RCON_TIMEOUT_SECONDS="$RCON_TIMEOUT_SECONDS"

loader="${1:?loader argument is required}"
jars_dir="${2:?runtime-jar directory argument is required}"
run_dir="${3:?run directory argument is required}"
python_command=python3
if ! command -v "$python_command" >/dev/null 2>&1; then
  python_command=python
fi

case "$loader" in
  fabric|forge|neoforge) ;;
  *)
    echo "Unsupported loader: $loader" >&2
    exit 2
    ;;
esac
if [[ ! "$RCON_PORT" =~ ^[0-9]+$ ]] || ((RCON_PORT < 1024 || RCON_PORT > 65535)); then
  echo "Invalid packaged-suite RCON port: $RCON_PORT" >&2
  exit 2
fi
if [[ ! "$RCON_TIMEOUT_SECONDS" =~ ^[0-9]+([.][0-9]+)?$ ]] ||
    ! "$python_command" -B - "$RCON_TIMEOUT_SECONDS" <<'PY'
import sys
timeout = float(sys.argv[1])
raise SystemExit(0 if 1 <= timeout <= 300 else 1)
PY
then
  echo "Invalid packaged-suite RCON timeout: $RCON_TIMEOUT_SECONDS" >&2
  exit 2
fi
jars_dir="$(realpath "$jars_dir")"
if [[ -e "$run_dir" ]]; then
  if [[ ! -d "$run_dir" ]]; then
    echo "Packaged-suite run path is not a directory: $run_dir" >&2
    exit 2
  fi
  if [[ -n "$(find "$run_dir" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
    echo "Packaged-suite run directory must be empty: $run_dir" >&2
    exit 2
  fi
else
  mkdir -p "$run_dir"
fi
run_dir="$(realpath "$run_dir")"
if [[ "$run_dir" == "/" || "$run_dir" == "$jars_dir" ]]; then
  echo "Unsafe packaged-suite run directory: $run_dir" >&2
  exit 2
fi

mkdir -p "$run_dir/mods" "$run_dir/qa-logs" "$run_dir/config"
mapfile -t runtime_jars < <(find "$jars_dir" -maxdepth 1 -type f -name "*-${loader}.jar" ! -name "*-sources.jar" | sort)
if [[ "${#runtime_jars[@]}" -ne 5 ]]; then
  printf 'Expected exactly five %s runtime jars, found %s:\n' "$loader" "${#runtime_jars[@]}" >&2
  printf '  %s\n' "${runtime_jars[@]:-<none>}" >&2
  exit 1
fi
cp "${runtime_jars[@]}" "$run_dir/mods/"
cp \
  "$script_dir/../fixtures/workbench-historical/seamlessdeconstructor.json" \
  "$run_dir/config/seamlessdeconstructor.json"

rcon_request_id=1000
historical_workbench_snbt="$(tr -d '\r\n' < "$script_dir/../fixtures/workbench-historical/block-entity.snbt")"

# Refuse to run when another local process is already listening.  Besides producing a clearer
# diagnostic, this prevents a stale development server with the same password from satisfying
# assertions intended for the newly launched process.
"$python_command" -B - "$RCON_PORT" <<'PY'
import socket
import sys

port = int(sys.argv[1])
with socket.socket() as probe:
    probe.bind(("127.0.0.1", port))
PY

rcon_command() {
  local command="$1"
  local request_id="$rcon_request_id"
  rcon_request_id=$((rcon_request_id + 2))
  PACKAGED_SUITE_RCON_ATTEMPTS="${PACKAGED_SUITE_RCON_ATTEMPTS:-3}" \
    "$python_command" "$script_dir/rcon-command.py" \
    127.0.0.1 "$RCON_PORT" "$RCON_PASSWORD" "$request_id" "$command"
}

assert_rcon_condition() {
  local score_holder="$1"
  local condition="$2"
  local description="$3"
  local response

  rcon_command "scoreboard players set ${score_holder} suite_qa 0" >/dev/null
  rcon_command "execute ${condition} run scoreboard players set ${score_holder} suite_qa 1" >/dev/null
  response="$(rcon_command "scoreboard players get ${score_holder} suite_qa")"
  if [[ ! "$response" =~ has[[:space:]]+1[[:space:]] ]]; then
    echo "Packaged-suite assertion failed: ${description}" >&2
    echo "RCON response: ${response}" >&2
    return 1
  fi
}

verify_workbench_config_migration() {
  local fixture="$script_dir/../fixtures/workbench-historical/seamlessdeconstructor.json"
  local legacy="$run_dir/config/seamlessdeconstructor.json"
  local backup="$run_dir/config/seamlessdeconstructor.json.bak"
  local canonical="$run_dir/config/seamless-deconstructing-workbench.json"

  cmp --silent "$fixture" "$legacy"
  cmp --silent "$fixture" "$backup"
  "$python_command" -B - "$canonical" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
actual = json.loads(path.read_text(encoding="utf-8"))
expected = {"processTicks": 20, "minLossPercent": 0, "maxLossPercent": 0}
if actual != expected:
    raise SystemExit(f"unexpected migrated Workbench config in {path}: {actual!r}")
PY
}

capture_persistent_state() {
  local destination="$1"
  {
    rcon_command "data get block ${WORKBENCH_POS} PendingOperation"
    rcon_command "data get block ${WORKBENCH_POS} Progress"
    rcon_command "data get block ${WORKBENCH_POS} MaxProgress"
    rcon_command "data get block ${WORKBENCH_POS} MachineState"
    rcon_command "data get block ${WORKBENCH_POS} BlockReason"
    rcon_command "data get block ${HISTORICAL_WORKBENCH_POS} Items"
    rcon_command "data get block ${HISTORICAL_WORKBENCH_POS} Progress"
    rcon_command "data get block ${HISTORICAL_WORKBENCH_POS} MaxProgress"
    rcon_command "data get entity @e[type=swordthrow:thrown_sword,tag=suite_embedded,limit=1] Item"
    rcon_command "data get entity @e[type=swordthrow:thrown_sword,tag=suite_embedded,limit=1] ThrownStackCount"
    rcon_command "data get entity @e[type=swordthrow:thrown_sword,tag=suite_embedded,limit=1] Embedded"
    rcon_command "data get entity @e[type=swordthrow:thrown_sword,tag=suite_embedded,limit=1] EmbeddedX"
    rcon_command "data get entity @e[type=swordthrow:thrown_sword,tag=suite_embedded,limit=1] EmbeddedY"
    rcon_command "data get entity @e[type=swordthrow:thrown_sword,tag=suite_embedded,limit=1] EmbeddedZ"
  } > "$destination"
}

prepare_persistence_fixtures() {
  rcon_command "scoreboard objectives add suite_qa dummy" >/dev/null || true
  rcon_command "forceload add 0 0" >/dev/null
  rcon_command "setblock ${WORKBENCH_POS} seamlessdeconstructor:reverse_deconstructor" >/dev/null
  # A full-durability iron pickaxe has deterministic, non-empty salvage at zero configured loss,
  # so six incompatible full output stacks always force a pending operation instead of allowing
  # a fractional recipe roll to produce no output.
  rcon_command "item replace block ${WORKBENCH_POS} container.0 with minecraft:iron_pickaxe 1" >/dev/null
  for slot in 2 3 4 5 6 7; do
    rcon_command "item replace block ${WORKBENCH_POS} container.${slot} with minecraft:cobblestone 64" >/dev/null
  done

  local pending_ready=false
  for _ in $(seq 1 80); do
    rcon_command "scoreboard players set pending suite_qa 0" >/dev/null
    rcon_command "execute if data block ${WORKBENCH_POS} PendingOperation run scoreboard players set pending suite_qa 1" >/dev/null
    local response
    response="$(rcon_command "scoreboard players get pending suite_qa")"
    if [[ "$response" =~ has[[:space:]]+1[[:space:]] ]]; then
      pending_ready=true
      break
    fi
    sleep 1
  done
  if [[ "$pending_ready" != true ]]; then
    echo "Workbench did not persist a blocked PendingOperation within 80 seconds" >&2
    return 1
  fi

  # A component-bearing, already-embedded projectile exercises entity registry and exact stack
  # persistence without relying on timing-sensitive collision geometry in a CI server.
  rcon_command \
    'execute unless entity @e[type=swordthrow:thrown_sword,tag=suite_embedded,limit=1] run summon swordthrow:thrown_sword 8 200 0 {Tags:["suite_embedded"],Item:{id:"minecraft:iron_sword",count:1,components:{"minecraft:custom_data":{suite_marker:"embedded-26.2"},"minecraft:damage":7}},ThrownStackCount:3,HitBlock:1b,Embedded:1b,EmbeddedRoll:11.25f,EmbeddedYaw:90.0f,EmbeddedPitch:0.0f,EmbeddedX:8.0d,EmbeddedY:200.0d,EmbeddedZ:0.0d,NoGravity:1b}' \
    >/dev/null

  assert_rcon_condition \
    sword \
    'if entity @e[type=swordthrow:thrown_sword,tag=suite_embedded,limit=1,nbt={Embedded:1b,ThrownStackCount:3,Item:{id:"minecraft:iron_sword",count:1,components:{"minecraft:custom_data":{suite_marker:"embedded-26.2"},"minecraft:damage":7}}}]' \
    "component-bearing embedded Sword projectile was not created"

  # This exact legacy payload contains only the historical eight-slot inventory and
  # Progress/MaxProgress fields.  Loading it through `data merge block` invokes the same block
  # entity deserializer used for a copied historical chunk, before the current serializer adds
  # its backward-compatible fields.
  rcon_command "tick freeze" >/dev/null
  rcon_command "forceload add 512 0" >/dev/null
  rcon_command "setblock ${HISTORICAL_WORKBENCH_POS} seamlessdeconstructor:reverse_deconstructor" >/dev/null
  rcon_command "data merge block ${HISTORICAL_WORKBENCH_POS} ${historical_workbench_snbt}" >/dev/null
  assert_rcon_condition \
    history \
    "if data block ${HISTORICAL_WORKBENCH_POS} ${historical_workbench_snbt}" \
    "historical eight-slot Workbench payload did not load"

  capture_persistent_state "$run_dir/qa-logs/persistent-state-before-reload.txt"
  # Keep the historical chunk out of both the forced and spawn-loaded sets at the next boot so
  # the current Workbench never ticks before the reload assertion freezes the server.
  rcon_command "forceload remove 512 0" >/dev/null
  rcon_command "save-all flush" >/dev/null
}

verify_reloaded_persistence() {
  rcon_command "scoreboard objectives add suite_qa dummy" >/dev/null || true
  rcon_command "tick freeze" >/dev/null
  rcon_command "forceload add 512 0" >/dev/null
  assert_rcon_condition \
    pending \
    "if data block ${WORKBENCH_POS} PendingOperation" \
    "Workbench PendingOperation disappeared across reload"
  assert_rcon_condition \
    history \
    "if data block ${HISTORICAL_WORKBENCH_POS} ${historical_workbench_snbt}" \
    "historical Workbench inventory/progress changed across reload"
  assert_rcon_condition \
    sword \
    'if entity @e[type=swordthrow:thrown_sword,tag=suite_embedded,limit=1,nbt={Embedded:1b,ThrownStackCount:3,Item:{id:"minecraft:iron_sword",count:1,components:{"minecraft:custom_data":{suite_marker:"embedded-26.2"},"minecraft:damage":7}}}]' \
    "embedded Sword projectile or its exact stack components changed across reload"

  capture_persistent_state "$run_dir/qa-logs/persistent-state-after-reload.txt"
  if ! cmp --silent \
      "$run_dir/qa-logs/persistent-state-before-reload.txt" \
      "$run_dir/qa-logs/persistent-state-after-reload.txt"; then
    echo "Packaged persistent state changed across the stop/reload boundary" >&2
    diff -u \
      "$run_dir/qa-logs/persistent-state-before-reload.txt" \
      "$run_dir/qa-logs/persistent-state-after-reload.txt" >&2 || true
    return 1
  fi
  rcon_command "save-all flush" >/dev/null
}

download() {
  local url="$1"
  local destination="$2"
  curl --fail --location --retry 4 --retry-all-errors --silent --show-error \
    "$url" --output "$destination"
}

declare -a launch_command
active_server_pid=""

cleanup_active_server() {
  if [[ -n "$active_server_pid" ]] && kill -0 "$active_server_pid" 2>/dev/null; then
    kill "$active_server_pid" 2>/dev/null || true
    wait "$active_server_pid" 2>/dev/null || true
  fi
  active_server_pid=""
}

trap cleanup_active_server EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

case "$loader" in
  fabric)
    download \
      "https://maven.fabricmc.net/net/fabricmc/fabric-installer/${FABRIC_INSTALLER_VERSION}/fabric-installer-${FABRIC_INSTALLER_VERSION}.jar" \
      "$run_dir/fabric-installer.jar"
    (
      cd "$run_dir"
      java -jar fabric-installer.jar server \
        -mcversion "$MINECRAFT_VERSION" \
        -loader "$FABRIC_LOADER_VERSION" \
        -downloadMinecraft
    )
    download \
      "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FABRIC_API_VERSION}/fabric-api-${FABRIC_API_VERSION}.jar" \
      "$run_dir/mods/fabric-api-${FABRIC_API_VERSION}.jar"
    launch_command=(java -Xms512M -Xmx2G -jar fabric-server-launch.jar nogui)
    ;;
  forge)
    download \
      "https://maven.minecraftforge.net/net/minecraftforge/forge/${FORGE_VERSION}/forge-${FORGE_VERSION}-installer.jar" \
      "$run_dir/forge-installer.jar"
    (
      cd "$run_dir"
      java -jar forge-installer.jar --installServer
    )
    launch_command=(
      java -Xms512M -Xmx2G
      @"libraries/net/minecraftforge/forge/${FORGE_VERSION}/unix_args.txt"
      nogui
    )
    ;;
  neoforge)
    download \
      "https://maven.neoforged.net/releases/net/neoforged/neoforge/${NEOFORGE_VERSION}/neoforge-${NEOFORGE_VERSION}-installer.jar" \
      "$run_dir/neoforge-installer.jar"
    (
      cd "$run_dir"
      java -jar neoforge-installer.jar --installServer
    )
    neoforge_args="libraries/net/neoforged/neoforge/${NEOFORGE_VERSION}/unix_args.txt"
    case "$(uname -s)" in
      MINGW*|MSYS*|CYGWIN*)
        # Git Bash still launches the Windows JVM, whose classpath separator is
        # ';'. The installer's unix argument file uses ':' and cannot boot here.
        neoforge_args="libraries/net/neoforged/neoforge/${NEOFORGE_VERSION}/win_args.txt"
        ;;
    esac
    launch_command=(
      java -Xms512M -Xmx2G
      @"${neoforge_args}"
      nogui
    )
    ;;
esac

printf 'eula=true\n' > "$run_dir/eula.txt"
printf '%s\n' \
  'allow-flight=true' \
  'enable-command-block=true' \
  'enable-rcon=true' \
  'level-name=suite-world' \
  'max-tick-time=120000' \
  'online-mode=false' \
  "rcon.password=${RCON_PASSWORD}" \
  "rcon.port=${RCON_PORT}" \
  'server-ip=127.0.0.1' \
  'server-port=0' \
  'spawn-protection=0' \
  > "$run_dir/server.properties"

run_once() {
  local run_number="$1"
  local assertion_callback="$2"
  local console_log="$run_dir/qa-logs/run-${run_number}-console.log"
  (
    cd "$run_dir"
    "${launch_command[@]}"
  ) < /dev/null > "$console_log" 2>&1 &
  local server_pid=$!
  active_server_pid="$server_pid"

  local ready=false
  for _ in $(seq 1 240); do
    if grep -Eq 'Done \([0-9.]+s\)!' "$console_log"; then
      ready=true
      break
    fi
    if ! kill -0 "$server_pid" 2>/dev/null; then
      break
    fi
    sleep 1
  done

  if [[ "$ready" != true ]]; then
    echo "${loader} packaged server run ${run_number} did not become ready" >&2
    tail -n 200 "$console_log" >&2 || true
    kill "$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
    active_server_pid=""
    return 1
  fi

  "$assertion_callback"
  verify_workbench_config_migration

  # A lost response to `stop` still means the server may already be shutting down, so issue it
  # only once and let the bounded process-exit check below decide whether it succeeded.
  if ! PACKAGED_SUITE_RCON_ATTEMPTS=1 rcon_command "stop" \
      > "$run_dir/qa-logs/run-${run_number}-stop-response.txt"; then
    echo "${loader} packaged server run ${run_number} lost the stop response; waiting for process exit" >&2
  fi
  local stopped=false
  for _ in $(seq 1 60); do
    if ! kill -0 "$server_pid" 2>/dev/null; then
      stopped=true
      break
    fi
    sleep 1
  done
  if [[ "$stopped" != true ]]; then
    echo "${loader} packaged server run ${run_number} ignored the stop command" >&2
    kill "$server_pid" 2>/dev/null || true
    wait "$server_pid" 2>/dev/null || true
    active_server_pid=""
    return 1
  fi
  local exit_code=0
  wait "$server_pid" || exit_code=$?
  active_server_pid=""
  if [[ "$exit_code" -ne 0 ]]; then
    echo "${loader} packaged server run ${run_number} exited with ${exit_code}" >&2
    tail -n 200 "$console_log" >&2 || true
    return "$exit_code"
  fi
  if grep -Eqi 'Exception in server tick loop|Failed to start the minecraft server|ModLoadingException|Encountered an unexpected exception|A fatal error has been detected' "$console_log"; then
    echo "${loader} packaged server run ${run_number} logged a fatal startup/runtime failure" >&2
    tail -n 200 "$console_log" >&2 || true
    return 1
  fi
}

run_once 1 prepare_persistence_fixtures
test -f "$run_dir/suite-world/level.dat"
run_once 2 verify_reloaded_persistence
test -f "$run_dir/suite-world/level.dat"

printf '%s packaged suite preserved exact Sword/Workbench state and migrated the historical Workbench fixture.\n' "$loader"

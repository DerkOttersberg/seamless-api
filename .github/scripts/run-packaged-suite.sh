#!/usr/bin/env bash
set -Eeuo pipefail

readonly MINECRAFT_VERSION="26.2"
readonly FABRIC_LOADER_VERSION="0.19.3"
readonly FABRIC_INSTALLER_VERSION="1.1.2"
readonly FABRIC_API_VERSION="0.159.0+26.2"
readonly FORGE_VERSION="26.2-65.1.3"
readonly NEOFORGE_VERSION="26.2.0.75"

loader="${1:?loader argument is required}"
jars_dir="${2:?runtime-jar directory argument is required}"
run_dir="${3:?run directory argument is required}"

case "$loader" in
  fabric|forge|neoforge) ;;
  *)
    echo "Unsupported loader: $loader" >&2
    exit 2
    ;;
esac

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

mkdir -p "$run_dir/mods" "$run_dir/qa-logs"
mapfile -t runtime_jars < <(find "$jars_dir" -maxdepth 1 -type f -name "*-${loader}.jar" ! -name "*-sources.jar" | sort)
if [[ "${#runtime_jars[@]}" -ne 5 ]]; then
  printf 'Expected exactly five %s runtime jars, found %s:\n' "$loader" "${#runtime_jars[@]}" >&2
  printf '  %s\n' "${runtime_jars[@]:-<none>}" >&2
  exit 1
fi
cp "${runtime_jars[@]}" "$run_dir/mods/"

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
  'rcon.password=release-hardening-local-only' \
  'rcon.port=25575' \
  'server-ip=127.0.0.1' \
  'server-port=0' \
  'spawn-protection=0' \
  > "$run_dir/server.properties"

run_once() {
  local run_number="$1"
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

  local python_command=python3
  if ! command -v "$python_command" >/dev/null 2>&1; then
    python_command=python
  fi
  "$python_command" "$(dirname "$0")/rcon-command.py" \
    127.0.0.1 25575 release-hardening-local-only "$run_number" stop
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

run_once 1
test -f "$run_dir/suite-world/level.dat"
run_once 2
test -f "$run_dir/suite-world/level.dat"

printf '%s packaged suite started cleanly and reloaded the same world with five runtime jars.\n' "$loader"

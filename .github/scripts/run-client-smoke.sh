#!/usr/bin/env bash
set -Eeuo pipefail

readonly MINECRAFT_VERSION="26.2"
readonly FABRIC_API_VERSION="0.159.0+26.2"
readonly JEI_VERSION="30.29.0.199"
readonly CLIENT_TIMEOUT_SECONDS="${CLIENT_SMOKE_TIMEOUT_SECONDS:-150}"

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(realpath "$script_dir/../..")"
client_project="$repo_root/.github/client-smoke"

loader="${1:?loader argument is required}"
jars_dir="${2:?runtime-jar directory argument is required}"
world_dir="${3:?persisted-world directory argument is required}"
run_dir="${4:?client run directory argument is required}"
viewer="${5:-none}"

case "$loader" in
  fabric|forge|neoforge) ;;
  *)
    echo "Unsupported client-smoke loader: $loader" >&2
    exit 2
    ;;
esac
case "$viewer" in
  none) ;;
  jei)
    if [[ "$loader" == "forge" ]]; then
      echo "JEI ${MINECRAFT_VERSION} has no Forge artifact" >&2
      exit 2
    fi
    ;;
  *)
    echo "Unsupported recipe viewer: $viewer" >&2
    exit 2
    ;;
esac
if [[ ! "$CLIENT_TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] || ((CLIENT_TIMEOUT_SECONDS < 30 || CLIENT_TIMEOUT_SECONDS > 600)); then
  echo "Invalid client-smoke timeout: $CLIENT_TIMEOUT_SECONDS" >&2
  exit 2
fi

jars_dir="$(realpath "$jars_dir")"
world_dir="$(realpath "$world_dir")"
if [[ ! -f "$world_dir/level.dat" ]]; then
  echo "Persisted client-smoke world is missing level.dat: $world_dir" >&2
  exit 2
fi
if [[ -e "$run_dir" ]]; then
  if [[ ! -d "$run_dir" ]] || [[ -n "$(find "$run_dir" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
    echo "Client-smoke run directory must be an empty directory: $run_dir" >&2
    exit 2
  fi
else
  mkdir -p "$run_dir"
fi
run_dir="$(realpath "$run_dir")"

mkdir -p "$run_dir/mods" "$run_dir/saves" "$run_dir/qa-logs"
mapfile -t runtime_jars < <(find "$jars_dir" -maxdepth 1 -type f -name "*-${loader}.jar" ! -name "*-sources.jar" | sort)
if [[ "${#runtime_jars[@]}" -ne 5 ]]; then
  printf 'Expected exactly five %s runtime jars, found %s:\n' "$loader" "${#runtime_jars[@]}" >&2
  printf '  %s\n' "${runtime_jars[@]:-<none>}" >&2
  exit 1
fi
cp "${runtime_jars[@]}" "$run_dir/mods/"
cp -a "$world_dir" "$run_dir/saves/suite-world"
# A pristine client otherwise opens the mandatory accessibility onboarding screen before
# Quick Play can consume --quickPlaySingleplayer. Keep this fixture intentionally minimal so
# Minecraft supplies platform-specific defaults while the smoke remains deterministic.
cp "$client_project/options.txt" "$run_dir/options.txt"

download() {
  local url="$1"
  local destination="$2"
  curl --fail --location --retry 4 --retry-all-errors --silent --show-error \
    "$url" --output "$destination"
}

if [[ "$loader" == "fabric" ]]; then
  download \
    "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FABRIC_API_VERSION}/fabric-api-${FABRIC_API_VERSION}.jar" \
    "$run_dir/mods/fabric-api-${FABRIC_API_VERSION}.jar"
fi
if [[ "$viewer" == "jei" ]]; then
  download \
    "https://maven.blamejared.com/mezz/jei/jei-${MINECRAFT_VERSION}-${loader}/${JEI_VERSION}/jei-${MINECRAFT_VERSION}-${loader}-${JEI_VERSION}.jar" \
    "$run_dir/mods/jei-${MINECRAFT_VERSION}-${loader}-${JEI_VERSION}.jar"
fi

gradle_command=(
  "$repo_root/gradlew"
  --no-daemon
  -p "$client_project"
  runClient
  "-PclientLoader=${loader}"
  "-PclientRunDir=${run_dir}"
)
display_command=()
if [[ "$(uname -s)" == Linux* ]]; then
  if ! command -v xvfb-run >/dev/null 2>&1; then
    echo "xvfb-run is required for the Linux packaged-client smoke" >&2
    exit 2
  fi
  display_command=(xvfb-run -a)
fi

set +e
timeout --signal=INT --kill-after=20s "${CLIENT_TIMEOUT_SECONDS}s" \
  "${display_command[@]}" "${gradle_command[@]}" \
  > "$run_dir/qa-logs/gradle-client-console.log" 2>&1
client_exit=$?
set -e

latest_log="$run_dir/logs/latest.log"
if [[ ! -f "$latest_log" ]]; then
  echo "${loader}/${viewer} client did not create logs/latest.log (exit ${client_exit})" >&2
  tail -n 200 "$run_dir/qa-logs/gradle-client-console.log" >&2 || true
  exit 1
fi

require_log_pattern() {
  local pattern="$1"
  local description="$2"
  if ! grep -Eq "$pattern" "$latest_log"; then
    echo "${loader}/${viewer} client log is missing ${description}" >&2
    tail -n 250 "$latest_log" >&2 || true
    exit 1
  fi
}

if grep -Eqi \
    'Mixin apply .* failed|MixinApplyError|InvalidMixinException|MixinTransformerError|Exception in server tick loop|Failed to start the minecraft server|ModLoadingException|Encountered an unexpected exception|A fatal error has been detected|The game crashed|Minecraft has crashed' \
    "$latest_log"; then
  echo "${loader}/${viewer} client logged a fatal, Mixin, or integrated-world failure" >&2
  tail -n 250 "$latest_log" >&2 || true
  exit 1
fi

require_log_pattern 'Backend library: LWJGL version' 'the rendered-client LWJGL startup marker'
require_log_pattern 'Starting integrated minecraft server version 26\.2' 'the quick-play integrated-world marker'
require_log_pattern 'joined the game' 'the client joining the persisted world'
require_log_pattern 'prettymeteors|pretty-meteors-with-trails' 'Pretty Meteors discovery'
require_log_pattern 'seamlessapi|seamless-api' 'Seamless API discovery'
require_log_pattern 'seamless_crafting|derk_easy_inventory_crafter|seamless-crafting' 'Seamless Crafting discovery'
require_log_pattern 'seamlessdeconstructor|seamless-deconstructing-workbench' 'Workbench discovery'
require_log_pattern 'swordthrow|sword-throw' 'Sword Throw discovery'
if [[ "$loader" == "neoforge" ]]; then
  require_log_pattern 'suiteclientsmoke|Seamless Suite Client Smoke Harness' \
    'the metadata-only NeoForge test harness'
fi
if [[ "$viewer" == "jei" ]]; then
  require_log_pattern 'jei-[^ ]*30\.29\.0\.199|jei[[:space:]]+30\.29\.0\.199|Just Enough Items' \
    'JEI 30.29.0.199 discovery'
  require_log_pattern 'Starting JEI (GUI|took)' 'JEI runtime initialization'
fi

# GNU timeout returns 124 when it ended a healthy interactive client after evidence was recorded.
# A Gradle-launched client may not finish its integrated-server shutdown within --kill-after and
# then timeout returns 137. On Windows, Gradle handles timeout's INT and reports its own expected
# "daemon ... stop command" exit 1. These are accepted only after every marker and fatal-log guard
# above. A clean probe exit is also accepted; other early process failures remain failures.
expected_gradle_stop=false
if [[ "$client_exit" -eq 1 ]] && grep -Fq \
    'Gradle build daemon has been stopped: stop command received' \
    "$run_dir/qa-logs/gradle-client-console.log"; then
  expected_gradle_stop=true
fi
if [[ "$client_exit" -ne 0 && "$client_exit" -ne 124 && "$client_exit" -ne 137 && "$expected_gradle_stop" != true ]]; then
  echo "${loader}/${viewer} client exited unexpectedly with ${client_exit}" >&2
  tail -n 250 "$run_dir/qa-logs/gradle-client-console.log" >&2 || true
  exit "$client_exit"
fi

printf '%s/%s client loaded all five packaged jars and quick-played the persisted world.\n' "$loader" "$viewer"

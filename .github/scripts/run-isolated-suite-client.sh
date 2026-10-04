#!/usr/bin/env bash
# Reproducible legacy real-client test: private Xvfb, never desktop input.
set -Eeuo pipefail
loader="${1:?fabric or forge}"
jars_dir="$(realpath "${2:?canonical runtime jars}")"
world_dir="$(realpath "${3:?generated QA world copy}")"
profile="${4:?fresh disposable profile directory}"
case "$loader" in fabric|forge) ;; *) exit 2 ;; esac
test -f "$world_dir/level.dat"
if [[ -e "$profile" ]] && [[ -n "$(find "$profile" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
  echo "Refusing to overwrite a non-empty QA profile" >&2; exit 2
fi
mkdir -p "$profile/saves"
profile="$(realpath "$profile")"
[[ "$profile" != / && "$profile" != "$world_dir" && "$profile" != "$jars_dir" ]] || exit 2
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
api_root="$(realpath "$script_dir/../..")"
helper="$api_root/.github/legacy-client-qa"
cp -r "$world_dir" "$profile/saves/qa-world"
cp "$helper/options-template.txt" "$profile/options.txt"
input_dir="$profile/mods"
[[ "$loader" != forge ]] || input_dir="$profile/artifact-inputs"
mkdir -p "$input_dir"
mapfile -t jars < <(find "$jars_dir" -maxdepth 1 -type f -name "*-$loader.jar" ! -name '*-sources.jar' | sort)
[[ "${#jars[@]}" -eq 5 ]] || { echo "Expected five matching runtime jars" >&2; exit 1; }
cp "${jars[@]}" "$input_dir/"
download() { curl -fL --retry 3 --silent --show-error "$1" -o "$2"; }
if [[ "$loader" == fabric ]]; then
  download 'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.92.12+1.20.1/fabric-api-0.92.12+1.20.1.jar' "$input_dir/fabric-api.jar"
  download 'https://maven.terraformersmc.com/releases/com/terraformersmc/modmenu/7.2.2/modmenu-7.2.2.jar' "$input_dir/modmenu.jar"
fi
download "https://maven.blamejared.com/mezz/jei/jei-1.20.1-$loader/15.62.0.219/jei-1.20.1-$loader-15.62.0.219.jar" "$input_dir/jei.jar"
download "https://maven.blamejared.com/net/mezzdev/config/mezz_config-1.20.1-$loader/0.6.8/mezz_config-1.20.1-$loader-0.6.8.jar" "$input_dir/mezz-config.jar"
bash "$script_dir/run-isolated-client.sh" bash "$api_root/gradlew" -p "$helper" --no-daemon \
  "-PclientLoader=$loader" "-PclientRunDir=$profile" \
  -Pqa.expectweapons=true -Pqa.expectworkbench=true -Pqa.expectcrafting=true -Pqa.expectmeteors=true runClient

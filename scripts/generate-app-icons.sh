#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /path/to/ratogramx-icons" >&2
  exit 2
fi

source_dir=$1
project_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
main_res="$project_dir/app/src/main/res"
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT

declare -A source_names=(
  [original]="original.svg"
  [amoled]="amoled.svg"
  [lab]="lab.svg"
  [malware]="malware.svg"
  [not_you]="not you.svg"
  [notele]="notele.svg"
  [steam]="steam.svg"
  [twins]="twins.svg"
  [xue]="xue.svg"
)

densities=(mdpi hdpi xhdpi xxhdpi xxxhdpi)
sizes=(48 72 96 144 192)

mkdir -p "$main_res/drawable-nodpi"
for density in "${densities[@]}"; do
  mkdir -p "$main_res/mipmap-$density"
done

for icon_id in original amoled lab malware not_you notele steam twins xue; do
  source_svg="$source_dir/${source_names[$icon_id]}"
  round_svg="$source_dir/rounded/${source_names[$icon_id]}"
  if [[ ! -f "$source_svg" || ! -f "$round_svg" ]]; then
    echo "Missing icon pair for $icon_id" >&2
    exit 1
  fi

  full_output="$main_res/drawable-nodpi/app_icon_${icon_id}_full.png"
  adaptive_output="$main_res/drawable-nodpi/app_icon_${icon_id}_adaptive.png"
  rsvg-convert -w 512 -h 512 "$source_svg" -o "$full_output"
  # AdaptiveIconDrawable expands each layer by 25% on every side (1.5x total).
  # 365px compensates that zoom while keeping the artwork about 7% larger than
  # the source composition, matching the in-app preview.
  magick "$full_output" -resize 365x365 -gravity center -background none -extent 512x512 "$adaptive_output"

  for index in "${!densities[@]}"; do
    density=${densities[$index]}
    size=${sizes[$index]}
    square_output="$main_res/mipmap-$density/app_icon_${icon_id}.png"
    round_output="$main_res/mipmap-$density/app_icon_${icon_id}_round.png"
    raw_round="$work_dir/${icon_id}_${density}_round.png"
    mask="$work_dir/${icon_id}_${density}_mask.png"

    rsvg-convert -w "$size" -h "$size" "$source_svg" -o "$square_output"
    rsvg-convert -w "$size" -h "$size" "$round_svg" -o "$raw_round"
    magick -size "${size}x${size}" xc:none -fill white -draw "circle $((size / 2)),$((size / 2)) $((size / 2)),0" "$mask"
    magick "$raw_round" "$mask" -alpha off -compose CopyOpacity -composite "$round_output"
  done
done

echo "Generated Ratogram X launcher resources from $source_dir"

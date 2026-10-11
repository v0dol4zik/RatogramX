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
  [original]="original"
  [amoled]="amoled"
  [lab]="lab"
  [malware]="malware"
  [not_you]="not you"
  [notele]="notele"
  [steam]="steam"
  [twins]="twins"
  [xue]="xue"
)

densities=(mdpi hdpi xhdpi xxhdpi xxxhdpi)
sizes=(48 72 96 144 192)

# Square sources are full-bleed; rounded sources are the same artwork clipped
# to a circle. Design exports may carry a " 1" suffix.
find_source () {
  local dir=$1 name=$2
  for candidate in "$dir/$name.svg" "$dir/$name 1.svg"; do
    if [[ -f "$candidate" ]]; then
      echo "$candidate"
      return
    fi
  done
  return 0
}

mkdir -p "$main_res/drawable-nodpi" "$main_res/mipmap-anydpi-v26"
for density in "${densities[@]}"; do
  mkdir -p "$main_res/mipmap-$density"
done

for icon_id in original amoled lab malware not_you notele steam twins xue; do
  source_svg=$(find_source "$source_dir" "${source_names[$icon_id]}")
  round_svg=$(find_source "$source_dir/rounded" "${source_names[$icon_id]}")
  if [[ -z "$source_svg" || -z "$round_svg" ]]; then
    echo "Missing icon pair for $icon_id" >&2
    exit 1
  fi

  background_svg="$work_dir/${icon_id}_background.svg"
  foreground_svg="$work_dir/${icon_id}_foreground.svg"
  python3 "$project_dir/scripts/split-icon-layers.py" "$source_svg" "$background_svg" "$foreground_svg"

  # In-app selector preview, clipped to a circle by AppIconSelectorAdapter.
  rsvg-convert -w 512 -h 512 "$round_svg" -o "$main_res/drawable-nodpi/app_icon_${icon_id}_full.png"
  # The background must cover the whole 108dp canvas, so any launcher mask
  # shape is filled up to its corners.
  rsvg-convert -w 512 -h 512 "$background_svg" -o "$main_res/drawable-nodpi/app_icon_${icon_id}_background.png"
  # AdaptiveIconDrawable expands each layer by 25% on every side (1.5x total).
  # 365px compensates that zoom while keeping the glyph about 7% larger than
  # the source composition, matching the in-app preview.
  rsvg-convert -w 365 -h 365 "$foreground_svg" -o "$work_dir/${icon_id}_foreground.png"
  magick "$work_dir/${icon_id}_foreground.png" -gravity center -background none -extent 512x512 \
    "$main_res/drawable-nodpi/app_icon_${icon_id}_foreground.png"

  for suffix in "" "_round"; do
    cat > "$main_res/mipmap-anydpi-v26/app_icon_${icon_id}${suffix}.xml" <<EOF
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
  <background android:drawable="@drawable/app_icon_${icon_id}_background" />
  <foreground android:drawable="@drawable/app_icon_${icon_id}_foreground" />
  <!--suppress AndroidElementNotAllowed --> <monochrome android:drawable="@drawable/app_icon_${icon_id}_foreground" />
</adaptive-icon>
EOF
  done

  for index in "${!densities[@]}"; do
    density=${densities[$index]}
    size=${sizes[$index]}
    radius=$((size * 11 / 48))
    square_output="$main_res/mipmap-$density/app_icon_${icon_id}.png"
    round_output="$main_res/mipmap-$density/app_icon_${icon_id}_round.png"
    raw_square="$work_dir/${icon_id}_${density}_square.png"
    raw_round="$work_dir/${icon_id}_${density}_round.png"
    square_mask="$work_dir/${icon_id}_${density}_square_mask.png"
    round_mask="$work_dir/${icon_id}_${density}_round_mask.png"

    # Pre-Oreo launchers draw legacy icons as is: rounded square and circle.
    rsvg-convert -w "$size" -h "$size" "$source_svg" -o "$raw_square"
    rsvg-convert -w "$size" -h "$size" "$round_svg" -o "$raw_round"
    magick -size "${size}x${size}" xc:none -fill white -draw "roundrectangle 0,0 $((size - 1)),$((size - 1)) $radius,$radius" "$square_mask"
    magick -size "${size}x${size}" xc:none -fill white -draw "circle $((size / 2)),$((size / 2)) $((size / 2)),0" "$round_mask"
    magick "$raw_square" -alpha set "$square_mask" -compose DstIn -composite "$square_output"
    magick "$raw_round" -alpha set "$round_mask" -compose DstIn -composite "$round_output"
  done
done

echo "Generated Ratogram X launcher resources from $source_dir"

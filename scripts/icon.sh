#!/usr/bin/env bash
# docs/icon.png, the launcher icon as a PNG for the README: Trackhop's icon (the beam, the glass
# tile, the mark; scripts/store_graphics.sh in Trackhop) with the Bridge's plug badge on the tile.
#
#   scripts/icon.sh              # writes docs/icon.png (512 px)
#   scripts/icon.sh preview DIR  # also 48/108 px previews and the themed (monochrome) layer
#
# Needs ImageMagick 6 (`convert`). Drawn from the same geometry as the vector drawables
# (android/app/src/main/res/drawable/ic_launcher_*.xml): keep the two in step by hand.
set -euo pipefail
root=$(cd "$(dirname "$0")/.." && pwd)
command -v convert >/dev/null || { echo "ImageMagick (convert) is required" >&2; exit 1; }
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT

BEAM_ANGLE=12
BEAM_STOPS="-0.45 #000000 -0.008 #400000 0.008 #421AFF 0.08 #59FFFF 0.61 #FFFFFF"
TILE_FILL="rgba(255,255,255,0.18)"
TILE_LINE="rgba(255,255,255,0.35)"
TILE_RADIUS=0.24

# beam OUT W H AX AY: the background, the band's edge through (AX,AY), no sweep (as the icon).
beam() {
  local out=$1 w=$2 h=$3 ax=$4 ay=$5 nx ny lut=() s1 c1 prev_s="" prev_c=""
  nx=$(awk "BEGIN{print sin($BEAM_ANGLE*atan2(0,-1)/180)}")
  ny=$(awk "BEGIN{print -cos($BEAM_ANGLE*atan2(0,-1)/180)}")
  set -- $BEAM_STOPS
  local first_s=$1 last_s
  while [ $# -ge 2 ]; do
    s1=$1; c1=$2; shift 2
    [ -n "$prev_s" ] && lut+=( -size 1x$(awk "BEGIN{printf \"%d\", ($s1-($prev_s))*1000}") "gradient:$prev_c-$c1" )
    prev_s=$s1; prev_c=$c1
  done
  last_s=$prev_s
  local a_x a_y b_x b_y
  a_x=$(awk "BEGIN{print $ax+$nx*$first_s*$h}"); a_y=$(awk "BEGIN{print $ay+$ny*$first_s*$h}")
  b_x=$(awk "BEGIN{print $ax+$nx*$last_s*$h}");  b_y=$(awk "BEGIN{print $ay+$ny*$last_s*$h}")
  convert "${lut[@]}" -append "$tmp/lut.png"
  convert -size "${w}x${h}" xc:black -sparse-color barycentric "$a_x,$a_y black $b_x,$b_y white" \
    "$tmp/lut.png" -clut "$out"
}

# The foreground's white parts on the 108-unit canvas at scale K: the mark (scaled 0.65 about the
# centre, as on the tile) and the badge, a disc with the plug cut out (even-odd).
MARK_AND_BADGE="
    push graphic-context
    translate 54,54 scale 0.65,0.65 translate -54,-54
    stroke white stroke-width 5.5 stroke-linecap round fill none
    path 'M30.6,40.5 A27,27 0 0 1 77.4,40.5'
    path 'M77.4,67.5 A27,27 0 0 1 30.6,67.5'
    stroke none fill white
    path 'M81.85,36.2 L71.45,42.2 L81.4,47.4 Z'
    path 'M26.15,71.8 L36.55,65.8 L26.6,60.6 Z'
    translate 2.1,1.5
    path 'M54.08,61.11 A7,5.6 -20 1 1 40.92,65.89 A7,5.6 -20 1 1 54.08,61.11 Z'
    stroke white stroke-width 3.6 stroke-linecap round fill none
    path 'M52.6,62.5 L52.6,37.5'
    stroke none fill white
    path 'M52.6,37.2 C58.5,38.6 63.4,42.4 63.2,49.6 C61.6,45.2 58,42.9 52.6,42.6 Z'
    pop graphic-context
    stroke none fill white fill-rule evenodd
    path '__BADGE__'"

# Must match the badge path in ic_launcher_foreground.xml and ic_launcher_monochrome.xml.
BADGE="M73,66.4 A6.6,6.6 0 1 1 73,79.6 A6.6,6.6 0 1 1 73,66.4 Z M71.1,68.8 h1.1 v2.6 h1.6 v-2.6 h1.1 v2.6 h1 v2.2 l-1.3,1.4 h-1 v2.4 h-1.2 v-2.4 h-1 l-1.3,-1.4 v-2.2 h1 Z"

# foreground OUT K TILE: the white parts (and the glass tile when TILE=1) on a transparent canvas.
foreground() {
  local out=$1 k=$2 with_tile=$3 size draw tile=""
  size=$((108*k))
  draw=${MARK_AND_BADGE/__BADGE__/$BADGE}
  if [ "$with_tile" = 1 ]; then
    tile="fill $TILE_FILL stroke $TILE_LINE stroke-width 0.4 roundrectangle 27.2,27.2 80.8,80.8 13,13"
  fi
  convert -size "${size}x${size}" xc:none -draw "scale $k,$k $tile $draw" "$out"
}

k=10
beam "$tmp/bg.png" $((108*k)) $((108*k)) $((72*k)) $((36*k))
foreground "$tmp/fg.png" $k 1
convert "$tmp/bg.png" "$tmp/fg.png" -composite "$tmp/canvas.png"
# The launcher's visible square: the central 72 of 108 units, opaque.
visible() { convert "$tmp/canvas.png" -gravity center -crop 720x720+0+0 +repage -resize "${2}x${2}" -depth 8 -strip "$1"; }
visible "$root/docs/icon.png" 512
echo "icon: docs/icon.png"

if [ "${1:-}" = preview ]; then
  dir=${2:?preview needs a directory}; mkdir -p "$dir"
  for s in 48 108 512; do visible "$dir/icon_$s.png" "$s"; done
  # A circular mask, as most launchers show it.
  convert "$dir/icon_108.png" \( -size 108x108 xc:black -fill white -draw "circle 54,54 54,0" \) -alpha off -compose copy_opacity -composite "$dir/icon_round_108.png"
  foreground "$tmp/mono.png" 2 0
  convert -size 216x216 xc:'#D8E2FF' \( "$tmp/mono.png" -fill '#2B4678' -colorize 100 \) -composite -gravity center -crop 144x144+0+0 +repage "$dir/icon_themed.png"
  echo "previews in $dir"
fi

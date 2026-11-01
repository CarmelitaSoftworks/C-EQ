#!/bin/sh
# Video de promoción: cuadros con PromoFramesTest y, con ffmpeg, un GIF del skin completo (docs/promo.gif)
# y un MP4 vertical de teléfono para redes. Uso: tools/promo.sh <carpeta de salida>
set -e
out=${1:?carpeta de salida}
frames="$out/frames"
rm -rf "$frames"
./gradlew -q testDebugUnitTest --tests 'cl.ecualizador.PromoFramesTest' -DpromoDir="$frames"
ffmpeg -loglevel error -y -framerate 20 -i "$frames/f%04d.png" \
  -vf "crop=1140:2012:60:416,scale=570:-1:flags=neighbor,fps=10,split[a][b];[a]palettegen=max_colors=128:stats_mode=full[p];[b][p]paletteuse=dither=none" \
  "$out/C-EQ-promo.gif"
ffmpeg -loglevel error -y -framerate 20 -i "$frames/f%04d.png" \
  -vf "scale=1080:2400:flags=lanczos,format=yuv420p" -c:v libx264 -crf 20 -preset slow -movflags +faststart \
  "$out/C-EQ-promo.mp4"
echo "Listo: $out/C-EQ-promo.gif y $out/C-EQ-promo.mp4"

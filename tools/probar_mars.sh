#!/usr/bin/env bash
# Corre en MARS real cada .asm que dejan los tests en target/asm y compara con su salida esperada.
#
# Uso, desde la raíz del repo y después de `mvn test`:
#   bash tools/probar_mars.sh                 # usa tools/Mars4_5.jar
#   bash tools/probar_mars.sh otro/Mars.jar
#
# Para casos nuevos: target/asm/<caso>[_ingenuo|_getreg].asm se compara con
# src/test/resources/mips/<caso>.salida. Los aleatorios traen su .salida al lado.
# MARS agrega una línea en blanco al final: se ignoran los espacios finales.
set -u
MARS="${1:-tools/Mars4_5.jar}"
[ -f "$MARS" ] || { echo "No existe $MARS" >&2; exit 2; }
ok=0; mal=0

comparar() { # asm esperado
  local obtenido
  if [ ! -f "$2" ]; then
    echo "SIN SALIDA ESPERADA $1"; return
  fi
  obtenido=$(java -Dfile.encoding=UTF-8 -jar "$MARS" nc sm 1000000 "$1" 2>&1)
  if [ "$(printf '%s' "$obtenido" | sed -e 's/[[:space:]]*$//')" = "$(sed -e 's/[[:space:]]*$//' "$2")" ]; then
    ok=$((ok + 1)); echo "OK    $1"
  else
    mal=$((mal + 1)); echo "FALLA $1"
    diff <(printf '%s\n' "$obtenido") "$2" | head -10
  fi
}

for asm in target/asm/*.asm; do
  [ -e "$asm" ] || { echo "No hay .asm en target/asm: corre mvn test primero" >&2; exit 2; }
  caso=$(basename "$asm" .asm); caso=${caso%_ingenuo}; caso=${caso%_getreg}
  comparar "$asm" "src/test/resources/mips/$caso.salida"
done
for asm in target/asm/aleatorios/*.asm; do
  [ -e "$asm" ] || continue
  comparar "$asm" "${asm%.asm}.salida"
done
echo "MARS: $ok bien, $mal mal"
[ "$mal" -eq 0 ]

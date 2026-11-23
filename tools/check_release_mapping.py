#!/usr/bin/env python3
"""Verifica el mapping.txt de R8 de la variante release.

Garantiza que el minificador NO renombró la CLASE del puente JNI: la biblioteca nativa enlaza por nombre
(Java_com_yeivikas_olyzecs_..._NativeEngineBindings_nativeXxx), así que renombrarla produce UnsatisfiedLinkError solo en release.

OJO con el formato: R8 NO lista en mapping.txt los miembros que conservan su nombre y no tienen cuerpo (un método nativo no tiene
líneas que mapear), así que su AUSENCIA aquí no significa que se eliminaran. Lo que este script comprueba de los métodos es que, si
aparecen, no estén renombrados. La prueba de que existen en el APK la da tools/check_release_dex.py (lee el DEX real). Además comprueba que la ofuscación SÍ está activa (que el release realmente se minimizó),
porque un mapping casi vacío indicaría que alguien volvió a poner `-keep` amplios.

Uso: python3 tools/check_release_mapping.py [ruta/mapping.txt]
"""
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parent.parent
default = root / "app/build/outputs/mapping/release/mapping.txt"
mapping = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else default

BINDINGS = "com.yeivikas.olyzecs.engine.audio.nativeengine.NativeEngineBindings"
EXPECTED_JNI = pathlib.Path(root / "app/src/main/java" / BINDINGS.replace(".", "/")).with_suffix(".kt")
MIN_RENAMED_RATIO = 0.30   # al menos el 30 % de las clases de la app debe estar ofuscado

if not mapping.is_file():
    print(f"ERROR no existe {mapping}: ¿se ejecutó assembleRelease/bundleRelease con minificación?")
    sys.exit(1)

# Clases: "original -> ofuscado:"   Miembros: "    [l1:l2:]tipo nombre(args) -> ofuscado"
class_re = re.compile(r"^(\S+) -> (\S+):$")
member_re = re.compile(r"^\s+(?:\d+:\d+:)?(?:[\w.$\[\]<>?]+)\s+(\w+)(?:\(.*?\))?(?::\d+:\d+)?\s+->\s+(\w+)$")

errors = []
classes = {}
current = None
for line in mapping.read_text(encoding="utf-8", errors="replace").splitlines():
    if line.startswith("#") or not line.strip():
        continue
    m = class_re.match(line)
    if m:
        current = m.group(1)
        classes[current] = (m.group(2), [])
        continue
    m = member_re.match(line)
    if m and current:
        classes[current][1].append((m.group(1), m.group(2)))

if BINDINGS not in classes:
    errors.append(f"{BINDINGS} no aparece en mapping.txt (R8 la eliminó: el motor nativo no se podría usar)")
else:
    obf, members = classes[BINDINGS]
    if obf != BINDINGS:
        errors.append(f"R8 renombró {BINDINGS} -> {obf}: los símbolos JNI ya no enlazan")
    declared = set(re.findall(r"external\s+fun\s+(\w+)", EXPECTED_JNI.read_text(encoding="utf-8")))
    for name, new in members:
        if name in declared and new != name:
            errors.append(f"R8 renombró el método nativo {name} -> {new}")

app_classes = [(o, n) for o, (n, _) in classes.items() if o.startswith("com.yeivikas.olyzecs.")]
if not app_classes:
    errors.append("mapping.txt no contiene clases de la app")
else:
    renamed = sum(1 for o, n in app_classes if o != n)
    ratio = renamed / len(app_classes)
    if ratio < MIN_RENAMED_RATIO:
        errors.append(f"solo el {ratio:.0%} de las clases de la app está ofuscado (mínimo {MIN_RENAMED_RATIO:.0%}): "
                      "¿volvieron los `-keep` amplios en proguard-rules.pro?")

if errors:
    print("\n".join("ERROR " + e for e in errors))
    sys.exit(1)
print(f"OK: clase del puente JNI intacta ({len(declared)} métodos nativos declarados, ninguno renombrado) y {renamed}/{len(app_classes)} clases de la app ofuscadas ({ratio:.0%})")

#!/usr/bin/env python3
"""Comprueba en el DEX REAL del APK release que el puente JNI sobrevivió a R8.

La biblioteca nativa enlaza por nombre (Java_com_yeivikas_olyzecs_..._NativeEngineBindings_nativeXxx). Si R8 renombrara o eliminara
la clase o un método nativo, el fallo sería UnsatisfiedLinkError en el teléfono y solo en release. mapping.txt no sirve para esto
(R8 omite los miembros sin renombrar), así que se lee el DEX del APK con `dexdump` (build-tools del SDK).

Si `dexdump` no está disponible o su salida no se puede interpretar, cae a una comprobación débil: buscar cada nombre exacto en la
tabla de cadenas del DEX (si R8 renombrara un método, la cadena original desaparecería). El resultado indica qué método se usó.

Uso: python3 tools/check_release_dex.py [--dexdump RUTA] [apk ...]   (por defecto app/build/outputs/apk/release/*.apk)
"""
import glob
import os
import pathlib
import re
import subprocess
import sys
import tempfile
import zipfile

root = pathlib.Path(__file__).resolve().parent.parent
DESC = "Lcom/yeivikas/olyzecs/engine/audio/nativeengine/NativeEngineBindings;"
BINDINGS_KT = root / "app/src/main/java/com/yeivikas/olyzecs/engine/audio/nativeengine/NativeEngineBindings.kt"


def declared_native_methods() -> set:
    return set(re.findall(r"external\s+fun\s+(\w+)", BINDINGS_KT.read_text(encoding="utf-8")))


def parse_dexdump(output: str):
    """Devuelve (clase_encontrada, {métodos nativos de NativeEngineBindings}) a partir de la salida de dexdump."""
    cur, pending, found, natives = None, None, False, set()
    for line in output.splitlines():
        m = re.search(r"Class descriptor\s*:\s*'([^']+)'", line)
        if m:
            cur, pending = m.group(1), None
            found = found or cur == DESC
            continue
        m = re.match(r"\s+name\s*:\s*'([^']*)'", line)
        if m:
            pending = m.group(1)
            continue
        m = re.match(r"\s+access\s*:\s*0x[0-9a-fA-F]+\s*\((.*)\)", line)
        if m and cur == DESC and pending and "NATIVE" in m.group(1).split():
            natives.add(pending)
    return found, natives


def strings_present(dex_bytes: bytes, names: set) -> set:
    """Nombres que figuran EXACTOS en la tabla de cadenas (prefijo de longitud ULEB de 1 byte + texto + NUL)."""
    return {n for n in names if bytes([len(n)]) + n.encode() + b"\x00" in dex_bytes}


def find_dexdump(explicit):
    if explicit:
        return explicit
    if os.environ.get("DEXDUMP"):
        return os.environ["DEXDUMP"]
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") or ""
    cands = sorted(glob.glob(os.path.join(sdk, "build-tools", "*", "dexdump")))
    return cands[-1] if cands else None


def check_apk(apk: str, dexdump, expected: set):
    errors, mode = [], None
    with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(apk) as z:
        dexes = [n for n in z.namelist() if re.fullmatch(r"classes\d*\.dex", n)]
        if not dexes:
            return [f"{apk}: sin classes.dex"], "n/a"
        class_found, natives, blobs = False, set(), []
        for name in dexes:
            path = z.extract(name, tmp)
            blobs.append(pathlib.Path(path).read_bytes())
            if dexdump:
                try:
                    out = subprocess.run([dexdump, path], capture_output=True, text=True, check=True).stdout
                except (OSError, subprocess.CalledProcessError) as e:
                    print(f"AVISO dexdump falló en {name}: {e}")
                    continue
                found, nat = parse_dexdump(out)
                class_found, natives = class_found or found, natives | nat
        if class_found:
            mode = "dexdump"
            for n in sorted(expected - natives):
                errors.append(f"{os.path.basename(apk)}: el método nativo {n} no está (o no es native) en {DESC}")
        else:
            mode = "cadenas del DEX (comprobación débil: dexdump no disponible o ilegible)"
            present = set().union(*(strings_present(b, expected) for b in blobs))
            for n in sorted(expected - present):
                errors.append(f"{os.path.basename(apk)}: la cadena '{n}' no está en el DEX (R8 la renombró o eliminó)")
            if not any(DESC[1:-1].encode() in b for b in blobs):
                errors.append(f"{os.path.basename(apk)}: la clase {DESC} no está en el DEX")
    return errors, mode


def main(argv):
    args, dexdump = list(argv), None
    if "--dexdump" in args:
        i = args.index("--dexdump")
        dexdump = args[i + 1]
        del args[i:i + 2]
    apks = args or sorted(glob.glob(str(root / "app/build/outputs/apk/release/*.apk")))
    if not apks:
        print("ERROR no hay APK de release que comprobar")
        return 1
    expected, dexdump, failed = declared_native_methods(), find_dexdump(dexdump), False
    for apk in apks:
        errs, mode = check_apk(apk, dexdump, expected)
        if errs:
            failed = True
            print("\n".join("ERROR " + e for e in errs))
        else:
            print(f"OK {os.path.basename(apk)}: {len(expected)} métodos nativos presentes y sin renombrar [{mode}]")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

#!/usr/bin/env python3
"""Verifica que las declaraciones `external fun` de Kotlin coincidan con las funciones JNI de C++.

Un desajuste de nombre o de parámetros NO falla al compilar: revienta en tiempo de ejecución (UnsatisfiedLinkError o
corrupción de pila). Este chequeo lo convierte en un error de CI. Uso: python3 tools/check_jni_signatures.py
"""
import re, sys, pathlib

root = pathlib.Path(__file__).resolve().parent.parent
kt = (root / "app/src/main/java/com/yeivikas/olyzecs/engine/audio/nativeengine/NativeEngineBindings.kt").read_text()
cpp = (root / "app/src/main/cpp/android/jni_bridge.cpp").read_text()

KT2JNI = {"Long": "jlong", "Int": "jint", "Boolean": "jboolean", "String": "jstring", "String?": "jstring",
          "LongArray": "jlongArray", "FloatArray": "jfloatArray", "Float": "jfloat"}
RET = {"Long": "jlong", "Int": "jint", "Boolean": "jboolean", "String?": "jstring", "String": "jstring", "": "void"}

kotlin = {}
for m in re.finditer(r"@JvmStatic\s+external\s+fun\s+(\w+)\s*\((.*?)\)\s*(?::\s*([\w?]+))?\s*(?=\n)", kt, re.S):
    name, params, ret = m.group(1), m.group(2), m.group(3) or ""
    types = [KT2JNI[p.split(":")[1].strip()] for p in re.split(r",", params) if p.strip()]
    kotlin[name] = (RET[ret], types)

native = {}
for m in re.finditer(r"JNIEXPORT\s+(\w+)\s+JNICALL\s+JNI_FN\((\w+)\)\s*\((.*?)\)\s*\{", cpp, re.S):
    ret, name, params = m.group(1), m.group(2), m.group(3)
    parts = [p.strip() for p in params.split(",") if p.strip()]
    # los dos primeros son (JNIEnv*, jclass) y pueden ir sin nombre
    types = [re.match(r"(\w+)", p).group(1) for p in parts[2:]]
    native[name] = (ret, types)

errors = []
for name in sorted(set(kotlin) | set(native)):
    if name not in native: errors.append(f"Kotlin declara {name} pero C++ no lo implementa")
    elif name not in kotlin: errors.append(f"C++ implementa {name} pero Kotlin no lo declara")
    elif kotlin[name] != native[name]: errors.append(f"{name}: Kotlin={kotlin[name]}  C++={native[name]}")

if errors:
    print("\n".join("ERROR " + e for e in errors)); sys.exit(1)
print(f"OK: {len(kotlin)} funciones JNI coinciden en nombre, parámetros y retorno")

#!/usr/bin/env python3
"""Verificación estática ligera del Kotlin del proyecto. NO sustituye a compilar: atrapa la clase de errores que más se cuelan al
editar sin compilador, y es lo bastante rápida para correr en CI antes de Gradle.

Comprueba, para cada .kt de app/src:
  1. Balance de (), [] y {} sobre los TOKENS reales (ignora cadenas, plantillas y comentarios, vía el lexer de Pygments) y
     que el lexer no marque errores léxicos.
  2. Que cada `import com.yeivikas.olyzecs.<paquete>.<Símbolo>` apunte a un símbolo declarado en ese paquete.
  3. Que cada `NativeEngineBindings.<función>` que se llama esté declarada (un nombre mal escrito en la frontera JNI compila
     distinto y revienta en ejecución).
  4. Que ningún nombre de función entre comillas invertidas (las pruebas ``fun `se comporta así`()``) contenga caracteres que la JVM
     prohíbe en un nombre de método (`. ; [ ] / < > : \\`). Kotlin lo rechaza al compilar las pruebas, que es tarde: llegó a romper el CI
     por un `:` en el título de una prueba.

Uso: python3 tools/check_kotlin_static.py        (requiere `pip install pygments`)
"""
import pathlib
import re
import sys

try:
    from pygments.lexers import KotlinLexer
    from pygments.token import Comment, Error, String
except ImportError:  # pragma: no cover
    print("Falta pygments: pip install pygments")
    sys.exit(2)

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "app" / "src"
BASE_PACKAGE = "com.yeivikas.olyzecs"

# Los genera Android Gradle Plugin al compilar: no existen como código fuente.
GENERATED_SYMBOLS = {"R", "BuildConfig"}

PAIRS = {")": "(", "]": "[", "}": "{"}
DECLARATION = re.compile(
    r"^\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:(?:public|internal|private|protected|abstract|open|sealed|data|enum|inline|value|annotation|"
    r"const|override|operator|infix|tailrec|suspend|external|lateinit|fun interface)\s+)*"
    r"(?:class|interface|object|typealias|fun|val|var)\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?(\w+)",
    re.M,
)


# Caracteres que la JVM no admite en el nombre de un método (Kotlin: "Name contains illegal characters").
ILLEGAL_JVM_NAME_CHARS = set(".;[]/<>:\\")
BACKTICK_FUN = re.compile(r"^\s*(?:override\s+|private\s+|internal\s+|suspend\s+)*fun\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?`([^`\n]+)`", re.M)


def package_of(text: str) -> str:
    m = re.search(r"^package\s+([\w.]+)", text, re.M)
    return m.group(1) if m else ""


def check_balance(path: pathlib.Path, text: str, errors: list) -> bool:
    """Devuelve `False` si el lexer no entiende el archivo (queda sin verificar en vez de dar un falso error)."""
    stack = []
    problems = []
    lexer_confused = False
    line = 1
    for token_type, value in KotlinLexer().get_tokens(text):
        # El lexer marca como error la llave de `companion object {`: es un delimitador normal y se cuenta como tal. Cualquier otro
        # error (p. ej. nombres de prueba con tildes entre comillas invertidas, cadenas crudas con barras) deja el archivo sin verificar.
        if token_type in Error and value not in "([{)]}":
            lexer_confused = True
        if token_type not in Comment and token_type not in String:
            for ch in value:
                if ch in "([{":
                    stack.append((ch, line))
                elif ch in ")]}":
                    if not stack or stack[-1][0] != PAIRS[ch]:
                        problems.append(f"{path.relative_to(ROOT)}:{line}: '{ch}' sin su apertura")
                        stack = []
                        break
                    stack.pop()
        line += value.count("\n")
    if stack:
        problems.append(f"{path.relative_to(ROOT)}:{stack[-1][1]}: '{stack[-1][0]}' sin cerrar")
    if lexer_confused:
        return False
    errors.extend(problems)
    return True


def main() -> int:
    files = sorted(SRC.rglob("*.kt"))
    texts = {f: f.read_text(encoding="utf-8") for f in files}

    declared = {}  # paquete -> {símbolos}
    for f, t in texts.items():
        pkg = package_of(t)
        declared.setdefault(pkg, set()).update(DECLARATION.findall(t))

    errors = []
    unverifiable = []
    for f, t in texts.items():
        if not check_balance(f, t, errors):
            unverifiable.append(f)
        for m in BACKTICK_FUN.finditer(t):
            bad = sorted(set(m.group(1)) & ILLEGAL_JVM_NAME_CHARS)
            if bad:
                line = t.count("\n", 0, m.start(1)) + 1
                errors.append(f"{f.relative_to(ROOT)}:{line}: el nombre `{m.group(1)}` contiene caracteres ilegales en la JVM: "
                              + " ".join(repr(c) for c in bad))
        for m in re.finditer(r"^import\s+(" + re.escape(BASE_PACKAGE) + r"[\w.]*)\s*$", t, re.M):
            fq = m.group(1)
            pkg, _, symbol = fq.rpartition(".")
            if symbol == "*" or symbol in GENERATED_SYMBOLS:
                continue
            # un símbolo anidado (Clase.Interna) se resuelve por su clase
            known = declared.get(pkg)
            if known is None:
                outer_pkg, _, outer = pkg.rpartition(".")
                known = declared.get(outer_pkg, set())
                if outer in known:
                    continue
            if symbol not in known:
                errors.append(f"{f.relative_to(ROOT)}: import sin símbolo declarado: {fq}")

    bindings = next((t for f, t in texts.items() if f.name == "NativeEngineBindings.kt"), "")
    available = set(re.findall(r"external fun (\w+)", bindings))
    for f, t in texts.items():
        for name in set(re.findall(r"NativeEngineBindings\.(native\w+)", t)):
            if name not in available:
                errors.append(f"{f.relative_to(ROOT)}: NativeEngineBindings.{name} no está declarada")

    if errors:
        print("\n".join("ERROR " + e for e in errors))
        return 1
    print(f"OK: {len(files) - len(unverifiable)} archivos Kotlin con llaves balanceadas verificadas, imports internos resueltos "
          f"({len(files)} archivos), JNI consistente, nombres de prueba válidos")
    if unverifiable:
        print(f"    (el lexer no entiende {len(unverifiable)} archivos, solo se les comprobaron imports/JNI: "
              + ", ".join(sorted(f.name for f in unverifiable)) + ")")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Mueve un bloque contiguo de declaraciones top-level de `ui/EditorScreen.kt` a un archivo nuevo de `ui/editor/` SIN cambiar su lógica.

Es la herramienta de la Fase 3 (ver docs/adr/ADR-022-particion-de-editorscreen.md). Hace tres cosas, en este orden:
  1. ANALIZA (siempre): límites del bloque, dependencias con visibilidad `private` en un sentido o en el otro, tipos privados que
     quedarían expuestos en una firma `internal`, y colisiones de nombres con el resto del paquete.
  2. APLICA (solo con --apply): escribe el archivo nuevo (mismo paquete, imports mínimos) y recorta EditorScreen.kt. La ÚNICA edición de
     código es `private` -> `internal` en lo estrictamente necesario; se poda de EditorScreen.kt solo el import que quedó huérfano.
  3. VERIFICA (tras --apply): mismo multiconjunto de declaraciones, bloque idéntico línea a línea salvo esas visibilidades, EditorScreen.kt
     = original - bloque - imports huérfanos. Si algo no cuadra, restaura el original y sale con error.

Uso (desde la raíz del repo):
  python3 tools/refactor/move_block.py --first RECOLOR_FLOATING_WINDOW_DEFAULT_SIZE --stop-before ContornoFloatingWindow \
          --dest RecolorWindows.kt --title "Ventanas de efectos: Recolor, Color básico y Básico 3D" [--apply]

--first        primer símbolo top-level del bloque (se incluye su KDoc/comentarios contiguos)
--stop-before  primer símbolo top-level DESPUÉS del bloque (no se mueve; se excluye su KDoc)
--dest         nombre del archivo nuevo dentro de ui/editor/
"""
import argparse, pathlib, re, shutil, sys, textwrap

ROOT = pathlib.Path(__file__).resolve().parent.parent.parent
UI = ROOT / "app/src/main/java/com/yeivikas/olyzecs/ui"
SRC = UI / "EditorScreen.kt"
IMPLICIT = {"getValue", "setValue", "provideDelegate", "plus", "minus", "times", "div", "rem", "unaryMinus", "unaryPlus", "not", "inc",
            "dec", "compareTo", "contains", "get", "set", "invoke", "iterator", "next", "hasNext", "rangeTo", "rangeUntil", "equals",
            *[f"component{i}" for i in range(1, 10)]}
DECL = re.compile(r'^(?:@\w+(?:\([^)]*\))?\s+)*(?:(private|internal|public)\s+)?'
                  r'(?:(?:inline|data|sealed|enum|const|annotation|abstract|open|suspend|tailrec|fun\s+interface)\s+)*'
                  r'(fun|class|object|interface|val|var|typealias)\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?(\w+)')


def tops_of(lines):
    out = []
    for i, l in enumerate(lines):
        if l and not l[0].isspace() and l[0] not in "}/*)":
            m = DECL.match(l)
            if m:
                out.append((i + 1, m.group(1) or "public", m.group(2), m.group(3)))
    return out


def strip_comments(txt):
    return re.sub(r"//[^\n]*", "", re.sub(r"/\*.*?\*/", "", txt, flags=re.S))


def tokens(txt):
    return set(re.findall(r"[A-Za-z_]\w*", txt))


def word(name, txt):
    return re.search(r"\b" + re.escape(name) + r"\b", txt) is not None


def real_start(lines, n):
    j = n - 1
    while j > 0 and (lines[j - 1].startswith(("@", "/**", " *", "*/", "//", "/*"))):
        j -= 1
    return j + 1


def decl_text(lines, tops, name):
    """Texto de una declaración top-level (desde su línea hasta la siguiente declaración)."""
    idx = [k for k, t in enumerate(tops) if t[3] == name][0]
    a = tops[idx][0]
    b = tops[idx + 1][0] - 1 if idx + 1 < len(tops) else len(lines)
    return lines[a - 1:b], tops[idx]


def signature(lines_of_decl, kind):
    """Para una clase/objeto/interfaz se analiza TODO el cuerpo (sus miembros públicos exponen tipos); para una función, solo la firma."""
    if kind in ("class", "object", "interface"):
        return "\n".join(lines_of_decl)
    depth, out = 0, []
    for l in lines_of_decl:
        out.append(l)
        depth += l.count("(") - l.count(")")
        if depth <= 0 and (l.rstrip().endswith("{") or l.rstrip().endswith("=") or "= " in l):
            break
    return "\n".join(out)


def analyze(first, stop_before):
    L = SRC.read_text(encoding="utf-8").split("\n")
    tops = tops_of(L)
    names = [t[3] for t in tops]
    for n in (first, stop_before):
        if names.count(n) != 1:
            sys.exit(f"ERROR: el símbolo '{n}' aparece {names.count(n)} veces como declaración top-level (debe ser 1)")
    s_line = [t for t in tops if t[3] == first][0][0]
    e_line = [t for t in tops if t[3] == stop_before][0][0]
    S = real_start(L, s_line)
    E = real_start(L, e_line) - 1
    while L[E - 1].strip() == "":
        E -= 1
    if E <= S:
        sys.exit("ERROR: --stop-before debe estar DESPUÉS de --first")
    block, rest = L[S - 1:E], L[:S - 1] + L[E + 1:]
    inblock = [t for t in tops if S <= t[0] <= E]
    unclassified = [(S + i, l[:80]) for i, l in enumerate(block)
                    if l and not l[0].isspace() and l[0] not in "}/*)@" and not DECL.match(l)]
    bt, rt = strip_comments("\n".join(block)), strip_comments("\n".join(rest))
    priv_in = [t for t in inblock if t[1] == "private"]
    priv_out = [t for t in tops if not (S <= t[0] <= E) and t[1] == "private"]
    to_internal = {t[3] for t in priv_in if word(t[3], rt)} | {t[3] for t in priv_out if word(t[3], bt)}
    # Cierre transitivo: si un símbolo pasa a internal, los tipos privados de su firma (o de todo su cuerpo, si es clase) también.
    all_private = {t[3]: t for t in tops if t[1] == "private"}
    changed = True
    while changed:
        changed = False
        for name in sorted(to_internal):
            lines_of, t = decl_text(L, tops, name)
            sig = strip_comments(signature(lines_of, t[2]))
            for p in all_private:
                if p not in to_internal and p != name and word(p, sig):
                    to_internal.add(p); changed = True
    others = {}
    for f in UI.glob("*.kt"):
        if f.name != "EditorScreen.kt":
            for t in tops_of(f.read_text(encoding="utf-8").split("\n")):
                others.setdefault(t[3], []).append((f.name, t[1]))
    return dict(L=L, tops=tops, S=S, E=E, block=block, rest=rest, inblock=inblock, unclassified=unclassified,
                to_internal=sorted(to_internal), others=others,
                in_block=[n for n in sorted(to_internal) if any(t[3] == n for t in inblock)],
                in_rest=[n for n in sorted(to_internal) if not any(t[3] == n for t in inblock)])


def report(a, first, stop_before):
    L, S, E = a["L"], a["S"], a["E"]
    print(f"bloque: líneas {S}-{E} ({E - S + 1} líneas, {len(a['inblock'])} declaraciones) | de '{first}' hasta antes de '{stop_before}'")
    print(f"  primera: {L[S - 1][:80]!r}\n  última : {L[E - 1][:80]!r}\n  siguiente: {L[E + 1][:80]!r}")
    print("  líneas top-level sin clasificar:", len(a["unclassified"]), a["unclassified"][:5])
    print("  private -> internal DENTRO del bloque :", a["in_block"])
    print("  private -> internal en EditorScreen.kt:", a["in_rest"])
    coll = {n: a["others"][n] for n in a["to_internal"] if n in a["others"]}
    dup = {t[3]: a["others"][t[3]] for t in a["inblock"] if t[3] in a["others"]}
    print("  colisiones de nombre (a internal):", coll or "ninguna", "| duplicados al mover:", dup or "ninguno")
    return not coll and not dup and not a["unclassified"]


def apply_move(a, dest, title, adr_note):
    L, S, E = a["L"], a["S"], a["E"]
    out = UI / "editor" / dest
    if out.exists():
        sys.exit(f"ERROR: {out} ya existe")
    block = list(a["block"])
    # visibilidad: SOLO se sustituye la palabra `private` de la línea de declaración; el resto de la línea no se toca
    rest_lines = list(L)
    def flip(lines, offset, name):
        for k, l in enumerate(lines):
            if l and not l[0].isspace() and re.match(r"^private\s", l):
                m = DECL.match(l)
                if m and m.group(3) == name:
                    lines[k] = re.sub(r"^private\b", "internal", l, count=1); return True
        return False
    for name in a["in_block"]:
        assert flip(block, 0, name), name
    imports = [(i, l) for i, l in enumerate(L) if l.startswith("import ")]
    def simple(l):
        body = l[len("import "):].strip()
        return body.split(" as ")[1].strip() if " as " in body else body.split(".")[-1]
    block_t = tokens("\n".join(block))
    rest_t = tokens("\n".join(l for l in a["rest"] if not l.startswith("import ")))
    keep = [l for i, l in imports if simple(l) == "*" or simple(l) in block_t or simple(l) in IMPLICIT]
    prune = {i for i, l in imports if simple(l) != "*" and simple(l) not in IMPLICIT and simple(l) in block_t and simple(l) not in rest_t}
    header = ("package com.yeivikas.olyzecs.ui\n\n" + "\n".join(keep) + f"\n\n// {'=' * 76}\n//  {dest} — {title}\n// {'=' * 76}\n"
              f"//  ORIGEN: movido TAL CUAL desde `EditorScreen.kt` (Fase 3 del plan de ingeniería; ADR-022). Sin cambios de lógica.\n"
              f"{adr_note}"
              f"//  El paquete SIGUE siendo `com.yeivikas.olyzecs.ui` (la carpeta `editor/` es solo organización).\n// {'=' * 76}\n\n")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(header + "\n".join(block) + "\n", encoding="utf-8")
    # recorte de EditorScreen.kt (+ visibilidad de lo que se queda y ahora usa el archivo nuevo)
    new_rest = [l for i, l in enumerate(L) if not (S <= i + 1 <= E + 1) and i not in prune]
    for name in a["in_rest"]:
        assert flip(new_rest, 0, name), name
    SRC.write_text("\n".join(new_rest), encoding="utf-8")
    return out, len(keep), len(prune)


def verify(orig_text, dest, a):
    import difflib
    ed = SRC.read_text(encoding="utf-8"); fw = (UI / "editor" / dest).read_text(encoding="utf-8")
    to, te, tf = (tops_of(t.split("\n")) for t in (orig_text, ed, fw))
    errs = []
    if sorted(t[3] for t in to) != sorted([t[3] for t in te] + [t[3] for t in tf]):
        errs.append("el conjunto de declaraciones cambió")
    vo = {t[3]: t[1] for t in to}; vn = {t[3]: t[1] for t in te + tf}
    vis = {n: (vo[n], vn[n]) for n in vo if vo[n] != vn[n]}
    if sorted(vis) != a["to_internal"] or any(v != ("private", "internal") for v in vis.values()):
        errs.append(f"visibilidades inesperadas: {vis}")
    O, F = orig_text.split("\n"), fw.split("\n")
    ob = O[a["S"] - 1:a["E"]]
    st = next((i for i, l in enumerate(F) if l == ob[0] and F[i + 1] == ob[1]), None)
    if st is None:
        errs.append("no se encuentra el bloque en el archivo nuevo")
    else:
        d = [x for x in difflib.unified_diff(ob, F[st:st + len(ob)], lineterm="", n=0) if not x.startswith(("---", "+++", "@@"))]
        if any(not (x.startswith(("-private ", "+internal ", "-@", "+@")) or True) for x in d):
            errs.append("el bloque difiere en algo más que la visibilidad")
        bad = [x for x in d if not re.match(r"^[-+](private|internal)\s", x)]
        if bad:
            errs.append(f"líneas distintas en el bloque (no son visibilidad): {bad[:4]}")
    NL = ed.split("\n"); removed = [l for l in O if l.startswith("import ") and l not in NL]
    rebuilt = [l for i, l in enumerate(O) if not (a["S"] <= i + 1 <= a["E"] + 1) and l not in removed]
    # las líneas de declaración con visibilidad cambiada difieren a propósito: se normalizan antes de comparar
    norm = lambda ls: [re.sub(r"^(private|internal)\b", "V", l) if l and not l[0].isspace() and DECL.match(l) else l for l in ls]
    if norm(rebuilt) != norm(NL):
        errs.append("EditorScreen.kt != original - bloque - imports huérfanos")
    return errs, removed


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--first", required=True); ap.add_argument("--stop-before", required=True)
    ap.add_argument("--dest", required=True); ap.add_argument("--title", default="")
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    a = analyze(args.first, args.stop_before)
    ok = report(a, args.first, args.stop_before)
    if not ok:
        sys.exit("ERROR: el análisis encontró colisiones o código sin clasificar; no se aplica nada")
    if not args.apply:
        print("\n(en seco: no se modificó nada; añade --apply para aplicar)"); return
    orig = SRC.read_text(encoding="utf-8"); backup = SRC.with_suffix(".kt.bak"); shutil.copy(SRC, backup)
    note = ""
    if a["to_internal"]:
        note = textwrap.fill(f"Visibilidad: {', '.join(a['to_internal'])} pasan de `private` a `internal` (se usan entre archivos del paquete).",
                             width=124, initial_indent="//  ", subsequent_indent="//      ") + "\n"
    out, n_keep, n_prune = apply_move(a, args.dest, args.title or args.dest, note)
    errs, removed = verify(orig, args.dest, a)
    if errs:
        shutil.copy(backup, SRC); out.unlink(missing_ok=True); backup.unlink()
        sys.exit("VERIFICACIÓN FALLIDA, original restaurado:\n  " + "\n  ".join(errs))
    backup.unlink()
    print(f"\nOK: {len(a['block'])} líneas -> {out.relative_to(ROOT)} | imports: {n_keep} en el nuevo, {n_prune} podados de EditorScreen.kt | "
          f"EditorScreen.kt: {len(orig.split(chr(10)))} -> {len(SRC.read_text(encoding='utf-8').split(chr(10)))} líneas")


if __name__ == "__main__":
    main()

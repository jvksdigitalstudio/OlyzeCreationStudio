import re, pathlib
ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT/'app/src'
def kt_files(sub=('main','test')):
    for s in sub:
        yield from sorted((SRC/s).rglob('*.kt'))
_tok = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\\n])*"|/\*[\s\S]*?\*/|//[^\n]*|\'(?:\\.|[^\'\\\n])\'')
def strip_comments(text):
    """Quita comentarios y KDoc conservando cadenas (un nombre dentro de "$x" cuenta como uso)."""
    out=[];i=0
    pat=re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\\n])*"|/\*[\s\S]*?\*/|//[^\n]*|\'(?:\\.|[^\'\\\n])\'')
    last=0
    for m in pat.finditer(text):
        s=m.group(0)
        out.append(text[last:m.start()])
        if s.startswith('//') or s.startswith('/*'):
            out.append(' '*0 if s.startswith('//') else '\n'*s.count('\n'))
        else:
            out.append(s)
        last=m.end()
    out.append(text[last:])
    return ''.join(out)

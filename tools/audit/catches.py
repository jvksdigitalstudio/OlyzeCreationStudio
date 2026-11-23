import re,collections
from common import *
pat=re.compile(r'catch\s*\(\s*(?:_|\w+)\s*:\s*([\w.]+)\s*\)\s*\{')
rows=[]
for f in kt_files(('main',)):
    t=f.read_text(encoding='utf-8'); code=strip_comments(t)
    for m in pat.finditer(code):
        i=m.end(); depth=1
        while i<len(code) and depth:
            depth+= (code[i]=='{')-(code[i]=='}'); i+=1
        body=code[m.end():i-1]
        b=re.sub(r'\s+',' ',body).strip()
        logs=bool(re.search(r'AppLogger|\.(e|w|i|d)\(|logFailure|report|fail\(|onFailure|onError|lastUserFacingError',b))
        throws=bool(re.search(r'\bthrow\b',b))
        rets=bool(re.search(r'\breturn\b|\bnull\b|false|emptyList|continue|break',b))
        kind='registra' if logs else 'relanza' if throws else 'SILENCIOSO' if b=='' or len(b)<60 else 'otro'
        ln=code.count('\n',0,m.start())+1
        rows.append((kind,m.group(1),str(f.relative_to(SRC)),ln,b[:110]))
c=collections.Counter((r[0]) for r in rows)
print("total catch:",len(rows),dict(c))
print("\n--- por tipo de excepción capturada ---"); print(dict(collections.Counter(r[1] for r in rows).most_common(8)))
print("\n--- SILENCIOSOS (sin registro ni relanzado) ---")
for r in rows:
    if r[0]=='SILENCIOSO': print(f"  {r[2]}:{r[3]}  catch({r[1]}) -> {r[4]!r}")

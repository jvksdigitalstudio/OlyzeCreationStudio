import re
from common import *
IMPLICIT = {'getValue','setValue','provideDelegate','plus','minus','times','div','rem','unaryMinus','unaryPlus','not','inc','dec','contains','get','set','invoke','iterator','next','hasNext','compareTo','rangeTo','rangeUntil','equals','component1','component2','component3','component4','component5','plusAssign','minusAssign','timesAssign','divAssign','remAssign'}
imp = re.compile(r'^import\s+([\w.]+)(?:\.\*)?(?:\s+as\s+(\w+))?\s*$', re.M)
total=0; per={}
for f in kt_files():
    text=f.read_text(encoding='utf-8')
    body_nocmt = text  # conservador: comentarios cuentan como uso
    lines=text.split('\n')
    body='\n'.join(l for l in lines if not l.startswith('import '))
    unused=[]
    for m in imp.finditer(text):
        full=m.group(0)
        if full.rstrip().endswith('.*'): continue
        fq=m.group(1); name=m.group(2) or fq.split('.')[-1]
        if name in IMPLICIT: continue
        if not re.search(r'(?<![\w`])'+re.escape(name)+r'(?![\w`])', body):
            unused.append((fq,name))
    if unused:
        per[str(f.relative_to(SRC))]=unused; total+=len(unused)
print(f"{total} imports sin uso en {len(per)} archivos")
for k,v in sorted(per.items(), key=lambda kv:-len(kv[1]))[:12]:
    print(f"  {len(v):>3}  {k}")

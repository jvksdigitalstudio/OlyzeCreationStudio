import re
from common import *
fn=re.compile(r'^[ \t]*(?:@\w+(?:\([^)]*\))?[ \t]*)*(?:(?:private|internal|public|protected|override|suspend|inline|operator|open|abstract)[ \t]+)*fun[ \t]+(?:<[^>]*>[ \t]*)?(?:[\w.<>?, ]+\.)?(\w+)\s*\(',re.M)
rows=[]
for f in kt_files(('main',)):
    code=strip_comments(f.read_text(encoding='utf-8'))
    for m in fn.finditer(code):
        j=code.find('{',m.end()); eq=code.find('=',m.end())
        # saltar parámetros balanceando paréntesis
        d=0;k=m.end()-1
        while k<len(code):
            d+=(code[k]=='(')-(code[k]==')')
            if d==0: break
            k+=1
        # después de ')' puede venir ': Tipo {' o '= ...'
        rest=code[k+1:k+400]
        mb=re.match(r'\s*(?::\s*[^={]+)?\s*\{',rest)
        if not mb: continue
        s=k+1+mb.end()-1; d=0; e=s
        while e<len(code):
            d+=(code[e]=='{')-(code[e]=='}')
            if d==0: break
            e+=1
        lines=code.count('\n',s,e)+1
        rows.append((lines,m.group(1),str(f.relative_to(SRC)).split('olyzecs/')[-1],code.count('\n',0,m.start())+1))
rows.sort(reverse=True)
print("funciones analizadas:",len(rows)," | >150 líneas:",sum(1 for r in rows if r[0]>150)," | >400:",sum(1 for r in rows if r[0]>400))
for r in rows[:14]: print(f"  {r[0]:>5} líneas  {r[2]}:{r[3]}  {r[1]}")

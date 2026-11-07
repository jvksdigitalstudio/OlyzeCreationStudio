import re,collections
from common import *
decl = re.compile(r'^[ \t]*((?:@\w+(?:\([^)]*\))?[ \t]*)*)((?:(?:internal|public|protected|open|abstract|final|inline|suspend|operator|infix|tailrec|data|sealed|enum|annotation|value|inner|const)[ \t]+)*)(fun|class|object|interface)[ \t]+(?:<[^>]*>[ \t]*)?(?:[\w.<>?, ]+\.)?`?(\w+)`?', re.M)
word=lambda n: re.compile(r'(?<![\w`])'+re.escape(n)+r'(?![\w`])')
main_code={str(f.relative_to(SRC)):strip_comments(f.read_text(encoding='utf-8')) for f in kt_files(('main',))}
test_code={str(f.relative_to(SRC)):strip_comments(f.read_text(encoding='utf-8')) for f in kt_files(('test',))}
other=''.join(p.read_text(encoding='utf-8',errors='ignore') for p in list((SRC/'main/res').rglob('*.xml'))+[SRC/'main/AndroidManifest.xml']+list((SRC/'main/cpp').rglob('*.cpp'))+list((SRC/'main/cpp').rglob('*.h'))+list(ROOT.glob('**/proguard*.pro')))
allmain='\n'.join(main_code.values()); alltest='\n'.join(test_code.values())
cands=[]
for path,code in main_code.items():
    for m in decl.finditer(code):
        pre=code[m.start():m.end()]
        line_end=code.find('\n',m.end()); line=code[m.start():line_end if line_end>0 else None]
        if 'private ' in m.group(2) or re.search(r'\b(private|override|external)\b',line): continue
        if '@Preview' in m.group(1): continue
        kind,name=m.group(3),m.group(4)
        if name in ('main','invoke','Companion') or name.startswith('Java_'): continue
        n_main=len(word(name).findall(allmain)); n_test=len(word(name).findall(alltest)); n_other=len(word(name).findall(other))
        if n_main<=1 and n_other==0:
            ln=code.count('\n',0,m.start())+1
            cands.append(('SIN REFERENCIAS' if n_test==0 else 'SOLO PRUEBAS',path,ln,kind,name,n_test))
c=collections.Counter(x[0] for x in cands)
print(dict(c))
for tag in ('SIN REFERENCIAS','SOLO PRUEBAS'):
    print(f"\n== {tag} ==")
    for x in cands:
        if x[0]==tag: print("  %s:%d  %s %s%s"%(x[1],x[2],x[3],x[4],f"  (en pruebas: {x[5]})" if x[5] else ""))

import re
from common import *
decl = re.compile(r'^\s*(?:@\w+(?:\([^)]*\))?\s+)*private\s+(?:(?:const|lateinit|suspend|inline|tailrec|operator|infix|data|sealed|enum|abstract|open)\s+)*(fun|val|var|class|object|interface|typealias)\s+(?:<[^>]*>\s*)?(?:[\w.<>?, ]+\.)?`?(\w+)`?', re.M)
res=[]
for f in kt_files():
    raw=f.read_text(encoding='utf-8'); code=strip_comments(raw)
    for m in decl.finditer(code):
        kind,name=m.group(1),m.group(2)
        n=len(re.findall(r'(?<![\w`])'+re.escape(name)+r'(?![\w`])', code))
        if n<=1:
            line=code.count('\n',0,m.start())+1
            res.append((str(f.relative_to(SRC)),line,kind,name))
print(f"{len(res)} declaraciones private con UNA sola aparición (solo su declaración)")
for r in res: print("  %s:%d  %s %s"%r)

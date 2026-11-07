import re
from common import *
blocking=re.compile(r'\b(projectStorage|ProjectStorage|layerRepository|LayerRepository|AudioProcessor|ThumbnailRenderer|VideoExporter|ProjectSharing|ProjectArchive)\.\w+\(|BitmapFactory\.|File\(|\.readText\(|\.readBytes\(|\.writeText\(|\.openInputStream\(|\.decode\w*\(|\.compress\(|probeDurationMs\(|extractMetadata\(')
def match_brace(s,i):   # s[i]=='{'
    d=0
    for j in range(i,len(s)):
        d+=(s[j]=='{')-(s[j]=='}')
        if d==0: return j
    return len(s)-1
def remove_bg(block):
    pat=re.compile(r'withContext\(\s*Dispatchers\.(?:IO|Default)[^)]*\)\s*\{|launch\(\s*Dispatchers\.(?:IO|Default)[^)]*\)\s*\{|async\(\s*Dispatchers\.(?:IO|Default)[^)]*\)\s*\{')
    while True:
        m=pat.search(block)
        if not m: return block
        e=match_brace(block,m.end()-1)
        block=block[:m.start()]+' /*BG*/ '+block[e+1:]
tot=0
for f in kt_files(('main',)):
    p=str(f.relative_to(SRC))
    if 'viewmodel/' not in p: continue
    code=strip_comments(f.read_text(encoding='utf-8'))
    for m in re.finditer(r'viewModelScope\.launch\s*(?:\(\s*(\w[\w.]*)?[^)]*\))?\s*\{',code):
        if m.group(0).count('Dispatchers.IO') or m.group(0).count('Dispatchers.Default'): continue
        e=match_brace(code,m.end()-1); block=code[m.end():e]
        rest=remove_bg(block)
        for b in blocking.finditer(rest):
            ln=code.count('\n',0,m.end()+b.start())+1
            print(f"  {p.split('olyzecs/')[-1]}:{ln}  llamada en hilo principal: {b.group(0)}")
            tot+=1
print("total sospechosas:",tot)

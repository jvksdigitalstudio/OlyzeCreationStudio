import re,collections,hashlib
from common import *
W=12
files={}
for f in kt_files(('main',)):
    code=strip_comments(f.read_text(encoding='utf-8'))
    norm=[]  # (lineno, texto normalizado)
    for i,l in enumerate(code.split('\n'),1):
        s=re.sub(r'\s+',' ',l.strip())
        if not s or s in ('{','}',')','})',']','),') or s.startswith('import ') or s.startswith('package '): continue
        if len(s)<8: continue
        norm.append((i,s))
    files[str(f.relative_to(SRC)).split('olyzecs/')[-1]]=norm
idx=collections.defaultdict(list)
for fn,norm in files.items():
    for k in range(len(norm)-W+1):
        h=hashlib.md5('\n'.join(s for _,s in norm[k:k+W]).encode()).hexdigest()
        idx[h].append((fn,k))
# agrupar ventanas consecutivas en bloques
seen=set(); blocks=[]
for h,occ in idx.items():
    if len(occ)<2: continue
    for fn,k in occ:
        if (fn,k) in seen: continue
        # extender hacia delante mientras el siguiente hash también se repita entre las mismas ocurrencias
        group=[(a,b) for a,b in occ]
        length=W; 
        cur=group
        while True:
            nxt=[]
            for a,b in cur:
                n=files[a]
                if b+length< len(n):
                    hh=hashlib.md5('\n'.join(s for _,s in n[b+1:b+1+W]).encode()).hexdigest()
                    if len(idx[hh])>=2: nxt.append((a,b+1))
            if len(nxt)==len(cur) and len(cur)>=2:
                cur=nxt; length+=1
            else: break
        for a,b in group: 
            for t in range(b,b+length-W+1): seen.add((a,t))
        blocks.append((length,[(a,files[a][b][0]) for a,b in group]))
blocks.sort(key=lambda x:-x[0]*len(x[1]))
print(f"{len(blocks)} bloques duplicados (>= {W} líneas significativas)")
for length,occ in blocks[:14]:
    where='; '.join(f"{a.split('/')[-1]}:{l}" for a,l in occ[:4])+(f" (+{len(occ)-4})" if len(occ)>4 else '')
    print(f"  {length:>3} líneas ×{len(occ)}  {where}")

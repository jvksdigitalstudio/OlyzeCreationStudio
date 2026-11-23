import re
from common import *
io = re.compile(r'BitmapFactory\.decode\w*\(|ImageDecoder\.|\.openInputStream\(|\.openFileDescriptor\(|\.openOutputStream\(|\.readText\(|\.readBytes\(|\.writeText\(|\.writeBytes\(|FileInputStream\(|FileOutputStream\(|\.listFiles\(|MediaMetadataRetriever\(|\.compress\(Bitmap|\.copyTo\(|ProjectStorage\.|\.deleteRecursively\(')
bg = re.compile(r'Dispatchers\.(IO|Default)|withContext|Executor|\.submit\(|\.execute\(|Thread|produceState|suspend fun|LaunchedEffect')
hits=[]
for f in kt_files(('main',)):
    p=str(f.relative_to(SRC))
    if not (p.startswith('main/java/com/yeivikas/olyzecs/ui/') or 'MainActivity' in p): continue
    t=strip_comments(f.read_text(encoding='utf-8')); lines=t.split('\n')
    for i,l in enumerate(lines):
        if io.search(l):
            window='\n'.join(lines[max(0,i-60):i+1])   # contexto hacia atrás
            near_bg=bool(bg.search(window))
            hits.append((p.split('olyzecs/')[-1],i+1,near_bg,l.strip()[:110]))
print("llamadas de E/S en ui/ + MainActivity:",len(hits)," | sin contexto de fondo en las 60 líneas previas:",sum(1 for h in hits if not h[2]))
for h in hits:
    if not h[2]: print(f"  {h[0]}:{h[1]}  {h[3]}")

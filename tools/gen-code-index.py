#!/usr/bin/env python3
"""يولّد docs/code-index.md: كل تصريح (class/fun/val عام) مع ملفه وسطره، للبحث بـ Grep بدل قراءة الملفات."""
import pathlib, re

ROOT = pathlib.Path(__file__).resolve().parent.parent
KT = re.compile(r'^\s*(?:@\w+\s+)*(?:(?:private|internal|public|data|sealed|enum|object|abstract|open|suspend|inline|override)\s+)*(class|interface|object|fun|val|var)\s+(?:<[^>]+>\s*)?([\w.]+)')
JS = re.compile(r'^(?:async\s+)?function\s+(\w+)|^const\s+(\w+)\s*=\s*(?:async\s*)?\(')
out = ['# code-index (مولّد: python3 tools/gen-code-index.py، لا يُقرأ كاملاً، استخدم Grep)', '']
for base, pat in (('app/src/main/java', '*.kt'), ('backend/worker/src', '*.js'), ('backend/scripts', '*.mjs')):
    for f in sorted((ROOT / base).rglob(pat)):
        rel = f.relative_to(ROOT).as_posix()
        for i, line in enumerate(f.read_text(encoding='utf-8').splitlines(), 1):
            if pat == '*.kt':
                m = KT.match(line)
                if m and not line.startswith('        ') and (m.group(1) not in ('val', 'var') or not line.startswith(' ')):
                    out.append(f'{m.group(2)} | {m.group(1)} | {rel}:{i}')
            else:
                m = JS.match(line)
                if m:
                    out.append(f'{m.group(1) or m.group(2)} | fn | {rel}:{i}')
(ROOT / 'docs' / 'code-index.md').write_text('\n'.join(out) + '\n', encoding='utf-8')
print(len(out) - 2, 'entries')

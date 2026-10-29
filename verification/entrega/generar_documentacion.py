"""Genera documentación HTML local/offline con la biblioteca estándar. No publica."""
from pathlib import Path
import html
import os
import re

ROOT=Path(__file__).resolve().parents[2]
SITE=ROOT/'docs/sitio'

def main():
    sources=sorted(set(list(ROOT.glob('*.md'))+list((ROOT/'docs').glob('*.md'))+
                       [ROOT/'verification/README.md',ROOT/'evidencias/README.md']))
    sources=[p for p in sources if p.exists()]
    names={p.resolve():str(p.relative_to(ROOT)).replace('\\','__').replace('/','__')[:-3]+'.html' for p in sources}
    SITE.mkdir(parents=True,exist_ok=True)
    def target(source,value):
        if value.startswith(('http://','https://','#')):return value
        resolved=(source.parent/value).resolve()
        if resolved in names:return names[resolved]
        return os.path.relpath(resolved,SITE).replace('\\','/')
    def inline(source,line):
        value=html.escape(line)
        value=re.sub(r'!\[([^]]*)\]\(([^)]+)\)',lambda m:'<img alt="'+m[1]+'" src="'+html.escape(target(source,html.unescape(m[2])))+'">',value)
        value=re.sub(r'(?<!!)\[([^]]+)\]\(([^)]+)\)',lambda m:'<a href="'+html.escape(target(source,html.unescape(m[2])))+'">'+m[1]+'</a>',value)
        value=re.sub(r'`([^`]+)`',r'<code>\1</code>',value)
        return re.sub(r'\*\*([^*]+)\*\*',r'<strong>\1</strong>',value)
    nav=''.join('<a href="'+name+'">'+html.escape(str(path.relative_to(ROOT)))+'</a>' for path,name in names.items())
    css='body{margin:0;background:#f8fafc;color:#172033;font:16px/1.6 system-ui}nav{padding:20px;background:#101b30;color:white}nav a{color:#e2e8f0;display:inline-block;margin:5px 12px}main{max-width:1000px;margin:auto;padding:30px}h1,h2{line-height:1.25}a{color:#154eac}code,pre{background:#e8edf4;border-radius:4px}code{padding:2px 4px}pre{padding:16px;overflow:auto}table{width:100%;border-collapse:collapse;background:white}td,th{padding:9px;border:1px solid #cbd5e1;text-align:left}img{max-width:100%;height:auto}li{margin:5px 0}:focus-visible{outline:3px solid #7c3aed}@media(max-width:700px){main{padding:12px}table{display:block;overflow:auto}}'
    for source,name in names.items():
        body=[];code=[];fenced=False;table=False;listing=False
        for line in source.read_text(encoding='utf-8-sig').splitlines():
            if line.startswith('```'):
                if fenced:body.append('<pre><code>'+html.escape('\n'.join(code))+'</code></pre>');code=[]
                fenced=not fenced;continue
            if fenced:code.append(line);continue
            if line.startswith('|'):
                if not table:body.append('<table>');table=True
                cells=[x.strip() for x in line.strip('|').split('|')]
                if all(re.fullmatch(r'[-: ]+',c) for c in cells):continue
                body.append('<tr>'+''.join('<td>'+inline(source,c)+'</td>' for c in cells)+'</tr>');continue
            if table:body.append('</table>');table=False
            if line.startswith('- '):
                if not listing:body.append('<ul>');listing=True
                body.append('<li>'+inline(source,line[2:])+'</li>');continue
            if listing:body.append('</ul>');listing=False
            heading=re.match(r'^(#{1,4})\s+(.*)',line)
            if heading:
                level=len(heading[1]);body.append(f'<h{level}>'+inline(source,heading[2])+f'</h{level}>')
            elif line.strip():body.append('<p>'+inline(source,line)+'</p>')
        if table:body.append('</table>')
        if listing:body.append('</ul>')
        document='<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Illari — '+html.escape(source.stem)+'</title><style>'+css+'</style><nav aria-label="Documentación">'+nav+'</nav><main>'+''.join(body)+'</main></html>'
        (SITE/name).write_text(document,encoding='utf-8')
    (SITE/'index.html').write_text('<!doctype html><html lang="es"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>ILLARI LEGAL — Unidad2</title><style>'+css+'</style><main><h1>ILLARI LEGAL — Unidad2</h1><p>Documentación local de entrega. No publica contenido ni contiene credenciales.</p>'+'<nav aria-label="Documentación">'+nav+'</nav></main></html>',encoding='utf-8')
    print('Documentos HTML locales:',len(names)+1)

if __name__=='__main__':main()

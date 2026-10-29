"""Preflight portable y smoke SOLO LECTURA. No crea datos ni aplica migraciones."""
import argparse
import ast
import hashlib
import json
import os
from pathlib import Path
import re
import sys

ROOT=Path(__file__).resolve().parents[2]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--package',action='store_true')
    parser.add_argument('--env-file')
    parser.add_argument('--project',default='illari-fase3-audit')
    parser.add_argument('--output',required=True)
    args=parser.parse_args();results=[]
    def record(name,passed,actual=None):
        results.append({'name':name,'passed':bool(passed),'actual':actual})
        destination=Path(args.output);destination.parent.mkdir(parents=True,exist_ok=True)
        destination.write_text(json.dumps({'passed':sum(x['passed'] for x in results),'failed':sum(not x['passed'] for x in results),'results':results},indent=2,ensure_ascii=False),encoding='utf-8')
        print(('PASS ' if passed else 'FAIL ')+name,flush=True)
    necessary=['README.md','FASE5_RESULTADOS.md','INFORME_FINAL_UNIDAD_2.md','MATRIZ_CUMPLIMIENTO_S12.md',
               'GUIA_DE_SUSTENTACION.md','CHECKLIST_ENTREGA_FINAL.md','GUIA_INSTALACION.md','SOLUCION_ERRORES.md',
               'docker-compose.yml','obs/compose.s12.yml','infra/keycloak/realm-illari-legal.json',
               'verification/infra/verificar_parte_b.py','verification/saga/pom.xml','docs/sitio/index.html']
    record('Entradas, documentación y verificadores disponibles',all((ROOT/p).is_file() for p in necessary))
    record('Migraciones aditivas y bootstrap presentes',all((ROOT/'database'/n).is_file() for n in
           ['01-schema.sql','04-fase1-saga.sql','05-fase2-compras-detalle.sql','06-fase2-mensajeria.sql','07-fase3-identidad-juridica.sql','s12/00-init.sh']))
    missing=[]
    for source in list(ROOT.glob('*.md'))+list((ROOT/'docs').glob('*.md')):
        for href in re.findall(r'\]\(([^)]+)\)',source.read_text(encoding='utf-8-sig')):
            if not href.startswith(('http://','https://','#')) and not (source.parent/href).exists():missing.append(str(source.relative_to(ROOT))+': '+href)
    record('Enlaces locales de documentación válidos',not missing,missing)
    for path in (ROOT/'verification').rglob('*.py'):
        if '__pycache__' not in path.parts:ast.parse(path.read_text(encoding='utf-8-sig'))
    record('Verificadores Python compilan sintácticamente',True)
    template=(ROOT/'.env.s12.example').read_text(encoding='utf-8-sig')
    record('Plantilla sin valores de contraseñas',all(not line.split('=',1)[1].strip() for line in template.splitlines() if re.match(r'^[A-Z_]*PASSWORD=',line)))
    realm=json.loads((ROOT/'infra/keycloak/realm-illari-legal.json').read_text(encoding='utf-8-sig'))
    record('Realm de entrega sin usuarios preconfigurados',not realm.get('users'))
    if args.package:
        bad=[];jwt=[]
        for base,dirs,files in os.walk(ROOT,followlinks=False):
            for name in list(dirs):
                if name in ('node_modules','target','dist','.angular','__pycache__','.git'):
                    bad.append(str((Path(base)/name).relative_to(ROOT)));dirs.remove(name)
            for name in files:
                p=Path(base)/name
                if name.startswith('.env') and name!='.env.s12.example' or p.suffix in ('.dump','.backup','.pyc','.zip'):bad.append(str(p.relative_to(ROOT)))
                if p.suffix in ('.json','.md','.log','.yml','.yaml') and re.search(r'eyJ[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}',p.read_text(encoding='utf-8-sig',errors='replace')):jwt.append(str(p.relative_to(ROOT)))
        record('Paquete sin dependencias/generados/archivos privados',not bad,bad)
        record('Sin JWT completo en documentos/configuración/evidencias',not jwt,jwt)
    if args.env_file:
        sys.path.insert(0,str(ROOT/'verification/infra'))
        from verificar_s12 import Suite
        old=Path.cwd();os.chdir(ROOT)
        try:
            suite=Suite(argparse.Namespace(env_file=args.env_file,project=args.project,output=args.output))
            for service,port in [('legal-gateway',18080),('cliente-ms',8081),('servicio-legal-ms',8082),('orden-legal-ms',8083),('pago-ms',8084),('backend-legacy',8080)]:
                status,body,_=suite.http('GET',suite.base(service,port)+'/actuator/health/readiness')
                record(service+' readiness real UP',status==200 and json.loads(body).get('status')=='UP')
            status,body,_=suite.http('GET',suite.gateway+'/api/v1/servicios')
            record('Ruta real de catálogo mediante Gateway',status==200 and len(json.loads(body))>=2)
            status,_,_=suite.http('GET',suite.front+'/health/ready');record('nginx entrega readiness del Gateway',status==200)
            record('Compra53 y dos detalles intactos',suite.sql('illari_ordenes',
                   "SELECT count(*) FROM ordenes_legales o WHERE id=53 AND estado='PAGADA' AND total=310 AND pago_id=53 "
                   'AND (SELECT count(*) FROM orden_detalle d WHERE d.orden_id=o.id)=2')=='1')
            record('Pago53 VALIDADO por310.00 conservado',suite.sql('illari_pagos',
                   "SELECT count(*) FROM pagos_legales WHERE id=53 AND id_orden=53 AND estado='VALIDADO' AND monto=310")=='1')
            state=suite.cmd(suite.compose+['ps','--format','json']);record('Compose del laboratorio se consulta desde entrega',bool(state))
        finally:os.chdir(old)
    raise SystemExit(1 if any(not x['passed'] for x in results) else 0)

if __name__=='__main__':main()

#!/usr/bin/env python3
"""Check route logic, durable passenger controls and packaged asset contracts."""
from pathlib import Path
import hashlib,json,math,os,struct,subprocess
from build import ROOT,server,tool
api=server();classes=ROOT/'build/classes';tests=ROOT/'build/test-classes';tests.mkdir(parents=True,exist_ok=True)
cp=str(classes)+os.pathsep+str(api)
subprocess.run([tool('javac'),'--release','25','-cp',cp,'-d',str(tests),*map(str,(ROOT/'src/test/java').rglob('*.java'))],check=True)
for test in ['GraphTests','PassengerStateTests']:subprocess.run([tool('java'),'-ea','-cp',cp+os.pathsep+str(tests),'dev.slipstream.'+test],check=True)
res=ROOT/'src/main/resources';items=list((res/'Server/Item/Items/Slipstream').glob('*.json'));assert len(items)==55
icons=list((res/'Common/Icons/Items').glob('ST_*.png'))+list((res/'Common/Icons/CraftingCategories/Slipstream').glob('*.png'));assert len(icons)==61
for p in icons:
 raw=p.read_bytes();assert raw[:8]==b'\x89PNG\r\n\x1a\n' and struct.unpack('>II',raw[16:24])==(64,64),p.name
for p in items:
 d=json.loads(p.read_text());b=d['BlockType'];assert (res/'Common'/b['CustomModel']).is_file();assert (res/'Common'/d['Icon']).is_file();assert (res/'Server/Item/Block/Hitboxes/Slipstream'/(b['HitboxType']+'.json')).is_file();assert (res/'Common'/b['CustomModelTexture'][0]['Texture']).is_file()
 req=d['Recipe']['BenchRequirement'][0];assert req['Id']==('Workbench' if p.stem=='ST_Workbench' else 'SlipstreamWorkbench')
for p in res.rglob('*.blockymodel'):
 d=json.loads(p.read_text());assert d['nodes']
 for n in d['nodes']:assert abs(sum(v*v for v in n['orientation'].values())-1)<1e-6
bench=json.loads((res/'Server/Item/Items/Slipstream/ST_Workbench.json').read_text())['BlockType']['Bench'];assert bench['Id']=='SlipstreamWorkbench' and len(bench['Categories'])==6
for c in bench['Categories']:assert (res/'Common'/c['Icon']).is_file()
wind=json.loads((res/'Server/Audio/SoundEvents/Slipstream/ST_SFX_Suction.json').read_text());assert wind['Volume']==-18 and wind['MaxDistance']==4
for entry in json.loads((ROOT/'assets/audio/provenance.json').read_text())['runtime_exports']:assert hashlib.sha256((res/entry['runtime']).read_bytes()).hexdigest()==entry['sha256']
print('Asset checks passed:55items,61icons,six bench categories,model rotations,recipe references and audio hashes.')

#!/usr/bin/env python3
"""Build the mod using a separately supplied, pinned official server API."""
from pathlib import Path
import hashlib,json,os,shutil,subprocess,zipfile
from release_pipeline import config, filename
ROOT=Path(__file__).resolve().parents[1]
EXPECTED_SERVER=config(ROOT/'release.json')['sdkSha256']
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def tool(name):
 home=os.environ.get('ST_JAVA_HOME',os.environ.get('JAVA_HOME'))
 found=str(Path(home)/'bin'/(name+('.exe' if os.name=='nt' else ''))) if home else shutil.which(name)
 if not found:raise SystemExit('Set ST_JAVA_HOME to a JDK25 installation')
 return found
def server():
 value=os.environ.get('ST_SERVER_JAR')
 if not value:raise SystemExit('Set ST_SERVER_JAR to your official Hytale0.7.3 HytaleServer.jar')
 path=Path(value)
 if not path.is_file() or sha(path)!=EXPECTED_SERVER:raise SystemExit('Server JAR does not match the verified Hytale0.7.3 pin')
 return path
if __name__=='__main__':
 api=server();classes=ROOT/'build/classes';classes.mkdir(parents=True,exist_ok=True)
 for f in classes.rglob('*.class'):f.unlink()
 sources=sorted((ROOT/'src/main/java').rglob('*.java'))
 subprocess.run([tool('javac'),'--release','25','-Xlint:all','-cp',str(api),'-d',str(classes),*map(str,sources)],check=True)
 manifest=json.loads((ROOT/'src/main/resources/manifest.json').read_text());out=ROOT/'build/libs'/filename(config(ROOT/'release.json'));out.parent.mkdir(parents=True,exist_ok=True)
 entries={}
 for folder in [classes,ROOT/'src/main/resources']:
  for f in folder.rglob('*'):
   if f.is_file():entries[f.relative_to(folder).as_posix()]=f.read_bytes()
 with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
  for name,data in sorted(entries.items()):
   info=zipfile.ZipInfo(name,(2026,10,22,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
 record={'artifact':out.relative_to(ROOT).as_posix(),'sha256':sha(out),'server_sha256':EXPECTED_SERVER,'java_release':25,'entries':len(entries)}
 (ROOT/'build/build.json').write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record,indent=2))

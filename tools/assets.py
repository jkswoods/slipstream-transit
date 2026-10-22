#!/usr/bin/env python3
"""Original Slipstream geometry, pixel atlas, icons and source previews. No native access."""
from pathlib import Path
import json,math,random,uuid
import numpy as np
from PIL import Image,ImageDraw,ImageFont
R=Path(__file__).resolve().parents[1];RES=R/'src/main/resources';C=RES/'Common';S=RES/'Server'
PAL={'teal':'#257f8d','coral':'#d66c52','cream':'#dfddc5','dark':'#253b49','edge':'#71888e','light':'#b4f7e9','glass':'#a9e1de','hazard':'#e2b641'}
IDS={'ST_Harbour':'Harbour Station','ST_Overlook':'Overlook Station','ST_Straight':'Straight Tube','ST_Bend':'Corner Tube','ST_RiseBend':'Rising Elbow','ST_DropBend':'Descending Elbow','ST_Riser':'Vertical Tube','ST_Splitter':'Tube Splitter','ST_Workbench':'Pipe Workbench','ST_UpIntake':'Straight Sucker Station'}
def write(p,d):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(d,indent=2)+'\n')
def vec(a):return dict(zip('xyz',map(float,a)))
def quat(m):
 # columns are local axes; avoid relying on external authoring packages
 tr=np.trace(m)
 if tr>0:
  s=math.sqrt(tr+1)*2;return dict(zip('xyzw',[(m[2,1]-m[1,2])/s,(m[0,2]-m[2,0])/s,(m[1,0]-m[0,1])/s,s/4]))
 i=int(np.argmax(np.diag(m)));j=(i+1)%3;k=(i+2)%3;s=math.sqrt(1+m[i,i]-m[j,j]-m[k,k])*2;q=[0.,0.,0.,0.];q[i]=s/4;q[3]=(m[k,j]-m[j,k])/s;q[j]=(m[j,i]+m[i,j])/s;q[k]=(m[k,i]+m[i,k])/s;return dict(zip('xyzw',q))
def box(name,c,size,mat='teal',basis=None):return {'name':name,'center':list(c),'size':list(size),'material':mat,'basis':np.eye(3).tolist() if basis is None else np.asarray(basis).tolist()}
def tube(points):
 parts=[];pts=[np.array(p,float) for p in points]
 for i,p in enumerate(pts):
  tangent=pts[min(i+1,len(pts)-1)]-pts[max(0,i-1)];tangent/=np.linalg.norm(tangent)
  if i in (0,len(pts)-1):
   axis=int(np.argmax(np.abs(tangent)));sign=1 if tangent[axis]>0 else -1;tangent=np.zeros(3);tangent[axis]=sign
  u=np.cross(tangent,[0,1,0] if abs(tangent[1])<.9 else [0,0,1]);u/=np.linalg.norm(u);v=np.cross(tangent,u)
  ringp=p+tangent*1.1 if i==0 else p-tangent*1.1 if i==len(pts)-1 else p
  for j in (range(8) if i in (0,len(pts)//2,len(pts)-1) else []):
   a=(j+.5)*math.pi/4;rad=u*math.cos(a)+v*math.sin(a);side=np.cross(tangent,rad);basis=np.column_stack([tangent,rad,side])
   parts.append(box(f'Ring{i}_{j}',ringp+rad*13,[2.2,2.2,10.8], 'teal',basis))
   parts.append(box(f'Gasket{i}_{j}',ringp+rad*11.95,[1.7,.3,9.7], 'dark',basis))
   if j%2==0:parts.append(box(f'Fastener{i}_{j}',ringp+rad*14.2,[1.1,.5,1.4], 'edge',basis))
  if i==len(pts)-1:continue
  p2=pts[i+1];direction=p2-p;length=np.linalg.norm(direction);direction/=length
  u=np.cross(direction,[0,1,0] if abs(direction[1])<.9 else [0,0,1]);u/=np.linalg.norm(u);v=np.cross(direction,u)
  for j in range(8):
   a=(j+.5)*math.pi/4;rad=u*math.cos(a)+v*math.sin(a);side=np.cross(direction,rad)
   parts.append(box(f'ClearPanel{i}_{j}',(p+p2)/2+rad*12.4,[max(.5,length-1.5),.25,10.2],'glass',np.column_stack([direction,rad,side])))
 return parts

def model(mid):
 if mid=='ST_Workbench':return [box('Top',[0,25,0],[32,3,28],'cream'),box('Frame',[0,22,0],[29,3,25],'teal'),box('Shelf',[0,6,0],[26,2,23],'dark'),box('Vice',[8,28,4],[10,4,9],'dark'),box('ViceJaw',[8,31,2],[10,2,2],'edge'),box('ViceMovingJaw',[8,31,7],[10,2,2],'edge'),box('Crank',[8,28,12],[8,1,2],'coral'),box('PipeRack',[-7,28,-4],[9,3,14],'teal')]+[box('Leg'+str(i),[x,11,z],[3,22,3],'dark') for i,(x,z) in enumerate([(-12,-10),(12,-10),(-12,10),(12,10)])]
 if mid=='ST_Splitter':
  p=[v for v in tube([[-16,16,0],[0,16,0],[16,16,0]]) if not v['name'].startswith(('ClearPanel','Ring1_','Gasket1_','Fastener1_'))]
  p += [v for v in tube([[0,16,0],[0,16,16]]) if v['name'].startswith(('Ring1_','Gasket1_','Fastener1_'))]
  p += [box('GlassRoof',[0,28.4,0],[24,.25,24],'glass'),box('GlassFloor',[0,3.6,0],[24,.25,24],'glass'),box('RearGlass',[0,16,-12.4],[24,24,.25],'glass'),box('SelectorBadge',[0,29.1,-4],[10,1.4,6],'coral')]
  return p
 station=mid in ('ST_Harbour','ST_Overlook');cy=16
 if station:
  points=[]
  for t in np.linspace(0,1,7):points.append([16*t*t,16*(2*t-t*t),0])
  p=tube(points)
  p += [box('DestinationSign',[0,27.8,-12],[31,8,2],'dark'),box('DestinationGlow',[0,27.8,-13.1],[29,6,.2],'light')]
 else:
  if mid=='ST_Straight':points=[[-16,cy,0],[0,cy,0],[16,cy,0]]
  elif mid in ('ST_Riser','ST_UpIntake'):points=[[0,0,0],[0,cy,0],[0,32,0]]
  else:
   end=np.array([0,0,16] if mid=='ST_Bend' else [0,16,0] if mid=='ST_RiseBend' else [0,-16,0],float);start=np.array([-16,0,0]);points=[]
   for t in np.linspace(0,1,7):points.append(((1-t)**2*start+t*t*end+[0,cy,0]).tolist())
  p=tube(points)
 if mid in ('ST_Harbour','ST_Overlook','ST_UpIntake'):
  for v in p:
   if v['name'].startswith('Ring0_'):v['material']='hazard'
 for part in p:
  if part['material']=='glass':
   extent=np.abs(np.array(part['basis']))@(np.array(part['size'])/2);centre=np.array(part['center']);part['center']=np.clip(centre,np.array([-16,0,-16])+extent,np.array([16,32,16])-extent).tolist()
 return p

COLOURS={'':'#257f8d','Coral':'#cb6655','Gold':'#c5a249','Blue':'#507eaf','Violet':'#886ca4','White':'#d5dbd2'}
def atlas(colour=''):
 im=Image.new('RGBA',(512,384));d=ImageDraw.Draw(im);rng=random.Random(1022)
 for i,(name,col) in enumerate(PAL.items()):
  if name=='teal':col=COLOURS[colour]
  x=(i%4)*128;y=(i//4)*128;rgb=tuple(bytes.fromhex(col[1:]));alpha=14 if name=='glass' else 255;d.rectangle([x,y,x+127,y+127],fill=(*rgb,alpha))
  if name=='hazard':
   for xx in range(128):
    for yy in range(128):
     if ((xx+yy)//4)%2:d.point((x+xx,y+yy),fill=(25,34,37,255))
  elif name=='glass':
   d.line([x+1,y+1,x+30,y+1],fill=(210,255,250,52),width=1)
   d.line([x+4,y+3,x+15,y+3],fill=(210,255,250,27),width=1)
  else:
   # Material detail at the native face scale, restrained highlights and broad wear.
   for yy in range(0,128,16):
    d.line([x,y+yy,x+127,y+yy],fill=tuple(max(0,c-15) for c in rgb)+(255,))
    d.line([x+1,y+yy+1,x+126,y+yy+1],fill=tuple(min(255,c+18) for c in rgb)+(255,))
   for a in range(100):
    xx=x+rng.randrange(0,124);yy=y+rng.randrange(0,126);shade=tuple(max(0,c-rng.randrange(6,17)) for c in rgb);d.rectangle([xx,yy,xx+rng.randrange(2,5),yy+1],fill=(*shade,255))
 (C/'Blocks/Slipstream').mkdir(parents=True,exist_ok=True);texture='TransitAtlas'+('_'+colour if colour else '')+'.png';im.save(C/'Blocks/Slipstream'/texture);(R/'art/source/models').mkdir(parents=True,exist_ok=True);im.save(R/'art/source/models'/texture)
 return texture

def render(mid,parts,size=512,label=True):
 im=Image.new('RGBA',(size,size),'#eef0e8' if label else (0,0,0,0));draw=ImageDraw.Draw(im);faces=[]
 # Faithful geometry source preview, orthographic camera, not a native screenshot.
 camera=np.array([1.5,1.1,1.8]);camera/=np.linalg.norm(camera);right=np.cross([0,1,0],camera);right/=np.linalg.norm(right);up=np.cross(camera,right)
 for p in parts:
  if p['material']=='glass':continue
  basis=np.array(p['basis']);c=np.array(p['center'])-np.array([0,15,0]);hs=np.array(p['size'])/2
  vertices=[c+basis@(hs*np.array([x,y,z])) for x,y,z in [(-1,-1,-1),(1,-1,-1),(1,1,-1),(-1,1,-1),(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]]
  for inds in [(0,1,2,3),(4,7,6,5),(0,4,5,1),(3,2,6,7),(0,3,7,4),(1,5,6,2)]:
   vs=[vertices[k] for k in inds];depth=sum(v@camera for v in vs)/4;xy=[(size/2+(v@right)*8.8*size/512,size/2-(v@up)*8.8*size/512) for v in vs];rgb=tuple(bytes.fromhex(PAL[p['material']][1:]));faces.append((depth,xy,rgb))
 for dep,xy,col in sorted(faces,key=lambda f:f[0]):draw.polygon(xy,fill=col,outline=tuple(max(0,x-25) for x in col))
 if label:draw.text((16,14),IDS[mid],fill='#253b49')
 return im

def main():
 catalog=[];lang='slipstream.board = Name destination\n'
 write(S/'Item/Block/Hitboxes/Slipstream/ST_Select.json',{'Boxes':[{'Min':{'X':0,'Y':0,'Z':0},'Max':{'X':1,'Y':1,'Z':1}}]})
 for colour in COLOURS:
  texture=atlas(colour)
  for base,title in IDS.items():
   if base=='ST_Workbench' and colour:continue
   mid=base+('_'+colour if colour else '');display=(colour+' ' if colour else 'Teal ' if base!='ST_Workbench' else '')+title
   parts=model(base);nodes=[]
   for p in parts:
    i=list(PAL).index(p['material']);ox=i%4*128;oy=i//4*128
    shape=dict(type='box',offset=vec([0,0,0]),stretch=vec([1,1,1]),settings=dict(size=vec(p['size'])),unwrapMode='custom',visible=True,doubleSided=p['material']=='glass',shadingMode='standard',textureLayout={f:dict(offset=dict(x=ox,y=oy),mirror=dict(x=False,y=False),angle=0,**({'transparent':True} if p['material']=='glass' else {})) for f in ['front','back','left','right','top','bottom']})
    nodes.append(dict(id=str(uuid.uuid5(uuid.NAMESPACE_URL,'slipstream/'+mid+'/'+p['name'])),name=p['name'],position=vec(p['center']),orientation=quat(np.array(p['basis'])),shape=shape))
   # A permanent intake label; saved player names appear in the native station pages.
   selected_texture=texture
   if base in ('ST_Harbour','ST_Overlook','ST_UpIntake'):
    at=Image.open(C/'Blocks/Slipstream'/texture);draw=ImageDraw.Draw(at);draw.rectangle([384,256,511,383],fill='#253b49');draw.text((386,256),'SUCTION',font=ImageFont.load_default(size=5),fill='#b4f7e9');selected_texture=mid+'_Atlas.png';at.save(C/'Blocks/Slipstream'/selected_texture)
    for n in nodes:
     if n['name']=='DestinationGlow':
      for face in n['shape']['textureLayout'].values():face['offset']={'x':384,'y':256}
   write(C/'Blocks/Slipstream'/f'{mid}.blockymodel',{'lod':'auto','nodes':nodes});write(R/'art/source/models'/f'{mid}.json',parts)
   # Native interaction selection is deliberately separate from physical collision.
   boxes=[{'Min':{'X':.02,'Y':0,'Z':.02},'Max':{'X':.08,'Y':.95,'Z':.08}}] if base!='ST_Workbench' else [{'Min':{'X':0,'Y':0,'Z':.06},'Max':{'X':1,'Y':.95,'Z':.94}}]
   write(S/'Item/Block/Hitboxes/Slipstream'/f'{mid}.json',{'Boxes':boxes})
   b=dict(Material='Solid',DrawType='Model',Opacity='Transparent',RequiresAlphaBlending=True,CustomModel=f'Blocks/Slipstream/{mid}.blockymodel',CustomModelTexture=[dict(Texture='Blocks/Slipstream/'+selected_texture,Weight=1)],VariantRotation='NESW',HitboxType=mid,InteractionHitboxType='ST_Select',PhysicalMaterialId='Metal',IgnoreSupportWhenPlaced=True,PlacementSettings=dict(BlockPreviewVisibility='AlwaysVisible',AllowRotationKey=True),Gathering=dict(Breaking=dict(GatherType='Benches')))
   station=base in ('ST_Harbour','ST_Overlook','ST_UpIntake');bench=base=='ST_Workbench'
   if station:b.update(AmbientSoundEventId='ST_SFX_Suction',InteractionHint='server.slipstream.board',Interactions={'Use':'ST_Board'},Light=dict(Color='#6BC',Radius=2),State=dict(Definitions=dict(Active=dict(AmbientSoundEventId='ST_SFX_SuctionActive',Light=dict(Color='#AD9',Radius=2)))))
   if bench:
    b.update(Bench={'Type':'Crafting','Id':'SlipstreamWorkbench','DescriptiveLabel':'server.slipstream.ST_Workbench.name','TierLevels':[{'CraftingTimeReductionModifier':0.0}],'Categories':[{'Id':'ST_'+(c or 'Teal'),'Name':'server.slipstream.colour.'+(c or 'Teal'),'Icon':'Icons/CraftingCategories/Slipstream/'+(c or 'Teal')+'.png'} for c in COLOURS],'CompletedSoundEventId':'ST_SFX_Workbench'},BlockEntity={'Components':{'BenchBlock':{}}},Support={'Down':[{'FaceType':'Full'}]})
   costs=(6,4) if bench else (4,2) if station else (3,2) if base=='ST_Splitter' else (2,1)
   req=[dict(Type='Crafting',Id='Workbench',Categories=['Workbench_Crafting'])] if bench else [dict(Type='Crafting',Id='SlipstreamWorkbench',Categories=['ST_'+(colour or 'Teal')])]
   item=dict(TranslationProperties=dict(Name='server.slipstream.'+mid+'.name',Description='server.slipstream.'+mid+'.description'),Icon=f'Icons/Items/{mid}.png',MaxStack=1 if bench else 16,Categories=['Furniture.Benches' if bench else 'Furniture.Furniture'],PlayerAnimationsId='Block',Interactions=dict(Primary='Block_Primary',Secondary='Block_Secondary'),BlockType=b,ItemSoundSetId='ST_WorkbenchHandling' if bench else 'ST_Handling',Recipe=dict(Input=[dict(ItemId='Ingredient_Bar_Iron',Quantity=costs[0]),dict(ItemId='Ingredient_Bar_Copper',Quantity=costs[1])],BenchRequirement=req,TimeSeconds=3 if bench else 2,OutputQuantity=1))
   if bench:item['Tags']={'Type':['Bench']}
   write(S/'Item/Items/Slipstream'/f'{mid}.json',item)
   previous=PAL['teal'];PAL['teal']=COLOURS[colour];preview=render(base,parts);(R/'art/previews').mkdir(parents=True,exist_ok=True);preview.save(R/'art/previews'/f'{mid}.png');(C/'Icons/Items').mkdir(parents=True,exist_ok=True);render(base,parts,512,label=False).resize((64,64),Image.Resampling.LANCZOS).save(C/'Icons/Items'/f'{mid}.png');PAL['teal']=previous
   catalog.append({'id':mid,'title':display,'footprint':'1x1x1','model':f'Common/Blocks/Slipstream/{mid}.blockymodel','preview':f'art/previews/{mid}.png','audio_family':'workbench_clamp' if bench else 'transit_pneumatic'})
   lang+=f'slipstream.{mid}.name = {display}\nslipstream.{mid}.description = '+('Craft tube shapes, splitters and suckers in six colours.' if bench else 'Adjacent ports snap into alignment. Suckers board automatically from below; F names the destination. Use a splitter for multiple stops.')+'\n'
 for c in COLOURS:
  import shutil
  (C/'Icons/CraftingCategories/Slipstream').mkdir(parents=True,exist_ok=True);shutil.copy2(C/'Icons/Items'/('ST_Straight'+('_'+c if c else '')+'.png'),C/'Icons/CraftingCategories/Slipstream'/((c or 'Teal')+'.png'))
 for c in COLOURS:lang+='slipstream.colour.'+(c or 'Teal')+' = '+(c or 'Teal')+'\n'
 write(R/'art/source/models/catalog.json',catalog)
 (S/'Languages/en-US').mkdir(parents=True,exist_ok=True);(S/'Languages/en-US/server.lang').write_text(lang)
 write(S/'Item/RootInteractions/Slipstream/ST_Board.json',{'Interactions':[{'Type':'ST_Board'}]})
 print('Generated',len(catalog),'original coloured tube/station/splitter and workbench assets.')
if __name__=='__main__':main()

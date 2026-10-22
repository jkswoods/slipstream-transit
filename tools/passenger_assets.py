#!/usr/bin/env python3
"""Original head-first avatar poses for the native player skeleton; no stock animation copied."""
from pathlib import Path
import json,math
R=Path(__file__).resolve().parents[1]/'src/main/resources'
def track(pos=None,angle=0):
 d={k:[] for k in ['position','orientation','shapeStretch','shapeVisible','shapeUvOffset']}
 if pos is not None:d['position']=[dict(time=0,delta=dict(zip('xyz',pos)),interpolationType='smooth')]
 a=math.radians(angle)/2;d['orientation']=[dict(time=0,delta=dict(x=math.sin(a),y=0,z=0,w=math.cos(a)),interpolationType='smooth')];return d
anims={}
for pitch in range(-90,91,10):
 name='Ride'+str(pitch);file='Characters/Slipstream/'+name+'.blockyanim';p=R/'Common'/file;p.parent.mkdir(parents=True,exist_ok=True)
 nodes={'Pelvis':track([0,8.2,0],90-pitch),'Belly':track(),'Chest':track(),'Head':track(angle=20),'L-Arm':track(angle=-15),'R-Arm':track(angle=-15),'L-Forearm':track(),'R-Forearm':track(),'L-Thigh':track(),'R-Thigh':track(),'L-Knee':track(),'R-Knee':track(),'L-Foot':track(),'R-Foot':track()}
 p.write_text(json.dumps(dict(duration=30,holdLastKeyframe=True,nodeAnimations=nodes),indent=2)+'\n')
 anims[name]=dict(ThirdPerson=file,ThirdPersonMoving=file,FirstPerson=file,Looping=True,BlendingDuration=.12,Speed=1)
p=R/'Server/Item/Animations/ST_Transit.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(dict(Animations=anims,Parent='Default'),indent=2)+'\n')
print('Generated19 original native avatar poses')

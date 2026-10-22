package dev.slipstream;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Rotation3f;
import org.joml.Vector3d;
/** Actual avatar animation and a camera attached to the actual travelling player. */
final class PassengerView {
 static void update(Store<EntityStore>s,Ride r,Vector3d direction) {
  var rot=Rotation3f.lookAt(direction);var transform=s.getComponent(r.ref,TransformComponent.getComponentType());
  transform.teleportRotation(new Rotation3f(0,rot.yaw(),0));
  var head=s.getComponent(r.ref,HeadRotation.getComponentType());if(head!=null)head.teleportRotation(rot);
  int pitch=Math.max(-90,Math.min(90,Math.round((float)Math.toDegrees(rot.pitch())/10)*10));
  if(pitch!=r.pose){r.pose=pitch;AnimationUtils.playAnimation(r.ref,AnimationSlot.Action,"ST_Transit","Ride"+pitch,true,s);}
  var c=new ServerCameraSettings();c.isFirstPerson=true;c.hideHeldItem=true;c.eyeOffset=false;c.allowPitchControls=false;c.displayReticle=false;c.distance=0;
  c.positionLerpSpeed=1;c.rotationLerpSpeed=.22f;c.rotationType=RotationType.Custom;
  c.rotation=new Direction(rot.yaw(),rot.pitch(),0);
  c.applyLookType=ApplyLookType.Rotation;c.lookMultiplier=new org.joml.Vector2f(0,0);
  c.positionOffset=new Position(0,.2775,0);


  r.player.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom,true,c));
 }
 static void reset(Store<EntityStore>s,Ref<EntityStore>ref,PlayerRef p) {
  if(ref.isValid())AnimationUtils.stopAnimation(ref,AnimationSlot.Action,true,s);
  p.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom,false,null));
 }
}

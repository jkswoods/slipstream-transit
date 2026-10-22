package dev.slipstream;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.FlyMode;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
/** Recovery is saved on the affected player, alongside the native scale itself. */
public final class TransitPassenger implements Component<EntityStore> {
 public static final BuilderCodec<TransitPassenger> CODEC=BuilderCodec.builder(TransitPassenger.class,TransitPassenger::new)
 .append(new KeyedCodec<>("InputSpeeds",Codec.FLOAT_ARRAY),(s,v)->{if(v.length!=0&&v.length!=9)throw new IllegalArgumentException("Invalid passenger input restore snapshot");for(float x:v)if(!Float.isFinite(x)||x<0)throw new IllegalArgumentException("Invalid passenger movement speed");s.inputSpeeds=v;},s->s.inputSpeeds).add()
 .append(new KeyedCodec<>("World",Codec.STRING),(s,v)->s.world=v,s->s.world).add()
 .append(new KeyedCodec<>("Scale",Codec.FLOAT),(s,v)->s.scale=v,s->s.scale).add()
 .append(new KeyedCodec<>("Fly",Codec.STRING),(s,v)->s.fly=v,s->s.fly).add()
 .append(new KeyedCodec<>("X",Codec.INTEGER),(s,v)->s.x=v,s->s.x).add()
 .append(new KeyedCodec<>("Y",Codec.INTEGER),(s,v)->s.y=v,s->s.y).add()
 .append(new KeyedCodec<>("Z",Codec.INTEGER),(s,v)->s.z=v,s->s.z).add().build();
 String world="",fly="Allowed";float scale=1;float[] inputSpeeds=new float[0];int x,y,z;
 public TransitPassenger(){}
 static TransitPassenger create(Store<EntityStore>s,Ref<EntityStore>ref,TransitGraph.Cell c,com.hypixel.hytale.protocol.MovementSettings movement){var p=new TransitPassenger();p.world=s.getExternalData().getWorld().getName();p.x=c.x();p.y=c.y();p.z=c.z();p.fly=movement.fly.name();p.inputSpeeds=PassengerControls.capture(movement);var sc=s.getComponent(ref,EntityScaleComponent.getComponentType());var model=s.getComponent(ref,ModelComponent.getComponentType());p.scale=sc!=null?sc.getScale():model==null?1:model.getModel().getScale();if(!Float.isFinite(p.scale)||p.scale<=0)throw new IllegalArgumentException("Invalid original player scale");return p;}
 static void scale(Store<EntityStore>s,Ref<EntityStore>ref,float size){var c=s.getComponent(ref,EntityScaleComponent.getComponentType());if(c==null)s.putComponent(ref,EntityScaleComponent.getComponentType(),new EntityScaleComponent(size));else c.setScale(size);}
 TransitGraph.Cell cell(){return new TransitGraph.Cell(x,y,z);}
 @Override public TransitPassenger clone(){var p=new TransitPassenger();p.world=world;p.fly=fly;p.inputSpeeds=inputSpeeds.clone();p.scale=scale;p.x=x;p.y=y;p.z=z;return p;}
}

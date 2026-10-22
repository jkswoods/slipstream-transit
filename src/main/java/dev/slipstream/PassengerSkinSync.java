package dev.slipstream;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
/** Native model changes must precede the skin update, including the affected player's own view. */
final class PassengerSkinSync extends EntityTickingSystem<EntityStore> {
 @Override public Query<EntityStore> getQuery(){return Query.and(PlayerRef.getComponentType(),PlayerSkinComponent.getComponentType());}
 @Override public SystemGroup<EntityStore> getGroup(){return EntityTrackerSystems.QUEUE_UPDATE_GROUP;}
 @Override public Set<Dependency<EntityStore>> getDependencies(){return Set.of(new SystemDependency<>(Order.AFTER,EntityTrackerSystems.EntityModel.class),new SystemDependency<>(Order.BEFORE,EntityTrackerSystems.EntitySkin.class));}
 @Override public void tick(float dt,int index,ArchetypeChunk<EntityStore>chunk,Store<EntityStore>s,CommandBuffer<EntityStore>b){
  if(chunk.getComponent(index,SlipstreamPlugin.passengerType)!=null)chunk.getComponent(index,PlayerSkinComponent.getComponentType()).setNetworkOutdated();
 }
}

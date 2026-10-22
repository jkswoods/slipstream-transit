package dev.slipstream;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.server.core.modules.entity.component.*;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
/** Keep the real PlayerSkin component; reapply it after native model/scale updates. */
final class PassengerAppearance {

 static void refresh(Store<EntityStore>s,Ref<EntityStore>ref){
  var skin=s.getComponent(ref,PlayerSkinComponent.getComponentType());var model=s.getComponent(ref,ModelComponent.getComponentType());
  if(skin==null||model==null||!"Player".equals(model.getModel().getModelAssetId()))return;
  s.putComponent(ref,ModelComponent.getComponentType(),new ModelComponent(model.getModel()));skin.setNetworkOutdated();
 }
 static void finish(Store<EntityStore>s,Ref<EntityStore>ref,float original){
  var model=s.getComponent(ref,ModelComponent.getComponentType());
  if(original==1f&&model!=null&&model.getModel().getScale()==1f&&"Player".equals(model.getModel().getModelAssetId())&&s.getComponent(ref,PlayerSkinComponent.getComponentType())!=null&&s.getComponent(ref,EntityScaleComponent.getComponentType())!=null)s.removeComponent(ref,EntityScaleComponent.getComponentType());
  refresh(s,ref);
 }
}

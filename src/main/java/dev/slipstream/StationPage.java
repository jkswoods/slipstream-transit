package dev.slipstream;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.protocol.packets.interface_.*;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.*;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
import static dev.slipstream.TransitGraph.*;
/** Short native station naming and departure pages; every action is revalidated on the world thread. */
final class StationPage extends InteractiveCustomUIPage<StationPage.Action> {
 public static final class Action {
  String action="",name="";
  static final BuilderCodec<Action> CODEC=BuilderCodec.builder(Action.class,Action::new)
   .append(new KeyedCodec<>("Action",Codec.STRING),(a,v)->a.action=v,a->a.action).add()
   .append(new KeyedCodec<>("@Name",Codec.STRING),(a,v)->a.name=v,a->a.name).add().build();
 }
 final Cell origin;final boolean naming;final List<Cell> destinations;boolean dismissed;
 StationPage(PlayerRef p,Cell at,boolean naming,List<Cell> destinations){super(p,CustomPageLifetime.CanDismiss,Action.CODEC);origin=at;this.naming=naming;this.destinations=List.copyOf(destinations);}
 static void open(Store<EntityStore>s,Ref<EntityStore>ref,PlayerRef p,Node n,boolean naming,List<List<Node>> paths){
  var d=SlipstreamPlugin.data(s);if(d.rides.containsKey(p.getUuid())||d.returns.containsKey(p.getUuid()))return;
  var player=s.getComponent(ref,Player.getComponentType());if(player==null)return;
  if(naming&&!n.owner().equals(p.getUuid())){SlipstreamPlugin.message(p,"Only this sucker's owner can rename it");return;}
  (naming?d.naming:d.choosing).add(p.getUuid());
  player.getPageManager().openCustomPage(ref,s,new StationPage(p,n.cell(),naming,paths.stream().map(path->path.getLast().cell()).toList()));
 }
 @Override public void build(Ref<EntityStore>ref,UICommandBuilder c,UIEventBuilder e,Store<EntityStore>s){
  var d=SlipstreamPlugin.data(s);var n=d.graph.nodes.get(origin);c.append("Pages/Slipstream/Station.ui");
  c.set("#StationTitle.Text",naming?"Name this sucker":"Where to?");c.set("#Hint.Text",naming?"Give this stop a name. Press F on the sucker to rename it later.":"Choose a destination. The tube takes care of the journey.");
  c.set("#Naming.Visible",naming);c.set("#Routes.Visible",!naming);
  if(naming){c.set("#Name.Value",n==null?"":d.name(n));e.addEventBinding(CustomUIEventBindingType.Activating,"#Save",EventData.of("Action","Save").append("@Name","#Name.Value"),false);}
  else for(int i=0;i<destinations.size();i++){String sel="#Routes["+i+"]";c.append("#Routes","Pages/Slipstream/Destination.ui");var end=d.graph.nodes.get(destinations.get(i));c.set(sel+".Text",end==null?"Unavailable":d.name(end));e.addEventBinding(CustomUIEventBindingType.Activating,sel,EventData.of("Action",Integer.toString(i)),false);}
  e.addEventBinding(CustomUIEventBindingType.Activating,"#Cancel",EventData.of("Action","Cancel"),false);
 }
 @Override public void handleDataEvent(Ref<EntityStore>ref,Store<EntityStore>s,Action action){
  s.getExternalData().getWorld().execute(()->{
   var d=SlipstreamPlugin.data(s);if(dismissed||!ref.isValid()||ref.getStore()!=s)return;
   var player=s.getComponent(ref,Player.getComponentType());if(player==null||player.getPageManager().getCustomPage()!=this)return;
   var n=d.graph.nodes.get(origin);var pos=s.getComponent(ref,TransformComponent.getComponentType()).getPosition();
   if(n==null||!NativeBlocks.present(s.getExternalData().getWorld(),n)||pos.distanceSquared(Ride.centre(n))>64){close();return;}
   if(action.action.equals("Cancel")){close();return;}
   if(naming){
    if(!n.owner().equals(playerRef.getUuid())){close();return;}
    String name=action.name.strip().replaceAll("[\\p{Cntrl}]","");
    if(name.isBlank()||name.length()>32){SlipstreamPlugin.message(playerRef,"Use a name of 1–32 characters");sendUpdate();return;}
    d.names.put(origin,name);SlipstreamPlugin.message(playerRef,"Destination named: "+name);close();
   }else{
    int i;try{i=Integer.parseInt(action.action);}catch(NumberFormatException ex){return;}if(i<0||i>=destinations.size())return;
    if(!SlipstreamPlugin.underMouth(pos,n,1.1)){close();return;}
    Cell chosen=destinations.get(i);close();SlipstreamPlugin.instance.board(s,ref,playerRef,origin,chosen);
   }
  });
 }
 @Override public void onDismiss(Ref<EntityStore>ref,Store<EntityStore>s){dismissed=true;var d=SlipstreamPlugin.data(s);d.choosing.remove(playerRef.getUuid());d.naming.remove(playerRef.getUuid());d.entryBlocked.computeIfAbsent(playerRef.getUuid(),k->new HashSet<>()).add(origin);}
}

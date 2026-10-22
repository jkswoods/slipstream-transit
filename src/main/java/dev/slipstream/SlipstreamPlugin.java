package dev.slipstream;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.FlyMode;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.event.events.ecs.*;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.*;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.*;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import java.util.*;
import java.util.concurrent.*;
import static dev.slipstream.TransitGraph.*;

/** Compact passenger routes with server-authoritative movement and state-driven audio. */
public final class SlipstreamPlugin extends JavaPlugin {
 static SlipstreamPlugin instance;
 static ResourceType<EntityStore,TransitWorld> worldType;
 static ComponentType<EntityStore,TransitPassenger> passengerType;
 public SlipstreamPlugin(JavaPluginInit init) {super(init);}
 @Override protected void setup() {
  instance=this;worldType=getEntityStoreRegistry().registerResource(TransitWorld.class,"SlipstreamCompactRoutesV2",TransitWorld.CODEC);
  passengerType=getEntityStoreRegistry().registerComponent(TransitPassenger.class,"SlipstreamPassengerV1",TransitPassenger.CODEC);
  getCodecRegistry(Interaction.CODEC).register("ST_Board",BoardInteraction.class,BoardInteraction.CODEC);
  getEntityStoreRegistry().registerSystem(new Tick());getEntityStoreRegistry().registerSystem(new PassengerSkinSync());getEntityStoreRegistry().registerSystem(new Placement());getEntityStoreRegistry().registerSystem(new Breaking());
  getCommandRegistry().registerCommand(new BoardCommand());getCommandRegistry().registerCommand(new Abort());
  getEventRegistry().registerGlobal(PlayerDisconnectEvent.class,e->{var p=e.getPlayerRef();var ref=p.getReference();if(ref!=null&&ref.isValid()) {var w=ref.getStore().getExternalData().getWorld();w.execute(()->{var s=w.getEntityStore().getStore();var d=data(s);var ride=d.rides.remove(p.getUuid());if(ride!=null) {if(ref.isValid()) {push(s,ref,new Vector3d());PassengerView.reset(s,ref,p);suspendFlight(s,ref,p,d);}NativeBlocks.state(w,ride.route.getFirst(),false);}});} });
 }
 @Override protected void start() {for(String id:NativeBlocks.ITEMS)if(BlockType.getAssetMap().getAsset(id)==null)throw new IllegalStateException("Missing Slipstream asset "+id);}
 static TransitWorld data(Store<EntityStore> s) {return s.getResource(worldType);}
 static void message(PlayerRef p,String text) {p.sendMessage(Message.raw(text));}
 static void push(Store<EntityStore> s,Ref<EntityStore> ref,Vector3d v) {
  var velocity=s.getComponent(ref,Velocity.getComponentType());if(velocity==null)return;
  var c=new VelocityConfig();c.setGroundResistance(0);c.setAirResistance(0);c.setGroundResistanceMax(0);c.setAirResistanceMax(0);velocity.addInstruction(v,c,ChangeVelocityType.Set);
 }
 static void cue(Store<EntityStore>s,PlayerRef p,String name,Vector3d at) {String id="ST_SFX_"+name.substring(0,1).toUpperCase(java.util.Locale.ROOT)+name.substring(1);var m=SoundEvent.getAssetMap();if(m.getAsset(id)!=null)SoundUtil.playSoundEvent3dToPlayer(p.getReference(),m.getIndex(id),SoundCategory.SFX,at,s);}
 void nameStation(Store<EntityStore>s,Ref<EntityStore>ref,PlayerRef p,Cell at){var w=s.getExternalData().getWorld();var n=data(s).graph.nodes.get(NativeBlocks.origin(w,at));if(n!=null&&station(n.item()))StationPage.open(s,ref,p,n,true,List.of());}
 void board(Store<EntityStore>s,Ref<EntityStore>ref,PlayerRef player,Cell target){board(s,ref,player,target,null);}
 void board(Store<EntityStore> s,Ref<EntityStore> ref,PlayerRef player,Cell target,Cell destination) {
  var d=data(s);var w=s.getExternalData().getWorld();
  try {
   if(d.rides.containsKey(player.getUuid()))return; // Repeated station input cannot cancel a committed ride.
   if(!d.rides.isEmpty()||d.returns.containsKey(player.getUuid()))throw new IllegalArgumentException("Wait until the current passenger has arrived or recovery finishes");
   target=NativeBlocks.origin(w,target);var n=d.graph.nodes.get(target);if(n==null)throw new IllegalArgumentException("This station has not finished placement");
   var t=s.getComponent(ref,TransformComponent.getComponentType());var pos=t.getPosition();
   if(!underMouth(pos,n,1.1))throw new IllegalArgumentException("Stand beneath an open overhead intake");
   var paths=d.graph.destinations(target,n.owner());
   paths=paths.stream().filter(path->path.stream().allMatch(part->NativeBlocks.present(w,part))).toList();
   if(paths.isEmpty())throw new IllegalArgumentException("No connected destination. Match the tube ports and finish with another sucker.");
   if(destination==null&&paths.size()>1){StationPage.open(s,ref,player,n,false,paths);return;}
   final Cell chosen=destination;
   var route=destination==null?paths.getFirst():paths.stream().filter(path->path.getLast().cell().equals(chosen)).findFirst().orElseThrow(()->new IllegalArgumentException("Destination is no longer connected"));
   if(!safeLanding(w,d,n)||!safeLanding(w,d,route.getLast()))throw new IllegalArgumentException("Keep a clear full-size landing and floor two blocks below both suckers");
   var movement=s.getComponent(ref,com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent.getComponentType());
   if(movement!=null&&movement.getMovementStates().flying)throw new IllegalArgumentException("Land on the station before boarding");
   var manager=s.getComponent(ref,MovementManager.getComponentType());if(manager==null)throw new IllegalArgumentException("Native movement adapter is unavailable");
   for(var part:route)if(!NativeBlocks.present(w,part))throw new IllegalArgumentException("Load and check every route part before boarding");
   var ride=new Ride(ref,player,route,pos);
   for(int i=1;i<ride.path.size();i++)if(!clearTravel(w,d,ride.path.get(i),ride.direction(ride.lengths.get(i)),ride))throw new IllegalArgumentException("The passenger corridor is blocked or unloaded");
   var passenger=TransitPassenger.create(s,ref,target,manager.getSettings());s.putComponent(ref,passengerType,passenger);PassengerAppearance.refresh(s,ref);TransitPassenger.scale(s,ref,passenger.scale);
   d.entryBlocked.computeIfAbsent(player.getUuid(),k->new HashSet<>()).add(target);
   d.returns.put(player.getUuid(),target);d.flightRestore.put(player.getUuid(),manager.getSettings().fly);d.rides.put(player.getUuid(),ride);
   PassengerControls.lock(manager.getSettings());manager.getSettings().fly=FlyMode.Forced;manager.update(player.getPacketHandler());

   NativeBlocks.state(w,n,true);cue(s,player,"intake",pos);message(player,"Departing for "+d.name(route.getLast())+". Shrinking intake — head-first transit. Camera follows travel; /stabort returns safely.");

  }catch(IllegalArgumentException ex) {message(player,ex.getMessage());}
 }
 static void suspendFlight(Store<EntityStore>s,Ref<EntityStore>ref,PlayerRef p,TransitWorld d) {
  if(!d.flightRestore.containsKey(p.getUuid()))return;var m=s.getComponent(ref,MovementManager.getComponentType());if(m!=null&&m.getSettings().fly!=FlyMode.Disabled) {m.getSettings().fly=FlyMode.Disabled;m.update(p.getPacketHandler());}
 }
 static void restoreFlight(Store<EntityStore>s,Ref<EntityStore>ref,PlayerRef p,TransitWorld d) {
  var original=d.flightRestore.get(p.getUuid());if(original==null)return;var m=s.getComponent(ref,MovementManager.getComponentType());if(m!=null) {m.getSettings().fly=original;m.update(p.getPacketHandler());d.flightRestore.remove(p.getUuid());var states=s.getComponent(ref,com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent.getComponentType());if(states!=null)com.hypixel.hytale.server.core.entity.entities.Player.applyMovementStates(ref,new com.hypixel.hytale.protocol.SavedMovementStates(false),states.getMovementStates(),s);}
 }
 // Test native collision shapes, not whole occupied voxels. Guided bases sit .2775 below the bore centre.
 static boolean clearBox(World w,TransitWorld d,Vector3d p,double radius,double height,Ride ride){
  var body=new com.hypixel.hytale.math.shape.Box(p.x-radius,p.y+.015,p.z-radius,p.x+radius,p.y+height-.015,p.z+radius);
  for(int x=(int)Math.floor(body.min.x);x<=Math.floor(body.max.x);x++)for(int y=(int)Math.floor(body.min.y);y<=Math.floor(body.max.y);y++)for(int z=(int)Math.floor(body.min.z);z<=Math.floor(body.max.z);z++){
   Cell c=new Cell(x,y,z);if(NativeBlocks.empty(w,c))continue;var section=NativeBlocks.section(w,c);if(section==null)return false;
   Cell origin=NativeBlocks.origin(w,c);var node=d.graph.nodes.get(origin);if(node!=null&&ride.uses(origin)&&NativeBlocks.present(w,node))continue;
   var block=NativeBlocks.block(w,origin);if(block==null)return false;var boxes=com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes.getAssetMap().getAsset(block.getHitboxTypeIndex());if(boxes==null)return false;
   var os=NativeBlocks.section(w,origin);if(os==null)return false;int rotation=os.getRotationIndex(origin.x()&31,origin.y()&31,origin.z()&31);
   for(var box:boxes.get(rotation).getDetailBoxes())if(body.isIntersecting(new com.hypixel.hytale.math.shape.Box(box).offset(origin.x(),origin.y(),origin.z())))return false;
  }return true;
 }
 static boolean clearBody(World w,TransitWorld d,Vector3d p,Ride ride){return clearBox(w,d,p,.32,1.85,ride);}
 static boolean clearTravel(World w,TransitWorld d,Vector3d p,Vector3d direction,Ride ride){return clearBox(w,d,p,.11,.555,ride);}
 void end(Store<EntityStore> s,TransitWorld d,Ride ride,boolean arrived,String reason) {
  d.rides.remove(ride.player.getUuid());if(ride.ref.isValid()) {push(s,ride.ref,new Vector3d());PassengerView.reset(s,ride.ref,ride.player);suspendFlight(s,ride.ref,ride.player,d);}
  NativeBlocks.state(s.getExternalData().getWorld(),ride.route.getFirst(),false);
  Cell target=(arrived?ride.route.getLast():ride.route.getFirst()).cell();d.returns.put(ride.player.getUuid(),target);
  message(ride.player,reason);
  if(ride.ref.isValid()){var saved=s.getComponent(ride.ref,passengerType);if(saved!=null){saved.x=target.x();saved.y=target.y();saved.z=target.z();}}
  d.entryBlocked.computeIfAbsent(ride.player.getUuid(),k->new HashSet<>()).add(target);
  if(arrived)cue(s,ride.player,"arrival",Ride.feet(ride.route.getLast()));
 }
 void recover(Store<EntityStore>s,TransitWorld d,PlayerRef p) {
  UUID id=p.getUuid();if(d.returning.contains(id)||d.growing.containsKey(id))return;Cell cell=d.returns.get(id);var node=d.graph.nodes.get(cell);var w=s.getExternalData().getWorld();
  if(node==null||!station(node.item())||!NativeBlocks.present(w,node))return;
  var ref=p.getReference();if(ref==null||!ref.isValid()||ref.getStore()!=s)return;
  var safe=Ride.feet(node);var t=s.getComponent(ref,TransformComponent.getComponentType());if(t==null)return;
  // The station's3x3 deck supplies the return floor. All body cells must belong to it or be empty.
  var check=new Ride(ref,p,List.of(node),safe);if(!clearBody(w,d,safe,check))return;
  d.returning.add(id);w.execute(()->{
   if(!ref.isValid()||ref.getStore()!=s) {d.returning.remove(id);return;}
   push(s,ref,new Vector3d());PassengerView.reset(s,ref,p);suspendFlight(s,ref,p,d);var teleport=Teleport.createExact(safe,t.getRotation(),p.getHeadRotation());var done=new CompletableFuture<Void>();teleport.setOnComplete(done);
   s.putComponent(ref,Teleport.getComponentType(),teleport);
   done.whenComplete((v,error)->w.execute(()->{d.returning.remove(id);if(error==null&&ref.isValid()&&ref.getStore()==s) {var saved=s.getComponent(ref,passengerType);if(saved!=null){var scale=s.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent.getComponentType());d.growing.put(id,new TransitWorld.Growth(scale==null?saved.scale:scale.getScale(),0));}
    PassengerView.reset(s,ref,p);d.entryBlocked.computeIfAbsent(id,k->new HashSet<>()).add(cell);}}));
  });
 }
 final class Tick extends TickingSystem<EntityStore> {
  @Override public void tick(float dt,int index,Store<EntityStore>s) {
   var d=data(s);var w=s.getExternalData().getWorld();int count=Math.min(64,d.confirmed.size());for(int i=0;i<count;i++)d.confirmed.remove().run();
   for(Ride r:List.copyOf(d.rides.values())) {
    if(!r.ref.isValid()||r.ref.getStore()!=s) {d.rides.remove(r.player.getUuid());continue;}
    r.elapsed+=dt;var movement=s.getComponent(r.ref,com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent.getComponentType());if(movement!=null&&movement.getMovementStates().crouching) {end(s,d,r,false,"Travel cancelled by crouching; returning safely.");continue;}var pos=s.getComponent(r.ref,TransformComponent.getComponentType()).getPosition();
    boolean intact=r.route.stream().allMatch(n->NativeBlocks.present(w,n));
    var passenger=s.getComponent(r.ref,passengerType);if(passenger==null){end(s,d,r,false,"Passenger recovery record missing");continue;}
    if(r.shrink<.85f){r.shrink=Math.min(.85f,r.shrink+dt);float t=r.shrink/.85f;float ease=t*t*(3-2*t);TransitPassenger.scale(s,r.ref,passenger.scale+(Math.min(.3f,passenger.scale*.3f)-passenger.scale)*ease);push(s,r.ref,new Vector3d(r.start).sub(pos).mul(12));continue;}
    if(!intact||r.elapsed>30||!clearTravel(w,d,pos,r.direction(r.progress),r)) {end(s,d,r,false,"Route unavailable; returning to the boarding station.");continue;}
    r.progress=r.nearest(pos);
    if(r.progress>=r.total-.12&&pos.distance(r.path.getLast())<.18) {end(s,d,r,true,"Arrived safely.");continue;}
    double speed=Math.min(6,1.5+r.elapsed*12);double next=Math.min(r.total,r.progress+.18);
    var target=r.at(next);if(pos.distance(target)>2.5) {end(s,d,r,false,"Left the guided corridor; returning safely.");continue;}
    var direction=r.direction(r.progress+.03);var v=new Vector3d(target).sub(pos).mul(20);
    if(next<r.total-.25)v.fma(speed,direction);if(v.length()>12)v.mul(12/v.length());
    PassengerView.update(s,r,direction);push(s,r.ref,v);


   }
   for(var p:w.getPlayerRefs()){
    UUID id=p.getUuid();var growth=d.growing.get(id);if(growth==null)continue;var ref=p.getReference();if(ref==null||!ref.isValid()||ref.getStore()!=s)continue;
    var saved=s.getComponent(ref,passengerType);if(saved==null){d.growing.remove(id);continue;}
    var node=d.graph.nodes.get(d.returns.get(id));var pos=s.getComponent(ref,TransformComponent.getComponentType()).getPosition();
    if(node==null||!safeLanding(w,d,node)||pos.distanceSquared(Ride.feet(node))>.5){d.growing.remove(id);continue;}
    float time=Math.min(.7f,growth.time()+dt);d.growing.put(id,new TransitWorld.Growth(growth.from(),time));float t=time/.7f;float ease=t*t*(3-2*t);TransitPassenger.scale(s,ref,growth.from()+(saved.scale-growth.from())*ease);
    if(time>=.7f){TransitPassenger.scale(s,ref,saved.scale);d.growing.remove(id);d.returning.add(id);w.execute(()->{if(ref.isValid()&&ref.getStore()==s){PassengerControls.apply(s.getComponent(ref,MovementManager.getComponentType()).getSettings(),saved.inputSpeeds);PassengerAppearance.finish(s,ref,saved.scale);s.removeComponent(ref,passengerType);d.returns.remove(id);restoreFlight(s,ref,p,d);message(p,"Full size restored. Step clear before boarding again.");}d.returning.remove(id);});}
   }
   scanEntries(s,d);
   for(var p:w.getPlayerRefs())if(d.returns.containsKey(p.getUuid())&&!d.rides.containsKey(p.getUuid()))recover(s,d,p);
   for(var n:d.graph.nodes.values())if(station(n.item())&&d.rides.values().stream().noneMatch(r->r.route.getFirst().cell().equals(n.cell())))NativeBlocks.state(w,n,false);
  }
 }
 static boolean underMouth(Vector3d p,Node n,double radius){double dx=p.x-n.cell().x()-.5,dz=p.z-n.cell().z()-.5,below=n.cell().y()-p.y;return dx*dx+dz*dz<radius*radius&&below>1.0&&below<3.2;}
 static boolean safeLanding(World w,TransitWorld d,Node n){var at=Ride.feet(n);var floor=new Cell((int)Math.floor(at.x),(int)Math.floor(at.y-.05),(int)Math.floor(at.z));return NativeBlocks.block(w,floor)!=null&&!NativeBlocks.empty(w,floor)&&clearBody(w,d,at,new Ride(null,null,List.of(n),at));}
 void scanEntries(Store<EntityStore>s,TransitWorld d){var w=s.getExternalData().getWorld();for(var player:w.getPlayerRefs()){
  var ref=player.getReference();if(ref==null||!ref.isValid()||ref.getStore()!=s)continue;var transform=s.getComponent(ref,TransformComponent.getComponentType());if(transform==null)continue;var pos=transform.getPosition();var id=player.getUuid();var blocked=d.entryBlocked.computeIfAbsent(id,k->new HashSet<>());
  var saved=s.getComponent(ref,passengerType);if(saved!=null&&!d.rides.containsKey(id)&&saved.world.equals(w.getName())){d.returns.putIfAbsent(id,saved.cell());d.flightRestore.putIfAbsent(id,FlyMode.valueOf(saved.fly));}
  if(d.entrySeen.add(id)){var scale=s.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent.getComponentType());if(saved==null&&scale!=null&&scale.getScale()==1f&&d.graph.nodes.values().stream().anyMatch(n->n.owner().equals(id)))w.execute(()->{if(ref.isValid()&&!d.rides.containsKey(id)&&s.getComponent(ref,passengerType)==null){PassengerAppearance.finish(s,ref,1f);}});for(var n:d.graph.nodes.values())if(station(n.item())&&underMouth(pos,n,1.5))blocked.add(n.cell());}
  blocked.removeIf(c->{var n=d.graph.nodes.get(c);return n==null||!underMouth(pos,n,1.5);});
  if(s.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager().getCustomPage()!=null||d.rides.containsKey(id)||d.returns.containsKey(id)||d.pendingAuto.contains(id)||d.choosing.contains(id)||d.naming.contains(id))continue;
  for(var n:d.graph.nodes.values())if(station(n.item())&&!blocked.contains(n.cell())&&underMouth(pos,n,.85)&&NativeBlocks.present(w,n)){
   blocked.add(n.cell());d.pendingAuto.add(id);w.execute(()->{try{if(ref.isValid()&&underMouth(s.getComponent(ref,TransformComponent.getComponentType()).getPosition(),n,.95))board(s,ref,player,n.cell());}finally{d.pendingAuto.remove(id);}});break;
  }
 }}
 final class Placement extends EntityEventSystem<EntityStore,PlaceBlockEvent> {
  Placement() {super(PlaceBlockEvent.class);}
  @Override public Query<EntityStore> getQuery() {return PlayerRef.getComponentType();}
  @Override public void handle(int i,ArchetypeChunk<EntityStore>chunk,Store<EntityStore>s,CommandBuffer<EntityStore>b,PlaceBlockEvent e) {
   if(e.isCancelled()||e.getItemInHand()==null)return;var d=data(s);var w=s.getExternalData().getWorld();Cell p=NativeBlocks.cell(e.getTargetBlock());String id=e.getItemInHand().getItemId();var player=chunk.getComponent(i,PlayerRef.getComponentType());
   if(!NativeBlocks.ITEMS.contains(id)) {boolean[] overlap={d.graph.reserved(p)!=null};String blockId=e.getItemInHand().getItem().getBlockId();var type=blockId==null?null:BlockType.getAssetMap().getAsset(blockId);
    if(type!=null&&e.getRotation()!=null) {var hitbox=com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex());if(hitbox!=null)com.hypixel.hytale.server.core.util.FillerBlockUtil.forEachFillerBlock(hitbox.get(e.getRotation().index()),(x,y,z)->{if(d.graph.reserved(new Cell(p.x()+x,p.y()+y,p.z()+z))!=null)overlap[0]=true;});}
    if(overlap[0]) {e.setCancelled(true);message(player,"Slipstream reserves this passenger corridor");}return;}
   if(e.getRotation()==null||d.confirmed.size()>=64||!NativeBlocks.clearAssembly(w,p)) {e.setCancelled(true);message(player,"Clear and load the placement block");return;}
   e.setRotation(NativeBlocks.snap(d.graph,p,id,player.getUuid(),e.getRotation()));
   try {var n=new Node(p,id,e.getRotation().index(),player.getUuid(),NativeBlocks.ports(id,e.getRotation().index()));d.graph.add(n);d.confirmed.add(()->{if(!NativeBlocks.present(w,n))d.graph.nodes.remove(p);else if(station(id))w.execute(()->{var ref=player.getReference();if(ref!=null&&ref.isValid())nameStation(s,ref,player,p);});});}
   catch(IllegalArgumentException ex) {e.setCancelled(true);message(player,ex.getMessage());}
  }
 }
 final class Breaking extends EntityEventSystem<EntityStore,BreakBlockEvent> {
  Breaking() {super(BreakBlockEvent.class);}
  @Override public Query<EntityStore> getQuery() {return PlayerRef.getComponentType();}
  @Override public void handle(int i,ArchetypeChunk<EntityStore>chunk,Store<EntityStore>s,CommandBuffer<EntityStore>b,BreakBlockEvent e) {
   if(e.isCancelled())return;var d=data(s);var w=s.getExternalData().getWorld();Cell p=NativeBlocks.origin(w,NativeBlocks.cell(e.getTargetBlock()));var n=d.graph.nodes.get(p);if(n==null)return;
   var player=chunk.getComponent(i,PlayerRef.getComponentType());if(!n.owner().equals(player.getUuid())||d.rides.values().stream().anyMatch(r->r.uses(p))||d.returns.containsValue(p)||d.confirmed.size()>=64) {e.setCancelled(true);message(player,"Only the owner may dismantle an idle route; keep recovery stations intact");return;}
   d.confirmed.add(()->{if(!NativeBlocks.present(w,n)){d.graph.nodes.remove(p);d.names.remove(p);}});
  }
 }
 final class BoardCommand extends AbstractPlayerCommand {
  BoardCommand() {super("stboard","Deliberately board from a nearby Slipstream station landing");requireNoPermission();}
  @Override protected void execute(CommandContext c,Store<EntityStore>s,Ref<EntityStore>r,PlayerRef p,World w) {
   var pos=s.getComponent(r,TransformComponent.getComponentType()).getPosition();var nearest=data(s).graph.nodes.values().stream().filter(n->station(n.item())).min(Comparator.comparingDouble(n->pos.distanceSquared(Ride.feet(n))));
   if(nearest.isEmpty())message(p,"No station is placed");else board(s,r,p,nearest.get().cell());
  }
 }

 final class Abort extends AbstractPlayerCommand {
  Abort() {super("stabort","Return safely from Slipstream passenger travel");requireNoPermission();}
  @Override protected void execute(CommandContext c,Store<EntityStore>s,Ref<EntityStore>r,PlayerRef p,World w) {var d=data(s);var ride=d.rides.get(p.getUuid());if(ride!=null)end(s,d,ride,false,"Travel aborted; returning to boarding station.");}
 }




}

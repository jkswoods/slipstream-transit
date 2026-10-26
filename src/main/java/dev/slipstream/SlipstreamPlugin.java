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

/** Bounded passenger routes with per-rider traffic, recovery and scoped chunk retention. */
public final class SlipstreamPlugin extends JavaPlugin {
 static SlipstreamPlugin instance;
 static final Map<World,RouteChunks> chunkPools=new ConcurrentHashMap<>();
 static ResourceType<EntityStore,TransitWorld> worldType;
 static ComponentType<EntityStore,TransitPassenger> passengerType;
 public SlipstreamPlugin(JavaPluginInit init) {super(init);}
 @Override protected void setup() {
  instance=this;worldType=getEntityStoreRegistry().registerResource(TransitWorld.class,"SlipstreamCompactRoutesV2",TransitWorld.CODEC);
  passengerType=getEntityStoreRegistry().registerComponent(TransitPassenger.class,"SlipstreamPassengerV1",TransitPassenger.CODEC);
  getCodecRegistry(Interaction.CODEC).register("ST_Board",BoardInteraction.class,BoardInteraction.CODEC);
  getEntityStoreRegistry().registerSystem(new Tick());getEntityStoreRegistry().registerSystem(new PassengerSkinSync());getEntityStoreRegistry().registerSystem(new Placement());getEntityStoreRegistry().registerSystem(new Breaking());
  getCommandRegistry().registerCommand(new BoardCommand());getCommandRegistry().registerCommand(new Abort());
  getEventRegistry().registerGlobal(PlayerDisconnectEvent.class,e->{var p=e.getPlayerRef();var ref=p.getReference();if(ref!=null&&ref.isValid()){var w=ref.getStore().getExternalData().getWorld();w.execute(()->{var store=w.getEntityStore().getStore();var d=data(store);cancelDeparture(d,p.getUuid());var ride=d.rides.remove(p.getUuid());var parked=d.parked.remove(p.getUuid());if(parked!=null&&parked.chunks!=null)parked.chunks.close();d.traffic.release(p.getUuid());closeRecovery(d,p.getUuid());d.growing.remove(p.getUuid());d.returning.remove(p.getUuid());d.entrySeen.remove(p.getUuid());d.entryBlocked.remove(p.getUuid());d.choosing.remove(p.getUuid());d.naming.remove(p.getUuid());if(ride!=null){if(ride.chunks!=null)ride.chunks.close();if(ref.isValid()){push(store,ref,new Vector3d());PassengerView.reset(store,ref,p);suspendFlight(store,ref,p,d);}refreshStation(w,d,ride.route.getFirst());}});}});
  getEventRegistry().registerGlobal(com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent.class,e->{var pool=chunkPools.remove(e.getWorld());if(pool!=null)pool.close();});
 }
 @Override protected void start() {for(String id:NativeBlocks.ITEMS)if(BlockType.getAssetMap().getAsset(id)==null)throw new IllegalStateException("Missing Slipstream asset "+id);}
 @Override protected void shutdown(){chunkPools.values().forEach(RouteChunks::close);chunkPools.clear();}
 static TransitWorld data(Store<EntityStore> s) {var d=s.getResource(worldType);if(d.chunks==null)d.chunks=chunkPools.computeIfAbsent(s.getExternalData().getWorld(),RouteChunks::new);return d;}
 static void refreshStation(World w,TransitWorld d,Node node){NativeBlocks.state(w,node,d.rides.values().stream().anyMatch(r->r.route.getFirst().cell().equals(node.cell())));}
 static void closeRecovery(TransitWorld d,UUID id){var lease=d.recoveryChunks.remove(id);if(lease!=null)lease.close();d.recoveryLoadTime.remove(id);}
 static void cancelDeparture(TransitWorld d,UUID id){var q=d.departures.remove(id);if(q!=null){if(q.chunks!=null)q.chunks.close();d.traffic.release(id);}}
 static void message(PlayerRef p,String text) {p.sendMessage(Message.raw(text));}
 static void push(Store<EntityStore> s,Ref<EntityStore> ref,Vector3d v) {
  var velocity=s.getComponent(ref,Velocity.getComponentType());if(velocity==null)return;
  var c=new VelocityConfig();c.setGroundResistance(0);c.setAirResistance(0);c.setGroundResistanceMax(0);c.setAirResistanceMax(0);velocity.addInstruction(v,c,ChangeVelocityType.Set);
 }
 static void cue(Store<EntityStore>s,PlayerRef p,String name,Vector3d at) {String id="ST_SFX_"+name.substring(0,1).toUpperCase(java.util.Locale.ROOT)+name.substring(1);var m=SoundEvent.getAssetMap();if(m.getAsset(id)!=null)SoundUtil.playSoundEvent3dToPlayer(p.getReference(),m.getIndex(id),SoundCategory.SFX,at,s);}
 void nameStation(Store<EntityStore>s,Ref<EntityStore>ref,PlayerRef p,Cell at){var w=s.getExternalData().getWorld();var n=data(s).graph.nodes.get(NativeBlocks.origin(w,at));if(n!=null&&station(n.item()))StationPage.open(s,ref,p,n,true,List.of());}
 void board(Store<EntityStore>s,Ref<EntityStore>ref,PlayerRef player,Cell target){board(s,ref,player,target,null);}
 void board(Store<EntityStore> s,Ref<EntityStore> ref,PlayerRef player,Cell target,Cell destination) {
  var d=data(s);var w=s.getExternalData().getWorld();UUID id=player.getUuid();
  try {
   if(d.rides.containsKey(id)||d.departures.containsKey(id))return;
   if(d.returns.containsKey(id))throw new IllegalArgumentException("Wait for your safe recovery to finish");
   target=NativeBlocks.origin(w,target);var n=d.graph.nodes.get(target);if(n==null||!station(n.item())||!NativeBlocks.present(w,n))throw new IllegalArgumentException("This intake is unavailable");
   var pos=s.getComponent(ref,TransformComponent.getComponentType()).getPosition();
   if(!underMouth(pos,n,1.1))throw new IllegalArgumentException("Stand beneath an open overhead intake");
   var paths=d.graph.destinations(target,n.owner());
   if(paths.isEmpty())throw new IllegalArgumentException("No connected destination within the journey limit. Match tube ports and finish with another sucker.");
   if(destination==null&&paths.size()>1){StationPage.open(s,ref,player,n,false,paths);return;}
   final Cell chosen=destination;var route=destination==null?paths.getFirst():paths.stream().filter(path->path.getLast().cell().equals(chosen)).findFirst().orElseThrow(()->new IllegalArgumentException("Destination is no longer connected"));
   if(d.returns.size()>=128)throw new IllegalArgumentException("Safe-return record capacity is full. Recover disconnected riders before starting more journeys.");
   if(d.departures.size()>=TransitLimits.WAITERS)throw new IllegalArgumentException("The station waiting list is full. Step clear and try again.");
   if(RouteChunks.sections(route).size()>TransitLimits.ROUTE_SECTIONS)throw new IllegalArgumentException("Journey exceeds the bounded chunk-section budget");
   d.departures.put(id,new TransitWorld.Departure(ref,player,route,d.clock));
   message(player,"Waiting for "+d.name(route.getLast())+". Keep beneath the intake; step away, crouch or /stabort to cancel.");cue(s,player,"waiting",pos);
  }catch(IllegalArgumentException ex){message(player,ex.getMessage());}
 }
 void departures(Store<EntityStore>s,TransitWorld d){var w=s.getExternalData().getWorld();int validationBudget=256;
  for(var q:List.copyOf(d.departures.values())){UUID id=q.player.getUuid();
   if(!q.ref.isValid()||q.ref.getStore()!=s){cancelDeparture(d,id);continue;}
   var pos=s.getComponent(q.ref,TransformComponent.getComponentType()).getPosition();var movement=s.getComponent(q.ref,com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent.getComponentType());
   if(!underMouth(pos,q.route.getFirst(),1.1)||(movement!=null&&movement.getMovementStates().crouching)||d.clock-q.queued>TransitLimits.WAIT_SECONDS){cancelDeparture(d,id);message(q.player,"Departure cancelled. Step clear to board again.");continue;}
   try{
    if(q.route.stream().anyMatch(n->!n.equals(d.graph.nodes.get(n.cell()))))throw new IllegalArgumentException("Route changed while waiting. Choose your destination again.");
    if(!d.traffic.reservations.containsKey(id)){
     boolean earlierConflict=false;for(var earlier:d.departures.values()){if(earlier==q)break;if(Traffic.conflicts(q.route,earlier.route)){earlierConflict=true;break;}}
     if(earlierConflict||!d.traffic.reserve(id,q.route)){waiting(q,"Tube busy; waiting for conflicting traffic to clear.");continue;}
    }
    if(q.chunks==null){q.chunks=d.chunks.request(q.route);if(q.chunks==null){d.traffic.release(id);waiting(q,"Waiting for the route chunk budget.");continue;}q.loading=d.clock;}
    if(q.chunks.failed()||d.clock-q.loading>TransitLimits.LOAD_SECONDS)throw new IllegalArgumentException("Could not prepare the route chunks in time. Try again.");
    if(!q.chunks.ready()){waiting(q,"Preparing route chunks…");continue;}
    if(d.rides.values().stream().anyMatch(r->r.route.getFirst().cell().equals(q.route.getFirst().cell())&&r.progress<TransitLimits.HEADWAY+2)||!d.traffic.landingFree(id,q.route.getFirst().cell())||d.returns.entrySet().stream().anyMatch(e->!d.rides.containsKey(e.getKey())&&q.route.getFirst().cell().equals(e.getValue()))){waiting(q,"Waiting for safe departure spacing.");continue;}
    if(!safeLanding(w,d,q.route.getFirst())||!safeLanding(w,d,q.route.getLast()))throw new IllegalArgumentException("Keep a clear full-size landing and floor two blocks below both suckers");
    if(movement!=null&&movement.getMovementStates().flying)throw new IllegalArgumentException("Land on the station before boarding");
    var manager=s.getComponent(q.ref,MovementManager.getComponentType());if(manager==null)throw new IllegalArgumentException("Native movement adapter is unavailable");
    if(q.prepared==null)q.prepared=new Ride(q.ref,q.player,q.route,pos);
    while(q.verified<q.prepared.path.size()&&validationBudget>0){int i=q.verified++;validationBudget--;if(!clearTravel(w,d,q.prepared.path.get(i),q.prepared.direction(q.prepared.lengths.get(i)),q.prepared))throw new IllegalArgumentException("The passenger corridor is blocked");}
    if(q.verified<q.prepared.path.size())continue;
    if(q.route.stream().anyMatch(n->!NativeBlocks.present(w,n)))throw new IllegalArgumentException("A tube part is missing or rotated");
    // Rebuild the short entrance from the actual waiting position, without changing the destination path.
    var ride=new Ride(q.ref,q.player,q.route,pos);for(int i=1;i<Math.min(11,ride.path.size());i++)if(!clearTravel(w,d,ride.path.get(i),ride.direction(ride.lengths.get(i)),ride))throw new IllegalArgumentException("The station entrance is blocked");ride.chunks=q.chunks;ride.sequence=++d.nextRide;
    if(d.returns.size()>=128)throw new IllegalArgumentException("Safe-return record capacity is full");
    var passenger=TransitPassenger.create(s,q.ref,q.route.getFirst().cell(),manager.getSettings());s.putComponent(q.ref,passengerType,passenger);PassengerAppearance.refresh(s,q.ref);TransitPassenger.scale(s,q.ref,passenger.scale);
    d.entryBlocked.computeIfAbsent(id,k->new HashSet<>()).add(q.route.getFirst().cell());d.returns.put(id,q.route.getFirst().cell());d.flightRestore.put(id,manager.getSettings().fly);d.rides.put(id,ride);d.departures.remove(id);
    PassengerControls.lock(manager.getSettings());manager.getSettings().fly=FlyMode.Forced;manager.update(q.player.getPacketHandler());
    refreshStation(w,d,q.route.getFirst());cue(s,q.player,"intake",pos);message(q.player,"Departing for "+d.name(q.route.getLast())+". /stabort returns safely.");
   }catch(IllegalArgumentException ex){cancelDeparture(d,id);message(q.player,ex.getMessage());}
  }
 }
 static void waiting(TransitWorld.Departure q,String text){if(!text.equals(q.state)){q.state=text;message(q.player,text);}}
 static boolean landingClear(Store<EntityStore>s,TransitWorld d,UUID id,Node node){
  if(!d.traffic.landingFree(id,node.cell()))return false;var at=Ride.feet(node);
  for(var player:s.getExternalData().getWorld().getPlayerRefs()){if(player.getUuid().equals(id))continue;var ref=player.getReference();if(ref==null||!ref.isValid()||ref.getStore()!=s)continue;var t=s.getComponent(ref,TransformComponent.getComponentType());if(t!=null&&Math.abs(t.getPosition().y-at.y)<2.5&&Math.hypot(t.getPosition().x-at.x,t.getPosition().z-at.z)<1.0)return false;}
  return true;
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
  d.rides.remove(ride.player.getUuid());d.parked.put(ride.player.getUuid(),ride);
  int near=ride.routeIndex(ride.progress);var small=d.chunks.request(List.of(ride.route.get(near),arrived?ride.route.getLast():ride.route.getFirst()));if(small!=null){if(ride.chunks!=null)ride.chunks.close();ride.chunks=small;}if(ride.ref.isValid()) {push(s,ride.ref,new Vector3d());PassengerView.reset(s,ride.ref,ride.player);suspendFlight(s,ride.ref,ride.player,d);}
  refreshStation(s.getExternalData().getWorld(),d,ride.route.getFirst());
  Cell target=(arrived?ride.route.getLast():ride.route.getFirst()).cell();d.returns.put(ride.player.getUuid(),target);
  message(ride.player,reason);
  if(ride.ref.isValid()){var saved=s.getComponent(ride.ref,passengerType);if(saved!=null){saved.x=target.x();saved.y=target.y();saved.z=target.z();}}
  d.entryBlocked.computeIfAbsent(ride.player.getUuid(),k->new HashSet<>()).add(target);
  if(arrived)cue(s,ride.player,"arrival",Ride.feet(ride.route.getLast()));
 }
 void recover(Store<EntityStore>s,TransitWorld d,PlayerRef p) {
  UUID id=p.getUuid();if(d.returning.contains(id)||d.growing.containsKey(id))return;Cell cell=d.returns.get(id);var node=d.graph.nodes.get(cell);var w=s.getExternalData().getWorld();
  if(node==null||!station(node.item()))return;
  var lease=d.recoveryChunks.get(id);if(lease==null){lease=d.chunks.request(List.of(node));if(lease==null)return;d.recoveryChunks.put(id,lease);d.recoveryLoadTime.put(id,d.clock);}
  if(lease.failed()||d.clock-d.recoveryLoadTime.get(id)>TransitLimits.LOAD_SECONDS){closeRecovery(d,id);return;}
  if(!lease.ready()||!NativeBlocks.present(w,node)||!landingClear(s,d,id,node))return;
  var ref=p.getReference();if(ref==null||!ref.isValid()||ref.getStore()!=s)return;
  var safe=Ride.feet(node);var t=s.getComponent(ref,TransformComponent.getComponentType());if(t==null)return;
  // The station's3x3 deck supplies the return floor. All body cells must belong to it or be empty.
  var check=new Ride(ref,p,List.of(node),safe);if(!safeLanding(w,d,node)||!clearBody(w,d,safe,check)||!d.traffic.claimLanding(id,cell))return;
  d.returning.add(id);w.execute(()->{
   if(!ref.isValid()||ref.getStore()!=s) {d.returning.remove(id);return;}
   push(s,ref,new Vector3d());PassengerView.reset(s,ref,p);suspendFlight(s,ref,p,d);var teleport=Teleport.createExact(safe,t.getRotation(),p.getHeadRotation());var done=new CompletableFuture<Void>();teleport.setOnComplete(done);
   s.putComponent(ref,Teleport.getComponentType(),teleport);
   done.whenComplete((v,error)->w.execute(()->{d.returning.remove(id);if(error==null&&ref.isValid()&&ref.getStore()==s) {var parked=d.parked.remove(id);if(parked!=null&&parked.chunks!=null)parked.chunks.close();d.traffic.releaseRoute(id);var saved=s.getComponent(ref,passengerType);if(saved!=null){var scale=s.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent.getComponentType());d.growing.put(id,new TransitWorld.Growth(scale==null?saved.scale:scale.getScale(),0));}
    PassengerView.reset(s,ref,p);d.entryBlocked.computeIfAbsent(id,k->new HashSet<>()).add(cell);}}));
  });
 }
 final class Tick extends TickingSystem<EntityStore> {
  @Override public void tick(float dt,int index,Store<EntityStore>s) {
   var d=data(s);var w=s.getExternalData().getWorld();d.clock+=dt;d.chunks.tick();
   Set<UUID> online=new HashSet<>();for(var p:w.getPlayerRefs())online.add(p.getUuid());for(var id:List.copyOf(d.recoveryChunks.keySet()))if(!online.contains(id))closeRecovery(d,id);for(var id:List.copyOf(d.parked.keySet()))if(!online.contains(id)){var parked=d.parked.remove(id);if(parked.chunks!=null)parked.chunks.close();d.traffic.release(id);d.growing.remove(id);d.returning.remove(id);}
   departures(s,d);int count=Math.min(64,d.confirmed.size());for(int i=0;i<count;i++)d.confirmed.remove().run();
   for(Ride r:List.copyOf(d.rides.values())) {
    if(!r.ref.isValid()||r.ref.getStore()!=s) {d.rides.remove(r.player.getUuid());d.traffic.release(r.player.getUuid());if(r.chunks!=null)r.chunks.close();refreshStation(w,d,r.route.getFirst());continue;}
    r.elapsed+=dt;r.logWait+=dt;var movement=s.getComponent(r.ref,com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent.getComponentType());if(movement!=null&&movement.getMovementStates().crouching) {end(s,d,r,false,"Travel cancelled by crouching; returning safely.");continue;}var pos=s.getComponent(r.ref,TransformComponent.getComponentType()).getPosition();
    boolean intact=true;int part=r.routeIndex(r.progress);for(int j=Math.max(0,part-1);j<=Math.min(r.route.size()-1,part+8);j++)if(!NativeBlocks.present(w,r.route.get(j)))intact=false;
    var passenger=s.getComponent(r.ref,passengerType);if(passenger==null){end(s,d,r,false,"Passenger recovery record missing");continue;}
    if(r.shrink<.85f){r.shrink=Math.min(.85f,r.shrink+dt);float t=r.shrink/.85f;float ease=t*t*(3-2*t);TransitPassenger.scale(s,r.ref,passenger.scale+(Math.min(.3f,passenger.scale*.3f)-passenger.scale)*ease);push(s,r.ref,new Vector3d(r.start).sub(pos).mul(12));if(r.logWait>.15f){r.logWait=0;}continue;}
    if(!intact||r.elapsed>r.timeout()||!clearTravel(w,d,pos,r.direction(r.progress),r)) {end(s,d,r,false,"Route unavailable; returning to the boarding station.");continue;}
    r.progress=r.nearest(pos);if(r.progress>r.lastProgress+.05){r.lastProgress=r.progress;r.stall=0;}else r.stall+=dt;
    if(r.stall>TransitLimits.WAIT_SECONDS){end(s,d,r,false,"Journey stalled; returning safely.");continue;}
    var ahead=new ArrayList<Ride>(d.rides.values());ahead.addAll(d.parked.values());double permitted=r.total;for(var other:ahead)if(other!=r&&other.route.getFirst().cell().equals(r.route.getFirst().cell()))permitted=Math.min(permitted,r.limitBehind(other));
    if(!landingClear(s,d,r.player.getUuid(),r.route.getLast())||!safeLanding(w,d,r.route.getLast()))permitted=Math.min(permitted,r.total-4);
    if(permitted>=r.total-.001&&r.progress>=r.total-.12&&pos.distance(r.path.getLast())<.18){end(s,d,r,true,"Arrived safely.");continue;}
    if(permitted<=r.progress+.1){push(s,r.ref,new Vector3d());if(r.logWait>5){r.logWait=0;message(r.player,"Holding for safe headway or a clear landing. /stabort returns safely.");}continue;}
    double speed=Math.min(6,1.5+r.elapsed*12);double next=Math.min(permitted,r.progress+.18);
    var target=r.at(next);if(pos.distance(target)>2.5) {end(s,d,r,false,"Left the guided corridor; returning safely.");continue;}
    var direction=r.direction(r.progress+.03);var v=new Vector3d(target).sub(pos).mul(20);
    if(next<permitted-.7)v.fma(speed,direction);if(v.length()>12)v.mul(12/v.length());
    PassengerView.update(s,r,direction);push(s,r.ref,v);


   }
   for(var p:w.getPlayerRefs()){
    UUID id=p.getUuid();var growth=d.growing.get(id);if(growth==null)continue;var ref=p.getReference();if(ref==null||!ref.isValid()||ref.getStore()!=s)continue;
    var saved=s.getComponent(ref,passengerType);if(saved==null){d.growing.remove(id);continue;}
    var node=d.graph.nodes.get(d.returns.get(id));var pos=s.getComponent(ref,TransformComponent.getComponentType()).getPosition();
    if(node==null||!safeLanding(w,d,node)||pos.distanceSquared(Ride.feet(node))>.5){d.growing.remove(id);continue;}
    float time=Math.min(.7f,growth.time()+dt);d.growing.put(id,new TransitWorld.Growth(growth.from(),time));float t=time/.7f;float ease=t*t*(3-2*t);TransitPassenger.scale(s,ref,growth.from()+(saved.scale-growth.from())*ease);
    if(time>=.7f){TransitPassenger.scale(s,ref,saved.scale);d.growing.remove(id);d.returning.add(id);w.execute(()->{if(ref.isValid()&&ref.getStore()==s){PassengerControls.apply(s.getComponent(ref,MovementManager.getComponentType()).getSettings(),saved.inputSpeeds);PassengerAppearance.finish(s,ref,saved.scale);s.removeComponent(ref,passengerType);d.returns.remove(id);d.traffic.release(id);closeRecovery(d,id);restoreFlight(s,ref,p,d);message(p,"Full size restored. Step clear before boarding again.");}d.returning.remove(id);});}
   }
   scanEntries(s,d);
   for(var p:w.getPlayerRefs())if(d.returns.containsKey(p.getUuid())&&!d.rides.containsKey(p.getUuid()))recover(s,d,p);
   for(var n:d.graph.stations())if(station(n.item())&&d.rides.values().stream().noneMatch(r->r.route.getFirst().cell().equals(n.cell())))NativeBlocks.state(w,n,false);
  }
 }
 static boolean underMouth(Vector3d p,Node n,double radius){double dx=p.x-n.cell().x()-.5,dz=p.z-n.cell().z()-.5,below=n.cell().y()-p.y;return dx*dx+dz*dz<radius*radius&&below>1.0&&below<3.2;}
 static boolean safeLanding(World w,TransitWorld d,Node n){var at=Ride.feet(n);var floor=new Cell((int)Math.floor(at.x),(int)Math.floor(at.y-.05),(int)Math.floor(at.z));return NativeBlocks.block(w,floor)!=null&&!NativeBlocks.empty(w,floor)&&clearBody(w,d,at,new Ride(null,null,List.of(n),at));}
 void scanEntries(Store<EntityStore>s,TransitWorld d){var w=s.getExternalData().getWorld();for(var player:w.getPlayerRefs()){
  var ref=player.getReference();if(ref==null||!ref.isValid()||ref.getStore()!=s)continue;var transform=s.getComponent(ref,TransformComponent.getComponentType());if(transform==null)continue;var pos=transform.getPosition();var id=player.getUuid();var blocked=d.entryBlocked.computeIfAbsent(id,k->new HashSet<>());
  var saved=s.getComponent(ref,passengerType);if(saved!=null&&!d.rides.containsKey(id)&&saved.world.equals(w.getName())){d.returns.putIfAbsent(id,saved.cell());d.flightRestore.putIfAbsent(id,FlyMode.valueOf(saved.fly));}
  if(d.entrySeen.add(id)){var scale=s.getComponent(ref,com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent.getComponentType());if(saved==null&&scale!=null&&scale.getScale()==1f&&d.graph.nodes.values().stream().anyMatch(n->n.owner().equals(id)))w.execute(()->{if(ref.isValid()&&!d.rides.containsKey(id)&&s.getComponent(ref,passengerType)==null){PassengerAppearance.finish(s,ref,1f);}});for(var n:d.graph.stations())if(station(n.item())&&underMouth(pos,n,1.5))blocked.add(n.cell());}
  blocked.removeIf(c->{var n=d.graph.nodes.get(c);return n==null||!underMouth(pos,n,1.5);});
  if(s.getComponent(ref,com.hypixel.hytale.server.core.entity.entities.Player.getComponentType()).getPageManager().getCustomPage()!=null||d.rides.containsKey(id)||d.departures.containsKey(id)||d.returns.containsKey(id)||d.pendingAuto.contains(id)||d.choosing.contains(id)||d.naming.contains(id))continue;
  for(var n:d.graph.stations())if(station(n.item())&&!blocked.contains(n.cell())&&underMouth(pos,n,.85)&&NativeBlocks.present(w,n)){
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
   var player=chunk.getComponent(i,PlayerRef.getComponentType());if(!n.owner().equals(player.getUuid())||d.traffic.occupied(p)||d.departures.values().stream().anyMatch(q->q.route.stream().anyMatch(part->part.cell().equals(p)))||d.returns.containsValue(p)||d.confirmed.size()>=64) {e.setCancelled(true);message(player,"Only the owner may dismantle an idle route; keep recovery stations intact");return;}
   d.confirmed.add(()->{if(!NativeBlocks.present(w,n)){d.graph.nodes.remove(p);d.names.remove(p);}});
  }
 }
 final class BoardCommand extends AbstractPlayerCommand {
  BoardCommand() {super("stboard","Deliberately board from a nearby Slipstream station landing");requireNoPermission();}
  @Override protected void execute(CommandContext c,Store<EntityStore>s,Ref<EntityStore>r,PlayerRef p,World w) {
   var pos=s.getComponent(r,TransformComponent.getComponentType()).getPosition();var nearest=data(s).graph.stations().stream().min(Comparator.comparingDouble(n->pos.distanceSquared(Ride.feet(n))));
   if(nearest.isEmpty())message(p,"No station is placed");else board(s,r,p,nearest.get().cell());
  }
 }

 final class Abort extends AbstractPlayerCommand {
  Abort() {super("stabort","Return safely from Slipstream passenger travel");requireNoPermission();}
  @Override protected void execute(CommandContext c,Store<EntityStore>s,Ref<EntityStore>r,PlayerRef p,World w) {var d=data(s);var ride=d.rides.get(p.getUuid());if(ride!=null)end(s,d,ride,false,"Travel aborted; returning to boarding station.");else if(d.departures.containsKey(p.getUuid())){cancelDeparture(d,p.getUuid());message(p,"Waiting departure cancelled.");}}
 }




}

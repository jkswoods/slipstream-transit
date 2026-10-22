package dev.slipstream;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.*;
public final class TransitWorld implements Resource<EntityStore> {
 public static final BuilderCodec<TransitWorld> CODEC=BuilderCodec.builder(TransitWorld.class,TransitWorld::new)
  .append(new KeyedCodec<>("Parts",Codec.STRING),(s,v)->s.graph=TransitGraph.restore(v),s->s.graph.snapshot()).add()
  .append(new KeyedCodec<>("ReturnStations",Codec.STRING),TransitWorld::readReturns,TransitWorld::returns).add()
  .append(new KeyedCodec<>("Names",Codec.STRING),TransitWorld::readNames,TransitWorld::names).add()
  .append(new KeyedCodec<>("FlightRestore",Codec.STRING),TransitWorld::readFlight,TransitWorld::flight).add().build();
 TransitGraph graph=new TransitGraph();
 final Map<UUID,TransitGraph.Cell> returns=new HashMap<>();
 final Map<UUID,com.hypixel.hytale.protocol.FlyMode> flightRestore=new HashMap<>();
 final ArrayDeque<Runnable> confirmed=new ArrayDeque<>();
 final Map<TransitGraph.Cell,String> names=new HashMap<>();
 record Growth(float from,float time) {}
 final Map<UUID,Growth> growing=new HashMap<>();
 final Set<UUID> choosing=new HashSet<>(),naming=new HashSet<>();
 final Map<UUID,Ride> rides=new HashMap<>();
 final Map<UUID,Set<TransitGraph.Cell>> entryBlocked=new HashMap<>();
 final Set<UUID> entrySeen=new HashSet<>(),pendingAuto=new HashSet<>();
 final Set<UUID> returning=new HashSet<>();
 String name(TransitGraph.Node n){return names.getOrDefault(n.cell(),(TransitGraph.kind(n.item()).equals("ST_Harbour")?"Harbour":TransitGraph.kind(n.item()).equals("ST_UpIntake")?"Straight Sucker":"Overlook")+" · "+n.cell().x()+", "+n.cell().z());}
 String names(){var join=new StringJoiner(";");names.forEach((c,n)->join.add(c.x()+","+c.y()+","+c.z()+","+Base64.getEncoder().encodeToString(n.getBytes(java.nio.charset.StandardCharsets.UTF_8))));return join.toString();}
 void readNames(String s){if(s.isEmpty())return;for(String row:s.split(";")){var p=row.split(",",4);if(p.length!=4||names.size()>=128)throw new IllegalStateException("Invalid station names");var n=new String(Base64.getDecoder().decode(p[3]),java.nio.charset.StandardCharsets.UTF_8);if(n.isBlank()||n.length()>32)throw new IllegalStateException("Invalid station name");names.put(new TransitGraph.Cell(Integer.parseInt(p[0]),Integer.parseInt(p[1]),Integer.parseInt(p[2])),n);}}
 String returns() {StringJoiner s=new StringJoiner(";");returns.forEach((id,c)->s.add(id+","+c.x()+","+c.y()+","+c.z()));return s.toString();}
 void readReturns(String s) {
  if(s.isEmpty())return;String[] rows=s.split(";");if(rows.length>128)throw new IllegalStateException("Too many Slipstream return markers");
  for(String row:rows) {String[] p=row.split(",");if(p.length!=4)throw new IllegalStateException("Invalid return marker");returns.put(UUID.fromString(p[0]),new TransitGraph.Cell(Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3])));}
 }
 String flight() {StringJoiner s=new StringJoiner(";");flightRestore.forEach((id,mode)->s.add(id+","+mode.name()));return s.toString();}
 void readFlight(String value) {if(value.isEmpty())return;String[] rows=value.split(";");if(rows.length>128)throw new IllegalStateException("Too many flight markers");for(String row:rows) {String[] a=row.split(",");if(a.length!=2)throw new IllegalStateException("Invalid flight marker");flightRestore.put(UUID.fromString(a[0]),com.hypixel.hytale.protocol.FlyMode.valueOf(a[1]));}}
 @Override public TransitWorld clone() {var s=new TransitWorld();s.graph=TransitGraph.restore(graph.snapshot());s.names.putAll(names);s.returns.putAll(returns);s.flightRestore.putAll(flightRestore);return s;}
}

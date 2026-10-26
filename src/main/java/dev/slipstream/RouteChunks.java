package dev.slipstream;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import java.util.*;
import static dev.slipstream.TransitGraph.*;

/** Holds only the registered route corridor and full-size station landing envelopes. */
final class RouteChunks implements AutoCloseable {
 record Section(int x,int y,int z){}
 record Held(ChunkSection section,WorldChunk column){}
 final ScopedLoads<Section,Held> pool;
 RouteChunks(World world){var grid=world.getChunkStore();pool=new ScopedLoads<>(TransitLimits.WORLD_SECTIONS,
  at->grid.getChunkSectionReferenceAsync(at.x,at.y,at.z).thenApply(ref->{
   // API futures finish on the world thread; component reads are deferred explicitly regardless.
   return ref;
  }).thenApplyAsync(ref->{if(ref==null||!ref.isValid())throw new IllegalStateException("Route section unavailable");var s=grid.getStore().getComponent(ref,ChunkSection.getComponentType());if(s==null)throw new IllegalStateException("Route section missing");var c=s.getChunkColumnReference();if(c==null||!c.isValid())throw new IllegalStateException("Route column missing");return new Held(s,grid.getStore().getComponent(c,WorldChunk.getComponentType()));},world),
  h->{if(h.column==null)throw new IllegalStateException("Route column unavailable");h.column.addKeepLoaded();h.section.addKeepLoaded();},
  h->{h.section.removeKeepLoaded();h.column.removeKeepLoaded();});}
 static Set<Section> sections(List<Node> route){Set<Section> keys=new LinkedHashSet<>();for(var node:route){Cell c=node.cell();int low=station(node.item())?-3:-1;
  for(int x:candidates(c.x()-1,c.x()+1))for(int y:candidates(c.y()+low,c.y()+2))for(int z:candidates(c.z()-1,c.z()+1))keys.add(new Section(x,y,z));
 }return Set.copyOf(keys);}
 static int[] candidates(int low,int high){int a=Math.floorDiv(low,32),b=Math.floorDiv(high,32);return a==b?new int[]{a}:new int[]{a,b};}
 ScopedLoads<Section,Held>.Lease request(List<Node> route){var keys=sections(route);if(keys.size()>TransitLimits.ROUTE_SECTIONS)throw new IllegalArgumentException("Route crosses more than "+TransitLimits.ROUTE_SECTIONS+" chunk sections; shorten it or adjust the bounded server limit");return pool.request(keys);}
 void tick(){pool.tick();}
 int retained(){return (int)pool.entries.values().stream().filter(e->e.value!=null).count();}
 @Override public void close(){pool.close();}
}

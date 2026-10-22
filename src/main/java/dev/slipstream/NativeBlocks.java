package dev.slipstream;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.*;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.protocol.BlockPosition;
import org.joml.Vector3i;
import java.util.*;
import static dev.slipstream.TransitGraph.*;
final class NativeBlocks {
 static final Set<String> ITEMS; static {var ids=new HashSet<String>();for(String k:KINDS)for(String c:COLOURS)ids.add(k+(c.isEmpty()?"":"_"+c));ITEMS=Set.copyOf(ids);}
 static Cell cell(Vector3i p) {return new Cell(p.x,p.y,p.z);}
 static BlockSection section(World w,Cell p) {var c=w.getChunkStore();var r=c.getChunkSectionReferenceAtBlock(p.x(),p.y(),p.z());return r==null||!r.isValid()?null:c.getStore().getComponent(r,BlockSection.getComponentType());}
 static BlockType block(World w,Cell p) {var s=section(w,p);return s==null?null:BlockType.getAssetMap().getAsset(s.get(p.x()&31,p.y()&31,p.z()&31));}
 static boolean empty(World w,Cell p) {var s=section(w,p);return s!=null&&s.get(p.x()&31,p.y()&31,p.z()&31)==BlockType.EMPTY_ID&&s.getFiller(p.x()&31,p.y()&31,p.z()&31)==0;}
 static Cell origin(World w,Cell p) {if(section(w,p)==null)return p;var b=BlockOperations.resolveBaseBlockPosition(w.getChunkStore(),new BlockPosition(p.x(),p.y(),p.z()));return b==null?p:new Cell(b.x,b.y,b.z);}
 static String id(BlockType b) {return b==null?null:b.isState()?b.getDefaultStateKey():b.getId();}
 static boolean present(World w,Node n) {var s=section(w,n.cell());return s!=null&&n.item().equals(id(block(w,n.cell())))&&s.getRotationIndex(n.cell().x()&31,n.cell().y()&31,n.cell().z()&31)==n.rotation();}
 static List<Cell> ports(String id,int rotation) {
  List<Cell> base=switch(kind(id)) {
   case "ST_UpIntake"->List.of(new Cell(0,1,0));
   case "ST_Harbour","ST_Overlook"->List.of(new Cell(1,0,0));
   case "ST_Bend"->List.of(new Cell(-1,0,0),new Cell(0,0,1));
   case "ST_RiseBend"->List.of(new Cell(-1,0,0),new Cell(0,1,0));
   case "ST_DropBend"->List.of(new Cell(-1,0,0),new Cell(0,-1,0));
   case "ST_Splitter"->List.of(new Cell(-1,0,0),new Cell(1,0,0),new Cell(0,0,1));
   case "ST_Riser"->List.of(new Cell(0,-1,0),new Cell(0,1,0));
   default->List.of(new Cell(-1,0,0),new Cell(1,0,0));
  };
  return base.stream().map(p->{var v=new Vector3i(p.x(),p.y(),p.z());RotationTuple.get(rotation).applyRotationTo(v);return cell(v);}).toList();
 }
 static RotationTuple snap(TransitGraph g,Cell at,String item,UUID owner,RotationTuple chosen) {
  RotationTuple best=chosen;int score=connections(g,at,item,owner,chosen.index());
  for(var yaw:Rotation.values()) {var candidate=RotationTuple.of(yaw,Rotation.None,Rotation.None);int n=connections(g,at,item,owner,candidate.index());if(n>score){best=candidate;score=n;}}
  return best;
 }
 static int connections(TransitGraph g,Cell at,String item,UUID owner,int rotation) {
  int count=0;for(Cell p:ports(item,rotation)){var neighbour=g.nodes.get(at.add(p,1));if(neighbour!=null&&neighbour.owner().equals(owner)&&neighbour.ports().contains(p.opposite()))count++;}return count;
 }

 static void state(World w,Node n,boolean active) {
  if(!present(w,n))return;var current=block(w,n.cell());var base=BlockType.getAssetMap().getAsset(n.item());var target=active?base.getBlockForState("Active"):base;if(target==null||current==target)return;
  var c=w.getChunkStore();var p=n.cell();var r=c.getChunkSectionReferenceAtBlock(p.x(),p.y(),p.z());
  BlockOperations.setBlock(c,r,p.x(),p.y(),p.z(),BlockType.getAssetMap().getIndex(target.getId()),target,n.rotation(),0,198);
 }
 static boolean place(World w,Node n) {
  var p=n.cell();var c=w.getChunkStore();var r=c.getChunkSectionReferenceAtBlock(p.x(),p.y(),p.z());if(r==null||!r.isValid())return false;var type=BlockType.getAssetMap().getAsset(n.item());
  BlockOperations.setBlock(c,r,p.x(),p.y(),p.z(),BlockType.getAssetMap().getIndex(n.item()),type,n.rotation(),0,198);return present(w,n);
 }



 static boolean clearAssembly(World w,Cell p) {return empty(w,p);}
}

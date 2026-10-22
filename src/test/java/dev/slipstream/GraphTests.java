package dev.slipstream;
import java.util.*;
import static dev.slipstream.TransitGraph.*;
public final class GraphTests {
 static UUID owner=UUID.randomUUID();static Cell X=new Cell(1,0,0),Z=new Cell(0,0,1),Y=new Cell(0,1,0);
 static Node node(int x,int y,int z,String id,Cell...ports) {return new Node(new Cell(x,y,z),id,0,owner,List.of(ports));}
 static void refuses(Runnable r) {try{r.run();throw new AssertionError("Expected refusal");}catch(IllegalArgumentException|IllegalStateException expected){}}
 public static void main(String[]a) {
  var g=new TransitGraph();g.add(node(0,80,0,"ST_Harbour",X));g.add(node(1,80,0,"ST_Straight",X.opposite(),X));g.add(node(2,80,0,"ST_Bend",X.opposite(),Z));g.add(node(2,80,1,"ST_RiseBend",Z.opposite(),Y));g.add(node(2,81,1,"ST_Riser",Y.opposite(),Y));g.add(node(2,82,1,"ST_DropBend",Y.opposite(),Z));g.add(node(2,82,2,"ST_Overlook",Z.opposite()));
  assert g.route(new Cell(0,80,0),owner).size()==7;assert g.route(new Cell(2,82,2),owner).size()==7;
  var copy=TransitGraph.restore(g.snapshot());assert copy.snapshot().equals(g.snapshot());assert copy.route(new Cell(0,80,0),owner).size()==7;
  refuses(()->g.route(new Cell(0,80,0),UUID.randomUUID()));
  refuses(()->g.add(node(0,80,0,"ST_Bend",X,Z)));refuses(()->TransitGraph.restore(g.snapshot()+"AAAA"));
  copy.nodes.remove(new Cell(2,81,1));refuses(()->copy.route(new Cell(0,80,0),owner));
  var h=new TransitGraph();h.add(node(0,80,0,"ST_Harbour",X));h.add(node(1,80,0,"ST_Overlook",Z));refuses(()->h.route(new Cell(0,80,0),owner));
  var branch=new TransitGraph();branch.add(node(0,80,0,"ST_Harbour",X));branch.add(node(1,80,0,"ST_Splitter_Gold",X.opposite(),X,Z));branch.add(node(2,80,0,"ST_Harbour_Blue",X.opposite()));branch.add(node(1,80,1,"ST_Overlook_Coral",Z.opposite()));
  assert branch.destinations(new Cell(0,80,0),owner).size()==2;
  assert branch.route(new Cell(0,80,0),new Cell(1,80,1),owner).getLast().item().equals("ST_Overlook_Coral");
  assert branch.route(new Cell(2,80,0),new Cell(1,80,1),owner).size()==3;
  refuses(()->branch.route(new Cell(0,80,0),owner));
  assert TransitGraph.restore(branch.snapshot()).destinations(new Cell(0,80,0),owner).size()==2;
  // Existing compact v2 records retain all user-placed parts.
  byte[] legacy=Base64.getDecoder().decode(g.snapshot());legacy[3]=2;assert TransitGraph.restore(Base64.getEncoder().encodeToString(legacy)).nodes.size()==g.nodes.size();
  var limit=new TransitGraph();limit.add(node(0,80,0,"ST_Harbour",X));for(int x=1;x<63;x++)limit.add(node(x,80,0,"ST_Straight",X.opposite(),X));limit.add(node(63,80,0,"ST_Overlook",X.opposite()));assert limit.route(new Cell(0,80,0),owner).size()==64;
  limit.nodes.remove(new Cell(63,80,0));limit.add(node(63,80,0,"ST_Straight",X.opposite(),X));limit.add(node(64,80,0,"ST_Overlook",X.opposite()));refuses(()->limit.route(new Cell(0,80,0),owner));
  System.out.println("Graph checks passed: bent/rising/reverse, coloured splitters and destination choice, v2 migration/v3 snapshot, 64-part bound, ownership, overlap, malformed and broken ports.");
 }
}

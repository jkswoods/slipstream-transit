package dev.slipstream;

import java.io.*;
import java.util.*;

/** Bounded reciprocal routing with shortest paths through T splitters. No chunk loading. */
public final class TransitGraph {
    public record Cell(int x,int y,int z) {
        public Cell add(Cell p,int n) {return new Cell(x+p.x*n,y+p.y*n,z+p.z*n);}
        public Cell opposite() {return new Cell(-x,-y,-z);}
    }
    public record Node(Cell cell,String item,int rotation,UUID owner,List<Cell> ports) {
        public Node {if(!part(item))throw new IllegalArgumentException("Unknown route part");ports=List.copyOf(ports);if(ports.size()!=(station(item)?1:kind(item).equals("ST_Splitter")?3:2))throw new IllegalArgumentException("Invalid port count");}
    }
    public static final List<String> KINDS=List.of("ST_Harbour","ST_Overlook","ST_Straight","ST_Bend","ST_RiseBend","ST_DropBend","ST_Riser","ST_Splitter","ST_UpIntake");
    public static final List<String> COLOURS=List.of("","Coral","Gold","Blue","Violet","White");
    public static String kind(String item) {for(String c:COLOURS)if(!c.isEmpty()&&item.endsWith("_"+c))return item.substring(0,item.length()-c.length()-1);return item;}
    public static boolean part(String item) {if(KINDS.contains(item))return true;for(String c:COLOURS)if(!c.isEmpty()&&item.endsWith("_"+c)&&KINDS.contains(kind(item)))return true;return false;}
    public static boolean station(String item) {return kind(item).equals("ST_Harbour")||kind(item).equals("ST_Overlook")||kind(item).equals("ST_UpIntake");}
    public final Map<Cell,Node> nodes=new LinkedHashMap<>();
    public Cell reserved(Cell p) {
        if(nodes.containsKey(p))return p;
        return null;
    }
    public void add(Node n) {
        if(nodes.size()>=128)throw new IllegalArgumentException("World limit is128 tube parts");
        if(reserved(n.cell)!=null)throw new IllegalArgumentException("This block is already occupied by a tube");
        nodes.put(n.cell,n);
    }
    /** Reachable terminal stations, each reached by one deterministic shortest reciprocal path. */
    public List<List<Node>> destinations(Cell start,UUID owner) {
        Node first=nodes.get(start);if(first==null||!station(first.item))throw new IllegalArgumentException("Board at a sucker");
        if(!first.owner.equals(owner))throw new IllegalArgumentException("Route owner changed");
        var queue=new ArrayDeque<List<Node>>();queue.add(List.of(first));Set<Cell> seen=new HashSet<>();seen.add(start);List<List<Node>> result=new ArrayList<>();
        while(!queue.isEmpty()) {
            var path=queue.removeFirst();Node n=path.getLast();
            if(path.size()>64)continue;
            if(n!=first&&station(n.item)) {result.add(path);continue;}
            for(Cell p:n.ports) {
                Node next=nodes.get(n.cell.add(p,1));
                if(next==null||!next.owner.equals(owner)||!next.ports.contains(p.opposite())||!seen.add(next.cell))continue;
                var extended=new ArrayList<>(path);extended.add(next);queue.add(List.copyOf(extended));
            }
        }
        return List.copyOf(result);
    }
    public List<Node> route(Cell start,UUID owner) {
        var paths=destinations(start,owner);if(paths.isEmpty())throw new IllegalArgumentException("No connected destination. Match adjacent tube ports and finish with a sucker.");
        if(paths.size()!=1)throw new IllegalArgumentException("Choose a destination at the intake");return paths.getFirst();
    }
    public List<Node> route(Cell start,Cell destination,UUID owner) {
        return destinations(start,owner).stream().filter(p->p.getLast().cell.equals(destination)).findFirst().orElseThrow(()->new IllegalArgumentException("That destination is no longer connected"));
    }
    public String snapshot() {
        try {var b=new ByteArrayOutputStream();var o=new DataOutputStream(b);o.writeInt(3);o.writeInt(nodes.size());
            for(Node n:nodes.values()) {o.writeInt(n.cell.x);o.writeInt(n.cell.y);o.writeInt(n.cell.z);o.writeUTF(n.item);o.writeInt(n.rotation);o.writeLong(n.owner.getMostSignificantBits());o.writeLong(n.owner.getLeastSignificantBits());o.writeInt(n.ports.size());for(Cell p:n.ports) {o.writeInt(p.x);o.writeInt(p.y);o.writeInt(p.z);}}
            return Base64.getEncoder().encodeToString(b.toByteArray());
        }catch(IOException e) {throw new UncheckedIOException(e);}
    }
    public static TransitGraph restore(String s) {
        try {var in=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(s)));int version=in.readInt();if(version!=2&&version!=3)throw new IOException("Unknown version");int count=in.readInt();if(count<0||count>128)throw new IOException("Invalid count");var g=new TransitGraph();
            for(int i=0;i<count;i++) {Cell c=new Cell(in.readInt(),in.readInt(),in.readInt());String item=in.readUTF();int rotation=in.readInt();UUID owner=new UUID(in.readLong(),in.readLong());int n=in.readInt();if(n<1||n>3)throw new IOException("Invalid ports");List<Cell> ports=new ArrayList<>();for(int p=0;p<n;p++) {var d=new Cell(in.readInt(),in.readInt(),in.readInt());if(Math.abs(d.x)+Math.abs(d.y)+Math.abs(d.z)!=1||ports.contains(d))throw new IOException("Invalid direction");ports.add(d);}g.add(new Node(c,item,rotation,owner,ports));}
            if(in.available()!=0)throw new IOException("Trailing data");return g;
        }catch(IOException|IllegalArgumentException e) {throw new IllegalStateException("Cannot restore Slipstream route registry; refusing to erase it",e);}
    }
}

package dev.slipstream;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import java.util.*;
import static dev.slipstream.TransitGraph.*;
final class Ride {
 final Ref<EntityStore> ref;final PlayerRef player;final List<Node> route;final List<Vector3d> path=new ArrayList<>();
 final List<Double> lengths=new ArrayList<>();final Map<Cell,Double> cellDistances=new LinkedHashMap<>();final Set<Cell> cells=new HashSet<>();
 ScopedLoads<RouteChunks.Section,RouteChunks.Held>.Lease chunks;long sequence;double lastProgress,stall;float checkWait;double total,progress;float elapsed,logWait,poseWait;float shrink;final Vector3d start;int pose=-999;
 Ride(Ref<EntityStore> ref,PlayerRef p,List<Node> route,Vector3d actual) {
  this.ref=ref;start=new Vector3d(actual);player=p;this.route=List.copyOf(route);for(var n:route)cells.add(n.cell());path.add(new Vector3d(actual));
  var first=route.getFirst();var firstCentre=centre(first);var firstMouth=new Vector3d(firstCentre.x,first.cell().y()+.02-.2775,firstCentre.z);path.add(firstMouth);
  var firstPort=port(first);for(int j=1;j<=8;j++){double t=j/8.;path.add(new Vector3d(firstMouth).mul((1-t)*(1-t)).fma(2*t*(1-t),firstCentre).fma(t*t,firstPort));}
  cellDistances.put(first.cell(),0.);
  for(int i=1;i<route.size()-1;i++) {
   cellDistances.put(route.get(i).cell(),(double)path.size());
   Node n=route.get(i);var centre=centre(n);var before=centre(route.get(i-1));var after=centre(route.get(i+1));
   var a=new Vector3d(centre).lerp(before,.5);var b=new Vector3d(centre).lerp(after,.5);path.add(a);
   for(int j=1;j<=16;j++) {double t=j/16.;path.add(new Vector3d(a).mul((1-t)*(1-t)).fma(2*t*(1-t),centre).fma(t*t,b));}
  }
  if(route.size()>1){var last=route.getLast();var c=centre(last);var mouth=new Vector3d(c.x,last.cell().y()+.02-.2775,c.z);var port=port(last);for(int j=1;j<=8;j++){double t=j/8.;path.add(new Vector3d(port).mul((1-t)*(1-t)).fma(2*t*(1-t),c).fma(t*t,mouth));}path.add(feet(last));}lengths.add(0.);for(int i=1;i<path.size();i++){total+=path.get(i-1).distance(path.get(i));lengths.add(total);}
  cellDistances.replaceAll((cell,offset)->lengths.get(Math.min(path.size()-1,offset.intValue())));cellDistances.put(route.getLast().cell(),total);
 }
 int segment(double distance){int low=1,high=lengths.size()-1;while(low<high){int middle=(low+high)>>>1;if(lengths.get(middle)<distance)low=middle+1;else high=middle;}return low;}
 Vector3d at(double distance) {distance=Math.max(0,Math.min(total,distance));int i=segment(distance);double span=lengths.get(i)-lengths.get(i-1);return new Vector3d(path.get(i-1)).lerp(path.get(i),span<1e-7?1:(distance-lengths.get(i-1))/span);}
 double nearest(Vector3d pos) {double best=Double.MAX_VALUE,found=progress;int first=segment(Math.max(0,progress-.5));for(int i=first;i<path.size()&&lengths.get(i-1)<=progress+3;i++){var a=path.get(i-1);var ab=new Vector3d(path.get(i)).sub(a);double den=ab.lengthSquared();if(den<1e-8)continue;double t=Math.max(0,Math.min(1,new Vector3d(pos).sub(a).dot(ab)/den));double distance=new Vector3d(a).fma(t,ab).distanceSquared(pos);if(distance<best){best=distance;found=lengths.get(i-1)+t*Math.sqrt(den);}}return Math.max(progress,found);}
 int routeIndex(double distance){int low=0,high=route.size()-1;while(low<high){int mid=(low+high+1)>>>1;if(cellDistances.get(route.get(mid).cell())<=distance)low=mid;else high=mid-1;}return low;}
 double limitBehind(Ride ahead){if(ahead.sequence>=sequence)return total;for(var e:cellDistances.entrySet()){if(e.getKey().equals(route.getFirst().cell()))continue;Double offset=ahead.cellDistances.get(e.getKey());if(offset!=null)return Math.max(progress,Math.min(total,ahead.progress-offset+e.getValue()-TransitLimits.HEADWAY));}return total;}
 double timeout(){return 30+total/2.0+TransitLimits.WAIT_SECONDS;}
 Vector3d direction(double distance) {var v=at(Math.min(total,distance+.09)).sub(at(Math.max(0,distance-.05)));return v.lengthSquared()<1e-8?new Vector3d(0,0,-1):v.normalize();}
 static Vector3d centre(Node n) {return new Vector3d(n.cell().x()+.5,n.cell().y()+.5-.2775,n.cell().z()+.5);}
 static Vector3d port(Node n){var p=n.ports().getFirst();return centre(n).add(p.x()*.5,p.y()*.5,p.z()*.5);}
 static Vector3d feet(Node n) {return new Vector3d(n.cell().x()+.5,n.cell().y()-2+.01,n.cell().z()+.5);}
 boolean uses(Cell c) {return cells.contains(c);}
}

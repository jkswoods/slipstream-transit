package dev.slipstream;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import java.util.*;
import static dev.slipstream.TransitGraph.*;
final class Ride {
 final Ref<EntityStore> ref;final PlayerRef player;final List<Node> route;final List<Vector3d> path=new ArrayList<>();
 final List<Double> lengths=new ArrayList<>();double total,progress;float elapsed;float shrink;final Vector3d start;int pose=-999;
 Ride(Ref<EntityStore> ref,PlayerRef p,List<Node> route,Vector3d actual) {
  this.ref=ref;start=new Vector3d(actual);player=p;this.route=route;path.add(new Vector3d(actual));
  var first=route.getFirst();var firstCentre=centre(first);var firstMouth=new Vector3d(firstCentre.x,first.cell().y()+.02-.2775,firstCentre.z);path.add(firstMouth);
  var firstPort=port(first);for(int j=1;j<=8;j++){double t=j/8.;path.add(new Vector3d(firstMouth).mul((1-t)*(1-t)).fma(2*t*(1-t),firstCentre).fma(t*t,firstPort));}
  for(int i=1;i<route.size()-1;i++) {
   Node n=route.get(i);var centre=centre(n);var before=centre(route.get(i-1));var after=centre(route.get(i+1));
   var a=new Vector3d(centre).lerp(before,.5);var b=new Vector3d(centre).lerp(after,.5);path.add(a);
   for(int j=1;j<=16;j++) {double t=j/16.;path.add(new Vector3d(a).mul((1-t)*(1-t)).fma(2*t*(1-t),centre).fma(t*t,b));}
  }
  if(route.size()>1){var last=route.getLast();var c=centre(last);var mouth=new Vector3d(c.x,last.cell().y()+.02-.2775,c.z);var port=port(last);for(int j=1;j<=8;j++){double t=j/8.;path.add(new Vector3d(port).mul((1-t)*(1-t)).fma(2*t*(1-t),c).fma(t*t,mouth));}path.add(feet(last));}lengths.add(0.);for(int i=1;i<path.size();i++){total+=path.get(i-1).distance(path.get(i));lengths.add(total);}
 }
 Vector3d at(double distance) {distance=Math.max(0,Math.min(total,distance));for(int i=1;i<path.size();i++)if(lengths.get(i)>=distance){double span=lengths.get(i)-lengths.get(i-1);return new Vector3d(path.get(i-1)).lerp(path.get(i),span<1e-7?1:(distance-lengths.get(i-1))/span);}return new Vector3d(path.getLast());}
 double nearest(Vector3d pos) {double best=Double.MAX_VALUE,found=progress;for(int i=1;i<path.size();i++){if(lengths.get(i)<progress-.5||lengths.get(i-1)>progress+3)continue;var a=path.get(i-1);var ab=new Vector3d(path.get(i)).sub(a);double den=ab.lengthSquared();if(den<1e-8)continue;double t=Math.max(0,Math.min(1,new Vector3d(pos).sub(a).dot(ab)/den));double distance=new Vector3d(a).fma(t,ab).distanceSquared(pos);if(distance<best){best=distance;found=lengths.get(i-1)+t*Math.sqrt(den);}}return Math.max(progress,found);}
 Vector3d direction(double distance) {var v=at(Math.min(total,distance+.09)).sub(at(Math.max(0,distance-.05)));return v.lengthSquared()<1e-8?new Vector3d(0,0,-1):v.normalize();}
 static Vector3d centre(Node n) {return new Vector3d(n.cell().x()+.5,n.cell().y()+.5-.2775,n.cell().z()+.5);}
 static Vector3d port(Node n){var p=n.ports().getFirst();return centre(n).add(p.x()*.5,p.y()*.5,p.z()*.5);}
 static Vector3d feet(Node n) {return new Vector3d(n.cell().x()+.5,n.cell().y()-2+.01,n.cell().z()+.5);}
 boolean uses(Cell c) {return route.stream().anyMatch(n->n.cell().equals(c));}
}

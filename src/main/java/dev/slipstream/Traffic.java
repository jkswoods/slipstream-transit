package dev.slipstream;

import java.util.*;
import static dev.slipstream.TransitGraph.*;

/** World-thread route reservations. Compatible convoys share only identical directed paths.
 * Conflicting junction turns/merges and opposing traffic wait outside the tube, avoiding deadlocks. */
final class Traffic {
 record Turn(Cell before,Cell after){}
 static final class Reservation {
  final List<Node> route;final Map<Cell,Turn> turns=new HashMap<>();
  Reservation(List<Node> path){route=List.copyOf(path);for(int i=0;i<route.size();i++)turns.put(route.get(i).cell(),new Turn(i==0?null:route.get(i-1).cell(),i==route.size()-1?null:route.get(i+1).cell()));}
 }
 final Map<UUID,Reservation> reservations=new LinkedHashMap<>();
 final Map<Cell,UUID> landings=new HashMap<>();
 static boolean conflicts(List<Node> a,List<Node>b){var next=new Reservation(a);var old=new Reservation(b);for(var t:next.turns.entrySet()){var turn=old.turns.get(t.getKey());if(turn!=null&&!turn.equals(t.getValue()))return true;}return false;}
 boolean compatible(List<Node> route,UUID rider) {for(var e:reservations.entrySet())if(!e.getKey().equals(rider)&&conflicts(route,e.getValue().route))return false;return true;}
 boolean reserve(UUID rider,List<Node> route){if(reservations.containsKey(rider))return false;if(reservations.size()>=TransitLimits.RIDERS||!compatible(route,rider))return false;reservations.put(rider,new Reservation(route));return true;}
 void releaseRoute(UUID rider){reservations.remove(rider);}
 void release(UUID rider){releaseRoute(rider);landings.values().removeIf(rider::equals);}
 boolean claimLanding(UUID rider,Cell at){UUID old=landings.get(at);if(old!=null&&!old.equals(rider))return false;landings.put(at,rider);return true;}
 boolean landingFree(UUID rider,Cell at){UUID old=landings.get(at);return old==null||old.equals(rider);}
 boolean occupied(Cell c){return reservations.values().stream().anyMatch(r->r.turns.containsKey(c))||landings.containsKey(c);}
}

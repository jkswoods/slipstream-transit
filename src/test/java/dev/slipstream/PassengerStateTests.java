package dev.slipstream;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.codec.EmptyExtraInfo;
import java.util.Arrays;
/** Original settings survive a serialized, interrupted trip, including non-default player speeds. */
public final class PassengerStateTests {
 public static void main(String[] args){
  var m=new MovementSettings();m.baseSpeed=4.25f;m.horizontalFlySpeed=11.5f;m.verticalFlySpeed=8.75f;m.jumpForce=12.3f;m.swimJumpForce=4.2f;m.climbSpeed=3.5f;m.climbSpeedLateral=2.5f;m.climbUpSprintSpeed=5.5f;m.climbDownSprintSpeed=6.5f;m.mass=73;m.dragCoefficient=.63f;m.airControlMaxMultiplier=.41f;
  var original=new MovementSettings(m);var saved=new TransitPassenger();saved.world="Test";saved.inputSpeeds=PassengerControls.capture(m);
  PassengerControls.lock(m);assert Arrays.equals(PassengerControls.capture(m),new float[9]);assert m.mass==73&&m.dragCoefficient==.63f&&m.airControlMaxMultiplier==.41f;
  var encoded=TransitPassenger.CODEC.encode(saved,EmptyExtraInfo.EMPTY);var reloaded=TransitPassenger.CODEC.decode(encoded,EmptyExtraInfo.EMPTY);PassengerControls.apply(m,reloaded.inputSpeeds);assert m.equals(original):"Original movement fields changed after serialized recovery";
  encoded.remove("InputSpeeds");var legacy=TransitPassenger.CODEC.decode(encoded,EmptyExtraInfo.EMPTY);PassengerControls.apply(m,legacy.inputSpeeds);assert m.equals(original):"Legacy recovery changed input settings";
  var world=new TransitWorld();var first=java.util.UUID.randomUUID();var second=java.util.UUID.randomUUID();var at=new TransitGraph.Cell(8,82,8);var away=new TransitGraph.Cell(519,82,8);
  world.returns.put(first,at);world.returns.put(second,away);world.flightRestore.put(first,com.hypixel.hytale.protocol.FlyMode.Allowed);world.flightRestore.put(second,com.hypixel.hytale.protocol.FlyMode.Disabled);world.names.put(at,"Original stop");
  var worldEncoded=TransitWorld.CODEC.encode(world,EmptyExtraInfo.EMPTY);var worldReloaded=TransitWorld.CODEC.decode(worldEncoded,EmptyExtraInfo.EMPTY);assert worldReloaded.returns.equals(world.returns)&&worldReloaded.flightRestore.equals(world.flightRestore)&&worldReloaded.names.equals(world.names):"Per-rider return state changed";
  worldReloaded.returns.remove(first);worldReloaded.flightRestore.remove(first);assert worldReloaded.returns.containsKey(second)&&worldReloaded.flightRestore.get(second)==com.hypixel.hytale.protocol.FlyMode.Disabled:"One recovery erased another rider";assert world.returns.size()==2:"Decoded resources share mutable recovery state";
  System.out.println("Passenger settings passed: custom speeds locked, unrelated physics preserved, serialized recovery restores exact settings, legacy marker accepted.");
 }
}

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
  System.out.println("Passenger settings passed: custom speeds locked, unrelated physics preserved, serialized recovery restores exact settings, legacy marker accepted.");
 }
}

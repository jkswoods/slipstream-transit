package dev.slipstream;
import com.hypixel.hytale.protocol.MovementSettings;
/** Only wish/input speeds are disabled. Native external velocity still drives the passenger. */
final class PassengerControls {
 static float[] capture(MovementSettings m){return new float[]{m.baseSpeed,m.horizontalFlySpeed,m.verticalFlySpeed,m.jumpForce,m.swimJumpForce,m.climbSpeed,m.climbSpeedLateral,m.climbUpSprintSpeed,m.climbDownSprintSpeed};}
 static void apply(MovementSettings m,float[] v){if(v.length!=9)return;m.baseSpeed=v[0];m.horizontalFlySpeed=v[1];m.verticalFlySpeed=v[2];m.jumpForce=v[3];m.swimJumpForce=v[4];m.climbSpeed=v[5];m.climbSpeedLateral=v[6];m.climbUpSprintSpeed=v[7];m.climbDownSprintSpeed=v[8];}
 static void lock(MovementSettings m){apply(m,new float[9]);}
}

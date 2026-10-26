package dev.slipstream;

/** Restart-only server limits. Saved registries always decode up to the format's hard ceiling. */
final class TransitLimits {
 static final int SAVED_PARTS=16384;
 static final int WORLD_PARTS=integer("worldParts",8192,128,SAVED_PARTS);
 static final int JOURNEY_PARTS=integer("journeyParts",512,64,1024);
 static final int RIDERS=integer("riders",8,2,16);
 static final int ROUTE_SECTIONS=integer("routeSections",96,16,128);
 static final int WORLD_SECTIONS=integer("worldSections",256,128,512);
 static final int WAITERS=32;
 static final double HEADWAY=4.0;
 static final double WAIT_SECONDS=120,LOAD_SECONDS=30,STALL_SECONDS=20;
 static int integer(String name,int fallback,int min,int max) {
  String value=System.getProperty("slipstream."+name);if(value==null)return fallback;
  int n;try{n=Integer.parseInt(value);}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid slipstream."+name,e);}
  if(n<min||n>max)throw new IllegalArgumentException("slipstream."+name+" must be "+min+".."+max);return n;
 }
 private TransitLimits(){}
}

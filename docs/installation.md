# Installation and compatibility

**Target: Hytale 0.7.3 · Java 25 · Development preview**

There is no published binary release yet. Build the source with your own official Hytale server JAR, or use the versioned test JAR supplied for this preview.

## Build from source

```sh
export ST_SERVER_JAR=/path/to/HytaleServer.jar
export ST_JAVA_HOME=/path/to/jdk-25
python3 tools/build.py
python3 tools/check.py
```

The build verifies the pinned server JAR and writes `SlipstreamTransit-<version>.jar` under `build/libs/`. The server JAR and game assets are not bundled.

## Try it safely

1. Create a separate test world and close it normally.
2. Keep a complete backup of the save, including player data.
3. Put the built JAR in that save's `mods/` folder and enable **SlipstreamTransit:SlipstreamTransit** for the world.
4. Reopen the world, then [craft the Pipe Workbench](getting-started.md).

Do not install or replace a JAR while its world is running. The preview has been exercised in Creative; survival ingredient deduction and real second-client multiplayer remain unverified.

## Updating or removing

Save and exit before changing versions. Keep the previous JAR alongside its matching full save backup. Do not downgrade a current save onto an older prototype build.

Finish travel and remove Slipstream blocks before removing the mod from a test world. If returning to a backup, restore its matching world, players and JAR together. Do not remove the mod while a player is inside a tube.

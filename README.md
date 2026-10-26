# Slipstream Transit

[Player docs](https://jkswoods.github.io/slipstream-transit/) · [Development releases](https://github.com/jkswoods/slipstream-transit/releases)

![Docs build](https://github.com/jkswoods/slipstream-transit/actions/workflows/docs.yml/badge.svg?branch=docs)

![Slipstream Transit — Take the tube](docs/assets/banner-cinematic-v2.png)

Travel through colourful passenger tube networks with automatic boarding, branching routes and named destinations.

![White straight intake feeding a gold junction and two coloured stations, captured in Hytale](docs/assets/screenshots/network-overview.png)

- Automatic suction, a smooth shrink transition and a camera facing the journey.
- One-block straights, corners, verticals and rising or descending elbows.
- Three-way splitters, named stops and a destination choice when several stops are reachable.
- Six mix-and-match colours, a Pipe Workbench and warning-striped open mouths.
- Locked movement during travel, with normal size, appearance and controls restored afterward.

[Player guide](https://jkswoods.github.io/slipstream-transit/hytale0.7/) · [Build your first route](https://jkswoods.github.io/slipstream-transit/hytale0.7/getting-started/) · [Recipes](https://jkswoods.github.io/slipstream-transit/hytale0.7/recipes/) · [Screenshots](https://jkswoods.github.io/slipstream-transit/hytale0.7/gallery/) · [Known limits](https://jkswoods.github.io/slipstream-transit/hytale0.7/troubleshooting/)

## Development status

This README describes the `hytale0.7` support branch.

Targets **Hytale 0.7.3 and Java 25**. This development branch adds up to eight reserved passenger journeys per world, 8,192 registered parts and journeys of up to 512 parts by default. Same-direction riders share a route with headway; opposing and conflicting junction traffic waits. Routes temporarily retain their chunk corridor beyond the starting view and require a single builder. Read each download’s release notes for included features. Other players may ride; a real second-client session and survival ingredient deduction still need verification. Keep a complete backup and use a test world.

Screenshots show Hytale 0.7 development gameplay in a separate showcase copy. They are native game captures, cropped to the feature or UI. No world or player save is included.

## Build

Provide your own official Hytale 0.7.3 server JAR and JDK 25. The proprietary server and game assets are not redistributed.

```sh
export ST_SERVER_JAR=/path/to/HytaleServer.jar
export ST_JAVA_HOME=/path/to/jdk-25
python3 tools/build.py
python3 tools/check.py
```

The JAR is written to `build/libs/`. See [installation](https://jkswoods.github.io/slipstream-transit/hytale0.7/installation/) before trying it in a world.

## Documentation

The [versioned player guide](https://jkswoods.github.io/slipstream-transit/) is maintained on the separate [`docs` branch](https://github.com/jkswoods/slipstream-transit/tree/docs). In that checkout, run `npm ci --ignore-scripts`, `npm run build` and `npm run check`. Select `docs` when manually dispatching its Pages workflow.
Pushes to `docs` also publish this guide; contributor PRs validate without deploying.

Choose a preview compatible with your installed Hytale version from [Releases](https://github.com/jkswoods/slipstream-transit/releases). Code and original project assets are available under the [MIT licence](LICENSE); see [third-party notices](THIRD_PARTY_NOTICES.md).

## Compatibility and builds

Current source targets Hytale `>=0.7.3 <0.8.0` and uses the `hytale0.7` support branch. See [build and release instructions](https://jkswoods.github.io/slipstream-transit/hytale0.7/build-and-release/) for the SDK pin, candidate checks, versioned filenames and CurseForge workflow.

## Contributing

See [the contribution guide](CONTRIBUTING.md) for feature and bug-fix PR targets.
Maintainers handle forward/back ports, and support-branch PRs require owner approval.

The player guide follows the Hytale support line and is updated independently of mod releases. Select downloads by their game compatibility and read the selected file’s release notes for included changes and artifact checksums.

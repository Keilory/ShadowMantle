# ShadowMantle

Client-side Fabric QoL mod for Minecraft 1.21.10, focused on Hypixel-style SkyBlock servers rather than a single server implementation.

ShadowMantle provides focused utilities that make common SkyBlock activities easier to read, calculate, and manage. Features are modular and can be enabled or disabled independently for distribution builds.

## Features

- Bazaar scanner and tooltip
- Dungeon chest calculator
- Dungeon mob glow

## Requirements

- Minecraft 1.21.10
- Fabric Loader 0.17.2
- Fabric API 0.138.4+1.21.10
- Java 21

## Development

Clone the repository, use Java 21, and run the Fabric development client through the Gradle Wrapper.

Linux/macOS:

```bash
./gradlew runClient
```

Windows:

```bat
gradlew.bat runClient
```

For a Windows convenience launcher:

```text
scripts/windows/start-shadowmantle.bat
```

## Distribution builds

`build_config.py` provides an interactive feature configurator for distribution builds. The default profile enables all supported features.

```bash
python build_config.py
```

The generated JAR is written to `dist/`. Build profiles are named according to the features they contain, for example `full` or `no-dungeon-glow`.

## Mod testing client

The repository includes a separate production-style Fabric client for testing external Fabric mods without loading ShadowMantle's development environment.

Place test mods in:

```text
test-client/mods/
```

Then launch the test client on Windows with:

```bat
gradlew.bat prodTestClient
```

Only JAR files from `test-client/mods/` are passed to the test client. The test client keeps its runtime data under `test-client/`, separate from the normal development `run/` directory.

## Configuration

ShadowMantle stores persistent data under:

```text
.minecraft/config/shadowmantle/
```

## License

ShadowMantle is licensed under the MIT License. See [LICENSE](LICENSE).

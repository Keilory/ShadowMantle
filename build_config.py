from __future__ import annotations

import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parent
OUTPUT_DIR = ROOT / "dist"
TEST_CLIENT_MODS = ROOT / "test-client" / "mods"

FEATURES = {
    "bazaar": "Bazaar scanner / tooltip",
    "dungeon_calculator": "Dungeon chest calculator",
    "dungeon_mob_glow": "Dungeon mob glow",
}

DEPENDENCIES = {
    "bazaar": set(),
    "dungeon_calculator": set(),
    "dungeon_mob_glow": set(),
}


def resolve_dependencies(enabled: dict[str, bool]) -> dict[str, bool]:
    resolved = dict(enabled)
    changed = True
    while changed:
        changed = False
        for feature, dependencies in DEPENDENCIES.items():
            if not resolved[feature]:
                continue
            for dependency in dependencies:
                if not resolved[dependency]:
                    print(
                        f"[WARNING] {FEATURES[feature]} depends on "
                        f"{FEATURES[dependency]}. Enabling dependency."
                    )
                    resolved[dependency] = True
                    changed = True
    return resolved


def ask_features() -> dict[str, bool]:
    enabled = {name: True for name in FEATURES}

    while True:
        print("\n========================================")
        print("ShadowMantle Modrinth build configurator")
        print("========================================")
        for index, (name, description) in enumerate(FEATURES.items(), 1):
            state = "ON " if enabled[name] else "OFF"
            print(f"  {index}. [{state}] {description}")
        print("\nEnter a number to toggle a feature.")
        print("Type 'all' to enable everything.")
        print("Type 'build' to build the selected profile.")
        print("Type 'q' to cancel.")

        value = input("> ").strip().lower()
        if value in {"q", "quit", "exit"}:
            raise SystemExit(0)
        if value == "all":
            enabled = {name: True for name in FEATURES}
            continue
        if value in {"build", "b"}:
            return resolve_dependencies(enabled)
        if value.isdigit():
            index = int(value) - 1
            names = list(FEATURES)
            if 0 <= index < len(names):
                name = names[index]
                enabled[name] = not enabled[name]
            else:
                print("Unknown feature number.")
            continue
        print("Unknown command.")


def copy_project(stage: Path) -> None:
    ignored = shutil.ignore_patterns(
        ".git", ".gradle", "build", "run", "dist", ".idea", "*.class"
    )
    shutil.copytree(ROOT, stage, ignore=ignored)


def rewrite_shadowmantle_client(stage: Path, enabled: dict[str, bool]) -> None:
    path = stage / "src/main/java/com/keilory/shadowmantle/ShadowMantleClient.java"
    needs_database = enabled["bazaar"] or enabled["dungeon_calculator"]

    imports = [
        "package com.keilory.shadowmantle;",
        "",
        "import com.keilory.shadowmantle.core.ShadowMantleCore;",
        "import net.fabricmc.api.ClientModInitializer;",
        "import net.minecraft.client.MinecraftClient;",
        "import org.slf4j.Logger;",
        "import org.slf4j.LoggerFactory;",
    ]

    if needs_database:
        imports.insert(2, "import com.keilory.shadowmantle.bazaar.BazaarPriceDatabase;")
        imports.append("import java.sql.SQLException;")
    if enabled["bazaar"]:
        imports.insert(3, "import com.keilory.shadowmantle.core.feature.BazaarFeature;")
    if enabled["dungeon_calculator"]:
        imports.insert(3, "import com.keilory.shadowmantle.core.feature.DungeonFeature;")
    if enabled["dungeon_mob_glow"]:
        imports.insert(3, "import com.keilory.shadowmantle.core.feature.DungeonMobGlowFeature;")

    fields = ["    private static ShadowMantleCore core;"]
    if needs_database:
        fields.append("    private static BazaarPriceDatabase database;")

    body = [
        "public final class ShadowMantleClient implements ClientModInitializer {",
        '    public static final String MOD_ID = "shadowmantle";',
        '    public static final String PLAYER_NAME = "Keilory";',
        '    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);',
        *fields,
        "",
        "    public static ShadowMantleCore core() { if (core == null) throw new IllegalStateException(); return core; }",
        "",
        "    @Override",
        "    public void onInitializeClient() {",
        "        MinecraftClient client = MinecraftClient.getInstance();",
        "        core = ShadowMantleCore.initialize(client);",
    ]

    if needs_database:
        body += [
            "",
            "        try {",
            "            database = BazaarPriceDatabase.open(client);",
        ]
        if enabled["bazaar"]:
            body.append("            core.registerFeature(new BazaarFeature(database));")
        if enabled["dungeon_calculator"]:
            body.append("            core.registerFeature(new DungeonFeature(database));")
        if enabled["dungeon_mob_glow"]:
            body.append("            core.registerFeature(new DungeonMobGlowFeature());")
        body += [
            '            LOGGER.info("ShadowMantle initialized");',
            "        } catch (SQLException error) {",
            '            LOGGER.error("Failed to initialize database", error);',
            "        }",
        ]
    elif enabled["dungeon_mob_glow"]:
        body += [
            "",
            "        core.registerFeature(new DungeonMobGlowFeature());",
            '        LOGGER.info("ShadowMantle initialized");',
        ]
    else:
        body += [
            "",
            '        LOGGER.info("ShadowMantle initialized");',
        ]

    body += ["    }", "}"]
    path.write_text("\n".join(imports) + "\n\n" + "\n".join(body) + "\n", encoding="utf-8")


def prune_sources(stage: Path, enabled: dict[str, bool]) -> None:
    java_root = stage / "src/main/java/com/keilory/shadowmantle"

    def remove(relative: str) -> None:
        target = java_root / relative
        if target.is_file():
            target.unlink()

    if not enabled["bazaar"]:
        remove("core/feature/BazaarFeature.java")

    if not enabled["dungeon_calculator"]:
        for path in (
            "core/feature/DungeonFeature.java",
            "bazaar/DungeonChestCalculator.java",
            "bazaar/DungeonChestOverlay.java",
            "bazaar/DungeonChestParser.java",
            "bazaar/DungeonChestState.java",
            "bazaar/DungeonChestStateCapture.java",
        ):
            remove(path)

    if not enabled["dungeon_mob_glow"]:
        remove("core/feature/DungeonMobGlowFeature.java")
        remove("core/feature/DungeonMobGlow.java")
        remove("mixin/EntityGlowMixin.java")

    if not enabled["bazaar"] and not enabled["dungeon_calculator"]:
        bazaar_dir = stage / "src/main/java/com/keilory/shadowmantle/bazaar"
        if bazaar_dir.exists():
            shutil.rmtree(bazaar_dir)


def rewrite_mixins(stage: Path, enabled: dict[str, bool]) -> None:
    path = stage / "src/main/resources/shadowmantle.mixins.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    mixins = data.get("client", [])

    if not enabled["dungeon_mob_glow"]:
        mixins = [name for name in mixins if name != "EntityGlowMixin"]

    data["client"] = mixins
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def build(stage: Path, enabled: dict[str, bool]) -> Path:
    rewrite_shadowmantle_client(stage, enabled)
    prune_sources(stage, enabled)
    rewrite_mixins(stage, enabled)

    gradle = stage / "gradlew.bat"
    result = subprocess.run(
        ["cmd", "/c", str(gradle), "clean", "build", "--no-daemon"],
        cwd=stage,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError("Gradle build failed.")

    jars = sorted((stage / "build/libs").glob("*.jar"))
    jars = [jar for jar in jars if "sources" not in jar.name]
    if not jars:
        raise FileNotFoundError("No output JAR found in build/libs.")
    return jars[0]


def profile_name(enabled: dict[str, bool]) -> str:
    if all(enabled.values()):
        return "full"

    disabled = [name for name, value in enabled.items() if not value]
    names = {
        "bazaar": "no-bazaar",
        "dungeon_calculator": "no-dungeon-calculator",
        "dungeon_mob_glow": "no-dungeon-glow",
    }
    return "-".join(names[name] for name in disabled)


def cleanup_test_client_mods() -> None:
    if not TEST_CLIENT_MODS.exists():
        return

    removed = 0
    for jar in TEST_CLIENT_MODS.glob("shadowmantle*.jar"):
        jar.unlink()
        removed += 1

    if removed:
        print(f"Removed {removed} old ShadowMantle JAR(s) from test-client/mods.")


def install_test_client_jar(output: Path) -> None:
    TEST_CLIENT_MODS.mkdir(parents=True, exist_ok=True)
    cleanup_test_client_mods()
    shutil.copy2(output, TEST_CLIENT_MODS / output.name)


def print_build_summary(enabled: dict[str, bool], output: Path) -> None:
    print("\n========================================")
    print("BUILD COMPLETE")
    print("========================================")
    print("\nEnabled:")
    for name, description in FEATURES.items():
        if enabled[name]:
            print(f"  {description}")

    print("\nDisabled:")
    disabled = [description for name, description in FEATURES.items() if not enabled[name]]
    if disabled:
        for description in disabled:
            print(f"  {description}")
    else:
        print("  None")

    print("\nOutput:")
    print(f"  {output}")
    print(f"  {TEST_CLIENT_MODS / output.name}")
    print("========================================")


def main() -> None:
    enabled = ask_features()

    print("\nBuild profile:")
    for name, description in FEATURES.items():
        print(f"  {'ON ' if enabled[name] else 'OFF'} - {description}")

    OUTPUT_DIR.mkdir(exist_ok=True)
    profile = profile_name(enabled)
    output = OUTPUT_DIR / f"shadowmantle-0.0.1-{profile}.jar"

    with tempfile.TemporaryDirectory(prefix="shadowmantle-build-") as temp:
        stage = Path(temp) / "project"
        print("\nPreparing isolated build...")
        copy_project(stage)
        jar = build(stage, enabled)
        shutil.copy2(jar, output)

    install_test_client_jar(output)
    print_build_summary(enabled, output)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\nCancelled.")
        sys.exit(130)
    except Exception as error:
        print(f"\n[ERROR] {error}")
        sys.exit(1)

# Create: Crystal Industry

**机械动力：晶簇工业** · Minecraft 1.21.1 · NeoForge · Create 6.0.10+

**English | [简体中文](README.md)**

Vanilla's amethyst geode, extended to an entire industrial system.

Stop strip-mining. Find a budding block, and let it grow.

---

## 1. Budding Blocks

Every budding block grows **Small Bud → Medium Bud → Large Bud → Cluster** from all six faces, exactly like Budding Amethyst (a 1-in-5 chance per random tick — same speed as vanilla). The budding block itself is never consumed; harvest the cluster and it starts over.

Growth speed comes in four tiers — **Very Slow (1/50), Slow (1/20), Normal (1/5), Fast (1/1)** — all defaulting to Normal. Which budding block sits in which tier is assigned by id in the config file, so you can make just a few of them faster or slower.

| Budding Block | Extra Growth Condition |
| --- | --- |
| Raw Iron, Raw Gold, Raw Copper, Raw Zinc, Diamond, Emerald, Lapis Lazuli, Redstone, Quartz | — |
| Glowstone, Rose Quartz | — |
| Flammable Ice | Buds must be submerged in water; growth stops the moment they leave it |
| Echo | Growth space must be at light level 0 |
| Fluix *(requires AE2)* | Must be on a powered, active ME Grid; each growth consumes AE |

> Budding Redstone emits a redstone signal of 15 — it works directly as a redstone source.

### Budding Blocks Are Renewable

This is the important part: an ore budding block has a **1-in-20** chance per random tick to convert stone/deepslate within a 3×3×3 area into the **matching ore**. At a much lower chance, it can also "infect" the matching raw ore block, turning it into another budding block of the same kind. Budding Quartz erodes diorite; Budding Echo converts dirt, stone, tuff and more into Sculk.

**One budding block is a self-expanding ore vein.**

---

## 2. Accelerators: From Minutes to Moments

A vanilla budding block only receives a random tick once every ~68 seconds on average, which makes natural growth almost negligible. An Accelerator forces **one random tick onto each of its six neighbouring blocks, every single tick**.

- **Electric Accelerator** — runs on FE; adjacent Accelerators balance power between themselves automatically
- **Mechanical Accelerator** — takes Rotational Force at its back; the faster the input, the faster it works, up to 256 RPM (at full speed it matches the electric one)

Both accelerators' **acceleration interval** is configurable (`Acceleration Interval (ticks)`, default 1 = every tick; larger = slower). The electric one pays FE per pass, so slowing it down also saves power. The Goggles multiplier follows the config.

> ⚠️ Random ticks drive **far more than crystals**: crops, saplings, copper oxidation, nether wart — anything random-tick based gets accelerated too. **It is a general-purpose accelerator**, and crystals are simply the most profitable thing to point it at.

---

## 3. Machines & Equipment

- **Smart Drill** — two modes. Normal Harvesting mines **twice as fast** as a regular Mechanical Drill; Silk Touch Harvesting mines at normal speed but **collects the budding block itself** — one of the legitimate ways to obtain budding blocks
- **Mechanical Cleaner** — does everything an Encased Fan's airstream can do, and collects items caught in its stream into its own inventory. Place a container directly in front and it **moves items in and out of it directly, without ever dropping anything into the world**. Blowing/sucking, filters and a range of up to 20 blocks are all configurable
- **Night Vision Goggles** — Create Goggles plus Echo Shards; toggle night vision with a keybind. Curios head slot supported
- **Echo Spyglass** — while in use, see through obstacles with blocks rendered as outlines; accepts a filter item and detects ores by default
- **Flammable Ice** — excellent fuel, and a favourite snack of Blazes

---

## 4. World Generation

Budding blocks generate at the depths of the ore they correspond to, usually **embedded inside ore veins** (Budding Diamond -64~16, Budding Raw Iron -24~56, Budding Raw Copper -16~112, Budding Raw Zinc -63~70, Budding Lapis Lazuli -64~64, Budding Redstone -63~15, and so on). Budding Glowstone appears at the bottom of naturally generated glowstone blobs, and Budding Flammable Ice only generates on the seafloor of deep oceans.

**Every one of these can be toggled individually in the config file**, along with Flammable Ice rarity, the Glowstone budding replacement chance and the budding growth speed tiers.

> ⚠️ **Naturally generated Budding Echo summons a Warden when broken.** Ones you place yourself will not.

---

## 5. Compatibility

- **Required**: Create 6.0.10+
- **Optional**: AE2 (enables Budding Fluix), Curios (goggles in the head slot), JEI
- **Without AE2 the mod starts normally** — it simply does not register the Fluix content
- **AE2's Crystal Growth Accelerator also speeds up this mod's budding blocks** (it applies random ticks at its own configured interval); the Goggles growth multiplier counts it too

Every machine ships with a Create-style Goggles info panel (growth status, work speed, growth multiplier) and **Ponder scenes**.

---

## Development: Adding a Budding Family & Data Generation

Everything that defines a budding family (light/water/energy requirements, growth rule, block
conversion, light levels, sounds, drops) lives in one table:
`src/main/java/com/minecart/yunxian/budding/BuddingFamilies.java` — **adding a family means adding
one entry there**. Block registration, growth logic, goggles tooltips, the creative tab, the world-gen
config flag and Ponder registration are all derived from it.
The one exception is growth speed: it is a global four-tier setting (`BuddingFamily.GrowthSpeed`)
driven by four id lists in the config file, and the default members of the NORMAL tier are derived
from this table too — a new family needs no extra configuration.

Generate the JSON assets with `./gradlew runData`. **AE2 must be present in `run/mods`** (the run
aborts otherwise, to avoid treating the already-generated Fluix assets as stale and deleting them).
Output goes to `src/generated/resources`, which is committed. These are now generated — do not
hand-write them: `blockstates/`, `models/block/`, `models/item/`, the budding and bud loot tables,
the `c:budding_blocks` tags, and the `mineable/pickaxe` / `needs_*_tool` tags.

Still hand-written: textures (and `.mcmeta`), the cluster and Fluix loot tables (their structure and
mod conditions cannot be reproduced faithfully by the generator), world-gen JSON, and lang files.

---

## License

**This mod may only be included in modpacks published on CurseForge, with no further permission required** — public or private, free or monetized.

The pack must use **CurseForge's standard packaging method**: reference this mod in `manifest.json` by its CurseForge project ID and file ID, so the launcher downloads it from CurseForge. **Bundling the jar inside the pack archive is not allowed**, and neither is publishing the pack on any platform other than CurseForge. The condition is attribution: credit **YunXian_LI** and link back to the official download page.

See [LICENSE.txt](LICENSE.txt) for the full terms.

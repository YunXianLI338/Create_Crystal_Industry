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
Modpack authors can also add budding blocks of their own with **KubeJS**, as many as they like (see "Adding Your Own Budding Blocks (KubeJS)" below).

> ⚠️ **Naturally generated Budding Echo summons a Warden when broken.** Ones you place yourself will not.

---

## 5. Compatibility

- **Required**: Create 6.0.10+
- **Optional**: AE2 (enables Budding Fluix), Curios (goggles in the head slot), JEI, KubeJS (add your own budding blocks from a script)
- **Without AE2 the mod starts normally** — it simply does not register the Fluix content
- **AE2's Crystal Growth Accelerator also speeds up this mod's budding blocks** (it applies random ticks at its own configured interval); the Goggles growth multiplier counts it too

Every machine ships with a Create-style Goggles info panel (growth status, work speed, growth multiplier) and **Ponder scenes**.

---

## Adding Your Own Budding Blocks (KubeJS)

The growth engine is **public**: with **KubeJS** you can add budding blocks of your own — **as many as you like**,
without writing Java and without a datapack. Even better, **one line registers the whole family** — the budding
block, the small/medium/large buds and the cluster, five blocks at once.

```js
StartupEvents.registry('block', event => {
  CustomBudding.create(event, 'example_crystal', 20)   // 1-in-20 chance
})
```

That produces `kubejs:example_crystal_budding` plus `_small_bud` / `_medium_bud` / `_large_bud` / `_cluster`;
the budding block's random ticks are already wired to the growth engine, and the buds/cluster carry the `FACING`
property (so they grow pointing at the budding block). The `id` may include a namespace
(`'mypack:example_crystal'`); without one it lands in the `kubejs` namespace.

For more control, pass an options object (read once, at call time):

```js
const opts = new CustomBuddingOptions()
opts.chance = 20            // 1-in-n per random tick
opts.maxLight = 7           // light limit for the growth spot (negative = unlimited)
opts.requiresWater = false  // true = flammable-ice style, the target block must be water
opts.displayName = 'Example Budding Block'
opts.dropItem = 'mypack:my_shard'      // dropped by the cluster on a normal break (silk touch always drops the cluster itself)
opts.dropCount = 2                     // how many of it, default 1
opts.buddingTexture = 'mypack:block/my_crystal'        // budding block texture
opts.stageTextures = ['mypack:block/my_small_bud', 'mypack:block/my_medium_bud',
                      'mypack:block/my_large_bud', 'mypack:block/my_cluster']
const family = CustomBudding.create(event, 'mypack:example_crystal', opts)
```

The options can also be chained — hang the whole chain off `create`'s third argument (the two styles are
equivalent and can be mixed):

```js
CustomBudding.create(event, 'mypack:example_crystal', new CustomBuddingOptions()
  .chance(20)
  .requiresWater()
  .maxLight(0)
  .dropItem('minecraft:amethyst_shard', 2))
```

The chain **has to sit inside the call**: options are read once, when `create` runs, so
`CustomBudding.create(event, id).chance(20)` does nothing — the blocks are already built by then.

- **Goggles**: point Create Goggles at your budding block and the panel shows the current growth speed plus the growth chance / light requirement / water requirement you configured (this mod's own families only show the speed line).
- **The default textures are vanilla amethyst's**, so it works with zero assets; override the textures above or
  ship a resource pack for your own art.
- The registered blocks go into the **creative "KubeJS" tab** by default (KubeJS blocks land in no tab at all
  otherwise, which makes it look like registration failed). `opts.group` can point at a vanilla tab
  (`'building_blocks'`, …); set it to `null` to keep them out of every tab (then only `/give` gets them).
- **Drops**: buds drop nothing unless you use silk touch (which drops the bud itself); the cluster drops `opts.dropItem` × `opts.dropCount` (nothing by default) on a normal break, and itself with silk touch — same behaviour as this mod's own buds/cluster.
- The returned `family` holds the five block ids (`family.budding`, `family.cluster`, …) so you can keep going with
  recipes, tags and loot tables.
- **Tags are automatic**: the budding block joins `#c:budding_blocks` (block + item) and all five blocks join `#minecraft:mineable/pickaxe` — so the Smart Drill's silk-touch mode harvests your budding block itself and AE2's Crystal Growth Accelerator speeds it up. No tags to write by hand.
- Names and looks come from a resource pack / lang; drops are handled by this mod as described above.

### Complete example (copy & run)

Drop this into `kubejs/startup_scripts/` (any file name, as long as it ends in `.js`) and you're done — no
textures, no resource pack: the budding block and its buds/cluster use vanilla amethyst's textures by default.

```js
// kubejs/startup_scripts/my_crystal.js
StartupEvents.registry('block', event => {
  const opts = new CustomBuddingOptions()
  opts.chance = 20                        // 1-in-20 per random tick to advance one stage (default 5)
  opts.maxLight = 7                       // light limit for the growth spot, 0-15; negative = unlimited (default -1)
  opts.requiresWater = false              // true = flammable-ice style, the target block must be water (default false)
  opts.displayName = 'Example Budding Block'   // omit to let KubeJS name it from the id (same for buds/cluster)
  opts.dropItem = 'minecraft:amethyst_shard'   // dropped by the cluster on a normal break; null = nothing (default)
  opts.dropCount = 2                      // how many of it, default 1
  opts.group = 'building_blocks'          // creative tab: 'kubejs' by default; null = no tab at all (only /give)

  // one line registers the whole family: budding block + small/medium/large bud + cluster
  const family = CustomBudding.create(event, 'mypack:example_crystal', opts)

  // family holds the five block ids, ready for recipes / tags / loot tables
})
```

Just the chance, everything else left at its default:

```js
StartupEvents.registry('block', event => {
  CustomBudding.create(event, 'example_crystal', 20)
})
```

For your own art, add two lines (without them the budding block and all four stages use vanilla amethyst textures):

```js
opts.buddingTexture = 'mypack:block/my_crystal'
opts.stageTextures = ['mypack:block/my_small_bud', 'mypack:block/my_medium_bud',
                      'mypack:block/my_large_bud', 'mypack:block/my_cluster']
```

### Known limitations

- They **do not appear in the config tiers** (the chance comes from your script), and baked-in block properties such as light emission or a redstone signal are not configurable either (for those, use the addon-mod route — see the Development section).

### Low-level API (build the definition yourself)

```js
const BuddingGrowthEngine = Java.loadClass('com.minecart.yunxian.budding.BuddingGrowthEngine')
const GrowthDefinition  = Java.loadClass('com.minecart.yunxian.budding.GrowthDefinition')

// your own block + whatever four stage blocks you like (vanilla buds, this mod's, your own)
const definition = GrowthDefinition.of('minecraft:small_amethyst_bud', 'minecraft:medium_amethyst_bud',
                                       'minecraft:large_amethyst_bud', 'minecraft:amethyst_cluster', 20)

event.create('my_budding').randomTick(ctx => {
  BuddingGrowthEngine.tryGrow(ctx.block.getLevel(), ctx.block.getPos(), ctx.random, definition)
})
```

See the complete example in the previous section; a local dev copy lives at
`run/kubejs/startup_scripts/custom_budding_example.js` (`run/` is gitignored, so it is not shipped with the repo).

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

### For Addon Mods & KubeJS: Plugging a Block Into the Growth Engine

The growth check and placement are **public** (`budding/BuddingGrowthEngine`), and three kinds of
callers share one code path: this mod's own budding blocks, addon blocks, and KubeJS-registered blocks.
The engine deliberately **excludes** random-tick side effects (conversion/spread) and growth energy —
the block owns those, and energy is paid through a `GrowthGate` callback right before placement.

**Addon mod (Java)**

```java
// 1) Your own budding block: either use the public GenericBuddingBlock (pass your own buds/cluster),
//    or implement randomTick yourself and call the engine
DeferredBlock<Block> myBudding = MY_BLOCKS.register("my_budding",
        () -> new GenericBuddingBlock(myFamilySpec, properties, small, medium, large, cluster));

// 2) Declare it in your @Mod constructor (must happen before the block registry event):
BuddingRegistration.declareBuddingBlock(myBudding.get());   // use the shared Goggles BE
BuddingRegistration.declareKnownId("my_budding");           // let the config tiers list it
```

`declareBuddingBlock` is not optional politeness: restoring a block entity from chunk NBT validates
`BlockEntityType#isValid` (`LevelChunk:392`), so a block missing from the shared BE's block list has
its **block entity dropped on chunk reload** — the Goggles info silently stops working.

**KubeJS (modpack authors, no Java)**

See "Adding Your Own Budding Blocks (KubeJS)" above: with KubeJS installed, one line —
`CustomBudding.create(event, 'my_crystal', 20)` — registers the whole five-block family (the binding comes from
this mod's KubeJS plugin). For hand-rolled blocks, use
`GrowthDefinition.of(small, medium, large, cluster, n)` plus the engine.
See the complete example in the "Adding Your Own Budding Blocks (KubeJS)" section; a local dev copy lives at
`run/kubejs/startup_scripts/custom_budding_example.js` (`run/` is gitignored, so it is not shipped with the repo).

**The two ways to call the engine**

```java
// Free growth (what scripts and most addons use)
BuddingGrowthEngine.tryGrow(level, pos, random, GrowthDefinition.of(smallBud, mediumBud, largeBud, cluster, 20));

// With a payment gate (how this mod's own families work: Budding Fluix spends AE before placing)
BuddingGrowthEngine.tryGrow(serverLevel, pos, random, definition, gate);
```

Everything else is yours: block/item registration, textures, the loot table, and adding the block to
the `#c:budding_blocks` tag (the Smart Drill's precise harvest and AE2's growth accelerator read it).

---

## License

**This mod may only be included in modpacks published on CurseForge, with no further permission required** — public or private, free or monetized.

The pack must use **CurseForge's standard packaging method**: reference this mod in `manifest.json` by its CurseForge project ID and file ID, so the launcher downloads it from CurseForge. **Bundling the jar inside the pack archive is not allowed**, and neither is publishing the pack on any platform other than CurseForge. The condition is attribution: credit **YunXian_LI** and link back to the official download page.

See [LICENSE.txt](LICENSE.txt) for the full terms.

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
| Raw Iron, Raw Gold, Raw Copper, Raw Zinc, Diamond, Emerald, Lapis Lazuli, Redstone | — |
| Rose Quartz | — |
| Quartz, Glowstone | Full speed only in the Nether: elsewhere every successful roll has a further 1-in-2 chance to fail |
| Flammable Ice | Buds must be submerged in water; growth stops the moment they leave it |
| Echo | Growth space must be at light level 0 |
| Fluix *(requires AE2)* | Must be on a powered, active ME Grid; each growth consumes AE |

> Budding Redstone emits a redstone signal of 15 — it works directly as a redstone source.

### Budding Blocks Are Renewable

This is the important part: an ore budding block has a **1-in-20** chance per random tick to convert stone/deepslate within a 3×3×3 area into the **matching ore**. At a much lower chance, it can also "infect" the matching raw ore block, turning it into another budding block of the same kind. Budding Quartz erodes netherrack instead (into Nether Quartz Ore); Budding Echo converts dirt, stone, tuff and more into Sculk.

**One budding block is a self-expanding ore vein.**

---

## 2. Accelerators: From Minutes to Moments

A vanilla budding block only receives a random tick once every ~68 seconds on average, which makes natural growth almost negligible. An Accelerator forces **one random tick onto each of its six neighbouring blocks, every single tick**.

- **Electric Accelerator** — runs on FE; adjacent Accelerators balance power between themselves automatically
- **Mechanical Accelerator** — takes Rotational Force at its back; the faster the input, the faster it works, up to 256 RPM (at full speed it matches the electric one)

Both accelerators' **acceleration interval** is configurable (`Acceleration Interval (ticks)`, default 1 = every tick; larger = slower). The electric one pays FE per pass, so slowing it down also saves power. The Goggles multiplier follows the config.

**Vanilla Budding Amethyst gets the same line**: point the Goggles at one inside a natural geode and it shows the growth speed too. The vanilla block itself is untouched and the info is synthesized client-side, so nothing extra is written into your saves — and it works on other people's servers as well.

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

**The "Budding Block Info" page in JEI**: one page per budding block — the budding block on the left with its cluster
growing on top of it (sitting on Create's own JEI shadow sprite), the cluster's product in a slot at the top left
(with Create's own arrow beside it — both the shadow and the arrow come from Create's `gui/jei/widgets.png`), and
**Growth Conditions / Growth Speed / World Generation** spelled out on the right. All three sections stay
**qualitative on purpose** — growth speed reads "Normal tier / slow / quick", world generation reads
"deep underground · extremely rare" — so the chances, seconds and Y levels stay something you learn by playing
(read the config file, or write your own JEI plugin page, if a pack needs the exact numbers).
The budding block, its buds/cluster and the cluster's product are all look-up-able: pressing R or U on any of them
in JEI leads to this page.
The world-generation facts and the cluster's product are **read from the mod's own JSON at runtime** (worldgen /
loot tables), so editing that JSON and rebuilding updates the page instead of drifting from reality — note that a
client cannot see `data/` from datapacks, so a datapack overriding those files does not change the page. Budding blocks
registered by KubeJS scripts, and those other mods list in `#c:budding_blocks`, show up here automatically too.

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
All five blocks also **show up in JEI's "Budding Block Info" page automatically** — growth speed, light and water
requirements are all listed (as qualitative wording, e.g. "slow", "must be dark enough"), with no extra declaration needed.

### Complete example (copy & run)

Drop this into `kubejs/startup_scripts/` (any file name, as long as it ends in `.js`) and you're done — no
textures, no resource pack: the budding block and its buds/cluster use vanilla amethyst's textures by default.

```js
// kubejs/startup_scripts/my_crystal.js
StartupEvents.registry('block', event => {
  // The whole chain is `create`'s third argument. It has to sit here: options are read once,
  // when `create` runs, so CustomBudding.create(event, id).chance(20) does nothing (the blocks
  // are already built by then). Field-by-field assignment is equivalent and can be mixed:
  // const opts = new CustomBuddingOptions(), then opts.chance = 20 …, then create(event, id, opts).
  const family = CustomBudding.create(event, 'mypack:example_crystal', new CustomBuddingOptions()
    .chance(20)                                  // 1-in-20 per random tick to advance one stage (default 5)
    .maxLight(7)                                 // light ceiling for the growth spot, 0-15; negative = unlimited (default -1)
    .minLight(1)                                 // light floor for the growth spot, 0-15; negative = unlimited (default -1)
                                                 // both lines = only light 1-7 advances; one line = only that end is constrained
                                                 // (Echo-style "must be pitch dark" is just .maxLight(0)); min above max is rejected
    .requiresWater(false)                        // false = no water needed (default); flammable-ice style is .requiresWater()
    .growthDimensions('minecraft:overworld')     // growth dimension ids, pick as many as you like: normal growth only there; omit = any dimension
    .growthBiomes('warm')                        // growth biomes: a biome id, a biome tag ('#minecraft:is_nether') or a climate keyword ('cold' 'warm' 'hot')
                                                 // prefix with ! to negate: '!cold' (anywhere but cold biomes), '!hot', '!#minecraft:is_taiga'
                                                 // both lines = intersection => here: only the Overworld's warm biomes count as your turf
    .outsideGrowthChance(0.1)                    // how much growth odds survive outside your turf (0-1, default 0.5; 0 = never grows there again); ignored when neither line above is set
    .displayName('Example Budding Block')        // omit to let KubeJS name it from the id
    .stageDisplayNames('Small Bud', 'Medium Bud', 'Large Bud', 'Cluster')   // names of the four buds/cluster, small → medium → large → cluster; null keeps that one automatic
    .dropItem('mypack:my_shard', 2)              // dropped by the cluster on a normal break (fortune adds 0..level more; silk touch always drops the cluster itself; omit for nothing)
    .buddingTexture('mypack:block/my_crystal')   // budding block texture
    .stageTextures('mypack:block/my_small_bud',  // four stage textures, same order
                   'mypack:block/my_medium_bud',
                   'mypack:block/my_large_bud',
                   'mypack:block/my_cluster')
    .buddingSound('stone')                       // break sound of the budding block, a vanilla sound name ('stone' / 'crop' / 'glass' / 'wood' …); default 'amethyst'
    .stageSound('crop')                          // break sound of the buds/cluster, shared by all four stages; default 'amethyst'
    .buddingTool('axe')                          // mining tool of the budding block: pickaxe / axe / shovel / hoe (or a full tag id); default 'pickaxe'
    .stageTool('pickaxe')                        // mining tool of the buds/cluster, shared by all four stages
    .buddingLevel('stone')                       // harvest tier of the budding block: stone / iron / diamond; 'none' (or null, or omitting) = no tier
    .stageLevel('stone')                         // harvest tier of the buds/cluster, shared by all four stages
    .group('building_blocks'))                   // creative tab: 'kubejs' by default; null = no tab at all (only /give)

  // family holds the five block ids (record accessors — the parentheses are required):
  const budding = family.budding()               // 'mypack:example_crystal_budding'
  const cluster = family.cluster()               // 'mypack:example_crystal_cluster'
  console.info(`registered: ${budding} / ${cluster}`)
})
```

The block ids follow a fixed pattern: `<namespace>:<id>_budding` plus `_small_bud` / `_medium_bud` / `_large_bud` /
`_cluster` (so `mypack:example_crystal_*` above). **Recipes and tags go in `server_scripts/`** (the startup script has
no events for them) — just use those id strings, no need to reach back for `family`:

```js
// kubejs/server_scripts/my_crystal.js
ServerEvents.recipes(event => {
  event.smelting('minecraft:amethyst_shard', 'mypack:example_crystal_cluster')   // example: smelt the cluster
})

ServerEvents.tags('block', event => {
  event.add('minecraft:mineable/axe', 'mypack:example_crystal_budding')          // example: one more mining tag
})
```

- **Names**: the budding block uses `displayName('Example Budding Block')`; the four buds/cluster default to the English title KubeJS derives from the id (`example_crystal_small_bud` → "Example Crystal Small Bud"). Use `stageDisplayNames('Small Bud', 'Medium Bud', 'Large Bud', 'Cluster')` to change them — the order is small → medium → large → cluster, and passing `null` for one entry keeps that one automatic, so you can name only some of them. A block item shares its name with the block, so the inventory, drops and creative tab all follow.
- **Goggles**: point Create Goggles at your budding block and the panel shows the current growth multiplier plus its growth speed / light requirement / water requirement / growth environment (dimensions and biomes), in qualitative terms only (no exact numbers). This mod's own families normally show just the multiplier line — the ones with `.growthDimensions(...)` / `.growthBiomes(...)` (and Budding Quartz / Budding Glowstone themselves) add a "Grows fastest only in X" line.
- **The default textures are vanilla amethyst's**, so it works with zero assets; override the textures above or
  ship a resource pack for your own art.
- **Sounds, mining tools and harvest tiers all default to vanilla amethyst's too** (amethyst break sound, pickaxe,
  no tier):
  - Break sounds are written as **vanilla sound names** — `buddingSound('stone')` covers the budding block only,
    `stageSound('crop')` covers the four buds/cluster (one sound for all of them). The valid names are the field
    names of vanilla `SoundType`; an unknown name makes KubeJS fail on the spot and list every valid one.
  - **Mining tools** (which decide **what mines it fastest**) come in two: `buddingTool('axe')` is the budding block's,
    `stageTool('pickaxe')` covers the four buds/cluster (one tool for all of them). Each is one of `pickaxe` / `axe` /
    `shovel` / `hoe` — the four vanilla mining tags `#minecraft:mineable/*` there are (any other bare name is rejected
    on the spot, so you can't silently register a tag nobody reads) — or a full block tag id (containing `:`, e.g.
    `'mymod:mineable/wrench'`). `null` registers no tag at all, so bare hands are fastest. The old combined
    `tool('hoe')` still works and simply sets both of them.
  - **Harvest tiers** (which decide **what tool can get the drops**) are split the same way: `buddingLevel('stone')`
    is the budding block's, `stageLevel('stone')` covers the four buds/cluster. A bare name is one of the three
    vanilla tier tags `#minecraft:needs_<name>_tool`: `'stone'` / `'iron'` / `'diamond'` (any other bare name is
    rejected), or a full tag id (e.g. `'neoforge:needs_netherite_tool'`); leaving it out or writing `'none'`
    means no tier. Setting a tier also adds `requiresCorrectToolForDrops` to the block (a tag alone is read by
    nobody), so once it is set: **a tool below the tier gets no drops at all** (silk touch included), and **the tool
    type has to match too** — with the budding block set to axe + `'stone'`, a stone axe drops it and a stone
    pickaxe does not. Leave the tier unset to keep "bare hands still get the drops" (the default, and how this
    mod's own buds/cluster behave).
- The registered blocks go into the **creative "KubeJS" tab** by default (KubeJS blocks land in no tab at all
  otherwise, which makes it look like registration failed). `group(...)` can point at a vanilla tab
  (`'building_blocks'`, …); `.group(null)` keeps them out of every tab (then only `/give` gets them).
- **Drops**: buds drop nothing unless you use silk touch (which drops the bud itself); on a normal break the cluster drops
  whatever `dropItem(item, count)` names (nothing by default, **fortune adds 0..level more** — matching the loot tables of
  this mod's own clusters), and itself with silk touch — same behaviour as this mod's own buds/cluster.
- The returned `family` holds the five block ids, read through its record accessors: `family.budding()`,
  `family.smallBud()`, `family.mediumBud()`, `family.largeBud()`, `family.cluster()` — these are **method calls,
  the parentheses are required**; `family.budding` without them yields the method object, not the id. Handy when you
  register more things in the same startup script; **recipes and tags live in `server_scripts/`**, where you just use
  those id strings (see the end of the example above).
- **Tags are automatic**: the budding block joins `#c:budding_blocks` (block + item) and all five blocks join the mining tag picked by `buddingTool(...)` / `stageTool(...)` plus the tier tag picked by `buddingLevel(...)` / `stageLevel(...)` (by default just `#minecraft:mineable/pickaxe` and no tier; blocks only, never the items) — so the Smart Drill's silk-touch mode harvests your budding block itself and AE2's Crystal Growth Accelerator speeds it up. No tags to write by hand.
- Names and looks come from a resource pack / lang; drops are handled by this mod as described above.

### Known limitations

- They **do not appear in the config tiers** (the chance comes from your script), and baked-in block properties such as light emission or a redstone signal are not configurable either (for those, use the addon-mod route — see the Development section).

### Low-level API (build the definition yourself)

```js
const BuddingGrowthEngine = Java.loadClass('com.minecart.yunxian.budding.BuddingGrowthEngine')
const GrowthDefinition  = Java.loadClass('com.minecart.yunxian.budding.GrowthDefinition')

// of(small, medium, large, cluster, n): the four stage blocks plus chance base n (1-in-n per random tick).
// Stage blocks are plain ids (vanilla buds, this mod's, your own). The call returns the definition itself,
// so light, water and growth environment all chain onto it:
const definition = GrowthDefinition.of('minecraft:small_amethyst_bud', 'minecraft:medium_amethyst_bud',
                                       'minecraft:large_amethyst_bud', 'minecraft:amethyst_cluster', 20)
    .growthDimensions('minecraft:overworld')   // growth dimensions, pick as many as you like
    .growthBiomes('warm', '!hot')              // growth biomes: id / '#tag' / keyword cold·warm·hot; ! negates
    .outsideGrowthChance(0.1)                  // how much growth odds survive outside your turf (0 = never, 1 = unlimited)

// Light and water requirements chain the same way (light 0-15, negative = that end unlimited;
// min above max is rejected):
//   .maxLight(7).minLight(1)   // advances only at light 1-7 (Echo-style "pitch dark" is just .maxLight(0))
//   .requiresWater()           // target must be a water source (flammable-ice style)
// The full of(...) overloads work too: of(small, medium, large, cluster, n, maxLight, minLight, requiresWater),
// or of(small, medium, large, cluster, n, maxLight, requiresWater) for a ceiling only.

event.create('my_budding').randomTick(ctx => {
  BuddingGrowthEngine.tryGrow(ctx.block.getLevel(), ctx.block.getPos(), ctx.random, definition)
})
```

> ⚠️ Building the definition at the top level like this is safe while the stage blocks are vanilla or your own.
> **If the definition references another mod's blocks**, build it inside the `randomTick` callback and cache it —
> those blocks are not registered yet when startup scripts run, so a top-level call fails on the spot.

See the "Complete example (copy & run)" above; a local dev copy lives at
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

Blocks built on `GenericBuddingBlock` are done after those two steps: they carry a family definition,
so JEI's Budding Block Info page can read their behaviour straight away. A block written against the
**low-level API** (your own `randomTick` calling the engine) has no definition to read, so declare one:

```java
// 3) Let JEI's "Budding Block Info" page list its growth speed / light / water requirements
BuddingRegistration.declareGrowthDefinition(myBudding.get(),
        () -> GrowthDefinition.of(smallBud, mediumBud, largeBud, cluster, 20));
```

The definition is a `Supplier`, so calling this before the stage blocks exist is safe (which is exactly
the case for script registration).

**KubeJS (modpack authors, no Java)**

See "Adding Your Own Budding Blocks (KubeJS)" above: with KubeJS installed, one line —
`CustomBudding.create(event, 'my_crystal', 20)` — registers the whole five-block family (the binding comes from
this mod's KubeJS plugin). For hand-rolled blocks, use
`GrowthDefinition.of(small, medium, large, cluster, n)` plus the engine (optional light bounds, a water
requirement, and a growth environment — dimensions and/or biomes — can be appended; see the example above).
See the complete example in the "Adding Your Own Budding Blocks (KubeJS)" section; a local dev copy lives at
`run/kubejs/startup_scripts/custom_budding_example.js` (`run/` is gitignored, so it is not shipped with the repo).

**The two ways to call the engine**

```java
// Free growth (what scripts and most addons use)
BuddingGrowthEngine.tryGrow(level, pos, random, GrowthDefinition.of(smallBud, mediumBud, largeBud, cluster, 20));

// With a payment gate (how this mod's own families work: Budding Fluix spends AE before placing)
BuddingGrowthEngine.tryGrow(serverLevel, pos, random, definition, gate);
```

A definition can carry one more requirement: `growthDimensions(Level.NETHER)` (pass several for several dimensions)
restricts normal growth to those dimensions, `growthBiomes("minecraft:lush_caves")` narrows it further by biome,
and the two combine as an intersection — everywhere else every successful roll keeps only
`outsideGrowthChance(0.5)` of its odds (0.5 is exactly how Budding Quartz and Budding Glowstone are set up;
write 0 and they never grow outside their turf again).

Biome entries take a biome id, a biome tag (`"#minecraft:is_nether"`), a climate keyword
(`"cold"` / `"warm"` / `"hot"`), or any of those prefixed with `!` to mean "not this". Negatives win: matching one
drops that biome outright; otherwise at least one positive has to match, and if you wrote only negatives they mean
"anywhere but these".

The climate keywords are binned like this:

| Keyword | Covers |
| --- | --- |
| `cold` | **the whole End**, or base temperature < 0.3 (snowy plains, ice spikes, snowy taiga, frozen ocean, snowy slopes, frozen peaks, plus cool biomes like taiga and mountains) |
| `warm` | everything else (plains, forest, jungle, swamp, ocean, mushroom fields, stony peaks…) |
| `hot` | **the whole Nether**, or base temperature ≥ 1.2 (desert, badlands, savanna) |

"The whole End/Nether" rides on the biome tags (`#minecraft:is_end` / `#minecraft:is_nether`), so modded dimensions
built from those biomes count too; tune the thresholds via the two constants in `GrowthEnvironment.Climate`.

Everything else is yours: block/item registration, textures, the loot table, and adding the block to
the `#c:budding_blocks` tag (the Smart Drill's precise harvest and AE2's growth accelerator read it).

---

## License

**This mod may only be included in modpacks published on CurseForge, with no further permission required** — public or private, free or monetized.

The pack must use **CurseForge's standard packaging method**: reference this mod in `manifest.json` by its CurseForge project ID and file ID, so the launcher downloads it from CurseForge. **Bundling the jar inside the pack archive is not allowed**, and neither is publishing the pack on any platform other than CurseForge. The condition is attribution: credit **YunXian_LI** and link back to the official download page.

See [LICENSE.txt](LICENSE.txt) for the full terms.

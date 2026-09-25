# Create: Crystal Industry

**机械动力：晶簇工业** · Minecraft 1.21.1 · NeoForge · Create 6.0.10+

**English | [简体中文](README.md)**

Vanilla's amethyst budding mechanic, generalized to the rest of the ore table. A budding block grows buds and clusters from its six faces, is never consumed, and keeps growing after each harvest. Ore budding blocks also convert the stone around them into the matching ore, so a single budding block amounts to a vein that expands on its own.

---

## 1. Budding Blocks

A budding block rolls for growth independently on each of its six faces. An empty face grows a **Small Bud**; an existing bud advances along **Small Bud → Medium Bud → Large Bud → Cluster**, with the cluster as the final stage. Rolls are driven by random ticks, and a successful roll advances one stage. The budding block itself is not consumed, so the face it occupies stays available after you harvest the cluster.

### Budding Blocks at a Glance

"Adjacent Conversion" works as follows: each random tick the budding block rolls a 1-in-20 chance; on success it picks **one random position within a radius of 1 (a 3×3×3 volume)** and converts it to the matching ore if that position holds Stone or Deepslate. Output therefore depends on how much of that volume is Stone or Deepslate — burying the budding block in stone raises the hit rate. Budding Echo uses a different radius and chance, noted in the table.

| Budding Block | Extra Growth Condition | Cluster Output | Adjacent Conversion |
| --- | --- | --- | --- |
| Rose Quartz | — | Create's Rose Quartz | — |
| Raw Iron | — | Raw Iron | Iron Ore / Deepslate Iron Ore |
| Raw Gold | — | Raw Gold | Gold Ore / Deepslate Gold Ore |
| Raw Copper | — | Raw Copper | Copper Ore / Deepslate Copper Ore |
| Raw Zinc | — | Create's Raw Zinc | Zinc Ore / Deepslate Zinc Ore |
| Diamond | — | Diamond | Diamond Ore / Deepslate Diamond Ore |
| Emerald | — | Emerald | Emerald Ore / Deepslate Emerald Ore |
| Lapis Lazuli | — | Lapis Lazuli ×4–9 | Lapis Lazuli Ore / Deepslate Lapis Lazuli Ore |
| Redstone | — | Redstone | Redstone Ore / Deepslate Redstone Ore |
| Quartz | Full speed only in the Nether; elsewhere a successful roll has a further 1-in-2 chance to fail | Nether Quartz | Netherrack → Nether Quartz Ore |
| Glowstone | Full speed only in the Nether, as above | Glowstone Dust | — |
| Echo | Growth space must be at light level 0 | Echo Shard | Any of 12 blocks within a radius of 2 (dirt, sand, stone, tuff, …) → Sculk, 1-in-4 |
| Flammable Ice | Target space must be a water source block | Flammable Ice | — |
| Fluix *(requires AE2)* | Must be on a powered, active ME Grid (uses 1 channel); each growth consumes 200 AE | Fluix Crystal | — |

Drop rules:

| Block | Normal Break | Silk Touch |
| --- | --- | --- |
| Cluster | The item in the "Cluster Output" column above, with Fortune applied | The cluster block itself |
| Bud | Nothing | The bud block itself |
| Budding Block | The block one tier below it (Budding Raw Iron drops a Block of Raw Iron, Budding Diamond drops a Block of Diamond) | The same; Silk Touch does not change this |

The budding block itself cannot be collected with ordinary tools. The Smart Drill's Silk Touch mode is the only way to obtain it, described in section 3.

Three block properties are worth noting. Budding Redstone, its buds and its cluster all emit a redstone signal (15 from the budding block, then 3 / 7 / 11 from the buds and 15 from the cluster), so they work directly as a redstone source. Budding Glowstone, its buds and its cluster all emit light (15 from the budding block, then 3 / 7 / 11 from the buds and 15 from the cluster). Budding Echo's buds and cluster deliberately emit no light at all: any emission would occupy the very growth space they need.

### Budding Block Reproduction

Beyond converting ore, an ore budding block has a **1-in-25000** chance to "infect" an adjacent block of the matching ore block, turning it into another budding block. Budding Quartz infects Smooth Quartz Blocks instead, and Budding Fluix infects Fluix Blocks (that conversion additionally requires ME Grid power).

| Budding Block | Block That Can Be Converted Into It |
| --- | --- |
| Raw Iron / Raw Gold / Raw Copper / Raw Zinc | The matching block of raw ore |
| Diamond / Emerald / Lapis Lazuli | The matching block of the ore |
| Redstone | Block of Redstone |
| Quartz | Smooth Quartz Block |
| Fluix | Fluix Block (requires power) |

### Growth Speed

| Tier | Chance to Advance per Random Tick | Config Key |
| --- | --- | --- |
| Very Slow | 1/50 | `growthSpeedVerySlow` |
| Slow | 1/20 | `growthSpeedSlow` |
| Normal *(default)* | 1/5, the same as vanilla Budding Amethyst | `growthSpeedNormal` |
| Fast | 1/1, a successful roll always advances | `growthSpeedFast` |

Each of the four config keys takes a list of **budding family ids** — the `<id>` of the `generate_<id>` switches, such as `raw_iron`, `diamond` or `echo`. `growthSpeedNormal` lists every budding block by default; a budding block named in no list is treated as Normal, so emptying that list changes nothing. If a budding block appears in two of the non-Normal tiers, the slower tier wins and a warning is logged.

---

## 2. Accelerators

A vanilla budding block sees a random tick about once every 68 seconds on average, which makes natural growth effectively unobservable. An accelerator applies **one random tick to each of its six adjacent faces**, compressing that process into seconds.

| | Accelerator | Mechanical Accelerator |
| --- | --- | --- |
| Power source | FE | Rotational force on the back face |
| Cost per pass | 100 FE | No energy |
| Trigger rate | Once every `acceleratorIntervalTicks` ticks (default 1, i.e. every tick) | Same |
| Strength | Fixed; every pass applies | Scales linearly with RPM, capped at 256 RPM |
| Effect at full speed | 6 random ticks per tick | Identical to the Accelerator |
| Internal buffer | 10,000 FE, max input 100 FE/t | — |

- Adjacent Accelerators balance energy among themselves: the fuller side pushes toward the emptier one, and a single push never exceeds half the difference, which keeps the two from oscillating back and forth.
- A Mechanical Accelerator below 256 RPM scales its effect with RPM; above 256 RPM it gains nothing further.
- The `acceleratorIntervalTicks` config key (**Acceleration Interval (ticks)**) applies to both types, and a larger value means slower growth. The Accelerator settles its FE cost per pass, so slowing it down also reduces its power draw. The multiplier shown by Engineer's Goggles follows this config value.

> **Random ticks drive far more than clusters.** Crops, saplings, copper oxidation, nether wart and every other random-tick-driven mechanic are accelerated along with them, which makes an accelerator a general-purpose tick accelerator as well.

Wearing Engineer's Goggles and looking at a **vanilla Budding Amethyst** shows the same growth speed and multiplier line. The vanilla block itself is untouched: the information is generated client-side on demand, so nothing is added to your save data.

---

## 3. Machines and Equipment

| Item | Description |
| --- | --- |
| **Smart Drill** | A mechanical drill with two modes, switchable at any time from the value slot on its side. **Normal mode**: breaks blocks at twice the speed of an ordinary Mechanical Drill. **Silk Touch mode**: same speed as an ordinary drill, but blocks drop intact as if mined with Silk Touch. Budding blocks are the special case: Normal mode only shatters them (dropping the tier below), and only Silk Touch mode collects the budding block itself. A filter item can restrict which blocks it breaks; an empty filter breaks anything. |
| **Mechanical Cleaner** | Has every airflow function of the Encased Fan (blow/suck, filtering, an airflow range of 1–20 blocks), and additionally pulls items caught in the airflow into its own 27-slot inventory; without rotational power it still passively collects items directly in front of it. If a container sits directly in front, it exchanges items with that container directly, never dropping anything into the world. **Waterlogged while blowing**, it applies washing to items, equivalent to the Encased Fan's washing. |
| **Night Vision Goggles** | Engineer's Goggles modified with an Echo Shard. While worn, a keybind toggles night vision (default `N`, rebindable in Controls), and the wearer is also immune to the Darkness effect. Wears in the vanilla helmet slot or a Curios head slot. |
| **Echo Spyglass** | While in use, your line of sight passes through blocks and renders them as outlines, for up to 60 seconds per use. Sneak-right-click opens a filter screen that accepts an item or a Create List Filter; with no filter it matches `#c:ores` (all ores) by default. |
| **Flammable Ice** | Flammable Ice (400 ticks) and Flammable Ice Blocks (4000 ticks) are both fuels, and both are listed among the superheated fuels for Create's Blaze Burner. Nine Flammable Ice craft into one Flammable Ice Block. |

---

## 4. World Generation

Each budding block generates at the depth of its corresponding ore, usually embedded in a vein.

| Budding Block | Dimension | Y Range | Placement |
| --- | --- | --- | --- |
| Diamond | Overworld | −64 – 16 | Vein, rarity 1/16 |
| Emerald | Overworld | −16 – 320 | Vein, rarity 1/16 |
| Raw Iron | Overworld | −24 – 56 | Vein, rarity 1/16 |
| Raw Gold | Overworld | −64 – 32 | Vein, rarity 1/16 |
| Raw Copper | Overworld | −16 – 112 | Vein, rarity 1/16 |
| Raw Zinc | Overworld | −63 – 70 | Vein, rarity 1/16 |
| Lapis Lazuli | Overworld | −64 – 64 | Vein, rarity 1/16 |
| Redstone | Overworld | −63 – 15 | Vein, rarity 1/16 |
| Quartz | Nether | 10 above bedrock to 10 below the top | Vein, rarity 1/8 |
| Glowstone | Nether | At natural glowstone blobs | Replaces the lowest block of a glowstone blob, at a chance set by `glowstoneBuddingChance` (default 0.5); `glowstoneGenerateBuds` and related keys control the buds that come with it |
| Echo | Overworld | −64 – 0 | Deep Dark, generated inside Sculk |
| Flammable Ice | Overworld | Below the deep-ocean seafloor | Structure, 1-in-256 per chunk (`flammableIceChance`), with soul sand scattered around it |
| Rose Quartz | — | — | Does not generate naturally |
| Fluix | — | — | Does not generate naturally |

Each one can be toggled individually in the config file (`generate_<budding id>`). The Flammable Ice structure and Budding Glowstone have their own additional chance settings.

> **Breaking a naturally generated Budding Echo summons a Warden.** The check reads the block's `can_summon` state, so a Budding Echo you placed yourself never triggers it.

---

## 5. Compatibility

| Mod | Relation | Notes |
| --- | --- | --- |
| Create | Required | 6.0.10+ |
| AE2 | Optional | Enables Budding Fluix; AE2's own Growth Accelerator accelerates this mod's budding blocks too, and the Engineer's Goggles multiplier counts it |
| Curios | Optional | Night Vision Goggles fit a Curios head slot (without Curios they use the vanilla helmet slot) |
| JEI | Optional | Adds a "Budding Block Info" page: one page per budding block covering growth conditions, growth speed and generation conditions, reachable from the budding block, its buds, its cluster and the cluster's output |
| KubeJS | Optional | Register your own budding blocks from a script, see the next section |

Without AE2 the mod starts normally and simply does not register any Fluix content. Every machine provides the standard Engineer's Goggles information panel, and the budding blocks (including vanilla Budding Amethyst), both Accelerators, the Smart Drill and the Mechanical Cleaner each ship with a Ponder scene.

The mod provides 31 advancements across five branches: budding blocks, accelerators, machines, equipment, and the deep ocean and Deep Dark.

---

## 6. Adding Budding Blocks with KubeJS

The growth engine is public, so you can register custom budding blocks with KubeJS — as many as you like, with no Java and no data pack.

**One line registers a whole family**: the budding block plus its small bud, medium bud, large bud and cluster, five blocks in one call. Put the script under `kubejs/startup_scripts/`; the file name is free as long as it ends in `.js`.

```js
StartupEvents.registry('block', event => {
  CustomBudding.create(event, 'example_crystal', 20)   // 1-in-20 chance
})
```

The resulting block ids are `kubejs:example_crystal_budding` and `_small_bud` / `_medium_bud` / `_large_bud` / `_cluster`. The budding block's random tick is already wired into the growth engine, and the buds and cluster carry a `FACING` property (pointing back at the budding block). Textures and sounds default to vanilla amethyst, so the script runs with no assets at all.

The `id` may include a namespace (`'mypack:example_crystal'`); without one it lands in the `kubejs` namespace.

### Full Example

Copy the following into `kubejs/startup_scripts/` and it runs as-is. The comments explain each option.

```js
// kubejs/startup_scripts/my_crystal.js
StartupEvents.registry('block', event => {
  // The options object is the third argument to create. The chain must be written here: options
  // are read once, at the moment create is called, so CustomBudding.create(event, id).chance(20)
  // does not work — by then the blocks have already been built.
  // Assigning fields and chaining are equivalent and can be mixed:
  //   const opts = new CustomBuddingOptions()
  //   opts.chance = 20 …
  //   CustomBudding.create(event, id, opts)
  const family = CustomBudding.create(event, 'mypack:example_crystal', new CustomBuddingOptions()
    .chance(20)                                  // 1-in-20 chance to advance a stage per random tick (default 5)
    .maxLight(7)                                 // max light at the growth space, 0–15; negative = unlimited (default -1)
    .minLight(1)                                 // min light at the growth space, 0–15; negative = unlimited (default -1)
                                                 // both lines = only light 1–7 advances; one line = only that end is bound
                                                 // (the Echo "must be pitch dark" case is just .maxLight(0))
                                                 // a min above the max throws immediately
    .requiresWater(false)                        // default false = no water needed
                                                 // the Flammable Ice form is .requiresWater() (target must be a water source)
    .growthDimensions('minecraft:overworld')     // growth dimension ids, multiple allowed; omit = any dimension
    .growthBiomes('warm')                        // growth biomes, see "Growth Environment" below; omit = any biome
                                                 // writing this and the line above intersects the two
    .outsideGrowthChance(0.1)                    // chance to keep growing outside your turf (0–1, default 0.5)
                                                 // 0 = never grows outside it; meaningless without the two lines above
    .displayName('Example Budding Block')        // omit and KubeJS names it from the id
    .stageDisplayNames('Small Bud', 'Medium Bud', 'Large Bud', 'Cluster')   // order: small → medium → large → cluster
                                                 // pass null for one entry to keep its automatic name
    .dropItem('mypack:my_shard', 2)              // what the cluster drops on a normal break (default: nothing)
                                                 // Fortune adds 0–level per level; Silk Touch always drops the cluster itself
    .buddingTexture('mypack:block/my_crystal')   // budding block texture
    .stageTextures('mypack:block/my_small_bud',  // the four stage textures, same order as above
                   'mypack:block/my_medium_bud',
                   'mypack:block/my_large_bud',
                   'mypack:block/my_cluster')
    .buddingSound('stone')                       // break sound of the budding block, a vanilla sound name (default 'amethyst')
    .stageSound('crop')                          // break sound of the buds and cluster, shared by all four stages
    .buddingTool('axe')                          // mining tool of the budding block: pickaxe / axe / shovel / hoe
                                                 // (or a full tag id); default 'pickaxe'
    .stageTool('pickaxe')                        // mining tool of the buds and cluster, shared by all four stages
    .buddingLevel('stone')                       // mining tier of the budding block: stone / iron / diamond
                                                 // 'none', null or omitted = no tier
    .stageLevel('stone')                         // mining tier of the buds and cluster, shared by all four stages
    .group('building_blocks'))                   // creative tab: default 'kubejs', null = no tab at all

  // family holds the five block ids; they are record accessors, so the parentheses are required:
  const budding = family.budding()               // 'mypack:example_crystal_budding'
  const cluster = family.cluster()               // 'mypack:example_crystal_cluster'
  console.info(`registered: ${budding} / ${cluster}`)
})
```

### Option Reference

Chained methods share their names with the fields; the two forms are equivalent and can be mixed. This table lists every option, with the default in parentheses.

| Option | Value | Effect |
| --- | --- | --- |
| `chance(n)` | integer ≥ 1 (5) | A 1-in-n chance to advance a stage per random tick |
| `maxLight(n)` | 0–15, negative = unlimited (−1) | Upper light bound at the growth space |
| `minLight(n)` | 0–15, negative = unlimited (−1) | Lower light bound at the growth space |
| `requiresWater()` | — (false) | Target space must be a water source block |
| `growthDimensions(...)` | dimension ids, multiple allowed | Grows normally only in these dimensions |
| `growthBiomes(...)` | biome ids / tags / climate keywords, multiple allowed | Grows normally only in these biomes |
| `outsideGrowthChance(x)` | 0–1 (0.5) | Chance to keep growing outside your turf |
| `displayName(s)` | string | Budding block display name; automatic if omitted |
| `stageDisplayNames(...)` | four strings, `null` allowed | Display names of the four stages, order: small → medium → large → cluster |
| `dropItem(id)` / `dropItem(id, n)` | item id, optional count (1) | What the cluster drops on a normal break |
| `dropCount(n)` | integer (1) | Drop count; anything below 1 is treated as 1 |
| `buddingTexture(s)` | texture path | Budding block texture (defaults to vanilla Budding Amethyst) |
| `stageTextures(...)` | four texture paths | The four stage textures, same order |
| `buddingSound(s)` / `stageSound(s)` | vanilla sound name (`amethyst`) | Break sound; an unknown name throws immediately and lists every valid name |
| `buddingTool(s)` / `stageTool(s)` / `tool(s)` | tool name or full tag id (`pickaxe`) | Mining tool; `tool` sets both at once |
| `buddingLevel(s)` / `stageLevel(s)` | tier name or full tag id (unset) | Mining tier; `'none'` or `null` = no tier |
| `group(s)` | tab id (`kubejs`) | Creative tab; `null` = no tab |

### Growth Environment

Writing both `growthDimensions` and `growthBiomes` intersects them: a position counts as your turf only if its dimension *and* its biome both match. Outside your turf, every successful roll makes a second roll and only grows with probability `outsideGrowthChance`. Writing neither treats everywhere alike.

Each `growthBiomes` entry may be:

| Form | Example | Meaning |
| --- | --- | --- |
| Biome id | `'minecraft:lush_caves'` | That biome |
| Biome tag | `'#minecraft:is_nether'` | Every biome in the tag |
| Climate keyword | `'cold'` / `'warm'` / `'hot'` | Bucketed by base temperature; the Nether counts as `hot` everywhere and the End as `cold` |
| Negation | `'!cold'`, `'!#minecraft:is_taiga'` | A leading `!` excludes |

Hitting any negation drops the position immediately; otherwise at least one positive entry must match. When only negations are written, the positive side is treated as "every biome", so `growthBiomes('!cold')` reads as "anywhere but cold biomes".

### Drops, Tools and Mining Tiers

- **Drops**: buds drop themselves only under Silk Touch, whatever breaks them. Clusters drop what `dropItem(item, count)` names on a normal break and the cluster itself under Silk Touch; with no `dropItem` a normal break drops nothing. The count benefits from Fortune, adding 0–level per level, matching this mod's own cluster loot tables.
- **Mining tool** decides *what mines fastest*. The bare names `pickaxe` / `axe` / `shovel` / `hoe` are the complete set of vanilla mining tags (`#minecraft:mineable/*`); any other bare name throws immediately, which prevents registering a tag nobody reads. You may also write a full block tag id (containing `:`, such as `'mymod:mineable/wrench'`). Writing `null` attaches no tag at all, making bare hands the fastest. Use `tool(...)` to give all five blocks the same tool.
- **Mining tier** decides *what may collect the drop*. A bare name resolves to a vanilla tier tag, `#minecraft:needs_<name>_tool`: `'stone'` / `'iron'` / `'diamond'`. A full tag id also works (e.g. `'neoforge:needs_netherite_tool'`). Omitting it, or writing `'none'`, means no tier.

  Setting a tier also gives the block `requiresCorrectToolForDrops`, because a bare tag is read by nothing on its own. That has two consequences: **a tool below the tier drops nothing at all**, Silk Touch included, and **the tool type must match as well** — with the budding block set to axe plus `'stone'`, a stone axe collects it and a stone pickaxe does not. To allow bare-hand collection, leave the tier unset, which is what this mod's own buds and clusters do.

### What Comes Next

Block ids follow a fixed pattern: `<namespace>:<id>_budding` and `_small_bud` / `_medium_bud` / `_large_bud` / `_cluster`. **Write recipes and tags in `server_scripts/`** (startup scripts have no such events) and use those id strings directly:

```js
// kubejs/server_scripts/my_crystal.js
ServerEvents.recipes(event => {
  event.smelting('minecraft:amethyst_shard', 'mypack:example_crystal_cluster')   // smelt the cluster into amethyst shards
})

ServerEvents.tags('block', event => {
  event.add('minecraft:mineable/axe', 'mypack:example_crystal_budding')          // attach one more mining tag
})
```

The value `create` returns, `family`, exposes five accessors for the block ids: `budding()` / `smallBud()` / `mediumBud()` / `largeBud()` / `cluster()`. They are **method calls, so the parentheses are required**; writing `family.budding` yields the method object rather than an id. They are useful for registering related items inside the same startup script, whereas `server_scripts/` should just use the id strings above.

### Behavior Notes

- **Tags are attached automatically.** The budding block joins `#c:budding_blocks` (both the block and item tags). The five blocks join the matching mining tags via `buddingTool` / `stageTool` and the matching tier tags via `buddingLevel` / `stageLevel` (by default only `#minecraft:mineable/pickaxe`, with no tier). As a result the Smart Drill's Silk Touch mode collects your budding block directly and AE2's Growth Accelerator accelerates it, with no tags to write by hand.
- **Goggles**: wearing Engineer's Goggles and looking at a custom budding block shows the current growth multiplier along with its growth speed, light requirement, water requirement and growth environment (dimension / biome, phrased qualitatively as "Slow" or "must be dark enough" without exact numbers). Built-in families normally show only the multiplier line; those with `growthDimensions` / `growthBiomes` gain an extra "fastest only in X" line.
- **Creative tab**: KubeJS-registered blocks go into no tab by default, which makes people think registration failed, so these land in the KubeJS tab unless told otherwise. `group(...)` switches to a vanilla tab, and `group(null)` omits the tab entirely (reachable only via `/give`).
- **Names and appearance** come from resource packs and language files. Without a display name, KubeJS derives an English title from the id (`example_crystal_small_bud` → "Example Crystal Small Bud"). The block item and the block share one translation key, so the inventory, dropped items and creative tab all change together.

### Known Limitations

- The four growth speed tiers in the config file do not apply; the chance comes entirely from `chance` in the script.
- Features **baked into block properties**, such as light emission and redstone output, cannot be configured from a script. Those require the addon Java route described in section 7.

### Low-Level Interface

For full control, load the growth engine and the definition class yourself and assemble them by hand. `CustomBudding` and `CustomBuddingOptions` are bound as script globals by this mod's KubeJS plugin and need no `Java.loadClass`; the engine and definition do.

```js
const BuddingGrowthEngine = Java.loadClass('com.minecart.yunxian.budding.BuddingGrowthEngine')
const GrowthDefinition  = Java.loadClass('com.minecart.yunxian.budding.GrowthDefinition')

// of(small, medium, large, cluster, n): the four stage blocks plus the chance base n (1-in-n per random tick).
// Stage blocks are written as ids; vanilla amethyst buds, this mod's buds and clusters, and blocks you
// registered yourself all work. The call returns the definition, so the chain continues from it:
const definition = GrowthDefinition.of('minecraft:small_amethyst_bud', 'minecraft:medium_amethyst_bud',
                                       'minecraft:large_amethyst_bud', 'minecraft:amethyst_cluster', 20)
    .growthDimensions('minecraft:overworld')   // growth dimensions, multiple allowed
    .growthBiomes('warm', '!hot')              // growth biomes: ids / '#tags' / keywords cold·warm·hot; ! negates
    .outsideGrowthChance(0.1)                  // chance to keep growing outside your turf (0 = never)

// Light and water live on the same chain (light 0–15, negative = that end unbounded; a min above the max throws):
//   .maxLight(7).minLight(1)   // only light 1–7 advances
//   .requiresWater()           // target must be a water source
// The full overload of of() also works: of(small, medium, large, cluster, n, maxLight, minLight, requiresWater);
// when only the max is wanted, omit the min: of(small, medium, large, cluster, n, maxLight, requiresWater).

event.create('my_budding').randomTick(ctx => {
  BuddingGrowthEngine.tryGrow(ctx.block.getLevel(), ctx.block.getPos(), ctx.random, definition)
})
```

> Building the definition at the top level of the script is safe, because the stage blocks are either vanilla or ones you registered yourself. **If the definition references another mod's blocks**, it must move into the `randomTick` callback and be cached there: those blocks are not registered yet when the script runs, and building it at the top level throws immediately.

A runnable example also ships in the local development directory: `run/kubejs/startup_scripts/custom_budding_example.js`. `run/` is listed in `.gitignore` and is not distributed with the repository.

---

## 7. Development

### Adding a Budding Block

Every trait of a budding block — light, water and power requirements, growth rules, block conversion, light and sound, drops — lives in a single table in
`src/main/java/com/minecart/yunxian/budding/BuddingFamilies.java`. **Adding a budding family amounts to adding one entry to that table**; block registration, growth logic, Goggles readouts, creative tabs, world generation switches and Ponder entries are all derived from it.

Growth speed is the exception: it is a global four-tier setting (`BuddingFamily.GrowthSpeed`) driven by the four id lists in the config file, and the default membership of the Normal tier is likewise derived from that table, so a new family needs no extra configuration.

### Data Generation

Generate the JSON assets with `./gradlew runData`. **AE2 must be present in `run/mods` before running it**, otherwise the task aborts outright — this is what prevents the already-generated Fluix assets from being judged stale and deleted. The output directory `src/generated/resources` is under version control.

Data generation owns the following files; do not write them by hand: `blockstates/`, `models/block/`, `models/item/`, the loot tables for budding blocks and buds, the `c:budding_blocks` tags, and the `mineable/pickaxe` and `needs_*_tool` tags.

Still hand-written: textures (including `.mcmeta`), the loot tables for clusters and Fluix (their structure and mod conditions cannot be reproduced by the generator), world generation JSON, and language files.

### Wiring a Block Into the Growth Engine

Growth checks and placement are public (`budding/BuddingGrowthEngine`), and three kinds of caller share one code path: this mod's own budding blocks, an addon's blocks, and blocks registered from a KubeJS script. The engine deliberately **excludes** random-tick side effects (conversion and spreading) and growth energy; the block owns those, and energy is requested through a `GrowthGate` callback before placement.

**Addon mods (Java)**

```java
// 1) Your budding block: either use the public GenericBuddingBlock (passing your own buds/cluster),
//    or implement randomTick yourself and call the engine
DeferredBlock<Block> myBudding = MY_BLOCKS.register("my_budding",
        () -> new GenericBuddingBlock(myFamilySpec, properties, small, medium, large, cluster));

// 2) Declare it in your own @Mod constructor (must happen before the block registration event):
BuddingRegistration.declareBuddingBlock(myBudding.get());   // use the shared Goggles block entity
BuddingRegistration.declareKnownId("my_budding");           // let the four config tiers list it
```

`declareBuddingBlock` is not a courtesy call you can skip: when a chunk restores block entities from NBT it validates `BlockEntityType#isValid` (`LevelChunk:392`), and a block absent from the shared block entity's valid-block list has its block entity **discarded after a chunk reload**, at which point the Goggles readout stops working.

A block built on `GenericBuddingBlock` is complete at this point: it carries its own family definition, which the JEI Budding Block Info page reads directly. Blocks that **assemble the low-level interface themselves** (implementing `randomTick` and calling the engine) have no definition to read, so they need one more declaration:

```java
// 3) Let the JEI "Budding Block Info" page list its growth speed / light / water requirements
BuddingRegistration.declareGrowthDefinition(myBudding.get(),
        () -> GrowthDefinition.of(smallBud, mediumBud, largeBud, cluster, 20));
```

Definitions are taken lazily through a `Supplier`, so calling this before the block and its stage blocks exist is safe — which is exactly the situation during script registration.

**The two call forms of the engine**

```java
// Free growth (the common case for scripts and addons)
BuddingGrowthEngine.tryGrow(level, pos, random, GrowthDefinition.of(smallBud, mediumBud, largeBud, cluster, 20));

// With the paid hook (this mod's own families use it: the AE2 budding block pays energy before placement)
BuddingGrowthEngine.tryGrow(serverLevel, pos, random, definition, gate);
```

A definition can take further gates: `growthDimensions(Level.NETHER)` restricts normal growth to the listed dimensions, and `growthBiomes("minecraft:lush_caves")` adds a biome filter. Writing both intersects them; outside your turf each successful check makes a second roll and only grows with probability `outsideGrowthChance(0.5)` — 0.5 is exactly what Budding Quartz and Budding Glowstone use, and 0 stops growth there entirely.

Biome entries resolve in this order: hitting any **negation** drops the position immediately; otherwise at least one **positive** entry must match; when only negations are written, the positive side counts as "every biome" (`growthBiomes("!cold")` means anywhere but cold biomes).

Everything else is yours to supply: block registration and textures, items, loot tables, and adding the block to the `#c:budding_blocks` tag (the Smart Drill's Silk Touch mode and AE2's Growth Accelerator read it).

### Climate Keywords

How the `cold` / `warm` / `hot` keywords are bucketed:

| Keyword | Coverage |
| --- | --- |
| `cold` | The whole End, or a biome base temperature below 0.3 (snowy plains, ice spikes, snowy taiga, frozen ocean, snowy slopes, frozen peaks, plus cool biomes such as taiga and windswept hills) |
| `warm` | Everything else (plains, forest, jungle, swamp, ocean, mushroom fields, stony peaks, …) |
| `hot` | The whole Nether, or a biome base temperature of at least 1.2 (desert, badlands, savanna) |

The "whole End / whole Nether" cases go through the biome tags `#minecraft:is_end` / `#minecraft:is_nether`, so modded dimensions using those biome sets count as well. The thresholds are adjustable: the two constants in `GrowthEnvironment.Climate`.

---

## License

**This mod may be included in modpacks published on CurseForge without asking for permission** — public or private, free or monetized.

The modpack must use **the CurseForge packaging method**: reference this mod in `manifest.json` by CurseForge project ID and file ID so the launcher downloads it from CurseForge itself. **Do not bundle the mod jar inside the archive**, and do not publish it on any platform other than CurseForge. The condition is that you credit the author (YunXian_LI) and link to the official download page.

See [LICENSE.txt](LICENSE.txt) for the full terms.

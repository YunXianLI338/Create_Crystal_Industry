# Changelog

This file records what each push brings in, limited to what a player can experience: what is
visible in game, what can be obtained, what can be changed in the config. Wording in the READMEs and
the language files, code comments, how a Ponder blueprint is laid out and internal implementation
are all left out.

Version numbers follow `mod_version` in `gradle.properties`, and an entry is filed under the version
it shipped as. The date is that of the last commit in the batch. Coverage starts at the 1.0.0
release; the earlier development cycle (1.0.0-beta.*) is not covered. Once a batch is done, add a new
section at the top.

---

## 1.0.10 · 2026-10-30

### Added

- The Smart Drill, Mechanical Accelerator and Mechanical Cleaner item tooltips now carry a Create-style Stress Impact entry, with the exact multiplier shown while wearing Engineer's Goggles.
- Both the Electric Accelerator and the Mechanical Accelerator can be quickly dismantled with a sneak right-click of a Wrench, and rotated with a normal right-click.
- While the player's view is inside Flammable Ice Slurry, the screen is tinted by a haze of the block's average texture color, matching vanilla Powder Snow.

### Changed

- Ancient Debris Budding Blocks, Buds and Clusters are no longer destroyed by lava or fire when dropped as items, matching vanilla Netherite items.
- The Resonance Filter's summary is now always shown; holding Shift no longer hides it.
- Right-clicking the Mechanical Cleaner's bottom (shaft) face no longer opens its screen.
- The Mechanical Accelerator's stress impact is reduced from 32x to 16x.

### Fixed

- A Deployer no longer hands back an extra Glass Bottle after turning a Flammable Ice Slurry Bottle into a Flammable Sundae.
- The Mechanical Cleaner screen now shows the "Inventory" label above the player's inventory.
- The Mechanical Cleaner's "=" mode now gathers a full batch across slots when sucking from a container, but takes exactly that many items instead of swallowing partial stacks.
- The Mechanical Cleaner's direct container transfer now runs at a cadence set by its rotation speed instead of ignoring it.
- Looking at a stock ticker through a Tunable Portable Ticker from beyond the simulation distance no longer puts everything into "Unsorted" when the categories are built from Resonance Filters.
- The Mechanical Cleaner's suction range and the Pressure Relief Hatch's nozzle-field check now follow the volume a Nozzle actually spreads, so drops outside the mesh are no longer pulled in.

## 1.0.9 · 2026-10-18 ~ 2026-10-29

### Added

- New enchantment "Airflow Resistance": any single piece of armor with it makes the wearer immune to the push of Encased Fans and Nozzles, and to Wind Charge knockback.
- The Resonance Filter screen now has four switches beneath the panel: blacklist, whitelist, match item data and ignore item data, with the active one marked by a green frame.

### Changed

- The Mechanical Accelerator's shaft now uses Create's half-shaft model.
- A powered Mechanical Cleaner now looks the same as an unpowered one, with only the textures swapped.
- Pressure Relief Hatches, Smart Pressure Relief Hatches and Smart Temperature Chambers can be dismantled by sneak-using a Wrench.
- Flammable Ice Slurry Bottles now stand upright on Depots and Belts.
- Flammable Ice Slurry Bottles no longer grant Frost Walker, and the Flammable Sundae's Frost Walker now lasts 60 seconds.
- Status effects are now classed by sign: Electrified, Frozen and Scorching Cold read as harmful, Flame Breath and Frost Walker as beneficial, with their tooltips shown in red or blue.
- The Resonance Filter no longer opens its screen while unconnected to a network, and right-clicking it only says that it is unconnected.
- A Resonance Filter that is not connected to a network filters only itself when placed in a filter slot, and its tooltip says so.

### Fixed

- Connected textures on Flammable Ice Pillars and Layered Flammable Ice no longer fail to appear in some environments.
- Smart Temperature Chambers now join their connected textures as soon as they are placed, removed or loaded, instead of waiting for a block update.
- Smart Temperature Chambers only draw their connected textures between chambers that actually share capacity, so two pieces left side by side after one is removed no longer join up.
- Smart Temperature Chambers now rejoin only as rectangles after one piece is removed, instead of carrying the old connections across the gap.
- A Pressure Relief Hatch now vents a single kind of item per batch instead of mixing several kinds into one batch.
- Blaze Burners filled to 500 seconds no longer accept Flammable Ice, so the item in hand is not spent for nothing.

## 1.0.8 · 2026-10-09 ~ 2026-10-17

### Added

- A new block, the Pressure Relief Hatch: mounted on a container, it hands the container's items to an air current passing by in batches of 16, further capped by each item's stack size, with the side slot available as a filter.
  - A sideways sweep, a flow leading out along its facing, and the cubic field a Nozzle spreads all open it, and only a head-on flow into its face closes it.
  - The hatch blocks neither air nor skylight: whatever sits downstream still receives its air, and skylight passes through its whole opening.
- A new block, the Smart Pressure Relief Hatch: it adds an amount slider to the side slot (up to 64 per batch) and stops working whenever any of its six sides is powered, switching to a powered look.
- The Mechanical Cleaner now picks up Packages lying on the ground, not just dropped items; while waterlogged it opens a Package on the spot, moving the contents into its inventory and consuming the packaging together with any empty Package, and leaves a Package whole when its contents do not all fit.
- With a Nozzle in front of the Mechanical Cleaner, the cubic field the Nozzle spreads is recognised as well, and drops and Packages landing in it go straight into the inventory, including points at the edge of the field or off the Cleaner's axis.
- The Resonance Filter gained a right-click screen that names the network it sits on and stores the name in the world save, shared by every filter and Resonance Table on that network (the filtering rules still come from the tables); Resonance Tables also float that name above themselves, visible within 64 blocks and left out while the network is unnamed.
- Smart Temperature Chambers placed next to each other share their capacity, and the grouping records placement order in the block: a rectangle placed earlier keeps its shape, a chamber added later leaves a connected part intact, and two crossing strips do not take each other's middle block.
- Accelerators gained an Acceleration Filter Mode: `Whitelist` accelerates only the blocks on the list, `Blacklist` skips them, and the default `Unlimited` behaves as before; the whitelist ships with budding blocks, crops and saplings, the lists affect only the accelerators, and the world's own random ticks still land on every block.
- Ponder scenes for the Pressure Relief Hatch and the Smart Pressure Relief Hatch, covering venting on a sideways current, the reach of a Nozzle's field, and the smart hatch's amount slider and redstone lock.

### Changed

- The Pressure Relief Hatch hands over a fixed 16 per batch; only the Smart Pressure Relief Hatch's amount slider reaches 64.
- The Mechanical Cleaner hides its amount slider while sucking in the open (with no container in front), leaving only the filter.
- Smart Temperature Chamber shared capacity now reaches 16x16: a full 16x16 block still forms one group, and its connected textures still merge into one surface.
- Flammable Ice fed to the Blaze Burner no longer stops after two pieces but tops up piece by piece like coal, up to the same cap as any ordinary fuel; it still drives the burner superheated, and only how much can be stockpiled has changed.
- A waterlogged Mechanical Cleaner no longer opens Packages whose contents are not items: copper fluid Packages, and copper bottles or brass batteries carrying fluid or energy, can still be picked up but stay whole in the inventory.

### Fixed

- A Smart Drill placed horizontally now renders the filter items on its top and bottom slots turned with the block.
- A Funnel no longer stops working after its ejected item is taken straight into the Mechanical Cleaner's inventory.

## 1.0.7 · 2026-10-06 ~ 2026-10-08

### Added

- Flammable Ice Slurry: a fluid with **no block form**, moved around in buckets just like powder
  snow. Pouring it places a Flammable Ice Slurry block (you sink into it, and it freezes you), and
  an empty bucket scoops it back up.
- The Flammable Ice Slurry Bottle and the chain around it: a Mechanical Mixer stirs Flammable Ice
  into slurry, a Spout fills a glass bottle with it, an Item Drain pours it back out, and a Deployer
  holding Magma Cream presses a bottle into a Flammable Sundae. The bottle can be drunk as well,
  granting Frozen just like the sundae.
- Every Flammable Ice item now works as fuel, in furnaces and in the Blaze Burner; the Slurry Bottle
  and the Flammable Sundae hand the empty glass bottle back once burnt.
- With other mods' reskins of the Blaze Burner, the whole Flammable Ice set drives them into their
  cooled state too.
- The Flammable Ice Slurry fluid works as fuel as well: piped into a Blaze Burner with Straw, it
  reaches superheated.
- The Flammable Ice Slurry can now be dumped into the world: an open pipe end or a Hose Pulley
  places one Flammable Ice Slurry block per bucket.
- A new status effect, Scorching Cold: frozen and burning at the same time. The screen frosts over and
  you shiver as before, but flames cling to you, costing half a heart every 20 ticks. The Flammable
  Ice Slurry Bottle and the Flammable Sundae grant it now instead of Frozen, and so does stepping
  into Flammable Ice Slurry, which no longer freezes you the powder-snow way.
- Items dropped into Flammable Ice Slurry catch fire and keep burning, and are gone after five
  seconds of it, the same way fire treats them. Netherite-tier items cannot be lit at all.
- Flame Breath now keeps embers rising off you for its whole minute, and the jet it spits throws off
  sparks of its own.
- In creative, middle-clicking a Flammable Ice Slurry block now gives you the Slurry Bucket. The
  block has no item form of its own, so picking it used to do nothing.
- Crystal clusters can now be fed to Crushing Wheels. Crushing yields **twice the mining amount minus
  one**, plus a 50% chance of one more (31–32 for a Redstone Cluster; 13–14 for a Lapis Cluster,
  taking the middle of its 4–9; and 3–4 for an Ancient Debris Cluster, the middle of its 1–3).
- A new status effect, Frost Walker: for the 10 seconds after drinking a Flammable Ice Slurry Bottle
  or a Flammable Sundae, the water under your feet freezes, so you can walk straight across it.
- The Smart Drill's Ponder gained a Redstone Control scene: redstone power locks it, and only the
  face it points at is unaffected by redstone.
- The Smart Temperature Chamber gained Ponder scenes: one for heating a Basin, one for shared
  capacity, and one for heating a Steam Boiler.
- A locked Smart Drill now shows its redstone indicator on the sides as well, not only on top.
- The Flammable Ice building blocks are shelved in this mod's own creative tab as well, not only in
  Create's Building Blocks tab.
- A new block, the Smart Temperature Chamber: place it directly beneath a Basin, feed it Flammable
  Ice Slurry, and that Basin **ignores its heat requirement and just runs** — whether the recipe asks
  for heated, superheated, or a cooled state added by another mod. It carries its own 1000 mB tank
  that pipes and buckets can fill, and the Engineer's Goggles read out both the level and how much
  slurry the group burns per second. The block is transparent and lets light through.
- Smart Temperature Chambers built as one block **share their capacity**. Chambers on the same
  level, edge-adjacent to one another, are cut into rectangles which each form a group, and a
  group's capacity is the sum of its parts (four of them make 4000 mB): a pipe into any one of them
  fills the whole group, and fuel added to any one lights them all at the same instant. They do
  **not** equalise fuel between themselves — when nothing is burning, each keeps exactly what it
  had. Stacking them vertically does not group them. The shape does not have to be a rectangle
  itself: a 3x3 missing one corner is cut into the 2x3 inside it, with the remaining two forming a
  group of their own.
- Smart Temperature Chambers have connected textures: the frames between chambers that **share
  capacity** drop away, so their sides and bases merge and a block of them reads as a single machine.
  Chambers that merely sit next to each other without landing in the same rectangle stay separate —
  connecting and sharing are the same test.
- Smart Temperature Chambers can now heat a Steam Boiler: put one underneath and, while it burns,
  it supplies the same top heat level as a Blaze Burner, and stops when the fuel runs out.

### Changed

- The Flammable Sundae no longer grants Fire Resistance. That resistance cancelled the Scorching Cold
  burn outright, which would have made the Magma Cream on top pointless.
- The Mechanical Cleaner's Ponder says the right thing about airflow direction now: blowing or
  sucking is switched in its interface, and the side slot only handles filters and amounts.
- Flammable Ice Slurry blocks are carried along by contraptions instead of being dropped halfway.
  The Slurry Bucket is registered in the common bucket tag, so other mods treat it as a plain bucket.
- Night Vision Goggles on an Armor Stand now stay in their worn form instead of flickering between
  worn and unworn as the ambient light changes.

## 1.0.6 · 2026-10-05 ~ 2026-10-06

### Added

- A Flammable Ice decoration set, 18 blocks in all: Flammable Ice Bricks, Slab, Stairs and Wall;
  Brick Slab, Brick Stairs and Brick Wall; Flammable Ice Pillar; Cut Flammable Ice (with its own slab,
  stairs and wall); Layered Flammable Ice; and Small Flammable Ice Bricks (with their own slab,
  stairs and wall). The whole set is shelved in Create's Building Blocks tab, next to Create's own
  palette blocks.
  - Recipes follow Create's stone palette: the pattern blocks (cut, bricks, small bricks, layered,
    pillar) come out of a **stonecutter**, the slab is 3 into 6, the stairs 6 into 4 and the wall
    6 into 6, and two slabs combine back into one block.
  - Stonecutting takes a "flammable ice stone type" tag holding everything in the set except slabs,
    so **any shape can be cut into any other**. Slabs stay out of it: one block cuts into two of them
    and two of them press back into a block, so letting them in would be a cheap universal
    conversion.
  - Layered Flammable Ice and the Flammable Ice Pillar use connected textures: neighbours merge into
    one continuous surface.
  - Stacking two Flammable Ice Slabs into a single block shows the two-slab side, so it no longer
    reads as a plain Flammable Ice Block.
- Flammable Sundae: a snack and an ornament in one. Sneak-use to place it and use to take it back;
  drinking it leaves an empty bottle and grants Frozen and Fire Resistance; set down, it freezes the
  water around it; and it can be lit like a candle.
- The Flame Breath effect: after drinking a Flammable Sundae, sneak-use with an empty hand to breathe
  a beam of fire that smelts the dropped items in its path (including ones riding belts and depots)
  and blinds, slows and sets fire to the mobs it hits.

### Changed

- The Echo Spyglass lies flat on depots and belts instead of standing upright.

### Fixed

- On the JEI budding block page, fluid-burning budding blocks (Budding Ancient Debris) no longer
  render as the empty-tank variant.

## 1.0.5 · 2026-10-03 ~ 2026-10-04

### Added

- Budding block conversion is now a single recipe type, "infection"
  (`create_crystal_industry:budding_conversion`), and datapacks can write it. One recipe is one rule:
  which budding block, the chance per random tick, the pick radius, and what turns into what. Leaving
  the output empty means the budding block itself, which is how budding blocks spread. The 21 built-in
  ones are emitted by data generation: overriding one in a datapack means dropping in a JSON with the
  same id, and dropping in a new one adds a rule. Stone to ore, raw iron block to a budding block and
  sculk spread are no longer separate things.
- A JEI "Infection" page: what each budding block turns into what, at what chance and over what
  radius, all on one page.
- KubeJS `.transform(input, output, n[, r])` adds infection rules to scripted budding blocks. The
  input accepts `#block_tags`, a budding block can carry several rules with their own chance and
  radius, and the fourth argument is the pick radius. On a built-in block, `CustomBudding.modify(...)`
  appends such rules without replacing the block's own.
- A hidden advancement, "Causality Dissolved": put a Resonance Filter back on a table of its own
  network and the infinite self-reference blows it apart (knockback only, no damage to blocks or
  players).

### Changed

- The config file is split into tables by feature: `[worldgen]`, `[infection]`, `[accelerator]`,
  `[spyglass]`, `[crystalBattery]` and `[cleaner]` (plus `[goggles]` client side), so keys now carry
  the table name as a prefix (e.g. `worldgen.generate_raw_iron`). **An old config is backed up as
  `.bak` and rewritten in the new shape, so any value you changed goes back to the default.**
- The infection switches were widened in meaning: the master switch
  `infection.buddingInfection` now covers every conversion (turning it off also stops stone growing
  into ore), and any budding block left out of `infection.infectingBudding` does nothing at all.
- The four soul sand options were merged into one switch, `worldgen.soulSandGenerate`. Count, spread
  and sink depth are fixed at their old defaults, so generation is identical down to the block.
- The goggles report the real growth rate: budding blocks that only run at full speed in the Nether
  (quartz, glowstone) now show a discounted number outside it instead of always reading
  "natural growth (×1)".
- A Resonance Table can now read a Resonance Filter from another network sitting on top of it, and
  that chains (a table on network A can read what network B filters, and B can read C). An empty
  table combined with a filter whose rules cannot be read now lets nothing through, instead of
  quietly degrading to letting everything through.
- Ponder rework: one scene per feature, 16 in total (4 for budding blocks and the Accelerators, 3
  for the Smart Drill, 3 for the Mechanical Cleaner, 6 for the Resonance family). Text dropped from
  5-7 lines per scene to 2-3, with block changes, item movement and gesture cues covering the rest.
  The Resonance Filter item now carries the same full set of scenes as the table.
- The Resonance family scene text and demos were redone: every line names the Resonance Table, two
  headers became "Automating the Filter" and "Configuring the Filter with Several Tables", and a new
  "List Filters and Attribute Filters" scene was added.
- The JEI budding info page drops the lines only an author cares about (config keys, raw JSON,
  registration source); biomes and dimensions use their translated names (ore budding blocks read
  "Overworld biomes", quartz and ancient debris read "Nether biomes"); and the fluid line is just
  "Growth consumes X", leaving how much is in the tank to the goggles tooltip and the tank's own UI.

### Fixed

- An infection recipe whose `budding` names some other block (obsidian, say) never fires; JEI no
  longer draws a fake page for it and instead logs which recipe and which block it was.
- Fixed the JEI budding info page's Fluix entry pointing at a section that had been renamed.

## 1.0.4 · 2026-10-02

### Changed

- Budding Glowstone's chance to replace Glowstone: default 0.5 → 0.1 (config key
  `glowstoneBuddingChance`).
- Three new Ponder scenes for the Resonance family: the table, the filter, and a Display Link
  reading a table.
- The text of the five existing Ponder scenes was rewritten: the subject is the machine or the
  mechanic itself, no second person, no em dashes, no semicolons in Chinese.

## 1.0.3 · 2026-10-02

1.0.3-beta.1.4 promoted to release. No further changes, see the next section.

## 1.0.3-beta.1.4 · 2026-09-28 ~ 2026-10-02

### Added

- Budding Arclight and Budding Ancient Debris, and the Current Slurry chain behind them.
  - Budding Ancient Debris: veins in the Nether at Y 8–22, same mining tier as Ancient Debris. The
    only budding block that runs on lava, carrying a 1 B lava tank and spending 250 mB per
    successful growth; with less than that stored nothing grows. The cluster drops 1–3 Netherite
    Scraps (Fortune applies), and the block drops 1 Ancient Debris on a normal break. Naturally
    generated ones come with an empty tank.
  - Budding Arclight: the only budding block that runs on FE. It holds 1,000,000 FE internally and
    spends 10,000 FE per successful growth, receive-only. The cluster drops 1 Arclight (a new item,
    item form only). It does not generate naturally and has no recipe yet.
  - Current Slurry: the mod's first fluid, light level 15. Anything living in it keeps taking 20
    armour-piercing shock damage. Made by pressing one Arclight in a Basin, superheated, for
    500 mB.
  - The "Electrified" effect: gained within 3 blocks of an arclight block or of Current Slurry,
    fading about 3 seconds after leaving. An electrified entity that takes damage takes 4 more
    shock damage itself and discharges to the nearest electrified entity within 5 blocks, drawing a
    spark between the two; the discharge counts as damage taken, so the chain keeps running. Shock
    was split into two damage types, both armour-piercing true damage, sharing one set of death
    messages.
  - `#create_crystal_industry:shock_immune` armour (the four chainmail pieces, the four netherite
    pieces and Create's three netherite diving items, 11 in all) is immune to the slurry's shock and
    to real lightning, and no longer gains Electrified.
- Inactive Budding Arclight: a decorative block that turns into Budding Arclight when struck by
  lightning (or when a lightning rod directly above it is struck), arriving fully charged and
  sprouting buds on the spot. Acquisition is undecided, creative only for now.
- The Resonance Table and the Resonance Filter. The filter stores no rules of its own: every test
  reads the items sitting on each table in its network and takes the union. A List Filter on a table
  has its allow/deny list and its 18 slots mirrored whole, an Attribute Filter mirrors its attribute
  set, and a plain item degrades to a type match. Change what sits on a table and every machine
  holding that filter changes its mind on the next test. When the network cannot be read, nothing
  passes.
  - Networking: sneak-right-click an existing table while holding a table to make the held one
    remember that network and join it when placed; sneak-right-click a table while holding a filter
    to link it.
  - A table accepts redstone power, freezing its filter at the moment it is powered.
  - A new Display Link source lists everything the network is filtering onto a Flap Display.
- Recipes for both (the table is Polished Rose Quartz / Electron Tube / Brass Casing in a vertical
  column, the filter is Polished Rose Quartz / Wool / Polished Rose Quartz in a row).
- KubeJS: `.needfluid(fluid id or #tag, mB per growth, tank capacity in mB)` lets a scripted budding
  block run on a fluid; `CustomBudding.modify(block id, options)` retunes the chance, light range,
  water requirement, growth environment, fluid tank, drops and mining tier of any existing budding
  block.
- Lava turns into deepslate with soul soil below it and a Flammable Ice Block beside it, with
  vanilla's fizzle sound.

### Changed

- Budding Ancient Debris switches to the unpowered texture when the tank cannot pay for a growth;
  growth chance 1/50 → 1/20.
- `needfluid` accepts fluids with data components, down to one specific potion. Fluid names,
  dimension names, biome names and biome tag names are now shown in the player's language.
- The lava tank of Budding Ancient Debris became a generic tank, so a block already placed in an old
  save loses its tank contents once (the block itself keeps working).
- The growth speed config keys are gone; each budding block's tier is fixed: Redstone and Lapis are
  fast, Ancient Debris very slow, everything else normal, matching the old defaults one for one.
- The Accelerators only tick neighbours that can receive a random tick.
- Arclight Clusters no longer take Fortune.
- In the Engineer's Goggles, only the inserted dimension and biome names in the three
  growth-condition lines are tinted; the sentences themselves stay grey.
- All 32 advancement descriptions rewritten to state their trigger, and to mention budding block /
  cluster where it fits.
- The Arclight family (Arclight and the inactive block) and the Current Slurry Bucket are held back
  from the creative tab. They are still registered and obtainable with `/give`.
- The network outline switches from two alternating colours to a single colour sliding back and
  forth along a rose quartz ramp (0.01 ≈ a 5-second cycle).
- Textures and models for the Ancient Debris family and the Resonance family.

### Fixed

- Right-clicking a budding block with a full lava bucket no longer pours lava onto the floor (a
  bucket that cannot be used is swallowed; sneak-right-click still pours it deliberately).
- Crash caused by the network outline on an integrated server.

## 1.0.3-beta.1.3 · 2026-09-25 ~ 2026-09-28

- Buds and Clusters joined the common tags `c:buds` / `c:clusters`, so other mods filtering blocks
  by category can see them.
- The Night Vision Goggles and the Echo Spyglass overlays no longer interleave with the chat and the
  hotbar.
- Fixed the Smart Drill dropping an extra item on a contraption: in Silk Touch mode a budding block
  also dropped its normal-break product.
- Fixed buds and clusters registered by a KubeJS script being unable to reach a contraption's
  storage.
- The Fast growth tier now defaults to Redstone and Lapis, and the Normal tier no longer contains
  them (otherwise the same id appeared under two config keys); the Echo Spyglass scan radius
  defaults to 64 instead of 16.

## 1.0.3-beta.1.2 · 2026-09-25

- Fixed a dedicated server crash on startup.

## 1.0.3-beta.1.1 · 2026-09-25

- Fixed the Echo Spyglass icon vanishing from the hotbar while in use.

## 1.0.3-beta.1.0 · 2026-09-25

- Fixed the Echo Spyglass outline flickering under Iris.

## 1.0.2 · 2026-09-21 ~ 2026-09-25

### Added

- The Crystal Battery: placing another layer on an existing 2x2 / 3x3 structure completes the whole
  layer; right-clicking crystals fills every empty slot in the whole block, sneak-right-clicking
  fills only the clicked slot, and creative mode consumes nothing; its own textures and models;
  contraption compatibility; it lights up with charge.
- Growth environment: a dimension list plus biome conditions (a concrete biome id, a `#biome` tag,
  or a climate keyword cold·warm·hot), `!` negates, and both written together intersect. Outside
  the allowed area each successful roll makes a second roll against `outsideGrowthChance`. KubeJS
  mirrors it with `.growthDimensions(...)` / `.growthBiomes(...)` / `.outsideGrowthChance(0.1)`.
- The Echo Spyglass scans faster, keeps the ring of results around the player rather than whichever
  side was scanned first when the view is full, and skips the redraw when two scans in a row return
  the same result.

### Changed

- The Engineer's Goggles and JEI no longer print concrete numbers for growth conditions (chances,
  seconds and Y ranges all became qualitative, "slow", "deep underground · very rare"), and a
  budding block with an environment requirement says "only grows fastest in X".
- The half-finished Crystal Battery was pulled from the creative tab (the block and item are still
  registered and obtainable with `/give`).

### Fixed

- Fixed the bubble column direction beside the Flammable Ice structure: it pointed down, now up.
- Fixed the Crystal Battery baking its connected texture with stale data after loading, which made
  the whole top face disappear.

## 1.0.2-beta.1.1 · 2026-09-20

- "It Really Does Grow" now checks the block: breaking a complete cluster completes it. The old
  trigger required Silk Touch on the player's very first break.

## 1.0.2-beta.1.0 · 2026-09-20

- 32 advancements in 5 chapters, on a tab of their own. Picking items up and placing blocks use
  vanilla triggers; the signature moments (a successful acceleration, watching it grow all the way,
  a Silk Touch harvest, waking the Warden) are granted from code.
- Fixed the Echo Spyglass: a flat icon in the GUI while the model in hand stays 3D (the advancement
  icon used to be blank), the item frame orientation corrected, and the icon no longer drawn twice.

## 1.0.1 · 2026-09-19 ~ 2026-09-20

### Added

- KubeJS budding blocks can customise their break sound and their mining tool.
- The mining tool split into `buddingTool` / `stageTool`, both defaulting to a pickaxe; the old
  `tool('hoe')` spelling is kept as shorthand for setting both at once.
- Mining tiers `buddingLevel` / `stageLevel` (`stone` / `iron` / `diamond`, and tag-style spellings
  like `needs_stone_tool` are accepted too). Setting a tier makes the tool type a hard requirement as
  well: the right tier with the wrong tool drops nothing.

## 1.0.1-beta.1.0 · 2026-09-20

- Pre-release version number, plus a mod description (shown in the mod list and on the project
  page). This number was dropped in the next commit, which restarts at 1.0.2; 1.0.1 had already been
  released.

## 1.0.0 · 2026-09-18 ~ 2026-09-19

### Added

- KubeJS growth definitions support a minimum light `minLight`, which together with `maxLight` forms
  a closed range; writing only one end is fine, and a floor above the ceiling fails on the spot. The
  Engineer's Goggles readout and the JEI Budding Block Info page each gained a "light ≥ n" line.

### Changed

- Loot from scripted clusters now benefits from Fortune, adding 0 to level items per level, matching
  this mod's own cluster loot tables; Silk Touch still drops the cluster itself.

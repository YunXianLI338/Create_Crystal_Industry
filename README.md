# 机械动力：晶簇工业

**Create: Crystal Industry** · Minecraft 1.21.1 · NeoForge · Create 6.0.10+

**[English](README_EN.md) | 简体中文**

把原版紫水晶母岩的生长机制推广到其它矿物：母岩从六个面向外长出芽与晶簇，母岩自身不被消耗，采收晶簇后继续生长。矿石母岩还会把周围的石头转化为对应矿石，因此一处母岩等于一座会自行扩张的矿脉。

---

## 一、母岩

母岩在六个面上各自判定生长。某个面为空位时长出**小型芽**，已有芽时按 **小型芽 → 中型芽 → 大型芽 → 完整晶簇** 依次进阶，晶簇是终态。判定由随机刻触发，通过则推进一级；母岩本身不参与消耗，采走晶簇后原位置继续生长。

### 母岩一览

「相邻转化」的工作方式是：母岩每随机刻以 1/20 的概率掷一次，通过后在**半径 1 格（3×3×3）范围内随机取一格**，该处是石头或深板岩时替换为对应矿石。因此产出取决于这个立方体里有多少格是石头或深板岩——把母岩埋在石头中等同于提高命中率。回响母岩的转化半径与概率不同于此，见表中标注。

| 母岩 | 额外生长条件 | 晶簇产出 | 相邻转化 |
| --- | --- | --- | --- |
| 玫瑰石英 | — | Create 玫瑰石英 | — |
| 粗铁 | — | 粗铁 | 铁矿石 / 深层铁矿石 |
| 粗金 | — | 粗金 | 金矿石 / 深层金矿石 |
| 粗铜 | — | 粗铜 | 铜矿石 / 深层铜矿石 |
| 粗锌 | — | Create 粗锌 | 锌矿石 / 深层锌矿石 |
| 钻石 | — | 钻石 | 钻石矿石 / 深层钻石矿石 |
| 绿宝石 | — | 绿宝石 | 绿宝石矿石 / 深层绿宝石矿石 |
| 青金石 | — | 青金石 ×4–9 | 青金石矿石 / 深层青金石矿石 |
| 红石 | — | 红石 | 红石矿石 / 深层红石矿石 |
| 石英 | 仅在下界满速，其它维度通过判定后再掷 1/2 失败 | 下界石英 | 下界岩 → 石英矿 |
| 荧石 | 仅在下界满速，同上 | 荧石粉 | — |
| 回响 | 生长格亮度必须为 0 | 回响碎片 | 半径 2 格内的泥土、沙、石头、凝灰岩等 12 种方块 → 幽匿（1/4） |
| 可燃冰 | 目标格必须是水源方块 | 可燃冰 | — |
| 福鲁伊克斯（需 AE2） | 须接入已激活的 ME 网络（占用 1 个频道），每次生长消耗 200 AE | 福鲁伊克斯水晶 | — |

掉落规则：

| 方块 | 普通破坏 | 精准采集 |
| --- | --- | --- |
| 晶簇 | 表中「晶簇产出」一栏的物品，受时运加成 | 晶簇方块本体 |
| 芽 | 无掉落 | 芽方块本体 |
| 母岩 | 比自身低一档的方块（粗铁母岩掉粗铁块、钻石母岩掉钻石块） | 同上，不因精准采集而改变 |

母岩方块本体无法用普通工具采下，只有智能钻头的精准采集模式能取得，见下节。

另有三处方块属性值得注意：红石母岩与它的各级芽、晶簇都自带红石信号（母岩 15，芽依次 3 / 7 / 11，晶簇 15），可直接作红石源；荧石母岩与它的芽、晶簇都会发光（母岩 15，芽依次 3 / 7 / 11，晶簇 15）；回响母岩的芽与晶簇则刻意不发光——一旦发光就会顶掉自己的生长格。

### 母岩再生

矿石母岩除转化矿石外，还会以 **1/25000** 的概率把紧邻的对应矿块"传染"成新的母岩。石英母岩侵蚀的是平滑石英，福鲁伊克斯母岩侵蚀的是福鲁伊克斯块（该转化还需 ME 网络供电）。

| 母岩 | 可被转化为母岩的方块 |
| --- | --- |
| 粗铁 / 粗金 / 粗铜 / 粗锌 | 对应粗矿块 |
| 钻石 / 绿宝石 / 青金石 | 对应矿物块 |
| 红石 | 红石块 |
| 石英 | 平滑石英 |
| 福鲁伊克斯 | 福鲁伊克斯块（需供电） |

### 生长速度

| 档位 | 每次随机刻的推进概率 | 配置项 |
| --- | --- | --- |
| 极慢 | 1/50 | `growthSpeedVerySlow` |
| 慢 | 1/20 | `growthSpeedSlow` |
| 正常（默认） | 1/5，与原版紫水晶母岩同速 | `growthSpeedNormal` |
| 快 | 1/1，抽中必定推进 | `growthSpeedFast` |

四个配置项各填一串**母岩家族 id**（即世界生成开关里 `generate_<id>` 的 `<id>`，如 `raw_iron`、`diamond`、`echo`）。`growthSpeedNormal` 默认列出全部母岩；未被任何列表提到的母岩按「正常」处理，因此把该列表清空也不会改变行为。同一母岩同时出现在两个非「正常」档时取更慢的一档，并在日志中告警。

---

## 二、催生器

原版母岩平均约 68 秒才等到一次随机刻，自然生长几乎不可观测。催生器对**相邻六个面各施加一次随机刻**，把这一过程压缩到数秒。

| | 电力催生器 | 动力催生器 |
| --- | --- | --- |
| 供能 | FE | 背面接入旋转力 |
| 每次催生消耗 | 100 FE | 不耗电 |
| 触发节奏 | 每 `acceleratorIntervalTicks` tick 一次（默认 1，即每 tick） | 同左 |
| 强度 | 固定，每次必定施加 | 随转速线性增长，256 RPM 封顶 |
| 满速时效果 | 6 随机刻 / tick | 与电力催生器相同 |
| 内部缓存 | 10 000 FE，最大输入 100 FE/t | — |

- 相邻的电力催生器之间会自动均分电力：能量较多的一端向较少的一端推送，单次推送不超过差值的一半，避免两端来回震荡。
- 动力催生器低于 256 RPM 时按转速比例生效，超过 256 RPM 不再增强。
- 配置项 `acceleratorIntervalTicks`（`催生间隔（tick）`）同时作用于两种催生器，数值越大越慢。电力催生器按「每次催生」结算 FE，因此调慢它也同时降低耗电；护目镜显示的倍率会跟随该配置变化。

> **随机刻驱动的远不止晶簇。** 作物、树苗、铜的锈蚀、地狱疣等一切依赖随机刻的机制都会被一并加速，因此催生器同时也是一台通用加速器。

戴上工程师护目镜看向**原版紫水晶母岩**，同样会显示生长速度与倍率。原版方块本身没有任何改动，信息由客户端按需生成，存档中因此不会多出数据。

---

## 三、机械与装备

| 物品 | 说明 |
| --- | --- |
| **智能钻头** | 机械钻头，两种模式，可用侧面的设置槽随时切换。**普通采集**：采集速度为普通机械钻头的 2 倍。**精准采集**：速度与普通钻头持平，但方块按精准采集附魔的方式完整掉落。母岩是特例：普通模式只会把它打碎（掉低一档的方块），只有精准模式才能采下母岩方块本身。可装过滤物品限定破坏目标（过滤为空则破坏一切）。 |
| **动力吸尘器** | 拥有鼓风机的全部气流功能（吹气/吸气、过滤、风力格数 1–20 可调），并额外把气流中的物品吸入自身的 27 格库存；无动力时仍能被动吸取正前方的物品。正前方若是容器，则与容器直接交换物品，全程不向世界掉落任何物品。**注水后吹气**可对物品执行洗涤加工，作用等同于鼓风机的洗涤。 |
| **夜视仪护目镜** | 用工程师护目镜加回响碎片改装而成，佩戴后按键切换夜视（默认 `N`，可在按键设置中修改），同时免疫黑暗效果。可装备于头盔槽或 Curios 头盔槽。 |
| **回响望远镜** | 使用时视线穿透障碍，把方块显示为轮廓，单次使用最长 60 秒。潜行右键打开过滤界面，可放入物品或 Create 的列表过滤器；不放过滤时默认识别 `#c:ores`（全部矿物）。 |
| **可燃冰** | 可燃冰（400 tick）与可燃冰块（4000 tick）均可作燃料，并列入 Create 烈焰人燃烧室的过热燃料；9 个可燃冰可合成为一块可燃冰块。 |

---

## 四、世界生成

各母岩按对应矿物的深度生成，通常嵌在矿脉之中。

| 母岩 | 维度 | Y 范围 | 生成方式 |
| --- | --- | --- | --- |
| 钻石 | 主世界 | −64 – 16 | 矿脉，rarity 1/16 |
| 绿宝石 | 主世界 | −16 – 320 | 矿脉，rarity 1/16 |
| 粗铁 | 主世界 | −24 – 56 | 矿脉，rarity 1/16 |
| 粗金 | 主世界 | −64 – 32 | 矿脉，rarity 1/16 |
| 粗铜 | 主世界 | −16 – 112 | 矿脉，rarity 1/16 |
| 粗锌 | 主世界 | −63 – 70 | 矿脉，rarity 1/16 |
| 青金石 | 主世界 | −64 – 64 | 矿脉，rarity 1/16 |
| 红石 | 主世界 | −63 – 15 | 矿脉，rarity 1/16 |
| 石英 | 下界 | 基岩上方 10 — 顶部下方 10 | 矿脉，rarity 1/8 |
| 荧石 | 下界 | 天然荧石团处 | 替换荧石团最低一块，概率由 `glowstoneBuddingChance` 决定（默认 0.5）；可另配 `glowstoneGenerateBuds` 等项控制附带的芽 |
| 回响 | 主世界 | −64 – 0 | 深暗之域，生成于幽匿之中 |
| 可燃冰 | 主世界 | 深海海床之下 | 结构，每区块 1/256（`flammableIceChance`），周围可散布灵魂沙 |
| 玫瑰石英 | — | — | 不自然生成 |
| 福鲁伊克斯 | — | — | 不自然生成 |

每一种都能在配置文件中单独开关（`generate_<母岩 id>`）。可燃冰结构与荧石母岩另有各自的概率配置项。

> **自然生成的回响母岩被破坏时会召唤监守者。** 判定依据是方块的 `can_summon` 状态，因此你自己放置的回响母岩不会触发。

---

## 五、兼容性

| 模组 | 关系 | 说明 |
| --- | --- | --- |
| Create | 必需 | 6.0.10+ |
| AE2 | 可选 | 启用福鲁伊克斯母岩；AE2 的催生器（Growth Accelerator）同样能加速本模组的母岩，护目镜的倍率会把它计入 |
| Curios | 可选 | 夜视仪护目镜可装备于 Curios 头盔槽（不装 Curios 时走原版头盔槽） |
| JEI | 可选 | 提供「母岩信息」页：每个母岩一页，写明生长条件、生长速度与生成条件，母岩及其芽、晶簇、晶簇产物均可反查 |
| KubeJS | 可选 | 用脚本注册自己的母岩，见下节 |

未安装 AE2 时模组正常启动，只是不注册福鲁伊克斯相关内容。所有机器均提供工程师护目镜信息面板；母岩（含原版紫水晶母岩）、电力与动力催生器、智能钻头、动力吸尘器均附带 Ponder 教学场景。

模组共提供 31 项进度，分为母岩、催生器、机械、装备、深海与深暗五个分支。

---

## 六、用 KubeJS 添加母岩

本模组的生长引擎是公开的，可以用 KubeJS 注册自定义母岩，数量不限，不需要写 Java、也不需要数据包。

**一行注册一整族**：母岩本体、小芽、中芽、大芽、晶簇五个方块一次全部生成。脚本放在 `kubejs/startup_scripts/` 下，文件名随意，以 `.js` 结尾即可。

```js
StartupEvents.registry('block', event => {
  CustomBudding.create(event, 'example_crystal', 20)   // 概率 1/20
})
```

生成的方块 id 为 `kubejs:example_crystal_budding` 与 `_small_bud` / `_medium_bud` / `_large_bud` / `_cluster`。母岩的随机刻已接入生长引擎，芽与晶簇自带 `FACING` 属性（朝向母岩的方向生长）。默认借用原版紫水晶那一套贴图与音效，零资源即可运行。

`id` 可以带命名空间（`'mypack:example_crystal'`）；不带则落在 `kubejs` 命名空间下。

### 完整示例

下面这段复制进 `kubejs/startup_scripts/` 即可运行，逐条注释说明了每个选项的作用：

```js
// kubejs/startup_scripts/my_crystal.js
StartupEvents.registry('block', event => {
  // 选项对象作为 create 的第三个实参传入。链必须写在这里：选项只在 create 调用时读取一次，
  // CustomBudding.create(event, id).chance(20) 这种写法不会生效——那时方块已经建好了。
  // 逐字段赋值与链式写法等价、可以混用：
  //   const opts = new CustomBuddingOptions()
  //   opts.chance = 20 …
  //   CustomBudding.create(event, id, opts)
  const family = CustomBudding.create(event, 'mypack:example_crystal', new CustomBuddingOptions()
    .chance(20)                                  // 每次随机刻有 1/20 的概率推进一级（默认 5）
    .maxLight(7)                                 // 生长格亮度上限 0–15；负数 = 不限制（默认 -1）
    .minLight(1)                                 // 生长格亮度下限 0–15；负数 = 不限制（默认 -1）
                                                 // 两行都写 = 只有亮度 1–7 才推进；只写一行 = 只管那一端
                                                 // （回响那种「必须全黑」就是只写 .maxLight(0)）
                                                 // 下限高于上限会直接报错
    .requiresWater(false)                        // 默认 false = 不需要水
                                                 // 可燃冰式写法是 .requiresWater()（目标格必须是水源）
    .growthDimensions('minecraft:overworld')     // 生长维度 id，可多选；不写 = 维度不限
    .growthBiomes('warm')                        // 生长群系，见下方「生长环境」；不写 = 群系不限
                                                 // 与上一行都写时取交集
    .outsideGrowthChance(0.1)                    // 地盘之外还剩多少概率生长（0–1，默认 0.5）
                                                 // 0 = 出了地盘完全不长；没写上面两行时本项无意义
    .displayName('示例母岩')                      // 不写则交给 KubeJS 按 id 自动命名
    .stageDisplayNames('小芽', '中芽', '大芽', '紫晶簇')   // 顺序：小 → 中 → 大 → 簇
                                                 // 某一项传 null 则该项保持自动命名
    .dropItem('mypack:my_shard', 2)              // 晶簇普通破坏的掉落物与数量（默认什么都不掉）
                                                 // 时运每级再加 0–等级 个；精准采集始终掉晶簇本体
    .buddingTexture('mypack:block/my_crystal')   // 母岩贴图
    .stageTextures('mypack:block/my_small_bud',  // 四个阶段贴图，顺序同上
                   'mypack:block/my_medium_bud',
                   'mypack:block/my_large_bud',
                   'mypack:block/my_cluster')
    .buddingSound('stone')                       // 母岩的破坏音效，写原版音效名（默认 'amethyst'）
    .stageSound('crop')                          // 芽与晶簇的破坏音效，四个阶段共用一个
    .buddingTool('axe')                          // 母岩的开采工具：pickaxe / axe / shovel / hoe
                                                 // （或完整标签 id）；默认 'pickaxe'
    .stageTool('pickaxe')                        // 芽与晶簇的开采工具，四个阶段共用一个
    .buddingLevel('stone')                       // 母岩的开采等级：stone / iron / diamond
                                                 // 'none'、null 或不写 = 不设等级
    .stageLevel('stone')                         // 芽与晶簇的开采等级，四个阶段共用一个
    .group('building_blocks'))                   // 创造栏：默认 'kubejs'，null = 不进任何页

  // family 中是五个方块 id，取用需要带括号（record 访问器）：
  const budding = family.budding()               // 'mypack:example_crystal_budding'
  const cluster = family.cluster()               // 'mypack:example_crystal_cluster'
  console.info(`注册完成：${budding} / ${cluster}`)
})
```

### 选项参考

链式方法与字段同名，二者等价、可混用。下表列出全部选项，括号内为默认值。

| 选项 | 取值 | 作用 |
| --- | --- | --- |
| `chance(n)` | 整数 ≥ 1（5） | 每次随机刻推进一级的概率为 1/n |
| `maxLight(n)` | 0–15，负数 = 不限（−1） | 生长格亮度上限 |
| `minLight(n)` | 0–15，负数 = 不限（−1） | 生长格亮度下限 |
| `requiresWater()` | —（false） | 目标格必须是水源方块 |
| `growthDimensions(...)` | 维度 id，可多个 | 只在这些维度正常生长 |
| `growthBiomes(...)` | 群系 id / 标签 / 气候关键字，可多个 | 只在这些群系正常生长 |
| `outsideGrowthChance(x)` | 0–1（0.5） | 地盘之外继续生长的概率 |
| `displayName(s)` | 字符串 | 母岩显示名；不写则自动命名 |
| `stageDisplayNames(...)` | 四个字符串，可传 `null` | 四个阶段显示名，顺序：小 → 中 → 大 → 簇 |
| `dropItem(id)` / `dropItem(id, n)` | 物品 id，可带数量（1） | 晶簇普通破坏的掉落物 |
| `dropCount(n)` | 整数（1） | 掉落数量，小于 1 按 1 处理 |
| `buddingTexture(s)` | 贴图路径 | 母岩贴图（默认原版紫水晶母岩） |
| `stageTextures(...)` | 四条贴图路径 | 四个阶段贴图，顺序同上 |
| `buddingSound(s)` / `stageSound(s)` | 原版音效名（`amethyst`） | 破坏音效；写不认识的名字会当场报错并列出全部可用名字 |
| `buddingTool(s)` / `stageTool(s)` / `tool(s)` | 工具名或完整标签 id（`pickaxe`） | 开采工具；`tool` 一次设两份 |
| `buddingLevel(s)` / `stageLevel(s)` | 等级名或完整标签 id（不设） | 开采等级；`'none'` 或 `null` = 不设 |
| `group(s)` | 标签页 id（`kubejs`） | 创造栏归属；`null` = 不进标签页 |

### 生长环境

`growthDimensions` 与 `growthBiomes` 都写时取交集，即维度与群系同时满足才算「自己的地盘」；地盘外每通过一次判定后再掷一次，只有 `outsideGrowthChance` 的概率继续生长。两条都不写则任何地方一视同仁。

`growthBiomes` 的每一项可以是：

| 写法 | 例子 | 含义 |
| --- | --- | --- |
| 群系 id | `'minecraft:lush_caves'` | 该群系 |
| 群系标签 | `'#minecraft:is_nether'` | 标签覆盖的全部群系 |
| 气候关键字 | `'cold'` / `'warm'` / `'hot'` | 按基础温度分档，下界全域算 `hot`，末地全域算 `cold` |
| 否定 | `'!cold'`、`'!#minecraft:is_taiga'` | 前面加 `!` 表示排除 |

命中任一否定项直接出局；否则至少命中一条肯定项才算满足。只写否定项时，肯定那一侧视为「全部群系」，所以 `growthBiomes('!cold')` 的含义是「除了寒冷群系哪里都长」。

### 掉落、工具与开采等级

- **掉落**：芽无论怎样破坏都只有精准采集才掉本体。晶簇普通破坏掉落 `dropItem(物品, 数量)` 指定的东西，精准采集掉晶簇本体；不写 `dropItem` 则普通破坏什么都不掉。数量受时运加成，每一级额外给 0–等级 个，与本模组自带晶簇的掉落表一致。
- **开采工具**决定「用什么挖最快」。可写 `pickaxe` / `axe` / `shovel` / `hoe` 之一——这是原版挖掘标签 `#minecraft:mineable/*` 的全部四个，写别的裸名字会当场报错，避免登记一个没人读的标签。也可以写完整的方块标签 id（含 `:`，如 `'mymod:mineable/wrench'`）；写 `null` 则一个标签都不挂，徒手就是最快。想让五个方块共用一个工具就用 `tool(...)`。
- **开采等级**决定「什么工具才拿得到掉落」。裸名字取原版三档标签 `#minecraft:needs_<名字>_tool`：`'stone'` / `'iron'` / `'diamond'`；也可以写完整标签 id（如 `'neoforge:needs_netherite_tool'`）。不写或写 `'none'` 表示不设等级。

  设了等级会连带给方块加上 `requiresCorrectToolForDrops`（只挂标签是没人读的），因此设完之后有两个后果：**等级不够的工具挖下来什么都不掉**，精准采集也一并失效；而且**工具种类必须对**——母岩设为斧头加 `'stone'` 时，石斧掉、石镐不掉。想要徒手也能拿到掉落就不要设等级，本模组自带的芽与晶簇正是如此。

### 后续操作

方块 id 有规律：`<命名空间>:<id>_budding` 与 `_small_bud` / `_medium_bud` / `_large_bud` / `_cluster`。**配方与标签写在 `server_scripts/` 里**（启动脚本中没有这些事件），直接用这些 id 字符串即可：

```js
// kubejs/server_scripts/my_crystal.js
ServerEvents.recipes(event => {
  event.smelting('minecraft:amethyst_shard', 'mypack:example_crystal_cluster')   // 晶簇烧成紫水晶碎片
})

ServerEvents.tags('block', event => {
  event.add('minecraft:mineable/axe', 'mypack:example_crystal_budding')          // 追加一个挖掘标签
})
```

`create` 的返回值 `family` 提供五个方块 id 的访问器：`budding()` / `smallBud()` / `mediumBud()` / `largeBud()` / `cluster()`。它们是**方法调用，括号不能省**；写成 `family.budding` 拿到的是方法对象而不是 id。同一脚本内继续注册相关物品时用得上，`server_scripts/` 里则直接用上面那套 id 字符串即可。

### 行为说明

- **自动挂标签**：母岩自动进入 `#c:budding_blocks`（方块与物品两份）。五个方块按 `buddingTool` / `stageTool` 进入对应的挖掘标签，按 `buddingLevel` / `stageLevel` 进入对应的等级标签（默认只有 `#minecraft:mineable/pickaxe`，不挂等级）。因此智能钻头的精准采集能直接采下你的母岩本体，AE2 的催生器也会加速它，不需要手写标签。
- **护目镜**：戴上工程师护目镜看向自定义母岩，会显示当前生长倍率，以及生长速度、光照要求、含水要求、生长环境（维度 / 群系，只给「缓慢」「必须足够暗」这类定性说法，不报具体数值）。自带家族平时只显示倍率那一行；写了 `growthDimensions` / `growthBiomes` 的会多出一行「只在 X 生长得最快」。
- **创造栏**：KubeJS 注册的方块默认不进任何标签页，容易让人误以为没注册成功，所以这里默认放进 KubeJS 那一页；`group(...)` 可改为原版标签页，`group(null)` 则完全不进标签页（只能用 `/give` 取）。
- **名字与外观**走资源包与语言文件；不指定显示名时由 KubeJS 按 id 自动生成英文标题（`example_crystal_small_bud` → "Example Crystal Small Bud"）。方块物品与方块共用同一个语言键，背包、掉落物、创造栏会一起变。

### 已知限制

- 不进配置文件的四档生长速度，概率完全由脚本里的 `chance` 决定。
- 自发光、红石信号这类**烘焙在方块属性里**的特性不可通过脚本配置。需要这类特性应走附属模组的 Java 路线，见下节。

### 低阶接口

需要完全自定义时，可以直接取生长引擎与定义类自行拼装。`CustomBudding` 与 `CustomBuddingOptions` 由本模组的 KubeJS 插件绑定为全局对象，无需 `Java.loadClass`；生长引擎与定义类则需要显式加载。

```js
const BuddingGrowthEngine = Java.loadClass('com.minecart.yunxian.budding.BuddingGrowthEngine')
const GrowthDefinition  = Java.loadClass('com.minecart.yunxian.budding.GrowthDefinition')

// of(小, 中, 大, 簇, n)：四个阶段方块 + 概率基数 n（每次随机刻有 1/n 的概率推进一级）。
// 阶段方块写 id 即可，原版紫水晶芽、本模组的芽/簇、自己注册的方块都行；
// 返回的是定义本身，可以继续链式追加限制：
const definition = GrowthDefinition.of('minecraft:small_amethyst_bud', 'minecraft:medium_amethyst_bud',
                                       'minecraft:large_amethyst_bud', 'minecraft:amethyst_cluster', 20)
    .growthDimensions('minecraft:overworld')   // 生长维度，可多选
    .growthBiomes('warm', '!hot')              // 生长群系：id / '#标签' / 关键字 cold·warm·hot；! 是否定
    .outsideGrowthChance(0.1)                  // 出了自己的地盘还剩多少概率继续长（0 = 完全不长）

// 光照与含水也在这条链上（亮度 0–15，负数 = 那一端不限制；下限高于上限会直接报错）：
//   .maxLight(7).minLight(1)   // 只有亮度 1–7 才推进
//   .requiresWater()           // 目标格必须是水源
// 也可以只用 of 的完整重载：of(小, 中, 大, 簇, n, 亮度上限, 亮度下限, 需要水源)；
// 只给上限时省略下限那一项：of(小, 中, 大, 簇, n, 亮度上限, 需要水源)。

event.create('my_budding').randomTick(ctx => {
  BuddingGrowthEngine.tryGrow(ctx.block.getLevel(), ctx.block.getPos(), ctx.random, definition)
})
```

> 把定义写在脚本顶层是安全的（阶段方块是原版或你自己注册的）。**若定义中引用了其它模组的方块**，必须挪进 `randomTick` 回调里构造并缓存一次：脚本执行时那些模组的方块尚未注册，写在顶层会当场报错。

本地开发目录下另有一份可运行的示例：`run/kubejs/startup_scripts/custom_budding_example.js`。`run/` 位于 `.gitignore` 中，不随仓库分发。

---

## 七、开发

### 新增母岩

母岩的全部特点——光照/含水/充能要求、生长规则、方块转化、亮度与音效、掉落物——集中在
`src/main/java/com/minecart/yunxian/budding/BuddingFamilies.java` 这一张表里。**新增一个母岩家族等于在表里加一条**，方块注册、生长逻辑、护目镜提示、创造模式标签、世界生成开关与 Ponder 条目都会自动派生。

例外是生长速度：它是全局四档（`BuddingFamily.GrowthSpeed`），由配置文件的四个 id 列表决定；「正常」档的默认成员列表同样由本表自动派生，新增家族无需额外配置。

### 数据生成

生成 JSON 资产：`./gradlew runData`。**运行前必须确保 `run/mods` 里有 AE2**，否则会直接中止——这是为了避免已生成的福鲁伊克斯资产被判定为过期文件而删除。输出目录 `src/generated/resources` 已纳入版本管理。

以下文件由数据生成接管，请勿手写：`blockstates/`、`models/block/`、`models/item/`、母岩与芽的掉落表、`c:budding_blocks` 标签、`mineable/pickaxe` 与 `needs_*_tool` 标签。

仍需手写的：材质（含 `.mcmeta`）、晶簇与福鲁伊克斯的掉落表（结构与模组条件无法由生成器等价复刻）、世界生成 JSON、语言文件。

### 把方块接进生长引擎

生长判定与放置是公开的（`budding/BuddingGrowthEngine`），三类调用方共用同一条代码路径：本模组自带的母岩、附属模组的方块、KubeJS 脚本注册的方块。引擎**不含**随机刻副作用（转化与传播）与生长能量，那两样由方块自己负责，能量通过 `GrowthGate` 在放置前回调。

**附属模组（Java）**

```java
// 1) 自己的母岩方块：可直接用公开的 GenericBuddingBlock（传入自己的芽/簇方块），
//    也可以自己实现 randomTick 再调引擎
DeferredBlock<Block> myBudding = MY_BLOCKS.register("my_budding",
        () -> new GenericBuddingBlock(myFamilySpec, properties, small, medium, large, cluster));

// 2) 在自己的 @Mod 构造器里声明（必须在方块注册事件之前）：
BuddingRegistration.declareBuddingBlock(myBudding.get());   // 用共享护目镜 BE
BuddingRegistration.declareKnownId("my_budding");           // 让配置文件的四档能列出它
```

`declareBuddingBlock` 不是可有可无的礼貌调用：区块从 NBT 恢复方块实体时会校验 `BlockEntityType#isValid`（`LevelChunk:392`），不在共享 BE 合法方块表里的方块，其方块实体会在**区块重载后被丢弃**，护目镜随即失效。

使用了 `GenericBuddingBlock` 的方块到这里就齐了：它自带家族定义，JEI 的母岩信息页能直接读出它的参数。**自己拼低阶接口**（自己实现 `randomTick` 调引擎）的方块没有定义可读，需要再补一次声明：

```java
// 3) 让 JEI 的「母岩信息」页也能列出它的生长速度 / 光照 / 含水要求
BuddingRegistration.declareGrowthDefinition(myBudding.get(),
        () -> GrowthDefinition.of(smallBud, mediumBud, largeBud, cluster, 20));
```

定义用 `Supplier` 惰性获取，因此方块与阶段方块都还没建出来时调用也是安全的（脚本注册时正是如此）。

**引擎的两种调用形态**

```java
// 免费生长（脚本与附属模组最常用）
BuddingGrowthEngine.tryGrow(level, pos, random, GrowthDefinition.of(smallBud, mediumBud, largeBud, cluster, 20));

// 带付费钩子（本模组自带家族走这条：AE2 母岩在放置前扣能量）
BuddingGrowthEngine.tryGrow(serverLevel, pos, random, definition, gate);
```

定义本身还能继续追加门槛：`growthDimensions(Level.NETHER)` 让方块只在列出的维度正常生长，`growthBiomes("minecraft:lush_caves")` 再按群系收一道。两者都写时取交集；出了地盘每次判定通过后再掷一次，只剩 `outsideGrowthChance(0.5)` 的概率继续生长（0.5 正是石英与荧石母岩的写法，写 0 则出了那些地方再也长不动）。

群系条目的判定顺序：命中任一**否定**项直接出局；否则至少命中一条**肯定**项才算满足；只写否定项时，肯定那一侧视为「全部群系」（`growthBiomes("!cold")` 即除了寒冷群系哪里都长）。

其余资源自备：方块的注册与贴图、物品、掉落表，以及把方块加入 `#c:budding_blocks` 标签（智能钻头的精准采集与 AE2 的催生器读它）。

### 气候关键字

`cold` / `warm` / `hot` 三个关键字的分档规则：

| 关键字 | 覆盖范围 |
| --- | --- |
| `cold` | 末地全域，或群系基础温度 < 0.3（雪原、冰刺、雪针叶林、冰洋、雪坡、冻峰，以及针叶林、山地这类寒凉群系） |
| `warm` | 其余群系（平原、森林、丛林、沼泽、海洋、蘑菇岛、裸岩峰等） |
| `hot` | 下界全域，或群系基础温度 ≥ 1.2（沙漠、恶地、热带草原） |

「末地 / 下界全域」走的是群系标签 `#minecraft:is_end` / `#minecraft:is_nether`，因此使用这两套群系的模组维度同样算数。阈值可改：`GrowthEnvironment.Climate` 中的两个常量。

---

## 授权

**本模组仅允许收录进发布在 CurseForge 上的整合包，无需另行申请许可**——公开或私有、免费或盈利均可。

整合包必须使用 **CurseForge 的标准打包方式**：在 `manifest.json` 中以本模组的 CurseForge 项目 ID 与文件 ID 引用，由启动器从 CurseForge 自行下载。**不得把模组 jar 直接打进压缩包**，也不得发布到 CurseForge 以外的平台。条件是保留作者署名（YunXian_LI）并附上官方发布页链接。

完整条款见 [LICENSE.txt](LICENSE.txt)。

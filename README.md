# 机械动力：晶簇工业

**Create: Crystal Industry** · Minecraft 1.21.1 · NeoForge · Create 6.0.10+

**[English](README_EN.md) | 简体中文**

把原版紫水晶那套"母岩慢慢长晶"的机制，推广到整个工业体系。

不用再满世界挖矿了——找到一块母岩，让它自己长。

---

## 一、核心：母岩

每块母岩都和紫水晶母岩一样，从六个面缓慢长出 **小型芽 → 中型芽 → 大型芽 → 完整晶簇**（每随机刻 1/5 概率推进一级，与原版同速）。母岩本身不会被消耗，摘掉晶簇它还会继续长。

生长速度分 **极慢(1/50)、慢(1/20)、正常(1/5)、快(1/1)** 四档，默认全部「正常」；哪一种母岩属于哪一档在配置文件里按 id 分配，可只让某几种母岩更快或更慢。

| 母岩 | 额外生长条件 |
| --- | --- |
| 粗铁、粗金、粗铜、粗锌、钻石、绿宝石、青金石、红石、石英 | — |
| 荧石、玫瑰石英 | — |
| 可燃冰 | 芽体必须浸在水中，离水即停止进阶 |
| 回响 | 生长格光照必须为 0 |
| 福鲁伊克斯（需 AE2） | 必须接入已激活的 ME 网络，每次生长消耗 AE |

> 红石母岩自带 15 级红石信号，可以直接当红石源用。

### 母岩本身也能再生

这才是关键：矿石母岩会以 **1/20** 的概率把周围 3×3×3 内的石头/深板岩转化为**对应矿石**；再以极低概率把对应的粗矿块"传染"成新的母岩。石英母岩侵蚀闪长岩，回响母岩把泥土、石头、凝灰岩等转化为幽匿。

**一块母岩 = 一座会自己扩张的矿脉。**

---

## 二、催生器：把"等待"变成"瞬间"

原版母岩平均要 ~68 秒才等到一次随机刻，所以自然生长慢到几乎可以忽略。催生器每 tick 对**六个相邻面各强制施加一次随机刻**，几分钟的生长瞬间完成。

- **电力催生器** —— 消耗 FE，相邻催生器之间自动均分电力
- **动力催生器** —— 背面接入旋转力，转速越高越快，256 RPM 达到上限（满速时与电力催生器同级）

两种催生器的**催生间隔**都可在配置里调整（`催生间隔（tick）`，默认 1 = 每 tick 一次，数值越大越慢）；电力催生器按「每次催生」结算 FE，所以调慢它同时也更省电。护目镜显示的倍率会跟着配置走。

> ⚠️ 随机刻驱动的**远不止晶簇**：作物、树苗、铜的锈蚀、地狱疣……一切依赖随机刻的机制都会被一起加速。**所以它同时也是一台通用加速器**，围一圈母岩只是最赚的用法。

---

## 三、配套机械与装备

- **智能钻头** —— 两种模式。普通采集：速度是普通机械钻头的 **2 倍**；精准采集：速度持平，但能**完整采下母岩方块本身**（母岩的合法获取途径之一）
- **动力吸尘器** —— 拥有鼓风机的全部气流功能，外加把气流中的物品吸入自身库存；正前方若是容器，则与容器**直接交换物品，全程不向世界掉落任何东西**。吹气/吸气、过滤、20 格以内距离均可调
- **夜视仪护目镜** —— Create 护目镜 + 回响碎片，按键切换夜视，支持 Curios 头盔槽
- **回响望远镜** —— 使用时视线穿透障碍，把方块显示为轮廓；可放入过滤物品，默认检测矿物
- **可燃冰** —— 优质燃料，也是烈焰人喜爱的零嘴

---

## 四、世界生成

母岩按对应矿物的深度生成，通常**嵌在矿脉之中**（钻石母岩 -64~16、粗铁 -24~56、粗铜 -16~112、粗锌 -63~70、青金石 -64~64、红石 -63~15 等）。荧石母岩出现在天然荧石团底部，可燃冰母岩只生成在深海海床之下。

**每一种生成都能在配置文件里单独开关**，可燃冰稀有度、荧石母岩替换概率与母岩生长速度档位同样可调。
整合包作者还能用 **KubeJS** 加自己的母岩，数量不限（见下方「加自己的母岩（KubeJS）」）。

> ⚠️ **自然生成的回响母岩被破坏时会召唤监守者**（你自己放置的不会）。

---

## 五、兼容性

- **必需**：机械动力 Create 6.0.10+
- **可选**：AE2（启用福鲁伊克斯母岩）、Curios（护目镜可装备于头盔槽）、JEI、KubeJS（用脚本加自己的母岩）
- **未安装 AE2 时模组会正常启动**，只是不注册福鲁伊克斯相关内容
- **AE2 的晶体催生器同样能加速本模组的母岩**（按它自己的配置间隔施加随机刻），护目镜的「生长速度」倍率会把它一并计入

所有机器均提供 Create 风格的护目镜信息面板（生长状态、工作速度、生长倍率）与 **Ponder 教学场景**。

---

## 加自己的母岩（KubeJS）

本模组的生长引擎是**公开**的，用 **KubeJS** 就能加自己的母岩，**数量不限**，不写 Java、也不需要数据包。
更省事的是：**一行注册一整族**——母岩本体、小芽、中芽、大芽、晶簇五个方块一次全出来。

```js
StartupEvents.registry('block', event => {
  CustomBudding.create(event, 'example_crystal', 20)   // 概率 1/20
})
```

生成的方块：`kubejs:example_crystal_budding` 以及 `_small_bud` / `_medium_bud` / `_large_bud` / `_cluster`；
母岩的随机刻已经接到生长引擎上，芽/簇自带 `FACING` 属性（朝母岩的方向长）。
`id` 可以带命名空间（`'mypack:example_crystal'`），不带就落在 `kubejs` 命名空间。

需要更多控制就用选项对象（选项在调用时读一次）：

```js
const opts = new CustomBuddingOptions()
opts.chance = 20            // 每次随机刻 1/n
opts.maxLight = 7           // 生长位亮度上限（负数 = 不限）
opts.requiresWater = false  // true = 可燃冰式，目标格必须含水
opts.displayName = '示例母岩'
opts.dropItem = 'mypack:my_shard'      // 晶簇普通破坏时掉的物品（精准采集始终掉晶簇本体）
opts.dropCount = 2                     // 掉落数量，默认 1
opts.buddingTexture = 'mypack:block/my_crystal'        // 母岩贴图
opts.stageTextures = ['mypack:block/my_small_bud', 'mypack:block/my_medium_bud',
                      'mypack:block/my_large_bud', 'mypack:block/my_cluster']
const family = CustomBudding.create(event, 'mypack:example_crystal', opts)
```

选项也可以链式写——整条链作为 `create` 的第三个实参（两种写法等价，也能混用）：

```js
CustomBudding.create(event, 'mypack:example_crystal', new CustomBuddingOptions()
  .chance(20)
  .requiresWater()
  .maxLight(0)
  .dropItem('minecraft:amethyst_shard', 2))
```

链**必须写在实参里**：选项只在 `create` 调用时读一次，`CustomBudding.create(event, id).chance(20)` 那种写法不会生效（那时方块已经建好了）。

- **护目镜**：戴上 Create 护目镜看你的母岩，会显示当前生长速度，外加它配置的生长概率 / 光照要求 / 含水要求（自带家族只显示速度那一行）。
- **默认贴图借用原版紫水晶那一套**，所以什么都不画也能跑；要自己的外观就改上面的贴图选项或用资源包。
- 注册出来的方块默认进**创造模式「KubeJS」那一页**（KubeJS 的方块本来不进任何标签页，容易让人以为没注册成功）；
  `opts.group` 可以换成原版页（`'building_blocks'` 等，用 id 里的下划线写法），设成 `null` 就完全不进标签页、只能用 `/give` 取。
- **掉落**：芽只有**精准采集**才掉本体；晶簇普通破坏掉 `opts.dropItem` × `opts.dropCount`（默认什么都不掉），精准采集掉本体——与本模组自带芽/簇的行为一致。
- 返回值 `family` 里有五个方块 id（`family.budding` / `family.cluster` …），方便接着写配方、标签、掉落表。
- **标签自动加**：母岩自动进 `#c:budding_blocks`（方块 + 物品），五个方块都进 `#minecraft:mineable/pickaxe`——于是智能钻头的精准采集能直接采下你的母岩本体，AE2 晶体催生器也会加速它，不用手写标签。
- 名字与外观走资源包 / lang（方块在 `kubejs` 或你自己的命名空间下）；掉落由本模组按上面的规则接管。

### 完整示例（复制即用）

把下面这段丢进 `kubejs/startup_scripts/`（文件名随意，`.js` 即可）就完事——零贴图、零资源包，
母岩与芽/簇默认借用原版紫水晶那一套贴图：

```js
// kubejs/startup_scripts/my_crystal.js
StartupEvents.registry('block', event => {
  const opts = new CustomBuddingOptions()
  opts.chance = 20                        // 每次随机刻有 1/20 的概率往上长一级（默认 5）
  opts.maxLight = 7                       // 生长位亮度上限 0–15；负数 = 不限（默认 -1）
  opts.requiresWater = false              // true = 可燃冰式，目标格必须是水源（默认 false）
  opts.displayName = '示例母岩'            // 不写就交给 KubeJS 按 id 自动命名（芽/簇同理）
  opts.dropItem = 'minecraft:amethyst_shard'   // 晶簇普通破坏时掉什么；null = 什么都不掉（默认）
  opts.dropCount = 2                      // 掉落数量，默认 1
  opts.group = 'building_blocks'          // 创造栏：默认 'kubejs'，null = 不进任何页（只能 /give）

  // 一行注册整族：母岩 + 小芽 + 中芽 + 大芽 + 晶簇
  const family = CustomBudding.create(event, 'mypack:example_crystal', opts)

  // family 里是五个方块 id，接着写配方 / 标签 / 掉落表都行
})
```

只要概率、其余全默认的话，一行就够：

```js
StartupEvents.registry('block', event => {
  CustomBudding.create(event, 'example_crystal', 20)
})
```

要自己的外观就补两行贴图（不写则四个阶段与母岩都用原版紫水晶的贴图）：

```js
opts.buddingTexture = 'mypack:block/my_crystal'
opts.stageTextures = ['mypack:block/my_small_bud', 'mypack:block/my_medium_bud',
                      'mypack:block/my_large_bud', 'mypack:block/my_cluster']
```

### 已知限制

- 不进配置文件里的四档（概率由脚本里的 `chance` 决定）；自发光、红石信号这类**烘焙在方块属性里**的东西也不可配
  （要这些就走附属模组的 Java 路线，见开发一节）。

### 低阶接口（自己拼定义）

```js
const BuddingGrowthEngine = Java.loadClass('com.minecart.yunxian.budding.BuddingGrowthEngine')
const GrowthDefinition  = Java.loadClass('com.minecart.yunxian.budding.GrowthDefinition')

// 自己的方块 + 自己指定的四个阶段方块（原版紫水晶芽、本模组的芽/簇、你注册的方块都行）
const definition = GrowthDefinition.of('minecraft:small_amethyst_bud', 'minecraft:medium_amethyst_bud',
                                       'minecraft:large_amethyst_bud', 'minecraft:amethyst_cluster', 20)

event.create('my_budding').randomTick(ctx => {
  BuddingGrowthEngine.tryGrow(ctx.block.getLevel(), ctx.block.getPos(), ctx.random, definition)
})
```

可跑的完整示例见上一节的「完整示例（复制即用）」；本地开发目录里另有一份
`run/kubejs/startup_scripts/custom_budding_example.js`（`run/` 在 `.gitignore` 里，不随仓库分发）。

---

## 开发：新增母岩与数据生成

母岩的全部特点（光照/含水/充能要求、生长规则、方块转化、亮度音效、掉落物）集中在
`src/main/java/com/minecart/yunxian/budding/BuddingFamilies.java` 这一张表里——
**新增一个母岩家族＝在表里加一条**，方块注册、生长逻辑、护目镜提示、创造模式标签、
世界生成开关与 Ponder 条目都会自动派生。
例外是生长速度：它是全局四档（`BuddingFamily.GrowthSpeed`），由配置文件的四个 id 列表决定，
「正常」档的默认成员列表也由本表自动派生——新增家族无需额外配置。

生成 JSON 资产：`./gradlew runData`。**运行前必须确保 `run/mods` 里有 AE2**（否则会
直接中止，避免把已生成的福鲁伊克斯资产判为过期文件删除），输出目录 `src/generated/resources`
已纳入版本管理。以下文件由数据生成接管，请勿手写：

`blockstates/`、`models/block/`、`models/item/`、母岩与芽的掉落表、`c:budding_blocks`
标签、`mineable/pickaxe` 与 `needs_*_tool` 标签。

仍需手写的：材质（含 `.mcmeta`）、晶簇与福鲁伊克斯的掉落表（结构与模组条件无法由生成器
等价复刻）、世界生成 JSON、语言文件。

### 给附属模组与 KubeJS：把方块接进母岩引擎

生长判定与放置是**公开**的（`budding/BuddingGrowthEngine`），三类调用方共用同一条代码路径：
本模组自带的母岩、附属模组的方块、KubeJS 脚本注册的方块。引擎**不含**随机刻副作用（转化/传播）与
生长能量——那两样由方块自己负责，能量通过 `GrowthGate` 在放置前回调。

**附属模组（Java）**

```java
// 1) 自己的母岩方块：可直接用公开的 GenericBuddingBlock（传自己的芽/簇方块），
//    也可以自己实现 randomTick 再调引擎
DeferredBlock<Block> myBudding = MY_BLOCKS.register("my_budding",
        () -> new GenericBuddingBlock(myFamilySpec, properties, small, medium, large, cluster));

// 2) 在自己的 @Mod 构造器里声明（必须在方块注册事件之前）：
BuddingRegistration.declareBuddingBlock(myBudding.get());   // 用共享护目镜 BE
BuddingRegistration.declareKnownId("my_budding");           // 让配置文件四档能列出它
```

`declareBuddingBlock` 不是可有可无的礼貌调用：区块从 NBT 恢复方块实体时会校验
`BlockEntityType#isValid`（`LevelChunk:392`），不在共享 BE 合法方块表里的方块，
其方块实体会在**区块重载后被丢弃**（护目镜随即失效）。

**KubeJS（整合包作者，不写 Java）**

见上一节「加自己的母岩（KubeJS）」：装机后脚本里一行 `CustomBudding.create(event, 'my_crystal', 20)`
就注册出整族五个方块（本模组的 KubeJS 插件提供的绑定）；
要自己拼低阶方块时可用 `GrowthDefinition.of(小芽, 中芽, 大芽, 晶簇, n)` + 引擎。
可跑的完整示例见「加自己的母岩（KubeJS）」一节；本地开发目录里另有一份
`run/kubejs/startup_scripts/custom_budding_example.js`（`run/` 在 `.gitignore` 里，不随仓库分发）。

**引擎的两种调用形态**

```java
// 免费生长（脚本/附属模组最常用）
BuddingGrowthEngine.tryGrow(level, pos, random, GrowthDefinition.of(smallBud, mediumBud, largeBud, cluster, 20));

// 带付费钩子（本模组自带家族走这条：AE2 母岩在放置前扣能量）
BuddingGrowthEngine.tryGrow(serverLevel, pos, random, definition, gate);
```

其余资源自备：方块的注册与贴图、物品、掉落表、以及把方块加进 `#c:budding_blocks`
标签（智能钻头精准采集与 AE2 晶体催生器读它）。

---

## 授权

**本模组仅允许收录进发布在 CurseForge 上的整合包，无需另行申请许可**——公开或私有、免费或盈利均可。

整合包必须使用 **CurseForge 的标准打包方式**：在 `manifest.json` 中以本模组的 CurseForge 项目 ID 与文件 ID 引用，由启动器从 CurseForge 自行下载。**不得把模组 jar 直接打进压缩包**，也不得发布到 CurseForge 以外的平台。条件是保留作者署名（YunXian_LI）并附上官方发布页链接。

完整条款见 [LICENSE.txt](LICENSE.txt)。

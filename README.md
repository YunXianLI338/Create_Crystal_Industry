# 机械动力：晶簇工业

**Create: Crystal Industry** · Minecraft 1.21.1 · NeoForge · Create 6.0.10+

**[English](README_EN.md) | 简体中文**

把原版紫水晶母岩的生长机制推广到其它矿物：母岩从六个面向外长出芽与晶簇，母岩自身不被消耗，采收晶簇后继续生长。矿石母岩还会把周围的石头转化为对应矿石，因此一处母岩等于一座会自行扩张的矿脉。

---

## 一、母岩

母岩在六个面上各自判定生长。某个面为空位时长出小型芽，已有芽时依次进阶为中型芽、大型芽、完整晶簇，晶簇是终态。判定由随机刻触发，通过则推进一级。母岩本身不被消耗，采走晶簇后原位置继续生长。

### 母岩一览

「侵染」的规则是：母岩每随机刻以 1/20 的概率掷一次，通过后在半径 1 格（3×3×3）范围内随机取一格，该处是石头或深板岩时替换为对应矿石。产出因此取决于这个立方体里有多少格是石头或深板岩，把母岩埋在石头中等同于提高命中率。回响母岩的半径与概率不同，见表中标注。

| 母岩 | 额外生长条件 | 晶簇产出 | 侵染 |
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
| 远古残骸 | 仅在下界满速，同上；每次生长还要花 250 mB 熔岩（本方块自带 1 B 熔岩罐，见下） | 下界合金碎片 ×1–3 | —（只有再生传播，见下） |
| 荧石 | 仅在下界满速，同上 | 荧石粉 | — |
| 回响 | 生长格亮度必须为 0 | 回响碎片 | 半径 2 格内的泥土、沙、石头、凝灰岩等 12 种方块 → 幽匿（1/4） |
| 可燃冰 | 目标格必须是水源方块 | 可燃冰 | — |
| 福鲁伊克斯（需 AE2） | 须接入已激活的 ME 网络（占用 1 个频道），每次生长消耗 200 AE | 福鲁伊克斯水晶 | — |

掉落规则：

| 方块 | 普通破坏 | 精准采集 |
| --- | --- | --- |
| 晶簇 | 表中「晶簇产出」一栏的物品，受时运加成 | 晶簇方块本体 |
| 芽 | 无掉落 | 芽方块本体 |
| 母岩 | 比自身低一档的方块（粗铁母岩掉粗铁块、钻石母岩掉钻石块；远古残骸母岩没有更低的档，掉 1 个远古残骸） | 同上，不因精准采集而改变 |

母岩方块本体无法用普通工具采下，只有智能钻头的精准采集模式能取得，见下节。

红石母岩与它的各级芽、晶簇都自带红石信号（母岩与晶簇 15，三档芽 3 / 7 / 11），可以直接作红石源。荧石母岩与它各级的发光同档。回响母岩的芽与晶簇刻意不发光，一旦发光就会顶掉自己的生长格。

### 远古残骸母岩的熔岩罐

远古残骸母岩是自带家族里唯一吃熔岩的。方块本体是一个容量 1 B 的流体容器，只认熔岩。每次成功生长扣 250 mB，罐里不够一次就不长，所以下界自然生成的空罐要先浇熔岩。1 B 是四次生长。

- **通入与取出**：管道（走 NeoForge 流体能力）或手持桶右键。一桶正好是罐的满容量，所以桶只在罐空或罐满时管用，零头靠管道补。
- **不会外溢**：拿错液体、或罐没得倒没得舀时，这一下被方块吞掉，液体不会洒在母岩旁边。想故意往旁边倒就潜行右键。
- **液位显示**：护目镜看液位与单次消耗，比较器按液位输出 1–15。

脚本母岩用的是同一个罐，见第六节的「流体消耗」与「修改已有的母岩」。

罐里的熔岩跟着方块走，不跟着物品走。打碎时熔岩丢失，蓝图粘出来的也是空罐。

### 熔岩淬成深板岩

熔岩下方是灵魂土、旁边是可燃冰块时，那格熔岩变成深板岩。这是原版玄武岩配方换一个方块的写法：

| 原版玄武岩 | 本模组 |
| --- | --- |
| 熔岩**下方**是灵魂土 | 熔岩下方**仍是灵魂土** |
| 熔岩**旁边**是蓝冰 | 熔岩旁边是**可燃冰块** |
| 熔岩 → 玄武岩 | 熔岩 → **深板岩** |

它排在原版「熔岩 + 水 → 黑曜石 / 圆石」之后，所以熔岩旁边只要有水，先命中的就是原版那条。这是全局规则，对世界里任何这种组合都生效，不限于本模组放的东西。

### 电击免疫

物品标签 `create_crystal_industry:shock_immune`。四个盔甲槽全部带此标签时，免疫原版真实落雷。标签里是原版锁链甲与下界合金甲各四件，加上机械动力的三件下界合金潜水装备，只看槽位与标签，不要求四件同材质。

### 母岩再生（侵染）

矿石母岩还会以 1/25000 的概率把紧邻的对应矿块传染成新的母岩。石英母岩侵蚀平滑石英，福鲁伊克斯母岩侵蚀福鲁伊克斯块。

远古残骸母岩是唯一没有「石头变矿石」转化的家族。它只把紧邻的远古残骸传染成新的母岩，这条传播同样扣 250 mB 熔岩，所以想多要一块就得自己搬远古残骸过来贴着它等。

| 母岩 | 可被转化为母岩的方块 |
| --- | --- |
| 粗铁 / 粗金 / 粗铜 / 粗锌 | 对应粗矿块 |
| 钻石 / 绿宝石 / 青金石 | 对应矿物块 |
| 红石 | 红石块 |
| 石英 | 平滑石英 |
| 远古残骸 | 远古残骸自身 |
| 福鲁伊克斯 | 福鲁伊克斯块（需供电） |

这一条**可以关**，两个配置项（`config/create_crystal_industry-common.toml`）：

| 配置项 | 默认 | 作用 |
| --- | --- | --- |
| `infection.buddingInfection` | `true` | 母岩会不会侵染紧邻的方块。**一切的总开关**：石头长成矿石、粗铁块变成新的母岩、回响母岩把周围变成幽匿都算。关掉之后母岩什么都不再转化（也就不会自己变多） |
| `infection.infectingBudding` | 空 | 会侵染周围方块的母岩名单。留空 = 全部允许；填了名单 = **只有**名单里的允许，其余母岩什么都不再转化。名单里填**母岩家族 id**（`raw_iron`、`quartz`，与 `worldgen.generate_<id>` 同名）或**方块 id**（`kubejs:my_crystal_budding`，脚本注册的母岩用这种） |

服务器主想彻底停掉转化（**包括石头变矿石**）就把第一项设为 `false`；只想留一两种母岩继续干活就往第二项里填名单。脚本注册的母岩同样受这两项管——判据是这块母岩的家族 id 或方块 id（见第六节的 `.transform`）。

#### 侵染配方（数据包可写）

石头→矿石、粗铁块→母岩自身、回响→幽匿……**它们现在没有区别，都是同一种配方** `create_crystal_industry:budding_conversion`。内置的那 21 条由数据生成从家族表吐出来（见第七节「数据生成」），整合包照着同一个 id 覆盖就是改；KubeJS 的 `.transform(...)` 写出来的也是它。

一条配方 = **一条规则**，不是一对 A→B：

```json
{
  "type": "create_crystal_industry:budding_conversion",
  "budding": "create_crystal_industry:raw_iron_budding",
  "chance": 20,
  "radius": 1,
  "replacements": [
    { "input": "minecraft:stone", "output": "minecraft:iron_ore" },
    { "input": "minecraft:deepslate", "output": "minecraft:deepslate_iron_ore" }
  ]
}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `budding` | 是 | 哪块母岩做这件事。**必须是本模组驱动的母岩**（自带的家族，或脚本 `CustomBudding` 注册的）——写别的方块（黑曜石之类）配方照样能加载，但永远不会触发，JEI 也不显示它 |
| `chance` | 是 | 概率基数 n：每随机刻有 1/n 的概率掷中一次（≥ 1） |
| `radius` | 是 | 取格半径 r：命中后在以母岩为中心、`2r+1` 见方（去掉中心）的立方体里随机取一格（0–8） |
| `gated` | 否 | 命中后还要过一道付费钩子（默认 `false`）——远古残骸的熔岩、福鲁伊克斯的 AE 就是它 |
| `replacements` | 是 | 替换表，**按顺序取第一条匹配的**；每条给 `input`（换成什么）与 `output`（换成什么） |

几条要留意的语义：

- **一次掷骰管一条配方**：上面那份 JSON 里石头与深板岩共用同一次 1/20 与同一次取格——拆成两条配方会让矿石产出翻倍。JEI 页面把同一条规则的两条替换拆开显示，所以页面上看起来是两页。
- `input` 三种写法都行：单个方块 id、方块 id 列表、`"#方块标签"`（例：`"input": ["minecraft:stone", "minecraft:deepslate"]`、`"input": "#c:stones"`）。
- `output` **留空**表示换成本母岩自身（再生传播就是这么写的），省得配方反过来引用自己所属的母岩。
- 半径不是产量旋钮：每随机刻只取一格，半径越大越够得着、但每格被抽中的机会同比变小。想让某种方块变快，调 `chance`。

改与删：

- **改**：在数据包里放一份同 id 的 JSON，例如 `data/create_crystal_industry/recipe/budding_conversion/raw_iron_budding/stone_to_iron_ore.json`。
- **加**：新放一份 JSON 就行，引擎只按 `budding` 认领属于哪块母岩。
- **删**：数据包覆盖不了 jar 内的配方，得用 KubeJS（`ServerEvents.recipes(e => e.remove(...))`），或者用上面那两个配置项把这块母岩停掉。

### 生长速度

概率写在家族表里（`BuddingFamilies` 各家族自己的 `Growth.speed`），没有配置项：

| 档位 | 每次随机刻的推进概率 | 哪些母岩 |
| --- | --- | --- |
| 快 | 1/1，抽中必定推进 | 红石、青金石 |
| 正常 | 1/5，与原版紫水晶母岩同速 | 其余全部 |
| 慢 | 1/20 | 远古残骸（它的晶簇掉下界合金碎片，按稀缺资源处理） |
| 极慢 | 1/50 | 出厂成员为空（留给脚本与附属模组用） |

想单独调某一块母岩的概率，用 KubeJS 的 `CustomBudding.modify(...)`（见第六节），不必改源码重编译。整族一起调就直接改家族表。

---

## 二、催生器

原版母岩平均约 68 秒才等到一次随机刻，自然生长几乎不可观测。催生器对相邻六个面中会吃随机刻的方块各施加一次随机刻，把这一过程压缩到数秒。

| | 电力催生器 | 动力催生器 |
| --- | --- | --- |
| 供能 | FE | 背面接入旋转力 |
| 每次催生消耗 | 100 FE | 不耗电 |
| 触发节奏 | 每 `acceleratorIntervalTicks` tick 一次（默认 1，即每 tick） | 同左 |
| 强度 | 固定，每次必定施加 | 随转速线性增长，256 RPM 封顶 |
| 满速时效果 | 6 随机刻 / tick | 与电力催生器相同 |
| 内部缓存 | 10 000 FE，最大输入 100 FE/t | — |

- 相邻的电力催生器之间自动均分电力。
- 动力催生器低于 256 RPM 时按转速比例生效，超过 256 RPM 不再增强。
- 配置项 `acceleratorIntervalTicks` 同时作用于两种催生器，数值越大越慢。电力催生器按每次催生结算 FE，所以调慢它也同时省电。
- 只催会吃随机刻的邻格，判定与原版随机刻相同。空气、石头这类方块当场跳过，所以护目镜上的倍率是上限。忘了写 `.randomTicks()` 的模组方块不会被催。

随机刻驱动的远不止晶簇。作物、树苗、铜的锈蚀这类依赖随机刻的机制都会被一并加速，催生器同时也是一台通用加速器。

戴上工程师护目镜看向原版紫水晶母岩，同样会显示生长速度与倍率。原版方块没有被改动，信息由客户端按需生成。

---

## 三、机械与装备

| 物品 | 说明 |
| --- | --- |
| **智能钻头** | 机械钻头，两种模式，用侧面的设置槽切换。普通模式采集速度为普通机械钻头的 2 倍。精准模式速度持平，但方块完整掉落。母岩只有精准模式才采得下方块本体。可放过滤物品限定破坏目标。 |
| **动力吸尘器** | 有鼓风机的全部气流功能，并把气流中的物品吸入自身 27 格库存。无动力时不通应力就没有风，只被动捡起正前方紧贴那一格的掉落物（侧面滑块的风场长度此时不生效）。正前方是容器则与其直接交换，全程不掉落，搬运节奏跟转速走：侧面数量滑块设成 `≤` 时每次最多吸这么多个，设成 `=` 时凑够一整批才搬、且只搬这一批。注水后吹气可洗涤物品。 |
| **泄压舱口** | 被风打到时，把依附容器里过滤通过的物品一批批吐进气流。侧面槽只做过滤，一批固定 16 个，再受物品自身堆叠数压一头。风不必是正对着吹来的：侧面扫过的风也算，只有"沿朝向吹入"会把它压住不放。吐货节奏跟风源转速走。**鼓风机前面挂分散网（喷嘴）时同样能触发**——喷嘴把风铺成以自身为中心、边长随转速走的方场（最远 20 格），落在场里的舱口会被吹开，挂在喷嘴侧面或背后的舱口因此也能吃到风。 |
| **智能泄压舱口** | 与泄压舱口同款，多出两件事：侧面槽带数量滑块，一批能到 64 个（滑块的 `≤ / =` 决定"够多少吐多少"还是"凑够一整批才吐"）；红石充能时锁住不吐货。 |
| **共振台** | 台面上放一样东西，接了共振过滤器的机器就按那样东西过滤。四个侧面的滑块设定台面最多堆多少个。红石充能时换上充能外观，并冻结当前这一份过滤规则。 |
| **共振过滤器** | 放进漏斗、溜槽、工作盆的过滤槽里。本身不存规则，规则实时取自它所在网络里各共振台的台面，取并集。台面上放列表过滤器或属性过滤器时，把里面的内容一并镜像过来。 |
| **夜视仪护目镜** | 用工程师护目镜加回响碎片改装，按键切换夜视（默认 `N`，可改键），同时免疫黑暗效果。可装备于头盔槽或 Curios 头盔槽。 |
| **回响望远镜** | 使用时视线穿透障碍，把方块显示为轮廓，单次最长 60 秒。潜行右键打开过滤界面，可放物品或 Create 的列表过滤器。不放过滤时默认识别 `#c:ores`。 |
| **可燃冰** | 可燃冰（400 tick）与可燃冰块（4000 tick）均可作燃料，并列入烈焰人燃烧室的过热燃料。9 个可燃冰合成一块可燃冰块。 |

共振台与共振过滤器合成一个网络。手持共振台潜行右键已有的共振台，手上的台子就记住这张网络；把带网络的台子放到别处，新台子自动并进同一个网络。手持共振过滤器潜行右键共振台，过滤器接入该台的网络；潜行右键空气退出。网络内容可以用显示链接器读出来。

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
| 远古残骸 | 下界 | 8 – 22 | 矿脉，rarity 1/16，母岩周围附带 2 块远古残骸；生成出来的熔岩罐是空的 |
| 荧石 | 下界 | 天然荧石团处 | 替换荧石团最低一块，概率由 `worldgen.glowstoneBuddingChance` 决定（默认 0.1）；可另配 `worldgen.glowstoneGenerateBuds` 等项控制附带的芽 |
| 回响 | 主世界 | −64 – 0 | 深暗之域，生成于幽匿之中 |
| 可燃冰 | 主世界 | 深海海床之下 | 结构，每区块 1/256（`worldgen.flammableIceChance`），周围可散布灵魂沙（`worldgen.soulSandGenerate`） |
| 玫瑰石英 | — | — | 不自然生成 |
| 福鲁伊克斯 | — | — | 不自然生成 |

每一种都能在配置文件中单独开关（`worldgen.generate_<母岩 id>`）。可燃冰结构与荧石母岩另有各自的概率配置项。

配置文件里的配置按功能分成了几张表：`[worldgen]` 世界生成、`[infection]` 母岩侵染、`[accelerator]` 催生器、`[spyglass]` 回响望远镜、`[crystalBattery]` 水晶电池、`[cleaner]` 动力吸尘器（客户端配置另有 `[goggles]` 夜视仪护目镜）。表名就是配置项的前缀。

自然生成的回响母岩被破坏时会召唤监守者，判定依据是方块的 `can_summon` 状态，玩家自己放置的不会触发。

---

## 五、兼容性

| 模组 | 关系 | 说明 |
| --- | --- | --- |
| Create | 必需 | 6.0.10+ |
| AE2 | 可选 | 启用福鲁伊克斯母岩；AE2 的催生器（Growth Accelerator）同样能加速本模组的母岩，护目镜的倍率会把它计入 |
| Curios | 可选 | 夜视仪护目镜可装备于 Curios 头盔槽（不装 Curios 时走原版头盔槽） |
| JEI | 可选 | 提供「母岩信息」页：每个母岩一页，写明生长条件、生长速度与生成条件；另有「侵染」页写明母岩把什么变成什么、多大概率，母岩及其芽、晶簇、晶簇产物均可反查 |
| KubeJS | 可选 | 用脚本注册自己的母岩，见下节 |

未安装 AE2 时模组正常启动，只是不注册福鲁伊克斯相关内容。母岩（含原版紫水晶母岩）、电力与动力催生器、智能钻头、动力吸尘器、共振台与共振过滤器都附带 Ponder 教学场景：每个功能一张，运行信息由工程师护目镜读出。

模组共提供 31 项进度，分为母岩、催生器、机械、装备、深海与深暗五个分支。

---

## 六、用 KubeJS 添加与修改母岩

本模组的生长引擎是公开的，可以用 KubeJS 注册自定义母岩（数量不限），也可以改**已经存在**的母岩——自带的、别的脚本做的、附属模组的都行。两种用法共用同一套选项，都不需要写 Java、也不需要数据包。

脚本统一放在游戏目录的 `kubejs/startup_scripts/` 下，文件名随意、以 `.js` 结尾；`CustomBudding` 与 `CustomBuddingOptions` 直接是脚本里的全局对象，不用 `Java.loadClass`。

### 教程一：从零做一块自己的母岩

**第 1 步 · 先让它存在。** 建一个脚本文件，最小内容就一行：

```js
// kubejs/startup_scripts/my_crystal.js
StartupEvents.registry('block', event => {
  CustomBudding.create(event, 'my_crystal', 20)   // 概率 1/20
})
```

进游戏用 `/give @s kubejs:my_crystal_budding` 验证（不写命名空间就落在 `kubejs` 下）。**方块没出现先看日志**：`logs/kubejs/startup.log` 会写 `Loaded N/N KubeJS startup scripts ... with 0 errors`，报错也在同一个文件里（脚本写错是整段不执行，不是只错一行）。

**第 2 步 · 摆到世界里看它长。** 母岩的随机刻已经接好，放下来就会自己长芽、再长成晶簇。原版随机刻很慢（平均几十秒才轮到一次），想快点看效果就在旁边放一台催生器，或把概率调大：`.chance(1)` 是每次抽中必推进。

**第 3 步 · 给它加条件。** 加什么、怎么写见下方「选项参考」，一行一项、可混用：

```js
StartupEvents.registry('block', event => {
  CustomBudding.create(event, 'my_crystal', new CustomBuddingOptions()
    .chance(10)                                // 每次随机刻 1/10 推进一级
    .minLight(4).maxLight(12)                  // 只在亮度 4–12 的生长格长
    .growthDimensions('minecraft:overworld')    // 只在主世界满速，别处按下一行的概率
    .growthBiomes('warm')
    .outsideGrowthChance(0.1))                  // 出了自己的地盘只剩一成概率
})
```

验证靠**工程师护目镜**：看向自己这块母岩会列出当前生效的这几项（只给「缓慢」「必须足够暗」这类定性说法）；JEI 的母岩信息页同样有一页。

**第 4 步 · 让它烧流体。** `needfluid` 会同时给母岩配一个流体罐：

```js
    .needfluid('minecraft:lava', 250, 1000)    // 认熔岩，每次成功生长扣 250 mB，罐最多 1 B
```

罐要先用管道灌、或手持桶右键灌（罐空时正好灌满一桶）：**罐里不够一次消耗就不长**（不是减速）。护目镜会显示液位与每次消耗，比较器读液位；拿错液体或倒不进去时不会把液体洒在母岩旁边（潜行右键才会照原版倒出去）。

**第 5 步 · 让它掉东西。** 晶簇普通破坏的掉落由 `dropItem` 决定（不写就什么都不掉；精准采集始终掉晶簇本体）：

```js
    .dropItem('mypack:my_shard', 2)             // 掉 2 个，受时运加成
    .buddingLevel('stone').stageLevel('iron')   // 母岩要石镐、芽/簇要铁镐才拿得到掉落
```

**第 6 步 · 换外观与名字。** 上面几步都用默认贴图（原版紫水晶那套），所以零资源就能跑。要自己的外观就准备 5 张 16×16 贴图，放进

```
kubejs/assets/<命名空间>/textures/block/<文件名>.png
```

（命名空间就是方块 id 里 `:` 前那段，不写命名空间就是 `kubejs`），再用 `buddingTexture('mypack:block/my_budding')` / `stageTextures(...)` 指过去。显示名最省事的是 `.displayName('我的母岩').stageDisplayNames('小芽', '中芽', '大芽', '晶簇')`（一次设好、所有语言都显示它）；不写就由 KubeJS 按 id 自动生成英文名，要按语言分别翻就在 `kubejs/assets/<命名空间>/lang/zh_cn.json` 里写 `block.<命名空间>.<方块 id>`。音效不用资源文件，写原版音效名即可（`'stone'` / `'amethyst'` / `'crop'` …）。

**第 7 步 · 让它转化周围的方块。** `transform(输入, 产物, n[, r])` 让母岩把附近的方块换掉：每随机刻 1/n 的概率发生一次，命中后在半径 r（默认 1，即紧邻一圈）内随机取一格。可以写多个（各算一条规则、各有各的 n），输入还能写方块标签：

```js
    .transform('minecraft:iron_block', 'mypack:my_crystal_budding')   // 把紧邻的粗铁块变成自己
    .transform('#c:storage_blocks/iron', 'mypack:my_crystal_budding') // 输入写标签：标签里的方块都算
    .transform('minecraft:stone', 'minecraft:iron_ore', 20)           // 产物与概率都自己填：1/20
    .transform('minecraft:stone', 'minecraft:iron_ore', 20, 3)        // 第四个参数：取格半径 3（7×7×7）
```

产物写自己的方块 id（脚本母岩是 `<命名空间>:<id>_budding`）就成了自带家族那种「母岩会自己变多」；这类规则和「石头 → 铁矿石」一样都是**侵染配方**，统一受配置里的侵染开关与名单管（见「一、母岩」的「母岩再生」）。

第三个参数是概率基数：**每随机刻 1/n 的概率**发生一次（半径 1 格）。不写就是 25000（与自带家族的再生传播同档，想快点看到效果就靠催生器堆随机刻）；想让它经常发生（比如做石头变铁矿这种玩法）就写小一点，1/20 配一台催生器几秒就能看见一次。每条 transform 各有各的 n，互不干扰。

### 教程二：改一块自带母岩（以可燃冰母岩为例）

需求：**可燃冰母岩要通入水才能生长，概率改成 1，生长格亮度至少 12**。用 `modify` 一行一行加：

```js
// kubejs/startup_scripts/modify_flammable_ice.js
StartupEvents.registry('block', event => {
  CustomBudding.modify('create_crystal_industry:flammable_ice_budding', new CustomBuddingOptions()
    .needfluid('minecraft:water', 250, 1000)  // 加一个水罐：每次成功生长扣 250 mB，最多存 1 B
    .chance(1)                                // 每次随机刻必推进一级（出厂是 1/5）
    .minLight(12))                            // 生长格亮度至少 12（原本不限）
})
```

要点：

- **合并语义**：没写到的项保持方块现状。上面只动了水罐、概率、亮度下限——可燃冰原本的「生长格必须是水源」等设定一个没变。想显式清掉某项也有写法：`maxLight(-1)` / `minLight(-1)` = 那一端不限制、`requiresWater(false)` = 不再要求水、`growthDimensions()`（不给参数）= 清掉维度限制、`needfluid('none')` = 取消流体需求、`buddingLevel('none')` = 取消开采等级。
- **id 写母岩方块 id**（`..._budding`），末尾的 `_budding` 可省；裸 id 落在 `kubejs` 命名空间。目标写错不会静默：启动日志里会有一条 `modify 的目标 ... 不是本模组驱动的母岩方块` 的警告。
- **水罐与含水要求是两回事**：后者是它本来的规则，前者是这次新加的门槛，两个都得满足。
- **改完要重载区块**（或重新放一块）才会换上新参数：罐是建方块实体时读定义的。换过流体的罐里若还留着旧流体（比如远古残骸的熔岩被改成水），护目镜会显示「罐里的流体不对」，用管道或桶抽掉即可。
- **验证**：罐空时贴着水也不长 → 灌一桶水后立刻开始长；生长格亮度低于 12 不长；JEI 与护目镜上显示的是改后的概率/亮度/液位。
- 带 AE 付费（福鲁伊克斯）或 FE 付费的母岩**不能加流体罐**——它们的方块实体要持网络节点/储罐，脚本里写了会当场报错（那会让付费被静默跳过）。

### 教程三 · 部署与排错速查

| 症状 | 先看这里 |
| --- | --- |
| 脚本整段没生效 | `logs/kubejs/startup.log` 的 `with N errors`；脚本报错是整段不执行 |
| 方块找不到 | 用 `/give` 验证，别只看创造栏（脚本方块默认进 `kubejs` 页，`group` 可改） |
| 母岩长不出来 | 护目镜看它当前的条件；催生器只催**会吃随机刻**的方块（本模组注册的母岩都吃） |
| 晶簇不掉东西 | 看有没有写 `dropItem`；不写就是什么都不掉（精准采集另算） |
| `modify` 没效果 | 启动日志里的目标警告；以及"改完要重载区块"这一条 |
| 服务器上行为不一致 | 脚本两端各跑一遍，服务端与客户端脚本要一致（否则只是显示对不上） |

两条硬性要求：`create` 与 `modify` **必须写在启动脚本里**（`kubejs/startup_scripts/`）；**启动脚本只在游戏启动时跑一次**（`/reload` 不会重跑它们），JEI 的母岩信息页也只在启动时构建一次——所以改了脚本要重启游戏，删掉的那一行也就随之撤销。

### 一行注册一整族

母岩本体、小芽、中芽、大芽、晶簇五个方块一次全部生成。

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
    .needfluid('minecraft:lava', 250, 1000)      // 生长要消耗流体（像远古残骸母岩烧熔岩那样）：
                                                 // 每次成功生长扣 250 mB，罐最多存 1 B；不写 = 不烧流体
                                                 // 详见下方「流体消耗」
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
| `needfluid(id, 消耗, 容量)` / `needfluid(id)` | 流体 id 或 `'#标签'` + 两个整数 mB（250 / 1000） | 生长要消耗流体：每次成功生长扣「消耗」mB，罐里不够就不长 |
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
| `transform(输入, 产物, n, r)` / `transform(输入, 产物, n)` / `transform(输入, 产物)` | 方块 id，输入也可写 `'#标签'`，可写多次；n = 概率基数（25000）、r = 取格半径（默认 1，上限 8） | 侵染周围方块：每随机刻 1/n 的概率发生一次，命中后在半径 r 内随机取一格；与家族表自带的规则一样受配置管 |
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

### 流体消耗

`needfluid(流体, 每次消耗, 容量)` 让母岩像远古残骸母岩那样「烧流体生长」：母岩自带一个小罐，每次**成功**生长扣掉一次消耗，罐里不够一次的量就完全不生长（不减速，也不半价推进）。

| 参数 | 写法 | 说明 |
| --- | --- | --- |
| 流体 | `'minecraft:lava'`、`'#minecraft:lava'` 或 `'create:potion[minecraft:potion_contents={potion:"minecraft:swiftness"}]'` | 流体 id 按**流体类型**判定，同一种流体的静止与流动变体都算（桶灌的、管道抽的都认）；加 `#` 前缀则写流体标签，一次收下标签里的全部流体；方括号里写**数据组件**（与原版物品的写法一致，值用 SNBT）则精确到"哪一种"——创造那一整个药水流体就靠它分出具体药水 |
| 每次消耗 | 整数 mB，默认 250 | 每次成功生长扣多少，必须 ≥ 1 |
| 容量 | 整数 mB，默认 1000 | 罐的容量，必须 ≥ 每次消耗，否则永远长不出来（这种写法会当场报错） |

只传流体（`needfluid('minecraft:lava')`）时，消耗与容量用默认的 250 / 1000，正好与远古残骸母岩同价（一桶）。流体名写错会当场报错，而不是安静地做出一个永远不长的方块。

写**数据组件**时判定是**子集**：罐里那份流体只要含有这些组件就算数，多出来的组件不影响（与 NeoForge 的 `DataComponentFluidIngredient.of(false, stack)` 一致，创造内部也是这么写的）。**标签不能带组件**——标签里可能有好几种流体，一条组件说明不了它们全部，这种写法会当场报错。典型用途就是机械动力的药水：所有药水共用 `create:potion` 一个流体，`needfluid('create:potion')` 只能表达"任意药水"，写成上面那种带组件的写法才拦得住别的药水，护目镜与 JEI 也才会显示「迅捷药水」而不是笼统的「药水」。药水的**等级**原版名字里本来就没有（迅捷 I 与迅捷 II 都叫 `item.minecraft.potion.effect.swiftness`，等级只写在效果那几行），而这一行是"只认哪一种"，所以这里照原版 `potion.potency` 的习惯补了后缀——显示成「迅捷药水 II」。

写**流体 id** 时，护目镜与 JEI 页面显示的是流体的翻译名（`minecraft:lava` → 「熔岩」，`create:honey` → 「蜂蜜」——流体自己带了翻译名，不需要本模组做什么）。写**标签**时标签没有自己的名字，所以另有一条带兜底的翻译键 `create_crystal_industry.fluid_tag.<命名空间>.<路径>`：本模组已经把常见的那批都备好了——原版的 `#minecraft:lava` / `#minecraft:water`，加上 NeoForge 公共标签 `#c:*`（水、熔岩、奶、药水、几种炖菜、经验等；`#c:chocolate` 与 `#c:tea` 是机械动力补进来的。只有 `#c:gaseous`、`#c:hidden_from_recipe_viewers` 这两条描述流体性质、不表示某种具体流体的标签没翻，故意让它退回字面写法）；别的标签由整合包/附属模组补自己的语言键，没人补就原样显示 `#mymod:syrup`。

流体怎么进罐：

- **管道 / 泵**：方块实现了 NeoForge 的流体能力，Create 的流体管道、泵，或任何认这个能力的机器都能直接灌进去、抽出来；
- **手持容器右键**：桶灌进去、舀出来。一桶 = 1000 mB，所以桶只在「罐空」或「罐满」时管用，半桶这种零头得靠管道补（与远古残骸母岩一致）。罐收不下（拿错流体）或没得舀时，这一下会被方块自己吞掉，液体**不会**被倒在母岩旁边——想故意往旁边倒液体就潜行着右键。**药水得走管道**：药剂瓶没有物品流体能力（创造只给杂物桶注册了），手持右键灌不进去；
- **比较器**读液位：空罐 0，其余按比例给 1–15；
- **护目镜**多两行：当前流体量与每次生长的消耗；不够一次时额外红字提示「当前不会生长」。

罐里的流体不会随方块带走：破坏母岩时（含精准采集）罐就没了，流体一并丢失，挪地方前先用管道抽出来。

> 流体需求要配上「能存流体的方块实体」，本模组自带家族与 `create` 注册的母岩都自动接好了（同一个通用罐，见「一、母岩」里的『远古残骸母岩的熔岩罐』）。自己拼低阶接口的方块写了 `fluidRequirement(...)` 只是一条参数，罐与扣费得自己实现——见下方「低阶接口」。

### 掉落、工具与开采等级

- **掉落**：芽无论怎样破坏都只有精准采集才掉本体。晶簇普通破坏掉落 `dropItem(物品, 数量)` 指定的东西，精准采集掉晶簇本体；不写 `dropItem` 则普通破坏什么都不掉。数量受时运加成，每一级额外给 0–等级 个，与本模组自带晶簇的掉落表一致。
- **开采工具**决定「用什么挖最快」。可写 `pickaxe` / `axe` / `shovel` / `hoe` 之一——这是原版挖掘标签 `#minecraft:mineable/*` 的全部四个，写别的裸名字会当场报错，避免登记一个没人读的标签。也可以写完整的方块标签 id（含 `:`，如 `'mymod:mineable/wrench'`）；写 `null` 则一个标签都不挂，徒手就是最快。想让五个方块共用一个工具就用 `tool(...)`。
- **开采等级**决定「什么工具才拿得到掉落」。裸名字取原版三档标签 `#minecraft:needs_<名字>_tool`：`'stone'` / `'iron'` / `'diamond'`；也可以写完整标签 id（如 `'neoforge:needs_netherite_tool'`）。不写或写 `'none'` 表示不设等级。

  设了等级会连带给方块加上 `requiresCorrectToolForDrops`（只挂标签是没人读的），因此设完之后有两个后果：**等级不够的工具挖下来什么都不掉**，精准采集也一并失效；而且**工具种类必须对**——母岩设为斧头加 `'stone'` 时，石斧掉、石镐不掉。想要徒手也能拿到掉落就不要设等级，本模组自带的芽与晶簇正是如此。

### 修改已有的母岩

同一套选项也能改**已经注册好的**母岩——自带的家族、别的脚本注册的、附属模组的都行：

```js
// kubejs/startup_scripts/my_tweaks.js
StartupEvents.registry('block', event => {
  CustomBudding.modify('create_crystal_industry:ancient_debris_budding', new CustomBuddingOptions()
    .chance(10)                                 // 出厂是慢档（1/20），再快一倍
    .growthDimensions('minecraft:overworld')    // 顺手解掉"只在下界满速"
    .needfluid('none'))                         // 不再扣熔岩
})
```

**没写到的项保持方块现状**（合并，不是替换）：上面这段只动了概率、维度与流体，远古残骸原本的其它参数一个没变。想显式清掉某一项也有写法：`maxLight(-1)` / `minLight(-1)` = 那一端不限制、`requiresWater(false)` = 不再要求水、`growthDimensions()`（不给参数）= 清掉维度限制（`growthBiomes()` 同理）、`needfluid('none')` = 取消流体需求、`buddingLevel('none')` = 取消开采等级。

母岩 id 写**方块 id**（`..._budding`），末尾的 `_budding` 可省；裸 id 落在 `kubejs` 命名空间（与 `create` 一致）。目标不存在、或者不是本模组引擎驱动的母岩，启动日志里会有一条警告——脚本跑在方块注册事件里，那时别的模组的方块还没入表，当场校验不了。

| 能改 | 说明 |
| --- | --- |
| 概率、光照上下限、含水、生长维度 / 群系、地盘外概率 | 生长参数的常规项 |
| `needfluid(...)` | 给**任何一块**母岩加、改、取消流体消耗——罐是通用的（`FluidTankBuddingBlockEntity`），远古残骸那份熔岩也是它 |
| `dropItem(...)` / `dropCount(...)` | 改的是晶簇的掉落；自带家族也会被这条规则接管（原本的战利品表不再生效），`.dropItem('none')` = 什么都不掉。只写 `.dropCount(...)` 会报错——改掉落是整条规则接管战利品表，得连物品一起写。JEI 母岩信息页左上角那栏与对着产物按 R 都跟着新掉落走 |
| `buddingLevel(...)` / `stageLevel(...)` | 开采等级改在运行时判定：只认原版三档（`stone` / `iron` / `diamond`），只影响"拿不拿得到掉落"这一步，方块标签本身不变 |
| `transform(输入, 产物, n[, r])` | 追加侵染规则（可写多次、各有各的 n 与 r），**不会顶掉方块原有的规则**。要停掉某块母岩的侵染请用配置里的名单，要全停用总开关 |

**改不了**的项（在方块注册时就固定下来，脚本里写了会当场报错）：材质 `buddingTexture` / `stageTextures`、破坏音效 `buddingSound` / `stageSound`、破坏工具 `buddingTool` / `stageTool`、翻译名 `displayName` / `stageDisplayNames`，以及**创造栏归属 `group`**——前四样是烘进方块属性与资源里的身份，`group` 是"这块母岩属于哪一页"，而 `modify` 只管**给母岩加特性**，不改它的身份。要换这些请用 `create` 注册一块新的母岩。

两条注意事项：

- **必须写在启动脚本里**（与 `create` 同一个 `StartupEvents.registry`）：它改的是注册期定下来的东西，而且 JEI 的母岩信息页只在启动时构建一次。
- 脚本在**两端各跑一遍**（客户端一份、专用服务端一份），所以两端看到的参数一致；服务端与客户端脚本不同时，护目镜与 JEI 的显示会跟服务端的实际行为对不上（只影响显示）。

> 用 `needfluid` 改过参数（或加罐、取消罐）的母岩，罐是在**方块实体创建时**按新参数建的：世界上已经摆下的方块要等区块重载（或者重新放一块）才会换成新参数。

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

- **自动挂标签**：母岩自动进入 `#c:budding_blocks`，三档芽进入 `#c:buds`、晶簇进入 `#c:clusters`（都是方块与物品两份，NeoForge 把这三类分开）。五个方块按 `buddingTool` / `stageTool` 进入对应的挖掘标签，按 `buddingLevel` / `stageLevel` 进入对应的等级标签（默认只有 `#minecraft:mineable/pickaxe`，不挂等级）。因此智能钻头的精准采集能直接采下你的母岩本体，AE2 的催生器也会加速它，不需要手写标签。
- **护目镜**：戴上工程师护目镜看向自定义母岩，会显示当前生长倍率，以及生长速度、光照要求、含水要求、生长环境（维度 / 群系，只给「缓慢」「必须足够暗」这类定性说法，不报具体数值）；写了 `needfluid` 的还会显示罐里的流体量与每次生长的消耗（液位是状态不是参数，所以这里给具体数字）。自带家族平时只显示倍率那一行（写了 `growthDimensions` / `growthBiomes` 的会多一行「只在 X 生长得最快」）；**被 `modify` 改过的家族母岩**会按改后的值多显示那几行——参数被脚本改过之后，游戏里看得见。
- **创造栏**：KubeJS 注册的方块默认不进任何标签页，容易让人误以为没注册成功，所以这里默认放进 KubeJS 那一页；`group(...)` 可改为原版标签页，`group(null)` 则完全不进标签页（只能用 `/give` 取）。
- **名字与外观**走资源包与语言文件；不指定显示名时由 KubeJS 按 id 自动生成英文标题（`example_crystal_small_bud` → "Example Crystal Small Bud"）。方块物品与方块共用同一个语言键，背包、掉落物、创造栏会一起变。

### 已知限制

- 脚本母岩的概率完全由脚本里的 `chance` 决定，不参与任何全局档位；想改一块**已有**母岩的概率就用 `CustomBudding.modify(...)`（自带家族同样能改）。
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
// 流体消耗同样在链上，但只写它不会扣流体：
//   .fluidRequirement('minecraft:lava', 250, 1000)   // 认熔岩，每次生长 250 mB，罐容量 1 B
// 引擎是按调用方递进来的付费钩子决定扣不扣的（tryGrow 的第四个参数），罐得由方块自己提供：
// CustomBudding 注册的母岩自带这个罐（见上节「流体消耗」），
// 自己 event.create 的普通方块没有方块实体，写了也只是定义里的一条参数，没人照着扣。
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

生长速度也在这张表里：给那一条的 `Growth(...)` 传一个 `GrowthSpeed` 档位即可（`NORMAL` / `FAST` / `SLOW` / `VERY_SLOW`，见 `BuddingFamily.GrowthSpeed`）；不写就是「正常」。红石与青金石是快档、远古残骸是慢档，其余都是正常档（极慢档没有出厂成员）。想改**已经存在**的母岩（含自带家族）的概率与其它生长参数，不必动这张表——脚本用 `CustomBudding.modify(...)` 就行。

流体消耗同样是表里的一项：给 `Growth(...)` 传一个 `FluidRequirement`（如远古残骸的 `FluidRequirement.ofTag(FluidTags.LAVA, 250, 1000)`），方块就会自带流体罐——通用罐的合法方块表是按"家族表里声明了流体"自动算出来的，不必再登记什么。

### 数据生成

生成 JSON 资产：`./gradlew runData`。**运行前必须确保 `run/mods` 里有 AE2**，否则会直接中止——这是为了避免已生成的福鲁伊克斯资产被判定为过期文件而删除。输出目录 `src/generated/resources` 已纳入版本管理。

以下文件由数据生成接管，请勿手写：`blockstates/`、`models/block/`、`models/item/`、母岩与芽的掉落表、`c:budding_blocks` / `c:buds` / `c:clusters` 标签、`mineable/pickaxe` 与 `needs_*_tool` 标签，以及**侵染配方**（`data/create_crystal_industry/recipe/budding_conversion/`，内容来自家族表里各家族的声明，写法见第一节的「侵染配方」）。

仍需手写的：材质（含 `.mcmeta`）、晶簇与福鲁伊克斯的掉落表（结构与模组条件无法由生成器等价复刻）、世界生成 JSON、语言文件、纯物品的模型（`models/item/`），以及那些合成/加工配方（`data/create_crystal_industry/recipe/` 下除 `budding_conversion/` 以外的部分）。

> 生成器会**校验贴图文件存在**（模型生成时读 `ExistingFileHelper`），所以新家族的贴图还没画时，得先往目标路径放占位图再跑 `runData`，跑完删掉占位图、只留文件夹。流体的贴图与颜色不归生成器管，在 `client/ModFluidExtensions` 里写死路径。

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
// 家族若声明了流体需求（Growth 的最后一项传 FluidRequirement），再补这一句：
// BuddingRegistration.declareFluidBuddingBlock(myBudding.getId());
```

`declareBuddingBlock` 不是可有可无的礼貌调用：区块从 NBT 恢复方块实体时会校验 `BlockEntityType#isValid`（`LevelChunk:392`），不在共享 BE 合法方块表里的方块，其方块实体会在**区块重载后被丢弃**，护目镜随即失效。流体罐同理——本模组自带家族的罐表是按家族表自动算出来的，**你的家族不在那张表里**，所以带流体必须自己声明一次。

使用了 `GenericBuddingBlock` 的方块到这里就齐了：它自带家族定义，JEI 的母岩信息页能直接读出它的参数。**自己拼低阶接口**（自己实现 `randomTick` 调引擎）的方块没有定义可读，需要再补一次声明——另外别忘了方块属性里的 `.randomTicks()`：不写的话，原版的随机刻与催生器都不会碰它（`GenericBuddingBlock` 是从原版紫水晶母岩整体拷贝属性的，自带这一项）。

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

自己拼低阶接口的方块还有一个可选项：**接上脚本的 `modify` 覆盖**，让服务器主也能像调本模组母岩那样调它——在自己的定义外面套一层 `BuddingOverrides.apply(this, definition)` 即可（本模组两个母岩方块类就是这么做的）。不套也没什么，只是脚本对它的 `CustomBudding.modify` 只能改掉落、创造栏与开采等级，改不动生长参数。

群系条目的判定顺序：命中任一**否定**项直接出局；否则至少命中一条**肯定**项才算满足；只写否定项时，肯定那一侧视为「全部群系」（`growthBiomes("!cold")` 即除了寒冷群系哪里都长）。

其余资源自备：方块的注册与贴图、物品、掉落表，以及把方块加入 `#c:budding_blocks` 标签（智能钻头的精准采集与 AE2 的催生器读它）。
芽与晶簇同理——要进 `#c:buds` 与 `#c:clusters`，别的模组按类别筛方块时才认得它们。

### 气候关键字

`cold` / `warm` / `hot` 三个关键字的分档规则：

| 关键字 | 覆盖范围 |
| --- | --- |
| `cold` | 末地全域，或群系基础温度 < 0.3（雪原、冰刺、雪针叶林、冰洋、雪坡、冻峰，以及针叶林、山地这类寒凉群系） |
| `warm` | 其余群系（平原、森林、丛林、沼泽、海洋、蘑菇岛、裸岩峰等） |
| `hot` | 下界全域，或群系基础温度 ≥ 1.2（沙漠、恶地、热带草原） |

「末地 / 下界全域」走的是群系标签 `#minecraft:is_end` / `#minecraft:is_nether`，因此使用这两套群系的模组维度同样算数。阈值可改：`GrowthEnvironment.Climate` 中的两个常量。

---

## 关于开发

本模组的代码与文档有相当一部分是在 AI 辅助下完成的，模型与贴图均为手工制作。设计方向、代码审查和实机测试由维护者负责，所有改动都要在游戏里跑通才会合入；若有疏漏，那是我们的责任，不是工具的。欢迎照常报 issue。

---

## 授权

**本模组以 All Rights Reserved 授权，但整合包可直接收录，无需另行申请。**

**任何整合包**都必须在 `manifest.json` / `.mrpack` 中按本模组的项目 ID 与文件 ID 引用，由启动器自行下载——**不得把模组 jar 直接打进压缩包**。转载本模组本体的独立下载，或分发修改版（含加过翻译的 jar），均需事先书面许可。翻译请以资源包形式分发，或提 PR 合入官方 jar。

完整条款见 [LICENSE.txt](LICENSE.txt)。

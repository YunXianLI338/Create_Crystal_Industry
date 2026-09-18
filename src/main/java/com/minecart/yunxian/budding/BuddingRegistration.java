package com.minecart.yunxian.budding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import com.minecart.yunxian.budding.BuddingFamily.BlockEntityKind;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * 附属模组（以及任何想接入本模组母岩体系的第三方方块）的注册入口。
 * <p>
 * 用法：在自己的 {@code @Mod} 构造器里调用本类的方法——<b>必须在方块注册事件之前</b>，
 * 而方块实体的合法方块表是在注册事件里才组装的，所以时序天然安全。
 * <pre>
 * BuddingRegistration.declareBuddingBlock(MY_BUDDING.get());
 * BuddingRegistration.declareKnownId("my_budding");
 * </pre>
 * 生长行为本身不需要在这里注册：让自己的方块在 {@code randomTick} 里调用
 * {@link BuddingGrowthEngine#tryGrow}，参数用 {@link GrowthDefinition} 给出即可。
 */
public final class BuddingRegistration {

    private BuddingRegistration() {
    }

    /** 外部声明的母岩方块（要保持声明顺序，所以用 LinkedHashSet） */
    private static final Set<Block> DECLARED_BLOCKS = new LinkedHashSet<>();
    /** 按 id 声明的母岩方块，延后到方块实体类型注册时解析 */
    private static final Set<ResourceLocation> DECLARED_BLOCK_IDS = new LinkedHashSet<>();
    /** 外部声明的家族 id：让配置文件的四档能列出它们 */
    private static final Set<String> DECLARED_IDS = new LinkedHashSet<>();
    /** 外部声明的生长定义（按方块）：自己实现 randomTick 的方块靠它让 JEI 信息页也能读到参数 */
    private static final Map<Block, Supplier<GrowthDefinition>> DECLARED_DEFINITIONS = new LinkedHashMap<>();
    /** 同上，但按方块 id 声明；首次查询时解析成上面的那张表 */
    private static final Map<ResourceLocation, Supplier<GrowthDefinition>> DECLARED_DEFINITION_IDS =
            new LinkedHashMap<>();

    /**
     * 声明一个母岩方块：它会被加进本模组的共享护目镜方块实体。
     * <p>
     * <b>这一步不是可选的礼貌行为</b>：区块从 NBT 恢复方块实体时会校验
     * {@code BlockEntityType#isValid}（{@code LevelChunk:392}），不在合法方块表里的方块，
     * 其方块实体会在区块重载后被丢弃——护目镜信息随即失效。
     */
    public static void declareBuddingBlock(Block block) {
        DECLARED_BLOCKS.add(block);
    }

    /**
     * 同上，但用方块 id 声明——给"脚本注册、拿不到方块对象"的场景用
     * （{@code CustomBudding} 在脚本执行时只有 id，方块要等注册事件才建出来）。
     * <p>
     * 解析推迟到方块实体类型注册时（{@link #sharedGogglesBlocks()}），那时方块已经入表：
     * 注册事件里方块总是先于方块实体类型，所以 id 一定能查到。
     */
    public static void declareBuddingBlock(ResourceLocation blockId) {
        DECLARED_BLOCK_IDS.add(blockId);
    }

    /**
     * 声明一个家族 id：配置文件 {@code growthSpeed*} 四档里写它时不会被判为"未知 id"，
     * 服主就能像调自带母岩那样给你的母岩换档位（前提是你的方块走 {@code GenericBuddingBlock}，
     * 概率取自家族 id）。
     */
    public static void declareKnownId(String familyId) {
        DECLARED_IDS.add(familyId);
    }

    /** 外部声明的家族 id；配置校验用它补全"已知 id"集合 */
    public static Set<String> declaredIds() {
        return Set.copyOf(DECLARED_IDS);
    }

    /**
     * 声明一个母岩的生长定义——<b>只有自己实现 randomTick 的方块需要它</b>。
     * <p>
     * 用 {@code GenericBuddingBlock} 建的方块自带 {@code BuddingFamily}、KubeJS 的
     * {@code CustomBudding.create} 建的方块自带 {@code GrowthDefinition}，它们的信息页是天然的；
     * 而"自己拼低阶接口"的方块（见 README 的「低阶接口」）两者都没有，JEI 的母岩信息页
     * 与任何想读参数的地方就只能显示"规则未知"。声明一次，页面就能把概率/光照/含水原样列出来。
     * <pre>{@code
     * BuddingRegistration.declareGrowthDefinition(MY_BUDDING.get(),
     *         () -> GrowthDefinition.of(smallBud, mediumBud, largeBud, cluster, 20));
     * }</pre>
     * 定义用 {@link Supplier} 惰性取：方块与阶段方块可能都还没建出来（脚本注册时尤其如此），
     * 真正读它的时机（JEI 建页）在注册表冻结之后。
     */
    public static void declareGrowthDefinition(Block block, Supplier<GrowthDefinition> definition) {
        DECLARED_DEFINITIONS.put(block, definition);
    }

    /**
     * 同上，但用方块 id 声明——给"脚本注册、拿不到方块对象"的场景用。
     * 解析推迟到 {@link #declaredDefinition(Block)} 首次查询时。
     */
    public static void declareGrowthDefinition(ResourceLocation blockId, Supplier<GrowthDefinition> definition) {
        DECLARED_DEFINITION_IDS.put(blockId, definition);
    }

    /**
     * 查询某方块声明过的生长定义；没声明过返回 {@code null}。
     * <p>
     * 按 id 声明的在这里解析成方块并<b>改挂到方块键上</b>，所以每个方块最多查一次注册表。
     */
    @Nullable
    public static GrowthDefinition declaredDefinition(Block block) {
        Supplier<GrowthDefinition> definition = DECLARED_DEFINITIONS.get(block);
        if (definition == null) {
            definition = DECLARED_DEFINITION_IDS.get(BuiltInRegistries.BLOCK.getKey(block));
            if (definition == null) {
                return null;
            }
            DECLARED_DEFINITIONS.put(block, definition);
        }
        return definition.get();
    }

    /**
     * 共享护目镜方块实体（{@code ModBlockEntities.BUDDING_GROWTH}）的合法方块：
     * 自带家族里用 {@code SHARED_GROWTH} 的那些 + 外部声明的方块。
     * <p>
     * 由 {@code ModBlockEntities} 在方块实体类型注册时调用（那时所有模组的构造器都已执行完）。
     */
    public static Block[] sharedGogglesBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (RegisteredFamily family : BuddingFamilies.ALL) {
            if (family.isRegistered() && family.spec().appearance().blockEntity() == BlockEntityKind.SHARED_GROWTH) {
                blocks.add(family.budding().get());
            }
        }
        blocks.addAll(DECLARED_BLOCKS);
        for (ResourceLocation blockId : DECLARED_BLOCK_IDS) {
            Block block = BuiltInRegistries.BLOCK.get(blockId);
            if (block != null) {
                blocks.add(block);
            }
        }
        return blocks.toArray(Block[]::new);
    }
}

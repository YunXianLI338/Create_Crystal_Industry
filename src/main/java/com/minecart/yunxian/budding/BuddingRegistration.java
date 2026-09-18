package com.minecart.yunxian.budding;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import com.minecart.yunxian.budding.BuddingFamily.BlockEntityKind;

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
    /** 外部声明的家族 id：让配置文件的四档能列出它们 */
    private static final Set<String> DECLARED_IDS = new LinkedHashSet<>();

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
        return blocks.toArray(Block[]::new);
    }
}

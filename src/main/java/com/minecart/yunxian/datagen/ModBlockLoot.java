package com.minecart.yunxian.datagen;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import com.minecart.yunxian.budding.BuddingFamilies.Stage;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

/**
 * 生成「母岩 + 三档芽」的掉落表。
 * <p>
 * 两类形状都取自原版 helper，与手写原件逐字对应：
 * <ul>
 *   <li>母岩：{@code createSingleItemTable} —— 单池、rolls 1、survives_explosion，掉落低一档的方块；</li>
 *   <li>芽：{@code createSilkTouchOnlyTable} —— 单池、rolls 1、match_tool 精准采集，掉落自身。</li>
 * </ul>
 * <b>晶簇与福鲁伊克斯家族的掉落表不在这里生成，仍为手写</b>：
 * 晶簇是两池结构（丝触池 + 非丝触池，rolls 为 4/16/1，幸运公式在 uniform_bonus_count 与
 * ore_drops 之间不同），没有任何原版 helper 能等价复刻，用 setCount+addOreBonusCount 的
 * 惯用写法会把 4 + 4·rand 变成 4 + rand，静默改变产出分布；福鲁伊克斯的 5 张表带
 * {@code neoforge:conditions: mod_loaded ae2}，而 1.21.1 的 LootTableProvider 没有条件钩子。
 */
public class ModBlockLoot extends BlockLootSubProvider {

    public ModBlockLoot(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        for (RegisteredFamily family : BuddingFamilies.ALL) {
            if (!generated(family)) {
                continue;
            }
            add(family.budding().get(), createSingleItemTable(family.spec().buddingDrop().get()));
            for (Stage stage : STAGES) {
                Block bud = family.stage(stage).get();
                add(bud, createSilkTouchOnlyTable(bud));
            }
        }
    }

    /**
     * getKnownBlocks 必须与 generate() 添加的方块完全一致：
     * 少一个会抛 Missing loottable，多一个会抛 Created block loot tables for non-blocks。
     */
    @Override
    protected Iterable<Block> getKnownBlocks() {
        List<Block> blocks = new ArrayList<>();
        for (RegisteredFamily family : BuddingFamilies.ALL) {
            if (!generated(family)) {
                continue;
            }
            blocks.add(family.budding().get());
            for (Stage stage : STAGES) {
                blocks.add(family.stage(stage).get());
            }
        }
        return blocks;
    }

    /** 该家族是否由数据生成掉落表（晶簇与 AE2 联动的家族除外） */
    private static boolean generated(RegisteredFamily family) {
        return family.isRegistered() && !family.spec().ae2Gated();
    }

    private static final Stage[] STAGES = {Stage.SMALL_BUD, Stage.MEDIUM_BUD, Stage.LARGE_BUD};
}

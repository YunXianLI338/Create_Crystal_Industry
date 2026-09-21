package com.minecart.yunxian.battery;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * 判断某个方块 / 物品能不能当晶体塞进水晶电池，以及算哪一档。
 * <p>
 * 判定就是查 {@link CrystalTier} 的四个方块标签，<b>每次现查、不做缓存</b>：标签是数据包内容，
 * 整合包能随时用 {@code /reload} 或数据包改，缓存就得操心失效时机，而一次标签查询只是几次哈希查找，
 * 摊到每格电池上完全可以忽略。档位按枚举声明顺序返回，靠前的档优先。
 */
public final class CrystalCapacities {

    /** 档位表缓存：{@code CrystalTier.values()} 每次调用都会复制一份数组，这里只取一次 */
    private static final CrystalTier[] TIERS = CrystalTier.values();

    private CrystalCapacities() {
    }

    /** 该方块是不是晶体方块；不是则返回 null */
    @Nullable
    public static CrystalTier tierOf(Block block) {
        for (CrystalTier tier : TIERS) {
            if (block.defaultBlockState().is(tier.tag())) {
                return tier;
            }
        }
        return null;
    }

    /** 物品是不是晶体方块（方块物品且方块进了某个档位标签）；不是则返回 null */
    @Nullable
    public static CrystalTier tierOf(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem ? tierOf(blockItem.getBlock()) : null;
    }

    /** 该物品能不能塞进电池 */
    public static boolean isCrystal(ItemStack stack) {
        return tierOf(stack) != null;
    }

    /** 一块该晶体方块的电容量（FE）；不是晶体方块则为 0 */
    public static int capacityOf(ItemStack stack) {
        CrystalTier tier = tierOf(stack);
        return tier == null ? 0 : tier.capacityPerBlock();
    }
}

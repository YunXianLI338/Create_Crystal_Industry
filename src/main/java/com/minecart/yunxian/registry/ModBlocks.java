package com.minecart.yunxian.registry;

import com.minecart.yunxian.*;
import com.minecart.yunxian.block.AcceleratorBlock;
import com.minecart.yunxian.block.CrystalBatteryBlock;
import com.minecart.yunxian.block.MechanicalAcceleratorBlock;
import com.minecart.yunxian.block.MechanicalCleanerBlock;
import com.minecart.yunxian.block.SmartDrillBlock;
import com.minecart.yunxian.item.CrystalBatteryItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.fml.ModList;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * 非母岩方块的注册表：机器、工具与可燃冰装饰方块。
 * <p>
 * 全部母岩（含各级芽与晶簇）由 {@code budding/BuddingFamilies} 的中央定义表注册，
 * 本类只提供注册表本身与 {@link #registerBlock} 工具方法。
 * <p>
 * <b>本类不得引用 BuddingFamilies</b>：BuddingFamilies 依赖本类，反向引用会让静态
 * 初始化成环（BuddingFamilies → ModBlocks → BuddingFamilies），启动即崩。
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Yunxian.MODID);

    // 可燃冰装饰方块（冰音效 + 蓝冰摩擦）
    public static final DeferredBlock<Block> FLAMMABLE_ICE_BLOCK = registerBlock("flammable_ice_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BLUE_ICE)
                    .sound(SoundType.GLASS)
                    .friction(0.989F)));

    //催生器
    public static final DeferredBlock<Block> ACCELERATOR = registerBlock("accelerator",
            () -> new AcceleratorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)));

    //智能钻头
    public static final DeferredBlock<Block> SMART_DRILL = registerBlock("smart_drill",
            () -> new SmartDrillBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));

    // 动力催生器
    public static final DeferredBlock<Block> MECHANICAL_ACCELERATOR = registerBlock("mechanical_accelerator",
            () -> new MechanicalAcceleratorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));

    // 动力吸尘器
    public static final DeferredBlock<Block> MECHANICAL_CLEANER = registerBlock("mechanical_cleaner",
            () -> new MechanicalCleanerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));

    // 水晶电池：属性逐条对齐机械动力流体储罐（铜块底 + 无遮挡 + 始终导电 + 掉落自己算，不走掉落表）
    // 物品用 CrystalBatteryItem：在 2x2 / 3x3 结构上再放一层时一次补齐整层
    public static final DeferredBlock<Block> CRYSTAL_BATTERY = registerBlock("crystal_battery",
            () -> new CrystalBatteryBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
                    .noOcclusion()
                    .isRedstoneConductor((state, level, pos) -> true)
                    .noLootTable()),
            CrystalBatteryItem::new);

    /** AE2 是否加载：可选联动（福鲁伊克斯母岩）的开关 */
    public static final boolean AE2_LOADED =
            ModList.get() != null && ModList.get().isLoaded("ae2");

    private ModBlocks() {
    }

    /** 注册方块并顺带注册对应的 BlockItem */
    public static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> blockSupplier) {
        return registerBlock(name, blockSupplier, BlockItem::new);
    }

    /**
     * 注册方块并顺带注册对应的 BlockItem，方块的物品需要自定义行为时用这个重载
     * （例如水晶电池那个"一次放一层"的 {@link CrystalBatteryItem}）。
     */
    public static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> blockSupplier,
                                                                  BiFunction<T, Item.Properties, ? extends Item> itemFactory) {
        DeferredBlock<T> block = BLOCKS.register(name, blockSupplier);
        ModItems.ITEMS.register(name, () -> itemFactory.apply(block.get(), new Item.Properties()));
        return block;
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}

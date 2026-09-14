package com.minecart.yunxian.registry;

import com.minecart.yunxian.*;
import com.minecart.yunxian.blockentity.*;
import com.minecart.yunxian.blockentity.budding.BuddingGrowthBlockEntity;
import com.minecart.yunxian.blockentity.budding.EchoConvertingBuddingBlockEntity;
import com.minecart.yunxian.blockentity.budding.FlammableIceBuddingBlockEntity;
import com.minecart.yunxian.integration.ae2.AE2BlockEntities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.block.Block;
import java.util.ArrayList;
import java.util.List;

import java.util.function.Supplier;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Yunxian.MODID);

    public static final Supplier<BlockEntityType<AcceleratorBlockEntity>> ACCELERATOR =
            BLOCK_ENTITIES.register("accelerator", () -> BlockEntityType.Builder.of(
                    AcceleratorBlockEntity::new,
                    ModBlocks.ACCELERATOR.get()
            ).build(null));

    public static final Supplier<BlockEntityType<SmartDrillBlockEntity>> SMART_DRILL =
            BLOCK_ENTITIES.register("smart_drill", () -> BlockEntityType.Builder.of(
                    SmartDrillBlockEntity::new,
                    ModBlocks.SMART_DRILL.get()
            ).build(null));

    // 动力催生器
    public static final Supplier<BlockEntityType<MechanicalAcceleratorBlockEntity>> MECHANICAL_ACCELERATOR =
            BLOCK_ENTITIES.register("mechanical_accelerator", () -> BlockEntityType.Builder.of(
                    MechanicalAcceleratorBlockEntity::new,
                    ModBlocks.MECHANICAL_ACCELERATOR.get()
            ).build(null));

    // 动力吸尘器
    public static final Supplier<BlockEntityType<MechanicalCleanerBlockEntity>> MECHANICAL_CLEANER =
            BLOCK_ENTITIES.register("mechanical_cleaner", () -> BlockEntityType.Builder.of(
                    MechanicalCleanerBlockEntity::new,
                    ModBlocks.MECHANICAL_CLEANER.get()
            ).build(null));
    // 可燃冰母岩：纯展示 BE，仅用于护目镜信息
    public static final Supplier<BlockEntityType<FlammableIceBuddingBlockEntity>> FLAMMABLE_ICE_BUDDING =
            BLOCK_ENTITIES.register("flammable_ice_budding", () -> BlockEntityType.Builder.of(
                    FlammableIceBuddingBlockEntity::new,
                    ModBlocks.FLAMMABLE_ICE_BUDDING.get()
            ).build(null));
    // 回响母岩：纯展示 BE，仅用于护目镜信息
    public static final Supplier<BlockEntityType<EchoConvertingBuddingBlockEntity>> ECHO_BUDDING =
            BLOCK_ENTITIES.register("echo_budding", () -> BlockEntityType.Builder.of(
                    EchoConvertingBuddingBlockEntity::new,
                    ModBlocks.ECHO_BUDDING.get()
            ).build(null));
    // 母岩共享的“生长速度”展示 BE（玫瑰石英/荧石走 GenericBuddingBlock，
    // 矿石×4 走 OreConvertingBuddingBlock，石英走 QuartzConvertingBuddingBlock，
    // 红石走 RedstoneBuddingBlock——全部继承 GenericBuddingBlock 的 newBlockEntity）
    public static final Supplier<BlockEntityType<BuddingGrowthBlockEntity>> BUDDING_GROWTH =
            BLOCK_ENTITIES.register("budding_growth", () -> {
                List<Block> buddingBlocks = new ArrayList<>(List.of(
                        ModBlocks.ROSE_QUARTZ_BUDDING.get(),
                        ModBlocks.RAW_IRON_BUDDING.get(),
                        ModBlocks.RAW_GOLD_BUDDING.get(),
                        ModBlocks.RAW_COPPER_BUDDING.get(),
                        ModBlocks.RAW_ZINC_BUDDING.get(),
                        ModBlocks.QUARTZ_BUDDING.get(),
                        ModBlocks.GLOWSTONE_BUDDING.get(),
                        ModBlocks.REDSTONE_BUDDING.get(),
                        ModBlocks.DIAMOND_BUDDING.get(),
                        ModBlocks.EMERALD_BUDDING.get(),
                        ModBlocks.LAPIS_BUDDING.get()
                ));
                return BlockEntityType.Builder.of(BuddingGrowthBlockEntity::new,
                        buddingBlocks.toArray(new Block[0])).build(null);
            });

    // ★ 软依赖：类型刻意写成 BlockEntityType<?>，避免 FluixBuddingBlockEntity
    // 出现在本常驻类的任何签名/描述符中（否则 JVM 校验时会去加载 AE2 类型而崩溃）。
    // 真正的 AE2 引用全部封装在 AE2BlockEntities 内。
    public static final Supplier<BlockEntityType<?>> FLUIX_BUDDING;

    static {
        if (ModBlocks.AE2_LOADED && ModBlocks.FLUIX_BUDDING != null) {
            FLUIX_BUDDING = AE2BlockEntities.registerFluix(
                    BLOCK_ENTITIES, () -> ModBlocks.FLUIX_BUDDING.get());
        } else {
            FLUIX_BUDDING = null;
        }
    }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        if (FLUIX_BUDDING != null) {
            AE2BlockEntities.registerCapabilities(event, FLUIX_BUDDING);
        }
    }

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
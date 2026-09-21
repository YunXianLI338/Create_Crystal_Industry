package com.minecart.yunxian.registry;

import com.minecart.yunxian.*;
import com.minecart.yunxian.blockentity.*;
import com.minecart.yunxian.blockentity.budding.BuddingGrowthBlockEntity;
import com.minecart.yunxian.blockentity.budding.EchoConvertingBuddingBlockEntity;
import com.minecart.yunxian.blockentity.budding.FlammableIceBuddingBlockEntity;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingRegistration;
import com.minecart.yunxian.integration.ae2.AE2BlockEntities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Arrays;
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

    // 水晶电池：多方块容器，骨架复用 Create 的 ConnectivityHandler
    public static final Supplier<BlockEntityType<CrystalBatteryBlockEntity>> CRYSTAL_BATTERY =
            BLOCK_ENTITIES.register("crystal_battery", () -> BlockEntityType.Builder.of(
                    CrystalBatteryBlockEntity::new,
                    ModBlocks.CRYSTAL_BATTERY.get()
            ).build(null));
    // 可燃冰母岩：纯展示 BE，仅用于护目镜信息
    public static final Supplier<BlockEntityType<FlammableIceBuddingBlockEntity>> FLAMMABLE_ICE_BUDDING =
            BLOCK_ENTITIES.register("flammable_ice_budding", () -> BlockEntityType.Builder.of(
                    FlammableIceBuddingBlockEntity::new,
                    BuddingFamilies.FLAMMABLE_ICE.budding().get()
            ).build(null));
    // 回响母岩：纯展示 BE，仅用于护目镜信息
    public static final Supplier<BlockEntityType<EchoConvertingBuddingBlockEntity>> ECHO_BUDDING =
            BLOCK_ENTITIES.register("echo_budding", () -> BlockEntityType.Builder.of(
                    EchoConvertingBuddingBlockEntity::new,
                    BuddingFamilies.ECHO.budding().get()
            ).build(null));
    // 母岩共享的“生长速度”展示 BE：凡是没指定专用 BE 的家族都走这里。
    // 合法方块表见 goggleInfoBlocks()：BuddingRegistration 的汇总 + 原版紫水晶母岩
    public static final Supplier<BlockEntityType<BuddingGrowthBlockEntity>> BUDDING_GROWTH =
            BLOCK_ENTITIES.register("budding_growth", () -> BlockEntityType.Builder
                    .of(BuddingGrowthBlockEntity::new, goggleInfoBlocks())
                    .build(null));

    /**
     * 共享护目镜 BE 的合法方块 = 自带家族 + 附属模组声明的方块 + 原版紫水晶母岩。
     * <p>
     * 原版母岩只是“信息展示”的借名对象：客户端按需为它合成一个临时实例
     * （见 {@code client/budding/VanillaBuddingGoggles}），实例不进世界、
     * 不落盘。但这里仍必须登记——方块实体的构造器会校验
     * {@code BlockEntityType#isValid}，不在表里当场抛异常。
     */
    private static Block[] goggleInfoBlocks() {
        Block[] declared = BuddingRegistration.sharedGogglesBlocks();
        Block[] blocks = Arrays.copyOf(declared, declared.length + 1);
        blocks[declared.length] = Blocks.BUDDING_AMETHYST;
        return blocks;
    }

    // ★ 软依赖：类型刻意写成 BlockEntityType<?>，避免 FluixBuddingBlockEntity
    // 出现在本常驻类的任何签名/描述符中（否则 JVM 校验时会去加载 AE2 类型而崩溃）。
    // 真正的 AE2 引用全部封装在 AE2BlockEntities 内。
    public static final Supplier<BlockEntityType<?>> FLUIX_BUDDING;

    static {
        if (BuddingFamilies.FLUIX.isRegistered()) {
            FLUIX_BUDDING = AE2BlockEntities.registerFluix(
                    BLOCK_ENTITIES, () -> BuddingFamilies.FLUIX.budding().get());
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
package com.minecart.yunxian.registry;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Yunxian.MODID);

    public static final Supplier<CreativeModeTab> YUNXIAN_TAB = CREATIVE_TABS.register("create_crystal_industry_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_crystal_industry"))
                    .icon(() -> new ItemStack(BuddingFamilies.ROSE_QUARTZ.cluster().get()))
                    .displayItems((parameters, output) -> {
                        // 母岩家族：顺序与物品栏一致，AE2 联动的家族排在工具之后
                        for (RegisteredFamily family : BuddingFamilies.ALL) {
                            if (family.isRegistered() && !family.spec().ae2Gated()) {
                                acceptFamily(output, family);
                            }
                        }

                        output.accept(ModBlocks.ACCELERATOR.get());
                        output.accept(ModBlocks.MECHANICAL_ACCELERATOR.get());
                        output.accept(ModBlocks.SMART_DRILL.get());
                        output.accept(ModBlocks.MECHANICAL_CLEANER.get());
                        output.accept(ModItems.ECHO_SPYGLASS.get());
                        output.accept(ModItems.NIGHT_VISION_GOGGLES.get());

                        for (RegisteredFamily family : BuddingFamilies.ALL) {
                            if (family.isRegistered() && family.spec().ae2Gated()) {
                                acceptFamily(output, family);
                            }
                        }
                    })
                    .build());

    /** 一个家族固定 5 个方块（母岩 + 三档芽 + 晶簇），外加家族自定义的追加物品 */
    private static void acceptFamily(CreativeModeTab.Output output, RegisteredFamily family) {
        for (var block : family.blocks()) {
            output.accept(block.get());
        }
        for (var extra : family.spec().appearance().tabExtras()) {
            output.accept(extra.get());
        }
    }

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}
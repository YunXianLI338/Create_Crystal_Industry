package com.minecart.yunxian.registry;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                        // 水晶电池还是半成品，暂时不上物品栏（方块与物品仍在注册表里，/give 能拿到）；
                        // 做完之后把这一行加回来即可
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

    /**
     * 脚本（KubeJS）注册的方块要进的标签页：键 = 标签页 id，值 = 物品 id。
     * <p>
     * 存 id 而不是 {@link Item}：脚本执行时方块还没注册，只能等标签页构建时再解析。
     */
    private static final Map<String, List<ResourceLocation>> SCRIPT_ENTRIES = new LinkedHashMap<>();

    /** 供 KubeJS 助手调用：把某个物品放进指定的创造栏标签页（延后到标签页构建时生效） */
    public static void addScriptedItem(String tabId, ResourceLocation itemId) {
        SCRIPT_ENTRIES.computeIfAbsent(tabId, key -> new ArrayList<>()).add(itemId);
    }

    /**
     * 把脚本注册的方块塞进对应的创造栏标签页——KubeJS 注册的方块**默认不进任何标签页**，
     * 玩家会以为"没注册成功"。匹配很宽松：标签页的完整 id、路径、命名空间三者之一等于配置值即可，
     * 所以写 {@code "kubejs"} 既能命中 {@code kubejs} 也能命中 {@code kubejs:xxx}。
     * <p>
     * 事件由 NeoForge 在构建标签页时触发（KubeJS 自己的 {@code modifyCreativeTab} 也是架在它上面的）。
     */
    public static void addScriptedEntries(BuildCreativeModeTabContentsEvent event) {
        if (SCRIPT_ENTRIES.isEmpty()) {
            return;
        }

        ResourceLocation tab = event.getTabKey().location();
        for (Map.Entry<String, List<ResourceLocation>> entry : SCRIPT_ENTRIES.entrySet()) {
            String group = entry.getKey();
            if (!group.equals(tab.toString()) && !group.equals(tab.getPath()) && !group.equals(tab.getNamespace())) {
                continue;
            }
            for (ResourceLocation itemId : entry.getValue()) {
                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item != null && item != Items.AIR && !alreadyInTab(event, item)) {
                    event.accept(item);
                }
            }
        }
    }

    /**
     * 标签页里已经有这个物品就不要再加：KubeJS 自己也会把脚本注册的方块放进它那一页，
     * 重复 {@code accept} 会让原版直接抛
     * {@code IllegalArgumentException: Itemstack … already exists in the tab's list}，
     * 而标签页是在**打开创造模式背包时**重建的——于是崩在开背包这一步。
     */
    private static boolean alreadyInTab(BuildCreativeModeTabContentsEvent event, Item item) {
        for (ItemStack stack : event.getParentEntries()) {
            if (stack.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}
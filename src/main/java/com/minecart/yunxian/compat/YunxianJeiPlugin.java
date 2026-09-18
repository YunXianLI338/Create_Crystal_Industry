package com.minecart.yunxian.compat;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import com.minecart.yunxian.client.echo.EchoSpyglassFilterScreen;
import com.minecart.yunxian.compat.jei.BuddingInfo;
import com.minecart.yunxian.compat.jei.BuddingInfoCategory;
import com.minecart.yunxian.compat.jei.BuddingInfoCollector;
import com.minecart.yunxian.item.EchoSpyglassItem;
import com.minecart.yunxian.network.SetFilterPayload;
import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class YunxianJeiPlugin implements IModPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    // ==================== 母岩信息页 ====================

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new BuddingInfoCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    /**
     * 每次 JEI 重载配方（进世界、数据包重载）都会重新收集一遍——生成条件正是从数据包里的世界生成 JSON
     * 现读的，所以不缓存，改了 JSON 页面立刻跟着变。
     */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<BuddingInfo> infos;
        try {
            infos = BuddingInfoCollector.collect();
        } catch (RuntimeException e) {
            // 收集失败不该让整个 JEI 配方加载挂掉：宁可这一页空着，也别把报错丢进别人的模组列表里
            LOGGER.error("[Yunxian] 收集母岩信息失败，JEI 的母岩信息页将为空", e);
            return;
        }
        LOGGER.info("[Yunxian] 注册 JEI 母岩信息页：{} 条", infos.size());
        registration.addRecipes(BuddingInfoCategory.TYPE, infos);
    }

    /**
     * 母岩与晶簇本体当催化剂，玩家在 JEI 里对着它们按 R/U 就能翻到信息页。
     * <p>
     * 只登记自带家族与原版母岩：脚本/外来母岩的物品已经由信息页里的"不可见材料"覆盖了检索，
     * 而这里要遍历的 {@code BuddingFamilies} 是纯静态数据，不必为此重跑一遍信息收集。
     */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        List<ItemStack> catalysts = new ArrayList<>();
        for (RegisteredFamily family : BuddingFamilies.ALL) {
            if (!family.isRegistered()) {
                continue;
            }
            addCatalyst(catalysts, family.budding().get());
            addCatalyst(catalysts, family.cluster().get());
        }
        addCatalyst(catalysts, Blocks.BUDDING_AMETHYST);

        if (!catalysts.isEmpty()) {
            registration.addRecipeCatalysts(BuddingInfoCategory.TYPE, catalysts.toArray(ItemStack[]::new));
        }
    }

    /** 方块没有物品时跳过（空 ItemStack 会让 JEI 报错） */
    private static void addCatalyst(List<ItemStack> catalysts, Block block) {
        Item item = block.asItem();
        if (item != Items.AIR) {
            catalysts.add(item.getDefaultInstance());
        }
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        LOGGER.info("[Yunxian] 注册 JEI 幽灵拖拽处理器");
        registration.addGhostIngredientHandler(EchoSpyglassFilterScreen.class, new EchoGhostIngredientHandler());
    }

    /** 幽灵拖拽处理器：只对回响望远镜过滤界面生效 */
    public static class EchoGhostIngredientHandler
            implements IGhostIngredientHandler<EchoSpyglassFilterScreen> {

        @Override
        public <I> List<Target<I>> getTargetsTyped(EchoSpyglassFilterScreen gui,
                                                   ITypedIngredient<I> ingredient,
                                                   boolean doStart) {
            List<Target<I>> targets = new ArrayList<>();

            // 只处理物品拖拽（流体等一律忽略）
            if (ingredient.getType() == VanillaTypes.ITEM_STACK) {
                I typed = ingredient.getIngredient();
                if (typed instanceof ItemStack stack && EchoSpyglassItem.isGhostAllowed(stack)) {
                    Slot filterSlot = gui.getMenu().getFilterSlot();
                    // 注意：JEI 19 的 Target 区域是屏幕坐标，必须加上 guiLeft / guiTop
                    Rect2i area = new Rect2i(gui.getGuiLeft() + filterSlot.x,
                            gui.getGuiTop() + filterSlot.y, 16, 16);
                    targets.add(new EchoTarget<>(gui, area));
                }
            }
            return targets;
        }

        @Override
        public void onComplete() {
        }

        @Override
        public boolean shouldHighlightTargets() {
            return true;   // 拖拽划过过滤槽时由 JEI 画高亮框
        }
    }

    /** 落点：鼠标在 Target 区域松开时被 JEI 回调 */
    private static class EchoTarget<I> implements IGhostIngredientHandler.Target<I> {

        private final EchoSpyglassFilterScreen gui;
        private final Rect2i area;

        EchoTarget(EchoSpyglassFilterScreen gui, Rect2i area) {
            this.gui = gui;
            this.area = area;
        }

        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(I ingredient) {
            if (!(ingredient instanceof ItemStack stack)) {
                return;
            }
            ItemStack copy = stack.copy();
            copy.setCount(1);

            // ① 客户端立即显示幽灵物品（JEI 拖拽不产生点击包，必须自己改槽）
            gui.getMenu().getFilterSlot().set(copy);

            // ② 同步服务端：服务端校验并写入望远镜数据组件
            PacketDistributor.sendToServer(new SetFilterPayload(copy));
        }
    }
}
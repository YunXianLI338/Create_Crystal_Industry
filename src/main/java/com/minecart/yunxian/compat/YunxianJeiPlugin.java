package com.minecart.yunxian.compat;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.client.echo.EchoSpyglassFilterScreen;
import com.minecart.yunxian.item.EchoSpyglassItem;
import com.minecart.yunxian.network.SetFilterPayload;
import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
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
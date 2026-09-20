package com.minecart.yunxian.item;

import com.minecart.yunxian.advancement.YunxianAdvancements;
import com.minecart.yunxian.attachment.EchoAttachments;
import com.minecart.yunxian.config.ModConfig;
import com.minecart.yunxian.menu.EchoSpyglassFilterMenu;
import com.minecart.yunxian.network.EchoRevealPayload;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class EchoSpyglassItem extends Item {

    private static final int USE_DURATION_TICKS = 1200;   // 60 秒

    public EchoSpyglassItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(ItemStack.EMPTY)));
        return stack;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                openFilterMenu(serverPlayer, hand);
            }
            return InteractionResultHolder.success(stack);
        }

        player.startUsingItem(hand);
        player.playSound(SoundEvents.SPYGLASS_USE, 1.0F, 1.0F);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && serverPlayer.getData(EchoAttachments.FIRST_PERSON.get())) {
            scanAndSend(level, serverPlayer, stack);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide || !(entity instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!serverPlayer.getData(EchoAttachments.FIRST_PERSON.get())) {
            return;   // 第三人称：只播放举起动画，不扫描
        }
        int usedTicks = USE_DURATION_TICKS - remainingUseDuration;
        int interval = ModConfig.Common.SCAN_INTERVAL_TICKS.get();
        if (usedTicks > 0 && usedTicks % interval == 0) {
            scanAndSend(level, serverPlayer, stack);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!level.isClientSide && entity instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, EchoRevealPayload.clear(level));
        }
        entity.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, EchoRevealPayload.clear(level));
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ItemStack filter = getFilterStack(stack);
        if (filter.isEmpty()) {
            tooltip.add(Component.translatable("item.create_crystal_industry.echo_spyglass.filter_tip_none")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("item.create_crystal_industry.echo_spyglass.filter_tip",
                            filter.getHoverName())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private void scanAndSend(Level level, ServerPlayer player, ItemStack heldStack) {
        if (level instanceof ServerLevel serverLevel) {
            ItemStack filter = getFilterStack(heldStack);
            List<BlockPos> found = EchoScanner.findOres(serverLevel, player.blockPosition(),
                    ModConfig.Common.SCAN_RADIUS.get(), filter);
            PacketDistributor.sendToPlayer(player, new EchoRevealPayload(serverLevel.dimension(), found));
            if (!found.isEmpty()) {
                // 真的透过障碍看见了东西——扫描一直没命中就不该发（空 filter 下扫到空气不算数）
                YunxianAdvancements.award(player, YunxianAdvancements.GEAR_REVEAL);
            }
        }
    }

    private static void openFilterMenu(ServerPlayer player, InteractionHand hand) {
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, p) -> new EchoSpyglassFilterMenu(containerId, inventory, hand),
                        Component.translatable("container.create_crystal_industry.echo_spyglass_filter")),
                buf -> buf.writeEnum(hand));
    }

    /** 是否可以作为虚拟过滤对象：任意方块物品，或机械动力过滤器 */
    public static boolean isGhostAllowed(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof BlockItem || isCreateFilter(stack));
    }

    /** 是否为机械动力过滤器（普通 Filter / 数据驱动属性过滤器） */
    public static boolean isCreateFilter(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof FilterItem) {
            return true;
        }
        return !FilterItemStack.of(stack).isEmpty();
    }

    /** 取出望远镜内保存的过滤物品（第一格）；没有则为 ItemStack.EMPTY */
    public static ItemStack getFilterStack(ItemStack spyglass) {
        ItemContainerContents contents = spyglass.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        return contents.stream().findFirst().orElse(ItemStack.EMPTY);
    }
}
package com.minecart.yunxian.item;

import com.minecart.yunxian.block.ResonanceTableBlock;
import com.minecart.yunxian.blockentity.ResonanceTableBlockEntity;
import com.minecart.yunxian.menu.ResonanceFilterMenu;
import com.minecart.yunxian.network.SetNetworkNamePayload;
import com.minecart.yunxian.registry.ModDataComponents;
import com.minecart.yunxian.resonance.ResonanceNetworkNames;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 共振过滤器：放进漏斗/溜槽/工作盆的过滤槽后，<b>过滤规则不来自它自己</b>，
 * 而是实时取自它所在的那个「共振台网络」里每张台面上放着的物品，取并集。
 * <p>
 * 接法见 {@link ResonanceFilterItemStack}。本类只负责四件事：
 * <ol>
 *   <li>{@link #makeStackWrapper} —— 把判定交给自定义的 {@code FilterItemStack} 子类
 *       （Create 的 {@code FilterItemStack.of} 会把判定分发到这里，这是唯一的接入口）；</li>
 *   <li>潜行右键共振台：让过滤器接入该台所在的网络（<b>组网本身</b>走
 *       {@code ResonanceTableItem#useOn}，和本类无关）；</li>
 *   <li>把网络 id 写进物品的 DataComponent，另外维护一份网络名字的副本供工具提示显示；</li>
 *   <li>非潜行右键打开 {@link ResonanceFilterMenu}（判定逻辑一概不经过那个界面）——
 *       没接入网络时打不开，只提示一句，因为界面里能显示的每一样东西都来自网络；</li>
 * </ol>
 * <b>网络 id 必须存在物品自己身上</b>：{@code FilteringBehaviour.read()} 是从 NBT 反序列化出的
 * ItemStack 重新 {@code FilterItemStack.of(...)} 构造判定对象的，方块实体那边不留任何状态，
 * 也正因为如此，蓝图复制、方块被拆掉落、物品栏搬运都会自动带着它走。
 * <p>
 * 措辞一律用 Create 物流网络那套现成的（{@code create.logistically_linked.*}），
 * 包括调频后的附魔光效和清除时的音效 —— <b>不自己造词</b>。频道的 UUID 不向玩家展示，
 * 名字倒是要展示（提示行里那一行），但它只是一份副本，正主见 {@link #refreshNetworkName}。
 */
public class ResonanceFilterItem extends FilterItem {

    public ResonanceFilterItem(Properties properties) {
        super(properties);
    }

    // ==================== 网络 ====================

    public static boolean isTuned(ItemStack stack) {
        return stack.get(ModDataComponents.RESONANCE_NETWORK.get()) != null;
    }

    /** 接入网络后带附魔光效，和 Create 仓储链接站一致 */
    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return isTuned(stack);
    }

    // ==================== FilterItem 的抽象方法 ====================

    @Override
    public FilterItemStack makeStackWrapper(ItemStack filter) {
        return new ResonanceFilterItemStack(filter);
    }

    @Override
    public DataComponentType<?> getComponentType() {
        return ModDataComponents.RESONANCE_NETWORK.get();
    }

    /**
     * 拿不到 Level，所以这里解析不出网络里的台子现在放着什么，只能返回空数组。
     * 唯一调用方是蓝图的需求覆盖层（{@code BlueprintOverlayRenderer}），
     * 表现为「蓝图里不列出共振过滤器当前镜像的物品」——可以接受。
     */
    @Override
    public ItemStack[] getFilterItems(ItemStack stack) {
        return new ItemStack[0];
    }

    /**
     * 非潜行右键打开的那个界面（界面本身见 {@code ResonanceFilterMenu}）。
     * 和 Create 的两种过滤器一样只是「手里这个物品的窗口」：过滤规则本身不在这里改，
     * 最终要在这里呈现的是网络当下的状态，规则仍只能在共振台上改。
     */
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return ResonanceFilterMenu.create(id, inv, player.getMainHandItem());
    }

    /**
     * 提示行：接入网络后先是 Create 那句「已接入物流网络」，再跟一行网络的名字（没起名字就不加）。
     * 未接入网络时只有一行，说明它此刻只过滤它自己 —— 这条规矩在过滤槽里是真的会生效的
     * （见 {@code ResonanceFilterItemStack#test}），不说玩家只能自己试出来。
     * <p>
     * 名字读的是物品自己身上那份副本（{@link ModDataComponents#RESONANCE_NETWORK_NAME}）：
     * 提示行在客户端画，客户端够不到服务端那张名字表。副本由服务端维护，见 {@link #inventoryTick}。
     */
    @Override
    public List<Component> makeSummary(ItemStack filter) {
        if (!isTuned(filter))
            return List.of(Component
                    .translatable("create_crystal_industry.resonance_filter.unlinked.summary")
                    .withStyle(ChatFormatting.GRAY));

        List<Component> summary = new ArrayList<>();
        summary.add(CreateLang.translateDirect("logistically_linked.tooltip")
                .withStyle(ChatFormatting.GOLD));

        String name = filter.get(ModDataComponents.RESONANCE_NETWORK_NAME.get());
        if (name != null && !name.isBlank())
            summary.add(Component.translatable("create_crystal_industry.resonance_filter.network_name.tooltip", name)
                    .withStyle(ChatFormatting.GRAY));
        return summary;
    }

    /**
     * 简介常驻，按住 Shift 不再收起。
     * <p>
     * Create 的 {@code FilterItem#appendHoverText} 第一句就是「按住 Shift 直接 return」
     * （{@code AllKeys.shiftDown()}），那是它给列表过滤器与属性过滤器定的习惯：悬停默认铺简介、
     * 按住 Shift 收起来。共振过滤器的简介只有一两行，说的又是「接没接入网络」这种一上手就该知道的事，
     * 收起来反而像没有提示。所以这里把 Create 那段排版照抄一遍，只去掉那次提前返回。
     * <p>
     * <b>不能调 {@code super}</b>：它按住 Shift 会提前返回，不按的时候又会把摘要加一份，
     * 这里再加一份就重复了。摘要为空时什么也不加，否则先空一行再铺摘要，
     * 与 Create 的排版（{@code CommonComponents.SPACE}）一致。
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        List<Component> summary = makeSummary(stack);
        if (summary.isEmpty())
            return;
        tooltip.add(CommonComponents.SPACE);
        tooltip.addAll(summary);
    }

    // ==================== 名字副本 ====================

    /**
     * 把网络名字的副本刷新到物品上：有名字就写上，没名字（或没接网络）就把副本摘掉。
     * <p>
     * <b>只在服务端调</b>：名字的正主在世界存档里。传进来的 {@code server} 为 null 时直接返回 ——
     * 那代表这是在客户端调，客户端手里那份副本是同步过来的，抹掉就等于把工具提示弄丢了。
     * <p>
     * 写之前先比一次，名字没变就一个字节都不动 —— 这个方法是每秒都会被调的，
     * 每 tick 都写一遍组件的话物品栏会一直往外发同步包。
     */
    public static void refreshNetworkName(ItemStack stack, @Nullable MinecraftServer server) {
        if (server == null)
            return;   // 客户端没有名字表：什么都不做，尤其不能把同步过来的副本抹掉

        UUID network = stack.get(ModDataComponents.RESONANCE_NETWORK.get());
        String name = network == null ? "" : ResonanceNetworkNames.get(server).getName(network);

        if (name.isBlank()) {
            if (stack.has(ModDataComponents.RESONANCE_NETWORK_NAME.get()))
                stack.remove(ModDataComponents.RESONANCE_NETWORK_NAME.get());
            return;
        }
        if (!name.equals(stack.get(ModDataComponents.RESONANCE_NETWORK_NAME.get())))
            stack.set(ModDataComponents.RESONANCE_NETWORK_NAME.get(), name);
    }

    /**
     * 玩家物品栏里的过滤器每秒把名字副本对一次答案，好让工具提示里的名字始终是新的
     * （原版磁石指针也是在 {@code inventoryTick} 里更新自己那份组件的）。
     * <p>
     * 只认服务端：客户端那份是同步过来的，在那边调只会把名字抹掉
     * （{@code level.getServer()} 在客户端恒为 null，正是这个意思）。
     * 一秒一次就够 —— 工具提示不是实时监视器，而每 tick 一次也没有意义。
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (level.isClientSide || entity.tickCount % 20 != 0 || !isTuned(stack))
            return;
        refreshNetworkName(stack, level.getServer());
    }

    // ==================== 交互 ====================

    /**
     * 潜行右键共振台 = 让过滤器<b>接入</b>这张台子所在的网络（只在过滤器还没接入任何网络时生效）。
     * <p>
     * <b>这里不负责组网</b>：让两张台子进同一个网络是手持「共振台」那件事
     * （见 {@code ResonanceTableItem#useOn}）。过滤器已经接入过网络时，对着别的网络里的台子
     * 点一下不会把它改过去 —— 什么也不做，因为 Create 那边没有"已经调过频"的措辞可用，
     * 与其自己造一句，不如不吭声；物品的附魔光效本身就说明它已经在网里了。
     * <p>
     * 能走到这里是因为原版在「潜行 + 手持物品」时会跳过方块的 {@code useItemOn}
     * （见 {@code Yunxian} 里 {@code CrystalBatteryInteractions} 那条注释），正好把
     * 共振台的普通右键让给「放/取台面上的物品」，接入只能用潜行——两边不打架。
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown())
            return InteractionResult.PASS;

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockState(pos).getBlock() instanceof ResonanceTableBlock))
            return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof ResonanceTableBlockEntity table))
            return InteractionResult.PASS;

        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        ItemStack stack = context.getItemInHand();
        if (isTuned(stack))
            return InteractionResult.SUCCESS;

        UUID joined = table.getNetwork();
        stack.set(ModDataComponents.RESONANCE_NETWORK.get(), joined);
        // 名字副本立刻写上，之后由 inventoryTick 每秒对答案
        refreshNetworkName(stack, level.getServer());
        // 措辞同 Create：接入一个已经存在的网络
        player.displayClientMessage(CreateLang.translateDirect("logistically_linked.connected"), true);
        return InteractionResult.SUCCESS;
    }

    /**
     * 非潜行右键主手 = 打开界面（<b>只限已接入网络的</b>），潜行右键空气 = 退出网络
     * （措辞与音效都照抄 Create 仓储链接站的清除动作）。
     * <p>
     * <b>开界面这一段不能交回 {@code super}</b>：{@code FilterItem#use} 只往数据流里写手里这叠物品，
     * 而网络名字存在世界存档里、客户端没有，只能在这一下一起带过去
     * （读法在 {@code ResonanceFilterMenu#createOnClient}，两边的顺序必须一致）。
     * 潜行与副手仍照 Create 的样子返回 {@code pass}。
     * <p>
     * <b>没接入网络时不开界面，只提示一句。</b> 那个窗口里要显示的每一样东西都来自网络：
     * 网络名、台面镜像的物品、以及四个开关实际影响的判定结果 —— 一份没接入网络的过滤器
     * 打开它只会是个空壳，开关动了也没有任何一次判定会读到。与其放一个改不动结果的界面进来，
     * 不如当场把缺的那件事说出来（提示用 {@code network_name.unlinked}，和界面标题里
     * "未接入网络"是同一句）。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!player.isShiftKeyDown() && hand == InteractionHand.MAIN_HAND) {
            // 没接入网络就没有可开的东西：界面上每一行都来自网络（见上面那段注释）。
            // 提示用屏幕标题里同一句"未接入网络"，不另造词。
            if (!isTuned(stack)) {
                if (!level.isClientSide)
                    player.displayClientMessage(
                            Component.translatable("create_crystal_industry.resonance_filter.network_name.unlinked"), true);
                return InteractionResultHolder.success(stack);
            }

            if (!level.isClientSide && player instanceof ServerPlayer)
                player.openMenu(this, buf -> {
                    ItemStack.STREAM_CODEC.encode(buf, stack);
                    buf.writeUtf(networkNameOf(level.getServer(), stack), SetNetworkNamePayload.MAX_LENGTH);
                });
            return InteractionResultHolder.success(stack);
        }

        if (hand != InteractionHand.MAIN_HAND || !isTuned(stack))
            return InteractionResultHolder.pass(stack);

        if (level.isClientSide) {
            level.playSound(player, player.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                    SoundSource.BLOCKS, 0.75f, 1.0f);
        } else {
            player.displayClientMessage(CreateLang.translateDirect("logistically_linked.cleared"), true);
            stack.remove(ModDataComponents.RESONANCE_NETWORK.get());
            refreshNetworkName(stack, level.getServer());   // 网络没了，名字副本一起摘掉
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * 开界面时随数据流带过去的网络名字。没接入网络、或者名字还没起过，都是空串
     * （界面那边空白即"没名字"）。只有服务端调得到 —— 名字由
     * {@link ResonanceNetworkNames} 按网络 id 记在世界存档里。
     */
    private static String networkNameOf(@Nullable MinecraftServer server, ItemStack stack) {
        UUID network = stack.get(ModDataComponents.RESONANCE_NETWORK.get());
        if (server == null || network == null)
            return "";
        return ResonanceNetworkNames.get(server).getName(network);
    }
}

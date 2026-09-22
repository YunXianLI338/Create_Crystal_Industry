package com.minecart.yunxian.item;

import com.minecart.yunxian.block.CrystalBatteryBlock;
import com.minecart.yunxian.blockentity.CrystalBatteryBlockEntity;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.equipment.symmetryWand.SymmetryWandItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 水晶电池的方块物品：在已有的 2x2 / 3x3 电池上再放一层时，自动把整层补齐，
 * 与机械动力的流体储罐（{@code FluidTankItem#tryMultiPlace}）完全同一套做法。
 * <p>
 * <b>潜行右键换晶体不在这里</b>：那个交互手里拿的是任意晶体方块（不一定是本物品），
 * 而且原版潜行时会跳过方块的 useItemOn、把控制权交给手持物品——所以它走的是
 * {@link com.minecart.yunxian.battery.CrystalBatteryInteractions} 里的
 * {@code UseItemOnBlockEvent} 钩子，与手持物品是什么无关。
 * <p>
 * 整层放置的触发条件很窄，不满足就退化成普通单块放置：
 * <ul>
 *   <li>玩家没在潜行，且手上不是对称手杖（手杖会镜像放方块，两边同时铺会打架）；</li>
 *   <li>贴在已有电池的<b>顶面或底面</b>朝上/下放（侧面贴不算，"层"是水平的）；</li>
 *   <li>贴的那一块所属结构的<b>宽度大于 1</b>——宽 1 的柱子本来就是一层一个，没有整层可补；</li>
 *   <li>新放的那一块所在的那一层，要么已经是电池，要么是能被替换掉的方块（有一格被别的东西占了就整层放弃，不做半吊子）；</li>
 *   <li>非创造模式下手上数量够补完这一层。</li>
 * </ul>
 * 整层放置期间会给玩家挂一个标记，让这一层只响一声（见
 * {@link CrystalBatteryBlock#getSoundType}）。
 */
public class CrystalBatteryItem extends BlockItem {

    /** 整层放置期间挂在玩家身上的标记键：方块据此把每块的放置音效静音 */
    public static final String SILENCE_BATCH_PLACEMENT = "SilenceBatteryPlacement";

    public CrystalBatteryItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult initialResult = super.place(context);
        if (!initialResult.consumesAction()) {
            return initialResult;
        }
        // 第一块已经放下去了，这里只负责把同一层剩下的补齐
        placeRemainingLayer(context);
        return initialResult;
    }

    private void placeRemainingLayer(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return;
        }
        Direction face = context.getClickedFace();
        if (!face.getAxis().isVertical()) {
            return;
        }
        if (SymmetryWandItem.presentInHotbar(player)) {
            return;
        }

        Level level = context.getLevel();
        BlockPos placedPos = context.getClickedPos();
        BlockPos clickedPos = placedPos.relative(face.getOpposite());
        if (!CrystalBatteryBlock.isBattery(level.getBlockState(clickedPos))) {
            return;
        }

        CrystalBatteryBlockEntity clickedBattery = ConnectivityHandler.partAt(
                ModBlockEntities.CRYSTAL_BATTERY.get(), level, clickedPos);
        if (clickedBattery == null) {
            return;
        }
        CrystalBatteryBlockEntity controller = clickedBattery.getControllerBE();
        if (controller == null) {
            return;
        }
        int width = controller.getWidth();
        if (width == 1) {
            return;
        }

        // 要补的那一层：朝下贴就是结构底面之下一层，朝上贴就是顶面之上一层
        BlockPos layerOrigin = face == Direction.DOWN
                ? controller.getBlockPos().below()
                : controller.getBlockPos().above(controller.getHeight());
        // 刚刚那一块必须正好落在这一层上，否则说明是贴着结构的侧面在别处放，不补
        if (layerOrigin.getY() != placedPos.getY()) {
            return;
        }

        int toPlace = 0;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                BlockState state = level.getBlockState(layerOrigin.offset(x, 0, z));
                if (CrystalBatteryBlock.isBattery(state)) {
                    continue;
                }
                if (!state.canBeReplaced()) {
                    return;
                }
                toPlace++;
            }
        }
        if (!player.isCreative() && context.getItemInHand().getCount() < toPlace) {
            return;
        }

        for (int x = 0; x < width; x++) {
            for (int z = 0; z < width; z++) {
                BlockPos target = layerOrigin.offset(x, 0, z);
                if (CrystalBatteryBlock.isBattery(level.getBlockState(target))) {
                    continue;
                }
                player.getPersistentData().putBoolean(SILENCE_BATCH_PLACEMENT, true);
                super.place(BlockPlaceContext.at(context, target, face));
                player.getPersistentData().remove(SILENCE_BATCH_PLACEMENT);
            }
        }
    }
}

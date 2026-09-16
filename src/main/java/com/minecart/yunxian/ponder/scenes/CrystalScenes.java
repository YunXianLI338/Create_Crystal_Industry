package com.minecart.yunxian.ponder.scenes;

import com.minecart.yunxian.block.AcceleratorBlock;
import com.minecart.yunxian.block.MechanicalAcceleratorBlock;

import com.minecart.yunxian.budding.BuddingFamilies;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.Block;

public class CrystalScenes {

    /*
     * ============ 母岩通用分镜 ============
     * 蓝图 budding/accelerated_growth.nbt：
     *   - 5x5 基座
     *   - (2,1,2)  原版紫水晶母岩
     *   - (2,2,2)  空气（生长格）
     *   - (1,1,2)  动力催生器（代码会强制朝向 EAST，背面即西侧）
     *   - (0,1,2)  机械动力的传动杆（轴沿 X）
     *   - (3,1,2)  电力催生器
     */
    public static void buddingGrowth(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("budding_growth", "Growing Budding Blocks with Accelerators");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos budding = util.grid().at(2, 1, 2);
        BlockPos spot = util.grid().at(2, 2, 2);
        BlockPos mech = util.grid().at(1, 1, 2);
        BlockPos shaft = util.grid().at(0, 1, 2);
        BlockPos electric = util.grid().at(3, 1, 2);

        // 连同生长格 (2,2,2) 一起纳入显示区域，保证后续 setBlock 的芽/簇一定可见
        scene.world().showSection(util.select().fromTo(budding, spot), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(130)
                .attachKeyFrame()
                .text("Vanilla budding blocks only grow when random ticks land on them. Each block receives one every ~68 seconds on average, and each roll only has a 1-in-5 chance to advance a growth stage.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(budding));
        scene.idle(140);

        scene.world().showSection(util.select().position(mech), Direction.SOUTH);
        scene.world().showSection(util.select().position(shaft), Direction.EAST);
        scene.world().showSection(util.select().position(electric), Direction.NORTH);
        scene.world().modifyBlock(mech, s -> s.setValue(MechanicalAcceleratorBlock.FACING, Direction.EAST), false);
        scene.idle(10);

        scene.overlay().showText(120)
                .attachKeyFrame()
                .text("Crystal Industry's Accelerators change this. The Electric Accelerator on the left runs on FE; the Mechanical Accelerator on the right runs on Rotational Force.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(electric));
        scene.idle(130);

        // 两个催生器通电；动力催生器获得动力后，背后的传动杆开始转动
        scene.world().cycleBlockProperty(electric, AcceleratorBlock.POWERED);
        scene.world().cycleBlockProperty(mech, MechanicalAcceleratorBlock.POWERED);
        scene.world().modifyBlockEntityNBT(util.select().position(shaft), KineticBlockEntity.class,
                nbt -> nbt.putFloat("Speed", 32f));
        scene.effects().indicateSuccess(electric);
        scene.effects().indicateSuccess(mech);
        scene.idle(10);

        scene.overlay().showText(110)
                .attachKeyFrame()
                .text("Each tick, an Accelerator forces one random tick onto every adjacent block. Growth stages that used to take minutes now take moments.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(budding));

        // 通电后立即开始生长：小芽 -> 中芽 -> 大芽 -> 晶簇
        scene.idle(20);
        growUp(scene, util, spot);
        scene.idle(70);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("In a few seconds a fully-grown Cluster appears. Farming crystals around Accelerators becomes a fast, highly efficient way to gather them.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(spot));
        scene.idle(110);

        // 再来一轮，体现可重复生产的吞吐量
        scene.world().restoreBlocks(util.select().position(spot));
        scene.idle(5);
        growUp(scene, util, spot);
        scene.idle(20);

        scene.overlay().showText(110)
                .text("Some budding blocks in this mod have extra conditions - Echo needs darkness, Flammable Ice needs water, Fluix needs AE power - but the growth itself is always driven by the same random ticks.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(budding));
        scene.idle(120);
    }

    /*
     * ============ 电力催生器分镜 ============
     * 蓝图 accelerator/electric.nbt：
     *   - (2,1,2)         电力催生器（中心）
     *   - (2,1,1)/(2,2,1) 粗铁母岩 / 小型粗铁芽（朝上）
     *   - (2,1,3)/(2,2,3) 玫瑰石英母岩 / 小型玫瑰石英芽（朝上）
     *   - (3,1,2)/(3,2,2) 原版紫水晶母岩 / 小型紫水晶芽（朝上）
     *   - (1,1,2)         小麦 age 0（基座 (1,0,2) 为耕地）
     */
    public static void electricAccelerator(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("accelerator_electric", "Using the Electric Accelerator");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos acc = util.grid().at(2, 1, 2);
        BlockPos ironBudding = util.grid().at(2, 1, 1);
        BlockPos ironBud = util.grid().at(2, 2, 1);
        BlockPos roseBudding = util.grid().at(2, 1, 3);
        BlockPos roseSpot = util.grid().at(2, 2, 3);
        BlockPos amethyst = util.grid().at(3, 1, 2);
        BlockPos amethystSpot = util.grid().at(3, 2, 2);
        BlockPos wheat = util.grid().at(1, 1, 2);

        scene.world().showSection(util.select().position(acc), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("Accelerators come in two types: this Electric Accelerator runs on FE, while the Mechanical Accelerator runs on Rotational Force.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(acc));
        scene.idle(110);

        // 依次展示围绕催生器的四种随机刻目标；芽已在蓝图里，与母岩一同淡入
        scene.world().showSection(util.select().fromTo(ironBudding, ironBud), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().fromTo(roseBudding, roseSpot), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().fromTo(amethyst, amethystSpot), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(wheat), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Supply it with FE and it activates. Every tick, it forces a random tick onto each of the six neighbouring blocks.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(acc));
        scene.idle(20);

        scene.world().cycleBlockProperty(acc, AcceleratorBlock.POWERED);
        scene.effects().indicateSuccess(acc);
        scene.idle(10);

        // 通电后，三块母岩上的小芽与小麦同时进阶（小芽→中芽）
        scene.world().setBlock(wheat, wheatState(2), false);
        scene.world().setBlock(ironBud, budState(BuddingFamilies.RAW_IRON.mediumBud().get(), Direction.UP), false);
        scene.world().setBlock(roseSpot, budState(BuddingFamilies.ROSE_QUARTZ.mediumBud().get(), Direction.UP), false);
        scene.world().setBlock(amethystSpot, budState(Blocks.MEDIUM_AMETHYST_BUD, Direction.UP), false);
        scene.idle(5);

        scene.world().setBlock(wheat, wheatState(5), false);
        scene.world().setBlock(ironBud, budState(BuddingFamilies.RAW_IRON.largeBud().get(), Direction.UP), false);
        scene.world().setBlock(roseSpot, budState(BuddingFamilies.ROSE_QUARTZ.largeBud().get(), Direction.UP), false);
        scene.world().setBlock(amethystSpot, budState(Blocks.LARGE_AMETHYST_BUD, Direction.UP), false);
        scene.idle(5);

        scene.world().setBlock(wheat, wheatState(7), false);
        scene.world().setBlock(ironBud, budState(BuddingFamilies.RAW_IRON.cluster().get(), Direction.UP), false);
        scene.world().setBlock(roseSpot, budState(BuddingFamilies.ROSE_QUARTZ.cluster().get(), Direction.UP), false);
        scene.world().setBlock(amethystSpot, budState(Blocks.AMETHYST_CLUSTER, Direction.UP), false);
        scene.idle(50);

        scene.overlay().showText(110)
                .attachKeyFrame()
                .text("Random ticks drive far more than crystal growth: crops, saplings, stems - anything random-tick based will be accelerated, not only budding blocks.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(wheat));
        scene.idle(120);

        scene.overlay().showOutlineWithText(util.select()
                        .position(ironBudding)
                        .add(util.select().position(roseBudding))
                        .add(util.select().position(amethyst)), 110)
                .text("And because all six sides are ticked at once, a single Accelerator can serve several budding blocks surrounding it.")
                .placeNearTarget();
        scene.idle(120);
    }

    /*
     * ============ 动力催生器分镜 ============
     * 蓝图 accelerator/mechanical.nbt：
     *   - (2,1,2)         动力催生器（正面朝北，背面朝南）
     *   - (2,1,1)/(2,2,1) 粗金母岩 / 向上生长位
     *   - (3,1,2)/(3,2,2) 原版紫水晶母岩 / 向上生长位
     *   - (1,1,2)         小麦 age 0（基座 (1,0,2) 为耕地）
     *   - (2,1,3)         传动杆（轴沿 Z，接催生器背面）
     *   - (2,1,4)         小齿轮（与大齿轮斜角啮合）
     *   - (3,2,4)         大齿轮（动力输入）
     */
    public static void mechanicalAccelerator(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("accelerator_mechanical", "Using the Mechanical Accelerator");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos acc = util.grid().at(2, 1, 2);
        BlockPos goldBudding = util.grid().at(2, 1, 1);
        BlockPos goldSpot = util.grid().at(2, 2, 1);
        BlockPos amethyst = util.grid().at(3, 1, 2);
        BlockPos amethystSpot = util.grid().at(3, 2, 2);
        BlockPos wheat = util.grid().at(1, 1, 2);
        BlockPos shaft = util.grid().at(2, 1, 3);
        BlockPos cog = util.grid().at(2, 1, 4);
        BlockPos largeCog = util.grid().at(3, 2, 4);

        scene.world().showSection(util.select().position(acc), Direction.DOWN);
        scene.world().modifyBlock(acc, s -> s.setValue(MechanicalAcceleratorBlock.FACING, Direction.NORTH), false);
        scene.idle(10);

        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("Accelerators come in two types: this Mechanical Accelerator runs on Rotational Force, while the Electric Accelerator runs on FE.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(acc));
        scene.idle(105);

        // 背面的动力输入链：传动杆 → 小齿轮 → 大齿轮
        scene.world().showSection(util.select().position(shaft), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(cog), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(largeCog), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(105)
                .attachKeyFrame()
                .text("Supply Rotational Force at its back - through any combination of shafts and cogs. The faster the input spins, the more random ticks it applies each second.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(largeCog));
        scene.idle(115);

        // 三个催生目标：两块母岩（各自带向上生长位）与小麦
        scene.world().showSection(util.select().fromTo(goldBudding, goldSpot), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().fromTo(amethyst, amethystSpot), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(wheat), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("Every tick, it forces a random tick onto each of the six neighbouring blocks - here, two budding blocks and an ordinary wheat crop.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(acc));
        scene.idle(105);

        // 通电：低转速运行（齿轮链缓慢转动）
        scene.world().cycleBlockProperty(acc, MechanicalAcceleratorBlock.POWERED);
        scene.world().modifyBlockEntityNBT(util.select().fromTo(shaft, cog), KineticBlockEntity.class,
                nbt -> nbt.putFloat("Speed", -16f));
        scene.world().modifyBlockEntityNBT(util.select().position(largeCog), KineticBlockEntity.class,
                nbt -> nbt.putFloat("Speed", 8f));
        scene.effects().indicateSuccess(acc);
        scene.idle(10);

        // 低转速演示：两块母岩各自只长出第一颗小芽（均在顶面）
        scene.world().setBlock(amethystSpot, budState(Blocks.SMALL_AMETHYST_BUD, Direction.UP), false);
        scene.world().setBlock(goldSpot, budState(BuddingFamilies.RAW_GOLD.smallBud().get(), Direction.UP), false);
        scene.idle(40);

        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("Running at low speed, each stage still takes a while - so far only the first advancement has happened.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(amethystSpot));
        scene.idle(105);

        // 提速：把输入转速调高
        scene.world().modifyBlockEntityNBT(util.select().fromTo(shaft, cog), KineticBlockEntity.class,
                nbt -> nbt.putFloat("Speed", -64f));
        scene.world().modifyBlockEntityNBT(util.select().position(largeCog), KineticBlockEntity.class,
                nbt -> nbt.putFloat("Speed", 32f));
        scene.effects().indicateSuccess(largeCog);
        scene.idle(5);

        // 快速完成：紫水晶/粗金 → 中/大/簇；小麦 → age 2/5/7
        scene.world().setBlock(amethystSpot, budState(Blocks.MEDIUM_AMETHYST_BUD, Direction.UP), false);
        scene.world().setBlock(goldSpot, budState(BuddingFamilies.RAW_GOLD.mediumBud().get(), Direction.UP), false);
        scene.world().setBlock(wheat, wheatState(2), false);
        scene.idle(4);
        scene.world().setBlock(amethystSpot, budState(Blocks.LARGE_AMETHYST_BUD, Direction.UP), false);
        scene.world().setBlock(goldSpot, budState(BuddingFamilies.RAW_GOLD.largeBud().get(), Direction.UP), false);
        scene.world().setBlock(wheat, wheatState(5), false);
        scene.idle(4);
        scene.world().setBlock(amethystSpot, budState(Blocks.AMETHYST_CLUSTER, Direction.UP), false);
        scene.world().setBlock(goldSpot, budState(BuddingFamilies.RAW_GOLD.cluster().get(), Direction.UP), false);
        scene.world().setBlock(wheat, wheatState(7), false);
        scene.effects().indicateSuccess(amethystSpot);
        scene.effects().indicateSuccess(goldSpot);
        scene.idle(20);

        scene.overlay().showText(110)
                .attachKeyFrame()
                .text("Raise the input speed and the same stages complete in moments. It accelerates anything driven by random ticks - not only budding blocks, but crops and more.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(wheat));
        scene.idle(120);

        // 收尾：六面同时
        scene.overlay().showOutlineWithText(util.select()
                        .position(goldBudding)
                        .add(util.select().position(amethyst)), 110)
                .text("With all six sides ticked at once, one Accelerator can serve several budding blocks surrounding it.")
                .placeNearTarget();
        scene.idle(120);
    }

    // ---- 工具方法 ----

    private static BlockState budState(Block block) {
        return budState(block, Direction.UP);
    }

    private static BlockState budState(Block block, Direction facing) {
        return block.defaultBlockState().setValue(AmethystClusterBlock.FACING, facing);
    }

    private static BlockState wheatState(int age) {
        return Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, age);
    }

    private static void growUp(SceneBuilder scene, SceneBuildingUtil util, BlockPos spot) {
        scene.world().setBlock(spot, budState(Blocks.SMALL_AMETHYST_BUD), false);
        scene.idle(4);
        scene.world().setBlock(spot, budState(Blocks.MEDIUM_AMETHYST_BUD), false);
        scene.idle(4);
        scene.world().setBlock(spot, budState(Blocks.LARGE_AMETHYST_BUD), false);
        scene.idle(4);
        scene.world().setBlock(spot, budState(Blocks.AMETHYST_CLUSTER), false);
        scene.idle(10);
    }

}
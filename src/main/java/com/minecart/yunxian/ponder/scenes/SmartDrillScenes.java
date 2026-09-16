package com.minecart.yunxian.ponder.scenes;

import com.minecart.yunxian.budding.BuddingFamilies;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SmartDrillScenes {

    /*
     * ============ 智能钻头分镜 ============
     * 蓝图 smart_drill/smart_drill.nbt：
     *   - (1,1,2)  智能钻头（朝北），背面 (1,1,3) 链式传动箱
     *   - (3,1,2)  普通机械钻头（朝北），背面 (3,1,3) 链式传动箱 → (3,1,4) 小齿轮 → (2,2,4) 大齿轮
     *   - (1,0,3)/(2,0,3)/(3,0,3) 底层取力回路（链式传动箱-包壳轴-链式传动箱），随基座层 (layer 0) 显示
     *   - 挖掘演示方块（石头/母岩）由脚本放置在钻头正前方 (1,1,1)/(3,1,1)
     *
     * 出场顺序：
     *   基座层 → 全部链式传动箱与齿轮先就位 → 智能钻头单独出场 → 普通钻头单独出场
     *
     * 说明：
     *  1. 模式切换与挖掘节奏均为脚本演绎（不修改方块实体数据）；
     *  2. 精准采集的产出用 createItemEntity 模拟；
     *  3. 演示位（空气格）已并入 showSection，运行时 setBlock 的方块才会显示；
     *  4. 方块碎裂用 destroyBlock + 一簇 BLOCK 粒子代替裂纹动画。
     */
    public static void smartDrill(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("smart_drill", "Using the Smart Drill");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos smart = util.grid().at(1, 1, 2);
        BlockPos smartBack = util.grid().at(1, 1, 3);
        BlockPos vanilla = util.grid().at(3, 1, 2);
        BlockPos vanillaBack = util.grid().at(3, 1, 3);
        BlockPos cog = util.grid().at(3, 1, 4);
        BlockPos largeCog = util.grid().at(2, 2, 4);

        BlockPos spotSmart = util.grid().at(1, 1, 1);
        BlockPos spotVanilla = util.grid().at(3, 1, 1);

        // 0) 传动部分先全部就位：两个链式传动箱同拍出现，随后小齿轮、大齿轮
        //    （底层两个链式传动箱已随基座层出现）
        scene.world().showSection(util.select()
                .position(smartBack)
                .add(util.select().position(vanillaBack)), Direction.NORTH);
        scene.idle(4);
        scene.world().showSection(util.select().position(cog), Direction.NORTH);
        scene.idle(4);
        scene.world().showSection(util.select().position(largeCog), Direction.DOWN);
        scene.idle(8);

        // 1) 智能钻头单独出场（其前方演示位一并纳入）
        scene.world().showSection(util.select().fromTo(spotSmart, smart), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("This is the Smart Drill: a Mechanical Drill with two harvesting modes - Normal Harvesting and Silk Touch Harvesting.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(smart));
        scene.idle(105);

        // 2) 对照的普通机械钻头单独出场
        scene.world().showSection(util.select().fromTo(spotVanilla, vanilla), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showOutlineWithText(util.select()
                        .position(smart)
                        .add(util.select().position(vanilla)), 105)
                .text("For comparison: a regular Mechanical Drill on the left. Both drills take Rotational Force at the back and mine the block in front of the head.")
                .placeNearTarget();
        scene.idle(115);

        // 3) 供电：整机转动（大齿轮为动力输入端 16 rpm；小齿轮/钻头按啮合与链传动取值）
        scene.world().modifyBlockEntityNBT(util.select().position(largeCog), KineticBlockEntity.class,
                nbt -> nbt.putFloat("Speed", 16f));
        scene.world().modifyBlockEntityNBT(util.select()
                        .position(cog)
                        .add(util.select().position(vanillaBack))
                        .add(util.select().position(vanilla))
                        .add(util.select().position(smartBack))
                        .add(util.select().position(smart))
                        .add(util.select().fromTo(util.grid().at(1, 0, 3), util.grid().at(3, 0, 3))),
                KineticBlockEntity.class, nbt -> nbt.putFloat("Speed", -32f));
        scene.effects().indicateSuccess(smart);
        scene.effects().indicateSuccess(vanilla);
        scene.idle(10);

        // 4) 普通采集：速度是普通钻头的两倍（同刻开挖，智能钻头先挖穿）
        scene.world().setBlock(spotSmart, Blocks.STONE.defaultBlockState(), false);
        scene.world().setBlock(spotVanilla, Blocks.STONE.defaultBlockState(), false);
        scene.idle(10);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("In Normal Harvesting, the Smart Drill mines twice as fast as a regular drill.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(smart));
        scene.idle(20);
        destroyWithDebris(scene, util, spotSmart, Blocks.STONE.defaultBlockState());
        scene.idle(20);
        destroyWithDebris(scene, util, spotVanilla, Blocks.STONE.defaultBlockState());
        scene.idle(75);

        // 5) 切换精准采集：速度与普通钻头相同（两块石头同时挖穿）
        scene.world().setBlock(spotSmart, Blocks.STONE.defaultBlockState(), false);
        scene.world().setBlock(spotVanilla, Blocks.STONE.defaultBlockState(), false);
        scene.idle(5);

        scene.overlay().showText(120)
                .attachKeyFrame()
                .text("Switch modes at any time through the setting slot on its side. In Silk Touch Harvesting, the mining speed matches a regular drill - but blocks drop as if mined with Silk Touch.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(smart));
        // 模式槽位指示：朝向为北时，四个垂直于轴向的面（上/下/东/西）均可配置；这里指向面向默认镜头的西面槽位。
        // 坐标取自 SmartDrillValueBoxTransform：西面槽位本地偏移 (0.03125, 0.5, 0.3125) + 方块 (1,1,2)
        scene.overlay().showFilterSlotInput(new Vec3(1.03125, 1.5, 2.6875), 80);
        scene.idle(30);
        destroyWithDebris(scene, util, spotSmart, Blocks.STONE.defaultBlockState());
        destroyWithDebris(scene, util, spotVanilla, Blocks.STONE.defaultBlockState());
        scene.idle(105);

        // 6) 母岩：普通钻头打碎后无法采集
        scene.world().setBlock(spotSmart, BuddingFamilies.ROSE_QUARTZ.budding().get().defaultBlockState(), false);
        scene.world().setBlock(spotVanilla, BuddingFamilies.ROSE_QUARTZ.budding().get().defaultBlockState(), false);
        scene.idle(10);

        scene.overlay().showText(110)
                .attachKeyFrame()
                .text("Budding Blocks are a special case: a regular drill - or the Smart Drill in Normal Harvesting - shatters them, and the block itself cannot be collected.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(spotVanilla));
        scene.idle(20);
        destroyWithDebris(scene, util, spotVanilla, BuddingFamilies.ROSE_QUARTZ.budding().get().defaultBlockState());
        scene.idle(105);

        // 7) 母岩：精准采集完整采下（掉落物留在场景中）
        scene.overlay().showText(110)
                .attachKeyFrame()
                .text("In Silk Touch Harvesting, the Smart Drill collects the Budding Block itself - one of the ways Crystal Industry lets you obtain budding blocks.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(spotSmart));
        scene.idle(10);
        destroyWithDebris(scene, util, spotSmart, BuddingFamilies.ROSE_QUARTZ.budding().get().defaultBlockState());
        scene.world().createItemEntity(util.vector().centerOf(spotSmart), Vec3.ZERO,
                new ItemStack(BuddingFamilies.ROSE_QUARTZ.budding().get().asItem()));
        scene.effects().indicateSuccess(spotSmart);
        scene.idle(120);
    }

    /**
     * 破坏方块并喷出一小簇该方块的碎屑粒子（替代原版挖掘裂纹动画的视觉演出）。
     * 观感调整点：emitParticles 末尾的 8、2 = 每次每刻粒子数、持续刻数。
     */
    private static void destroyWithDebris(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, BlockState state) {
        scene.world().destroyBlock(pos);
        scene.effects().emitParticles(
                util.vector().centerOf(pos),
                scene.effects().particleEmitterWithinBlockSpace(
                        new BlockParticleOption(ParticleTypes.BLOCK, state),
                        Vec3.ZERO),
                8, 2);
    }
}
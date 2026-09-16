package com.minecart.yunxian.ponder.scenes;

import com.minecart.yunxian.blockentity.MechanicalCleanerBlockEntity;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class MechanicalCleanerScenes {

    /*
     * ============ 动力吸尘器分镜 ============
     * 蓝图 mechanical_cleaner/mechanical_cleaner.nbt：
     *   - (2,1,3)          动力吸尘器（朝北，正面 = 北）
     *   - (2,1,1)/(2,1,2)  双联箱子（位于正前方；开场隐藏，容器交互段落再出场）
     *   - (2,1,4)          小齿轮（轴 Z，接吸尘器背面）
     *   - (4,1,4)          小齿轮（轴 Z）
     *   - (3,2,4)          大齿轮（轴 Z，动力输入）
     *
     * 演出说明：
     *  1. 气流、吸取与容器直取直送均为脚本演绎；物品飞行轨迹是近似值，
     *     可调参数集中在每段 createItemEntity 的坐标/速度与随后的 idle 上；
     *  2. 吹/吸模式不做 NBT 翻转（缺 MechanicalCleanerFilterBehaviour 序列化细节），
     *     只以文字与槽位图标提示，视觉上直接演绎两个流向；
     *  3. 开场隐藏箱子：先演对掉落物的吸取，再让箱子出场演容器交互。
     */
    public static void mechanicalCleaner(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("mechanical_cleaner", "Using the Mechanical Cleaner");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos cleaner = util.grid().at(2, 1, 3);
        BlockPos smallCogA = util.grid().at(2, 1, 4);
        BlockPos smallCogB = util.grid().at(4, 1, 4);
        BlockPos largeCog = util.grid().at(3, 2, 4);

        Selection chests = util.select().fromTo(util.grid().at(2, 1, 1), util.grid().at(2, 1, 2));

        // 除箱子外的全部机械部件一起淡入（整体选择可覆盖大齿轮的全部构成格）
        scene.world().showSection(util.select().layersFrom(1).substract(chests), Direction.DOWN);
        scene.idle(15);

        // 供电：大齿轮（动力输入）16 rpm；两个小齿轮与其啮合取 -32；吸尘器由背面小齿轮同轴驱动，同样 -32
        scene.world().modifyBlockEntityNBT(util.select().position(largeCog), KineticBlockEntity.class,
                nbt -> nbt.putFloat("Speed", 16f));
        scene.world().modifyBlockEntityNBT(util.select()
                        .position(smallCogA)
                        .add(util.select().position(smallCogB)),
                KineticBlockEntity.class, nbt -> nbt.putFloat("Speed", -32f));
        scene.world().modifyBlockEntityNBT(util.select().position(cleaner),
                MechanicalCleanerBlockEntity.class, nbt -> nbt.putFloat("Speed", -32f));
        scene.effects().indicateSuccess(cleaner);
        scene.idle(15);

        // 1) 鼓风机能力提示（一句话，不展开演示）
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("The Mechanical Cleaner drives the same airstream as an Encased Fan - in short, every airflow ability of the fan applies here as well.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(cleaner));
        scene.idle(110);

        // 2) 吸取掉落物
        scene.overlay().showText(110)
                .attachKeyFrame()
                .text("On top of that, it is also a collector: items caught in its stream - or simply lying in front of it - are sucked in and stored in its built-in inventory.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(cleaner));
        scene.idle(20);

        // 风向粒子（纯视觉，可换粒子类型/数量）
        scene.effects().emitParticles(util.vector().centerOf(2, 1, 1),
                scene.effects().particleEmitterWithinBlockSpace(ParticleTypes.CLOUD, new Vec3(0, 0.01, 0.12)),
                2, 40);

        // 两件物品一前一后画抛物线飞入机器前脸（轨迹为近似值，可调）
        ElementLink<EntityElement> suckA = scene.world().createItemEntity(
                new Vec3(2.5, 1.2, 1.1), new Vec3(0, 0.22, 0.19),
                new ItemStack(BuddingFamilies.ROSE_QUARTZ.cluster().get().asItem()));
        scene.idle(4);
        ElementLink<EntityElement> suckB = scene.world().createItemEntity(
                new Vec3(2.5, 1.2, 1.1), new Vec3(0, 0.22, 0.19),
                new ItemStack(BuddingFamilies.ROSE_QUARTZ.cluster().get().asItem()));
        scene.idle(8);
        scene.world().modifyEntity(suckA, Entity::discard);
        scene.effects().indicateSuccess(cleaner);
        scene.idle(4);
        scene.world().modifyEntity(suckB, Entity::discard);
        scene.idle(84);

        // 3) 容器交互：箱子出场
        scene.world().showSection(chests, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(110)
                .attachKeyFrame()
                .text("A container placed directly in front is used in a special way: the Cleaner moves items in and out of it directly - nothing is dropped into the world.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(2, 1, 2));
        scene.idle(120);

        // 4) 容器 -> 吸尘器（直接抽取，不产生掉落物）
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("While collecting, it can reach into that container and pull matching items straight into its own inventory.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(2, 1, 2));
        scene.idle(20);
        ElementLink<EntityElement> pullA = scene.world().createItemEntity(
                new Vec3(2.5, 1.5, 2.98), new Vec3(0, 0.02, 0.055),
                new ItemStack(BuddingFamilies.ROSE_QUARTZ.cluster().get().asItem()));
        scene.idle(4);
        ElementLink<EntityElement> pullB = scene.world().createItemEntity(
                new Vec3(2.5, 1.5, 2.98), new Vec3(0, 0.02, 0.055),
                new ItemStack(BuddingFamilies.ROSE_QUARTZ.cluster().get().asItem()));
        scene.idle(6);
        scene.world().modifyEntity(pullA, Entity::discard);
        scene.idle(4);
        scene.world().modifyEntity(pullB, Entity::discard);
        scene.idle(76);

        // 5) 吸尘器 -> 容器（直接送回，不洒落）
        scene.overlay().showText(110)
                .attachKeyFrame()
                .text("While blowing, stored items are fed back into the container in front - again, without spilling anything into the world.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(2, 1, 2));
        scene.idle(15);
        ElementLink<EntityElement> pushA = scene.world().createItemEntity(
                new Vec3(2.5, 1.5, 3.15), new Vec3(0, 0.02, -0.055),
                new ItemStack(BuddingFamilies.ROSE_QUARTZ.cluster().get().asItem()));
        scene.idle(5);
        ElementLink<EntityElement> pushB = scene.world().createItemEntity(
                new Vec3(2.5, 1.5, 3.15), new Vec3(0, 0.02, -0.055),
                new ItemStack(BuddingFamilies.ROSE_QUARTZ.cluster().get().asItem()));
        scene.idle(8);
        scene.world().modifyEntity(pushA, Entity::discard);
        scene.idle(5);
        scene.world().modifyEntity(pushB, Entity::discard);
        scene.idle(90);

        // 6) 收尾：模式 / 过滤 / 数量在侧面配置槽与界面里
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("Blowing or sucking, filters, and transfer amounts are all configured through its interface and the side slot.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(cleaner));
        scene.overlay().showCenteredScrollInput(cleaner, Direction.WEST, 80);
        scene.idle(110);
    }
}
package com.minecart.yunxian.worldgen;

import com.mojang.serialization.Codec;
import com.minecart.yunxian.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Optional;

public class FlammableIceFeature extends Feature<NoneFeatureConfiguration> {
    public static final ResourceLocation TEMPLATE_ID =
            ResourceLocation.fromNamespaceAndPath("create_crystal_industry", "flammable_ice");

    public FlammableIceFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (!ModConfig.Common.enabled("flammable_ice")) {
            return false;
        }
        int chance = ModConfig.Common.FLAMMABLE_ICE_CHANCE.get();
        if (chance > 1 && context.random().nextInt(chance) != 0) {
            return false;
        }

        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        StructureTemplateManager templateManager =
                ((ServerLevel) level.getLevel()).getStructureManager();
        Optional<StructureTemplate> templateOpt = templateManager.get(TEMPLATE_ID);
        if (templateOpt.isEmpty()) {
            return false;
        }
        StructureTemplate template = templateOpt.get();
        int sizeX = template.getSize().getX();
        int sizeY = template.getSize().getY();
        int sizeZ = template.getSize().getZ();

        int floorY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX(), origin.getZ());

        int originX = origin.getX() - sizeX / 2;
        int originY = floorY - sizeY + 1;
        int originZ = origin.getZ() - sizeZ / 2;
        BlockPos placePos = new BlockPos(originX, originY, originZ);

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setMirror(Mirror.NONE)
                .setRandom(random)
                .setIgnoreEntities(true)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);

        template.placeInWorld(level, placePos, placePos, settings, random, 2);

        scatterSoulSand(level, random, originX, originZ, sizeX, sizeZ);
        return true;
    }

    private void scatterSoulSand(WorldGenLevel level, RandomSource random,
                                 int originX, int originZ, int sizeX, int sizeZ) {
        // 配置读取
        if (!ModConfig.Common.SOUL_SAND_GENERATE.get()) {
            return;
        }
        int min = ModConfig.Common.SOUL_SAND_MIN.get();
        int max = Math.max(min, ModConfig.Common.SOUL_SAND_MAX.get());
        int margin = ModConfig.Common.SOUL_SAND_MARGIN.get();
        int sink = ModConfig.Common.SOUL_SAND_SINK.get();

        int count = min + random.nextInt(max - min + 1);
        int seaLevel = level.getSeaLevel();

        int minX = originX - margin;
        int maxX = originX + sizeX - 1 + margin;
        int minZ = originZ - margin;
        int maxZ = originZ + sizeZ - 1 + margin;

        int placed = 0;
        for (int attempt = 0; attempt < 200 && placed < count; attempt++) {
            int x = minX + random.nextInt(maxX - minX + 1);
            int z = minZ + random.nextInt(maxZ - minZ + 1);
            if (x >= originX && x <= originX + sizeX - 1
                    && z >= originZ && z <= originZ + sizeZ - 1) {
                continue;
            }
            // 只要求在海面以下至少 2 格（保证上方有水）
            int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
            if (y > seaLevel - 2) {
                continue;
            }

            // 灵魂沙埋到海床表面下 sink 格
            BlockPos soulPos = new BlockPos(x, y - sink, z);
            level.setBlock(soulPos, Blocks.SOUL_SAND.defaultBlockState(), 2);

            // 把灵魂沙顶面到海床表面之间挖成水（形成 sink 格深的坑）
            for (int dy = y - sink + 1; dy <= y; dy++) {
                BlockPos fill = new BlockPos(x, dy, z);
                if (level.getBlockState(fill).getBlock() != Blocks.WATER) {
                    level.setBlock(fill, Blocks.WATER.defaultBlockState(), 2);
                }
            }

            // 气泡柱从坑底冒到海面
            // 注意：BUBBLE_COLUMN 的默认状态 DRAG_DOWN = true，是岩浆块那种下降气泡柱；
            // 必须显式置 false 才是灵魂沙的上升气泡柱（否则只能等邻居更新触发的 5 tick 纠正）
            BlockState upwardColumn = Blocks.BUBBLE_COLUMN.defaultBlockState()
                    .setValue(BubbleColumnBlock.DRAG_DOWN, false);
            BlockPos.MutableBlockPos bub = new BlockPos(x, y - sink + 1, z).mutable();
            while (bub.getY() < seaLevel
                    && (level.getBlockState(bub).is(Blocks.WATER)
                    || level.getBlockState(bub).is(Blocks.BUBBLE_COLUMN))) {
                level.setBlock(bub, upwardColumn, 2);
                bub.move(Direction.UP);
            }
            placed++;
        }
    }
}
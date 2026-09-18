package com.minecart.yunxian.mixin;

import com.minecart.yunxian.client.budding.VanillaBuddingGoggles;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让原版紫水晶母岩也显示护目镜信息。
 * <p>
 * Create 的 {@code GoggleOverlayRenderer} 取信息的方式是
 * {@code level.getBlockEntity(pos)}，最终落到本方法；原版母岩没有方块实体，
 * 这里查空时补一个展示用实例，让浮窗有东西可读（合成逻辑见
 * {@link VanillaBuddingGoggles}，实例不会进区块、不落盘）。
 * <p>
 * 本类登记在 {@code create_crystal_industry.mixins.json} 的 {@code client} 段，
 * 只随物理客户端加载：服务端既无热路径开销，原版方块的行为也不受任何影响。
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {

    @Inject(
            method = "getBlockEntity(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk$EntityCreationType;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            at = @At("RETURN"),
            cancellable = true)
    private void yunxian$vanillaBuddingAmethystGoggles(BlockPos pos, LevelChunk.EntityCreationType creationType,
                                                       CallbackInfoReturnable<BlockEntity> cir) {
        // 绝大多数调用在这里就结束了：要么本来就有方块实体，要么不是母岩
        if (cir.getReturnValue() != null) {
            return;
        }

        BlockEntity display = VanillaBuddingGoggles.displayBlockEntity((LevelChunk) (Object) this, pos);
        if (display != null) {
            cir.setReturnValue(display);
        }
    }
}

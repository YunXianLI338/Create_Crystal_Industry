package com.minecart.yunxian.integration.ae2;

import appeng.api.AECapabilities;
import com.minecart.yunxian.blockentity.budding.FluixBuddingBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * AE2 专用的方块实体注册逻辑。
 * <p>
 * 本类中所有代码都会引用 {@code appeng.*}，因此<b>只能在确认 AE2 已加载
 * （{@code ModBlocks.AE2_LOADED}）之后才允许触碰本类</b>。
 * <p>
 * 之所以把 AE2 引用全部集中到这一个类里，而不是留在 {@code ModBlockEntities} 中
 * 用 {@code if (AE2_LOADED)} 包起来：Java 的类加载是「先校验、后执行」，
 * {@code if} 守卫只能挡住<b>执行</b>，挡不住 JVM 对常驻类的<b>字节码校验</b>。
 * 校验一个方法体时会解析其中出现的类型，而 lambda 的合成方法描述符里
 * 会带上推断出的返回类型 —— 例如 AE2 的 {@code IInWorldGridNodeHost} ——
 * 于是 AE2 缺失时即便分支永远不会执行，加载 {@code ModBlockEntities} 仍会抛
 * {@code NoClassDefFoundError}。
 * <p>
 * 把引用挪进本类后，{@code ModBlockEntities} 里只剩签名干净的
 * {@code invokestatic}，JVM 校验时无需解析本类，软依赖才真正成立。
 */
public final class AE2BlockEntities {

    private AE2BlockEntities() {
    }

    /** 注册福鲁伊克斯母岩的方块实体类型。 */
    public static Supplier<BlockEntityType<?>> registerFluix(
            DeferredRegister<BlockEntityType<?>> registry, Supplier<Block> fluixBuddingBlock) {
        return registry.register("fluix_budding", () -> BlockEntityType.Builder.of(
                (pos, state) -> FluixBuddingBlockEntity.create(pos, state),
                fluixBuddingBlock.get()
        ).build(null));
    }

    /** 把福鲁伊克斯母岩方块实体注册为 ME 网格节点宿主（供 AE 能量与频道使用）。 */
    @SuppressWarnings("unchecked")
    public static void registerCapabilities(RegisterCapabilitiesEvent event,
                                            Supplier<BlockEntityType<?>> fluixBuddingType) {
        // 常驻类那边类型被擦成 BlockEntityType<?>，这里补回具体类型：
        // registerBlockEntity 的 BE 与 T（= IInWorldGridNodeHost）之间需要真实子类型关系。
        BlockEntityType<FluixBuddingBlockEntity> type =
                (BlockEntityType<FluixBuddingBlockEntity>) fluixBuddingType.get();
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                type,
                (blockEntity, context) -> blockEntity);
    }
}

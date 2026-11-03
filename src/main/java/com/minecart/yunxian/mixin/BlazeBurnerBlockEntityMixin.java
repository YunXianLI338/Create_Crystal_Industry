package com.minecart.yunxian.mixin;

import com.minecart.yunxian.Yunxian;
import com.simibubi.create.api.data.datamaps.BlazeBurnerFuel;
import com.simibubi.create.api.registry.CreateDataMaps;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让可燃冰燃料越过 Create 的「超热燃料不许排队」限制：燃烧室已处于 SEETHING 时，
 * 可燃冰照样能一件一件灌下去，上限与普通燃料同为 {@code MAX_HEAT_CAPACITY}（10000 tick，即 500 秒），
 * 喂满就拒收。
 *
 * <h2>Create 原本的规则</h2>
 * {@code BlazeBurnerBlockEntity#tryUpdateFuel} 对<b>同种</b>燃料的续加分三条路：
 * <ul>
 *   <li>剩余燃烧时间 ≤ {@code INSERTION_THRESHOLD}（500 tick）—— 直接叠加，两种燃料一视同仁；</li>
 *   <li>越线之后，只有 {@code forceOverflow && newFuel == FuelType.NORMAL} 才允许溢出补料，
 *       封顶 10000 tick；</li>
 *   <li>其余情形（也就是所有超热燃料）一律 {@code return false}。</li>
 * </ul>
 * 于是煤炭（NORMAL，1600）能一路补到 10000，而可燃冰（SPECIAL，400）只能 400 → 800 ——
 * 第三次开始撞上 500 那条线，手里再怎么右键都没反应。根子在 SPECIAL 被显式排除在溢出分支之外，
 * 与数值无关：把 burn_time 调小也只是把「两个」变成「三个」。
 *
 * <h2>为什么是 HEAD 注入，而不是改那个条件</h2>
 * 判据是个复合布尔（{@code forceOverflow && newFuel == FuelType.NORMAL}），
 * {@code @Redirect} / {@code @ModifyExpressionValue} 都打不到整个表达式；退而求其次去重定向中间那次
 * {@code GETSTATIC FuelType.NORMAL} 也不行——同一个常量在方法里还被三处赋值语句读着，
 * 按 ordinal 定位既脆又会误伤。所以在方法最前面接管「原版必定拒绝」的那一段，
 * 其余情形一律 {@code return} 交给原版，边界反而最清楚：
 * <b>原版管 ≤500 的叠加，我们只补 500 &lt; t &lt; 10000 的那一段，到顶由我们拒收</b>。
 *
 * <h2>判据</h2>
 * 「本模组命名空间 + 出现在 {@code create:superheated_blaze_burner_fuels} 数据表里」。
 * 用数据表而不是另建标签，是因为可燃冰燃料的清单本来就只有这一份（可燃冰本体、各装饰方块、
 * 浆液瓶/桶、可燃冰圣代、各级晶体芽与簇都在里面），以后加变体不用两头同步。
 *
 * <h2>边界</h2>
 * <ul>
 *   <li>喂满就收手：{@code remainingBurnTime} 够到 {@link BlazeBurnerBlockEntity#MAX_HEAT_CAPACITY}
 *       时回 {@code false}，手里那件留在手上。原版那条溢出分支（只对普通燃料开放）在到顶时是把燃烧时间
 *       原样写回、<b>仍然回 true</b>；照抄这个写法，喂满之后每右键一次就白搭一件燃料。宁可到顶即拒；</li>
 *   <li>只对 {@code forceOverflow == true} 生效，也就是<b>玩家手持右键</b>。机械臂走的是
 *       {@code AllArmInteractionPointTypes} 里 {@code forceOverflow = false} 的那条路，
 *       维持 Create 原样，免得机器把超热燃料无限灌满；</li>
 *   <li>{@code simulate} 只回 {@code true} 不动数据，跟原版一致——调用方拿它试算，
 *       真正扣料的是随后的非模拟那次；</li>
 *   <li>燃烧室烧的是别家的超热燃料（比如烈焰蛋糕）时，拿可燃冰喂照样能续——它本来就是本模组燃料；
 *       反过来拿烈焰蛋糕喂，判据不过，Create 自己的平衡一点不动。</li>
 * </ul>
 *
 * <h2>双端</h2>
 * {@code tryUpdateFuel} 客户端与服务端都会跑（客户端那次由 {@code BlazeBurnerBlock#useItemOn} 触发）。
 * 所以这里跟原版尾部一样分岔：客户端只放粒子，服务端才改 NBT、播插入音、刷新方块状态。
 * 放 mixins.json 的 {@code "mixins"} 段——方块实体双端都跑。
 */
@Mixin(BlazeBurnerBlockEntity.class)
public abstract class BlazeBurnerBlockEntityMixin {

    @Shadow
    protected BlazeBurnerBlockEntity.FuelType activeFuel;

    @Shadow
    protected int remainingBurnTime;

    @Shadow
    protected abstract void playSound();

    @Inject(method = "tryUpdateFuel", at = @At("HEAD"), cancellable = true)
    private void yunxian$overflowFlammableIce(ItemStack itemStack, boolean forceOverflow, boolean simulate,
                                              CallbackInfoReturnable<Boolean> cir) {
        BlazeBurnerBlockEntity self = (BlazeBurnerBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null)
            return;

        if (!forceOverflow || self.isCreative)
            return;
        if (activeFuel != BlazeBurnerBlockEntity.FuelType.SPECIAL)
            return;
        // 没越线时原版自己就会叠加，别抢
        if (remainingBurnTime <= BlazeBurnerBlockEntity.INSERTION_THRESHOLD)
            return;

        int burnTime = yunxian$superheatedFuelBurnTime(itemStack);
        if (burnTime < 0)
            return;

        // 已经喂满（10000 tick = 500 秒）：一件也塞不进去了。原版的溢出分支到顶时只把燃烧时间原样
        // 写回、照样回 true，物品会被白白吃掉；这里直接拒收，手里那件不动，也免得客户端先冒一圈粒子。
        if (remainingBurnTime >= BlazeBurnerBlockEntity.MAX_HEAT_CAPACITY) {
            cir.setReturnValue(false);
            return;
        }

        if (simulate) {
            cir.setReturnValue(true);
            return;
        }

        remainingBurnTime = Math.min(remainingBurnTime + burnTime,
                BlazeBurnerBlockEntity.MAX_HEAT_CAPACITY);

        if (level.isClientSide) {
            self.spawnParticleBurst(true);
            cir.setReturnValue(true);
            return;
        }

        playSound();
        self.updateBlockState();
        cir.setReturnValue(true);
    }

    /**
     * 可燃冰燃料的燃烧时间；不是本模组登记在超热燃料表里的物品则返回 -1。
     * <p>
     * 走 {@code BuiltInRegistries.ITEM} 而不是 {@code Item#builtInRegistryHolder()}——后者在
     * NeoForge 里已标 {@code @Deprecated}。两条路读的是同一份数据表：{@code Holder.Reference#getData}
     * 内部就是把它转交给所属注册表。
     */
    private static int yunxian$superheatedFuelBurnTime(ItemStack stack) {
        if (stack.isEmpty())
            return -1;
        ResourceKey<Item> key = BuiltInRegistries.ITEM.getResourceKey(stack.getItem())
                .orElse(null);
        if (key == null || !Yunxian.MODID.equals(key.location().getNamespace()))
            return -1;
        BlazeBurnerFuel fuel = BuiltInRegistries.ITEM.getData(
                CreateDataMaps.SUPERHEATED_BLAZE_BURNER_FUELS, key);
        return fuel == null ? -1 : fuel.burnTime();
    }
}

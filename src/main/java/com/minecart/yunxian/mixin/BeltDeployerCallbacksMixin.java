package com.minecart.yunxian.mixin;

import java.util.List;

import com.minecart.yunxian.item.FlammableIceSlurryBottleItem;
import com.minecart.yunxian.registry.ModBlocks;
import com.simibubi.create.content.kinetics.deployer.BeltDeployerCallbacks;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.recipe.RecipeApplier;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 可燃冰沙瓶 → 可燃冰圣代 这条装配配方（{@code create_crystal_industry:flammable_sundae_from_deploying}）
 * 只交出圣代，不再顺带还一只空玻璃瓶。
 * <p>
 * 现象：传送带（或置物台）上的可燃冰沙瓶被机械手蘸岩浆膏一压，圣代旁边还多躺着一只空玻璃瓶。
 * <p>
 * <b>为什么改配方数据改不掉</b>：{@code create:deploying} 的 JSON 走
 * {@code ItemApplicationRecipeParams}，那里只有一个 {@code keep_held_item}
 * （ItemApplicationRecipeParams.java:25），管的是机械手<b>手里那件</b>（岩浆膏）留不留；
 * 空瓶跟岩浆膏无关，它是<b>被处理物</b>的容器余物，配方数据里没有任何字段能关掉它。
 * <p>
 * 空瓶其实来自 {@code RecipeApplier#applyRecipeOn}：算完配方产物之后，它还会看一眼被处理物有没有
 * 容器余物，有就往结果列表里再塞一份（RecipeApplier.java:56-58）。传送带上的机械手正是拿
 * {@code returnProcessingRemainder = true} 调的这个方法（BeltDeployerCallbacks.java:101）。
 * 余物的来源是瓶子的 {@code craftRemainder(Items.GLASS_BOTTLE)}
 * （见 {@code ModItems#FLAMMABLE_ICE_SLURRY_BOTTLE}）。
 * <p>
 * <b>为什么不能直接删掉瓶子的 craftRemainder</b>：那是「烧掉燃料还你空瓶」的依据
 * （烈焰人在 {@code BlazeBurnerBlock} 里自己判 {@code stack.hasCraftingRemainingItem()}，
 * BlazeBurnerBlock.java:199；照着它写的冷却器同理）。而且容器余物是物品实例自己的数据——
 * {@code Item#craftRemainder} 在 {@code ModItems} 注册物品时就定死了，数据包与配方都碰不到，
 * 只能从调用点下手。
 * <p>
 * <b>范围只掐这条配方</b>：被处理物是本模组的可燃冰沙瓶、且配方产物是可燃冰圣代
 * （{@link ModBlocks#FLAMMABLE_SUNDAE}）时，把 {@code returnProcessingRemainder} 传成 {@code false}，
 * 其余情形原样转发。1.21.1 的 {@code Recipe} 接口没有 {@code getId()}，这一刻拿不到配方 id，
 * 所以用「装配配方 + 产物是圣代」锁定它——这两件合起来正好就是那一条 JSON；
 * 日后若改了这条配方的产物或输入，这里要跟着改。压床、粉碎轮、砂纸、机械合成器各自的余物判定
 * 都写在自己类里，压根不走这一句；机械手在别的配方族（压印、砂纸）与别的物品上也不受影响；
 * 瓶子当燃料烧掉仍然还瓶。
 * <p>
 * 放 mixins.json 的 {@code "mixins"} 段：机械手的加工判定在服务端跑，双端加载这个类无害。
 */
@Mixin(BeltDeployerCallbacks.class)
public class BeltDeployerCallbacksMixin {

    @Redirect(
            method = "activate",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/recipe/RecipeApplier;"
                            + "applyRecipeOn(Lnet/minecraft/world/level/Level;"
                            + "Lnet/minecraft/world/item/ItemStack;"
                            + "Lnet/minecraft/world/item/crafting/Recipe;Z)Ljava/util/List;"
            )
    )
    private static List<ItemStack> yunxian$deployingReturnsNoBottle(Level level, ItemStack stackIn,
                                                                    Recipe<?> recipe,
                                                                    boolean returnProcessingRemainder) {
        if (returnProcessingRemainder
                && stackIn.getItem() instanceof FlammableIceSlurryBottleItem
                && isFlammableSundaeAssembly(recipe))
            returnProcessingRemainder = false;
        return RecipeApplier.applyRecipeOn(level, stackIn, recipe, returnProcessingRemainder);
    }

    /**
     * 这条装配配方：机械手把可燃冰沙瓶压成可燃冰圣代。
     * <p>
     * 这里刻意写成 for 循环而不是 stream + lambda：Mixin 把方法并进目标类时，
     * lambda 的 invokedynamic 引导参数仍指着本 mixin 类，边界情况容易炸；本项目别的 mixin 也都不写 lambda。
     */
    private static boolean isFlammableSundaeAssembly(Recipe<?> recipe) {
        if (!(recipe instanceof DeployerApplicationRecipe deployingRecipe))
            return false;
        Item sundae = ModBlocks.FLAMMABLE_SUNDAE.get()
                .asItem();
        for (ProcessingOutput output : deployingRecipe.getRollableResults()) {
            if (output.getStack()
                    .is(sundae))
                return true;
        }
        return false;
    }
}

package com.minecart.yunxian.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/**
 * 可燃冰沙瓶：一瓶 250 mB 的可燃冰沙，喝下去给 10 秒「灼寒」并留下空瓶。
 * <p>
 * 除了饮用姿势，它没有别的行为——食物那部分全在 {@code Item.Properties#food(...)} 里
 * （见 {@code ModItems#FLAMMABLE_ICE_SLURRY_BOTTLE}），喝空留瓶由原版
 * {@code FoodProperties#usingConvertsTo} 负责，和可燃冰圣代同一套写法。
 * <p>
 * <b>为什么要单独一个类</b>：默认的 {@code Item#getUseAnimation} 是 {@code EAT}（啃的动作），
 * 而瓶装饮料该是 {@code DRINK}（仰头灌）。音效同理——原版蜂蜜瓶用的就是这两样。
 * <p>
 * 它同时也是件燃料（见燃料数据映射），所以带着 {@code craftRemainder(玻璃瓶)}：
 * 塞进烈焰人/冷却器烧掉时，那一份"剩下的容器"会被还回来。
 * <p>
 * 正途则是拿去加工：机械手蘸岩浆膏一压就成可燃冰圣代（{@code create:deploying}），
 * 这一次不还瓶——容器余物只留给「当燃料烧掉」那一路（见 {@code BeltDeployerCallbacksMixin}）。
 */
public class FlammableIceSlurryBottleItem extends Item {

    public FlammableIceSlurryBottleItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.HONEY_DRINK;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.HONEY_DRINK;
    }
}

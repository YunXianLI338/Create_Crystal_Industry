package com.minecart.yunxian.compat.jei;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * JEI 母岩信息页的一「条配方」：左边要渲染的母岩与晶簇，右边要逐行写出的全部文字。
 * <p>
 * 刻意<b>不引用任何 JEI 类型</b>：本类与收集/解析（{@code BuddingInfoCollector}、
 * {@code GenerationInfoReader}）都是纯 Minecraft 代码，只有 {@code BuddingInfoCategory}
 * 与插件类碰 JEI——JEI 在本模组里是可选依赖（build.gradle 里是 {@code compileOnly}），
 * 玩家没装时这些类根本不会被加载。
 * <p>
 * 每行的文字与样式在<b>构建期</b>就定好，绘制时不再做判断；版面是<b>固定尺寸</b>的
 * （{@code AbstractRecipeCategory} 的宽高没有配方参数，做不到一页一尺寸），
 * 所以文案要写短——{@link BuddingInfoCategory} 画不下时会直接停笔，不会画出框。
 *
 * @param budding     左侧渲染的母岩方块
 * @param cluster     长在母岩顶面上的晶簇（按它在世界里的朝向渲染）；null = 对应的晶簇未知
 *                    （外来模组方块的兜底情况，那时只画母岩）
 * @param product     晶簇被采下时掉的物品，显示在左上角的栏位里；{@link ItemStack#EMPTY} = 不显示栏位
 *                    （脚本晶簇可以配置成什么都不掉）
 * @param lookupItems 参与 JEI 检索的物品（母岩 + 各级芽/晶簇）。它们不显示，只保证玩家在 JEI 里
 *                    对着母岩按 R/U、或把它拖进收藏书签时能翻到这一页；
 *                    {@link #product} 走的是可见栏位，本身就参与检索，不重复登记
 * @param rows        右侧逐行文字
 */
public record BuddingInfo(BlockState budding,
                          @Nullable BlockState cluster,
                          ItemStack product,
                          List<ItemStack> lookupItems,
                          List<Row> rows) {

    /** 一行文字：样式 + 内容。颜色与缩进由 {@link BuddingInfoCategory} 按样式统一决定 */
    public record Row(Style style, Component text) {

        /** 小节标题（生长条件 / 生长速度 / 生成条件） */
        public static Row header(Component text) {
            return new Row(Style.HEADER, text);
        }

        /** 正文行 */
        public static Row line(Component text) {
            return new Row(Style.LINE, text);
        }

        /** 补充说明（灰字）：来源提示、不自然生成一类 */
        public static Row note(Component text) {
            return new Row(Style.NOTE, text);
        }
    }

    public enum Style {
        HEADER,
        LINE,
        NOTE
    }
}

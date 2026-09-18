package com.minecart.yunxian.compat.jei;

import java.util.List;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 信息页文案的公共拼装件。
 * <p>
 * 句子本身一律走语言文件（{@link #LANG} 前缀下的键），这里只负责把动态部分——方块名、群系名——
 * 拼成能当 {@code Component.translatable} 实参的片段，以及「一长串名字折成前几个 + 等 N 个」这件反复要做的事。
 */
final class BuddingInfoText {

    /** 全部 JEI 文案的键前缀 */
    static final String LANG = "create_crystal_industry.jei.budding.";

    /** 一串名字最多显示几个，其余折成「等 N 个」 */
    static final int MAX_SHOWN = 3;

    /** 并列名字之间的分隔符。中英文都读得顺，所以不放进语言文件 */
    private static final String SEPARATOR = " / ";

    private BuddingInfoText() {
    }

    /**
     * 并列句子之间的分隔符（如"石头→铁矿（1/20）"与"粗铁块→母岩（1/25000）"之间）。
     * 中文用「；」、英文用「; 」，所以放语言文件里；构建这一页时取一次。
     */
    static String sentenceSeparator() {
        return I18n.get(LANG + "list.separator");
    }

    /** 用 {@link #SEPARATOR} 连接若干片段；空列表得到空组件 */
    static MutableComponent join(List<Component> parts) {
        return join(parts, SEPARATOR);
    }

    static MutableComponent join(List<Component> parts, String separator) {
        MutableComponent joined = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                joined.append(Component.literal(separator));
            }
            joined.append(parts.get(i));
        }
        return joined;
    }

    /** 名字太多时只列前 {@link #MAX_SHOWN} 个，再补一句「等 N 个」 */
    static Component summarize(List<Component> names) {
        List<Component> shown = names.subList(0, Math.min(MAX_SHOWN, names.size()));
        MutableComponent joined = join(shown);
        return names.size() > shown.size()
                ? Component.translatable(LANG + "list.more", joined, names.size())
                : joined;
    }
}

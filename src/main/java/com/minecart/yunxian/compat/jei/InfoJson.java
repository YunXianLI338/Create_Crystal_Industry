package com.minecart.yunxian.compat.jei;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.minecart.yunxian.Yunxian;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.locating.IModFile;

/**
 * 信息页读本模组自己那几个数据包 JSON（世界生成、掉落表）的那点管道。
 * <p>
 * <b>为什么不用客户端的资源管理器</b>：{@code Minecraft} 的资源管理器是拿
 * {@code PackType.CLIENT_RESOURCES} 建的，而这个名字对应的是 {@code assets/} 目录——
 * 用 {@code getResource()} 去读 {@code data/...} 只会去 {@code assets/...} 找，
 * 永远找不到，而且不报错（只是返回空）。客户端能看到的 <i>data</i> 内容只有服务端同步过来的
 * 那几个注册表（配方、标签、生物群系……），placed_feature 与掉落表都不在其中。
 * 所以这里改成<b>直接读本模组自己的 mod 文件</b>：dev 环境读到构建输出目录、
 * 生产环境读到自己那个 jar，两边一致。
 * <p>
 * 代价要说清楚：这样读到的只能是<b>本模组自己打包的</b>那些 JSON，
 * 整合包在数据包里覆盖同名文件时页面不会跟着变（客户端根本看不到那些文件）。
 * 模组自身改 JSON → 重新构建后页面即更新，这一点仍然成立。
 * <p>
 * 另外，这个类只在<b>本模组</b>的数据上工作：vanilla 与原版数据包的文件不在本模组的 mod 文件里，
 * 需要它们的调用方（如 {@link ClusterProductReader} 对原版紫水晶簇）另有兜底。
 */
final class InfoJson {

    private static final Logger LOGGER = LogUtils.getLogger();

    private InfoJson() {
    }

    /**
     * 读本模组数据包里的一个文件。
     *
     * @param path 相对命名空间根的路径，<b>不含</b> {@code .json} 后缀，
     *             如 {@code worldgen/placed_feature/x}、{@code loot_table/blocks/x}
     * @return 解析好的对象；文件不存在或读不出来时返回 {@code null}
     */
    @Nullable
    static JsonObject read(ResourceLocation path) {
        Path file = find(path);
        if (file == null) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.warn("[JEI] 解析 {} 失败，相关信息页将退回默认文案", path, e);
            return null;
        }
    }

    /**
     * 找本模组数据包里的文件（不读内容）。
     *
     * @return 文件路径；不存在时返回 {@code null}
     */
    @Nullable
    static Path find(ResourceLocation path) {
        IModFile modFile = modFile();
        if (modFile == null) {
            return null;
        }
        try {
            // findResource 的参数是路径分段：data/<命名空间>/<路径>.json
            Path file = modFile.findResource("data", path.getNamespace(), path.getPath() + ".json");
            if (file == null || !Files.isRegularFile(file)) {
                LOGGER.debug("[JEI] 本模组数据包里没有 {}，相关信息页将退回默认文案", path);
                return null;
            }
            return file;
        } catch (RuntimeException e) {
            LOGGER.warn("[JEI] 访问本模组数据包文件 {} 失败", path, e);
            return null;
        }
    }

    /** 本模组自己的 mod 文件（dev 下是构建输出目录，生产环境是 jar） */
    @Nullable
    private static IModFile modFile() {
        ModList modList = ModList.get();
        if (modList == null) {
            return null;
        }
        IModFileInfo info = modList.getModFileById(Yunxian.MODID);
        return info == null ? null : info.getFile();
    }
}

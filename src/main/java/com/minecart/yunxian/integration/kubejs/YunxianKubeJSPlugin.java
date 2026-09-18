package com.minecart.yunxian.integration.kubejs;

import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

/**
 * KubeJS 插件：把 {@link CustomBudding} 绑成脚本全局对象，并放开脚本对本模组类的访问。
 * <p>
 * KubeJS 不读模组 jar 里的脚本，但会读 jar 根部的 {@code kubejs.plugins.txt}
 * （一行一个类名），据此加载本类——所以本类<b>只在装了 KubeJS 时才会被加载</b>，
 * 没装 KubeJS 的玩家完全不受影响（与 AE2 联动的隔离做法一致）。
 */
public class YunxianKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void registerBindings(BindingRegistry bindings) {
        // 脚本里直接写 CustomBudding.create(...) / new CustomBuddingOptions()
        bindings.add("CustomBudding", CustomBudding.class);
        bindings.add("CustomBuddingOptions", CustomBudding.Options.class);
    }

    @Override
    public void registerClasses(ClassFilter filter) {
        // 进阶用法：脚本可以直接 Java.loadClass 本模组的生长引擎与定义自己拼
        filter.allow("com.minecart.yunxian.**");
    }
}

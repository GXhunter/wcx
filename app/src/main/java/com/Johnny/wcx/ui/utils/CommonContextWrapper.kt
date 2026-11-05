package com.Johnny.wcx.ui.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.view.ContextThemeWrapper
import com.Johnny.wcx.constants.PackageNames
import com.Johnny.wcx.utils.reflection.ClassLoaders

/**
 * 模块注入 UI 时使用的 Context 包装。
 *
 * 历史实现曾把 Resources 也包装成 `ResourcesWrapper : Resources(base.assets, ...)`，
 * 那并不是「委托」，而是用 base 的 AssetManager **新建了一个 Resources 实例**。
 * 在 Android 15/16（targetSdk 36）上，未重写的 `Resources.getDrawableForDensity`
 * 会走到这个新建实例的 impl，其资源 cookie 与微信原包不一致，于是
 * `WeImageView.setImageResource()` 抛
 * `Resources$NotFoundException: Drawable com.tencent.mm:raw/icons_filled_edited`，微信进程崩溃。
 *
 * 因此这里改为**完全不碰 Resources**：getResources() 直接返回宿主原 Resources，
 * 保证微信所有 drawable / color / dimen 的查找行为与未注入时完全一致。
 *
 * Compose 的无障碍描述仍依赖模块字符串，必须使用 [composeContext] 创建 ComposeView，
 * 并将其 Resources 提供给 LocalResources；宿主 View 和业务操作继续使用当前 Context。
 */
class CommonContextWrapper(val base: Context) : ContextWrapper(base) {

    val composeContext: Context by lazy {
        val moduleContext = base.createPackageContext(PackageNames.MODULE, 0)
            .createConfigurationContext(base.resources.configuration)
        // 无障碍委托直接读取 View.resources，仅替换 LocalResources 无法修复百分比文案崩溃。
        object : ContextThemeWrapper(this, moduleContext.theme) {
            override fun getResources(): Resources = moduleContext.resources
            override fun getAssets() = moduleContext.assets
        }
    }

    override fun getClassLoader(): ClassLoader {
        return ClassLoaders.MODULE
    }

    /**
     * 必须返回宿主原 Resources，禁止再包装或新建实例。
     * 详见类注释：包装会让微信自身的资源查找失败并崩溃。
     */
    override fun getResources(): Resources {
        return base.resources
    }
}

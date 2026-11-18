package io.legado.app.base

import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color

/**
 * 高对比纯黑文字。
 * 通过包装 Resources, 把界面里"发灰"的文本色替换为纯黑(#FF000000),
 * 只影响被替换的颜色 id, 其余资源原样返回。开关由 PreferKey.highContrastText 控制。
 */
class HighContrastContext(base: Context) : ContextWrapper(base) {

    private var hcResources: Resources? = null

    override fun getResources(): Resources =
        hcResources ?: HighContrastResources(super.getResources()).also { hcResources = it }
}

private class HighContrastResources(res: Resources) :
    Resources(res.assets, res.displayMetrics, res.configuration) {

    private fun isGrayTextColor(id: Int): Boolean {
        val name = try {
            getResourceEntryName(id)
        } catch (e: Exception) {
            return false
        }
        return name == "primaryText" ||
                name == "secondaryText" ||
                name == "tv_text_summary" ||
                name == "ate_primary_text_light" ||
                name == "ate_secondary_text_light" ||
                name == "ate_icon_light" ||
                name == "ate_control_normal_light" ||
                name == "ate_text_disabled_light"
    }

    override fun getColor(id: Int): Int =
        if (isGrayTextColor(id)) Color.BLACK else super.getColor(id)

    override fun getColorStateList(id: Int): ColorStateList =
        if (isGrayTextColor(id)) ColorStateList.valueOf(Color.BLACK) else super.getColorStateList(id)

    override fun getColor(id: Int, theme: Theme?): Int =
        if (isGrayTextColor(id)) Color.BLACK else super.getColor(id, theme)

    override fun getColorStateList(id: Int, theme: Theme?): ColorStateList =
        if (isGrayTextColor(id)) ColorStateList.valueOf(Color.BLACK) else super.getColorStateList(id, theme)
}

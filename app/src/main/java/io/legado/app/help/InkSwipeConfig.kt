package io.legado.app.help

import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefInt
import splitties.init.appCtx

/**
 * 阅读页左右侧上下滑手势的感应区。
 *
 * 感应区是贴着屏幕左右边缘的两条竖带，宽度从边缘往内计算；
 * 默认只有 20dp（约 4mm），避免占用中间用于翻页/选字的区域，也给侧边控制中心留出位置。
 * 左右各自最多占屏幕宽度的 30%，所以两侧不可能铺满整个屏幕。
 */
object InkSwipeConfig {
    /** 感应区宽度下限 / 上限 / 默认值（dp） */
    const val minEdgeWidth = 20
    const val maxEdgeWidth = 120
    const val defaultEdgeWidth = 20

    /** 单侧宽度上限占屏幕宽度的比例（左右各 30%，中间至少留 40%） */
    const val maxWidthRatio = 0.30f

    var edgeWidth: Int
        get() = appCtx.getPrefInt(PreferKey.inkSwipeEdgeWidth, defaultEdgeWidth)
            .coerceIn(minEdgeWidth, maxEdgeWidth)
        set(value) {
            appCtx.putPrefInt(
                PreferKey.inkSwipeEdgeWidth,
                value.coerceIn(minEdgeWidth, maxEdgeWidth)
            )
        }
}

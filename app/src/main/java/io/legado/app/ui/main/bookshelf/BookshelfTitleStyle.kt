package io.legado.app.ui.main.bookshelf

import android.graphics.Typeface
import android.widget.TextView
import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt

/**
 * 书架书名样式：0 表示沿用布局默认字号，其余为实际 sp。
 */
fun TextView.upBookshelfTitleStyle() {
    val size = context.getPrefInt(PreferKey.bookshelfTitleSize, 0)
    if (size > 0) {
        textSize = size.toFloat()
    }
    val bold = context.getPrefBoolean(PreferKey.bookshelfTitleBold, false)
    typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
}

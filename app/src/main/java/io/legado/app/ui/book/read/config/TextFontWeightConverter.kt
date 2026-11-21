package io.legado.app.ui.book.read.config

import android.content.Context
import android.util.AttributeSet
import io.legado.app.R
import io.legado.app.help.ReadBookConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.widget.text.StrokeTextView

/**
 * 字重切换：0~10 共 11 档，点击弹出选择器。
 * 显示成「字重N」，N 越大越粗（0 最细、10 最粗）。
 */
class TextFontWeightConverter(context: Context, attrs: AttributeSet?) :
    StrokeTextView(context, attrs) {

    private var onChanged: (() -> Unit)? = null

    init {
        if (!isInEditMode) {
            upUi(ReadBookConfig.textBold)
        }
        setOnClickListener {
            selectType()
        }
    }

    fun upUi(type: Int) {
        text = context.getString(R.string.ink_text_weight_value, type.coerceIn(0, 10))
    }

    private fun selectType() {
        val levels = (0..10).map { it.toString() }
        context.alert(titleResource = R.string.text_font_weight_converter) {
            items(levels) { _, i ->
                ReadBookConfig.textBold = i
                upUi(i)
                onChanged?.invoke()
            }
        }
    }

    fun onChanged(unit: () -> Unit) {
        onChanged = unit
    }
}

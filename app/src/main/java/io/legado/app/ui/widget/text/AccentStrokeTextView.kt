package io.legado.app.ui.widget.text

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import io.legado.app.R
import io.legado.app.lib.theme.Selector
import io.legado.app.utils.dp
import io.legado.app.utils.getCompatColor

class AccentStrokeTextView(context: Context, attrs: AttributeSet) :
    AppCompatTextView(context, attrs) {

    private var radius = 3.dp
    private val isBottomBackground: Boolean

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AccentStrokeTextView)
        radius = typedArray.getDimensionPixelOffset(R.styleable.StrokeTextView_radius, radius)
        isBottomBackground =
            typedArray.getBoolean(R.styleable.StrokeTextView_isBottomBackground, false)
        typedArray.recycle()
        upStyle()
    }

    private fun upStyle() {
        // 墨水屏：描边按钮（如「反选」「删除」）文字与描边恒为纯黑，
        // 不用主题里的 accent / disabled 半透明灰，否则看着像灰字。
        val inkBlack = android.graphics.Color.BLACK
        background = Selector.shapeBuild()
            .setCornerRadius(radius)
            .setStrokeWidth(1.dp)
            .setDisabledStrokeColor(inkBlack)
            .setDefaultStrokeColor(inkBlack)
            .setPressedBgColor(context.getCompatColor(R.color.transparent30))
            .create()
        setTextColor(
            Selector.colorBuild()
                .setDefaultColor(inkBlack)
                .setDisabledColor(inkBlack)
                .create()
        )
    }

}

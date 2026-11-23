package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import io.legado.app.R
import io.legado.app.help.InkSwipeConfig
import io.legado.app.utils.dp
import io.legado.app.utils.sp

/**
 * 「侧滑感应区」预览：画一个竖屏示意，左右两条灰色竖带表示可上下滑的感应区。
 * 只用于设置界面，帮助直观看到感应区宽度。
 */
class SwipeEdgePreview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var edgeWidthDp: Int = InkSwipeConfig.defaultEdgeWidth
        set(value) {
            field = value.coerceIn(InkSwipeConfig.minEdgeWidth, InkSwipeConfig.maxEdgeWidth)
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val screenRect = RectF()
    private val stripRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        // 竖屏示意：JDRead1 1072×1448
        val pad = 16f.dp
        val availW = width - pad * 2
        val availH = height - pad * 2 - 18f.dp // 底部留一行数值
        val ratio = 1072f / 1448f
        var h = availH
        var w = h * ratio
        if (w > availW) {
            w = availW
            h = w / ratio
        }
        val left = (width - w) / 2f
        val top = pad
        screenRect.set(left, top, left + w, top + h)

        // 屏幕白底
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawRoundRect(screenRect, 6f.dp, 6f.dp, paint)

        // 左右感应区：宽度按 536dp 屏宽换算，单侧最多 30%
        val stripW = w * (edgeWidthDp / 536f)
            .coerceAtMost(InkSwipeConfig.maxWidthRatio)
        paint.color = Color.parseColor("#DDDDDD")
        stripRect.set(screenRect.left, screenRect.top, screenRect.left + stripW, screenRect.bottom)
        canvas.drawRect(stripRect, paint)
        stripRect.set(screenRect.right - stripW, screenRect.top, screenRect.right, screenRect.bottom)
        canvas.drawRect(stripRect, paint)

        // 感应区描边
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f.dp
        paint.color = Color.BLACK
        stripRect.set(screenRect.left, screenRect.top, screenRect.left + stripW, screenRect.bottom)
        canvas.drawRect(stripRect, paint)
        stripRect.set(screenRect.right - stripW, screenRect.top, screenRect.right, screenRect.bottom)
        canvas.drawRect(stripRect, paint)

        // 屏幕边框
        paint.strokeWidth = 2f.dp
        canvas.drawRoundRect(screenRect, 6f.dp, 6f.dp, paint)

        // 中间区域说明
        textPaint.textSize = 13f.sp
        canvas.drawText(
            context.getString(R.string.ink_swipe_edge_center_area),
            screenRect.centerX(),
            screenRect.centerY() + textPaint.textSize / 3f,
            textPaint
        )

        // 底部当前宽度
        textPaint.textSize = 12f.sp
        canvas.drawText(
            context.getString(R.string.ink_swipe_edge_width_value, edgeWidthDp),
            screenRect.centerX(),
            screenRect.bottom + 14f.dp,
            textPaint
        )
    }
}

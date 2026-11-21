package io.legado.app.lib.theme.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSeekBar
import io.legado.app.help.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.applyTint
import io.legado.app.utils.dp

/**
 * @author Aidan Follestad (afollestad)
 */
class ThemeSeekBar(context: Context, attrs: AttributeSet) : AppCompatSeekBar(context, attrs) {

    private val inkMode = AppConfig.isEInkMode
    private val thumbDp = 12.dp
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xB4, 0xB4, 0xB4)
        strokeWidth = 2.dp.toFloat()
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 2.dp.toFloat()
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

    init {
        if (!isInEditMode) {
            if (inkMode) {
                // 墨水屏：轨道与球全部纯黑自绘。保留一个正确尺寸的 thumb,
                // 让 SeekBar 自身的触摸坐标计算与 onDraw 里的坐标完全一致。
                thumb = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.BLACK)
                    setSize(thumbDp, thumbDp)
                }
                progressDrawable = ColorDrawable(Color.TRANSPARENT)
                thumbOffset = thumbDp / 2
            } else {
                applyTint(context.accentColor)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!inkMode || isInEditMode) {
            super.onDraw(canvas)
            return
        }
        // 球心与轨道线共用同一个 cy，彻底消除 Y 轴错位
        val cy = paddingTop + (height - paddingTop - paddingBottom) / 2f
        val insetL = paddingLeft + thumbDp / 2f
        val insetR = paddingRight + thumbDp / 2f
        val available = (width - insetL - insetR).coerceAtLeast(0f)
        val ratio = if (max <= 0) 0f else (progress.toFloat() / max.toFloat()).coerceIn(0f, 1f)
        val cx = insetL + available * ratio
        canvas.drawLine(insetL, cy, width - insetR, cy, trackPaint)
        if (ratio > 0f) canvas.drawLine(insetL, cy, cx, cy, progressPaint)
        canvas.drawCircle(cx, cy, thumbDp / 2f, thumbPaint)
    }
}

package io.legado.app.help.underline

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import io.legado.app.help.underline.UnderlineStore.Kind

/**
 * 下划线绘制工具。
 * 由 ContentTextView 在绘制完文字后调用；传入的是「合并后的连续区间」，
 * 保证虚线/点线/波浪线的相位在整行上连续，而不是每个字各画一段。
 */
object UnderlineDraw {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun draw(
        canvas: Canvas,
        x0: Float,
        x1: Float,
        y: Float,
        kind: Kind,
        color: Int,
        densityPx: Float
    ) {
        if (x1 <= x0 || kind == Kind.NONE) return
        strokePaint.color = color
        dotPaint.color = color
        val unit = densityPx.coerceIn(4f, 40f)

        when (kind) {
            Kind.SOLID -> canvas.drawLine(x0, y, x1, y, strokePaint)

            Kind.DASHED -> {
                val dash = unit * 0.62f
                val gap = unit * 0.38f
                var x = x0
                while (x < x1) {
                    canvas.drawLine(x, y, minOf(x + dash, x1), y, strokePaint)
                    x += dash + gap
                }
            }

            Kind.DOTTED -> {
                val step = (unit * 0.5f).coerceAtLeast(4f)
                val r = strokePaint.strokeWidth * 0.9f
                var x = x0
                while (x <= x1) {
                    canvas.drawCircle(x, y, r, dotPaint)
                    x += step
                }
            }

            Kind.DOUBLE -> {
                val gap = (strokePaint.strokeWidth * 1.8f).coerceAtLeast(2f)
                canvas.drawLine(x0, y, x1, y, strokePaint)
                canvas.drawLine(x0, y + gap, x1, y + gap, strokePaint)
            }

            Kind.WAVY -> {
                val waveLen = unit.coerceIn(8f, 18f)
                val amp = waveLen * 0.28f
                val path = Path()
                path.moveTo(x0, y)
                var x = x0
                while (x < x1) {
                    val next = minOf(x + waveLen, x1)
                    path.quadTo((x + next) / 2f, y - amp, next, y)
                    x = next
                }
                canvas.drawPath(path, strokePaint)
            }

            Kind.NONE -> Unit
        }
    }
}

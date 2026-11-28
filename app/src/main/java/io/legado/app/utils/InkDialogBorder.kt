package io.legado.app.utils

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Window

/**
 * 墨水屏弹窗描边：白底内容之外补一圈 2dp 纯黑边框，
 * 避免弹窗白底和阅读页白底连成一片、看不出边界。
 *
 * 必须在各 DialogFragment 自己的 onStart()（可能重置 window 背景/decor padding）之后调用。
 */
fun Window.applyInkDialogBorder() {
    val border = 2.dp
    setBackgroundDrawable(GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.TRANSPARENT)
        setStroke(border, Color.BLACK)
    })
    // 内容内缩，防止子视图把窗口背景上的描边盖住
    decorView.setPadding(border, border, border, border)
}

package io.legado.app.ui.book.read.config

import android.content.Context
import android.text.InputType
import android.widget.EditText
import io.legado.app.R
import io.legado.app.help.ReadBookConfig
import io.legado.app.lib.dialogs.AndroidAlertBuilder
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.utils.dp
import io.legado.app.utils.toastOnUi

/**
 * 翻页刷新（每翻 N 页做一次硬件 GC 清残影）。
 * 阅读菜单里的「刷新」悬浮按钮与「界面」设置里的入口共用这一套档位控件。
 */
object PageRefreshConfig {

    val levels = listOf(5, 10, 20, 30)

    /** 当前档位的显示文案 */
    fun summary(context: Context): String {
        val count = ReadBookConfig.pageRefreshCount
        return if (count <= 0) {
            context.getString(R.string.ink_page_refresh_off)
        } else {
            context.getString(R.string.ink_page_refresh_on, count)
        }
    }

    /** 打开档位选择：关 / 5 / 10 / 20 / 30 / 自定义 */
    fun showSelector(context: Context) {
        val labels = ArrayList<CharSequence>()
        labels.add(context.getString(R.string.ink_off))
        levels.forEach { labels.add("$it") }
        labels.add(context.getString(R.string.ink_page_refresh_custom))
        context.selector(
            title = context.getString(R.string.ink_page_refresh_title),
            items = labels
        ) { _, index ->
            when {
                index == 0 -> setCount(context, 0)
                index <= levels.size -> setCount(context, levels[index - 1])
                else -> showCustomInput(context)
            }
        }
    }

    /** 长按开关：关闭 / 恢复到上次档位 */
    fun toggle(context: Context) {
        val current = ReadBookConfig.pageRefreshCount
        if (current > 0) {
            ReadBookConfig.pageRefreshLastCount = current
            ReadBookConfig.pageRefreshCount = 0
            context.toastOnUi(context.getString(R.string.ink_page_refresh_off))
        } else {
            val last = ReadBookConfig.pageRefreshLastCount.coerceIn(1, 999)
            ReadBookConfig.pageRefreshCount = last
            context.toastOnUi(context.getString(R.string.ink_page_refresh_on, last))
        }
    }

    private fun setCount(context: Context, count: Int) {
        ReadBookConfig.pageRefreshCount = count
        if (count > 0) ReadBookConfig.pageRefreshLastCount = count
        context.toastOnUi(summary(context))
    }

    private fun showCustomInput(context: Context) {
        val editText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine()
            hint = "1 ~ 999"
            setText(
                ReadBookConfig.pageRefreshCount
                    .takeIf { it > 0 }?.toString()
                    ?: ReadBookConfig.pageRefreshLastCount.toString()
            )
            setSelection(text.length)
            setBackgroundColor(context.bottomBackground)
            setPadding(16.dp, 12.dp, 16.dp, 12.dp)
        }
        AndroidAlertBuilder(context).apply {
            setTitle(context.getString(R.string.ink_page_refresh_custom_title))
            setCustomView(editText)
            positiveButton(R.string.ok) {
                val value = editText.text.toString().trim().toIntOrNull()
                if (value == null || value < 1 || value > 999) {
                    context.toastOnUi(R.string.ink_page_refresh_invalid)
                } else {
                    setCount(context, value)
                }
            }
            negativeButton(R.string.cancel)
        }.show()
    }
}

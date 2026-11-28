package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogInkThemeLayoutBinding
import io.legado.app.help.ReadBookConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding
import timber.log.Timber
import java.io.File

/**
 * 墨水屏：主题与布局设置面板。
 * - 多套布局：切换 / 新建 / 删除
 * - 自定义背景：唯一纯白背景（纯白 / assets/bg/纯白.jpg）/ 相册图片 / 重置
 * 文字始终保持纯黑（ReadBookConfig.textColor），不受背景影响。
 */
class ThemeLayoutDialog : BaseDialogFragment(R.layout.dialog_ink_theme_layout) {

    private val binding by viewBinding(DialogInkThemeLayoutBinding::bind)

    // 墨水屏只预制一个白色背景：assets/bg/纯白.jpg（白底 + 纯黑“纯白”字样）
    private val builtinImages = listOf("纯白.jpg")
    private val colorNames = listOf("纯白")
    private val colorValues = listOf("#FFFFFF")

    private val selectImage = registerForActivityResult(SelectImageContract()) { result ->
        readUri(result?.second) { name, bytes ->
            try {
                // 注意：这里必须是“目录”。旧代码误用 createFileIfNotExist 建了一个名为 bg 的
                // 空文件，导致后续 File(bg, name).writeBytes() 必然失败（父路径不是目录），
                // 表现就是“选了图片但背景没变化”。顺带清理历史遗留的同名空文件。
                val dir = File(FileUtils.getPath(requireContext().externalFiles, "bg"))
                if (dir.exists() && !dir.isDirectory) dir.delete()
                if (!dir.exists()) dir.mkdirs()
                val safeName = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    .ifBlank { "bg_${System.currentTimeMillis()}.jpg" }
                val file = File(dir, safeName)
                file.writeBytes(bytes)
                applyBg(2, file.absolutePath)
                context?.toastOnUi("背景已应用: $safeName")
            } catch (e: Exception) {
                Timber.e(e)
                context?.toastOnUi("背景保存失败: ${e.localizedMessage}")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 与「信息」「边距」一致：90% 宽 + 按内容高度自适应，垂直居中
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        upView()
        binding.btnSwitchLayout.setOnClickListener { switchLayout() }
        binding.btnAddLayout.setOnClickListener {
            ReadBookConfig.addStyle()
            upView()
            refresh()
            context?.toastOnUi(R.string.ink_layout_saved)
        }
        binding.btnDeleteLayout.setOnClickListener { deleteLayout() }
        binding.btnBgColor.setOnClickListener {
            context?.selector(getString(R.string.ink_bg_color), colorNames) { _, i ->
                applyBg(0, colorValues[i])
            }
        }
        binding.btnBgImage.setOnClickListener {
            val items = builtinImages + listOf(getString(R.string.ink_bg_pick))
            context?.selector(getString(R.string.ink_bg_image), items) { _, i ->
                if (i < builtinImages.size) applyBg(1, builtinImages[i])
                else selectImage.launch(null)
            }
        }
        binding.btnBgReset.setOnClickListener { applyBg(0, "#FFFFFF") }
    }

    private fun upView() {
        binding.tvCurrentLayout.text =
            "${getString(R.string.ink_layout)}: ${ReadBookConfig.durConfig.name} " +
                    "(${ReadBookConfig.styleSelect + 1}/${ReadBookConfig.configList.size})"
    }

    private fun switchLayout() {
        val names = ReadBookConfig.configList.mapIndexed { i, c -> "${i + 1}. ${c.name}" }
        context?.selector(getString(R.string.ink_layout_switch), names) { _, i ->
            ReadBookConfig.selectStyle(i)
            upView()
            refresh()
        }
    }

    private fun deleteLayout() {
        if (ReadBookConfig.configList.size <= 1) {
            context?.toastOnUi(R.string.ink_layout_min)
            return
        }
        val names = ReadBookConfig.configList.mapIndexed { i, c -> "${i + 1}. ${c.name}" }
        context?.selector(getString(R.string.ink_layout_delete), names) { _, i ->
            if (ReadBookConfig.deleteStyle(i)) {
                upView()
                refresh()
                context?.toastOnUi(R.string.ink_layout_saved)
            }
        }
    }

    private fun applyBg(type: Int, value: String) {
        ReadBookConfig.durConfig.setCurBg(type, value)
        ReadBookConfig.upBg()
        ReadBookConfig.save()
        refresh()
    }

    /** 通知阅读界面刷新背景与排版 */
    private fun refresh() {
        postEvent(EventBus.UP_CONFIG, true)
    }
}

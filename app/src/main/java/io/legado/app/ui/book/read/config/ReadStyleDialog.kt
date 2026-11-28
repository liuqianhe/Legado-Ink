package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogReadBookStyleBinding
import io.legado.app.help.ReadBookConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.widget.font.FontSelectDialog
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding

class ReadStyleDialog : BaseDialogFragment(R.layout.dialog_read_book_style),
    FontSelectDialog.CallBack {

    private val binding by viewBinding(DialogReadBookStyleBinding::bind)
    private val callBack get() = activity as? ReadBookActivity

    override fun onStart() {
        super.onStart()
        dialog?.window?.let {
            it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            it.setBackgroundDrawableResource(R.color.background)
            it.decorView.setPadding(0, 0, 0, 0)
            val attr = it.attributes
            attr.dimAmount = 0.0f
            attr.gravity = Gravity.BOTTOM
            it.attributes = attr
            it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (activity as ReadBookActivity).bottomDialog++
        initView()
        initData()
        initViewEvent()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookConfig.save()
        (activity as ReadBookActivity).bottomDialog--
    }

    private fun initView() = binding.run {
        val bg = requireContext().bottomBackground
        rootView.setBackgroundColor(bg)
        dsbTextSize.valueFormat = {
            (it + 5).toString()
        }
        dsbTextLetterSpacing.valueFormat = {
            ((it - 50) / 100f).toString()
        }
        dsbLineSize.valueFormat = { ((it - 10) / 10f).toString() }
        dsbParagraphSpacing.valueFormat = { (it / 10f).toString() }

    }

    private fun initData() {
        upView()
    }

    private fun initViewEvent() = binding.run {
        chineseConverter.onChanged {
            postEvent(EventBus.UP_CONFIG, true)
        }
        textFontWeightConverter.onChanged {
            postEvent(EventBus.UP_CONFIG, true)
        }
        tvTextFont.setOnClickListener {
            showDialogFragment<FontSelectDialog>()
        }
        tvTextIndent.setOnClickListener {
            context?.selector(
                title = getString(R.string.text_indent),
                items = resources.getStringArray(R.array.indent).toList()
            ) { _, index ->
                ReadBookConfig.paragraphIndent = "　".repeat(index)
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
        // 「信息」「边距」统一作为子面板打开：关掉后回到「界面」面板，
        // 方便在两个面板之间来回对照，不用重新从工具栏进入。
        tvPadding.setOnClickListener {
            PaddingConfigDialog().show(childFragmentManager, "paddingConfigDialog")
        }
        tvTip.setOnClickListener {
            TipConfigDialog().show(childFragmentManager, "tipConfigDialog")
        }
        tvThemeLayout.setOnClickListener {
            ThemeLayoutDialog().show(childFragmentManager, "themeLayoutDialog")
        }
        // 横排 / 竖排
        tvTextVertical.setOnClickListener {
            val vertical = !ReadBookConfig.textVertical
            ReadBookConfig.textVertical = vertical
            // 竖排会改变排版尺寸（宽高互换），必须让 ChapterProvider 重算
            ChapterProvider.upLayout()
            upView()
            postEvent(EventBus.UP_CONFIG, true)
        }
        // 竖排起点：右侧 / 左侧
        tvVerticalStart.setOnClickListener {
            if (!ReadBookConfig.textVertical) {
                context?.toastOnUi(R.string.ink_vertical_start_right)
                return@setOnClickListener
            }
            ReadBookConfig.verticalStartLeft = !ReadBookConfig.verticalStartLeft
            upView()
            postEvent(EventBus.UP_CONFIG, false)
        }
        // 翻页刷新
        tvPageRefresh.setOnClickListener {
            context?.let { PageRefreshConfig.showSelector(it) }
            binding.root.postDelayed({ upView() }, 400L)
        }
        dsbTextSize.onChanged = {
            ReadBookConfig.textSize = it + 5
            postEvent(EventBus.UP_CONFIG, true)
        }
        dsbTextLetterSpacing.onChanged = {
            ReadBookConfig.letterSpacing = (it - 50) / 100f
            postEvent(EventBus.UP_CONFIG, true)
        }
        dsbLineSize.onChanged = {
            ReadBookConfig.lineSpacingExtra = it
            postEvent(EventBus.UP_CONFIG, true)
        }
        dsbParagraphSpacing.onChanged = {
            ReadBookConfig.paragraphSpacing = it
            postEvent(EventBus.UP_CONFIG, true)
        }
    }

    private fun upView() = binding.run {
        textFontWeightConverter.upUi(ReadBookConfig.textBold)
        ReadBookConfig.let {
            dsbTextSize.progress = it.textSize - 5
            dsbTextLetterSpacing.progress = (it.letterSpacing * 100).toInt() + 50
            dsbLineSize.progress = it.lineSpacingExtra
            dsbParagraphSpacing.progress = it.paragraphSpacing
            tvTextVertical.setText(
                if (it.textVertical) R.string.ink_text_vertical else R.string.ink_text_horizontal
            )
            tvVerticalStart.setText(
                if (it.verticalStartLeft) R.string.ink_vertical_start_left
                else R.string.ink_vertical_start_right
            )
            tvVerticalStart.isEnabled = it.textVertical
            tvVerticalStart.alpha = if (it.textVertical) 1f else 0.4f
            tvPageRefresh.text = PageRefreshConfig.summary(requireContext())
        }
    }

    override val curFontPath: String
        get() = ReadBookConfig.textFont

    override fun selectFont(path: String) {
        if (path != ReadBookConfig.textFont) {
            ReadBookConfig.textFont = path
            postEvent(EventBus.UP_CONFIG, true)
        }
    }

}

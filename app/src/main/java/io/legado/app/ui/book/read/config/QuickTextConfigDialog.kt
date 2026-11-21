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
import io.legado.app.databinding.DialogQuickTextBinding
import io.legado.app.help.ReadBookConfig
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.utils.postEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 快捷排版悬浮窗：长按阅读菜单里的「A」按钮弹出。
 * 只放最常调的四个滑杆（字号 / 字距 / 行距 / 段距），贴着底部弹出、不压黑背景，
 * 方便一边看正文一边拖。
 */
class QuickTextConfigDialog : BaseDialogFragment(R.layout.dialog_quick_text) {

    private val binding by viewBinding(DialogQuickTextBinding::bind)

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
            it.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? ReadBookActivity)?.let { it.bottomDialog++ }
        initView()
        initData()
        initEvent()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookConfig.save()
        (activity as? ReadBookActivity)?.let { it.bottomDialog-- }
    }

    private fun initView() = binding.run {
        rootView.setBackgroundColor(
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.background)
        )
        dsbTextSize.valueFormat = { (it + 5).toString() }
        dsbTextLetterSpacing.valueFormat = { ((it - 50) / 100f).toString() }
        dsbLineSize.valueFormat = { ((it - 10) / 10f).toString() }
        dsbParagraphSpacing.valueFormat = { (it / 10f).toString() }
    }

    private fun initData() = binding.run {
        ReadBookConfig.let {
            dsbTextSize.progress = it.textSize - 5
            dsbTextLetterSpacing.progress = (it.letterSpacing * 100).toInt() + 50
            dsbLineSize.progress = it.lineSpacingExtra
            dsbParagraphSpacing.progress = it.paragraphSpacing
        }
    }

    private fun initEvent() = binding.run {
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
}

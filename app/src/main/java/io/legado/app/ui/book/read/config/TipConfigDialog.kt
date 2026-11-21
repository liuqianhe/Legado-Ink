package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogTipConfigBinding
import io.legado.app.help.ReadBookConfig
import io.legado.app.help.ReadTipConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding


class TipConfigDialog : BaseDialogFragment(R.layout.dialog_tip_config) {

    private val binding by viewBinding(DialogTipConfigBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        initEvent()
    }

    private fun initView() = binding.run {
        rgTitleMode.checkByIndex(ReadBookConfig.titleMode)
        dsbTitleSize.progress = ReadBookConfig.titleSize
        dsbTitleTop.progress = ReadBookConfig.titleTopSpacing
        dsbTitleBottom.progress = ReadBookConfig.titleBottomSpacing

        tvHeaderShow.text = ReadTipConfig.getHeaderModes(requireContext())[ReadTipConfig.headerMode]
        tvFooterShow.text = ReadTipConfig.getFooterModes(requireContext())[ReadTipConfig.footerMode]

        tvHeaderLeft.text = ReadTipConfig.tipHeaderLeftStr
        tvHeaderMiddle.text = ReadTipConfig.tipHeaderMiddleStr
        tvHeaderRight.text = ReadTipConfig.tipHeaderRightStr
        tvFooterLeft.text = ReadTipConfig.tipFooterLeftStr
        tvFooterMiddle.text = ReadTipConfig.tipFooterMiddleStr
        tvFooterRight.text = ReadTipConfig.tipFooterRightStr

        // 页眉/页脚字号：progress = 字号 - 最小值(6)，范围 6~24sp
        dsbHeaderFontSize.valueFormat = { (it + ReadBookConfig.minTipFontSize).toString() }
        dsbFooterFontSize.valueFormat = { (it + ReadBookConfig.minTipFontSize).toString() }
        dsbHeaderFontSize.progress = ReadBookConfig.headerFontSize - ReadBookConfig.minTipFontSize
        dsbFooterFontSize.progress = ReadBookConfig.footerFontSize - ReadBookConfig.minTipFontSize

        // 墨水屏：页眉/页脚分割线开关与粗细
        tvHeaderLine.text = dividerText(ReadBookConfig.showHeaderLine)
        tvFooterLine.text = dividerText(ReadBookConfig.showFooterLine)
        dsbHeaderLineSize.valueFormat = { (it + ReadBookConfig.minDividerSize).toString() }
        dsbFooterLineSize.valueFormat = { (it + ReadBookConfig.minDividerSize).toString() }
        dsbHeaderLineSize.progress =
            ReadBookConfig.headerLineSize - ReadBookConfig.minDividerSize
        dsbFooterLineSize.progress =
            ReadBookConfig.footerLineSize - ReadBookConfig.minDividerSize

    }

    private fun dividerText(show: Boolean): String =
        getString(if (show) R.string.show else R.string.hide)

    private fun initEvent() = binding.run {
        rgTitleMode.setOnCheckedChangeListener { _, checkedId ->
            ReadBookConfig.titleMode = rgTitleMode.getIndexById(checkedId)
            postEvent(EventBus.UP_CONFIG, true)
        }
        dsbTitleSize.onChanged = {
            ReadBookConfig.titleSize = it
            postEvent(EventBus.UP_CONFIG, true)
        }
        dsbTitleTop.onChanged = {
            ReadBookConfig.titleTopSpacing = it
            postEvent(EventBus.UP_CONFIG, true)
        }
        dsbTitleBottom.onChanged = {
            ReadBookConfig.titleBottomSpacing = it
            postEvent(EventBus.UP_CONFIG, true)
        }
        dsbHeaderFontSize.onChanged = {
            ReadBookConfig.headerFontSize = it + ReadBookConfig.minTipFontSize
            postEvent(EventBus.UP_CONFIG, true)
        }
        dsbFooterFontSize.onChanged = {
            ReadBookConfig.footerFontSize = it + ReadBookConfig.minTipFontSize
            postEvent(EventBus.UP_CONFIG, true)
        }
        dsbHeaderLineSize.onChanged = {
            ReadBookConfig.headerLineSize = it + ReadBookConfig.minDividerSize
            postEvent(EventBus.UP_CONFIG, true)
        }
        dsbFooterLineSize.onChanged = {
            ReadBookConfig.footerLineSize = it + ReadBookConfig.minDividerSize
            postEvent(EventBus.UP_CONFIG, true)
        }
        val dividerChoices = listOf(getString(R.string.show), getString(R.string.hide))
        llHeaderLine.setOnClickListener {
            context?.selector(items = dividerChoices) { _, i ->
                ReadBookConfig.showHeaderLine = i == 0
                tvHeaderLine.text = dividerText(ReadBookConfig.showHeaderLine)
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
        llFooterLine.setOnClickListener {
            context?.selector(items = dividerChoices) { _, i ->
                ReadBookConfig.showFooterLine = i == 0
                tvFooterLine.text = dividerText(ReadBookConfig.showFooterLine)
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
        llHeaderShow.setOnClickListener {
            val headerModes = ReadTipConfig.getHeaderModes(requireContext())
            context?.selector(items = headerModes.values.toList()) { _, i ->
                ReadTipConfig.headerMode = headerModes.keys.toList()[i]
                tvHeaderShow.text = headerModes[ReadTipConfig.headerMode]
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
        llFooterShow.setOnClickListener {
            val footerModes = ReadTipConfig.getFooterModes(requireContext())
            context?.selector(items = footerModes.values.toList()) { _, i ->
                ReadTipConfig.footerMode = footerModes.keys.toList()[i]
                tvFooterShow.text = footerModes[ReadTipConfig.footerMode]
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
        llHeaderLeft.setOnClickListener {
            context?.selector(items = ReadTipConfig.tips) { _, i ->
                clearRepeat(i)
                ReadTipConfig.tipHeaderLeft = i
                tvHeaderLeft.text = ReadTipConfig.tips[i]
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
        llHeaderMiddle.setOnClickListener {
            context?.selector(items = ReadTipConfig.tips) { _, i ->
                clearRepeat(i)
                ReadTipConfig.tipHeaderMiddle = i
                tvHeaderMiddle.text = ReadTipConfig.tips[i]
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
        llHeaderRight.setOnClickListener {
            context?.selector(items = ReadTipConfig.tips) { _, i ->
                clearRepeat(i)
                ReadTipConfig.tipHeaderRight = i
                tvHeaderRight.text = ReadTipConfig.tips[i]
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
        llFooterLeft.setOnClickListener {
            context?.selector(items = ReadTipConfig.tips) { _, i ->
                clearRepeat(i)
                ReadTipConfig.tipFooterLeft = i
                tvFooterLeft.text = ReadTipConfig.tips[i]
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
        llFooterMiddle.setOnClickListener {
            context?.selector(items = ReadTipConfig.tips) { _, i ->
                clearRepeat(i)
                ReadTipConfig.tipFooterMiddle = i
                tvFooterMiddle.text = ReadTipConfig.tips[i]
                postEvent(EventBus.UP_CONFIG, true)
            }
        }
        llFooterRight.setOnClickListener {
            context?.selector(items = ReadTipConfig.tips) { _, i ->
                clearRepeat(i)
                ReadTipConfig.tipFooterRight = i
                tvFooterRight.text = ReadTipConfig.tips[i]
                postEvent(EventBus.UP_CONFIG, true)
            }
        }

    }

    private fun clearRepeat(repeat: Int) = ReadTipConfig.apply {
        if (repeat != none) {
            if (tipHeaderLeft == repeat) {
                tipHeaderLeft = none
                binding.tvHeaderLeft.text = tips[none]
            }
            if (tipHeaderMiddle == repeat) {
                tipHeaderMiddle = none
                binding.tvHeaderMiddle.text = tips[none]
            }
            if (tipHeaderRight == repeat) {
                tipHeaderRight = none
                binding.tvHeaderRight.text = tips[none]
            }
            if (tipFooterLeft == repeat) {
                tipFooterLeft = none
                binding.tvFooterLeft.text = tips[none]
            }
            if (tipFooterMiddle == repeat) {
                tipFooterMiddle = none
                binding.tvFooterMiddle.text = tips[none]
            }
            if (tipFooterRight == repeat) {
                tipFooterRight = none
                binding.tvFooterRight.text = tips[none]
            }
        }
    }

}
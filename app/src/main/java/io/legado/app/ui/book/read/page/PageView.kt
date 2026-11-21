package io.legado.app.ui.book.read.page

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.updateLayoutParams
import io.legado.app.R
import io.legado.app.constant.AppConst.timeFormat
import io.legado.app.data.entities.Bookmark
import io.legado.app.databinding.ViewBookPageBinding
import io.legado.app.help.ReadBookConfig
import io.legado.app.help.ReadTipConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.widget.BatteryView
import io.legado.app.utils.*
import java.util.*

/**
 * 阅读界面
 */
class PageView(context: Context) : FrameLayout(context) {

    private val binding = ViewBookPageBinding.inflate(LayoutInflater.from(context), this, true)
    private val readBookActivity get() = activity as? ReadBookActivity
    private var battery = 100
    private var tvTitle: BatteryView? = null
    private var tvTime: BatteryView? = null
    private var tvBattery: BatteryView? = null
    private var tvPage: BatteryView? = null
    private var tvTotalProgress: BatteryView? = null
    private var tvPageAndTotal: BatteryView? = null
    private var tvBookName: BatteryView? = null
    private var tvTimeBattery: BatteryView? = null

    val headerHeight: Int
        get() {
            val h1 = if (ReadBookConfig.hideStatusBar) 0 else context.statusBarHeight
            val h2 = if (binding.llHeader.isGone) 0 else binding.llHeader.height
            // 上限保护：页眉(含状态栏)不得超过屏幕高度的 12%，避免字号过大遮住正文
            val maxH = (context.resources.displayMetrics.heightPixels * 0.12f).toInt()
            return (h1 + h2).coerceAtMost(maxH)
        }

    init {
        if (!isInEditMode) {
            //设置背景防止切换背景时文字重叠
            setBackgroundColor(context.getCompatColor(R.color.background))
            upStyle()
        }
        binding.contentTextView.upView = {
            setProgress(it)
        }
    }

    fun upStyle() = binding.run {
        upTipStyle()
        ReadBookConfig.let {
            val tipColor = it.textColor
            bvHeaderLeft.setColor(tipColor)
            tvHeaderLeft.setColor(tipColor)
            tvHeaderMiddle.setColor(tipColor)
            tvHeaderRight.setColor(tipColor)
            bvFooterLeft.setColor(tipColor)
            tvFooterLeft.setColor(tipColor)
            tvFooterMiddle.setColor(tipColor)
            tvFooterRight.setColor(tipColor)
            upStatusBar()
            llHeader.setPadding(
                it.headerPaddingLeft.dp,
                it.headerPaddingTop.dp,
                it.headerPaddingRight.dp,
                it.headerPaddingBottom.dp
            )
            llFooter.setPadding(
                it.footerPaddingLeft.dp,
                it.footerPaddingTop.dp,
                it.footerPaddingRight.dp,
                it.footerPaddingBottom.dp
            )
            // 墨水屏：分割线恒为纯黑，粗细由「信息」里的滑杆控制
            vwTopDivider.setBackgroundColor(Color.BLACK)
            vwBottomDivider.setBackgroundColor(Color.BLACK)
            vwTopDivider.updateLayoutParams { height = ReadBookConfig.headerLineSize.dp }
            vwBottomDivider.updateLayoutParams { height = ReadBookConfig.footerLineSize.dp }
            vwTopDivider.visible(it.showHeaderLine)
            vwBottomDivider.visible(it.showFooterLine)
        }
        contentTextView.upVisibleRect()
        upTime()
        upBattery(battery)
    }

    /**
     * 显示状态栏时隐藏header
     */
    fun upStatusBar() = with(binding.vwStatusBar) {
        setPadding(paddingLeft, context.statusBarHeight, paddingRight, paddingBottom)
        isGone = ReadBookConfig.hideStatusBar || readBookActivity?.isInMultiWindow == true
    }

    private fun upTipStyle() = binding.run {
        ReadTipConfig.apply {
            tvHeaderLeft.isInvisible = tipHeaderLeft != chapterTitle
            bvHeaderLeft.isInvisible =
                tipHeaderLeft == none || !tvHeaderLeft.isInvisible
            tvHeaderRight.isGone = tipHeaderRight == none
            tvHeaderMiddle.isGone = tipHeaderMiddle == none
            tvFooterLeft.isInvisible = tipFooterLeft != chapterTitle
            bvFooterLeft.isInvisible =
                tipFooterLeft == none || !tvFooterLeft.isInvisible
            tvFooterRight.isGone = tipFooterRight == none
            tvFooterMiddle.isGone = tipFooterMiddle == none
            llHeader.isGone = when (headerMode) {
                1 -> false
                2 -> true
                else -> !ReadBookConfig.hideStatusBar
            }
            llFooter.isGone = when (footerMode) {
                1 -> true
                else -> false
            }
        }
        tvTitle = getTipView(ReadTipConfig.chapterTitle)
        tvTitle?.apply {
            isBattery = false
            typeface = ChapterProvider.typeface
            textSize = 12f
        }
        tvTime = getTipView(ReadTipConfig.time)
        tvTime?.apply {
            isBattery = false
            typeface = ChapterProvider.typeface
            textSize = 12f
        }
        tvBattery = getTipView(ReadTipConfig.battery)
        tvBattery?.apply {
            isBattery = true
            textSize = 10f
        }
        tvPage = getTipView(ReadTipConfig.page)
        tvPage?.apply {
            isBattery = false
            typeface = ChapterProvider.typeface
            textSize = 12f
        }
        tvTotalProgress = getTipView(ReadTipConfig.totalProgress)
        tvTotalProgress?.apply {
            isBattery = false
            typeface = ChapterProvider.typeface
            textSize = 12f
        }
        tvPageAndTotal = getTipView(ReadTipConfig.pageAndTotal)
        tvPageAndTotal?.apply {
            isBattery = false
            typeface = ChapterProvider.typeface
            textSize = 12f
        }
        tvBookName = getTipView(ReadTipConfig.bookName)
        tvBookName?.apply {
            isBattery = false
            typeface = ChapterProvider.typeface
            textSize = 12f
        }
        tvTimeBattery = getTipView(ReadTipConfig.timeBattery)
        tvTimeBattery?.apply {
            isBattery = false
            typeface = ChapterProvider.typeface
            textSize = 12f
        }
        // 页眉/页脚字号独立可调，覆盖上面的固定值
        applyTipFontSize(binding.llHeader, ReadBookConfig.headerFontSize)
        applyTipFontSize(binding.llFooter, ReadBookConfig.footerFontSize)
    }

    /**
     * 递归给页眉/页脚容器内的文字视图设置字号；电池图标略小一档。
     * 字号已在 ReadBookConfig 里 coerceIn(6,24)，这里再兜底，避免过大遮挡正文。
     */
    private fun applyTipFontSize(container: android.view.ViewGroup, sizeSp: Int) {
        val safeSize = sizeSp.coerceIn(ReadBookConfig.minTipFontSize, ReadBookConfig.maxTipFontSize)
        fun walk(v: android.view.View) {
            if (v is BatteryView) {
                v.textSize = if (v.isBattery) {
                    (safeSize - 2).coerceAtLeast(ReadBookConfig.minTipFontSize).toFloat()
                } else {
                    safeSize.toFloat()
                }
            }
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
        }
        for (i in 0 until container.childCount) walk(container.getChildAt(i))
    }

    private fun getTipView(tip: Int): BatteryView? = binding.run {
        return when (tip) {
            ReadTipConfig.tipHeaderLeft ->
                if (tip == ReadTipConfig.chapterTitle) tvHeaderLeft else bvHeaderLeft
            ReadTipConfig.tipHeaderMiddle -> tvHeaderMiddle
            ReadTipConfig.tipHeaderRight -> tvHeaderRight
            ReadTipConfig.tipFooterLeft ->
                if (tip == ReadTipConfig.chapterTitle) tvFooterLeft else bvFooterLeft
            ReadTipConfig.tipFooterMiddle -> tvFooterMiddle
            ReadTipConfig.tipFooterRight -> tvFooterRight
            else -> null
        }
    }

    fun setBg(bg: Drawable?) {
        binding.pagePanel.background = bg
    }

    fun upTime() {
        tvTime?.text = timeFormat.format(Date(System.currentTimeMillis()))
        upTimeBattery()
    }

    fun upBattery(battery: Int) {
        this.battery = battery
        tvBattery?.setBattery(battery)
        upTimeBattery()
    }

    @SuppressLint("SetTextI18n")
    private fun upTimeBattery() {
        tvTimeBattery?.let {
            val time = timeFormat.format(Date(System.currentTimeMillis()))
            it.text = "$time $battery%"
        }
    }

    fun setContent(textPage: TextPage, resetPageOffset: Boolean = true) {
        setProgress(textPage)
        if (resetPageOffset) {
            resetPageOffset()
        }
        binding.contentTextView.setContent(textPage)
    }

    fun prepareAdjacentPageText() {
        binding.contentTextView.prepareAdjacentPage()
    }

    fun setContentDescription(content: String) {
        binding.contentTextView.contentDescription = content
    }

    fun resetPageOffset() {
        binding.contentTextView.resetPageOffset()
    }

    @SuppressLint("SetTextI18n")
    fun setProgress(textPage: TextPage) = textPage.apply {
        tvBookName?.text = ReadBook.book?.name
        tvTitle?.text = textPage.title
        tvPage?.text = "${index.plus(1)}/$pageSize"
        tvTotalProgress?.text = readProgress
        tvPageAndTotal?.text = "${index.plus(1)}/$pageSize  $readProgress"
    }

    fun scroll(offset: Int) {
        binding.contentTextView.scroll(offset)
    }

    fun upSelectAble(selectAble: Boolean) {
        binding.contentTextView.selectAble = selectAble
    }

    fun selectText(
        x: Float, y: Float,
        select: (relativePage: Int, lineIndex: Int, charIndex: Int) -> Unit,
    ) {
        return binding.contentTextView.selectText(x, y - headerHeight, select)
    }

    fun selectStartMove(x: Float, y: Float) {
        binding.contentTextView.selectStartMove(x, y - headerHeight)
    }

    fun selectStartMoveIndex(relativePage: Int, lineIndex: Int, charIndex: Int) {
        binding.contentTextView.selectStartMoveIndex(relativePage, lineIndex, charIndex)
    }

    fun selectEndMove(x: Float, y: Float) {
        binding.contentTextView.selectEndMove(x, y - headerHeight)
    }

    fun selectEndMoveIndex(relativePage: Int, lineIndex: Int, charIndex: Int) {
        binding.contentTextView.selectEndMoveIndex(relativePage, lineIndex, charIndex)
    }

    fun cancelSelect() {
        binding.contentTextView.cancelSelect()
    }

    fun createBookmark(): Bookmark? {
        return binding.contentTextView.createBookmark()
    }

    /** 墨水屏下划线：转发到 ContentTextView */
    val selectedText: String get() = binding.contentTextView.selectedText

    val textPage get() = binding.contentTextView.textPage
}

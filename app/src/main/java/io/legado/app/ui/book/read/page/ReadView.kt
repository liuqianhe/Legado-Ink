package io.legado.app.ui.book.read.page

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.widget.FrameLayout
import io.legado.app.help.AppConfig
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.InkSwipeConfig
import io.legado.app.help.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.api.DataSource
import io.legado.app.ui.book.read.page.delegate.*
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.TextPageFactory
import io.legado.app.utils.activity
import io.legado.app.utils.dp
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.postEvent
import io.legado.app.utils.sp
import java.text.BreakIterator
import java.util.*
import kotlin.math.abs


class ReadView(context: Context, attrs: AttributeSet) :
    FrameLayout(context, attrs),
    DataSource {

    val callBack: CallBack get() = activity as CallBack
    var pageFactory: TextPageFactory = TextPageFactory(this)
    var pageDelegate: PageDelegate? = null
        private set(value) {
            field?.onDestroy()
            field = null
            field = value
            upContent()
        }
    var isScroll = false
    val prevPage by lazy { PageView(context) }
    val curPage by lazy { PageView(context) }
    val nextPage by lazy { PageView(context) }
    private var pressDown = false
    private var isMove = false
    private var pageTurnStart = 0L
    private var lastTurnDirection = PageDirection.NEXT

    // ===== 侧边上下滑调节排版（右侧字号 / 左侧字重）=====
    // 候选：按下点落在屏幕左侧或右侧区域；激活：确认是纵向滑动后接管手势。
    private var swipeCandidate = false
    private var swipeActive = false
    private var swipeShowing = false
    /** true=调字重（左侧），false=调字号（右侧） */
    private var swipeOnWeight = false
    private var swipeStartValue = 0
    private var swipeValue = 0
    private var swipeLastY = 0f
    private var swipeAccum = 0f
    private var swipeHideRunnable: Runnable? = null
    private val swipeStep = 36f.dp
    private val swipeMinFontSize = 8
    private val swipeMaxFontSize = 50
    private val swipeMinWeight = 0
    private val swipeMaxWeight = 10
    private val swipePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val swipeRect = RectF()
    /** 贴边感应区宽度：用户可调，单侧最多 30% 屏宽，左右不可能铺满屏幕 */
    private val swipeEdgePx: Float
        get() = InkSwipeConfig.edgeWidth.dp.toFloat()
            .coerceAtMost(width * InkSwipeConfig.maxWidthRatio)
    private val swipeEnabledRight: Boolean
        get() = context.getPrefBoolean(PreferKey.inkRightSwipeFontSize, true)
    private val swipeEnabledLeft: Boolean
        get() = context.getPrefBoolean(PreferKey.inkLeftSwipeFontWeight, true)

    //起始点
    var startX: Float = 0f
    var startY: Float = 0f

    //上一个触碰点
    var lastX: Float = 0f
    var lastY: Float = 0f

    //触碰点
    var touchX: Float = 0f
    var touchY: Float = 0f

    //是否停止动画动作
    var isAbortAnim = false

    //长按
    private var longPressed = false
    private val longPressTimeout = 600L
    private val longPressRunnable = Runnable {
        longPressed = true
        onLongPress()
    }
    var isTextSelected = false
    private var pressOnTextSelected = false
    private var firstRelativePage = 0
    private var firstLineIndex: Int = 0
    private var firstCharIndex: Int = 0

    val slopSquare by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    private val tlRect = RectF()
    private val tcRect = RectF()
    private val trRect = RectF()
    private val mlRect = RectF()
    private val mcRect = RectF()
    private val mrRect = RectF()
    private val blRect = RectF()
    private val bcRect = RectF()
    private val brRect = RectF()
    private val boundary by lazy { BreakIterator.getWordInstance(Locale.getDefault()) }

    init {
        addView(nextPage)
        addView(curPage)
        addView(prevPage)
        if (!isInEditMode) {
            upBg()
            setWillNotDraw(false)
            upPageAnim()
        }
        setRect9x()
    }

    fun setRect9x() {
        val edge = if (AppConfig.fullScreenGesturesSupport) 200f else 0f
        tlRect.set(0f + edge, 0f, width * 0.33f, height * 0.33f)
        tcRect.set(width * 0.33f, 0f, width * 0.66f, height * 0.33f)
        trRect.set(width * 0.36f, 0f, width - 0f - edge, height * 0.33f)
        mlRect.set(0f + edge, height * 0.33f, width * 0.33f, height * 0.66f)
        mcRect.set(width * 0.33f, height * 0.33f, width * 0.66f, height * 0.66f)
        mrRect.set(width * 0.66f, height * 0.33f, width - 0f - edge, height * 0.66f)
        blRect.set(0f + edge, height * 0.66f, width * 0.33f, height - 10f - edge)
        bcRect.set(width * 0.33f, height * 0.66f, width * 0.66f, height - 0f - edge)
        brRect.set(width * 0.66f, height * 0.66f, width - 0f - edge, height - 0f - edge)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setRect9x()
        prevPage.x = -w.toFloat()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val drawStarted = SystemClock.uptimeMillis()
        if (BuildConfig.DEBUG && pageTurnStart != 0L) {
            Log.d("PagePerf", "input-to-draw-start=${drawStarted - pageTurnStart}ms")
        }
        if (pageDelegate is NoAnimPageDelegate && !isScroll) {
            // The next page is stacked behind the current page. Drawing it is wasted
            // work on e-ink devices when there is no page-turn animation.
            drawChild(canvas, curPage, drawingTime)
        } else {
            super.dispatchDraw(canvas)
        }
        if (swipeShowing) {
            drawSwipeIndicator(canvas)
        }
        if (BuildConfig.DEBUG && pageTurnStart != 0L) {
            Log.d("PagePerf", "draw-duration=${SystemClock.uptimeMillis() - drawStarted}ms")
            pageTurnStart = 0L
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return true
    }

    /**
     * 触摸事件
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        callBack.screenOffTimerStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets =
                this.rootWindowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.mandatorySystemGestures())
            val height = activity?.windowManager?.currentWindowMetrics?.bounds?.height()
            if (height != null) {
                if (event.y > height.minus(insets.bottom)) {
                    return true
                }
            }
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isTextSelected) {
                    curPage.cancelSelect()
                    isTextSelected = false
                    pressOnTextSelected = true
                } else {
                    pressOnTextSelected = false
                }
                longPressed = false
                postDelayed(longPressRunnable, longPressTimeout)
                pressDown = true
                isMove = false
                pageDelegate?.onTouch(event)
                setStartPoint(event.x, event.y)
                // 侧边上下滑：右侧调字号、左侧调字重；只有贴边竖条内才算候选
                val edge = swipeEdgePx
                val inRightEdge = event.x > width - edge
                val inLeftEdge = event.x < edge
                swipeCandidate = !pressOnTextSelected && !isTextSelected &&
                        ((inRightEdge && swipeEnabledRight) || (inLeftEdge && swipeEnabledLeft))
                if (swipeCandidate) {
                    swipeOnWeight = inLeftEdge && swipeEnabledLeft
                    swipeStartValue = if (swipeOnWeight) ReadBookConfig.textBold
                    else ReadBookConfig.textSize
                    swipeValue = swipeStartValue
                    swipeLastY = event.y
                    swipeAccum = 0f
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (swipeCandidate && !isTextSelected) {
                    val dx = event.x - startX
                    val dy = event.y - startY
                    if (!swipeActive && abs(dy) > slopSquare && abs(dy) > abs(dx) * 1.5f) {
                        // 确认是纵向滑动：接管手势，取消长按与翻页判定
                        swipeActive = true
                        swipeShowing = true
                        isMove = true
                        longPressed = true
                        removeCallbacks(longPressRunnable)
                        swipeLastY = event.y
                        swipeAccum = 0f
                        invalidate()
                    }
                    if (swipeActive) {
                        // 上滑变大、下滑变小
                        swipeAccum += swipeLastY - event.y
                        swipeLastY = event.y
                        val steps = (swipeAccum / swipeStep).toInt()
                        if (steps != 0) {
                            swipeAccum -= steps * swipeStep
                            swipeValue = if (swipeOnWeight) {
                                (swipeValue + steps).coerceIn(swipeMinWeight, swipeMaxWeight)
                            } else {
                                (swipeValue + steps).coerceIn(swipeMinFontSize, swipeMaxFontSize)
                            }
                            invalidate()
                        }
                        return true
                    }
                }
                if (!isMove) {
                    isMove =
                        abs(startX - event.x) > slopSquare || abs(startY - event.y) > slopSquare
                }
                if (isMove) {
                    longPressed = false
                    removeCallbacks(longPressRunnable)
                    if (isTextSelected) {
                        selectText(event.x, event.y)
                    } else {
                        pageDelegate?.onTouch(event)
                    }
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                swipeCandidate = false
                if (swipeActive) {
                    swipeActive = false
                    pressDown = false
                    applySwipeValue()
                    return true
                }
                if (!pressDown) return true
                pressDown = false
                if (!isMove) {
                    if (!longPressed && !pressOnTextSelected) {
                        onSingleTapUp()
                        return true
                    }
                }
                if (isTextSelected) {
                    callBack.showTextActionMenu()
                } else if (isMove) {
                    pageDelegate?.onTouch(event)
                }
                pressOnTextSelected = false
            }
        }
        return true
    }

    /**
     * 手势结束时才真正应用：墨水屏重排一次要几百毫秒，
     * 拖动过程中只在侧边显示「字号/字重 N」指示，松手后统一重排。
     */
    private fun applySwipeValue() {
        val changed = swipeValue != swipeStartValue
        swipeShowThenHide()
        if (!changed) {
            invalidate()
            return
        }
        if (swipeOnWeight) {
            ReadBookConfig.textBold = swipeValue
        } else {
            ReadBookConfig.textSize = swipeValue
        }
        ReadBookConfig.save()
        invalidate()
        postEvent(EventBus.UP_CONFIG, true)
    }

    /** 指示框在松手后再停留一小会儿，让用户看到最终数值 */
    private fun swipeShowThenHide() {
        swipeShowing = true
        swipeHideRunnable?.let { removeCallbacks(it) }
        val runnable = Runnable {
            swipeShowing = false
            // 需要主动重绘一次才能擦掉指示框（墨水屏只更新脏区）
            invalidate()
        }
        swipeHideRunnable = runnable
        postDelayed(runnable, 1200L)
    }

    /** 侧边指示框：白底 + 2dp 黑框 + 纯黑字，右边显示字号、左边显示字重 */
    private fun drawSwipeIndicator(canvas: Canvas) {
        val text = if (swipeOnWeight) {
            context.getString(R.string.ink_text_weight_value, swipeValue)
        } else {
            context.getString(R.string.ink_font_size_indicator, swipeValue)
        }
        val padH = 14f.dp
        val padV = 9f.dp
        swipePaint.reset()
        swipePaint.isAntiAlias = true
        swipePaint.typeface = Typeface.DEFAULT_BOLD
        swipePaint.textSize = 20f.sp
        val textWidth = swipePaint.measureText(text)
        val boxWidth = textWidth + padH * 2
        val boxHeight = swipePaint.textSize + padV * 2
        // 贴近顶部显示：避开页眉、也避免滑动时被手指挡住
        val topGap = curPage.headerHeight + 14f.dp
        val cy = (topGap + boxHeight / 2f).coerceAtMost(height * 0.30f)
        if (swipeOnWeight) {
            val left = 12f.dp
            swipeRect.set(left, cy - boxHeight / 2f, left + boxWidth, cy + boxHeight / 2f)
        } else {
            val right = width - 12f.dp
            swipeRect.set(right - boxWidth, cy - boxHeight / 2f, right, cy + boxHeight / 2f)
        }
        // 白底
        swipePaint.style = Paint.Style.FILL
        swipePaint.color = Color.WHITE
        canvas.drawRoundRect(swipeRect, 8f.dp, 8f.dp, swipePaint)
        // 黑框
        swipePaint.style = Paint.Style.STROKE
        swipePaint.strokeWidth = 2f.dp
        swipePaint.color = Color.BLACK
        canvas.drawRoundRect(swipeRect, 8f.dp, 8f.dp, swipePaint)
        // 文字
        swipePaint.style = Paint.Style.FILL
        val baseline = cy - (swipePaint.descent() + swipePaint.ascent()) / 2f
        canvas.drawText(text, swipeRect.left + padH, baseline, swipePaint)
    }

    fun upStatusBar() {
        curPage.upStatusBar()
        prevPage.upStatusBar()
        nextPage.upStatusBar()
    }

    /**
     * 保存开始位置
     */
    fun setStartPoint(x: Float, y: Float, invalidate: Boolean = true) {
        startX = x
        startY = y
        lastX = x
        lastY = y
        touchX = x
        touchY = y

        if (invalidate) {
            invalidate()
        }
    }

    /**
     * 保存当前位置
     */
    fun setTouchPoint(x: Float, y: Float, invalidate: Boolean = true) {
        lastX = touchX
        lastY = touchY
        touchX = x
        touchY = y
        if (invalidate) {
            invalidate()
        }
    }

    /**
     * 长按选择
     */
    private fun onLongPress() {
        kotlin.runCatching {
            with(curPage.textPage) {
                curPage.selectText(startX, startY) { relativePage, lineIndex, charIndex ->
                    isTextSelected = true
                    firstRelativePage = relativePage
                    firstLineIndex = lineIndex
                    firstCharIndex = charIndex
                    var lineStart = lineIndex
                    var lineEnd = lineIndex
                    var start: Int
                    var end: Int
                    if (lineIndex - 1 > 0 && lineIndex + 1 < lineSize) {
                        // 中间行
                        val lineText = with(textLines) {
                            get(lineIndex - 1).text + get(lineIndex).text + get(lineIndex + 1).text
                        }
                        boundary.setText(lineText)
                        start = boundary.first()
                        end = boundary.next()
                        val cIndex = textLines[lineIndex - 1].text.length + charIndex
                        while (end != BreakIterator.DONE) {
                            if (cIndex in start until end) {
                                break
                            }
                            start = end
                            end = boundary.next()
                        }
                        if (start < textLines[lineIndex - 1].text.length) {
                            lineStart = lineIndex - 1
                        } else {
                            start -= textLines[lineIndex - 1].text.length
                        }
                        if (end > textLines[lineIndex - 1].text.length + textLines[lineIndex].text.length) {
                            lineEnd = lineIndex + 1
                            end = (end - textLines[lineIndex - 1].text.length
                                    - textLines[lineIndex].text.length)
                        } else {
                            end = end - textLines[lineIndex - 1].text.length - 1
                        }
                    } else if (lineIndex - 1 > 0) {
                        // 尾行
                        val lineText = with(textLines) {
                            get(lineIndex - 1).text + get(lineIndex).text
                        }
                        boundary.setText(lineText)
                        start = boundary.first()
                        end = boundary.next()
                        val cIndex = textLines[lineIndex - 1].text.length + charIndex
                        while (end != BreakIterator.DONE) {
                            if (cIndex in start until end) {
                                break
                            }
                            start = end
                            end = boundary.next()
                        }
                        if (start < textLines[lineIndex - 1].text.length) {
                            lineStart = lineIndex - 1
                        } else {
                            start -= textLines[lineIndex - 1].text.length
                        }
                        end = end - textLines[lineIndex - 1].text.length - 1
                    } else if (lineIndex + 1 < lineSize) {
                        // 首行
                        val lineText = with(textLines) {
                            get(lineIndex).text + get(lineIndex + 1).text
                        }
                        boundary.setText(lineText)
                        start = boundary.first()
                        end = boundary.next()
                        while (end != BreakIterator.DONE) {
                            if (charIndex in start until end) {
                                break
                            }
                            start = end
                            end = boundary.next()
                        }
                        if (end > textLines[lineIndex].text.length) {
                            lineEnd = lineIndex + 1
                            end -= textLines[lineIndex].text.length
                        } else {
                            end -= 1
                        }
                    } else {
                        // 单行
                        val lineText = textLines[lineIndex].text
                        boundary.setText(lineText)
                        start = boundary.first()
                        end = boundary.next()
                        while (end != BreakIterator.DONE) {
                            if (charIndex in start until end) {
                                break
                            }
                            start = end
                            end = boundary.next()
                        }
                        end -= 1
                    }
                    curPage.selectStartMoveIndex(firstRelativePage, lineStart, start)
                    curPage.selectEndMoveIndex(firstRelativePage, lineEnd, end)
                }
            }
        }
    }

    /**
     * 单击
     */
    private fun onSingleTapUp() {
        when {
            isTextSelected -> isTextSelected = false
            mcRect.contains(startX, startY) -> if (!isAbortAnim) {
                click(AppConfig.clickActionMC)
            }
            bcRect.contains(startX, startY) -> {
                click(AppConfig.clickActionBC)
            }
            blRect.contains(startX, startY) -> {
                click(AppConfig.clickActionBL)
            }
            brRect.contains(startX, startY) -> {
                click(AppConfig.clickActionBR)
            }
            mlRect.contains(startX, startY) -> {
                click(AppConfig.clickActionML)
            }
            mrRect.contains(startX, startY) -> {
                click(AppConfig.clickActionMR)
            }
            tlRect.contains(startX, startY) -> {
                click(AppConfig.clickActionTL)
            }
            tcRect.contains(startX, startY) -> {
                click(AppConfig.clickActionTC)
            }
            trRect.contains(startX, startY) -> {
                click(AppConfig.clickActionTR)
            }
        }
    }

    private fun click(action: Int) {
        when (action) {
            1, 3 -> lastTurnDirection = PageDirection.NEXT
            2, 4 -> lastTurnDirection = PageDirection.PREV
        }
        if (BuildConfig.DEBUG && pageDelegate is NoAnimPageDelegate && (action == 1 || action == 2)) {
            pageTurnStart = SystemClock.uptimeMillis()
            Log.d("PagePerf", "turn action=$action")
        }
        when (action) {
            0 -> callBack.showActionMenu()
            1 -> pageDelegate?.nextPage()
            2 -> pageDelegate?.prevPage()
            3 -> ReadBook.moveToNextChapter(true)
            4 -> ReadBook.moveToPrevChapter(upContent = true, toLast = false)
        }
    }

    /**
     * 选择文本
     */
    private fun selectText(x: Float, y: Float) {
        curPage.selectText(x, y) { relativePage, lineIndex, charIndex ->
            when {
                relativePage > firstRelativePage -> {
                    curPage.selectStartMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
                    curPage.selectEndMoveIndex(relativePage, lineIndex, charIndex)
                }
                relativePage < firstRelativePage -> {
                    curPage.selectEndMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
                    curPage.selectStartMoveIndex(relativePage, lineIndex, charIndex)
                }
                lineIndex > firstLineIndex -> {
                    curPage.selectStartMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
                    curPage.selectEndMoveIndex(relativePage, lineIndex, charIndex)
                }
                lineIndex < firstLineIndex -> {
                    curPage.selectEndMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
                    curPage.selectStartMoveIndex(relativePage, lineIndex, charIndex)
                }
                charIndex > firstCharIndex -> {
                    curPage.selectStartMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
                    curPage.selectEndMoveIndex(relativePage, lineIndex, charIndex)
                }
                else -> {
                    curPage.selectEndMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
                    curPage.selectStartMoveIndex(relativePage, lineIndex, charIndex)
                }
            }
        }
    }

    fun onDestroy() {
        ContentTextView.clearPreparedPage()
        swipeHideRunnable?.let { removeCallbacks(it) }
        pageDelegate?.onDestroy()
        curPage.cancelSelect()
    }

    fun fillPage(direction: PageDirection): Boolean {
        if (direction != PageDirection.NONE) lastTurnDirection = direction
        return when (direction) {
            PageDirection.PREV -> {
                pageFactory.moveToPrev(true)
            }
            PageDirection.NEXT -> {
                pageFactory.moveToNext(true)
            }
            else -> false
        }
    }

    fun upPageAnim() {
        isScroll = false
        ChapterProvider.upLayout()
        if (pageDelegate !is NoAnimPageDelegate) {
            pageDelegate = NoAnimPageDelegate(this)
        }
    }

    override fun upContent(relativePosition: Int, resetPageOffset: Boolean) {
        val started = SystemClock.uptimeMillis()
        curPage.setContentDescription(pageFactory.curPage.text)
        if (isScroll) {
            curPage.setContent(pageFactory.curPage, resetPageOffset)
        } else {
            curPage.resetPageOffset()
            when (relativePosition) {
                -1 -> prevPage.setContent(pageFactory.prevPage)
                1 -> nextPage.setContent(pageFactory.nextPage)
                else -> {
                    curPage.setContent(pageFactory.curPage)
                    nextPage.setContent(pageFactory.nextPage)
                    prevPage.setContent(pageFactory.prevPage)
                }
            }
        }
        if (BuildConfig.DEBUG && pageTurnStart != 0L) {
            Log.d("PagePerf", "upContent=${SystemClock.uptimeMillis() - started}ms relative=$relativePosition")
        }
        if (pageDelegate is NoAnimPageDelegate && !isScroll) {
            if (lastTurnDirection == PageDirection.PREV) {
                prevPage.prepareAdjacentPageText()
            } else {
                nextPage.prepareAdjacentPageText()
            }
        }
        callBack.screenOffTimerStart()
    }

    fun upStyle() {
        ContentTextView.clearPreparedPage()
        ChapterProvider.upStyle()
        curPage.upStyle()
        prevPage.upStyle()
        nextPage.upStyle()
    }

    fun upBg() {
        ReadBookConfig.bg ?: let {
            ReadBookConfig.upBg()
        }
        curPage.setBg(ReadBookConfig.bg)
        prevPage.setBg(ReadBookConfig.bg)
        nextPage.setBg(ReadBookConfig.bg)
    }

    fun upTime() {
        curPage.upTime()
        prevPage.upTime()
        nextPage.upTime()
    }

    fun upBattery(battery: Int) {
        curPage.upBattery(battery)
        prevPage.upBattery(battery)
        nextPage.upBattery(battery)
    }

    override val currentChapter: TextChapter?
        get() {
            return if (callBack.isInitFinish) ReadBook.textChapter(0) else null
        }

    override val nextChapter: TextChapter?
        get() {
            return if (callBack.isInitFinish) ReadBook.textChapter(1) else null
        }

    override val prevChapter: TextChapter?
        get() {
            return if (callBack.isInitFinish) ReadBook.textChapter(-1) else null
        }

    override fun hasNextChapter(): Boolean {
        return ReadBook.durChapterIndex < ReadBook.chapterSize - 1
    }

    override fun hasPrevChapter(): Boolean {
        return ReadBook.durChapterIndex > 0
    }

    interface CallBack {
        val isInitFinish: Boolean
        fun showActionMenu()
        fun screenOffTimerStart()
        fun showTextActionMenu()
    }
}

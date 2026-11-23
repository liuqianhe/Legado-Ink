package io.legado.app.help

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.Keep
import io.legado.app.constant.PreferKey
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.utils.*
import splitties.init.appCtx
import timber.log.Timber
import java.io.File

/**
 * 阅读界面配置（墨水屏版）。
 * 支持多套布局 + 自定义背景；文字恒为纯黑以保证墨水屏对比度。
 */
@Keep
object ReadBookConfig {
    const val configFileName = "readConfig.json"
    val configFilePath = FileUtils.getPath(appCtx.filesDir, configFileName)
    val configList: ArrayList<Config> = arrayListOf()

    /** 当前布局索引（多套布局） */
    var styleSelect: Int = appCtx.getPrefInt("ink_style_select", 0)
        private set

    var durConfig
        get() = getConfig(styleSelect)
        set(value) {
            if (configList.isEmpty()) configList.add(value)
            else configList[styleSelect] = value
            upBg()
        }

    var bg: Drawable? = null
    var bgMeanColor: Int = 0
    // 墨水屏：文字恒纯黑，不随背景变化
    val textColor: Int get() = Color.BLACK

    init {
        initConfigs()
    }

    @Synchronized
    fun getConfig(index: Int): Config {
        if (configList.isEmpty()) resetAll()
        return configList.getOrNull(index) ?: configList[0]
    }

    /** 切换布局（互斥于其它布局，立即生效） */
    fun selectStyle(index: Int) {
        val safe = index.coerceIn(0, configList.lastIndex)
        styleSelect = safe
        appCtx.putPrefInt("ink_style_select", safe)
        upBg()
        save()
    }

    fun initConfigs() {
        val configFile = File(configFilePath)
        var configs: List<Config>? = null
        if (configFile.exists()) {
            try {
                configs = GSON.fromJsonArray(configFile.readText())
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
        (configs ?: DefaultData.readConfigs).let {
            configList.clear()
            if (it.isEmpty()) {
                configList.add(Config().applyInkDefaults())
            } else {
                it.forEach { c -> configList.add(c.applyInkDefaults()) }
            }
        }
        if (styleSelect > configList.lastIndex) selectStyle(0)
    }

    /** 新建一套布局（复制当前布局） */
    fun addStyle(): Int {
        val copy = GSON.fromJsonObject<Config>(GSON.toJson(durConfig))?.apply {
            name = "布局${configList.size + 1}"
        } ?: Config().applyInkDefaults()
        configList.add(copy)
        selectStyle(configList.lastIndex)
        return styleSelect
    }

    /** 删除指定布局，至少保留一套 */
    fun deleteStyle(index: Int): Boolean {
        if (configList.size <= 1) return false
        configList.removeAt(index.coerceIn(0, configList.lastIndex))
        selectStyle(styleSelect.coerceIn(0, configList.lastIndex))
        return true
    }

    fun upBg() {
        val dm = appCtx.resources.displayMetrics
        bg = durConfig.curBgDrawable(dm.widthPixels, dm.heightPixels).apply {
            bgMeanColor = when (this) {
                is BitmapDrawable -> bitmap.getMeanColor()
                is ColorDrawable -> color
                else -> Color.WHITE
            }
        }
    }

    fun save() {
        Coroutine.async {
            synchronized(this) {
                GSON.toJson(configList).let {
                    FileUtils.deleteFile(configFilePath)
                    FileUtils.createFileIfNotExist(configFilePath).writeText(it)
                }
            }
        }
    }

    private fun resetAll() {
        configList.clear()
        configList.add((DefaultData.readConfigs.firstOrNull() ?: Config()).applyInkDefaults())
        save()
    }

    //配置写入读取
    var readBodyToLh = appCtx.getPrefBoolean(PreferKey.readBodyToLh, true)
    val textFullJustify get() = appCtx.getPrefBoolean(PreferKey.textFullJustify, true)
    val textBottomJustify get() = appCtx.getPrefBoolean(PreferKey.textBottomJustify, true)
    var hideStatusBar = appCtx.getPrefBoolean(PreferKey.hideStatusBar)
    var hideNavigationBar = appCtx.getPrefBoolean(PreferKey.hideNavigationBar)
    var useZhLayout = appCtx.getPrefBoolean(PreferKey.useZhLayout)

    /**
     * 竖排（直排）阅读。排版仍在「文字空间」里按横排规则做，
     * 绘制时整体旋转 90° 上屏；字形逐字扶正，因此中文是正常的立排效果。
     */
    var textVertical: Boolean = appCtx.getPrefBoolean(PreferKey.inkTextVertical, false)
        set(value) {
            if (field != value) {
                field = value
                appCtx.putPrefBoolean(PreferKey.inkTextVertical, value)
            }
        }

    /** 竖排起点：false=右侧（默认，自右向左分列），true=左侧（自左向右分列） */
    var verticalStartLeft: Boolean = appCtx.getPrefBoolean(PreferKey.inkVerticalStartLeft, false)
        set(value) {
            if (field != value) {
                field = value
                appCtx.putPrefBoolean(PreferKey.inkVerticalStartLeft, value)
            }
        }

    /**
     * 翻页刷新：每翻 N 页做一次硬件 GC 清残影（0 = 关闭）。
     * 只在本机（JDRead1 / onyx 框架，android.onyx.ViewUpdateHelper）有效。
     */
    var pageRefreshCount: Int = appCtx.getPrefInt(PreferKey.inkPageRefreshCount, 0)
        set(value) {
            val safe = value.coerceIn(0, 999)
            if (field != safe) {
                field = safe
                appCtx.putPrefInt(PreferKey.inkPageRefreshCount, safe)
            }
        }

    /** 上次启用的档位，长按开关时用来恢复 */
    var pageRefreshLastCount: Int = appCtx.getPrefInt(PreferKey.inkPageRefreshLast, 10)
        set(value) {
            val safe = value.coerceIn(1, 999)
            if (field != safe) {
                field = safe
                appCtx.putPrefInt(PreferKey.inkPageRefreshLast, safe)
            }
        }

    val config get() = durConfig

    /** 首次在竖排下改布局时，把当前横排布局整体复制到竖排字段，避免跳到硬编码默认值 */
    private fun ensureVerticalLayout() {
        if (config.vLayoutCustom) return
        config.vTextBold = config.textBold
        config.vTextSize = config.textSize
        config.vLetterSpacing = config.letterSpacing
        config.vLineSpacingExtra = config.lineSpacingExtra
        config.vParagraphSpacing = config.paragraphSpacing
        config.vLayoutCustom = true
    }

    private val useVerticalLayout: Boolean
        get() = textVertical && config.vLayoutCustom

    var textFont: String
        get() = config.textFont
        set(value) {
            config.textFont = value
        }

    var textBold: Int
        get() = if (useVerticalLayout) config.vTextBold else config.textBold
        set(value) {
            if (textVertical) {
                ensureVerticalLayout()
                config.vTextBold = value
            } else config.textBold = value
        }

    var textSize: Int
        get() = if (useVerticalLayout) config.vTextSize else config.textSize
        set(value) {
            if (textVertical) {
                ensureVerticalLayout()
                config.vTextSize = value
            } else config.textSize = value
        }

    var letterSpacing: Float
        get() = if (useVerticalLayout) config.vLetterSpacing else config.letterSpacing
        set(value) {
            if (textVertical) {
                ensureVerticalLayout()
                config.vLetterSpacing = value
            } else config.letterSpacing = value
        }

    var lineSpacingExtra: Int
        get() = if (useVerticalLayout) config.vLineSpacingExtra else config.lineSpacingExtra
        set(value) {
            if (textVertical) {
                ensureVerticalLayout()
                config.vLineSpacingExtra = value
            } else config.lineSpacingExtra = value
        }

    var paragraphSpacing: Int
        get() = if (useVerticalLayout) config.vParagraphSpacing else config.paragraphSpacing
        set(value) {
            if (textVertical) {
                ensureVerticalLayout()
                config.vParagraphSpacing = value
            } else config.paragraphSpacing = value
        }

    var titleMode: Int
        get() = config.titleMode
        set(value) {
            config.titleMode = value
        }
    var titleSize: Int
        get() = config.titleSize
        set(value) {
            config.titleSize = value
        }

    var titleTopSpacing: Int
        get() = config.titleTopSpacing
        set(value) {
            config.titleTopSpacing = value
        }

    var titleBottomSpacing: Int
        get() = config.titleBottomSpacing
        set(value) {
            config.titleBottomSpacing = value
        }

    /** 段落首行缩进：横排/竖排完全独立，互不影响（竖排默认无缩进） */
    var paragraphIndent: String
        get() = if (textVertical) config.vParagraphIndent else config.paragraphIndent
        set(value) {
            if (textVertical) config.vParagraphIndent = value
            else config.paragraphIndent = value
        }

    var paddingBottom: Int
        get() = config.paddingBottom
        set(value) {
            config.paddingBottom = value
        }

    var paddingLeft: Int
        get() = config.paddingLeft
        set(value) {
            config.paddingLeft = value
        }

    var paddingRight: Int
        get() = config.paddingRight
        set(value) {
            config.paddingRight = value
        }

    var paddingTop: Int
        get() = config.paddingTop
        set(value) {
            config.paddingTop = value
        }

    var headerPaddingBottom: Int
        get() = config.headerPaddingBottom
        set(value) {
            config.headerPaddingBottom = value
        }

    var headerPaddingLeft: Int
        get() = config.headerPaddingLeft
        set(value) {
            config.headerPaddingLeft = value
        }

    var headerPaddingRight: Int
        get() = config.headerPaddingRight
        set(value) {
            config.headerPaddingRight = value
        }

    var headerPaddingTop: Int
        get() = config.headerPaddingTop
        set(value) {
            config.headerPaddingTop = value
        }

    var footerPaddingBottom: Int
        get() = config.footerPaddingBottom
        set(value) {
            config.footerPaddingBottom = value
        }

    var footerPaddingLeft: Int
        get() = config.footerPaddingLeft
        set(value) {
            config.footerPaddingLeft = value
        }

    var footerPaddingRight: Int
        get() = config.footerPaddingRight
        set(value) {
            config.footerPaddingRight = value
        }

    var footerPaddingTop: Int
        get() = config.footerPaddingTop
        set(value) {
            config.footerPaddingTop = value
        }

    var showHeaderLine: Boolean
        get() = config.showHeaderLine
        set(value) {
            config.showHeaderLine = value
        }

    var showFooterLine: Boolean
        get() = config.showFooterLine
        set(value) {
            config.showFooterLine = value
        }

    // ===== 页眉/页脚字号（独立配置的 headerFontSize / footerFontSize）=====
    // 限制范围，避免字号过大导致页眉页脚遮住正文
    const val minTipFontSize = 6
    const val maxTipFontSize = 24

    // ===== 页眉/页脚字重（0~10 档，与正文字重同一套算法）=====
    const val minTipFontWeight = 0
    const val maxTipFontWeight = 10

    var headerFontSize: Int
        get() = config.headerFontSize.coerceIn(minTipFontSize, maxTipFontSize)
        set(value) {
            config.headerFontSize = value.coerceIn(minTipFontSize, maxTipFontSize)
        }

    var footerFontSize: Int
        get() = config.footerFontSize.coerceIn(minTipFontSize, maxTipFontSize)
        set(value) {
            config.footerFontSize = value.coerceIn(minTipFontSize, maxTipFontSize)
        }

    var headerFontWeight: Int
        get() = config.headerFontWeight.coerceIn(minTipFontWeight, maxTipFontWeight)
        set(value) {
            config.headerFontWeight = value.coerceIn(minTipFontWeight, maxTipFontWeight)
        }

    var footerFontWeight: Int
        get() = config.footerFontWeight.coerceIn(minTipFontWeight, maxTipFontWeight)
        set(value) {
            config.footerFontWeight = value.coerceIn(minTipFontWeight, maxTipFontWeight)
        }

    // ===== 页眉/页脚分割线粗细（dp），1~8dp =====
    const val minDividerSize = 1
    const val maxDividerSize = 8

    var headerLineSize: Int
        get() = config.headerLineSize.coerceIn(minDividerSize, maxDividerSize)
        set(value) {
            config.headerLineSize = value.coerceIn(minDividerSize, maxDividerSize)
        }

    var footerLineSize: Int
        get() = config.footerLineSize.coerceIn(minDividerSize, maxDividerSize)
        set(value) {
            config.footerLineSize = value.coerceIn(minDividerSize, maxDividerSize)
        }

    @Keep
    class Config(
        var name: String = "墨水屏",
        // 背景：bgType 0=纯色 1=assets内置图片 2=外部图片路径
        var bgStr: String = "#FFFFFF",
        var bgType: Int = 0,
        var textFont: String = "",//字体
        var textBold: Int = 0,//是否粗体字 0:正常, 1:粗体, 2:细体
        var textSize: Int = 20,//文字大小
        var letterSpacing: Float = 0.1f,//字间距
        var lineSpacingExtra: Int = 12,//行间距
        var paragraphSpacing: Int = 2,//段距
        // 竖排专用布局：vLayoutCustom=false 时整体回退到横排同名值（老配置无缝兼容）；
        // 一旦用户在竖排模式下调整过任一布局项，标记置 true，竖排改用这一整套独立值。
        var vLayoutCustom: Boolean = false,
        var vTextBold: Int = 0,
        var vTextSize: Int = 20,
        var vLetterSpacing: Float = 0.1f,
        var vLineSpacingExtra: Int = 12,
        var vParagraphSpacing: Int = 2,
        var vParagraphIndent: String = "",//竖排段落缩进（独立于横排，默认无缩进）
        var titleMode: Int = 0,//标题居中
        var titleSize: Int = 0,
        var titleTopSpacing: Int = 0,
        var titleBottomSpacing: Int = 0,
        var paragraphIndent: String = "　　",//段落缩进
        var paddingBottom: Int = 6,
        var paddingLeft: Int = 16,
        var paddingRight: Int = 16,
        var paddingTop: Int = 6,
        var headerPaddingBottom: Int = 0,
        var headerPaddingLeft: Int = 16,
        var headerPaddingRight: Int = 16,
        var headerPaddingTop: Int = 0,
        var footerPaddingBottom: Int = 6,
        var footerPaddingLeft: Int = 16,
        var footerPaddingRight: Int = 16,
        var footerPaddingTop: Int = 6,
        var showHeaderLine: Boolean = false,
        var showFooterLine: Boolean = false,
        var headerFontSize: Int = 12,
        var footerFontSize: Int = 12,
        var headerFontWeight: Int = 0,
        var footerFontWeight: Int = 0,
        var headerLineSize: Int = 1,
        var footerLineSize: Int = 1,
        var tipHeaderLeft: Int = ReadTipConfig.time,
        var tipHeaderMiddle: Int = ReadTipConfig.none,
        var tipHeaderRight: Int = ReadTipConfig.battery,
        var tipFooterLeft: Int = ReadTipConfig.chapterTitle,
        var tipFooterMiddle: Int = ReadTipConfig.none,
        var tipFooterRight: Int = ReadTipConfig.pageAndTotal,
        var headerMode: Int = 0,
        var footerMode: Int = 0
    ) {

        fun applyInkDefaults(): Config = apply {
            if (name.isBlank()) name = "墨水屏"
            // 墨水屏统一白底：清理旧版本残留的背景。
            // - bgType=1 且不是唯一预制的“纯白.jpg” → 旧图案（资源已删）归白；
            // - bgType=0 且非纯白 → 旧预设色（米白/护眼黄等）归白；
            // - 相册自选背景(bgType=2)与“纯白.jpg”是主动选择，予以保留。
            when {
                bgType == 1 && bgStr != "纯白.jpg" -> {
                    bgType = 0
                    bgStr = "#FFFFFF"
                }
                bgType == 0 && !bgStr.equals("#FFFFFF", true) -> {
                    bgStr = "#FFFFFF"
                }
            }
        }

        fun curStatusIconDark(): Boolean = true

        /** 设置背景（bgType: 0 颜色 / 1 assets 图片 / 2 外部图片） */
        fun setCurBg(type: Int, bg: String) {
            bgType = type
            bgStr = bg
        }

        /** 当前背景 Drawable；文字恒黑，背景可换 */
        fun curBgDrawable(width: Int, height: Int): Drawable {
            val res = appCtx.resources
            return try {
                when (bgType) {
                    0 -> ColorDrawable(Color.parseColor(bgStr))
                    1 -> BitmapDrawable(
                        res,
                        BitmapUtils.decodeAssetsBitmap(
                            appCtx, "bg${File.separator}$bgStr", width, height
                        )
                    )
                    else -> CenterCropDrawable(BitmapUtils.decodeBitmap(bgStr, width, height))
                }
            } catch (e: Throwable) {
                Timber.e(e)
                ColorDrawable(Color.WHITE)
            }
        }
    }
}

/**
 * 等比裁切铺满的位图 Drawable（安卓 4.4 的 BitmapDrawable 没有 CENTER_CROP）。
 * 背景图按原比例铺满整个页面，只裁掉多余部分，不拉伸变形。
 */
private class CenterCropDrawable(private val bmp: Bitmap) : Drawable() {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty || bmp.isRecycled) return
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        if (bw <= 0f || bh <= 0f) return
        val scale = maxOf(b.width() / bw, b.height() / bh)
        val dw = bw * scale
        val dh = bh * scale
        val left = b.left + (b.width() - dw) / 2f
        val top = b.top + (b.height() - dh) / 2f
        dst.set(left, top, left + dw, top + dh)
        canvas.save()
        canvas.clipRect(b)
        canvas.drawBitmap(bmp, null, dst, paint)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

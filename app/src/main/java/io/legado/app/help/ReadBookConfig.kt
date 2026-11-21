package io.legado.app.help

import android.graphics.Color
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

    val config get() = durConfig

    var textFont: String
        get() = config.textFont
        set(value) {
            config.textFont = value
        }

    var textBold: Int
        get() = config.textBold
        set(value) {
            config.textBold = value
        }

    var textSize: Int
        get() = config.textSize
        set(value) {
            config.textSize = value
        }

    var letterSpacing: Float
        get() = config.letterSpacing
        set(value) {
            config.letterSpacing = value
        }

    var lineSpacingExtra: Int
        get() = config.lineSpacingExtra
        set(value) {
            config.lineSpacingExtra = value
        }

    var paragraphSpacing: Int
        get() = config.paragraphSpacing
        set(value) {
            config.paragraphSpacing = value
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

    var paragraphIndent: String
        get() = config.paragraphIndent
        set(value) {
            config.paragraphIndent = value
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
                    else -> BitmapDrawable(res, BitmapUtils.decodeBitmap(bgStr, width, height))
                }
            } catch (e: Exception) {
                Timber.e(e)
                ColorDrawable(Color.WHITE)
            }
        }
    }
}

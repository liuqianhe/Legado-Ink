package io.legado.app.help.underline

import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

/**
 * 下划线配置与记录存储。
 *
 * 用 SharedPreferences 存 JSON，不碰 Room 数据库，避免 migration。
 * 三种模式：
 *  - globalKind  : 全局下划线（每行都画，标题/页眉页脚除外）——与 body/dialogue 互斥
 *  - bodyKind    : 正文下划线（非对话部分）
 *  - dialogueKind: 对话下划线（预制对话正则命中部分）——可与 body 共存
 */
object UnderlineStore {

    private const val CONFIG_KEY = "ink_underline_config"
    private const val LEGACY_PREFIX = "ink_underline_"

    /** 线型。0=关闭 */
    enum class Kind(val value: Int) {
        NONE(0),        // 关闭
        SOLID(1),       // 标准下划线
        DASHED(2),      // 虚线
        DOTTED(3),      // 点线
        DOUBLE(4),      // 双下划线
        WAVY(5);        // 波浪线

        companion object {
            fun from(value: Int): Kind = values().firstOrNull { it.value == value } ?: NONE
        }
    }

    /** 供 UI 顺序使用的线型（不含 NONE） */
    val selectableKinds = listOf(Kind.SOLID, Kind.DASHED, Kind.DOUBLE, Kind.WAVY, Kind.DOTTED)

    /**
     * 下划线模式配置。
     * 互斥规则由 [setGlobal] / [setBody] / [setDialogue] 保证：
     * 全局打开时正文与对话被清空；反之打开正文或对话会清空全局。
     */
    data class Config(
        var globalKind: Int = Kind.NONE.value,
        var bodyKind: Int = Kind.NONE.value,
        var dialogueKind: Int = Kind.NONE.value
    ) {
        val isGlobal get() = globalKind != Kind.NONE.value
        val anyEnabled get() = globalKind != Kind.NONE.value ||
                bodyKind != Kind.NONE.value || dialogueKind != Kind.NONE.value

        fun setGlobal(kind: Kind) {
            globalKind = kind.value
            if (kind != Kind.NONE) {
                bodyKind = Kind.NONE.value
                dialogueKind = Kind.NONE.value
            }
        }

        fun setBody(kind: Kind) {
            bodyKind = kind.value
            if (kind != Kind.NONE) globalKind = Kind.NONE.value
        }

        fun setDialogue(kind: Kind) {
            dialogueKind = kind.value
            if (kind != Kind.NONE) globalKind = Kind.NONE.value
        }
    }

    /** 旧的选区记录（保留兼容 createUnderline，新规则式配置不再依赖它） */
    data class Record(
        val chapterIndex: Int = 0,
        val start: Int = 0,
        val endInclusive: Int = 0,
        val kind: Int = Kind.SOLID.value,
        val scope: Int = 1,
        val text: String = ""
    )

    fun getConfig(): Config {
        val json = appCtx.getPrefString(CONFIG_KEY) ?: return Config()
        return runCatching {
            GSON.fromJsonObject<Config>(json) ?: Config()
        }.getOrElse { Config() }
    }

    fun saveConfig(config: Config) {
        appCtx.putPrefString(CONFIG_KEY, GSON.toJson(config))
    }

    // ---- 保留旧记录 API，避免其它调用点编译失败 ----

    private fun keyOf(bookUrl: String) = LEGACY_PREFIX + bookUrl.hashCode()

    fun getAll(bookUrl: String): MutableList<Record> {
        val json = appCtx.getPrefString(keyOf(bookUrl)) ?: return mutableListOf()
        return runCatching {
            GSON.fromJsonObject<List<Record>>(json)?.toMutableList() ?: mutableListOf()
        }.getOrElse { mutableListOf() }
    }

    fun save(bookUrl: String, records: List<Record>) {
        appCtx.putPrefString(keyOf(bookUrl), GSON.toJson(records))
    }

    fun add(bookUrl: String, record: Record) {
        val records = getAll(bookUrl)
        if (records.none {
                it.chapterIndex == record.chapterIndex && it.start == record.start &&
                        it.endInclusive == record.endInclusive && it.kind == record.kind
            }) {
            records.add(record)
            save(bookUrl, records)
        }
    }

    fun remove(bookUrl: String, chapterIndex: Int) {
        save(bookUrl, getAll(bookUrl).filterNot { it.chapterIndex == chapterIndex })
    }

    fun getForChapter(bookUrl: String, chapterIndex: Int): List<Record> =
        getAll(bookUrl).filter { it.chapterIndex == chapterIndex }
}

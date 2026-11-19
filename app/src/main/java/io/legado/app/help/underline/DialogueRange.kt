package io.legado.app.help.underline

/**
 * 对话识别：使用预制的对话正则（设备不便输入，直接内置）。
 *
 * 正则来源（用户指定）：
 *   “[^”\n]{1,120}”|"[^"\n]{1,120}"|「[^」\n]{1,120}」|『[^』\n]{1,120}』
 *
 * 说明：只识别成对的中英文双引号与中日引号；不匹配中文单引号，也不跨行。
 */
object DialogueRange {

    const val DIALOGUE_PATTERN =
        "“[^”\\n]{1,120}”|\"[^\"\\n]{1,120}\"|「[^」\\n]{1,120}」|『[^』\\n]{1,120}』"

    private val regex = Regex(DIALOGUE_PATTERN)

    /** 返回对话所在的闭区间列表（相对传入文本） */
    fun findRanges(text: String): List<IntRange> =
        regex.findAll(text).map { it.range }.toList()

    fun hasDialogue(text: String): Boolean = regex.containsMatchIn(text)

    /**
     * 正文区间 = [0, text.length) 减去所有对话区间（闭区间）。
     * 用于「正文下划线」：给对话以外的内容加线。
     */
    fun findBodyRanges(text: String, dialogue: List<IntRange> = findRanges(text)): List<IntRange> {
        if (text.isEmpty()) return emptyList()
        if (dialogue.isEmpty()) return listOf(0..text.lastIndex)

        val sorted = dialogue.sortedBy { it.first }
        val result = mutableListOf<IntRange>()
        var cursor = 0
        for (r in sorted) {
            if (r.first > cursor) result.add(cursor..(r.first - 1))
            cursor = maxOf(cursor, r.last + 1)
        }
        if (cursor <= text.lastIndex) result.add(cursor..text.lastIndex)
        return result
    }
}

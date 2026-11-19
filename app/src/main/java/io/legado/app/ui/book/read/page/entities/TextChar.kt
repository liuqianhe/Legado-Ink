package io.legado.app.ui.book.read.page.entities

data class TextChar(
    val charData: String,
    var start: Float,
    var end: Float,
    var selected: Boolean = false,
    var isImage: Boolean = false,
    // 下划线样式（墨水屏定制）：underlineKind=全局/正文线型，dialogueUnderlineKind=对话线型
    // 0=无 1=标准 2=虚线 3=点线 4=双线 5=波浪
    var underlineKind: Int = 0,
    var dialogueUnderlineKind: Int = 0
) {

    fun isTouch(x: Float): Boolean {
        return x > start && x < end
    }

}
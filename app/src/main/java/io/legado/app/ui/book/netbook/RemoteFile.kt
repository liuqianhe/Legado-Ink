package io.legado.app.ui.book.netbook

/** 网络书籍列表项：文件夹或书籍文件 */
data class RemoteFile(
    val name: String,
    val path: String,
    val isDir: Boolean = false,
    val size: Long = 0,
    val lastModify: Long = 0,
    val contentType: String = "",
    val isOnBookShelf: Boolean = false
)

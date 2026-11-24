package io.legado.app.ui.book.netbook

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** 网络书籍的一个 WebDAV 服务器配置（存 SharedPreferences JSON，避免改 Room 结构） */
@Parcelize
data class NetBookServer(
    var id: Long = System.currentTimeMillis(),
    var name: String = "",
    var url: String = "",
    // 留空即可，子目录直接写到 url 末尾（页面不做子目录导航）
    var path: String = "",
    var account: String = "",
    var password: String = ""
) : Parcelable

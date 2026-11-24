package io.legado.app.ui.book.netbook

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.help.AppConfig
import io.legado.app.model.NoStackTraceException
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** 网络书籍：目录浏览 + 下载到「保存本地书籍」目录并加入书架 */
class NetBookViewModel(application: Application) : BaseViewModel(application) {

    companion object {
        const val DEFAULT_SERVER_ID = -1L
        private const val DEFAULT_WEBDAV_URL = "https://dav.jianguoyun.com/dav/"
    }

    val listLiveData = MutableLiveData<List<RemoteFile>>()
    val errorLiveData = MutableLiveData<String>()
    val serversLiveData = MutableLiveData<List<NetBookServer>>()

    val dirList = arrayListOf<RemoteFile>()
    var sortKey = NetBookSort.Default
    var sortAscending = false

    private val bookRegex = Regex("(?i).*\\.(txt|epub|umd|zip|rar|7z)")

    enum class NetBookSort { Default, Name }

    /** 旧版单服务器地址迁移 */
    fun servers(): List<NetBookServer> {
        var json = AppConfig.netBookServersJson
        if (json.isBlank()) {
            val oldUrl = AppConfig.netBookWebDavUrl
            if (oldUrl.isNotBlank()) {
                val migrated = NetBookServer(
                    name = "旧版地址",
                    url = oldUrl,
                    path = AppConfig.netBookWebDavPath,
                    account = AppConfig.netBookWebDavAccount,
                    password = AppConfig.netBookWebDavPassword
                )
                AppConfig.netBookServersJson = GSON.toJson(listOf(migrated))
                AppConfig.netBookServerId = migrated.id
                json = AppConfig.netBookServersJson
            }
        }
        return GSON.fromJsonArray<NetBookServer>(json).orEmpty()
    }

    fun notifyServersChanged() = serversLiveData.postValue(servers())

    fun saveServers(list: List<NetBookServer>) {
        AppConfig.netBookServersJson = GSON.toJson(list)
        notifyServersChanged()
    }

    fun upsertServer(server: NetBookServer) {
        val list = servers().toMutableList()
        val index = list.indexOfFirst { it.id == server.id }
        if (index >= 0) list[index] = server else list.add(server)
        saveServers(list)
    }

    fun deleteServer(server: NetBookServer) {
        saveServers(servers().filter { it.id != server.id })
        if (AppConfig.netBookServerId == server.id) AppConfig.netBookServerId = DEFAULT_SERVER_ID
        notifyServersChanged()
    }

    fun selectServer(id: Long) {
        AppConfig.netBookServerId = id
        notifyServersChanged()
    }

    fun currentServer(): NetBookServer? {
        if (AppConfig.netBookServerId == DEFAULT_SERVER_ID) return defaultServer()
        return servers().firstOrNull { it.id == AppConfig.netBookServerId } ?: defaultServer()
    }

    /** 默认地址 = 当前备份 WebDAV 地址本身，不追加 books/ 等子目录 */
    private fun defaultServer(): NetBookServer = NetBookServer(
        id = DEFAULT_SERVER_ID,
        name = "默认地址（备份 WebDAV）",
        url = context.getPrefString("web_dav_url").orEmpty().ifBlank { DEFAULT_WEBDAV_URL }.trim(),
        path = "",
        account = context.getPrefString("web_dav_account").orEmpty(),
        password = context.getPrefString("web_dav_password").orEmpty()
    )

    private fun normalizeUrl(raw: String): String {
        val url = raw.trim()
        if (url.isEmpty()) return url
        return if (url.contains("://")) url else "http://$url"
    }

    /** 根地址：路径字段为空时直接用填写的地址 */
    fun rootUrl(): String = currentServer()?.let { buildUrl(it) }.orEmpty()

    private fun buildUrl(server: NetBookServer): String {
        var base = normalizeUrl(server.url)
        if (!base.endsWith("/")) base += "/"
        val folder = server.path.trim().trim('/')
        if (folder.isEmpty()) return base
        if (base.trimEnd('/').endsWith(folder.trimEnd('/'), ignoreCase = true)) return base
        return "$base$folder/"
    }

    fun isConfigured(): Boolean = currentServer()?.url?.isNotBlank() == true

    /** 测试连接，返回目录下书籍数 */
    suspend fun testConnection(server: NetBookServer): Int = withContext(IO) {
        if (server.url.isBlank()) throw NoStackTraceException("请先设置 WebDAV 服务器")
        NetBookWebDav.list(buildUrl(server), server.account, server.password)
            .count { !it.isDir && bookRegex.matches(it.name) }
    }

    /** 列出当前目录的文件夹和书籍，按设置排序 */
    fun loadList(path: String?) {
        execute {
            val server = currentServer() ?: throw NoStackTraceException("请先设置 WebDAV 服务器")
            val url = path ?: buildUrl(server)
            val onShelf = appDb.bookDao.all.mapNotNull { it.originName?.substringBeforeLast('.') }.toHashSet()
            val files = NetBookWebDav.list(url, server.account, server.password)
                .filter { it.isDir || bookRegex.matches(it.name) }
                .map {
                    it.copy(
                        contentType = if (it.isDir) "" else it.name.substringAfterLast('.', ""),
                        isOnBookShelf = !it.isDir && onShelf.contains(it.name.substringBeforeLast('.'))
                    )
                }
            val sorted = if (sortAscending) {
                when (sortKey) {
                    NetBookSort.Name -> files.sortedWith(compareBy({ !it.isDir }, { it.name }))
                    else -> files.sortedWith(compareBy({ !it.isDir }, { it.lastModify }))
                }
            } else {
                when (sortKey) {
                    NetBookSort.Name -> files.sortedWith(compareByDescending<RemoteFile> { it.isDir }.thenByDescending { it.name })
                    else -> files.sortedWith(compareByDescending<RemoteFile> { it.isDir }.thenByDescending { it.lastModify })
                }
            }
            listLiveData.postValue(sorted)
        }.onError {
            errorLiveData.postValue(it.localizedMessage ?: "获取远程书籍列表出错")
        }
    }

    /** 下载所选书籍到「保存本地书籍」目录并加入书架 */
    fun addToBookshelf(files: List<RemoteFile>, finally: () -> Unit) {
        execute {
            val treeUriStr = AppConfig.defaultBookTreeUri
                ?: throw NoStackTraceException("请先设置保存本地书籍目录")
            val server = currentServer() ?: throw NoStackTraceException("请先设置 WebDAV 服务器")
            val saveUri = Uri.parse(treeUriStr)
            // 4.4 自带选择器返回 content://，桥接选择器返回 file://，两种都要支持
            val contentScheme = treeUriStr.isContentScheme()
            val treeDoc = if (contentScheme) {
                DocumentFile.fromTreeUri(context, saveUri)
                    ?: throw NoStackTraceException("保存本地书籍目录不可用，请重新选择")
            } else null
            val dirFile = if (!contentScheme) {
                val dir = saveUri.path?.let { File(it) }
                    ?: throw NoStackTraceException("保存本地书籍目录路径无效，请重新选择")
                if (!dir.isDirectory) throw NoStackTraceException("保存本地书籍目录不存在，请重新选择")
                dir
            } else null
            var count = 0
            files.filter { !it.isDir && bookRegex.matches(it.name) }.forEach { remote ->
                val bytes = NetBookWebDav.download(remote.path, server.account, server.password)
                val targetUri: Uri
                val output = if (treeDoc != null) {
                    val target = treeDoc.createFile("application/octet-stream", remote.name)
                        ?: throw NoStackTraceException("无法创建 ${remote.name}")
                    targetUri = target.uri
                    context.contentResolver.openOutputStream(target.uri)
                } else {
                    val target = File(dirFile, remote.name)
                    targetUri = Uri.fromFile(target)
                    runCatching { FileOutputStream(target) }.getOrNull()
                }
                output?.use { it.write(bytes) }
                    ?: throw NoStackTraceException("无法写入 ${remote.name}")
                LocalBook.importFile(targetUri)
                count++
            }
            count
        }.onSuccess { count ->
            context.toastOnUi("已加入书架 $count 本")
            finally.invoke()
        }.onError {
            errorLiveData.postValue(it.localizedMessage ?: "下载失败")
            finally.invoke()
        }
    }
}

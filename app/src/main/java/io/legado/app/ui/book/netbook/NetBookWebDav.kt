package io.legado.app.ui.book.netbook

import io.legado.app.help.http.okHttpClient
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 网络书籍专用 WebDAV 访问。
 * 复用全局 okHttpClient（已包含 TLS1.2 修复与 CLEARTEXT），不改动备份/进度同步用的 WebDav。
 */
object NetBookWebDav {

    private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:"><D:prop>
<D:displayname/><D:resourcetype/><D:getcontentlength/><D:getlastmodified/>
</D:prop></D:propfind>"""

    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)

    private fun localName(e: Element): String =
        e.tagName().substringAfter(':').lowercase(Locale.ROOT)

    private fun childText(e: Element, name: String): String? {
        for (child in e.getAllElements()) {
            if (child === e) continue
            if (localName(child) == name && child.text().isNotBlank()) return child.text()
        }
        return null
    }

    private fun normalize(url: String): String {
        val u = url.trim()
        return if (u.contains("://")) u else "http://$u"
    }

    private fun authHeader(account: String, password: String): String =
        Credentials.basic(account, password)

    /** 列出目录下的文件夹与文件 */
    fun list(url: String, account: String, password: String): List<RemoteFile> {
        val base = HttpUrl.get(normalize(url))
        val request = Request.Builder()
            .url(base)
            .header("Depth", "1")
            .header("Authorization", authHeader(account, password))
            .method(
                "PROPFIND",
                RequestBody.create(MediaType.parse("application/xml; charset=utf-8"), PROPFIND_BODY)
            )
            .build()
        val body = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("WebDAV ${response.code()}: ${response.message()}")
            }
            response.body()?.string().orEmpty()
        }
        if (body.isBlank()) return emptyList()

        val doc = Jsoup.parse(body, "", Parser.xmlParser())
        val result = mutableListOf<RemoteFile>()
        for (element in doc.getAllElements()) {
            if (localName(element) != "response") continue
            val href = childText(element, "href") ?: continue
            val fullUrl = base.resolve(href)?.toString() ?: continue
            // 目录自身的条目跳过
            if (fullUrl.trimEnd('/').equals(base.toString().trimEnd('/'), ignoreCase = true)) continue
            val isDir = href.endsWith("/") || element.getAllElements().any {
                it !== element && localName(it) == "collection"
            }
            val encodedName = href.removeSuffix("/").substringAfterLast("/")
            if (encodedName.isEmpty()) continue
            val name = childText(element, "displayname")
                ?.takeIf { it.isNotBlank() }
                ?: java.net.URLDecoder.decode(encodedName, "UTF-8")
            val size = childText(element, "getcontentlength")?.toLongOrNull() ?: 0
            val lastModify = childText(element, "getlastmodified")?.let {
                runCatching { dateFormat.parse(it)?.time ?: 0 }.getOrDefault(0)
            } ?: 0
            result.add(
                RemoteFile(
                    name = name,
                    path = fullUrl,
                    isDir = isDir,
                    size = size,
                    lastModify = lastModify
                )
            )
        }
        return result
    }

    /** 下载文件字节 */
    fun download(url: String, account: String, password: String): ByteArray {
        val request = Request.Builder()
            .url(HttpUrl.get(normalize(url)))
            .header("Authorization", authHeader(account, password))
            .get()
            .build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("WebDAV 下载失败 ${response.code()}: ${response.message()}")
            }
            response.body()?.bytes() ?: throw Exception("WebDAV 下载返回为空")
        }
    }
}

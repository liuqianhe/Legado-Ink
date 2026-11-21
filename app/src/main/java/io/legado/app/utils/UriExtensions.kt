package io.legado.app.utils

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import io.legado.app.R
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.model.NoStackTraceException
import timber.log.Timber
import java.io.File

fun Uri.isContentScheme() = this.scheme == "content"

/**
 * 读取URI
 *
 * 墨水屏/API19 说明：这里不再走 PermissionsCompat 回调。4.4 存储权限是安装时授予的，
 * 走权限框架反而可能因为回调时机/宿主对象问题静默失败（表现为“选了图但没生效”）。
 * 直接读 stream / File，失败一律抛出并提示。
 */
fun AppCompatActivity.readUri(uri: Uri?, success: (name: String, bytes: ByteArray) -> Unit) {
    uri ?: return
    try {
        val name: String
        val bytes: ByteArray
        if (uri.isContentScheme()) {
            val doc = DocumentFile.fromSingleUri(this, uri)
            name = doc?.name
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "picked_file"
            bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw NoStackTraceException("无法读取所选文件")
        } else {
            val path = uri.path ?: RealPathUtil.getPath(this, uri)
            ?: throw NoStackTraceException("无法解析文件路径")
            val file = File(path)
            if (!file.exists()) throw NoStackTraceException("文件不存在: $path")
            name = file.name
            bytes = file.readBytes()
        }
        success.invoke(name, bytes)
    } catch (e: Exception) {
        Timber.e(e)
        toastOnUi("读取失败: ${e.localizedMessage}")
    }
}

/**
 * 读取URI
 */
fun Fragment.readUri(uri: Uri?, success: (name: String, bytes: ByteArray) -> Unit) {
    uri ?: return
    val ctx = context ?: return
    try {
        val name: String
        val bytes: ByteArray
        if (uri.isContentScheme()) {
            val doc = DocumentFile.fromSingleUri(ctx, uri)
            name = doc?.name
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "picked_file"
            bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw NoStackTraceException("无法读取所选文件")
        } else {
            val path = uri.path ?: RealPathUtil.getPath(ctx, uri)
            ?: throw NoStackTraceException("无法解析文件路径")
            val file = File(path)
            if (!file.exists()) throw NoStackTraceException("文件不存在: $path")
            name = file.name
            bytes = file.readBytes()
        }
        success.invoke(name, bytes)
    } catch (e: Exception) {
        Timber.e(e)
        toastOnUi("读取失败: ${e.localizedMessage}")
    }
}

@Throws(Exception::class)
fun Uri.readBytes(context: Context): ByteArray {
    return if (this.isContentScheme()) {
        DocumentUtils.readBytes(context, this)
    } else {
        val path = RealPathUtil.getPath(context, this)
        if (path?.isNotEmpty() == true) {
            File(path).readBytes()
        } else {
            throw NoStackTraceException("获取文件真实地址失败\n${this.path}")
        }
    }
}

@Throws(Exception::class)
fun Uri.readText(context: Context): String {
    readBytes(context).let {
        return String(it)
    }
}

@Throws(Exception::class)
fun Uri.writeBytes(
    context: Context,
    byteArray: ByteArray
): Boolean {
    if (this.isContentScheme()) {
        return DocumentUtils.writeBytes(context, byteArray, this)
    } else {
        val path = RealPathUtil.getPath(context, this)
        if (path?.isNotEmpty() == true) {
            File(path).writeBytes(byteArray)
            return true
        }
    }
    return false
}

@Throws(Exception::class)
fun Uri.writeText(context: Context, text: String): Boolean {
    return writeBytes(context, text.toByteArray())
}

fun Uri.writeBytes(
    context: Context,
    fileName: String,
    byteArray: ByteArray
): Boolean {
    if (this.isContentScheme()) {
        DocumentFile.fromTreeUri(context, this)?.let { pDoc ->
            DocumentUtils.createFileIfNotExist(pDoc, fileName)?.let {
                return it.uri.writeBytes(context, byteArray)
            }
        }
    } else {
        FileUtils.createFileWithReplace(path + File.separatorChar + fileName)
            .writeBytes(byteArray)
        return true
    }
    return false
}
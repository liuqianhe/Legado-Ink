package io.legado.app.help

import android.util.Log
import android.view.View
import java.lang.reflect.Method

/**
 * 墨水屏刷新接口（本机 JDRead1 / onyx 框架：android.onyx.ViewUpdateHelper）。
 *
 * 说明（与控制中心一致，踩过的坑）：
 *  - 真正可移植的 GC 是 `applyGCOnce()`：全局波形级清残影，不闪屏、不需要目标应用配合；
 *    它是同步接口，会阻塞 400~600 ms，**必须在后台线程调**。
 *  - 不要用 `mergeDisplayByCount(N, mode)` 当「每 N 页刷新」——那是「把 N 次显示更新合并成 1 次」，
 *    会把画面冻住；关闭时更不能传 MAX_VALUE。
 *  - 这台 ROM 不给我方应用 signature|system 权限，只能走反射（Android 4.4 无 hidden API 限制）。
 */
object EinkRefresh {
    private const val TAG = "Ink-Eink"
    private const val CLS = "android.onyx.ViewUpdateHelper"

    private val helperClass: Class<*>? by lazy {
        runCatching { Class.forName(CLS) }.getOrNull()
    }

    private fun find(name: String, vararg types: Class<*>): Method? {
        val cls = helperClass ?: return null
        return runCatching {
            cls.getMethod(name, *types).apply { isAccessible = true }
        }.onFailure { Log.w(TAG, "no method $name: $it") }.getOrNull()
    }

    private fun call(name: String, vararg args: Any?): Boolean {
        val types = args.map { it?.javaClass ?: Any::class.java }.toTypedArray()
        val method = find(name, *types) ?: return false
        return runCatching {
            val result = method.invoke(null, *args)
            Log.i(TAG, "$name ok -> $result")
            true
        }.onFailure { Log.w(TAG, "$name failed: $it") }.getOrDefault(false)
    }

    val available: Boolean get() = find("applyGCOnce") != null || find("repaintEverything") != null

    /** 做一次硬件 GC 清残影（阻塞 400~600ms，调用方放后台线程） */
    fun gcOnce(): Boolean {
        if (call("applyGCOnce")) return true
        return call("repaintEverything")
    }

    /** 兼容旧接口：按优先级尝试全刷 */
    fun fullRefresh(view: View? = null): Boolean {
        if (gcOnce()) return true
        if (view != null) {
            val method = find("fullRefreshScreen", View::class.java) ?: return false
            return runCatching {
                method.invoke(null, view)
                true
            }.getOrDefault(false)
        }
        return false
    }
}

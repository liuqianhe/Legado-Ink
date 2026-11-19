package io.legado.app.ui.widget.font

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogFontSelectBinding
import io.legado.app.help.AppConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.document.HandleFileContract
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.io.File
import java.util.ArrayDeque
import kotlin.collections.ArrayList

/**
 * 字体选择对话框：按【目录浏览】方式展示字体。
 * - 只列出当前目录的直接内容：子目录 + 字体文件（ttf/otf）；
 * - 点击子目录进入下一级，Toolbar 的返回按钮回到上一级；
 * - 点击字体文件即应用。
 * 兼容 SAF tree uri（content scheme）与直接文件路径两种目录来源。
 */
class FontSelectDialog : BaseDialogFragment(R.layout.dialog_font_select),
    Toolbar.OnMenuItemClickListener,
    FontAdapter.CallBack {
    private val fontRegex = Regex("(?i).*\\.[ot]tf")
    private val fontFolder by lazy {
        FileUtils.createFolderIfNotExist(appCtx.filesDir, "Fonts")
    }
    private val binding by viewBinding(DialogFontSelectBinding::bind)
    private val adapter by lazy {
        val curFontPath = callBack?.curFontPath ?: ""
        FontAdapter(requireContext(), curFontPath, this)
    }

    // 当前正在浏览的目录及其显示名
    private var currentDirUri: Uri? = null
    private var currentDirName: String = ""
    // 目录导航栈：进入子目录时压入父目录 (uri, 显示名)，返回时弹出
    private val dirStack = ArrayDeque<Pair<Uri, String>>()

    private val selectFontDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            if (uri.toString().isContentScheme()) {
                putPrefString(PreferKey.fontFolder, uri.toString())
                val doc = DocumentFile.fromTreeUri(requireContext(), uri)
                if (doc != null) {
                    context?.contentResolver?.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    navigateToRoot(uri.toString())
                } else {
                    RealPathUtil.getPath(requireContext(), uri)?.let { path ->
                        putPrefString(PreferKey.fontFolder, path)
                        navigateToRoot(path)
                    }
                }
            } else {
                uri.path?.let { path ->
                    putPrefString(PreferKey.fontFolder, path)
                    navigateToRoot(path)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.inflateMenu(R.menu.font_select)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter

        val fontPath = getPrefString(PreferKey.fontFolder)
        if (fontPath.isNullOrEmpty()) {
            openFolder()
        } else {
            navigateToRoot(fontPath)
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_parent -> goParent()
            R.id.menu_default -> {
                val requireContext = requireContext()
                alert(titleResource = R.string.system_typeface) {
                    items(
                        requireContext.resources.getStringArray(R.array.system_typefaces).toList()
                    ) { _, i ->
                        AppConfig.systemTypefaces = i
                        onDefaultFontChange()
                        dismissAllowingStateLoss()
                    }
                }
            }
            R.id.menu_other -> openFolder()
        }
        return true
    }

    private fun openFolder() {
        launch(Main) {
            val defaultPath = "SD${File.separator}Fonts"
            selectFontDir.launch {
                otherActions = arrayListOf(SelectItem(defaultPath, -1))
            }
        }
    }

    /** 进入用户选定的字体根目录，清空导航栈 */
    private fun navigateToRoot(fontPath: String) {
        dirStack.clear()
        currentDirUri = Uri.parse(fontPath)
        currentDirName = getString(R.string.select_font)
        if (fontPath.isContentScheme()) {
            val doc = DocumentFile.fromTreeUri(requireContext(), Uri.parse(fontPath))
            if (doc?.canRead() == true) {
                loadCurrentDir()
            } else {
                openFolder()
            }
        } else {
            loadFontFilesByPermission()
        }
    }

    private fun loadFontFilesByPermission() {
        PermissionsCompat.Builder(this@FontSelectDialog)
            .addPermissions(*Permissions.Group.STORAGE)
            .rationale(R.string.tip_perm_request_storage)
            .onGranted {
                loadCurrentDir()
            }
            .request()
    }

    /** 只列出当前目录的直接内容：子目录 + 字体文件 */
    private fun loadCurrentDir() {
        val dir = currentDirUri ?: return
        execute {
            val raw: ArrayList<FileDoc> = if (dir.toString().isContentScheme()) {
                DocumentUtils.listFiles(dir)
            } else {
                DocumentUtils.listFiles(dir.toString())
            }
            // 目录和字体都要显示；其它文件忽略
            val shown = arrayListOf<FileDoc>()
            raw.forEach { item ->
                if (item.isDir || item.name.matches(fontRegex)) {
                    shown.add(item)
                }
            }
            // 仅在根目录合并 app 内部已导入的字体，子目录保持干净
            if (dirStack.isEmpty()) {
                mergeFontItems(shown, getLocalFonts())
            } else {
                sortFontItems(shown)
            }
        }.onSuccess {
            adapter.setItems(it)
            updateTitle()
        }.onError {
            toastOnUi("getFontFiles:${it.localizedMessage}")
        }
    }

    private fun getLocalFonts(): ArrayList<FileDoc> {
        val path = FileUtils.getPath(requireContext().externalFiles, "font")
        return DocumentUtils.listFiles(path) {
            it.name.matches(fontRegex)
        }
    }

    /** 目录排在前面，字体排在后面，各自按中文拼音排序 */
    private fun sortFontItems(items: List<FileDoc>): List<FileDoc> =
        items.sortedWith { a, b ->
            when {
                a.isDir && !b.isDir -> -1
                !a.isDir && b.isDir -> 1
                else -> a.name.cnCompare(b.name)
            }
        }

    private fun mergeFontItems(
        items1: ArrayList<FileDoc>,
        items2: ArrayList<FileDoc>
    ): List<FileDoc> {
        val items = ArrayList(items1)
        items2.forEach { item2 ->
            var isInFirst = false
            items1.forEach for1@{ item1 ->
                if (item2.name == item1.name) {
                    isInFirst = true
                    return@for1
                }
            }
            if (!isInFirst) {
                items.add(item2)
            }
        }
        return sortFontItems(items)
    }

    private fun updateTitle() {
        binding.toolBar.title = if (dirStack.isEmpty()) {
            getString(R.string.select_font)
        } else {
            currentDirName
        }
    }

    /** 进入子目录 */
    private fun enterDir(doc: FileDoc) {
        currentDirUri?.let { dirStack.addLast(it to currentDirName) }
        currentDirUri = doc.uri
        currentDirName = doc.name
        loadCurrentDir()
    }

    /** 返回上级目录；已在根目录则关闭对话框 */
    private fun goParent() {
        if (dirStack.isNotEmpty()) {
            val (uri, name) = dirStack.removeLast()
            currentDirUri = uri
            currentDirName = name
            loadCurrentDir()
        } else {
            dismissAllowingStateLoss()
        }
    }

    override fun onClick(docItem: FileDoc) {
        // 目录项：进入下一级
        if (docItem.isDir) {
            enterDir(docItem)
            return
        }
        // 字体项：应用
        execute {
            FileUtils.deleteFile(fontFolder.absolutePath)
            callBack?.selectFont(docItem.uri.toString())
        }.onSuccess {
            dismissAllowingStateLoss()
        }
    }

    private fun onDefaultFontChange() {
        callBack?.selectFont("")
    }

    private val callBack: CallBack?
        get() = (parentFragment as? CallBack) ?: (activity as? CallBack)

    interface CallBack {
        fun selectFont(path: String)
        val curFontPath: String
    }
}

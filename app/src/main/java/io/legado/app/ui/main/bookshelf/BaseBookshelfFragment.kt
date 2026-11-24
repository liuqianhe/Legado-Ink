package io.legado.app.ui.main.bookshelf

import android.annotation.SuppressLint
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.Book
import io.legado.app.databinding.DialogBookshelfConfigBinding
import io.legado.app.databinding.DialogBookshelfTitleBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.AppConfig
import io.legado.app.help.DirectLinkUpload
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.log.AppLogDialog
import io.legado.app.ui.book.arrange.ArrangeBookActivity
import io.legado.app.ui.book.cache.CacheActivity
import io.legado.app.ui.book.group.GroupManageDialog
import io.legado.app.ui.book.local.ImportBookActivity
import io.legado.app.ui.book.netbook.NetBookActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.document.HandleFileContract
import io.legado.app.ui.main.MainViewModel
import io.legado.app.utils.*

abstract class BaseBookshelfFragment(layoutId: Int) : VMBaseFragment<BookshelfViewModel>(layoutId) {

    val activityViewModel by activityViewModels<MainViewModel>()
    override val viewModel by viewModels<BookshelfViewModel>()

    private val importLocalBooks = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
            val bookUris = uris.filter { uri ->
                val name = DocumentFile.fromSingleUri(requireContext(), uri)?.name.orEmpty()
                name.endsWith(".txt", true) || name.endsWith(".epub", true) || name.endsWith(".umd", true)
            }
            if (bookUris.isEmpty()) {
                toastOnUi(R.string.empty_msg_import_book)
            } else {
                viewModel.importLocalBooks(bookUris)
            }
        }
    }

    private val importBookshelf = registerForActivityResult(HandleFileContract()) {
        kotlin.runCatching {
            it.uri?.readText(requireContext())?.let { text ->
                viewModel.importBookshelf(text, groupId)
            }
        }.onFailure {
            toastOnUi(it.localizedMessage ?: "ERROR")
        }
    }
    private val exportResult = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            alert(R.string.export_success) {
                if (uri.toString().isAbsUrl()) {
                    DirectLinkUpload.getSummary()?.let { summary ->
                        setMessage(summary)
                    }
                }
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.hint = getString(R.string.path)
                    editView.setText(uri.toString())
                }
                customView { alertBinding.root }
                okButton {
                    requireContext().sendToClip(uri.toString())
                }
            }
        }
    }
    abstract val groupId: Long
    abstract val books: List<Book>

    abstract fun gotoTop()

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.main_bookshelf, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        when (item.itemId) {
            R.id.menu_search -> startActivity<SearchActivity>()
            R.id.menu_update_toc -> activityViewModel.upToc(books)
            R.id.menu_bookshelf_layout -> configBookshelf()
            R.id.menu_group_manage -> showDialogFragment<GroupManageDialog>()
            R.id.menu_add_local -> startActivity<ImportBookActivity>()
            R.id.menu_net_book -> startActivity<NetBookActivity>()
            R.id.menu_add_url -> addBookByUrl()
            R.id.menu_arrange_bookshelf -> startActivity<ArrangeBookActivity> {
                putExtra("groupId", groupId)
            }
            R.id.menu_bookshelf_title -> configBookshelfTitle()
            R.id.menu_download -> startActivity<CacheActivity> {
                putExtra("groupId", groupId)
            }
            R.id.menu_export_bookshelf -> viewModel.exportBookshelf(books) {
                exportResult.launch {
                    mode = HandleFileContract.EXPORT
                    fileData = Triple(
                        "bookshelf.json",
                        it.toByteArray(),
                        "application/json"
                    )
                }
            }
            R.id.menu_import_bookshelf -> importBookshelfAlert(groupId)
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
        }
    }

    @SuppressLint("InflateParams")
    fun addBookByUrl() {
        alert(titleResource = R.string.add_book_url) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url"
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    viewModel.addBookByUrl(it)
                }
            }
            noButton()
        }
    }

    @SuppressLint("InflateParams")
    fun configBookshelf() {
        alert(titleResource = R.string.bookshelf_layout) {
            val bookshelfLayout = getPrefInt(PreferKey.bookshelfLayout)
            val bookshelfSort = getPrefInt(PreferKey.bookshelfSort)
            val alertBinding =
                DialogBookshelfConfigBinding.inflate(layoutInflater)
                    .apply {
                        spGroupStyle.setSelection(AppConfig.bookGroupStyle)
                        swShowUnread.isChecked = AppConfig.showUnread
                        rgLayout.checkByIndex(bookshelfLayout)
                        rgSort.checkByIndex(bookshelfSort)
                    }
            customView { alertBinding.root }
            okButton {
                alertBinding.apply {
                    if (AppConfig.bookGroupStyle != spGroupStyle.selectedItemPosition) {
                        AppConfig.bookGroupStyle = spGroupStyle.selectedItemPosition
                        postEvent(EventBus.NOTIFY_MAIN, false)
                    }
                    if (AppConfig.showUnread != swShowUnread.isChecked) {
                        AppConfig.showUnread = swShowUnread.isChecked
                        postEvent(EventBus.BOOKSHELF_REFRESH, "")
                    }
                    var changed = false
                    if (bookshelfLayout != rgLayout.getCheckedIndex()) {
                        putPrefInt(PreferKey.bookshelfLayout, rgLayout.getCheckedIndex())
                        changed = true
                    }
                    if (bookshelfSort != rgSort.getCheckedIndex()) {
                        putPrefInt(PreferKey.bookshelfSort, rgSort.getCheckedIndex())
                        changed = true
                    }
                    if (changed) {
                        postEvent(EventBus.RECREATE, "")
                    }
                }
            }
            noButton()
        }
    }


    @SuppressLint("InflateParams")
    fun configBookshelfTitle() {
        alert(titleResource = R.string.bookshelf_title) {
            val sizePref = getPrefInt(PreferKey.bookshelfTitleSize)
            val boldPref = getPrefBoolean(PreferKey.bookshelfTitleBold)
            val alertBinding = DialogBookshelfTitleBinding.inflate(layoutInflater).apply {
                dsbBookshelfTitleSize.max = 12
                dsbBookshelfTitleSize.progress = if (sizePref in 10..22) sizePref - 10 else 0
                dsbBookshelfTitleSize.valueFormat = { progress ->
                    if (progress == 0) getString(R.string.bookshelf_title_size_default)
                    else "${progress + 10}sp"
                }
                dsbBookshelfTitleSize.refreshValue()
                rgBookshelfTitleWeight.check(
                    if (boldPref) R.id.rb_bookshelf_title_bold else R.id.rb_bookshelf_title_normal
                )
            }
            customView { alertBinding.root }
            okButton {
                val progress = alertBinding.dsbBookshelfTitleSize.progress
                val newSize = if (progress == 0) 0 else progress + 10
                val newBold = alertBinding.rbBookshelfTitleBold.isChecked
                if (sizePref != newSize || boldPref != newBold) {
                    putPrefInt(PreferKey.bookshelfTitleSize, newSize)
                    putPrefBoolean(PreferKey.bookshelfTitleBold, newBold)
                    postEvent(EventBus.BOOKSHELF_REFRESH, "")
                }
            }
            noButton()
        }
    }

    private fun importBookshelfAlert(groupId: Long) {
        alert(titleResource = R.string.import_bookshelf) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url/json"
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    viewModel.importBookshelf(it, groupId)
                }
            }
            noButton()
            neutralButton(R.string.select_file) {
                importBookshelf.launch {
                    mode = HandleFileContract.FILE
                    allowExtensions = arrayOf("txt", "json")
                }
            }
        }
    }

}

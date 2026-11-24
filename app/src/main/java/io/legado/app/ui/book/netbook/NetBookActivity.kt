package io.legado.app.ui.book.netbook

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isGone
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.appDb
import io.legado.app.databinding.ActivityNetBookBinding
import io.legado.app.databinding.DialogNetBookServerBinding
import io.legado.app.help.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch

/**
 * 网络书籍：与原版页面结构一致，额外提供「保存本地书籍」目录选择。
 */
class NetBookActivity : VMBaseActivity<ActivityNetBookBinding, NetBookViewModel>(),
    NetBookAdapter.CallBack,
    SelectActionBar.CallBack,
    ServersDialog.Callback {

    override val binding by viewBinding(ActivityNetBookBinding::inflate)
    override val viewModel by viewModels<NetBookViewModel>()

    private val adapter by lazy { NetBookAdapter(this, this) }
    private var allFiles = listOf<RemoteFile>()
    private var groupMenu: SubMenu? = null
    private val searchView by lazy { binding.titleBar.findViewById<androidx.appcompat.widget.SearchView>(R.id.search_view) }

    private val saveLocalBooks = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            AppConfig.defaultBookTreeUri = it.toString()
            upSaveLocalText()
            toastOnUi(R.string.net_book_select_save_dir)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        searchView.queryHint = getString(R.string.screen) + " • " + getString(R.string.net_book)
        initView()
        initEvent()
        upSaveLocalText()

        viewModel.listLiveData.observe(this) { files ->
            binding.refreshProgressBar.isAutoLoading = false
            allFiles = files
            adapter.setItems(files)
            binding.tvEmptyMsg.isGone = files.isNotEmpty()
        }
        viewModel.errorLiveData.observe(this) { msg ->
            binding.refreshProgressBar.isAutoLoading = false
            if (!msg.isNullOrBlank()) toastOnUi(msg)
        }
        viewModel.serversLiveData.observe(this) { upPath() }

        if (AppConfig.defaultBookTreeUri.isNullOrBlank()) {
            alert(titleResource = R.string.net_book_save_local) {
                setMessage(R.string.net_book_save_local_hint)
                okButton { saveLocalBooks.launch(null) }
                cancelButton()
            }
        }
        upPath()
    }

    private fun initView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.selectActionBar.setMainActionText(R.string.add_to_shelf)
        binding.selectActionBar.setCallBack(this)
    }

    private fun initEvent() {
        binding.tvGoBack.setOnClickListener { goBackDir() }
        binding.btnSaveLocal.setOnClickListener {
            saveLocalBooks.launch(AppConfig.defaultBookTreeUri?.let { Uri.parse(it) })
        }
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                val key = newText.orEmpty()
                if (key.isBlank()) adapter.setItems(allFiles)
                else adapter.setItems(allFiles.filter { it.name.contains(key, true) })
                return false
            }
        })
    }

    private fun upSaveLocalText() {
        val uri = AppConfig.defaultBookTreeUri
        binding.btnSaveLocal.text = if (uri.isNullOrBlank()) {
            getString(R.string.net_book_save_local)
        } else {
            getString(R.string.net_book_save_local_set)
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.net_book, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_refresh -> upPath()
            R.id.menu_server_config -> showDialogFragment<ServersDialog>()
            R.id.menu_sort_name -> {
                item.isChecked = true
                sortCheck(NetBookViewModel.NetBookSort.Name)
                upPath()
            }
            R.id.menu_sort_time -> {
                item.isChecked = true
                sortCheck(NetBookViewModel.NetBookSort.Default)
                upPath()
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        groupMenu = menu.findItem(R.id.menu_sort)?.subMenu
        groupMenu?.setGroupCheckable(R.id.menu_group_sort, true, true)
        groupMenu?.findItem(R.id.menu_sort_name)?.isChecked =
            viewModel.sortKey == NetBookViewModel.NetBookSort.Name
        groupMenu?.findItem(R.id.menu_sort_time)?.isChecked =
            viewModel.sortKey == NetBookViewModel.NetBookSort.Default
        return super.onPrepareOptionsMenu(menu)
    }

    private fun sortCheck(key: NetBookViewModel.NetBookSort) {
        if (viewModel.sortKey == key) viewModel.sortAscending = !viewModel.sortAscending
        else {
            viewModel.sortAscending = true
            viewModel.sortKey = key
        }
    }

    private fun upPath() {
        binding.tvGoBack.isEnabled = viewModel.dirList.isNotEmpty()
        val path = buildString {
            append('/')
            viewModel.dirList.forEach { append(it.name).append('/') }
        }
        binding.tvPath.text = path
        adapter.selected.clear()
        if (!viewModel.isConfigured()) {
            binding.tvEmptyMsg.isGone = false
            return
        }
        binding.refreshProgressBar.isAutoLoading = true
        viewModel.loadList(viewModel.dirList.lastOrNull()?.path)
    }

    override fun openDir(item: RemoteFile) {
        viewModel.dirList.add(item)
        upPath()
    }

    private fun goBackDir(): Boolean {
        if (viewModel.dirList.isEmpty()) return false
        viewModel.dirList.removeAt(viewModel.dirList.lastIndex)
        upPath()
        return true
    }

    override fun onBackPressed() {
        if (!goBackDir()) super.onBackPressed()
    }

    override fun upCountView() {
        binding.selectActionBar.upCountView(adapter.selected.size, adapter.checkableCount)
    }

    override fun selectAll(selectAll: Boolean) = adapter.selectAll(selectAll)

    override fun revertSelection() = adapter.revertSelection()

    override fun onClickMainAction() {
        if (AppConfig.defaultBookTreeUri.isNullOrBlank()) {
            toastOnUi(R.string.net_book_save_local)
            return
        }
        val files = adapter.selected.toList()
        if (files.isEmpty()) return
        binding.refreshProgressBar.isAutoLoading = true
        viewModel.addToBookshelf(files) {
            binding.refreshProgressBar.isAutoLoading = false
            adapter.selected.clear()
            upPath()
        }
    }

    override fun startRead(item: RemoteFile) {
        appDb.bookDao.all.firstOrNull { it.originName == item.name }?.let { book ->
            startActivity<ReadBookActivity> { putExtra("bookUrl", book.bookUrl) }
        }
    }

    override fun addToBookShelfAgain(item: RemoteFile) {
        alert(R.string.sure, R.string.net_book_add_again) {
            yesButton {
                binding.refreshProgressBar.isAutoLoading = true
                viewModel.addToBookshelf(listOf(item)) {
                    binding.refreshProgressBar.isAutoLoading = false
                    upPath()
                }
            }
            noButton()
        }
    }

    override fun editServer(server: NetBookServer?) {
        val editing = server ?: NetBookServer()
        alert(titleResource = R.string.net_book_server_config) {
            val dialogBinding = DialogNetBookServerBinding.inflate(layoutInflater).apply {
                etName.setText(editing.name)
                etUrl.setText(editing.url)
                etAccount.setText(editing.account)
                etPassword.setText(editing.password)
                etPath.setText(editing.path)
                tvTest.setOnClickListener {
                    val temp = editing.copy(
                        url = etUrl.text?.toString().orEmpty().trim(),
                        path = etPath.text?.toString().orEmpty().trim(),
                        account = etAccount.text?.toString().orEmpty(),
                        password = etPassword.text?.toString().orEmpty()
                    )
                    launch {
                        runCatching { viewModel.testConnection(temp) }
                            .onSuccess { count ->
                                toastOnUi(
                                    if (count > 0) "${getString(R.string.net_book_connected)}（$count 本）"
                                    else getString(R.string.net_book_empty_hint)
                                )
                            }
                            .onFailure { toastOnUi(it.localizedMessage ?: "连接失败") }
                    }
                }
            }
            customView { dialogBinding.root }
            okButton {
                val url = dialogBinding.etUrl.text?.toString().orEmpty().trim()
                if (url.isBlank()) return@okButton
                val saved = editing.copy(
                    name = dialogBinding.etName.text?.toString().orEmpty().trim().ifBlank { url },
                    url = url,
                    path = dialogBinding.etPath.text?.toString().orEmpty().trim(),
                    account = dialogBinding.etAccount.text?.toString().orEmpty(),
                    password = dialogBinding.etPassword.text?.toString().orEmpty()
                )
                viewModel.upsertServer(saved)
                if (server == null) viewModel.selectServer(saved.id)
                upPath()
            }
            cancelButton()
        }
    }

    override fun onServersChanged() = upPath()
}

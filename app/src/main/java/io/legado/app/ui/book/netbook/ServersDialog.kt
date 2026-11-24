package io.legado.app.ui.book.netbook

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemNetBookServerBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyTint
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible

/** 网络书籍服务器管理：多选一 + 添加/编辑/删除 + 默认（备份 WebDAV）地址 */
class ServersDialog : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val viewModel by activityViewModels<NetBookViewModel>()
    private val adapter by lazy { ServersAdapter(requireContext()) }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setTitle(R.string.net_book_server_config)
        binding.toolBar.inflateMenu(R.menu.net_book_servers)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter
        adapter.setItems(viewModel.servers())
        viewModel.serversLiveData.observe(viewLifecycleOwner) { list ->
            adapter.setItems(list)
        }

        binding.tvFooterLeft.setText(R.string.net_book_default_server)
        binding.tvFooterLeft.visible()
        binding.tvFooterLeft.setOnClickListener {
            viewModel.selectServer(NetBookViewModel.DEFAULT_SERVER_ID)
            dismissAllowingStateLoss()
        }
        binding.tvCancel.visible()
        binding.tvCancel.setOnClickListener { dismissAllowingStateLoss() }
        binding.tvOk.visible()
        binding.tvOk.setOnClickListener {
            viewModel.selectServer(adapter.selectedId)
            dismissAllowingStateLoss()
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_add_server) {
            (activity as? Callback)?.editServer(null)
        }
        return true
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        (activity as? Callback)?.onServersChanged()
    }

    inner class ServersAdapter(context: Context) :
        RecyclerAdapter<NetBookServer, ItemNetBookServerBinding>(context) {

        var selectedId: Long = viewModel.currentServer()?.id ?: NetBookViewModel.DEFAULT_SERVER_ID

        override fun getViewBinding(parent: ViewGroup): ItemNetBookServerBinding =
            ItemNetBookServerBinding.inflate(inflater, parent, false)

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemNetBookServerBinding,
            item: NetBookServer,
            payloads: MutableList<Any>
        ) {
            binding.rbServer.text = item.name.ifBlank { item.url }
            binding.rbServer.isChecked = item.id == selectedId
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemNetBookServerBinding) {
            binding.rbServer.setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let {
                    selectedId = it.id
                    notifyDataSetChanged()
                }
            }
            binding.tvEdit.setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let {
                    (activity as? Callback)?.editServer(it)
                }
            }
            binding.tvDelete.setOnClickListener {
                getItemByLayoutPosition(holder.layoutPosition)?.let { server ->
                    alert {
                        setTitle(R.string.delete)
                        setMessage("${context.getString(R.string.sure_del)} ${server.name}?")
                        yesButton {
                            viewModel.deleteServer(server)
                            setItems(viewModel.servers())
                        }
                        noButton()
                    }
                }
            }
        }
    }

    interface Callback {
        fun editServer(server: NetBookServer?)
        fun onServersChanged()
    }
}

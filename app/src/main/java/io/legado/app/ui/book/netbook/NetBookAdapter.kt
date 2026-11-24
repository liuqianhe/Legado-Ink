package io.legado.app.ui.book.netbook

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppConst
import io.legado.app.databinding.ItemImportBookBinding
import io.legado.app.utils.StringUtils
import io.legado.app.utils.gone
import io.legado.app.utils.invisible
import io.legado.app.utils.visible

/** 与原版文件列表一致：文件夹可进入、书籍可多选、已在书架的可点开阅读 */
class NetBookAdapter(context: Context, val callBack: CallBack) :
    RecyclerAdapter<RemoteFile, ItemImportBookBinding>(context) {

    val selected = hashSetOf<RemoteFile>()
    var checkableCount = 0

    override fun getViewBinding(parent: ViewGroup): ItemImportBookBinding =
        ItemImportBookBinding.inflate(inflater, parent, false)

    override fun onCurrentListChanged() = upCheckableCount()

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemImportBookBinding,
        item: RemoteFile,
        payloads: MutableList<Any>
    ) {
        binding.run {
            if (payloads.isEmpty()) {
                if (item.isDir) {
                    ivIcon.setImageResource(R.drawable.ic_folder)
                    ivIcon.visible()
                    cbSelect.invisible()
                    llBrief.gone()
                    cbSelect.isChecked = false
                } else if (item.isOnBookShelf) {
                    ivIcon.setImageResource(R.drawable.ic_book_has)
                    ivIcon.visible()
                    cbSelect.invisible()
                    llBrief.visible()
                    tvTag.text = item.contentType.ifBlank { item.name.substringAfterLast('.') }
                    tvSize.text = if (item.size > 0) StringUtils.toSize(item.size) else ""
                    tvDate.text = if (item.lastModify > 0) AppConst.dateFormat.format(item.lastModify) else ""
                    cbSelect.isChecked = false
                } else {
                    ivIcon.invisible()
                    cbSelect.visible()
                    llBrief.visible()
                    tvTag.text = item.contentType.ifBlank { item.name.substringAfterLast('.') }
                    tvSize.text = if (item.size > 0) StringUtils.toSize(item.size) else ""
                    tvDate.text = if (item.lastModify > 0) AppConst.dateFormat.format(item.lastModify) else ""
                    cbSelect.isChecked = selected.contains(item)
                }
                tvName.text = item.name
            } else {
                cbSelect.isChecked = selected.contains(item)
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemImportBookBinding) {
        holder.itemView.setOnClickListener {
            getItem(holder.layoutPosition)?.let { item ->
                when {
                    item.isDir -> callBack.openDir(item)
                    item.isOnBookShelf -> callBack.startRead(item)
                    else -> {
                        if (selected.contains(item)) selected.remove(item) else selected.add(item)
                        notifyItemChanged(holder.layoutPosition, true)
                        callBack.upCountView()
                    }
                }
            }
        }
        holder.itemView.setOnLongClickListener {
            getItem(holder.layoutPosition)?.let { item ->
                if (item.isOnBookShelf) callBack.addToBookShelfAgain(item)
            }
            true
        }
    }

    private fun upCheckableCount() {
        checkableCount = getItems().count { !it.isDir && !it.isOnBookShelf }
        callBack.upCountView()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun selectAll(selectAll: Boolean) {
        if (selectAll) {
            getItems().forEach { if (!it.isDir && !it.isOnBookShelf) selected.add(it) }
        } else {
            selected.clear()
        }
        notifyDataSetChanged()
        callBack.upCountView()
    }

    fun revertSelection() {
        getItems().forEach {
            if (!it.isDir && !it.isOnBookShelf) {
                if (selected.contains(it)) selected.remove(it) else selected.add(it)
            }
        }
        notifyDataSetChanged()
        callBack.upCountView()
    }

    interface CallBack {
        fun openDir(item: RemoteFile)
        fun upCountView()
        fun startRead(item: RemoteFile)
        fun addToBookShelfAgain(item: RemoteFile)
    }
}

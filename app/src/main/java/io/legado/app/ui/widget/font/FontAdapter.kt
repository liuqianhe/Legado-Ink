package io.legado.app.ui.widget.font

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.ViewGroup
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.ItemFontBinding
import io.legado.app.utils.*
import timber.log.Timber
import java.io.File
import java.net.URLDecoder

class FontAdapter(context: Context, curFilePath: String, val callBack: CallBack) :
    RecyclerAdapter<FileDoc, ItemFontBinding>(context) {

    private val curName = URLDecoder.decode(curFilePath, "utf-8")
        .substringAfterLast(File.separator)

    override fun getViewBinding(parent: ViewGroup): ItemFontBinding {
        return ItemFontBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemFontBinding,
        item: FileDoc,
        payloads: MutableList<Any>
    ) {
        binding.run {
            if (item.isDir) {
                // 目录项：显示文件夹图标，用系统字体显示目录名，不加载字体、不显示勾选
                ivFolder.visible()
                tvFont.typeface = Typeface.DEFAULT
                tvFont.text = item.name
                ivChecked.invisible()
                return@run
            }
            // 字体项：用字体本身预览
            ivFolder.gone()
            kotlin.runCatching {
                val typeface: Typeface? = if (item.isContentScheme) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.contentResolver
                            .openFileDescriptor(item.uri, "r")
                            ?.fileDescriptor?.let {
                                Typeface.Builder(it).build()
                            }
                    } else {
                        Typeface.createFromFile(RealPathUtil.getPath(context, item.uri))
                    }
                } else {
                    Typeface.createFromFile(item.uri.toString())
                }
                tvFont.typeface = typeface
            }.onFailure {
                Timber.e(it)
                context.toastOnUi("Read ${item.name} Error: ${it.localizedMessage}")
            }
            tvFont.text = item.name
            if (item.name == curName) {
                ivChecked.visible()
            } else {
                ivChecked.invisible()
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemFontBinding) {
        holder.itemView.setOnClickListener {
            getItem(holder.layoutPosition)?.let {
                callBack.onClick(it)
            }
        }
    }

    interface CallBack {
        fun onClick(docItem: FileDoc)
    }
}

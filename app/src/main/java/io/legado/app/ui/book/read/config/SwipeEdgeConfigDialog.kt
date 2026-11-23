package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogSwipeEdgeBinding
import io.legado.app.help.InkSwipeConfig
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 侧滑感应区设置：用轨道 + 屏幕示意图调整左右边缘的感应宽度。
 * 左右各最多 30% 屏宽，中间始终保留翻页/选字区域。
 */
class SwipeEdgeConfigDialog : BaseDialogFragment(R.layout.dialog_swipe_edge) {

    private val binding by viewBinding(DialogSwipeEdgeBinding::bind)

    override fun onStart() {
        super.onStart()
        val dm = resources.displayMetrics
        setLayout((dm.widthPixels * 0.9f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.run {
            dsbEdgeWidth.max = InkSwipeConfig.maxEdgeWidth - InkSwipeConfig.minEdgeWidth
            dsbEdgeWidth.valueFormat = {
                getString(R.string.ink_swipe_edge_width_value, it + InkSwipeConfig.minEdgeWidth)
            }
            val current = InkSwipeConfig.edgeWidth
            dsbEdgeWidth.progress = current - InkSwipeConfig.minEdgeWidth
            dsbEdgeWidth.refreshValue()
            preview.edgeWidthDp = current
            dsbEdgeWidth.onChanged = { progress ->
                val width = progress + InkSwipeConfig.minEdgeWidth
                InkSwipeConfig.edgeWidth = width
                preview.edgeWidthDp = width
            }
            btnDone.setOnClickListener { dismissAllowingStateLoss() }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (parentFragment as? MoreConfigDialog.ReadPreferenceFragment)
            ?.refreshSwipeEdgeSummary()
    }
}

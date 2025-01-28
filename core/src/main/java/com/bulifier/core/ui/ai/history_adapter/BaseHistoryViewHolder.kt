package com.bulifier.core.ui.ai.history_adapter

import android.view.View
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bulifier.core.databinding.HistoryItemBinding
import com.bulifier.core.db.HistoryItem
import com.bulifier.core.db.HistoryStatus
import com.bulifier.core.ui.ai.HistoryViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import kotlin.math.min

abstract class BaseHistoryViewHolder(
    itemView: View,
    private val viewModel: HistoryViewModel,
    val viewLifecycleOwner: LifecycleOwner
) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(historyItem: HistoryItem?, position: Int)
    open fun onCreated() {}

    fun bindToolbar(
        binding: HistoryItemBinding,
        historyItem: HistoryItem?
    ) {
        binding.clickSpace.setOnClickListener {
            onToolbarClick(historyItem)
        }

        binding.date.text = historyItem?.lastUpdated?.toString()

        historyItem?.lastUpdated?.let {
            val simpleDateFormat = SimpleDateFormat.getDateInstance()
            val timeFormat = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT)
            binding.date.text = "${simpleDateFormat.format(it)} at ${timeFormat.format(it)}"
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val agentPrefix = if(historyItem?.schema == "agent"){
                "\uD83E\uDD16 "
            }else ""
            val title = (viewModel.getErrorMessages(historyItem?.promptId ?: -1)
                ?: historyItem?.prompt) ?: ""
            binding.title.text = agentPrefix + title.substring(0, min(title.length, 100))
        }
        binding.deleteButton.setOnClickListener {
            historyItem?.let {
                viewModel.deleteHistoryItem(historyItem)
            }
        }

        binding.status.text = if (historyItem == null) {
            "---"
        } else if (historyItem.progress == -1f) {
            when (historyItem.status) {
                HistoryStatus.ERROR -> "Error"
                HistoryStatus.PROMPTING -> "Prompting"
                HistoryStatus.SUBMITTED -> "Submitted"
                HistoryStatus.PROCESSING -> "Processing"
                HistoryStatus.RESPONDED -> "Success"
                HistoryStatus.RE_APPLYING -> "Re-Applying"
            }
        } else if (historyItem.progress == 1f) {
            "Success"
        } else {
            "${(historyItem.progress * 100).toInt()}%"
        }
    }

    abstract fun onToolbarClick(historyItem: HistoryItem?)
}
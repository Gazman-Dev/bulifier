package com.bulifier.core.ui.ai.history_adapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.bulifier.core.databinding.CoreHistoryItemBinding
import com.bulifier.core.db.HistoryItem
import com.bulifier.core.db.HistoryStatus
import com.bulifier.core.ui.ai.HistoryViewModel
import java.text.SimpleDateFormat
import kotlin.math.min

abstract class BaseHistoryViewHolder(
    itemView: View,
    private val viewModel: HistoryViewModel
) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(historyItem: HistoryItem?, position: Int)
    open fun onCreated() {}

    fun bindToolbar(
        binding: CoreHistoryItemBinding,
        historyItem: HistoryItem?
    ) {
        binding.clickSpace.setOnClickListener {
            onToolbarClick(historyItem)
        }

        SimpleDateFormat.SHORT
        binding.date.text = historyItem?.lastUpdated?.toString()

        historyItem?.lastUpdated?.let {
            val simpleDateFormat = SimpleDateFormat.getDateInstance()
            val timeFormat = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT)
            binding.date.text = "${simpleDateFormat.format(it)} at ${timeFormat.format(it)}"
        }

        binding.title.text =
            historyItem?.prompt?.substring(
                0,
                min(historyItem.prompt.length, 100)
            )
        binding.deleteButton.setOnClickListener {
            historyItem?.let {
                viewModel.deleteHistoryItem(historyItem)
            }
        }
        binding.status.text = when (historyItem?.status) {
            HistoryStatus.ERROR -> "Error"
            HistoryStatus.PROMPTING -> "Prompting"
            HistoryStatus.SUBMITTED -> "Loading"
            HistoryStatus.RESPONDED -> "Success"
            HistoryStatus.RE_APPLYING -> "Re-Applying"
            null -> "---"
        }
    }

    abstract fun onToolbarClick(historyItem: HistoryItem?)
}
package com.bulifier.core.ui.ai.history_adapter

import androidx.lifecycle.LifecycleOwner
import com.bulifier.core.databinding.HistoryItemBinding
import com.bulifier.core.db.HistoryItem
import com.bulifier.core.ui.ai.HistoryViewModel

class HistoryViewHolder(
    private val viewModel: HistoryViewModel,
    private val binding: HistoryItemBinding,
    viewLifecycleOwner: LifecycleOwner
) :
    BaseHistoryViewHolder(binding.root, viewModel, viewLifecycleOwner) {

    override fun bind(historyItem: HistoryItem?, position: Int) {
        val binding = binding
        bindToolbar(binding, historyItem)
    }

    override fun onToolbarClick(historyItem: HistoryItem?) {
        historyItem?.let {
            viewModel.selectDetailedItem(it)
        }
    }
}
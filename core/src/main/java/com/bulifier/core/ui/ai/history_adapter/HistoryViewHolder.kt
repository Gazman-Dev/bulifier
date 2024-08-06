package com.bulifier.core.ui.ai.history_adapter

import com.bulifier.core.databinding.CoreHistoryItemBinding
import com.bulifier.core.db.HistoryItem
import com.bulifier.core.ui.ai.HistoryViewModel

class HistoryViewHolder(
    private val viewModel: HistoryViewModel,
    private val binding: CoreHistoryItemBinding
) :
    BaseHistoryViewHolder(binding.root, viewModel) {

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
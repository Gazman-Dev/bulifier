package com.bulifier.core.ui.ai.history_adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bulifier.core.databinding.CoreHistoryItemBinding
import com.bulifier.core.databinding.CoreSelectedHistoryItemBinding
import com.bulifier.core.db.HistoryItemWithSelection
import com.bulifier.core.ui.ai.HistoryViewModel


class HistoryAdapter(
    private val viewModel: HistoryViewModel,
    private val historyList: RecyclerView,
    private val schemas: Array<String>,
    private val viewLifecycleOwner: LifecycleOwner
) :
    PagingDataAdapter<HistoryItemWithSelection, BaseHistoryViewHolder>(
        HistoryDiffCallback
    ) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseHistoryViewHolder {
        return if (viewType == 0) {
            SelectedHistoryViewHolder(
                viewModel,
                historyList,
                CoreSelectedHistoryItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                schemas,
                viewLifecycleOwner
            )
        } else {
            HistoryViewHolder(
                viewModel,
                CoreHistoryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }.apply {
            onCreated()
        }
    }

    override fun onBindViewHolder(holder: BaseHistoryViewHolder, position: Int) {
        holder.bind(getItem(position)?.historyItem, position)
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position)?.selected == true) 0 else 1
    }


    companion object {
        private val HistoryDiffCallback =
            object : DiffUtil.ItemCallback<HistoryItemWithSelection>() {
                override fun areItemsTheSame(
                    oldItem: HistoryItemWithSelection,
                    newItem: HistoryItemWithSelection
                ) =
                    oldItem.historyItem.promptId == newItem.historyItem.promptId

                override fun areContentsTheSame(
                    oldItem: HistoryItemWithSelection,
                    newItem: HistoryItemWithSelection
                ) =
                    oldItem == newItem
            }
    }
}



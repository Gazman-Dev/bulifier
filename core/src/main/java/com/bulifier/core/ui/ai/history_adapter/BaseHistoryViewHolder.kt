package com.bulifier.core.ui.ai.history_adapter

import android.icu.text.NumberFormat
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bulifier.core.databinding.HistoryItemBinding
import com.bulifier.core.db.HistoryItem
import com.bulifier.core.db.HistoryStatus
import com.bulifier.core.ui.ai.HistoryViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
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

        binding.cost.isVisible = historyItem?.cost != null
        binding.cost.text = historyItem?.cost?.let {
            "${formatDouble(it.toDouble())} tokens"
        } ?: run {
            ""
        }

        binding.date.text = historyItem?.lastUpdated?.toString()

        historyItem?.lastUpdated?.let {
            val simpleDateFormat = SimpleDateFormat.getDateInstance()
            val timeFormat = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT)
            binding.date.text = "${simpleDateFormat.format(it)} at ${timeFormat.format(it)}"
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val agentPrefix = if (historyItem?.schema == "agent") {
                "\uD83E\uDD16 "
            } else ""
            val title = (viewModel.getErrorMessages(historyItem?.promptId ?: -1)
                ?: historyItem?.prompt) ?: ""
            binding.title.text = agentPrefix + title.substring(0, min(title.length, 100))
        }
        binding.deleteButton.setOnClickListener {
            historyItem?.let {
                viewModel.deleteHistoryItem(historyItem)
            }
        }

        binding.status.text = when (historyItem?.status) {
            null -> "---"
            HistoryStatus.ERROR -> "Error"
            HistoryStatus.PROMPTING -> "Prompting"
            HistoryStatus.SUBMITTED -> "Submitted"
            HistoryStatus.PROCESSING -> "Processing"
            HistoryStatus.RESPONDED -> "Success"
            HistoryStatus.RE_APPLYING -> "Re-Applying"
        }

    }

    abstract fun onToolbarClick(historyItem: HistoryItem?)
}

private fun formatDouble(value: Double, locale: Locale = Locale.getDefault()): String {
    val formatter = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 2 // Set maximum decimal points to 2
        minimumFractionDigits = 0 // Optional: Avoid trailing zeros
        isGroupingUsed = true    // Use grouping separators (e.g., commas)
    }
    return formatter.format(value)
}
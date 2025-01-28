package com.bulifier.core.git.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bulifier.core.databinding.ItemCommitBinding
import com.bulifier.core.git.CommitInfo


class CommitsAdapter(private val onRevertClick: (CommitInfo) -> Unit) :
    PagingDataAdapter<CommitInfo, CommitsAdapter.CommitViewHolder>(DiffCallback) {

    object DiffCallback : DiffUtil.ItemCallback<CommitInfo>() {
        override fun areItemsTheSame(oldItem: CommitInfo, newItem: CommitInfo) =
            oldItem.commitHash == newItem.commitHash

        override fun areContentsTheSame(oldItem: CommitInfo, newItem: CommitInfo) =
            oldItem == newItem
    }

    override fun onBindViewHolder(holder: CommitViewHolder, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommitViewHolder {
        val binding = ItemCommitBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CommitViewHolder(binding)
    }

    inner class CommitViewHolder(private val binding: ItemCommitBinding) :
        RecyclerView.ViewHolder(binding.cardRoot) {
        private var commit: CommitInfo? = null;

        init {
            binding.commitMessage.setOnClickListener {
                val commit = commit ?: return@setOnClickListener
                AlertDialog.Builder(it.context)
                    .setTitle("Commit Details")
                    .setMessage(commit.commitMessage)
                    .setNegativeButton("Close", null)
                    .show()
            }

            binding.revertButton.setOnClickListener {
                val commit = commit ?: return@setOnClickListener
                onRevertClick(commit)
            }
        }

        fun bind(commit: CommitInfo) {
            this.commit = commit
            binding.commitMessage.text = commit.commitMessage
            binding.commitDate.text = commit.commitDate.toString()
        }
    }
}

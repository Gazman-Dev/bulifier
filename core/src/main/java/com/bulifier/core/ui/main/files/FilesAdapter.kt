package com.bulifier.core.ui.main.files

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import com.bulifier.core.databinding.FileItemBinding
import com.bulifier.core.db.File
import com.bulifier.core.ui.main.MainViewModel

class FilesAdapter(
    private val viewModel: MainViewModel
) : PagingDataAdapter<File, FileViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<File>() {
            override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
                return oldItem.fileId == newItem.fileId
            }

            override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        val binding = FileItemBinding.inflate(inflater, parent, false)
        return FileViewHolder(binding, viewModel)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}


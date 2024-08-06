package com.bulifier.core.ui.main.files

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bulifier.core.R
import com.bulifier.core.databinding.CoreFileItemBinding
import com.bulifier.core.db.File
import com.bulifier.core.ui.main.MainViewModel
import java.util.Locale

class FilesAdapter(
    private val viewModel: MainViewModel
) : PagingDataAdapter<File, ItemViewHolder>(DIFF_CALLBACK) {

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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        val binding = CoreFileItemBinding.inflate(inflater, parent, false)
        return ItemViewHolder(binding, viewModel)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}

class ItemViewHolder(
    private val binding: CoreFileItemBinding,
    private val viewModel: MainViewModel,
) :
    RecyclerView.ViewHolder(binding.root) {

    @SuppressLint("SetTextI18n")
    fun bind(file: File?) {
        binding.itemTitle.text = file?.fileName ?: "----"
        if (file?.isFile != false) {
            binding.itemIcon.setImageResource(R.drawable.core_baseline_file_24)
            binding.itemDetail.text = formatFileSize(file?.size)
            binding.root.setOnClickListener {
                file?.let {
                    viewModel.openFile(it)
                }
            }
        } else {
            binding.itemIcon.setImageResource(R.drawable.core_baseline_folder_24)
            binding.itemDetail.text = "${file.filesCount} Files"
            binding.root.setOnClickListener {
                viewModel.updatePath(
                    (if (file.path.isBlank()) {
                        file.fileName
                    } else {
                        "${file.path}/${file.fileName}"
                    })
                )
            }
        }
    }

    private fun formatFileSize(sizeInBytes: Int?): String {
        if (sizeInBytes == null) {
            return "..."
        }
        val sizeInMB = sizeInBytes / (1024.0 * 1024.0)
        val sizeInGB = sizeInBytes / (1024.0 * 1024.0 * 1024.0)

        return when {
            sizeInGB >= 1 -> String.format(Locale.US, "%.2f GB", sizeInGB)
            sizeInMB >= 1 -> String.format(Locale.US, "%.2f MB", sizeInMB)
            else -> "$sizeInBytes bytes"
        }
    }
}


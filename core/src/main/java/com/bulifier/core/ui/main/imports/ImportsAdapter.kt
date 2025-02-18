package com.bulifier.core.ui.main.imports

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bulifier.core.databinding.ItemImportBinding
import com.bulifier.core.db.File

class ImportAdapter(
    private val onRemove: (File) -> Unit
) : ListAdapter<File, ImportAdapter.ImportViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImportViewHolder {
        // Inflate the item layout via view binding (adjust if you’re not using view binding)
        val binding = ItemImportBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImportViewHolder(binding, onRemove)
    }

    override fun onBindViewHolder(holder: ImportViewHolder, position: Int) {
        val file = getItem(position)
        holder.bind(file)
    }

    class ImportViewHolder(
        private val binding: ItemImportBinding,
        private val onRemove: (File) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(file: File) {
            // Set the text to the full import path.
            binding.tvImport.text = file.fullPath
            binding.btnRemove.setOnClickListener {
                onRemove(file)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<File>() {
            override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
                // Assuming each File has a unique fileId.
                return oldItem.fileId == newItem.fileId
            }

            override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
                return oldItem == newItem
            }
        }
    }
}

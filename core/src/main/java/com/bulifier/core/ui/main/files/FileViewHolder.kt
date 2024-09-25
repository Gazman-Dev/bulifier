package com.bulifier.core.ui.main.files

import android.annotation.SuppressLint
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.bulifier.core.R
import com.bulifier.core.databinding.CoreFileItemBinding
import com.bulifier.core.db.File
import com.bulifier.core.ui.main.MainViewModel
import com.bulifier.core.ui.utils.showErrorDialog
import com.bulifier.core.ui.utils.showTextDialog
import java.util.Locale

class FileViewHolder(
    private val binding: CoreFileItemBinding,
    private val viewModel: MainViewModel,
) :
    RecyclerView.ViewHolder(binding.root) {


    private var file: File? = null

    init {
        binding.root.setOnLongClickListener {
            showMenu()
            true
        }

        binding.moreIcon.setOnClickListener {
            showMenu()
        }
    }

    private fun showMenu() {
        val file = file ?: return
        val options = mutableListOf(
            "Rename/Move",
            "Delete"
        )
        if (file.isFile) {
            options.add(0, "Copy Content")
        }
        AlertDialog.Builder(binding.root.context)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Rename/Move" -> {
                        val title = if (file.isFile) "Rename file" else "Rename folder"
                        val text = when {
                            file.path.isBlank() -> file.fileName
                            else -> file.path + "/" + file.fileName
                        }
                        showTextDialog(binding.root.context, text = text, title = title) {
                            if (!viewModel.renameFile(file, it)) {
                                showErrorDialog(
                                    binding.root.context,
                                    message = "Failed to rename file"
                                )
                            }
                        }
                    }

                    "Delete" -> {
                        viewModel.deleteFile(file)
                    }

                    "Copy Content" -> {
                        viewModel.loadContentToClipboard(file.fileId)
                    }

                    else -> throw Error("Options got messed up")
                }
            }
            .show()
    }

    @SuppressLint("SetTextI18n")
    fun bind(file: File?) {
        this.file = file
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
            binding.itemDetail.text = ""//"${file.filesCount} Files"
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
        val sizeInKB = sizeInBytes / 1024.0
        val sizeInMB = sizeInBytes / (1024.0 * 1024.0)
        val sizeInGB = sizeInBytes / (1024.0 * 1024.0 * 1024.0)

        return when {
            sizeInGB >= 1 -> String.format(Locale.US, "%.2f GB", sizeInGB)
            sizeInMB >= 1 -> String.format(Locale.US, "%.2f MB", sizeInMB)
            sizeInKB >= 1 -> String.format(Locale.US, "%.2f KB", sizeInKB)
            else -> "$sizeInBytes bytes"
        }
    }
}
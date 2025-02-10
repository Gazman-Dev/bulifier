package com.bulifier.core.ui.main.files

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.bulifier.core.R
import com.bulifier.core.databinding.FileItemBinding
import com.bulifier.core.db.File
import com.bulifier.core.ui.main.MainViewModel
import com.bulifier.core.ui.utils.FileType
import com.bulifier.core.ui.utils.getFileType
import com.bulifier.core.ui.utils.showErrorDialog
import com.bulifier.core.ui.utils.showTextDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class FileViewHolder(
    private val binding: FileItemBinding,
    private val viewModel: MainViewModel,
    private val uiScope: CoroutineScope
) :
    RecyclerView.ViewHolder(binding.root) {


    private var file: File? = null
    private val folderColor = ContextCompat.getColor(binding.root.context, R.color.folder_color)

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
        if (file.path.isBlank() && file.fileName == "schemas") {
            Toast.makeText(binding.root.context, "Schemas folder is protected", Toast.LENGTH_SHORT)
                .show()
            return
        }
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
                            if (!viewModel.moveFile(file, it)) {
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
        binding.itemIcon.setColorFilter(folderColor, PorterDuff.Mode.SRC_IN)

        if (file?.isFile == true) {
            binding.itemDetail.text = formatFileSize(file.size)
            if (file.isBinary) {
                when (getFileType(file.fileName)) {
                    FileType.IMAGE -> {
                        bindImage(file)
                    }
                    FileType.VIDEO -> {
                        bindVideo(file)
                    }
                    FileType.FONT -> {
                        bindFont(file)
                    }
                    FileType.PDF -> {
                        bindPDF(file)
                    }
                    FileType.OTHER -> {
                        bindOtherBinaries(file)
                    }
                }
            } else {
                Glide.with(binding.itemIcon)
                    .load(R.drawable.baseline_file_24)
                    .into(binding.itemIcon)
                binding.root.setOnClickListener {
                    file.let {
                        viewModel.openFile(it)
                    }
                }
            }
        } else {
            Glide.with(binding.itemIcon)
                .load(R.drawable.baseline_folder_24)
                .into(binding.itemIcon)
            binding.itemDetail.text = ""//"${file.filesCount} Files"
            binding.root.setOnClickListener {
                viewModel.updatePath(
                    (if (file?.path?.isBlank() == true) {
                        file.fileName
                    } else {
                        "${file?.path}/${file?.fileName}"
                    })
                )
            }
        }
    }

    private fun bindOtherBinaries(file: File) {
        Glide.with(binding.itemIcon)
            .load(R.drawable.baseline_device_unknown_24)
            .into(binding.itemIcon)

        binding.root.setOnClickListener {
            AlertDialog.Builder(itemView.context)
                .setTitle(file.fileName)
                .setPositiveButton("Options") { dialog, _ ->
                    showMenu()
                }
                .setNegativeButton("close", null)
                .show()
        }
    }

    private fun bindImage(file: File) {
        binding.root.setOnClickListener {
            showImagePreviewDialog(file)
        }
        Glide.with(binding.itemIcon.context)
            .load(file.getBinaryFile(binding.root.context))
            .placeholder(R.drawable.baseline_device_unknown_24)
            .error(R.drawable.baseline_device_unknown_24)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable?>?,
                    isFirstResource: Boolean
                ) = false

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable?>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.itemIcon.clearColorFilter()
                    return false
                }
            })
            .into(binding.itemIcon)
    }

    private fun showImagePreviewDialog(imageFile: File) {
        val context = itemView.context

        // Calculate max dimensions for the image
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        // Use 80% of screen size, for example
        val maxWidth = (screenWidth * 0.8f).toInt()
        val maxHeight = (screenHeight * 0.8f).toInt()

        val imageView = ImageView(context).apply {
            adjustViewBounds = true
        }

        // Use a container layout to add margins around the ImageView
        val container = FrameLayout(context).apply {
            setPadding(32, 32, 32, 32)
            addView(imageView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        Glide.with(context)
            .load(imageFile.getBinaryFile(context))
            // Restrict Glide to load a scaled version
            .override(maxWidth, maxHeight)
            // Or .fitCenter(), .centerCrop(), etc., depending on your UI preference
            .fitCenter()
            .into(imageView)

        AlertDialog.Builder(context)
            .setTitle(imageFile.fileName)
            // Instead of setting the ImageView directly, set the container
            .setView(container)
            .setPositiveButton("Options") { _, _ ->
                showMenu()
            }
            .setNegativeButton("Close", null)
            .show()
    }


    private fun bindVideo(file: File) {
        // Thumbnail for item icon using Glide
        Glide.with(binding.itemIcon.context)
            .asBitmap() // Fetch as a bitmap so we can display a frame thumbnail
            .load(file.getBinaryFile(binding.root.context))
            .placeholder(R.drawable.baseline_device_unknown_24)
            .error(R.drawable.baseline_device_unknown_24)
            .listener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Bitmap?>?,
                    isFirstResource: Boolean
                ) = false

                override fun onResourceReady(
                    resource: Bitmap?,
                    model: Any?,
                    target: Target<Bitmap?>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.itemIcon.clearColorFilter()
                    return false
                }
            })
            .into(binding.itemIcon)

        // Click action: show video preview dialog
        binding.root.setOnClickListener {
            showVideoPreviewDialog(file)
        }
    }

    private fun showVideoPreviewDialog(videoFile: File) {
        val context = itemView.context

        // VideoView to play the video
        val videoView = VideoView(context).apply {
            // Convert your File to a usable URI
            val uri = Uri.fromFile(videoFile.getBinaryFile(context))
            setVideoURI(uri)

            // Start playing when it’s ready
            setOnPreparedListener { mediaPlayer ->
                mediaPlayer.start()
            }
        }

        // Build and show an AlertDialog
        AlertDialog.Builder(context)
            .setTitle(videoFile.fileName)  // Use file name as dialog title
            .setView(videoView)
            .setPositiveButton("Options") { _, _ ->
                showMenu()
            }
            .setNegativeButton("Close", null)
            .show()
    }


    private fun formatFileSize(sizeInBytes: Long?): String {
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

    private fun bindFont(file: File) {
        // Show some icon or placeholder in itemIcon (e.g., a font icon).
        // Glide can't load font files directly, so we just display a drawable resource.
        Glide.with(binding.itemIcon.context)
            .load(R.drawable.baseline_font_download_24) // Replace with any placeholder icon
            .placeholder(R.drawable.baseline_device_unknown_24)
            .error(R.drawable.baseline_device_unknown_24)
            .into(binding.itemIcon)

        // On click: show font preview
        binding.root.setOnClickListener {
            showFontPreviewDialog(file)
        }
    }

    private fun showFontPreviewDialog(fontFile: File) {
        val context = itemView.context

        val textView = TextView(context).apply {
            text = "I love Bulifier! ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz 1234567890 !@#$%^&*()_+"
            textSize = 24f
            setPadding(32, 32, 32, 32)

            // Convert file to actual binary font file, then load it as a Typeface
            val actualFontFile = fontFile.getBinaryFile(context)
            typeface = Typeface.createFromFile(actualFontFile)
        }

        AlertDialog.Builder(context)
            .setTitle(fontFile.fileName)  // Use file name as dialog title
            .setView(textView)
            .setPositiveButton("Options") { _, _ ->
                showMenu()  // Replace with your actual method for additional actions
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun bindPDF(file: File) {
        Glide.with(binding.itemIcon.context)
            .load(R.drawable.baseline_picture_as_pdf_24) // Replace with any placeholder icon
            .placeholder(R.drawable.baseline_device_unknown_24)
            .error(R.drawable.baseline_device_unknown_24).skipMemoryCache(false)
            .into(binding.itemIcon)

        // When clicked, launch an external PDF viewer
        binding.root.setOnClickListener {
            uiScope.launch {
                openPdfExternally(file, binding.root.context)
            }
        }
    }

    private suspend fun openPdfExternally(pdfFile: File, context: Context) {
        // Use FileProvider for security; ensure you have a provider set up in your Manifest
        val file = pdfFile.getBinaryFile(context)
        val pdfFile = java.io.File(context.cacheDir, "pdf/bulifier.pdf")
        withContext(Dispatchers.IO) {
            file.copyTo(pdfFile, overwrite = true)
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider", // or your provider authority
            pdfFile
        )

        val pdfIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Present the chooser so user can pick their PDF viewer
        try {
            context.startActivity(Intent.createChooser(pdfIntent, "Open PDF with..."))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No PDF reader found.", Toast.LENGTH_SHORT).show()
        }
    }



}
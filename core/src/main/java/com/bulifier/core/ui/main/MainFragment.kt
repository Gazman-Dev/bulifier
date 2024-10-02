package com.bulifier.core.ui.main

import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bulifier.core.R
import com.bulifier.core.databinding.CoreMainFragmentBinding
import com.bulifier.core.prefs.Prefs
import com.bulifier.core.schemas.SchemaModel
import com.bulifier.core.ui.ai.HistoryViewModel
import com.bulifier.core.ui.core.BaseFragment
import com.bulifier.core.ui.main.files.FilesAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class TitleAction(val title: String, val action: () -> Unit)

class MainFragment : BaseFragment<CoreMainFragmentBinding>() {

    private val viewModel by activityViewModels<MainViewModel>()
    private val historyViewModel by activityViewModels<HistoryViewModel>()
    private val filesAdapter by lazy { FilesAdapter(viewModel) }
    private val errorPattern by lazy { Regex("[a-zA-Z0-9]+\\.") }
    private val fileNamePattern by lazy {
        Regex("^[a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)*$")
    }

    private val folderNamePattern by lazy {
        Regex("^[a-zA-Z0-9/.]+$")
    }

    private val callback by lazy {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (viewModel.openedFile.value != null) {
                    viewModel.closeFile()
                } else {
                    findNavController().popBackStack()
                }
            }
        }
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = CoreMainFragmentBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        registerPath()
        binding.toolbar.showProjects.setOnClickListener {
            findNavController().navigate(R.id.projectsFragment)
        }
        binding.toolbar.createFile.setOnClickListener {
            showCreateFileFolderDialog(true)
        }
        binding.toolbar.createFolder.setOnClickListener {
            showCreateFileFolderDialog(false)
        }
        binding.toolbar.jobs.setOnClickListener {
            findNavController().navigate(R.id.aiHistoryFragment)
        }
        binding.bottomBar.ai.setOnClickListener {
            viewModel.fullPath.value?.run {
                historyViewModel.createNewAiJob(path, fileName)
            }
            findNavController().navigate(R.id.aiHistoryFragment)
        }

        binding.bottomBar.downloadButton.setOnClickListener {
            viewModel.shareFiles()
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = filesAdapter

        binding.reloadSchemasButton.setOnClickListener {
            viewModel.reloadSchemas()
        }

        binding.resetSchemasButton.setOnClickListener {
            viewModel.resetSystemSchemas()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            Prefs.path.flow.combine(viewModel.openedFile){ path, openFile ->
                if(openFile != null){
                    null
                }
                else{
                    path
                }
            }.collectLatest {
                binding.reloadSchemasButton.isVisible = it == "schemas"
                binding.resetSchemasButton.isVisible = it == "schemas"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                viewModel.openedFile.collect {
                    val showFileContent = it != null
                    binding.recyclerView.isVisible = !showFileContent
                    binding.fileContent.isVisible = showFileContent
                    binding.bottomBar.toolbar.isVisible = !showFileContent
                }
            }

            launch {
                viewModel.pagingDataFlow.collect { pagingData ->
                    filesAdapter.submitData(lifecycle, pagingData)
                }
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(callback)
    }

    private fun registerPath() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.fullPath.collect {
                updatePath(it.path, it.fileName)
            }
        }
    }

    private fun updatePath(path: String, fileName: String?) {
        val pathParts = path.split("/")
        createClickableSpanString(binding.path, pathParts.mapIndexed { index, value ->
            TitleAction(value) {
                viewModel.closeFile()
                when {
                    index == 0 || pathParts.size < 2 -> {
                        viewModel.updatePath("")
                    }

                    index == pathParts.size - 1 -> {
                        // ignore
                    }

                    else -> {
                        viewModel.updatePath(pathParts.subList(1, index + 1).joinToString("/"))
                    }
                }
            }
        }.run {
            if (fileName != null) {
                this + TitleAction(fileName) {}
            } else {
                this
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        callback.remove()
    }

    private fun showCreateFileFolderDialog(isFile: Boolean) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(
                if (isFile) "Create New File" else "Create New Folder"
            )
            .setMessage(
                if (isFile) "Enter a name of a file, no spaces or special characters." else
                    "Enter a name of a folder, no spaces or special characters."
            )
            .setView(R.layout.core_dialog_create_file)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .show()

        val fileNameEditText = dialog.findViewById<EditText>(R.id.file_name)
        val createButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)

        fileNameEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val name = s.toString()
                if ((if (isFile) fileNamePattern else folderNamePattern).matches(name)) {
                    fileNameEditText.error = null
                    createButton.isEnabled = true
                } else {
                    if (name.isNotBlank() && (!isFile || needError(name))) {
                        fileNameEditText.error =
                            if (isFile) "Invalid file name" else "Invalid folder name"
                    }
                    createButton.isEnabled = false
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        createButton.setOnClickListener {
            fileNameEditText?.text?.toString()?.let {
                if (isFile) {
                    createFile(it)
                } else {
                    createFolder(it)
                }
            }
            dialog.dismiss()
        }
    }

    private fun needError(fileName: String): Boolean {
        return !errorPattern.matches(fileName)
    }

    // To be implemented
    private fun createFile(fileName: String) {
        viewModel.updateCreateFile(fileName)
    }

    private fun createFolder(folderName: String) {
        viewModel.createFolder(folderName)
    }


    private fun createClickableSpanString(textView: TextView, items: List<TitleAction>) {
        val separator = " / "
        // Concatenate all titles with the separator
        val fullText = items.joinToString(separator) { it.title }

        // Create a SpannableString
        val spannableString = SpannableString(fullText)

        var startIndex = 0
        items.forEachIndexed { _, item ->
            val endIndex = startIndex + item.title.length

            // Create a ClickableSpan for each item
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    item.action.invoke() // Invoke the click action
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false // Set to true if you want underlines
                }
            }

            // Set the ClickableSpan to the SpannableString
            spannableString.setSpan(
                clickableSpan,
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            // Update startIndex for the next item, considering the separator length
            startIndex = endIndex + separator.length
        }

        // Set the SpannableString to the TextView
        textView.text = spannableString

        // IMPORTANT: Enable movement method for handling clicks
        textView.movementMethod = LinkMovementMethod.getInstance()
    }


}
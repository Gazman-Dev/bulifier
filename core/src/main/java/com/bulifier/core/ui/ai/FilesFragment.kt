package com.bulifier.core.ui.ai

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import com.bulifier.core.R
import com.bulifier.core.databinding.FragmentFilesBinding
import com.bulifier.core.ui.core.BaseFragment
import com.bulifier.core.ui.main.MainViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class FilesFragment : BaseFragment<FragmentFilesBinding>() {

    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private val viewModel by activityViewModels<MainViewModel>()
    private val folderNamePattern by lazy {
        Regex("^[a-zA-Z0-9/.]+$")
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = FragmentFilesBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Set up the file picker launcher.
        filePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                viewModel.saveFile(data)
                (parentFragment as? AgentBottomSheet)?.dismiss()
                // Process the selected file URI here.
            }
        }

        binding.uploadButton.setOnClickListener {
            openFilePicker()
        }

        binding.createFolder.setOnClickListener {
            showCreateFileFolderDialog()
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*" // Adjust MIME types as needed.
        }
        filePickerLauncher.launch(intent)
    }

    private fun showCreateFileFolderDialog() {
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(
            "Create New Folder"
        ).setMessage(
            "Enter a name of a folder, no spaces or special characters."
        ).setView(R.layout.dialog_create_file).setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null).show()

        val fileNameEditText = dialog.findViewById<EditText>(R.id.file_name)
        val createButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)

        fileNameEditText?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                createButton?.performClick()
                true
            } else {
                false
            }
        }

        fileNameEditText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val name = s.toString()
                if (folderNamePattern.matches(name)) {
                    fileNameEditText.error = null
                    createButton.isEnabled = true
                } else {
                    if (name.isNotBlank()) {
                        fileNameEditText.error = "Invalid folder name"
                    }
                    createButton.isEnabled = false
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        createButton.setOnClickListener {
            fileNameEditText?.text?.toString()?.let {
                viewModel.createFolder(it)
            }
            dialog.dismiss()
        }
    }

}

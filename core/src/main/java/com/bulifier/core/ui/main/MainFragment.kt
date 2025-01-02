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
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bulifier.core.R
import com.bulifier.core.databinding.DialogSyncOptionsBinding
import com.bulifier.core.databinding.MainFragmentBinding
import com.bulifier.core.db.SyncMode
import com.bulifier.core.git.GitViewModel
import com.bulifier.core.navigation.findNavController
import com.bulifier.core.prefs.Prefs
import com.bulifier.core.security.ProductionSecurityFactory
import com.bulifier.core.security.UiVerifier
import com.bulifier.core.ui.GitUiHelper
import com.bulifier.core.ui.ai.AgentBottomSheet
import com.bulifier.core.ui.ai.AiHistoryFragment
import com.bulifier.core.ui.ai.HistoryViewModel
import com.bulifier.core.ui.ai.ModelsHelper
import com.bulifier.core.ui.core.BaseFragment
import com.bulifier.core.ui.main.files.FilesAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.getValue


data class TitleAction(val title: String, val action: () -> Unit)

@AndroidEntryPoint
class MainFragment : BaseFragment<MainFragmentBinding>() {

    @Inject
    lateinit var uiVerifier: UiVerifier

    private val viewModel by activityViewModels<MainViewModel>()
    private val historyModel by activityViewModels<HistoryViewModel>()
    private val gitViewModel by activityViewModels<GitViewModel>()
    private val filesAdapter by lazy { FilesAdapter(viewModel) }
    private val errorPattern by lazy { Regex("[a-zA-Z0-9]+\\.") }
    private val fileNamePattern by lazy {
        Regex("^[a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)*$")
    }

    private val security by lazy {
        EntryPointAccessors.fromApplication(
            binding.root.context.applicationContext, ProductionSecurityFactory::class.java
        )
    }

    private val folderNamePattern by lazy {
        Regex("^[a-zA-Z0-9/.]+$")
    }

    override fun createBinding(
        inflater: LayoutInflater, container: ViewGroup?
    ) = MainFragmentBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, null)
        binding.toolbar.settings.setOnClickListener {
            PopupMenu(requireContext(), binding.toolbar.settings).apply {
                inflate(R.menu.settings_menu)
                setOnMenuItemClickListener {
                    uiVerifier.verifyMenuAction(it, requireView())
                    true
                }
                show()
            }
        }
        binding.toolbar.showProjects.setOnClickListener {
            findNavController().navigate(ProjectsFragment::class.java)
        }
        binding.toolbar.createFile.setOnClickListener {
            showCreateFileFolderDialog(true)
        }
        binding.toolbar.createFolder.setOnClickListener {
            showCreateFileFolderDialog(false)
        }
        binding.toolbar.jobContainer.setOnClickListener {
            findNavController().navigate(AiHistoryFragment::class.java)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = filesAdapter

        binding.reloadSchemasButton.setOnClickListener {
            viewModel.reloadSchemas()
        }

        binding.resetSchemasButton.setOnClickListener {
            viewModel.resetSystemSchemas()
        }

        binding.aiFab.setOnClickListener {
            AgentBottomSheet().show(parentFragmentManager, "agent_bottom_sheet")
        }
        binding.syncFab.setOnClickListener {
            showSyncOptions()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            historyModel.progress.collectLatest { progress ->
                binding.toolbar.jobContainer.apply {
                    val isInProgress = progress != null && progress < 1

                    // Toggle visibility
                    binding.toolbar.jobProgress.isVisible = isInProgress
                    binding.toolbar.jobIcon.isVisible = !isInProgress

                    if (isInProgress) {
                        startDotAnimation(binding.toolbar.jobProgress, progress)
                    } else {
                        stopDotAnimation()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch{
            viewModel.fullScreenMode.collectLatest { fullScreenMode ->
                this@MainFragment.binding.toolbar.toolbar.isVisible = !fullScreenMode
                binding.pathContainer.isVisible = !fullScreenMode
                if (fullScreenMode) {
                    binding.syncFab.hide()
                    binding.aiFab.hide()
                } else {
                    binding.syncFab.show()
                    binding.aiFab.show()
                }
            }
        }
    }

    private var dotJob: Job? = null

    private fun startDotAnimation(textView: TextView, progress: Double) {
        dotJob?.cancel() // Cancel any existing animation job
        dotJob = viewLifecycleOwner.lifecycleScope.launch {
            var dots = ""
            while (isActive) {
                dots = when (dots.length) {
                    0 -> "."
                    1 -> ".."
                    2 -> "..."
                    else -> ""
                }
                textView.text = String.format(java.util.Locale.US, "%.2f%%", progress * 100) + dots
                delay(1000) // Wait for 1 second
            }
        }
    }

    // Function to stop dots animation
    private fun stopDotAnimation() {
        dotJob?.cancel()
    }

    private fun showSyncOptions() {
        val binding = DialogSyncOptionsBinding.inflate(LayoutInflater.from(requireContext()))
        var modelId: String? = null

        ModelsHelper(
            binding.modelSpinner, binding.modelSpinnerContainer, viewLifecycleOwner
        ) {
            modelId = it
        }.setupModels()

        val dialog =
            MaterialAlertDialogBuilder(requireContext()).setView(binding.syncOptionsContainer)
                .create()

        binding.syncButton.setOnClickListener {
            val syncAll = when (binding.syncModeRadioGroup.checkedRadioButtonId) {
                R.id.radioChangesOnly -> false
                R.id.radioAll -> true
                else -> false // Default to changes only
            }

            val syncBullets = binding.syncBulletsCheckBox.isChecked

            val uiVerifier: UiVerifier = security.uiVerifier()
            if (!uiVerifier.verifySendAction(binding.syncOptionsContainer)) {
                dialog.dismiss()
                return@setOnClickListener
            }

            if (modelId == null) {
                Snackbar.make(binding.syncButton, "Please select a model", Snackbar.LENGTH_SHORT)
                    .show()
            } else {
                viewLifecycleOwner.lifecycleScope.launch {
                    historyModel.sync(
                        if (syncBullets) {
                            SyncMode.BULLET
                        } else {
                            SyncMode.RAW
                        }, modelId, syncAll
                    ).let { result ->
                        if (!result) {
                            Snackbar.make(
                                this@MainFragment.binding.syncFab,
                                "No sync is needed - All is up to date",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }


    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        registerPath()
        GitUiHelper(
            binding.toolbar.git, gitViewModel, viewModel, viewLifecycleOwner
        )

        viewLifecycleOwner.lifecycleScope.launch {
            Prefs.path.flow.combine(viewModel.openedFile) { path, openFile ->
                if (openFile != null) {
                    null
                } else {
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
                }
            }

            launch {
                viewModel.pagingDataFlow.collect { pagingData ->
                    filesAdapter.submitData(lifecycle, pagingData)
                }
            }
        }
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

    private fun showCreateFileFolderDialog(isFile: Boolean) {
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(
            if (isFile) "Create New File" else "Create New Folder"
        ).setMessage(
            if (isFile) "Enter a name of a file, no spaces or special characters." else "Enter a name of a folder, no spaces or special characters."
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
                clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
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
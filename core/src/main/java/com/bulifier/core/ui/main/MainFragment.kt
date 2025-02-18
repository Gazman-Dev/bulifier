package com.bulifier.core.ui.main

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bulifier.core.R
import com.bulifier.core.databinding.DialogManageImportsBinding
import com.bulifier.core.databinding.DialogSyncOptionsBinding
import com.bulifier.core.databinding.MainFragmentBinding
import com.bulifier.core.db.File
import com.bulifier.core.db.SyncMode
import com.bulifier.core.git.GitViewModel
import com.bulifier.core.navigation.findNavController
import com.bulifier.core.prefs.AppSettings
import com.bulifier.core.security.ProductionSecurityFactory
import com.bulifier.core.security.UiVerifier
import com.bulifier.core.ui.GitUiHelper
import com.bulifier.core.ui.ai.AgentBottomSheet
import com.bulifier.core.ui.ai.AiHistoryFragment
import com.bulifier.core.ui.ai.HistoryViewModel
import com.bulifier.core.ui.ai.ModelsHelper
import com.bulifier.core.ui.core.BaseFragment
import com.bulifier.core.ui.main.files.FilesAdapter
import com.bulifier.core.ui.main.imports.ImportAdapter
import com.bulifier.core.utils.showToast
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

const val KEY_FILE_MODE = "fileMode"

@AndroidEntryPoint
class MainFragment : BaseFragment<MainFragmentBinding>() {

    @Inject
    lateinit var uiVerifier: UiVerifier

    private val viewModel by activityViewModels<MainViewModel>()
    private val historyModel by activityViewModels<HistoryViewModel>()
    private val gitViewModel by activityViewModels<GitViewModel>()
    private lateinit var filesAdapter: FilesAdapter
    private var currentGitColor = Color.Transparent.toArgb()
    private var colorAnimator: ValueAnimator? = null

    private val isFileMode by lazy {
        arguments?.getBoolean(KEY_FILE_MODE) == true
    }


    private val security by lazy {
        EntryPointAccessors.fromApplication(
            binding.root.context.applicationContext, ProductionSecurityFactory::class.java
        )
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
        binding.toolbar.jobContainer.setOnClickListener {
            findNavController().navigate(AiHistoryFragment::class.java)
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        filesAdapter = FilesAdapter(viewModel, viewLifecycleOwner.lifecycleScope)
        binding.recyclerView.adapter = filesAdapter

        binding.reloadSchemasButton.setOnClickListener {
            viewModel.reloadSchemas()
        }

        binding.imports.setOnClickListener {
            showManageImportsDialog()
        }

        binding.resetSchemasButton.setOnClickListener {
            viewModel.resetSystemSchemas()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            gitViewModel.branch.collectLatest {
                binding.toolbar.gitBranch.text = it
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            gitViewModel.gitStatus.collectLatest { status ->
                val endColor = when (status) {
                    GitViewModel.GitStatus.IDLE -> Color.Transparent.toArgb()
                    GitViewModel.GitStatus.PROCESSING -> Color(1f, 1f, 0f, 0.5f).toArgb() // Yellow
                    GitViewModel.GitStatus.SUCCESS -> Color(0f, 1f, 0f, 0.5f).toArgb() // Green
                    GitViewModel.GitStatus.ERROR -> Color(1f, 0f, 0f, 0.5f).toArgb() // Red
                }

                // Animate the color change smoothly
                animateColorChange(
                    binding.toolbar.git,
                    R.id.background,
                    endColor
                )
            }
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
                    val isInProgress = progress != null && progress > 0

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

        viewLifecycleOwner.lifecycleScope.launch {
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

        if (isFileMode) {
            updateFileContentView(viewModel.openedFile.value != null)
        }
    }

    private var dotJob: Job? = null

    @SuppressLint("SetTextI18n")
    private fun startDotAnimation(textView: TextView, progress: Double) {
        dotJob?.cancel() // Cancel any existing animation job
        dotJob = viewLifecycleOwner.lifecycleScope.launch {
            var dots = ""
            while (isActive) {
                dots = when (dots.length) {
                    0 -> "_"
                    1 -> "__"
                    2 -> "___"
                    else -> ""
                }
                val spaces = " ".repeat(3 - dots.length)
                val formattedProgress = if (progress % 1.0 == 0.0) {
                    String.format(java.util.Locale.US, "%.0f", progress)
                } else {
                    String.format(java.util.Locale.US, "%.2f", progress)
                }
                textView.text = "$spaces$dots$formattedProgress$dots$spaces"
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
                    if (gitViewModel.autoCommit()) {
                        gitViewModel.commit("Pre sync - syncAll=$syncAll syncBullets=$syncBullets")
                    }
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
            binding.toolbar.git,
            binding.toolbar.gitContainer,
            gitViewModel,
            viewModel,
            viewLifecycleOwner,
            parentFragmentManager
        )

        viewLifecycleOwner.lifecycleScope.launch {
            AppSettings.path.flow.combine(viewModel.openedFile) { path, openFile ->
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
                viewModel.openedFile.collect { openedFile ->
                    updateFileContentView(openedFile != null)
                }
            }

            launch {
                AppSettings.project.collectLatest { project ->
                    updateFileContentView(viewModel.openedFile.value != null)
                }
            }

            launch {
                viewModel.pagingDataFlow.collectLatest { pagingData ->
                    filesAdapter.submitData(lifecycle, pagingData)
                }
            }
        }
    }

    private fun updateFileContentView(isFileOpen: Boolean) {
        if (isFileMode && !isFileOpen) {
            findNavController().popBackStack()
            return
        }
        binding.recyclerView.isVisible = !isFileOpen
        binding.fileContent.isVisible = isFileOpen
        binding.imports.isVisible = isFileOpen

        uiVerifier.verifyRunButton(binding.runButton, isFileOpen)
        uiVerifier.verifyReleaseButton(binding.releaseButton, isFileOpen)
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

    fun animateColorChange(imageView: ImageView, layerId: Int, endColor: Int) {
        val drawable = imageView.drawable as? LayerDrawable
        val layer = drawable?.findDrawableByLayerId(layerId)?.mutate()

        if (layer != null) {
            // Use ObjectAnimator for a smooth color transition
            colorAnimator?.cancel()
            val colorAnimator = ObjectAnimator.ofArgb(currentGitColor, endColor)
            colorAnimator.duration = 300 // Animation duration in milliseconds
            colorAnimator.setEvaluator(ArgbEvaluator())
            colorAnimator.addUpdateListener { animator ->
                val animatedColor = animator.animatedValue as Int
                layer.setTint(animatedColor)
                currentGitColor = animatedColor
                imageView.invalidateDrawable(drawable) // Ensure the drawable is redrawn
            }
            colorAnimator.start()
            this.colorAnimator = colorAnimator
        }
    }

    private fun showManageImportsDialog() {
        // Inflate the dialog view using view binding.
        val binding = DialogManageImportsBinding.inflate(LayoutInflater.from(requireContext()))

        // This mutable list will be our local copy to support optimistic UI updates.
        val currentImports = mutableListOf<File>()

        // Set up the RecyclerView adapter. Note that when the user clicks the X button,
        // we update the local list immediately before calling the ViewModel.
        lateinit var adapter: ImportAdapter
        adapter = ImportAdapter { file ->
            // Optimistically remove the file.
            currentImports.remove(file)
            adapter.submitList(currentImports.toList())
            viewModel.removeImport(file)
        }
        binding.recyclerImports.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerImports.adapter = adapter

        // Show the dialog immediately.
        AlertDialog.Builder(requireContext())
            .setTitle("Manage Imports")
            .setView(binding.root)
            .setPositiveButton("Done", null)
            .show()

        // Load the initial list of current imports from the database.
        lifecycleScope.launch {
            val importsFromDb = viewModel.getImports()
            currentImports.clear()
            currentImports.addAll(importsFromDb)
            adapter.submitList(currentImports.toList())
        }

        // Prepare available suggestions.
        var availableFiles: List<File> = emptyList()
        lifecycleScope.launch {
            availableFiles = viewModel.getAllImports() // suspend function returning List<File>
// Map each File to its full path string.
            val suggestionStrings = availableFiles.map { it.fullPath }
            val suggestionsAdapter = ImportSuggestionsAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                suggestionStrings
            )
            binding.autoCompleteImport.setAdapter(suggestionsAdapter)
            binding.autoCompleteImport.threshold = 1  // start suggesting after one character
        }

        // When the user clicks the Add button, update the list optimistically.
        binding.btnAddImport.setOnClickListener {
            val input = binding.autoCompleteImport.text.toString().trim()
            if (input.isNotEmpty()) {
                // Look up the File object corresponding to the full path string.
                val fileToAdd = availableFiles.find { it.fullPath == input }
                if (fileToAdd != null && !currentImports.contains(fileToAdd)) {
                    // Optimistic addition: update the local list first.
                    currentImports.add(fileToAdd)
                    adapter.submitList(currentImports.toList())
                    viewModel.addImport(fileToAdd)
                    binding.autoCompleteImport.text.clear()
                } else {
                    showToast("Import not found")
                }
            }
        }
    }
}
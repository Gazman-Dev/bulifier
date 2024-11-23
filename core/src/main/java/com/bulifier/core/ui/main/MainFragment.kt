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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bulifier.core.BuildConfig
import com.bulifier.core.R
import com.bulifier.core.databinding.MainFragmentBinding
import com.bulifier.core.databinding.PopupCloneBinding
import com.bulifier.core.databinding.PopupPushBinding
import com.bulifier.core.git.GitError
import com.bulifier.core.git.GitViewModel
import com.bulifier.core.navigation.findNavController
import com.bulifier.core.prefs.Prefs
import com.bulifier.core.security.UiVerifier
import com.bulifier.core.ui.ai.AiHistoryFragment
import com.bulifier.core.ui.ai.HistoryViewModel
import com.bulifier.core.ui.core.BaseFragment
import com.bulifier.core.ui.main.files.FilesAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import javax.inject.Inject


data class TitleAction(val title: String, val action: () -> Unit)

@AndroidEntryPoint
class MainFragment : BaseFragment<MainFragmentBinding>() {

    @Inject
    lateinit var uiVerifier: UiVerifier

    private val viewModel by activityViewModels<MainViewModel>()
    private val gitViewModel by activityViewModels<GitViewModel>()
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

    override fun onResume() {
        super.onResume()
        callback.isEnabled = true
    }

    override fun onPause() {
        callback.isEnabled = false
        super.onPause()
    }

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = MainFragmentBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
        binding.toolbar.jobs.setOnClickListener {
            findNavController().navigate(AiHistoryFragment::class.java)
        }
        binding.bottomBar.ai.setOnClickListener {
            viewModel.fullPath.value.run {
                historyViewModel.createNewAiJob(path, fileName)
            }
            findNavController().navigate(AiHistoryFragment::class.java)
        }

        binding.bottomBar.gitButton.setOnClickListener {
            PopupMenu(requireContext(), binding.bottomBar.gitButton).apply {
                inflate(R.menu.git_menu)

                val isCloneNeeded = gitViewModel.isCloneNeeded()
                menu.forEach {
                    it.isEnabled = !isCloneNeeded || it.itemId == R.id.clone
                }

                setForceShowIcon(true)
                setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.clone -> clone()
                        R.id.checkout -> checkout()
                        R.id.pull -> gitViewModel.pull()
                        R.id.push -> gitViewModel.push()
                        R.id.commit -> commit()
                        R.id.clear -> clear()
                        else -> Unit
                    }
                    true
                }
                show()
            }
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = filesAdapter

        binding.reloadSchemasButton.setOnClickListener {
            viewModel.reloadSchemas()
        }

        binding.resetSchemasButton.setOnClickListener {
            viewModel.resetSystemSchemas()
        }

        requireActivity().onBackPressedDispatcher.addCallback(callback)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        registerPath()
        viewLifecycleOwner.lifecycleScope.launch {
            gitViewModel.gitErrors.collect { gitError ->
                when (gitError.type) {
                    else -> showGeneralError(gitError)
                }
            }
        }

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
                    binding.bottomBar.toolbar.isVisible = !showFileContent
                }
            }

            launch {
                viewModel.pagingDataFlow.collect { pagingData ->
                    filesAdapter.submitData(lifecycle, pagingData)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            gitViewModel.gitInfo.collect {
                binding.bottomBar.info.text = it
                delay(500) // allow some time to read the message
            }
        }
    }

    private fun clear() {
        AlertDialog.Builder(requireContext()).apply {
            setTitle("Hard Reset & Clean")
            setMessage("Remove all uncommited changed")
            setPositiveButton("Clean") { _, _ ->
                gitViewModel.clean()
            }
            setCancelable(false)
            show()
        }
    }

    private fun showGeneralError(gitError: GitError) {
        AlertDialog.Builder(requireContext()).apply {
            setTitle(gitError.title)
            setMessage(gitError.message)
            setPositiveButton("Ok") { _, _ ->
            }
            setCancelable(false)
            show()
        }
    }


    private fun commit() {
        val binding = PopupPushBinding.inflate(layoutInflater)
        AlertDialog.Builder(requireActivity())
            .setTitle("Commit to local")
            .setView(binding.root)
            .setPositiveButton("Commit") { _, _ ->
                val commitMessage = binding.commitMessage.text.toString()
                    .ifEmpty { "Made some changes..." }
                gitViewModel.commit(commitMessage)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkout() {
        val checkoutDialogManager = CheckoutDialogManager(
            context = requireContext(),
            lifecycleOwner = viewLifecycleOwner,
            gitViewModel = gitViewModel
        )
        checkoutDialogManager.showDialog()
    }

    private fun clone() {
        viewLifecycleOwner.lifecycleScope.launch {
            val binding = PopupCloneBinding.inflate(layoutInflater)

            val popup = AlertDialog.Builder(requireActivity())
                .setTitle("Clone Repository") // Placeholder title
                .setView(binding.root)
                .setPositiveButton("Clone", null)
                .setNegativeButton("Cancel", null)
                .create()

            val projectEmpty = viewModel.isProjectEmpty()

            popup.show()

            setupAutoCleanErrors(binding, popup, projectEmpty)
            val cloneButton = popup.getButton(AlertDialog.BUTTON_POSITIVE)
            cloneButton.setOnClickListener {
                // Handle "Clone" action here
                val repoUrl = binding.repoUrl.text.toString()
                val username = binding.username.text.toString()
                val passwordToken = binding.passwordToken.text.toString()

                // Check if any field is empty, and if so, mark it with an error
                var isValid = true

                if (repoUrl.isEmpty()) {
                    binding.repoUrl.error = "Repository URL is required"
                    isValid = false
                } else {
                    binding.repoUrl.error = null
                }

                if (username.isEmpty()) {
                    binding.username.error = "Username is required"
                    isValid = false
                } else {
                    binding.username.error = null
                }

                if (passwordToken.isEmpty()) {
                    binding.passwordToken.error = "Password/Token is required"
                    isValid = false
                } else {
                    binding.passwordToken.error = null
                }

                if (isValid) {
                    gitViewModel.clone(repoUrl, username, passwordToken)
                    popup.dismiss()
                } else {
                    cloneButton.isEnabled = false
                }
            }

            // Retrieve credentials and fill the fields
            gitViewModel.getCredentials().apply {
                if (this is UsernamePasswordCredentialsProvider) {
                    val password = CredentialItem.Password()
                    val username = CredentialItem.Username()
                    get(URIish(), password, username)
                    binding.username.setText(username.value)
                    binding.passwordToken.setText(String(password.value))
                }
            }


            val title = if (projectEmpty && viewModel.wasProjectJustUpdated()) {
                "Set Up Git Repository"
            } else {
                "Clone Repository"
            }
            popup.setTitle(title)

            binding.overwriteProjectFiles.isVisible = !projectEmpty
            binding.overwriteProjectFiles.setOnCheckedChangeListener { _, _ ->
                maybeEnableCloneButton(projectEmpty, binding, popup)
            }
            binding.username.setText(BuildConfig.GIT_USERNAME)
            binding.passwordToken.setText(BuildConfig.GIT_PASSWARD)
            binding.repoUrl.setText(BuildConfig.GIT_REPO)
            maybeEnableCloneButton(projectEmpty, binding, popup)
        }
    }

    private fun maybeEnableCloneButton(
        projectEmpty: Boolean,
        binding: PopupCloneBinding,
        popup: AlertDialog
    ) {
        val button = popup.getButton(AlertDialog.BUTTON_POSITIVE)
        button.isEnabled = projectEmpty || binding.overwriteProjectFiles.isChecked
    }

    private fun setupAutoCleanErrors(
        binding: PopupCloneBinding,
        popup: AlertDialog,
        projectEmpty: Boolean
    ) {
        for (editText in listOf(binding.repoUrl, binding.username, binding.passwordToken)) {
            editText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    maybeEnableCloneButton(projectEmpty, binding, popup)
                    editText.error = null
                }

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                }
            })
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
            .setView(R.layout.dialog_create_file)
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
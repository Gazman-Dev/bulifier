package com.bulifier.core.ui

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bulifier.core.BuildConfig
import com.bulifier.core.R
import com.bulifier.core.databinding.PopupCloneBinding
import com.bulifier.core.databinding.PopupPushBinding
import com.bulifier.core.git.GitError
import com.bulifier.core.git.GitViewModel
import com.bulifier.core.ui.main.CheckoutDialogManager
import com.bulifier.core.ui.main.MainViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.eclipse.jgit.transport.CredentialItem
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

class GitUiHelper(
    private val gitButton: View,
    private val gitViewModel: GitViewModel,
    private val viewModel: MainViewModel,
    private val lifeCycleOwner: LifecycleOwner
) {

    init {
        gitButton.setOnClickListener {
            PopupMenu(gitButton.context, gitButton).apply {
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

        lifeCycleOwner.lifecycleScope.launch {
            gitViewModel.gitErrors.collect { gitError ->
                when (gitError.type) {
                    else -> showGeneralError(gitError)
                }
            }
        }

        lifeCycleOwner.lifecycleScope.launch {
            gitViewModel.gitUpdates.collect {
                Snackbar.make(gitButton, it, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun clear() {
        AlertDialog.Builder(gitButton.context).apply {
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
        AlertDialog.Builder(gitButton.context).apply {
            setTitle(gitError.title)
            setMessage(gitError.message)
            setPositiveButton("Ok") { _, _ ->
            }
            setCancelable(false)
            show()
        }
    }


    private fun commit() {
        val binding = PopupPushBinding.inflate(LayoutInflater.from(gitButton.context))
        AlertDialog.Builder(gitButton.context)
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
            context = gitButton.context,
            lifecycleOwner = lifeCycleOwner,
            gitViewModel = gitViewModel
        )
        checkoutDialogManager.showDialog()
    }

    private fun clone() {
        lifeCycleOwner.lifecycleScope.launch {
            val binding = PopupCloneBinding.inflate(LayoutInflater.from(gitButton.context))

            val popup = AlertDialog.Builder(gitButton.context)
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

    private fun maybeEnableCloneButton(
        projectEmpty: Boolean,
        binding: PopupCloneBinding,
        popup: AlertDialog
    ) {
        val button = popup.getButton(AlertDialog.BUTTON_POSITIVE)
        button.isEnabled = projectEmpty || binding.overwriteProjectFiles.isChecked
    }
}
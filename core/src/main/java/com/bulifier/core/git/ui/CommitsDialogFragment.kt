package com.bulifier.core.git.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bulifier.core.databinding.FragmentCommitsPopupBinding
import com.bulifier.core.git.GitViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CommitsDialogFragment : DialogFragment() {

    private var binding: FragmentCommitsPopupBinding? = null

    private val viewModel: GitViewModel by activityViewModels() // Replace with your ViewModel provider
    private val adapter = CommitsAdapter { commit ->
        // Handle revert logic here
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm Revert")
            .setMessage(
                "All local changes will be deleted, and the branch will be reverted to the commit below:\n\n" +
                        "\"${commit.commitMessage}\""
            )
            .setPositiveButton("Revert") { _, _ ->
                viewModel.cleanAndRevert(commit.commitHash)
                dismiss()
            }
            .setNegativeButton("Cancel", null) // Dismiss dialog on cancel
            .show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = FragmentCommitsPopupBinding.inflate(inflater, container, false).apply {
        binding = this
    }.commitsRecyclerView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup RecyclerView
        binding?.commitsRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        binding?.commitsRecyclerView?.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.commits.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Optional: Style the dialog as desired
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.8).toInt()
        )
    }
}

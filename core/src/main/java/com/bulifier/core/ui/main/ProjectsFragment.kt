package com.bulifier.core.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bulifier.core.R
import com.bulifier.core.databinding.CoreProjectsFragmentBinding
import com.bulifier.core.db.Project
import com.bulifier.core.ui.core.BaseFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class ProjectsFragment : BaseFragment<CoreProjectsFragmentBinding>() {

    private val mainViewModel by activityViewModels<MainViewModel>()

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = CoreProjectsFragmentBinding.inflate(inflater, container, false)


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.createButton.setOnClickListener {
            createProject(binding.textBox.text.toString())
        }
        binding.textBox.setOnEditorActionListener { textBox, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                createProject(textBox.text.toString())
                true
            } else {
                false
            }
        }
        setupProjectsList()
    }

    private fun setupProjectsList() {
        val projectsAdapter = ProjectsAdapter(mainViewModel) {
            viewLifecycleOwner.lifecycleScope.launch {
                mainViewModel.selectProject(it)
                findNavController().navigate(R.id.mainFragment)
            }
        }
        binding.projectsList.adapter = projectsAdapter
        binding.projectsList.layoutManager = LinearLayoutManager(context)
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.projectsFlow.collectLatest { pagingData ->
                projectsAdapter.submitData(pagingData)
            }
        }
    }

    private fun createProject(projectName: String) {
        lifecycleScope.launch {
            mainViewModel.createProject(projectName)
            findNavController().navigate(R.id.mainFragment)
        }
    }

}
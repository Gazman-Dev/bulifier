package com.bulifier.core.ui.main

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bulifier.core.databinding.ProjectsFragmentBinding
import com.bulifier.core.git.GitViewModel
import com.bulifier.core.navigation.findNavController
import com.bulifier.core.ui.core.BaseFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProjectsFragment : BaseFragment<ProjectsFragmentBinding>() {

    private val mainViewModel by activityViewModels<MainViewModel>()
    private val gitViewModel by activityViewModels<GitViewModel>()

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = ProjectsFragmentBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.createButton.setOnClickListener {
            createOrOpenProject(binding.textBox.text.toString())
        }
        binding.toolbar.backButton.setOnClickListener{
            findNavController().popBackStack()
        }
        if(findNavController().isBackStackEmpty()){
            binding.toolbar.backButton.isVisible = false
        }

        binding.textBox.setOnEditorActionListener { textBox, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                createOrOpenProject(textBox.text.toString())
                true
            } else {
                false
            }
        }

        binding.textBox.addTextChangedListener(object : TextWatcher {
            private var job: Job? = null

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Do nothing
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                job?.cancel()
                val projectName = s.toString()
                if (projectName.isBlank()) {
                    binding.createButton.text = "Create"
                    return
                }
                job = viewLifecycleOwner.lifecycleScope.launch {
                    val exists = mainViewModel.isProjectExists(projectName)
                    if (exists) {
                        binding.createButton.text = "Open"
                    } else {
                        binding.createButton.text = "Create"
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {
                binding.textBox.error = null
            }
        })

        setupProjectsList()
    }

    private fun setupProjectsList() {
        val projectsAdapter = ProjectsAdapter(mainViewModel, gitViewModel) {
            viewLifecycleOwner.lifecycleScope.launch {
                mainViewModel.selectProject(it)
                findNavController().navigate(
                    MainFragment::class.java,
                    clearBackStack = true,
                    cacheFragment = true
                )
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

    private fun createOrOpenProject(projectName: String) {
        if (projectName.isBlank()) {
            binding.textBox.error = "Project name cannot be empty"
            return
        }
        lifecycleScope.launch {
            mainViewModel.createOrSelectProject(projectName)
            findNavController().navigate(
                MainFragment::class.java,
                clearBackStack = true,
                cacheFragment = true
            )
        }
    }
}

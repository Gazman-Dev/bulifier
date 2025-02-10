package com.bulifier.core.ui.main

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bulifier.core.databinding.ProjectsFragmentBinding
import com.bulifier.core.db.Project
import com.bulifier.core.git.GitViewModel
import com.bulifier.core.navigation.findNavController
import com.bulifier.core.ui.core.BaseFragment
import com.bulifier.core.ui.navigateToMain
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProjectsFragment : BaseFragment<ProjectsFragmentBinding>() {

    private val mainViewModel by activityViewModels<MainViewModel>()
    private val gitViewModel by activityViewModels<GitViewModel>()
    private val templates by lazy {
        listOf("Use Template", "Default") + loadTemplates()
    }
    private var lastSelectedProjectName = ""

    private var projectNames = emptyList<String>()

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = ProjectsFragmentBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.createButton.setOnClickListener {
            createOrOpenProject(binding.projectNameInput.text.toString())
        }
        binding.toolbar.backButton.setOnClickListener {
            findNavController().popBackStack()
        }
        if (findNavController().isBackStackEmpty()) {
            binding.toolbar.backButton.isVisible = false
        }

        viewLifecycleOwner.lifecycleScope.launch {
            projectNames = mainViewModel.projectNames()
        }

        setupTemplates()

        setupProjectName()
        setupProjectsList()
    }

    private fun setupTemplates() {
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, templates)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.templates.adapter = adapter
    }

    private fun loadTemplates(): List<String> {
        return try {
            val files = requireContext().assets.list("templates")
            files?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun setupProjectName() {
        binding.projectNameInput.setOnEditorActionListener { textBox, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                createOrOpenProject(textBox.text.toString())
                true
            } else {
                false
            }
        }

        binding.projectNameInput.addTextChangedListener(object : TextWatcher {
            private var job: Job? = null

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Do nothing
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val projectName = s.toString()
                if (projectName in projectNames) {
                    binding.createButton.text = "Open & Update"
                    if (lastSelectedProjectName != projectName) {
                        lastSelectedProjectName = projectName
                        job?.cancel()
                        job = viewLifecycleOwner.lifecycleScope.launch {
                            mainViewModel.getProject(projectName)?.let {
                                updateProject(it)
                            }
                        }
                    }
                } else {
                    binding.createButton.text = "Create"
                    lastSelectedProjectName = ""
                }

            }

            override fun afterTextChanged(s: Editable?) {
                binding.projectNameInput.error = null
            }
        })
    }

    private fun setupProjectsList() {
        val projectsAdapter = ProjectsAdapter(mainViewModel, gitViewModel) {
            viewLifecycleOwner.lifecycleScope.launch {
                updateProject(it)
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

    private fun updateProject(project: Project) {
        binding.projectNameInput.setText(project.projectName)
        binding.projectDetailsInput.setText(project.projectDetails)
        val templateIndex = templates.indexOf(project.template)
        if (templateIndex != -1) {
            binding.templates.setSelection(templateIndex)
        }
    }

    private fun createOrOpenProject(projectName: String) {
        if (projectName.isBlank()) {
            binding.projectNameInput.error = "Project name cannot be empty"
            return
        }
        if (binding.templates.selectedItemPosition == 0) {
            Toast.makeText(requireContext(), "Please select a template", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val projectDetails = binding.projectDetailsInput.text.toString().trim().ifBlank { null }
            mainViewModel.createUpdateOrSelectProject(
                projectName, projectDetails,
                if (binding.templates.selectedItemPosition < 2) null else binding.templates.selectedItem.toString()
            )
            navigateToMain()
        }
    }
}

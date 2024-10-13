package com.bulifier.core.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bulifier.core.databinding.CoreItemProjectBinding
import com.bulifier.core.db.Project
import com.bulifier.core.git.GitViewModel

class ProjectsAdapter(
    private val mainViewModel: MainViewModel,
    private val gitViewModel: GitViewModel,
    private val onItemClick: (Project) -> Unit
) :
    PagingDataAdapter<Project, ProjectsAdapter.ProjectViewHolder>(ProjectComparator) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ProjectViewHolder(
        CoreItemProjectBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    )

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        getItem(position)?.let { project ->
            holder.binding.apply {
                projectNameTextView.text = project.projectName
                deleteButton.setOnClickListener {
                    AlertDialog.Builder(root.context).apply {
                        setTitle("Delete Project")
                        setMessage("Are you sure you want to delete the project? This action will delete all files, logs, and history related to this project.")
                        setPositiveButton("Delete") { _, _ ->
                            mainViewModel.deleteProject(project)
                            gitViewModel.deleteProject(project.projectName)
                        }
                        setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }
                    }.create().show()
                }
            }
            holder.itemView.setOnClickListener { onItemClick(project) }
        }
    }

    class ProjectViewHolder(val binding: CoreItemProjectBinding) :
        RecyclerView.ViewHolder(binding.root)

    object ProjectComparator : DiffUtil.ItemCallback<Project>() {
        override fun areItemsTheSame(oldItem: Project, newItem: Project): Boolean {
            return oldItem.projectId == newItem.projectId
        }

        override fun areContentsTheSame(oldItem: Project, newItem: Project): Boolean {
            return oldItem == newItem
        }
    }
}
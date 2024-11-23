package com.bulifier.core.ui.main

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bulifier.core.databinding.DialogCheckoutBinding
import com.bulifier.core.git.GitViewModel
import kotlinx.coroutines.launch

class CheckoutDialogManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val gitViewModel: GitViewModel
) {

    private lateinit var dialog: AlertDialog
    private lateinit var binding: DialogCheckoutBinding
    private lateinit var adapter: ArrayAdapter<String>

    private var currentItems: List<String> = emptyList()
    private var branches: List<String> = emptyList()
    private var tags: List<String> = emptyList()

    fun showDialog() {
        val inflater = LayoutInflater.from(context)
        binding = DialogCheckoutBinding.inflate(inflater)
        adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, mutableListOf())
        binding.listViewItems.adapter = adapter

        val builder = AlertDialog.Builder(context)
            .setTitle("Checkout")
            .setView(binding.root)
            .setPositiveButton("Checkout") { _, _ ->
                val name = binding.editTextBranchTag.text.toString()
                val isExisting = currentItems.contains(name)
                val isNewBranch = !isExisting
                gitViewModel.checkout(name, isNewBranch)
            }
            .setNegativeButton("Cancel", null)

        dialog = builder.create()
        dialog.setOnShowListener {
            updateActionButton()
        }
        dialog.show()

        loadBranchesAndTags()
        setupListeners()
    }

    private fun loadBranchesAndTags() {
        lifecycleOwner.lifecycleScope.launch {
            gitViewModel.fetch()
            branches = gitViewModel.getBranches()
            tags = gitViewModel.getTags()

            // Initially display branches
            currentItems = branches
            updateListView()
        }
    }

    private fun setupListeners() {
        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                binding.radioBranches.id -> {
                    currentItems = branches
                    updateListView()
                }

                binding.radioTags.id -> {
                    currentItems = tags
                    updateListView()
                }
            }
            updateActionButton()
        }

        binding.listViewItems.setOnItemClickListener { _, _, position, _ ->
            val selectedName = currentItems[position]
            binding.editTextBranchTag.setText(selectedName)
            updateActionButton()
        }

        binding.editTextBranchTag.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateActionButton()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun updateListView() {
        adapter.clear()
        adapter.addAll(currentItems)
    }

    private fun updateActionButton() {
        val name = binding.editTextBranchTag.text.toString().lowercase().trim()
        val isExisting = tags.contains(name) || branches.contains(name)
        val actionButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        actionButton.text = if (isExisting) "Checkout" else "Checkout New"
        actionButton.isEnabled = name.isNotBlank()
    }
}


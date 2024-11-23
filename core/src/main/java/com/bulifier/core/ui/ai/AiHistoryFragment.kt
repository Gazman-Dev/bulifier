package com.bulifier.core.ui.ai

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bulifier.core.databinding.AiHistoryFragmentBinding
import com.bulifier.core.navigation.findNavController
import com.bulifier.core.ui.ai.history_adapter.HistoryAdapter
import com.bulifier.core.ui.core.BaseFragment
import com.bulifier.core.ui.main.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AiHistoryFragment : BaseFragment<AiHistoryFragmentBinding>() {

    private val viewModel: HistoryViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var adapter: HistoryAdapter

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = AiHistoryFragmentBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d("PagingAdapter", "AiHistoryFragment onViewCreated")
        viewLifecycleOwner.lifecycleScope.launch {
            adapter = HistoryAdapter(viewModel, binding.historyList, viewLifecycleOwner)
            binding.historyList.adapter = adapter
            Log.d("PagingAdapter", "New emission from historySource")
            viewModel.historySource.collectLatest { pagingData ->
                Log.d("PagingAdapter", "Submitting new PagingData: $pagingData")
                adapter.submitData(viewLifecycleOwner.lifecycle, pagingData)
            }
        }

        binding.historyList.layoutManager = LinearLayoutManager(requireContext())
        binding.toolbar.backButton.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.toolbar.newButton.setOnClickListener {
            mainViewModel.fullPath.value.run {
                viewModel.createNewAiJob(path, fileName)
            }
        }
    }

    override fun onStop() {
        viewModel.saveToDraft()
        super.onStop()
    }

}
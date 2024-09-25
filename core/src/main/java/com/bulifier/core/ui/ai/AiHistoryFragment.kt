package com.bulifier.core.ui.ai

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.util.fastForEachIndexed
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.bulifier.core.databinding.CoreAiHistoryFragmentBinding
import com.bulifier.core.schemas.SchemaModel
import com.bulifier.core.ui.ai.history_adapter.HistoryAdapter
import com.bulifier.core.ui.core.BaseFragment
import com.bulifier.core.ui.main.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiHistoryFragment : BaseFragment<CoreAiHistoryFragmentBinding>() {

    private val viewModel: HistoryViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: HistoryAdapter

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = CoreAiHistoryFragmentBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("PagingAdapter", "AiHistoryFragment onViewCreated")
        viewLifecycleOwner.lifecycleScope.launch {
            val schemas = withContext(Dispatchers.IO) {
                SchemaModel.getSchemaNames()
            }
            adapter = HistoryAdapter(viewModel, binding.historyList, schemas, viewLifecycleOwner)
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
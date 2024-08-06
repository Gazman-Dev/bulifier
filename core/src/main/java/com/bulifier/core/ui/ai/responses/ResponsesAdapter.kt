package com.bulifier.core.ui.ai.responses

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bulifier.core.databinding.CoreResponseItemBinding
import com.bulifier.core.db.ResponseItem

class ResponsesAdapter(val responses: List<ResponseItem>) :
    RecyclerView.Adapter<ResponsesAdapter.ResponsesViewHolder>() {

    class ResponsesViewHolder(val binding: CoreResponseItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(content: String) {
            binding.content.text = content
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ResponsesViewHolder(
        CoreResponseItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ResponsesViewHolder, position: Int) {
        holder.bind(getContent(position))
    }

    fun getContent(position: Int) = if (position % 2 == 0) {
        responses[position / 2].response
    } else {
        responses[position / 2].request
    }.replace(
            "\\r\\n", "\n"
    )

    override fun getItemCount(): Int = responses.size * 2


}

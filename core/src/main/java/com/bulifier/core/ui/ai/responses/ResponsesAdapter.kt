package com.bulifier.core.ui.ai.responses

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bulifier.core.databinding.ResponseItemBinding
import com.bulifier.core.db.ResponseItem

class ResponsesAdapter(val responses: List<ResponseItem>) :
    RecyclerView.Adapter<ResponsesAdapter.ResponsesViewHolder>() {

    class ResponsesViewHolder(val binding: ResponseItemBinding) :
        RecyclerView.ViewHolder(binding.content) {
        fun bind(content: String) {
            binding.content.setText(content)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ResponsesViewHolder(
        ResponseItemBinding.inflate(LayoutInflater.from(parent.context), parent, false).apply {
            content.setIsWrapped(true)
            content.isEditable = false
        }
    )

    override fun onBindViewHolder(holder: ResponsesViewHolder, position: Int) {
        holder.bind(getContent(position))
    }

    fun getContent(position: Int): String {
        return if (position % 2 == 0) {
            responses[position / 2].response
        } else {
            responses[position / 2].request
        }
            .replace("\\\\r\\\\n", "\n") // Handle Windows-style line breaks
            .replace("\\\\n", "\n")       // Handle Unix-style line breaks
            .replace("\\u0027", "'")      // Convert unicode apostrophes
            .replace("\\\"", "\"")        // Convert escaped double quotes
            .replace("\\r\\n", "\n")      // Handle leftover \r\n
            .replace("\\n", "\n")         // Handle leftover \n
            .trim()                       // Trim any extra spaces or line breaks
    }


    override fun getItemCount(): Int = responses.size * 2


}

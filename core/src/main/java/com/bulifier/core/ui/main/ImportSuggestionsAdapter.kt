package com.bulifier.core.ui.main

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import java.util.Locale

class ImportSuggestionsAdapter(
    context: Context,
    resource: Int,
    private val allItems: List<String>
) : ArrayAdapter<String>(context, resource, allItems), Filterable {

    // This will hold the filtered list.
    private var filteredItems: List<String> = allItems

    override fun getCount(): Int = filteredItems.size

    override fun getItem(position: Int): String? = filteredItems[position]

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val results = FilterResults()
                if (constraint.isNullOrEmpty()) {
                    results.values = allItems
                    results.count = allItems.size
                } else {
                    val query = constraint.toString().lowercase(Locale.getDefault())
                    // Filter items by checking if the query is contained anywhere in the string
                    val filteredList = allItems.filter {
                        it.lowercase(Locale.getDefault()).contains(query)
                    }
                    results.values = filteredList
                    results.count = filteredList.size
                }
                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredItems = results?.values as? List<String> ?: emptyList()
                notifyDataSetChanged()
            }
        }
    }
}
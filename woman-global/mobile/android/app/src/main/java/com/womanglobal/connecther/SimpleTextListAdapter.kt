package com.womanglobal.connecther

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SimpleTextListAdapter(
    private val items: List<String>,
    private val onDelete: ((Int) -> Unit)?,
) : RecyclerView.Adapter<SimpleTextListAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(android.R.id.text1)
        val delete: ImageButton = view.findViewById(android.R.id.button1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_simple_text_row, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.text.text = items[position]
        holder.delete.visibility = if (onDelete != null) View.VISIBLE else View.GONE
        holder.delete.setOnClickListener { onDelete?.invoke(position) }
    }

    override fun getItemCount(): Int = items.size
}

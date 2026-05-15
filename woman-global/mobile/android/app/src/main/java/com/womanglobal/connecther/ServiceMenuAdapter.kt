package com.womanglobal.connecther

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.womanglobal.connecther.booking.ServiceMenuRow

class ServiceMenuAdapter(
    private val rows: MutableList<ServiceMenuRow>,
    private val onAnyChange: () -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SECTION = 0
        private const val TYPE_QUANTITY = 1
        private const val TYPE_TOGGLE = 2
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int =
        when (rows[position]) {
            is ServiceMenuRow.Section -> TYPE_SECTION
            is ServiceMenuRow.QuantityLine -> TYPE_QUANTITY
            is ServiceMenuRow.ToggleLine -> TYPE_TOGGLE
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SECTION ->
                SectionVH(inflater.inflate(R.layout.item_service_menu_section, parent, false))
            TYPE_QUANTITY ->
                QtyVH(inflater.inflate(R.layout.item_service_menu_quantity, parent, false))
            else ->
                ToggleVH(inflater.inflate(R.layout.item_service_menu_toggle, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is ServiceMenuRow.Section -> (holder as SectionVH).bind(row)
            is ServiceMenuRow.QuantityLine -> (holder as QtyVH).bind(row, position)
            is ServiceMenuRow.ToggleLine -> (holder as ToggleVH).bind(row, position)
        }
    }

    private inner class SectionVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title = itemView.findViewById<TextView>(R.id.textSectionTitle)
        private val subtitle = itemView.findViewById<TextView>(R.id.textSectionSubtitle)

        fun bind(row: ServiceMenuRow.Section) {
            title.text = row.title
            if (row.subtitle.isNullOrBlank()) {
                subtitle.visibility = View.GONE
            } else {
                subtitle.visibility = View.VISIBLE
                subtitle.text = row.subtitle
            }
        }
    }

    private inner class QtyVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumb = itemView.findViewById<ImageView>(R.id.imageThumb)
        private val textTitle = itemView.findViewById<TextView>(R.id.textTitle)
        private val textUnit = itemView.findViewById<TextView>(R.id.textUnitLabel)
        private val textPrice = itemView.findViewById<TextView>(R.id.textPrice)
        private val textQty = itemView.findViewById<TextView>(R.id.textQty)
        private val minus = itemView.findViewById<MaterialButton>(R.id.buttonMinus)
        private val plus = itemView.findViewById<MaterialButton>(R.id.buttonPlus)

        fun bind(row: ServiceMenuRow.QuantityLine, position: Int) {
            textTitle.text = row.title
            if (row.unitLabel.isBlank()) {
                textUnit.visibility = View.GONE
            } else {
                textUnit.visibility = View.VISIBLE
                textUnit.text = row.unitLabel
            }
            textPrice.text = itemView.context.getString(R.string.service_menu_line_price, row.unitPrice)
            textQty.text = row.quantity.toString()
            bindThumb(thumb, row.imageUrl)
            minus.setOnClickListener {
                if (row.quantity > row.min) {
                    row.quantity--
                    notifyItemChanged(position)
                    onAnyChange()
                }
            }
            plus.setOnClickListener {
                if (row.quantity < row.max) {
                    row.quantity++
                    notifyItemChanged(position)
                    onAnyChange()
                }
            }
        }
    }

    private inner class ToggleVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumb = itemView.findViewById<ImageView>(R.id.imageThumb)
        private val textTitle = itemView.findViewById<TextView>(R.id.textTitle)
        private val textUnit = itemView.findViewById<TextView>(R.id.textUnitLabel)
        private val textPrice = itemView.findViewById<TextView>(R.id.textPrice)
        private val check = itemView.findViewById<MaterialCheckBox>(R.id.checkToggle)

        fun bind(row: ServiceMenuRow.ToggleLine, position: Int) {
            textTitle.text = row.title
            if (row.unitLabel.isBlank()) {
                textUnit.visibility = View.GONE
            } else {
                textUnit.visibility = View.VISIBLE
                textUnit.text = row.unitLabel
            }
            textPrice.text = itemView.context.getString(R.string.service_menu_line_price, row.unitPrice)
            bindThumb(thumb, row.imageUrl)
            check.setOnCheckedChangeListener(null)
            check.isChecked = row.checked
            check.setOnCheckedChangeListener { _, isChecked ->
                row.checked = isChecked
                onAnyChange()
            }
        }
    }

    private fun bindThumb(image: ImageView, url: String?) {
        if (url.isNullOrBlank()) {
            image.visibility = View.GONE
        } else {
            image.visibility = View.VISIBLE
            Glide.with(image.context).load(url).centerCrop().into(image)
        }
    }
}

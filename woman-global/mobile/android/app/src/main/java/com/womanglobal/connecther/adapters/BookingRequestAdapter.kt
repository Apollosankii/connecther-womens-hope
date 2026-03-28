package com.womanglobal.connecther.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.womanglobal.connecther.R
import com.womanglobal.connecther.supabase.SupabaseData

class BookingRequestAdapter(
    private val items: List<SupabaseData.MyBookingRequest>,
    private val onAccept: ((SupabaseData.MyBookingRequest) -> Unit)? = null,
    private val onDecline: ((SupabaseData.MyBookingRequest) -> Unit)? = null,
    private val onCancel: ((SupabaseData.MyBookingRequest) -> Unit)? = null,
    private val onOpenMaps: ((SupabaseData.MyBookingRequest) -> Unit)? = null,
) : RecyclerView.Adapter<BookingRequestAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.bookingTitle)
        val status: TextView = view.findViewById(R.id.bookingStatus)
        val details: TextView = view.findViewById(R.id.bookingDetails)
        val accept: Button = view.findViewById(R.id.btnAccept)
        val decline: Button = view.findViewById(R.id.btnDecline)
        val cancel: Button = view.findViewById(R.id.btnCancel)
        val maps: Button = view.findViewById(R.id.btnMaps)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_booking_request_action_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val req = items[position]
        val otherParty = if (req.role == "provider") req.client_display else req.provider_display
        holder.title.text = "Request from ${otherParty ?: "client"}"
        holder.status.text = "Status: ${req.status}"
        val servicePart = req.service_id?.toString()?.let { "Service #$it" } ?: ""
        val createdPart = req.created_at?.take(16)?.let { "Created $it" }
        val pricePart = req.proposed_price?.let { "KES $it" }
        holder.details.text = listOfNotNull(servicePart, req.location_text, pricePart, createdPart).joinToString(" • ")

        holder.accept.visibility = if (onAccept != null && req.status.equals("pending", ignoreCase = true)) View.VISIBLE else View.GONE
        holder.decline.visibility = if (onDecline != null && req.status.equals("pending", ignoreCase = true)) View.VISIBLE else View.GONE
        holder.cancel.visibility = if (onCancel != null && req.status.equals("pending", ignoreCase = true)) View.VISIBLE else View.GONE
        holder.maps.visibility = if (onOpenMaps != null) View.VISIBLE else View.GONE

        holder.accept.setOnClickListener { onAccept?.invoke(req) }
        holder.decline.setOnClickListener { onDecline?.invoke(req) }
        holder.cancel.setOnClickListener { onCancel?.invoke(req) }
        holder.maps.setOnClickListener { onOpenMaps?.invoke(req) }
    }

    override fun getItemCount(): Int = items.size
}


package com.womanglobal.connecther.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.womanglobal.connecther.R
import com.womanglobal.connecther.supabase.SupabaseData
import java.util.Locale

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
        val requestId: TextView = view.findViewById(R.id.bookingRequestId)
        val details: TextView = view.findViewById(R.id.bookingDetails)
        val actionsPrimary: View = view.findViewById(R.id.actionsRowPrimary)
        val actionsSecondary: View = view.findViewById(R.id.actionsRowSecondary)
        val accept: MaterialButton = view.findViewById(R.id.btnAccept)
        val decline: MaterialButton = view.findViewById(R.id.btnDecline)
        val cancel: MaterialButton = view.findViewById(R.id.btnCancel)
        val maps: MaterialButton = view.findViewById(R.id.btnMaps)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_booking_request_action_row, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val req = items[position]
        val ctx = holder.itemView.context
        val otherParty = if (req.role == "provider") req.client_display else req.provider_display
        val name = otherParty?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.booking_request_party_unknown)
        holder.title.text = ctx.getString(R.string.booking_request_title_with_name, name)
        holder.status.applyBookingStatus(ctx, req.status)
        holder.requestId.text = ctx.getString(R.string.booking_request_id_format, req.id)

        val lines = buildList {
            req.service_id?.let { add(ctx.getString(R.string.booking_detail_service, it.toString())) }
            req.location_text?.takeIf { it.isNotBlank() }?.let { add(it) }
            req.maps_url?.takeIf { it.isNotBlank() }?.let { add(it) }
            req.proposed_price?.let { p ->
                val pStr = if (p % 1.0 == 0.0) p.toLong().toString() else p.toString()
                add(ctx.getString(R.string.booking_detail_price_format, pStr))
            }
            req.message?.takeIf { it.isNotBlank() }?.let { add(it) }
            req.created_at?.takeIf { it.isNotBlank() }?.let { raw ->
                val short = raw.replace("T", " ").take(16).trim()
                if (short.isNotEmpty()) add(short)
            }
        }
        holder.details.text = lines.joinToString("\n")
        holder.details.isVisible = lines.isNotEmpty()

        val pending = req.status.equals("pending", ignoreCase = true)
        val isIncomingAsProvider = req.role.equals("provider", ignoreCase = true)
        val isOutgoingAsClient =
            req.role.equals("client", ignoreCase = true) || req.role.equals("seeker", ignoreCase = true)
        val showAccept = onAccept != null && pending && isIncomingAsProvider
        val showDecline = onDecline != null && pending && isIncomingAsProvider
        holder.accept.isVisible = showAccept
        holder.decline.isVisible = showDecline
        holder.actionsPrimary.isVisible = showAccept || showDecline

        val showCancel = onCancel != null && pending && isOutgoingAsClient
        val showMaps = onOpenMaps != null
        holder.cancel.isVisible = showCancel
        holder.maps.isVisible = showMaps
        holder.actionsSecondary.isVisible = showCancel || showMaps

        holder.accept.setOnClickListener { onAccept?.invoke(req) }
        holder.decline.setOnClickListener { onDecline?.invoke(req) }
        holder.cancel.setOnClickListener { onCancel?.invoke(req) }
        holder.maps.setOnClickListener { onOpenMaps?.invoke(req) }
    }

    override fun getItemCount(): Int = items.size
}

private fun TextView.applyBookingStatus(context: Context, raw: String) {
    val key = raw.lowercase()
    val (bg, fg) = when {
        key == "pending" -> R.drawable.bg_booking_status_pending to R.color.booking_status_pending_text
        key == "accepted" -> R.drawable.bg_booking_status_accepted to R.color.booking_status_accepted_text
        key == "declined" -> R.drawable.bg_booking_status_declined to R.color.booking_status_declined_text
        key == "cancelled" || key == "canceled" -> R.drawable.bg_booking_status_cancelled to R.color.booking_status_cancelled_text
        else -> R.drawable.bg_booking_status_neutral to R.color.on_surface_variant
    }
    setBackgroundResource(bg)
    setTextColor(ContextCompat.getColor(context, fg))
    text = raw.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
    }
}

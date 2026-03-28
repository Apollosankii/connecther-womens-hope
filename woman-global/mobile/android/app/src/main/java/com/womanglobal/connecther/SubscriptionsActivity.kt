package com.womanglobal.connecther

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.womanglobal.connecther.data.SubscriptionPackage
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.launch

class SubscriptionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscriptions)
        findViewById<Toolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        val rv = findViewById<RecyclerView>(R.id.recyclerSubscriptions)
        val progress = findViewById<ProgressBar>(R.id.progressBar)
        val empty = findViewById<TextView>(R.id.emptyText)
        rv.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            val plans = runCatching { SupabaseData.getSubscriptionPlans() }.getOrDefault(emptyList())
            progress.visibility = View.GONE
            empty.visibility = if (plans.isEmpty()) View.VISIBLE else View.GONE
            rv.adapter = SubscriptionPlanAdapter(plans) { plan ->
                val amountKobo = plan.price.replace(",", "").toDoubleOrNull()?.times(100)?.toInt() ?: 0
                startActivity(Intent(this@SubscriptionsActivity, PaymentOptionsActivity::class.java).apply {
                    putExtra("plan_id", plan.id.toIntOrNull() ?: -1)
                    putExtra("price", plan.price)
                    putExtra("amount_kobo", amountKobo)
                    putExtra("email", getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", ""))
                })
            }
        }
    }
}

private class SubscriptionPlanAdapter(
    private val plans: List<SubscriptionPackage>,
    private val onSubscribe: (SubscriptionPackage) -> Unit,
) : RecyclerView.Adapter<SubscriptionPlanAdapter.VH>() {
    class VH(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subscription_package, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = plans[position]
        holder.itemView.findViewById<TextView>(R.id.packageName).text = item.name
        holder.itemView.findViewById<TextView>(R.id.packageDescription).text = item.description
        holder.itemView.findViewById<TextView>(R.id.packagePrice).text = "KES ${item.price}"
        holder.itemView.findViewById<TextView>(R.id.packageDuration).text = "/ ${item.duration}"
        holder.itemView.findViewById<TextView>(R.id.packageFeatures).text = item.features.joinToString("\n") { "- $it" }
        holder.itemView.findViewById<TextView>(R.id.popularBadge).visibility = if (item.isPopular) View.VISIBLE else View.GONE
        holder.itemView.findViewById<com.google.android.material.button.MaterialButton>(R.id.subscribeButton)
            .setOnClickListener { onSubscribe(item) }
    }

    override fun getItemCount(): Int = plans.size
}

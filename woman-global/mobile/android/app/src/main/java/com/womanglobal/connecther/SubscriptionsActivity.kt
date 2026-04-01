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
import com.google.android.material.button.MaterialButton
import com.womanglobal.connecther.data.SubscriptionPackage
import com.womanglobal.connecther.data.local.AppOfflineCache
import com.womanglobal.connecther.supabase.SupabaseData
import com.womanglobal.connecther.supabase.SupabaseData.ActiveSubscription
import com.womanglobal.connecther.utils.NetworkStatus
import kotlinx.coroutines.launch

class SubscriptionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscriptions)
        findViewById<Toolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        val rv = findViewById<RecyclerView>(R.id.recyclerSubscriptions)
        val progress = findViewById<ProgressBar>(R.id.progressBar)
        val empty = findViewById<TextView>(R.id.emptyText)
        val activeSubCard = findViewById<View>(R.id.activeSubCard)
        val activeSubPlan = findViewById<TextView>(R.id.activeSubPlan)
        val activeSubExpires = findViewById<TextView>(R.id.activeSubExpires)
        val activeSubConnects = findViewById<TextView>(R.id.activeSubConnects)
        val plansSectionTitle = findViewById<TextView>(R.id.plansSectionTitle)
        rv.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            val online = NetworkStatus.isOnline(this@SubscriptionsActivity)
            val plans = if (online) {
                val p = runCatching { SupabaseData.getSubscriptionPlans() }.getOrDefault(emptyList())
                AppOfflineCache.writeSubscriptionPlans(this@SubscriptionsActivity, p)
                p
            } else {
                AppOfflineCache.readSubscriptionPlans(this@SubscriptionsActivity).orEmpty()
            }
            progress.visibility = View.GONE
            empty.visibility = if (plans.isEmpty()) View.VISIBLE else View.GONE
            plansSectionTitle.visibility = if (plans.isNotEmpty()) View.VISIBLE else View.GONE

            val activeSub = if (online) {
                runCatching { SupabaseData.getActiveSubscription() }.getOrNull().also {
                    AppOfflineCache.writeActiveSubscription(this@SubscriptionsActivity, it)
                }
            } else {
                AppOfflineCache.readActiveSubscription(this@SubscriptionsActivity)
            }
            bindCurrentPlanSection(activeSubCard, activeSubPlan, activeSubExpires, activeSubConnects, activeSub)

            rv.adapter = SubscriptionPlanAdapter(
                plans = plans,
                currentPlanId = activeSub?.planId,
                onSubscribe = { plan ->
                    val amountKobo = plan.price.replace(",", "").toDoubleOrNull()?.times(100)?.toInt() ?: 0
                    startActivity(Intent(this@SubscriptionsActivity, PaystackNativePaymentActivity::class.java).apply {
                        putExtra("plan_id", plan.id.toIntOrNull() ?: -1)
                        putExtra("price", plan.price)
                        putExtra("amount_kobo", amountKobo)
                        putExtra("email", getSharedPreferences("user_session", MODE_PRIVATE).getString("user_email", ""))
                    })
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val activeSubCard = findViewById<View>(R.id.activeSubCard)
            val activeSubPlan = findViewById<TextView>(R.id.activeSubPlan)
            val activeSubExpires = findViewById<TextView>(R.id.activeSubExpires)
            val activeSubConnects = findViewById<TextView>(R.id.activeSubConnects)
            val online = NetworkStatus.isOnline(this@SubscriptionsActivity)
            val activeSub = if (online) {
                runCatching { SupabaseData.getActiveSubscription() }.getOrNull().also {
                    AppOfflineCache.writeActiveSubscription(this@SubscriptionsActivity, it)
                }
            } else {
                AppOfflineCache.readActiveSubscription(this@SubscriptionsActivity)
            }
            bindCurrentPlanSection(activeSubCard, activeSubPlan, activeSubExpires, activeSubConnects, activeSub)

            val adapter = findViewById<RecyclerView>(R.id.recyclerSubscriptions).adapter as? SubscriptionPlanAdapter
            adapter?.updateCurrentPlanId(activeSub?.planId)
        }
    }

    private fun bindCurrentPlanSection(
        activeSubCard: View,
        activeSubPlan: TextView,
        activeSubExpires: TextView,
        activeSubConnects: TextView,
        activeSub: ActiveSubscription?,
    ) {
        if (activeSub != null) {
            activeSubCard.visibility = View.VISIBLE
            activeSubPlan.text = activeSub.planName
            activeSubExpires.text = if (!activeSub.expiresAt.isNullOrBlank()) {
                getString(R.string.subscriptions_current_plan_renews, activeSub.expiresAt)
            } else {
                getString(R.string.subscriptions_current_plan_active_no_end)
            }
            val granted = activeSub.connectsGranted
            if (granted == null) {
                activeSubConnects.text = getString(R.string.subscriptions_connects_unlimited)
            } else {
                val used = activeSub.connectsUsed ?: 0
                val left = maxOf(0, granted - used)
                activeSubConnects.text = getString(R.string.subscriptions_connects_remaining, left, used, granted)
            }
            activeSubConnects.visibility = View.VISIBLE
        } else {
            activeSubCard.visibility = View.GONE
            activeSubConnects.visibility = View.GONE
        }
    }
}

private class SubscriptionPlanAdapter(
    private val plans: List<SubscriptionPackage>,
    private var currentPlanId: Int?,
    private val onSubscribe: (SubscriptionPackage) -> Unit,
) : RecyclerView.Adapter<SubscriptionPlanAdapter.VH>() {
    class VH(view: View) : RecyclerView.ViewHolder(view)

    fun updateCurrentPlanId(planId: Int?) {
        if (currentPlanId == planId) return
        currentPlanId = planId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subscription_package, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = plans[position]
        val ctx = holder.itemView.context
        val planIdInt = item.id.toIntOrNull() ?: -1
        val isCurrent = currentPlanId != null && currentPlanId == planIdInt

        holder.itemView.findViewById<TextView>(R.id.packageName).text = item.name
        holder.itemView.findViewById<TextView>(R.id.packageDescription).text = item.description
        holder.itemView.findViewById<TextView>(R.id.packagePrice).text = "KES ${item.price}"
        holder.itemView.findViewById<TextView>(R.id.packageDuration).text = "/ ${item.duration}"
        holder.itemView.findViewById<TextView>(R.id.packageFeatures).text = item.features.joinToString("\n") { "- $it" }

        val popularBadge = holder.itemView.findViewById<TextView>(R.id.popularBadge)
        val currentBadge = holder.itemView.findViewById<TextView>(R.id.currentPlanBadge)
        val subscribeButton = holder.itemView.findViewById<MaterialButton>(R.id.subscribeButton)
        val currentFooter = holder.itemView.findViewById<TextView>(R.id.currentPlanFooter)

        currentBadge.visibility = if (isCurrent) View.VISIBLE else View.GONE
        popularBadge.visibility = if (!isCurrent && item.isPopular) View.VISIBLE else View.GONE

        if (isCurrent) {
            subscribeButton.visibility = View.GONE
            currentFooter.visibility = View.VISIBLE
        } else {
            subscribeButton.visibility = View.VISIBLE
            currentFooter.visibility = View.GONE
            subscribeButton.setOnClickListener { onSubscribe(item) }
        }
    }

    override fun getItemCount(): Int = plans.size
}

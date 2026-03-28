package com.womanglobal.connecther.payment

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class PaystackPaymentViewModel(
    private val repo: PaystackPaymentRepository = PaystackPaymentRepository(),
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val message: String? = null,
        val error: String? = null,
    )

    data class SheetLaunch(
        val accessCode: String,
        val reference: String,
        val planId: Int,
    )

    private val _state = MutableLiveData(UiState())
    val state: LiveData<UiState> = _state

    private val _sheetLaunch = MutableLiveData<SheetLaunch?>(null)
    val sheetLaunch: LiveData<SheetLaunch?> = _sheetLaunch

    fun clearSheetLaunch() {
        _sheetLaunch.value = null
    }

    /**
     * Loads `access_code` from `paystack-express` for [planId], then Activity shows [com.paystack.android_sdk.ui.paymentsheet.PaymentSheet].
     */
    fun preparePaymentSheet(planId: Int, email: String) {
        if (planId < 1) {
            _state.value = UiState(error = "Missing plan. Open this screen from Subscriptions.")
            return
        }
        if (email.isBlank()) {
            _state.value = UiState(error = "Email is required.")
            return
        }

        _state.value = UiState(loading = true, message = "Preparing payment…")

        viewModelScope.launch {
            val init = repo.initializeTransaction(planId, email).getOrElse { e ->
                _state.postValue(
                    UiState(
                        loading = false,
                        error = e.message ?: "Failed to initialize payment",
                    ),
                )
                return@launch
            }
            _sheetLaunch.postValue(SheetLaunch(init.accessCode, init.reference, planId))
            _state.postValue(UiState(loading = false, message = "Choose a payment method…"))
        }
    }

    fun onSheetFlowFinished(message: String?, error: String?) {
        _state.value = UiState(loading = false, message = message, error = error)
    }

    fun pollSubscriptionAfterSuccess(planId: Int, paymentReference: String, onDone: (activated: Boolean) -> Unit) {
        viewModelScope.launch {
            _state.postValue(UiState(loading = true, message = "Activating your subscription…"))
            val ok = repo.waitForSubscriptionActive(planId, paymentReference)
            _state.postValue(
                UiState(
                    loading = false,
                    message = if (ok) "Subscription is active." else "Payment received. Your plan may take a moment to activate—pull to refresh in Subscriptions.",
                    error = null,
                ),
            )
            onDone(ok)
        }
    }
}

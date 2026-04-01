package com.womanglobal.connecther.payment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.womanglobal.connecther.R
import com.womanglobal.connecther.utils.UserFriendlyMessages
import kotlinx.coroutines.launch

class PaystackPaymentViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val repo = PaystackPaymentRepository()

    data class UiState(
        val loading: Boolean = false,
        val message: String? = null,
        val error: String? = null,
    )

    data class SheetLaunch(
        val accessCode: String,
        val reference: String,
        val planId: Int,
        val authorizationUrl: String,
    )

    private val _state = MutableLiveData(UiState())
    val state: LiveData<UiState> = _state

    private val _sheetLaunch = MutableLiveData<SheetLaunch?>(null)
    val sheetLaunch: LiveData<SheetLaunch?> = _sheetLaunch

    private val app get() = getApplication<Application>()

    fun clearSheetLaunch() {
        _sheetLaunch.value = null
    }

    fun preparePaymentSheet(planId: Int, email: String) {
        if (planId < 1) {
            _state.value = UiState(error = app.getString(R.string.paystack_plan_missing))
            return
        }
        if (email.isBlank()) {
            _state.value = UiState(error = app.getString(R.string.paystack_email_missing))
            return
        }

        _state.value = UiState(loading = true, message = app.getString(R.string.paystack_preparing))

        viewModelScope.launch {
            val init = repo.initializeTransaction(planId, email).getOrElse { e ->
                _state.postValue(
                    UiState(
                        loading = false,
                        error = UserFriendlyMessages.paystackInit(app, e),
                    ),
                )
                return@launch
            }
            _sheetLaunch.postValue(SheetLaunch(init.accessCode, init.reference, planId, init.authorizationUrl))
            _state.postValue(UiState(loading = false, message = app.getString(R.string.paystack_choose_method)))
        }
    }

    fun onSheetFlowFinished(message: String?, error: String?) {
        _state.value = UiState(loading = false, message = message, error = error)
    }

    fun pollSubscriptionAfterSuccess(planId: Int, paymentReference: String, onDone: (activated: Boolean) -> Unit) {
        viewModelScope.launch {
            _state.postValue(
                UiState(loading = true, message = app.getString(R.string.paystack_activation_loading)),
            )
            val viaVerify = repo.verifyPaystackTransaction(paymentReference)
            val ok = viaVerify || repo.waitForSubscriptionActive(planId, paymentReference)
            _state.postValue(
                UiState(
                    loading = false,
                    message = if (ok) {
                        app.getString(R.string.paystack_subscription_activated)
                    } else {
                        app.getString(R.string.paystack_activation_pending)
                    },
                    error = null,
                ),
            )
            onDone(ok)
        }
    }
}

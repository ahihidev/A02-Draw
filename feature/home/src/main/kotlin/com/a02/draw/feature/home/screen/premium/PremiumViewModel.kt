package com.a02.draw.feature.home.screen.premium

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.a02.draw.core.ui.billing.PremiumBillingController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val billingController: PremiumBillingController,
) : ViewModel() {
    val state = billingController.state

    init {
        billingController.refreshProducts()
    }

    fun select(offerKey: String) = billingController.selectOffer(offerKey)

    fun retry() = billingController.refreshProducts()

    fun restore() = billingController.restorePurchases()

    fun synchronizeEntitlement() = billingController.synchronizeEntitlement()

    fun buy(activity: Activity) {
        state.value.selectedOfferKey?.let { billingController.launchPurchase(activity, it) }
    }
}

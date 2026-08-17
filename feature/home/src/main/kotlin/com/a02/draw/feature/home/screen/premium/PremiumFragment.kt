package com.a02.draw.feature.home.screen.premium

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.a02.draw.core.ui.ads.AppAdsController
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.billing.PremiumCatalogStatus
import com.a02.draw.core.ui.billing.PremiumProductType
import com.a02.draw.core.ui.billing.PremiumPurchaseStatus
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.applySystemBarsPadding
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.databinding.ScreenPremiumBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PremiumFragment : BaseFragment<ScreenPremiumBinding>(ScreenPremiumBinding::inflate) {
    @Inject
    lateinit var appAdsController: AppAdsController

    private val viewModel: PremiumViewModel by viewModels()
    private val offersAdapter = PremiumOfferAdapter { offerKey -> viewModel.select(offerKey) }
    private var hasHandledSuccess = false

    override fun setupViews(savedInstanceState: Bundle?) = with(binding) {
        WindowCompat.getInsetsController(
            requireActivity().window,
            root,
        ).isAppearanceLightStatusBars = true
        title.text = highlightedTitle()
        scrollContent.applySystemBarsPadding(includeTop = false)
        closeButton.applyStatusBarPadding()
        offers.layoutManager = LinearLayoutManager(requireContext())
        offers.adapter = offersAdapter
        closeButton.setDebouncedClickListener { findNavController().navigateUp() }
        retryButton.setDebouncedClickListener { viewModel.retry() }
        purchaseButton.setDebouncedClickListener { viewModel.buy(requireActivity()) }
        restoreButton.setDebouncedClickListener { viewModel.restore() }
        manageSubscriptionsButton.setDebouncedClickListener { openSubscriptionCenter() }
        termsButton.setDebouncedClickListener { openLegal("terms") }
        privacyButton.setDebouncedClickListener { openLegal("privacy") }
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    if (state.purchaseStatus is PremiumPurchaseStatus.Verified && !hasHandledSuccess) {
                        hasHandledSuccess = true
                        appAdsController.releaseAdsForPremium()
                        Toast.makeText(
                            requireContext(),
                            R.string.premium_purchase_congratulations,
                            Toast.LENGTH_SHORT,
                        ).show()
                        findNavController().navigateUp()
                        return@collect
                    }
                    val loading = state.catalogStatus == PremiumCatalogStatus.LOADING
                    binding.loadingSkeleton.isVisible = loading
                    binding.offers.isVisible = !loading && state.offers.isNotEmpty()
                    binding.retryButton.isVisible = state.catalogStatus in setOf(
                        PremiumCatalogStatus.ERROR,
                        PremiumCatalogStatus.EMPTY,
                    )
                    binding.catalogMessage.isVisible = !loading && (
                            state.catalogStatus != PremiumCatalogStatus.READY ||
                                    state.unfetchedProductIds.isNotEmpty()
                            )
                    binding.catalogMessage.text = when {
                        state.catalogStatus == PremiumCatalogStatus.ERROR ->
                            getString(R.string.premium_billing_unavailable)

                        state.catalogStatus == PremiumCatalogStatus.EMPTY &&
                                state.unfetchedProductIds.isNotEmpty() -> getString(
                            R.string.premium_products_unavailable,
                            state.unfetchedProductIds.joinToString(),
                        )

                        state.catalogStatus == PremiumCatalogStatus.EMPTY ->
                            getString(R.string.premium_no_products)

                        state.unfetchedProductIds.isNotEmpty() -> getString(
                            R.string.premium_partial_products,
                            state.unfetchedProductIds.joinToString(),
                        )

                        else -> ""
                    }
                    val subscriptionOffers = state.offers.filter {
                        it.productType == PremiumProductType.SUBSCRIPTION
                    }
                    val popularKey = subscriptionOffers
                        .takeIf { it.size > 1 }
                        ?.maxByOrNull { it.recurringPeriodWeight() }
                        ?.key
                    offersAdapter.submitList(state.offers.map { offer ->
                        PremiumOfferRow(
                            offer = offer,
                            selected = offer.key == state.selectedOfferKey,
                            popular = offer.key == popularKey,
                        )
                    })
                    val selectedOffer = state.offers.firstOrNull {
                        it.key == state.selectedOfferKey
                    }
                    binding.offerDisclosure.isVisible = selectedOffer != null
                    binding.offerDisclosure.text = selectedOffer?.policyDisclosure(requireContext())
                        .orEmpty()
                    val checkoutBusy = state.purchaseStatus == PremiumPurchaseStatus.Launching
                    binding.purchaseButton.isEnabled =
                        state.selectedOfferKey != null && !loading && !checkoutBusy
                    binding.purchaseButton.text = if (checkoutBusy) {
                        getString(R.string.premium_preparing_checkout)
                    } else {
                        selectedOffer?.checkoutButtonLabel(requireContext())
                            ?: getString(R.string.premium_continue)
                    }
                    binding.purchaseMessage.isVisible =
                        state.purchaseStatus !in setOf(
                            PremiumPurchaseStatus.Idle,
                            PremiumPurchaseStatus.Canceled,
                        )
                    binding.purchaseMessage.text = when (state.purchaseStatus) {
                        is PremiumPurchaseStatus.WaitingForPayment ->
                            getString(R.string.premium_payment_pending)

                        is PremiumPurchaseStatus.WaitingForVerification ->
                            getString(R.string.premium_verification_pending)

                        is PremiumPurchaseStatus.Verified ->
                            getString(R.string.premium_purchase_verified)

                        PremiumPurchaseStatus.Error -> getString(R.string.premium_purchase_error)
                        else -> ""
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.synchronizeEntitlement()
    }

    private fun openLegal(settingId: String) {
        findNavController().navigate(
            R.id.settingsDetailFragment,
            Bundle().apply {
                putString("settingId", settingId)
                putBoolean("fromPremium", true)
            },
        )
    }

    private fun openSubscriptionCenter() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            GOOGLE_PLAY_SUBSCRIPTIONS_URL.toUri(),
        )
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            binding.catalogMessage.isVisible = true
            binding.catalogMessage.setText(R.string.premium_cannot_open_subscription_center)
        }
    }

    private fun highlightedTitle(): CharSequence {
        val title = getString(R.string.premium_title)
        val emphasis = getString(R.string.premium_title_emphasis)
        val start = title.indexOf(emphasis)
        if (start < 0) return title
        return SpannableString(title).apply {
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.home_purple)),
                start,
                start + emphasis.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }


    private companion object {
        const val GOOGLE_PLAY_SUBSCRIPTIONS_URL =
            "https://play.google.com/store/account/subscriptions"
    }
}

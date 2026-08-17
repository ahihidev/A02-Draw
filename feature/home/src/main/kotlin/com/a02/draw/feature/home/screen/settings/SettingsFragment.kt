package com.a02.draw.feature.home.screen.settings

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.a02.draw.core.ui.ads.AppAdPlacement
import com.a02.draw.core.ui.ads.AppAdsController
import com.a02.draw.core.ui.ads.AppNativeAdFormat
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.domain.model.AppSettingItem
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.ads.runAdNavigation
import com.a02.draw.feature.home.common.component.SettingAdapter
import com.a02.draw.feature.home.common.model.BottomDestination
import com.a02.draw.feature.home.common.navigation.bindBottomNavigation
import com.a02.draw.feature.home.common.navigation.bindMainTabHeader
import com.a02.draw.feature.home.common.navigation.navigateBottom
import com.a02.draw.feature.home.databinding.ScreenSettingsBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : BaseFragment<ScreenSettingsBinding>(ScreenSettingsBinding::inflate) {
    @Inject
    lateinit var appAdsController: AppAdsController
    override val useScreenTransitions: Boolean = false

    private val viewModel: SettingsViewModel by viewModels()
    private val adapter = SettingAdapter { viewModel.onAction(SettingsAction.OpenItem(it)) }
    private var isPremiumOwned = false

    override fun setupViews(savedInstanceState: Bundle?) {
        isPremiumOwned = appAdsController.isPremium.value
        binding.premiumBanner.isVisible = !isPremiumOwned
        appAdsController.attachNative(
            binding.nativeAdContainer,
            AppAdPlacement.SETTING,
            AppNativeAdFormat.MEDIUM,
            viewLifecycleOwner,
        )
        binding.header.bindMainTabHeader()
        binding.settingsList.layoutManager = LinearLayoutManager(requireContext())
        binding.settingsList.adapter = adapter
        binding.premiumBanner.setDebouncedClickListener {
            viewModel.onAction(SettingsAction.OpenItem(PREMIUM_SETTING_ID))
        }
        binding.bottomNavigationInclude.bindBottomNavigation(BottomDestination.SETTINGS) {
            viewModel.onAction(SettingsAction.OpenBottom(it))
        }
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    val showSkeleton = state.isLoading && state.items.isEmpty()
                    renderItems(state.items)
                    binding.loadingSkeleton.isVisible = showSkeleton
                    binding.settingsList.isVisible = !showSkeleton
                }
            }
            launch {
                appAdsController.isPremium.collect { isPremium ->
                    isPremiumOwned = isPremium
                    binding.premiumBanner.isVisible = !isPremium
                    renderItems(viewModel.state.value.items)
                }
            }
            launch { viewModel.effects.collect(::handleEffect) }
        }
    }

    private fun handleEffect(effect: SettingsEffect) {
        when (effect) {
            SettingsEffect.NavigatePremium -> findNavController().navigate(R.id.premiumFragment)
            is SettingsEffect.NavigateDetail -> runAdNavigation(appAdsController) {
                findNavController().navigate(
                    R.id.settingsDetailFragment,
                    Bundle().apply { putString("settingId", effect.settingId) },
                )
            }

            is SettingsEffect.OpenExternal -> open(effect.target)
            SettingsEffect.OpenStore -> open("market://details?id=${requireContext().packageName}")
            SettingsEffect.ShareApp -> runCatching {
                appAdsController.suppressNextBackgroundInterstitial()
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, getString(R.string.app_display_name))
                        },
                        getString(R.string.app_display_name),
                    ),
                )
            }.onFailure { showError() }

            is SettingsEffect.NavigateBottom -> runAdNavigation(appAdsController) {
                findNavController().navigateBottom(effect.destination)
            }
        }
    }

    private fun open(target: String) {
        runCatching {
            appAdsController.suppressNextBackgroundInterstitial()
            startActivity(
                Intent(
                    if (target.startsWith("mailto:")) Intent.ACTION_SENDTO else Intent.ACTION_VIEW,
                    target.toUri(),
                ),
            )
        }.onFailure {
            showError()
        }
    }

    private fun showError() {
        Snackbar.make(binding.root, R.string.generic_error, Snackbar.LENGTH_SHORT).show()
    }

    private fun renderItems(items: List<AppSettingItem>) {
        adapter.submitList(
            items.mapNotNull { item ->
                when {
                    item.id != PREMIUM_SETTING_ID -> item
                    isPremiumOwned -> item.copy(
                        title = getString(R.string.premium_manage_subscriptions),
                    )

                    else -> null
                }
            },
        )
    }

    private companion object {
        const val PREMIUM_SETTING_ID = "subscription"
    }
}

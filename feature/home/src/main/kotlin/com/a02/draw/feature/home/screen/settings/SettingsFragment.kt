package com.a02.draw.feature.home.screen.settings

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.component.SettingAdapter
import com.a02.draw.feature.home.common.model.BottomDestination
import com.a02.draw.feature.home.common.navigation.bindBottomNavigation
import com.a02.draw.feature.home.common.navigation.bindMainTabHeader
import com.a02.draw.feature.home.common.navigation.navigateBottom
import com.a02.draw.feature.home.databinding.ScreenSettingsBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : BaseFragment<ScreenSettingsBinding>(ScreenSettingsBinding::inflate) {
    override val useScreenTransitions: Boolean = false

    private val viewModel: SettingsViewModel by viewModels()
    private val adapter = SettingAdapter { viewModel.onAction(SettingsAction.OpenItem(it)) }

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.header.bindMainTabHeader()
        binding.settingsList.layoutManager = LinearLayoutManager(requireContext())
        binding.settingsList.adapter = adapter
        binding.bottomNavigationInclude.bindBottomNavigation(BottomDestination.SETTINGS) {
            viewModel.onAction(SettingsAction.OpenBottom(it))
        }
    }

    override fun observeData() {
        collectWhenStarted {
            launch {
                viewModel.state.collect { state ->
                    val showSkeleton = state.isLoading && state.items.isEmpty()
                    adapter.submitList(state.items)
                    binding.loadingSkeleton.isVisible = showSkeleton
                    binding.settingsList.isVisible = !showSkeleton
                }
            }
            launch { viewModel.effects.collect(::handleEffect) }
        }
    }

    private fun handleEffect(effect: SettingsEffect) {
        when (effect) {
            is SettingsEffect.NavigateDetail -> findNavController().navigate(
                R.id.settingsDetailFragment,
                Bundle().apply { putString("settingId", effect.settingId) },
            )

            is SettingsEffect.OpenExternal -> open(effect.target)
            SettingsEffect.OpenStore -> open("market://details?id=${requireContext().packageName}")
            SettingsEffect.ShareApp -> runCatching {
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

            is SettingsEffect.NavigateBottom -> findNavController().navigateBottom(effect.destination)
        }
    }

    private fun open(target: String) {
        runCatching {
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
}

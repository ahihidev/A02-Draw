package com.a02.draw

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.a02.draw.ads.startup.StartupAdPlacement
import com.a02.draw.ads.startup.StartupAdsCoordinator
import com.a02.draw.core.ui.base.BaseActivity
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.databinding.ActivityLanguageBinding
import com.a02.draw.language.LanguageAction
import com.a02.draw.language.LanguageAdapter
import com.a02.draw.language.LanguageEffect
import com.a02.draw.language.LanguageUiState
import com.a02.draw.language.LanguageViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LanguageActivity : BaseActivity<ActivityLanguageBinding>(ActivityLanguageBinding::inflate) {
    @Inject
    lateinit var startupAdsCoordinator: StartupAdsCoordinator

    private val viewModel: LanguageViewModel by viewModels()
    private var hasRequestedLanguageAd = false
    private val languageAdapter = LanguageAdapter { languageTag ->
        viewModel.onAction(LanguageAction.Select(languageTag))
    }

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.languageList.adapter = languageAdapter
        binding.languageList.itemAnimator = null
    }

    override fun onPostResume() {
        super.onPostResume()
        if (hasRequestedLanguageAd) return
        hasRequestedLanguageAd = true
        binding.languageAd.post(::loadLanguageAd)
    }

    private fun loadLanguageAd() {
        if (isFinishing || isDestroyed) return
        startupAdsCoordinator.attachNative(
            host = binding.languageAd,
            placement = StartupAdPlacement.LANGUAGE,
            lifecycleOwner = this,
        )
    }

    override fun setupListeners() {
        binding.languageContinue.setDebouncedClickListener {
            viewModel.onAction(LanguageAction.Continue)
        }
    }

    override fun observeData() {
        collectWhenStarted {
            launch { viewModel.state.collect(::render) }
            launch { viewModel.effects.collect(::handleEffect) }
        }
    }

    private fun render(state: LanguageUiState) {
        val isFirstPopulation = languageAdapter.itemCount == 0 && state.languages.isNotEmpty()
        languageAdapter.submitList(state.languages) {
            if (isFirstPopulation) binding.languageList.scrollToPosition(0)
        }
        binding.languageContinue.isEnabled = !state.isSaving
        binding.languageContinue.text = getString(
            if (state.isSaving) R.string.language_saving else R.string.language_continue,
        )
    }

    private fun handleEffect(effect: LanguageEffect) {
        when (effect) {
            is LanguageEffect.OpenIntro -> {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(effect.languageTag),
                )
                startActivity(
                    Intent(this, OnboardingActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
                finish()
            }

            LanguageEffect.ShowSaveError -> Snackbar.make(
                binding.root,
                R.string.language_save_error,
                Snackbar.LENGTH_LONG,
            ).show()
        }
    }
}

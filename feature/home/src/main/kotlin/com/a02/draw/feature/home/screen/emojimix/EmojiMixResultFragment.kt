package com.a02.draw.feature.home.screen.emojimix

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.a02.draw.core.ui.ads.AppAdPlacement
import com.a02.draw.core.ui.ads.AppAdsController
import com.a02.draw.core.ui.ads.AppNativeAdFormat
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.common.ads.runAdNavigation
import com.a02.draw.feature.home.databinding.ScreenEmojiMixResultBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class EmojiMixResultFragment :
    BaseFragment<ScreenEmojiMixResultBinding>(ScreenEmojiMixResultBinding::inflate) {
    @Inject
    lateinit var appAdsController: AppAdsController
    private val viewModel: EmojiMixResultViewModel by viewModels()
    private var latestResult: EmojiMixResult? = null
    private val saveDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
            uri?.let { viewModel.onAction(EmojiMixResultAction.Save(it.toString())) }
        }

    override fun setupViews(savedInstanceState: Bundle?) {
        appAdsController.attachNative(
            binding.nativeAdContainer,
            AppAdPlacement.APP_GENERIC,
            AppNativeAdFormat.MEDIUM,
            viewLifecycleOwner,
        )
        binding.root.applyStatusBarPadding()
        binding.backButton.setDebouncedClickListener { viewModel.onAction(EmojiMixResultAction.Back) }
        binding.retryButton.setDebouncedClickListener { viewModel.onAction(EmojiMixResultAction.Retry) }
        binding.drawButton.setDebouncedClickListener { viewModel.onAction(EmojiMixResultAction.Draw) }
        binding.newButton.setDebouncedClickListener { viewModel.onAction(EmojiMixResultAction.CreateNew) }
        binding.saveButton.setDebouncedClickListener {
            latestResult?.let {
                appAdsController.suppressNextBackgroundInterstitial()
                saveDocument.launch(it.displayName)
            }
        }
        binding.copyButton.setDebouncedClickListener { copyResult() }
        binding.shareButton.setDebouncedClickListener { shareResult() }
    }

    override fun observeData() {
        collectWhenStarted {
            launch { viewModel.state.collect(::render) }
            launch { viewModel.effects.collect(::handleEffect) }
        }
    }

    private fun render(state: EmojiMixResultUiState) {
        latestResult = state.result
        binding.progress.isVisible = state.isLoading
        binding.errorGroup.isVisible = state.errorMessage != null
        binding.errorMessage.text = state.errorMessage
        binding.resultGroup.isVisible = state.result != null
        binding.inputs.text = state.inputs.joinToString("  +  ")
        if (state.result != null) binding.resultImage.setImageURI(state.result.uri.toUri())
    }

    private fun handleEffect(effect: EmojiMixResultEffect) {
        when (effect) {
            EmojiMixResultEffect.NavigateBack -> runAdNavigation(appAdsController) {
                findNavController().navigateUp()
            }
            EmojiMixResultEffect.NavigateToDrawingMode -> findNavController().navigate(R.id.tutorialCameraFragment)
            is EmojiMixResultEffect.CreateNew -> runAdNavigation(appAdsController) {
                findNavController().navigate(
                    R.id.emojiMixPickerFragment,
                    Bundle().apply { putString(EmojiMixHomeFragment.ARG_MODE, effect.mode.name) },
                    NavOptions.Builder()
                        .setPopUpTo(R.id.emojiMixPickerFragment, true)
                        .build(),
                )
            }

            EmojiMixResultEffect.Saved -> showMessage(R.string.emoji_mix_saved)
            is EmojiMixResultEffect.ShowError -> Snackbar.make(
                binding.root,
                effect.message,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun copyResult() {
        val result = latestResult ?: return
        val uri = result.uri.toUri()
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newUri(
                requireContext().contentResolver,
                result.displayName,
                uri
            )
        )
        showMessage(R.string.emoji_mix_copied)
    }

    private fun shareResult() {
        val result = latestResult ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, result.uri.toUri())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(
                requireContext().contentResolver,
                result.displayName,
                result.uri.toUri()
            )
        }
        appAdsController.suppressNextBackgroundInterstitial()
        startActivity(Intent.createChooser(intent, getString(R.string.emoji_mix_share)))
    }

    private fun showMessage(message: Int) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}

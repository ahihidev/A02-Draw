package com.a02.draw.feature.home.screen.emojimix

import android.os.Bundle
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applyStatusBarPadding
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.R
import com.a02.draw.feature.home.databinding.ScreenEmojiMixPickerBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EmojiMixPickerFragment :
    BaseFragment<ScreenEmojiMixPickerBinding>(ScreenEmojiMixPickerBinding::inflate) {
    private val viewModel: EmojiMixPickerViewModel by viewModels()
    private val adapter = EmojiOptionAdapter { viewModel.onAction(EmojiMixPickerAction.Toggle(it)) }

    override fun setupViews(savedInstanceState: Bundle?) {
        binding.root.applyStatusBarPadding()
        binding.emojiList.layoutManager = GridLayoutManager(requireContext(), 4)
        binding.emojiList.adapter = adapter
        binding.backButton.setDebouncedClickListener { viewModel.onAction(EmojiMixPickerAction.Back) }
        binding.clearButton.setDebouncedClickListener { viewModel.onAction(EmojiMixPickerAction.Clear) }
        binding.createButton.setDebouncedClickListener { viewModel.onAction(EmojiMixPickerAction.Create) }
    }

    override fun observeData() {
        collectWhenStarted {
            launch { viewModel.state.collect(::render) }
            launch { viewModel.effects.collect(::handleEffect) }
        }
    }

    private fun render(state: EmojiMixPickerUiState) {
        binding.title.text =
            getString(if (state.mode == EmojiMixMode.MIX_2) R.string.emoji_mix_two_title else R.string.emoji_mix_three_title)
        binding.subtitle.text = resources.getQuantityString(
            R.plurals.emoji_mix_picker_hint,
            state.mode.slotCount,
            state.mode.slotCount
        )
        binding.selection.text = state.selected.joinToString("  +  ")
            .ifEmpty { getString(R.string.emoji_mix_no_selection) }
        binding.clearButton.isVisible = state.selected.isNotEmpty()
        binding.createButton.isEnabled = state.canCreate
        adapter.submitList(state.options)
        adapter.setSelected(state.selected)
    }

    private fun handleEffect(effect: EmojiMixPickerEffect) {
        when (effect) {
            EmojiMixPickerEffect.NavigateBack -> findNavController().navigateUp()
            is EmojiMixPickerEffect.OpenResult -> findNavController().navigate(
                R.id.emojiMixResultFragment,
                Bundle().apply {
                    putString(EmojiMixHomeFragment.ARG_MODE, effect.mode.name)
                    putStringArray(ARG_INPUTS, effect.inputs.toTypedArray())
                },
            )
        }
    }

    override fun onDestroyView() {
        binding.emojiList.adapter = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_INPUTS = "selectedInputs"
    }
}

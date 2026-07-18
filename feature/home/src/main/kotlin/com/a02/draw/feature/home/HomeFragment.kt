package com.a02.draw.feature.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.a02.draw.core.ui.base.BaseFragment
import com.a02.draw.core.ui.extensions.applySystemBarsPadding
import com.a02.draw.core.ui.extensions.setDebouncedClickListener
import com.a02.draw.feature.home.databinding.FragmentHomeBinding
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding>(FragmentHomeBinding::inflate) {
    private val viewModel: HomeViewModel by viewModels()
    private val drawingAdapter = DrawingAdapter(::onDrawingClicked)

    override fun setupViews(savedInstanceState: Bundle?) {
        with(binding) {
            root.applySystemBarsPadding()
            drawingList.apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = drawingAdapter
                setHasFixedSize(true)
            }
        }
    }

    override fun setupListeners() = with(binding) {
        addDrawingButton.setDebouncedClickListener { viewModel.onAddDrawingClicked() }
        retryButton.setDebouncedClickListener { viewModel.onRetryClicked() }
    }

    override fun observeData() {
        collectWhenStarted {
            launch { viewModel.state.collect(::render) }
            launch {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is HomeEffect.ShowMessage -> Snackbar.make(
                            binding.root,
                            effect.messageRes,
                            Snackbar.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }

    private fun render(state: HomeUiState) = with(binding) {
        progressIndicator.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        errorGroup.visibility = if (state.hasError) View.VISIBLE else View.GONE
        emptyMessage.visibility = if (!state.isLoading && !state.hasError && state.drawings.isEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        drawingList.visibility = if (!state.isLoading && !state.hasError && state.drawings.isNotEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        drawingAdapter.submitList(state.drawings)
    }

    private fun onDrawingClicked(drawing: com.a02.draw.domain.model.Drawing) {
        Snackbar.make(binding.root, getString(R.string.open_drawing, drawing.title), Snackbar.LENGTH_SHORT)
            .show()
    }
}
